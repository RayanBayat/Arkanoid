package Arkanoid;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * The settings and about screens.
 *
 * <p>The settings page is driven entirely by {@link Settings#definitions()}, so
 * adding an option is a one-line change there and needs no work here.</p>
 */
public final class MenuScreen {

    private static final int W = Field.W;
    private static final int H = Field.H;

    private static final int ROW_X = 96;
    private static final int ROW_W = W - ROW_X * 2;
    private static final int ROW_H = 44;
    private static final int ROWS_Y = 232;

    private Settings.Tab tab = Settings.Tab.AUDIO;
    private int index;

    public void reset() {
        tab = Settings.Tab.AUDIO;
        index = 0;
    }

    public Settings.Tab tab() {
        return tab;
    }

    public void moveTab(int direction) {
        Settings.Tab[] tabs = Settings.Tab.values();
        tab = tabs[Math.floorMod(tab.ordinal() + direction, tabs.length)];
        index = 0;
    }

    public void move(int direction) {
        int n = Settings.forTab(tab).size();
        if (n > 0) {
            index = Math.floorMod(index + direction, n);
        }
    }

    public Settings.Def selected() {
        List<Settings.Def> rows = Settings.forTab(tab);
        if (rows.isEmpty()) {
            return null;
        }
        index = Math.max(0, Math.min(rows.size() - 1, index));
        return rows.get(index);
    }

    /** Selects whatever row is under the pointer. @return true if one was hit */
    public boolean pointAt(double mx, double my) {
        List<Settings.Def> rows = Settings.forTab(tab);
        for (int i = 0; i < rows.size(); i++) {
            int y = ROWS_Y + i * ROW_H;
            if (mx >= ROW_X && mx <= ROW_X + ROW_W && my >= y && my <= y + ROW_H - 4) {
                index = i;
                return true;
            }
        }
        return false;
    }

    /** Selects a tab from a click on the tab strip. @return true if one was hit */
    public boolean pointAtTab(double mx, double my) {
        if (my < 168 || my > 200) {
            return false;
        }
        Settings.Tab[] tabs = Settings.Tab.values();
        int total = 0;
        int[] widths = new int[tabs.length];
        for (int i = 0; i < tabs.length; i++) {
            widths[i] = 26 + tabs[i].title.length() * 9;
            total += widths[i];
        }
        int x = (W - total) / 2;
        for (int i = 0; i < tabs.length; i++) {
            if (mx >= x && mx <= x + widths[i]) {
                tab = tabs[i];
                index = 0;
                return true;
            }
            x += widths[i];
        }
        return false;
    }

    // ------------------------------------------------------------- settings

    public void drawSettings(Graphics2D g, Settings settings, Palette palette,
            String seedCode, int highScore, double time) {
        Ui.scrim(g, W, H, 226);
        Ui.glowText(g, "SETTINGS", Ui.BIG, Color.WHITE, palette.accent, W / 2, 122, Ui.CENTER);
        Ui.text(g, "changes save automatically", Ui.SMALL, new Color(150, 175, 205),
                W / 2, 148, Ui.CENTER);

        drawTabs(g, palette);

        List<Settings.Def> rows = Settings.forTab(tab);
        index = Math.max(0, Math.min(Math.max(0, rows.size() - 1), index));

        Ui.panel(g, ROW_X - 14, ROWS_Y - 14, ROW_W + 28, rows.size() * ROW_H + 22,
                new Color(10, 15, 26, 210), Palette.alpha(palette.accent, 0.22), 1.4);

        for (int i = 0; i < rows.size(); i++) {
            drawRow(g, settings, palette, rows.get(i), ROWS_Y + i * ROW_H, i == index,
                    seedCode, highScore, time);
        }

        drawHelp(g, palette, rows.isEmpty() ? null : rows.get(index));

        Ui.text(g, "UP DOWN  select        LEFT RIGHT  change        TAB  section",
                Ui.SMALL, new Color(160, 185, 215), W / 2, H - 74, Ui.CENTER);
        Ui.text(g, "ENTER  toggle or run        ESC  back", Ui.SMALL,
                new Color(160, 185, 215), W / 2, H - 50, Ui.CENTER);
    }

    private void drawTabs(Graphics2D g, Palette palette) {
        Settings.Tab[] tabs = Settings.Tab.values();
        int total = 0;
        int[] widths = new int[tabs.length];
        for (int i = 0; i < tabs.length; i++) {
            widths[i] = 26 + tabs[i].title.length() * 9;
            total += widths[i];
        }
        int x = (W - total) / 2;
        for (int i = 0; i < tabs.length; i++) {
            boolean active = tabs[i] == tab;
            if (active) {
                g.setColor(Palette.alpha(palette.accent, 0.20));
                g.fill(new RoundRectangle2D.Double(x, 168, widths[i], 32, 10, 10));
                g.setColor(palette.accent);
                g.setStroke(new BasicStroke(1.6f));
                g.draw(new RoundRectangle2D.Double(x, 168, widths[i], 32, 10, 10));
                g.setStroke(new BasicStroke(1f));
            }
            Ui.label(g, tabs[i].title, x + widths[i] / 2, 188,
                    active ? Color.WHITE : new Color(140, 165, 195), Ui.CENTER);
            x += widths[i];
        }
    }

    private void drawRow(Graphics2D g, Settings settings, Palette palette, Settings.Def def,
            int y, boolean active, String seedCode, int highScore, double time) {

        if (active) {
            g.setColor(Palette.alpha(palette.accent, 0.14));
            g.fill(new RoundRectangle2D.Double(ROW_X - 6, y, ROW_W + 12, ROW_H - 6, 9, 9));
            g.setColor(Palette.alpha(palette.accent, 0.55));
            g.setStroke(new BasicStroke(1.3f));
            g.draw(new RoundRectangle2D.Double(ROW_X - 6, y, ROW_W + 12, ROW_H - 6, 9, 9));
            g.setStroke(new BasicStroke(1f));
        }

        int textY = y + 26;
        Ui.text(g, def.title(), Ui.ROW, active ? Color.WHITE : new Color(198, 214, 236),
                ROW_X + 6, textY, Ui.LEFT);

        int valueRight = ROW_X + ROW_W - 8;

        switch (def.kind()) {
            case ACTION -> {
                String hint = switch (def.key()) {
                    case Settings.ACTION_SEED -> seedCode;
                    case Settings.ACTION_RESET_HIGH -> String.valueOf(highScore);
                    default -> "";
                };
                if (!hint.isEmpty()) {
                    Ui.text(g, hint, Ui.MONO, new Color(0xFFD166),
                            valueRight - 78, textY, Ui.RIGHT);
                }
                g.setColor(Palette.alpha(active ? palette.accent : palette.accentSoft, 0.9));
                g.setStroke(new BasicStroke(1.4f));
                g.draw(new RoundRectangle2D.Double(valueRight - 68, y + 9, 68, 24, 8, 8));
                g.setStroke(new BasicStroke(1f));
                Ui.label(g, "ENTER", valueRight - 34, y + 25,
                        active ? Color.WHITE : new Color(160, 185, 215), Ui.CENTER);
            }
            case TOGGLE -> {
                boolean on = settings.on(def.key());
                double tx = valueRight - 52;
                g.setColor(on ? Palette.alpha(palette.accent, 0.85) : new Color(255, 255, 255, 40));
                g.fill(new RoundRectangle2D.Double(tx, y + 11, 52, 22, 22, 22));
                g.setColor(Color.WHITE);
                double knob = on ? tx + 32 : tx + 4;
                g.fill(new Ellipse2D.Double(knob, y + 14, 16, 16));
                Ui.label(g, on ? "ON" : "OFF", (int) tx - 12, y + 27,
                        on ? Color.WHITE : new Color(150, 172, 200), Ui.RIGHT);
            }
            case PERCENT -> {
                double frac = (settings.get(def.key()) - def.min())
                        / (double) Math.max(1, def.max() - def.min());
                Ui.bar(g, valueRight - 150, y + 17, 100, 9, frac,
                        Palette.alpha(palette.accent, 0.9));
                Ui.text(g, settings.valueText(def.key()), Ui.MONO, Color.WHITE,
                        valueRight, textY, Ui.RIGHT);
            }
            default -> Ui.text(g, settings.valueText(def.key()), Ui.MONO,
                    active ? Color.WHITE : new Color(198, 214, 236),
                    valueRight, textY, Ui.RIGHT);
        }

        if (active && def.kind() != Settings.Kind.ACTION) {
            double pulse = 0.55 + 0.45 * Math.sin(time * 6);
            drawChevron(g, ROW_X + ROW_W - 172, y + ROW_H / 2.0 - 3, -1,
                    Palette.alpha(palette.accent, pulse));
            drawChevron(g, ROW_X + ROW_W + 4, y + ROW_H / 2.0 - 3, 1,
                    Palette.alpha(palette.accent, pulse));
        }
    }

    private void drawChevron(Graphics2D g, double x, double y, int dir, Color c) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(x + (dir > 0 ? 0 : 6), y - 5);
        p.lineTo(x + (dir > 0 ? 6 : 0), y);
        p.lineTo(x + (dir > 0 ? 0 : 6), y + 5);
        g.setColor(c);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(p);
        g.setStroke(new BasicStroke(1f));
    }

    private void drawHelp(Graphics2D g, Palette palette, Settings.Def def) {
        if (def == null) {
            return;
        }
        int y = H - 190;
        Ui.panel(g, ROW_X - 14, y, ROW_W + 28, 76,
                new Color(8, 12, 22, 200), Palette.alpha(palette.accentSoft, 0.20), 1.2);
        Ui.label(g, def.title(), W / 2, y + 24, Palette.alpha(palette.accentSoft, 0.95),
                Ui.CENTER);
        Ui.wrapped(g, def.help(), Ui.SMALL, new Color(178, 198, 222),
                ROW_X, y + 46, ROW_W, 19);
    }

    // ---------------------------------------------------------------- about

    public void drawAbout(Graphics2D g, Palette palette, String seedCode, double time) {
        Ui.scrim(g, W, H, 230);

        Ui.glowText(g, "ARKANOID", Ui.BIG, Color.WHITE, palette.accent, W / 2, 108, Ui.CENTER);
        Ui.label(g, "R E S O N A N C E", W / 2, 132,
                Palette.alpha(palette.accentSoft, 0.95), Ui.CENTER);

        int y = Ui.wrapped(g,
                "A brick-breaker where the content is generated rather than authored. "
                        + "Levels, colour, music and run structure are all produced at runtime "
                        + "from a single seed. Pure Java and AWT: no engine, no libraries, and "
                        + "no asset files of any kind.",
                Ui.SMALL, new Color(186, 205, 228), 110, 170, W - 220, 21);

        y += 14;
        Ui.label(g, "HOW IT IS BUILT", W / 2, y, Palette.alpha(palette.accent, 0.95), Ui.CENTER);
        y += 22;

        String[][] tech = {
            {"Levels", "Wave Function Collapse solves a structural grid, then mirrors it."},
            {"Colour", "Palettes generated in Oklab / OkLCH, so hues always agree."},
            {"Sound", "A live subtractive synth: PolyBLEP oscillators, ADSR, filter, delay."},
            {"Music", "Key and Markov-generated chords per level; bricks are tuned to the scale."},
            {"Difficulty", "A Kalman-style skill estimate aims each level just above you."},
            {"Physics", "Swept continuous collision against a lattice broadphase."},
            {"Render", "Baked backdrop and brick sprites, plus an alpha-channel bloom pass."},
        };
        for (String[] row : tech) {
            Ui.text(g, row[0], Ui.ROW, Palette.alpha(palette.accentSoft, 0.95), 150, y, Ui.LEFT);
            Ui.text(g, row[1], Ui.SMALL, new Color(176, 196, 220), 268, y, Ui.LEFT);
            y += 25;
        }

        y += 14;
        Ui.label(g, "CONTROLS", W / 2, y, Palette.alpha(palette.accent, 0.95), Ui.CENTER);
        y += 22;

        String[][] keys = {
            {"Mouse / <- ->", "Move the paddle"},
            {"Space", "Launch the ball, fire the laser"},
            {"1 2 3", "Draft a relic"},
            {"P", "Pause"},
            {"M", "Mute"},
            {"B", "Toggle bloom"},
            {"R", "Reroll seed / replay seed"},
            {"Esc", "Back, or quit from the title"},
        };
        int col = 0;
        int startY = y;
        for (String[] row : keys) {
            int cx = col == 0 ? 150 : 470;
            int cy = startY + (col == 0 ? y - startY : y - startY);
            Ui.text(g, row[0], Ui.MONO, new Color(0xFFD166), cx, cy, Ui.LEFT);
            Ui.text(g, row[1], Ui.SMALL, new Color(176, 196, 220), cx + 130, cy, Ui.LEFT);
            if (col == 0) {
                col = 1;
            } else {
                col = 0;
                y += 24;
            }
        }
        if (col == 1) {
            y += 24;
        }

        y += 16;
        Ui.panel(g, 110, y, W - 220, 62, new Color(8, 12, 22, 200),
                Palette.alpha(palette.accentSoft, 0.20), 1.2);
        Ui.label(g, "SEEDS", W / 2, y + 22, Palette.alpha(palette.accentSoft, 0.95), Ui.CENTER);
        Ui.text(g, "This run is " + seedCode
                        + " - the same code always rebuilds the same levels and relics.",
                Ui.SMALL, new Color(178, 198, 222), W / 2, y + 46, Ui.CENTER);

        Ui.text(g, "Originally a Programming 2 final project by Rayan Bayat.",
                Ui.TINY, new Color(140, 165, 195), W / 2, H - 96, Ui.CENTER);
        Ui.text(g, "Reimagined as ARKANOID // RESONANCE.", Ui.TINY,
                new Color(140, 165, 195), W / 2, H - 76, Ui.CENTER);

        if (Math.sin(time * 3.2) > -0.4) {
            Ui.text(g, "ESC or ENTER  back", Ui.MID, new Color(210, 232, 255),
                    W / 2, H - 40, Ui.CENTER);
        }
    }
}
