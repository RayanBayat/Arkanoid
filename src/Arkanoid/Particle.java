package Arkanoid;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;

/** A short-lived spark thrown out when something breaks or bounces. */
public class Particle extends Figure {

    private static final double GRAVITY = 420;

    private double vx;
    private double vy;
    private double life;
    private final double maxLife;
    private final double size;
    private final Color color;

    public Particle(double x, double y, double vx, double vy, double life, double size, Color color) {
        super(x, y);
        this.vx = vx;
        this.vy = vy;
        this.life = life;
        this.maxLife = life;
        this.size = size;
        this.color = color;
    }

    @Override
    public void update(double dt) {
        vy += GRAVITY * dt;
        vx *= 1 - Math.min(1, dt * 1.1);
        x += vx * dt;
        y += vy * dt;
        life -= dt;
    }

    public boolean isDead() {
        return life <= 0;
    }

    public void render(Graphics2D g) {
        float a = (float) Math.max(0, Math.min(1, life / maxLife));
        double s = size * (0.35 + 0.65 * a);
        g.setColor(new Color(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
                a * a));
        g.fill(new Ellipse2D.Double(x - s / 2, y - s / 2, s, s));
    }
}
