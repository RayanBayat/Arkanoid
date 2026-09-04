package Arkanoid;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * A brick.
 *
 * <p>Beyond hit points, each brick carries two things the original had no notion
 * of: a {@link Kind} that changes how it behaves when struck, and a
 * {@code scaleDegree} that decides which note it plays when it breaks. The
 * degree is assigned by {@link LevelGen} from the brick's position, so a run of
 * bricks cleared left to right walks up the scale and the board is playable as
 * an instrument.</p>
 *
 * <p>The static part of the face is baked once by {@link BrickSprites}; only
 * what actually animates is drawn per frame.</p>
 */
public class Bricks extends Figure {

    public enum Kind {
        /** One hit, no tricks. */
        NORMAL,
        /** Two hits. */
        TOUGH,
        /** Three hits; generated buried inside a mass. */
        CORE,
        /** Never breaks. Pure geometry. */
        SOLID,
        /** Detonates on death, damaging a radius — chains into other explosives. */
        EXPLOSIVE,
        /** Comes back a limited number of times. */
        REGEN,
    }

    private static final double REGEN_DELAY = 5.5;

    /**
     * Global render mode. Baked into the sprite cache, so {@link BrickSprites}
     * must be cleared when it changes.
     */
    private static boolean highContrast;

    public static void setHighContrast(boolean on) {
        highContrast = on;
    }

    public static boolean isHighContrast() {
        return highContrast;
    }

    private final double w;
    private final double h;
    private final Color base;
    private final Kind kind;
    private final int tier;
    private final int scaleDegree;
    private final int maxHp;

    private int hp;
    private boolean alive = true;
    private double respawnIn;
    private int respawnsLeft;

    /** Decays 1 to 0 after a hit; drives the impact flash. */
    private double flash;
    private final double phaseOffset;

    public Bricks(double x, double y, double w, double h, Color color, Kind kind,
            int tier, int scaleDegree) {
        super(x, y);
        this.w = w;
        this.h = h;
        this.base = color;
        this.kind = kind;
        this.tier = tier;
        this.scaleDegree = scaleDegree;
        this.maxHp = switch (kind) {
            case TOUGH -> 2;
            case CORE -> 3;
            case SOLID -> 9;
            default -> 1;
        };
        this.hp = maxHp;
        this.respawnsLeft = kind == Kind.REGEN ? 2 : 0;
        this.phaseOffset = x * 0.031 + y * 0.017;
    }

    @Override
    public void update(double dt) {
        flash = Math.max(0, flash - dt * 4.5);
        if (!alive && respawnIn > 0) {
            respawnIn -= dt;
            if (respawnIn <= 0) {
                alive = true;
                hp = maxHp;
                flash = 1;
            }
        }
    }

    /**
     * Registers a hit.
     *
     * @param damage hit points to remove
     * @return true when this hit killed the brick
     */
    public boolean hit(int damage) {
        flash = 1;
        if (!alive || kind == Kind.SOLID) {
            return false;
        }
        hp -= Math.max(1, damage);
        if (hp > 0) {
            return false;
        }
        alive = false;
        if (kind == Kind.REGEN && respawnsLeft > 0) {
            respawnsLeft--;
            respawnIn = REGEN_DELAY;
        }
        return true;
    }

    /** True when the brick is gone for good and can be dropped from the board. */
    public boolean isFinished() {
        return !alive && respawnIn <= 0;
    }

    public boolean isAlive() {
        return alive;
    }

    /** Solid blocks are scenery: they never count towards clearing the level. */
    public boolean isBreakable() {
        return kind != Kind.SOLID;
    }

    public boolean isSolid() {
        return kind == Kind.SOLID;
    }

    public Kind getKind() {
        return kind;
    }

    public int getTier() {
        return tier;
    }

    public int getScaleDegree() {
        return scaleDegree;
    }

    public int getHp() {
        return hp;
    }

    public double getW() {
        return w;
    }

    public double getH() {
        return h;
    }

    public Color getColor() {
        return base;
    }

    public double centerX() {
        return x + w / 2;
    }

    public double centerY() {
        return y + h / 2;
    }

    /** Score weight — tougher and rarer bricks are worth more. */
    public int baseValue() {
        return switch (kind) {
            case NORMAL -> 100;
            case TOUGH -> 180;
            case CORE -> 280;
            case EXPLOSIVE -> 220;
            case REGEN -> 150;
            case SOLID -> 0;
        };
    }

    // ------------------------------------------------------------------ render

    /**
     * Draws the brick: one blit of its baked face, plus whatever is genuinely
     * animated on top.
     */
    public void render(Graphics2D g, double time, BrickSprites sprites) {
        if (!alive) {
            renderGhost(g, time);
            return;
        }

        g.drawImage(sprites.get(this),
                (int) Math.round(x) - BrickSprites.PAD,
                (int) Math.round(y) - BrickSprites.PAD, null);

        if (kind == Kind.EXPLOSIVE) {
            double pulse = 0.5 + 0.5 * Math.sin(time * 5 + phaseOffset * 40);
            double r = Math.min(w, h) * (0.22 + 0.06 * pulse);
            g.setColor(new Color(255, 245, 210, (int) (130 + 100 * pulse)));
            g.fill(new Ellipse2D.Double(centerX() - r, centerY() - r, r * 2, r * 2));
        } else if (kind == Kind.REGEN) {
            g.setColor(new Color(255, 255, 255, 130));
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    1f, new float[] {4f, 4f}, (float) (time * 14 % 8)));
            g.draw(new RoundRectangle2D.Double(x + 1.5, y + 1.5, w - 3, h - 3, 7, 7));
            g.setStroke(new BasicStroke(1f));
        }

        if (flash > 0) {
            g.setColor(new Color(1f, 1f, 1f, (float) (flash * 0.85)));
            g.fill(new RoundRectangle2D.Double(x + 1.5, y + 1.5, w - 3, h - 3, 7, 7));
        }
    }

    /**
     * Paints the static face into a sprite. The graphics context is translated so
     * that (0, 0) is this brick's top-left corner.
     */
    void paintFace(Graphics2D g) {
        double wear = maxHp <= 1 ? 1.0 : 0.55 + 0.45 * (hp / (double) maxHp);
        RoundRectangle2D shape = new RoundRectangle2D.Double(1.5, 1.5, w - 3, h - 3, 7, 7);

        // Outer bleed; the bloom pass turns this into a real glow.
        g.setColor(Palette.alpha(base, kind == Kind.EXPLOSIVE ? 0.30 : 0.17));
        g.fill(new RoundRectangle2D.Double(-1, -1, w + 2, h + 2, 10, 10));

        switch (kind) {
            case SOLID -> {
                g.setPaint(new GradientPaint(0, 0, new Color(0x6B7280),
                        0, (float) h, new Color(0x2B303B)));
                g.fill(shape);
                g.setColor(new Color(255, 255, 255, 40));
                g.setStroke(new BasicStroke(1.6f));
                for (double i = -h; i < w; i += 9) {
                    g.drawLine((int) (i + 3), (int) (h - 3), (int) (i + h), 3);
                }
            }
            case EXPLOSIVE -> {
                g.setPaint(new GradientPaint(0, 0, Palette.mix(base, Color.WHITE, 0.42),
                        0, (float) h, Palette.scale(base, 0.5)));
                g.fill(shape);
                g.setColor(new Color(255, 255, 255, 200));
                g.setStroke(new BasicStroke(1.4f));
                double r = Math.min(w, h) * 0.44;
                g.draw(new Ellipse2D.Double(w / 2 - r, h / 2 - r, r * 2, r * 2));
            }
            default -> {
                double lift = highContrast ? 1.55 : 1.28;
                double floor = highContrast ? 0.86 : 0.60;
                g.setPaint(new GradientPaint(0, 0, Palette.scale(base, lift * (2.0 - wear)),
                        0, (float) h, Palette.scale(base, floor * wear)));
                g.fill(shape);
                g.setColor(new Color(255, 255, 255, highContrast ? 96 : 62));
                g.fill(new RoundRectangle2D.Double(4, 3.5, w - 8, (h - 3) * 0.32, 5, 5));
            }
        }

        if (kind == Kind.CORE || kind == Kind.TOUGH) {
            g.setColor(new Color(255, 255, 255, 60));
            g.setStroke(new BasicStroke(1.2f));
            double step = 7;
            double startX = w / 2 - (maxHp - 1) * step / 2;
            for (int i = 0; i < maxHp; i++) {
                double px = startX + i * step;
                double py = h - 6;
                if (i < hp) {
                    g.fill(new Ellipse2D.Double(px - 2, py - 2, 4, 4));
                } else {
                    g.draw(new Ellipse2D.Double(px - 2, py - 2, 4, 4));
                }
            }
        }

        if (hp < maxHp && kind != Kind.SOLID) {
            g.setColor(new Color(0, 0, 0, 110));
            g.setStroke(new BasicStroke(1.6f));
            g.drawLine((int) (w * 0.25), 5, (int) (w * 0.45), (int) (h - 6));
            g.drawLine((int) (w * 0.55), (int) (h - 7), (int) (w * 0.78), 6);
        }

        g.setColor(new Color(0, 0, 0, highContrast ? 190 : 90));
        g.setStroke(new BasicStroke(highContrast ? 2.2f : 1f));
        g.draw(shape);
        g.setStroke(new BasicStroke(1f));
    }

    /** A destroyed regenerating brick leaves a faint socket while it recharges. */
    private void renderGhost(Graphics2D g, double time) {
        if (respawnIn <= 0) {
            return;
        }
        double t = 1 - respawnIn / REGEN_DELAY;
        RoundRectangle2D shape = new RoundRectangle2D.Double(x + 1.5, y + 1.5, w - 3, h - 3, 7, 7);
        g.setColor(Palette.alpha(base, 0.06 + 0.14 * t));
        g.fill(shape);
        g.setColor(Palette.alpha(base, 0.25 + 0.45 * t));
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                1f, new float[] {3f, 5f}, (float) (time * 10 % 8)));
        g.draw(shape);
        g.setStroke(new BasicStroke(1f));

        g.setColor(Palette.alpha(base, 0.55));
        g.fill(new RoundRectangle2D.Double(x + 5, y + h - 6, (w - 10) * t, 2.5, 2, 2));
    }
}
