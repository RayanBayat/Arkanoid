package Arkanoid;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.geom.RoundRectangle2D;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Shared text and panel drawing, plus the resolved font set.
 *
 * <p>Both the game HUD and the menu screens draw the same kinds of thing, so the
 * primitives live here rather than being duplicated per screen.</p>
 */
public final class Ui {

    public static final int LEFT = 0;
    public static final int CENTER = 1;
    public static final int RIGHT = 2;

    private static final String UI_FAMILY =
            pickFamily("Bahnschrift", "Segoe UI Semibold", "Segoe UI", "Trebuchet MS");
    private static final String NUM_FAMILY =
            pickFamily("Consolas", "Lucida Console", "DejaVu Sans Mono");

    public static final Font HUGE = new Font(UI_FAMILY, Font.BOLD, 74);
    public static final Font BIG = new Font(UI_FAMILY, Font.BOLD, 40);
    public static final Font MID = new Font(UI_FAMILY, Font.BOLD, 23);
    public static final Font SMALL = new Font(UI_FAMILY, Font.PLAIN, 15);
    public static final Font TINY = new Font(UI_FAMILY, Font.PLAIN, 13);
    public static final Font LABEL = new Font(UI_FAMILY, Font.BOLD, 11);
    public static final Font ROW = new Font(UI_FAMILY, Font.BOLD, 17);
    public static final Font VALUE = new Font(NUM_FAMILY, Font.BOLD, 25);
    public static final Font BADGE = new Font(NUM_FAMILY, Font.BOLD, 14);
    public static final Font POPUP = new Font(NUM_FAMILY, Font.BOLD, 17);
    public static final Font MONO = new Font(NUM_FAMILY, Font.BOLD, 16);

    private Ui() {
    }

    private static String pickFamily(String... names) {
        try {
            Set<String> have = new HashSet<>(Arrays.asList(GraphicsEnvironment
                    .getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            for (String n : names) {
                if (have.contains(n)) {
                    return n;
                }
            }
        } catch (Throwable ignored) {
            // Fall through to the logical family.
        }
        return Font.SANS_SERIF;
    }

    /** Draws a string with a soft drop shadow. */
    public static void text(Graphics2D g, String s, Font f, Color c, int x, int y, int align) {
        g.setFont(f);
        int w = g.getFontMetrics().stringWidth(s);
        int px = align == CENTER ? x - w / 2 : align == RIGHT ? x - w : x;
        g.setColor(new Color(0, 0, 0, 150));
        g.drawString(s, px + 2, y + 2);
        g.setColor(c);
        g.drawString(s, px, y);
    }

    /** Neon text: the string stamped repeatedly at low alpha, then solid on top. */
    public static void glowText(Graphics2D g, String s, Font f, Color core, Color glow,
            int x, int y, int align) {
        g.setFont(f);
        int w = g.getFontMetrics().stringWidth(s);
        int px = align == CENTER ? x - w / 2 : align == RIGHT ? x - w : x;
        for (int r = 8; r >= 2; r -= 3) {
            g.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(),
                    Math.max(8, glow.getAlpha() / 9)));
            for (int dx = -r; dx <= r; dx += r) {
                for (int dy = -r; dy <= r; dy += r) {
                    if (dx != 0 || dy != 0) {
                        g.drawString(s, px + dx, y + dy);
                    }
                }
            }
        }
        g.setColor(core);
        g.drawString(s, px, y);
    }

    /** Small letter-spaced caps. */
    public static void label(Graphics2D g, String s, int x, int y, Color c, int align) {
        g.setFont(LABEL);
        FontMetrics fm = g.getFontMetrics();
        final int tracking = 3;
        int w = -tracking;
        for (int i = 0; i < s.length(); i++) {
            w += fm.charWidth(s.charAt(i)) + tracking;
        }
        int px = align == CENTER ? x - w / 2 : align == RIGHT ? x - w : x;
        g.setColor(c);
        for (int i = 0; i < s.length(); i++) {
            g.drawString(String.valueOf(s.charAt(i)), px, y);
            px += fm.charWidth(s.charAt(i)) + tracking;
        }
    }

    /**
     * Word-wrapped, centred text.
     *
     * @return the y just below the last line drawn
     */
    public static int wrapped(Graphics2D g, String s, Font f, Color c,
            int x, int y, int w, int lineHeight) {
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        StringBuilder line = new StringBuilder();
        int cy = y;
        for (String word : s.split(" ")) {
            String probe = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(probe) > w && line.length() > 0) {
                text(g, line.toString(), f, c, x + w / 2, cy, CENTER);
                line = new StringBuilder(word);
                cy += lineHeight;
            } else {
                line = new StringBuilder(probe);
            }
        }
        if (line.length() > 0) {
            text(g, line.toString(), f, c, x + w / 2, cy, CENTER);
            cy += lineHeight;
        }
        return cy;
    }

    public static void scrim(Graphics2D g, int w, int h, int alpha) {
        g.setColor(new Color(3, 5, 12, alpha));
        g.fillRect(0, 0, w, h);
    }

    /** A rounded panel with a border. */
    public static void panel(Graphics2D g, double x, double y, double w, double h,
            Color fill, Color border, double borderWidth) {
        RoundRectangle2D r = new RoundRectangle2D.Double(x, y, w, h, 14, 14);
        g.setColor(fill);
        g.fill(r);
        if (border != null) {
            g.setStroke(new BasicStroke((float) borderWidth));
            g.setColor(border);
            g.draw(r);
            g.setStroke(new BasicStroke(1f));
        }
    }

    /** A horizontal fill bar, used for percentage settings. */
    public static void bar(Graphics2D g, double x, double y, double w, double h,
            double fraction, Color colour) {
        g.setColor(new Color(255, 255, 255, 34));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, h, h));
        double f = Math.max(0, Math.min(1, fraction));
        if (f > 0) {
            g.setColor(colour);
            g.fill(new RoundRectangle2D.Double(x, y, Math.max(h, w * f), h, h, h));
        }
    }
}
