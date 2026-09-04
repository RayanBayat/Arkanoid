package Arkanoid;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * A bloom pass, which is what makes the neon actually glow rather than just be
 * brightly coloured.
 *
 * <p>Java2D has no additive composite and a per-pixel add over the full surface
 * is too slow to do every frame in a single thread. The trick used here is to
 * put the glow in the <em>alpha</em> channel: the bright pass writes a small
 * ARGB image whose colour is the bright pixel and whose alpha is that pixel's
 * luminance. Compositing that over the scene with ordinary {@code SRC_OVER}
 * then adds light exactly where the scene was bright and does nothing where it
 * was dark — the visual result of an additive blend, but performed by the
 * JDK's native scaling blit instead of a Java loop.</p>
 *
 * <p>The blur itself runs on a surface {@value #DOWNSAMPLE} times smaller in
 * each axis, so it costs a fraction of a millisecond, and bilinear upscaling
 * hides the low resolution completely on something this soft.</p>
 */
public final class Bloom {

    private static final int DOWNSAMPLE = 6;
    private static final int BLUR_PASSES = 3;
    private static final int BLUR_RADIUS = 2;

    private final int width;
    private final int height;
    private final int smallW;
    private final int smallH;

    private final BufferedImage smallImage;
    private final int[] smallPixels;
    private final int[] scratch;

    private boolean enabled = true;

    public Bloom(int width, int height) {
        this.width = width;
        this.height = height;
        this.smallW = Math.max(1, width / DOWNSAMPLE);
        this.smallH = Math.max(1, height / DOWNSAMPLE);
        // Premultiplied: Java2D has far faster blit loops for ARGB_PRE, and blurring
        // premultiplied samples is the mathematically correct thing to do anyway -
        // straight alpha bleeds the colour of fully transparent pixels into the glow.
        this.smallImage = new BufferedImage(smallW, smallH, BufferedImage.TYPE_INT_ARGB_PRE);
        this.smallPixels = ((DataBufferInt) smallImage.getRaster().getDataBuffer()).getData();
        this.scratch = new int[smallW * smallH];
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean on) {
        enabled = on;
    }

    public void toggle() {
        enabled = !enabled;
    }

    /**
     * Extracts the bright parts of {@code scene}, blurs them, and composites the
     * result back over {@code g}.
     *
     * @param scene     the rendered frame, {@code TYPE_INT_RGB}
     * @param threshold luminance below which a pixel contributes no glow, 0..1
     * @param strength  overall glow opacity, 0..1
     */
    public void apply(Graphics2D g, BufferedImage scene, double threshold, double strength) {
        if (!enabled || strength <= 0.01) {
            return;
        }
        int[] src = ((DataBufferInt) scene.getRaster().getDataBuffer()).getData();
        brightPass(src, threshold, strength);
        for (int i = 0; i < BLUR_PASSES; i++) {
            blurHorizontal();
            blurVertical();
        }

        // Strength is already folded into the alpha channel, so this can use the
        // default composite. Setting an AlphaComposite with a custom alpha forces
        // Java2D onto a much slower general blit path for a full-surface draw.
        Object oldHint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(smallImage, 0, 0, width, height, null);
        if (oldHint != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldHint);
        }
    }

    /**
     * Downsamples while keeping only what is brighter than the threshold, writing
     * luminance into alpha.
     */
    private void brightPass(int[] src, double threshold, double strength) {
        int cut = (int) (threshold * 255);
        for (int y = 0; y < smallH; y++) {
            int sy = y * DOWNSAMPLE;
            int sy2 = Math.min(height - 1, sy + DOWNSAMPLE / 2);
            for (int x = 0; x < smallW; x++) {
                int sx = x * DOWNSAMPLE;
                int sx2 = Math.min(width - 1, sx + DOWNSAMPLE / 2);

                // Four taps per block: cheap, and enough to stop the glow from
                // crawling as thin bright edges move.
                int a = src[sy * width + sx];
                int b = src[sy * width + sx2];
                int c = src[sy2 * width + sx];
                int d = src[sy2 * width + sx2];

                int r = (((a >> 16) & 0xFF) + ((b >> 16) & 0xFF)
                        + ((c >> 16) & 0xFF) + ((d >> 16) & 0xFF)) >> 2;
                int gg = (((a >> 8) & 0xFF) + ((b >> 8) & 0xFF)
                        + ((c >> 8) & 0xFF) + ((d >> 8) & 0xFF)) >> 2;
                int bb = ((a & 0xFF) + (b & 0xFF) + (c & 0xFF) + (d & 0xFF)) >> 2;

                // Rec. 709 luma, integer-approximated.
                int luma = (r * 54 + gg * 183 + bb * 19) >> 8;
                int over = luma - cut;
                int alpha = over <= 0
                        ? 0
                        : (int) (Math.min(255, over * 255 / Math.max(1, 255 - cut)) * strength);

                // Premultiply so the blur and the blit both stay on the fast path.
                smallPixels[y * smallW + x] = (alpha << 24)
                        | ((r * alpha / 255) << 16)
                        | ((gg * alpha / 255) << 8)
                        | (bb * alpha / 255);
            }
        }
    }

    private void blurHorizontal() {
        int r = BLUR_RADIUS;
        for (int y = 0; y < smallH; y++) {
            int row = y * smallW;
            for (int x = 0; x < smallW; x++) {
                int sa = 0;
                int sr = 0;
                int sg = 0;
                int sb = 0;
                int n = 0;
                for (int k = -r; k <= r; k++) {
                    int xx = x + k;
                    if (xx < 0 || xx >= smallW) {
                        continue;
                    }
                    int p = smallPixels[row + xx];
                    sa += (p >>> 24);
                    sr += (p >> 16) & 0xFF;
                    sg += (p >> 8) & 0xFF;
                    sb += p & 0xFF;
                    n++;
                }
                scratch[row + x] = ((sa / n) << 24) | ((sr / n) << 16) | ((sg / n) << 8) | (sb / n);
            }
        }
        System.arraycopy(scratch, 0, smallPixels, 0, scratch.length);
    }

    private void blurVertical() {
        int r = BLUR_RADIUS;
        for (int x = 0; x < smallW; x++) {
            for (int y = 0; y < smallH; y++) {
                int sa = 0;
                int sr = 0;
                int sg = 0;
                int sb = 0;
                int n = 0;
                for (int k = -r; k <= r; k++) {
                    int yy = y + k;
                    if (yy < 0 || yy >= smallH) {
                        continue;
                    }
                    int p = smallPixels[yy * smallW + x];
                    sa += (p >>> 24);
                    sr += (p >> 16) & 0xFF;
                    sg += (p >> 8) & 0xFF;
                    sb += p & 0xFF;
                    n++;
                }
                scratch[y * smallW + x] =
                        ((sa / n) << 24) | ((sr / n) << 16) | ((sg / n) << 8) | (sb / n);
            }
        }
        System.arraycopy(scratch, 0, smallPixels, 0, scratch.length);
    }
}
