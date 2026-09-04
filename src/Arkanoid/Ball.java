package Arkanoid;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.util.Arrays;

/**
 * The ball. {@link #getX()}/{@link #getY()} are the centre, and velocity is in
 * pixels per second.
 *
 * <p>Integration is driven externally by {@link Physics}, because the ball has
 * to be advanced in sub-steps between contacts rather than in one jump.</p>
 */
public class Ball extends Figure {

    public static final double RADIUS = 8.0;
    public static final double MAX_SPEED = 1250.0;

    /** The ball is never allowed to travel flatter than this share of vertical speed. */
    private static final double MIN_VERTICAL_RATIO = 0.30;

    private static final int TRAIL = 18;

    private double vx;
    private double vy;

    /** True while the ball rests on the paddle waiting to be launched. */
    private boolean held;
    private double holdOffset;

    /** Bricks this ball may pass straight through before it deflects again. */
    private int pierce;

    private final double[] trailX = new double[TRAIL];
    private final double[] trailY = new double[TRAIL];

    public Ball(double x, double y, double vx, double vy) {
        super(x, y);
        this.vx = vx;
        this.vy = vy;
        // Inlined rather than calling snapTrail(): the class is subclassable, and
        // calling an overridable method from a constructor is a 'this' escape.
        Arrays.fill(trailX, x);
        Arrays.fill(trailY, y);
    }

    /**
     * Simple integration. The main simulation advances the ball through
     * {@link Physics} instead; this exists for the {@link Figure} contract and
     * for anything that just wants the ball to drift.
     */
    @Override
    public void update(double dt) {
        if (held) {
            snapTrail();
            return;
        }
        x += vx * dt;
        y += vy * dt;
        pushTrail();
    }

    /** Records the current position into the motion trail. */
    public void pushTrail() {
        System.arraycopy(trailX, 0, trailX, 1, TRAIL - 1);
        System.arraycopy(trailY, 0, trailY, 1, TRAIL - 1);
        trailX[0] = x;
        trailY[0] = y;
    }

    /** Collapses the trail onto the current position, so a parked ball has no tail. */
    public void snapTrail() {
        Arrays.fill(trailX, x);
        Arrays.fill(trailY, y);
    }

    public double getVx() {
        return vx;
    }

    public double getVy() {
        return vy;
    }

    public void setVelocity(double vx, double vy) {
        this.vx = vx;
        this.vy = vy;
    }

    public double speed() {
        return Math.hypot(vx, vy);
    }

    /** Rescales velocity to {@code s} without changing direction. */
    public void setSpeed(double s) {
        double cur = speed();
        if (cur < 1e-6) {
            vx = 0;
            vy = -s;
            return;
        }
        vx = vx / cur * s;
        vy = vy / cur * s;
    }

    /**
     * Nudges the ball away from near-horizontal travel. Without this it can end
     * up ping-ponging between the side walls forever.
     */
    public void enforceMinAngle() {
        double sp = speed();
        if (sp < 1e-6) {
            vx = 0;
            vy = -240;
            return;
        }
        double minVy = sp * MIN_VERTICAL_RATIO;
        if (Math.abs(vy) < minVy) {
            vy = Math.copySign(minVy, vy == 0 ? -1 : vy);
            double rest = sp * sp - vy * vy;
            vx = Math.copySign(Math.sqrt(Math.max(0, rest)), vx == 0 ? 1 : vx);
        }
    }

    public boolean isHeld() {
        return held;
    }

    public double getHoldOffset() {
        return holdOffset;
    }

    public void hold(double offset) {
        held = true;
        holdOffset = offset;
        vx = 0;
        vy = 0;
    }

    /**
     * Releases a held ball.
     *
     * @param speed launch speed in px/s
     * @param angle radians, where -PI/2 is straight up
     */
    public void release(double speed, double angle) {
        held = false;
        vx = Math.cos(angle) * speed;
        vy = Math.sin(angle) * speed;
    }

    public void resetPierce(int charges) {
        pierce = Math.max(0, charges);
    }

    /** @return true if a pierce charge was spent, meaning skip the bounce */
    public boolean consumePierce() {
        if (pierce <= 0) {
            return false;
        }
        pierce--;
        return true;
    }

    public boolean isPiercing() {
        return pierce > 0;
    }

    public void render(Graphics2D g, Color tint, boolean showTrail) {
        for (int i = showTrail ? TRAIL - 1 : 0; i >= 1; i--) {
            double f = 1.0 - (double) i / TRAIL;
            double r = RADIUS * (0.22 + 0.70 * f);
            g.setColor(Palette.alpha(tint, 0.34 * f * f));
            g.fill(new Ellipse2D.Double(trailX[i] - r, trailY[i] - r, r * 2, r * 2));
        }

        float glow = (float) (RADIUS * 3.6);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), glow,
                new float[] {0f, 1f},
                new Color[] {Palette.alpha(tint, 0.55), Palette.alpha(tint, 0)}));
        g.fill(new Ellipse2D.Double(x - glow, y - glow, glow * 2, glow * 2));

        g.setColor(pierce > 0 ? new Color(0xFFD166) : tint);
        g.fill(new Ellipse2D.Double(x - RADIUS, y - RADIUS, RADIUS * 2, RADIUS * 2));
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(x - RADIUS * 0.55, y - RADIUS * 0.7, RADIUS, RADIUS));
    }
}
