package Arkanoid;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

/**
 * The pre-rendered background.
 *
 * <p>The backdrop is a full-surface vertical gradient, two large radial nebulae
 * and a field of stars. Drawn live that is three software gradient fills across
 * 720k pixels plus 140 small fills <em>every frame</em>, which measured as the
 * single largest cost in the renderer — more than the entire brick field.</p>
 *
 * <p>None of it actually needs to be redrawn: it only changes when the palette
 * does, once per level. Baking it into an image and blitting that turns the
 * whole background into one native copy.</p>
 */
public final class Backdrop {

    private static final int STAR_COUNT = 220;

    private final int width;
    private final int height;
    private final BufferedImage image;

    public Backdrop(int width, int height) {
        this.width = width;
        this.height = height;
        this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    }

    /** Repaints the backdrop for a palette. Call when the level changes. */
    public void rebuild(Palette palette, long seed) {
        Rng rng = new Rng(seed);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            g.setPaint(new GradientPaint(0, 0, palette.bgTop, 0, height, palette.bgBottom));
            g.fillRect(0, 0, width, height);

            for (int i = 0; i < 3; i++) {
                double px = rng.range(width * 0.15, width * 0.85);
                double py = rng.range(height * 0.25, height * 0.85);
                float r = (float) rng.range(240, 420);
                g.setPaint(new RadialGradientPaint(new Point2D.Double(px, py), r,
                        new float[] {0f, 1f},
                        new Color[] {Palette.alpha(palette.nebula, 0.22),
                                Palette.alpha(palette.nebula, 0)}));
                g.fill(new Ellipse2D.Double(px - r, py - r, r * 2, r * 2));
            }

            for (int i = 0; i < STAR_COUNT; i++) {
                double x = rng.nextDouble() * width;
                double y = Field.CEIL + rng.nextDouble() * (height - Field.CEIL);
                double s = 0.6 + rng.nextDouble() * 1.9;
                float a = (float) rng.range(0.16, 0.55);
                g.setColor(new Color(0.72f, 0.86f, 1f, a));
                g.fill(new Ellipse2D.Double(x, y, s, s));
            }
        } finally {
            g.dispose();
        }
    }

    public void draw(Graphics2D g) {
        g.drawImage(image, 0, 0, null);
    }
}
