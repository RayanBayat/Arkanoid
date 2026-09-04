package Arkanoid;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;

/**
 * A capsule that drops out of a destroyed brick. {@link #getX()}/{@link #getY()}
 * are the centre of the capsule.
 */
public class PowerupBall extends Figure {

    /** Everything a capsule can be. {@code good} decides the drop weighting. */
    public enum Kind {
        EXPAND('E', new Color(80, 230, 130), "WIDE PADDLE", true, 16),
        MULTI('M', new Color(90, 210, 255), "MULTIBALL", true, 14),
        CATCH('G', new Color(245, 220, 80), "STICKY PADDLE", true, 11),
        LASER('L', new Color(255, 160, 60), "LASER CANNON", true, 13),
        SLOW('S', new Color(120, 150, 255), "SLOW BALL", true, 10),
        LIFE('+', new Color(255, 105, 160), "EXTRA LIFE", true, 5),
        SHRINK('C', new Color(240, 90, 90), "NARROW PADDLE", false, 8),
        FAST('F', new Color(235, 90, 200), "FAST BALL", false, 8);

        private final char badge;
        private final Color color;
        private final String label;
        private final boolean good;
        private final int weight;

        Kind(char badge, Color color, String label, boolean good, int weight) {
            this.badge = badge;
            this.color = color;
            this.label = label;
            this.good = good;
            this.weight = weight;
        }

        public char badge() {
            return badge;
        }

        public Color color() {
            return color;
        }

        public String label() {
            return label;
        }

        public boolean isGood() {
            return good;
        }

        private static final int TOTAL;

        static {
            int t = 0;
            for (Kind k : values()) {
                t += k.weight;
            }
            TOTAL = t;
        }

        /** Picks a kind using the weights above. */
        public static Kind random(Rng rng) {
            int roll = rng.nextInt(TOTAL);
            for (Kind k : values()) {
                roll -= k.weight;
                if (roll < 0) {
                    return k;
                }
            }
            return EXPAND;
        }
    }

    public static final double W = 38;
    public static final double H = 20;
    private static final double FALL_SPEED = 185;

    private final Kind kind;
    private double spin;

    public PowerupBall(double x, double y, Kind kind) {
        super(x, y);
        this.kind = kind;
    }

    @Override
    public void update(double dt) {
        y += FALL_SPEED * dt;
        spin += dt * 3.2;
    }

    public Kind getKind() {
        return kind;
    }

    /** Simple AABB overlap against the paddle. */
    public boolean caughtBy(Paddle p) {
        return x + W / 2 > p.left()
                && x - W / 2 < p.right()
                && y + H / 2 > p.top()
                && y - H / 2 < p.top() + Paddle.HEIGHT;
    }

    public void render(Graphics2D g, Font font) {
        double pulse = 0.85 + 0.15 * Math.sin(spin * 2);
        Color c = kind.color();

        float glow = (float) (W * 0.9 * pulse);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), glow,
                new float[] {0f, 1f},
                new Color[] {new Color(c.getRed(), c.getGreen(), c.getBlue(), 110),
                        new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
        g.fill(new Ellipse2D.Double(x - glow, y - glow, glow * 2, glow * 2));

        // A little vertical squash makes the capsule look like it is tumbling.
        double h = H * (0.72 + 0.28 * Math.abs(Math.cos(spin)));
        RoundRectangle2D body = new RoundRectangle2D.Double(x - W / 2, y - h / 2, W, h, h, h);
        g.setPaint(new GradientPaint(0, (float) (y - h / 2), c.brighter(),
                0, (float) (y + h / 2), c.darker()));
        g.fill(body);
        g.setColor(new Color(255, 255, 255, 200));
        g.draw(body);

        if (h > H * 0.75) {
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            String s = String.valueOf(kind.badge());
            g.setColor(new Color(20, 20, 30, 220));
            g.drawString(s, (float) (x - fm.stringWidth(s) / 2.0),
                    (float) (y + fm.getAscent() / 2.0 - 1));
        }
    }
}
