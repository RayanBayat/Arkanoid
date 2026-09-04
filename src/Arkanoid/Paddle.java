package Arkanoid;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;

/**
 * The player's paddle. Unlike the other figures {@link #getX()} is the
 * <em>centre</em> of the paddle, which keeps mouse control and the width
 * power-ups symmetric.
 */
public class Paddle extends Figure {

    public static final double BASE_WIDTH = 118;
    public static final double WIDE_WIDTH = 190;
    public static final double NARROW_WIDTH = 72;
    public static final double HEIGHT = 16;
    public static final double SPEED = 760;

    private static final double LASER_COOLDOWN = 0.20;

    private double w = BASE_WIDTH;
    private double targetW = BASE_WIDTH;
    private double vx;

    private boolean sticky;
    private boolean laser;
    private double cooldown;

    public Paddle(double x, double y) {
        super(x, y);
    }

    @Override
    public void update(double dt) {
        x += vx * dt;
        // Ease towards the target width so power-ups grow instead of snapping.
        w += (targetW - w) * Math.min(1.0, dt * 12.0);
        cooldown = Math.max(0, cooldown - dt);
    }

    /** Keeps the paddle inside the walls. */
    public void clamp(double minX, double maxX) {
        double half = w / 2;
        x = Math.max(minX + half, Math.min(maxX - half, x));
    }

    public double getWidth() {
        return w;
    }

    public double left() {
        return x - w / 2;
    }

    public double right() {
        return x + w / 2;
    }

    public double top() {
        return y - HEIGHT / 2;
    }

    public void setTargetWidth(double target) {
        this.targetW = target;
    }

    public double getTargetWidth() {
        return targetW;
    }

    public void setVx(double vx) {
        this.vx = vx;
    }

    public boolean isSticky() {
        return sticky;
    }

    public void setSticky(boolean sticky) {
        this.sticky = sticky;
    }

    public boolean hasLaser() {
        return laser;
    }

    public void setLaser(boolean laser) {
        this.laser = laser;
    }

    /** @return true if a shot was available; the caller spawns the bullets */
    public boolean tryFire() {
        if (!laser || cooldown > 0) {
            return false;
        }
        cooldown = LASER_COOLDOWN;
        return true;
    }

    /** Resets everything that a lost life should take away. */
    public void clearEffects() {
        targetW = BASE_WIDTH;
        sticky = false;
        laser = false;
        cooldown = 0;
    }

    public void render(Graphics2D g, double time) {
        double l = left();
        double t = top();

        float glow = (float) (w * 0.75);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), glow,
                new float[] {0f, 1f},
                new Color[] {new Color(70, 190, 255, 90), new Color(70, 190, 255, 0)}));
        g.fill(new Ellipse2D.Double(x - glow, y - glow, glow * 2, glow * 2));

        if (laser) {
            g.setColor(new Color(255, 170, 70));
            g.fill(new RoundRectangle2D.Double(l + 3, t - 9, 9, 11, 4, 4));
            g.fill(new RoundRectangle2D.Double(right() - 12, t - 9, 9, 11, 4, 4));
        }

        RoundRectangle2D body = new RoundRectangle2D.Double(l, t, w, HEIGHT, HEIGHT, HEIGHT);
        g.setPaint(new GradientPaint(0, (float) t, new Color(120, 235, 255),
                0, (float) (t + HEIGHT), new Color(35, 110, 210)));
        g.fill(body);

        g.setColor(new Color(255, 255, 255, 190));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new RoundRectangle2D.Double(l + 1, t + 1, w - 2, HEIGHT - 2, HEIGHT, HEIGHT));

        if (sticky) {
            float dash = 6f;
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    1f, new float[] {dash, dash}, (float) (time * 30 % (dash * 2))));
            g.setColor(new Color(245, 220, 80));
            g.drawLine((int) l + 6, (int) t - 2, (int) right() - 6, (int) t - 2);
        }
        g.setStroke(new BasicStroke(1f));
    }
}
