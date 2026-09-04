package Arkanoid;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;

/** A laser shot fired by the paddle. */
public class Bullet extends Figure {

    public static final double W = 4;
    public static final double H = 16;
    private static final double SPEED = 760;

    public Bullet(double x, double y) {
        super(x, y);
    }

    @Override
    public void update(double dt) {
        y -= SPEED * dt;
    }

    /** AABB overlap against a brick. */
    public boolean hits(Bricks b) {
        return x + W / 2 > b.getX()
                && x - W / 2 < b.getX() + b.getW()
                && y - H / 2 < b.getY() + b.getH()
                && y + H / 2 > b.getY();
    }

    public void render(Graphics2D g) {
        g.setColor(new Color(255, 190, 90, 90));
        g.fill(new RoundRectangle2D.Double(x - W, y - H / 2 - 3, W * 2, H + 6, W, W));
        g.setColor(new Color(255, 240, 200));
        g.fill(new RoundRectangle2D.Double(x - W / 2, y - H / 2, W, H, W, W));
    }
}
