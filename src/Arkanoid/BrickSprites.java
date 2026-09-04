package Arkanoid;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Pre-rendered brick faces.
 *
 * <p>Drawing a brick from scratch means a gradient fill, a glow, a gloss inset,
 * an outline and some pips — several {@code Paint} allocations and a handful of
 * shape fills. That is fine for twelve bricks and much less fine for a hundred,
 * where it dominated the frame and pushed the renderer past its budget.</p>
 *
 * <p>A board only ever contains a few distinct brick faces, though: appearance
 * is a function of kind, colour tier and remaining hit points, so a full board
 * has on the order of a dozen unique images. Rendering each once and blitting
 * it turns per-brick cost into a native image copy. Anything genuinely animated
 * — the explosive core, the regen shimmer, the impact flash — is still drawn
 * live on top.</p>
 */
public final class BrickSprites {

    /** Padding around each brick so the baked glow is not clipped. */
    public static final int PAD = 5;

    private final Map<Long, BufferedImage> cache = new HashMap<>();

    /** Drops every cached face. Call when the palette changes. */
    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    /** The baked face for this brick's current appearance. */
    public BufferedImage get(Bricks b) {
        long key = (Bricks.isHighContrast() ? 1L << 52 : 0)
                ^ ((long) b.getKind().ordinal() << 40)
                ^ ((long) b.getTier() << 32)
                ^ ((long) Math.max(0, Math.min(9, b.getHp())) << 24)
                ^ (b.getColor().getRGB() & 0xFFFFFFL);

        BufferedImage cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        int w = (int) Math.ceil(b.getW()) + PAD * 2;
        int h = (int) Math.ceil(b.getH()) + PAD * 2;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                    RenderingHints.VALUE_STROKE_PURE);
            g.translate(PAD, PAD);
            b.paintFace(g);
        } finally {
            g.dispose();
        }

        cache.put(key, img);
        return img;
    }
}
