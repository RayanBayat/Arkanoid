package Arkanoid;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Every player-facing option, its metadata, and its persistence.
 *
 * <p>Options are defined as data rather than as fields with a hand-written
 * widget each, so the settings screen can render and edit any of them without
 * knowing what they mean, and saving is just writing the map. Every value is an
 * {@code int} — booleans are 0/1 and choices are indices — which keeps both the
 * generic UI and the properties file trivial.</p>
 *
 * <p>Values live in {@code settings.properties} next to the game.</p>
 */
public final class Settings {

    public enum Tab {
        AUDIO("AUDIO"),
        VIDEO("VIDEO"),
        GAMEPLAY("GAMEPLAY"),
        ACCESS("ACCESS"),
        DATA("RUN & DATA");

        public final String title;

        Tab(String title) {
            this.title = title;
        }
    }

    public enum Kind {
        /** On or off. */
        TOGGLE,
        /** An integer shown with a percent sign and a fill bar. */
        PERCENT,
        /** A plain integer. */
        NUMBER,
        /** One of a fixed list. */
        CHOICE,
        /** Does something immediately instead of holding a value. */
        ACTION
    }

    /** One option's definition. */
    public record Def(String key, Tab tab, String title, String help, Kind kind,
            int fallback, int min, int max, int step, String[] choices) {

        static Def toggle(String key, Tab tab, String title, boolean on, String help) {
            return new Def(key, tab, title, help, Kind.TOGGLE, on ? 1 : 0, 0, 1, 1, null);
        }

        static Def percent(String key, Tab tab, String title, int def, int min, int max,
                int step, String help) {
            return new Def(key, tab, title, help, Kind.PERCENT, def, min, max, step, null);
        }

        static Def number(String key, Tab tab, String title, int def, int min, int max,
                String help) {
            return new Def(key, tab, title, help, Kind.NUMBER, def, min, max, 1, null);
        }

        static Def choice(String key, Tab tab, String title, int def, String help,
                String... choices) {
            return new Def(key, tab, title, help, Kind.CHOICE, def, 0, choices.length - 1, 1,
                    choices);
        }

        static Def action(String key, Tab tab, String title, String help) {
            return new Def(key, tab, title, help, Kind.ACTION, 0, 0, 0, 0, null);
        }
    }

    // ---- keys ---------------------------------------------------------------

    public static final String MASTER = "audio.master";
    public static final String MUSIC = "audio.music";
    public static final String SFX = "audio.sfx";
    public static final String MUTE = "audio.mute";

    public static final String BLOOM = "video.bloom";
    public static final String BLOOM_STRENGTH = "video.bloomStrength";
    public static final String SHAKE = "video.shake";
    public static final String PARTICLES = "video.particles";
    public static final String TRAIL = "video.trail";
    public static final String STATS = "video.stats";

    public static final String CONTROL = "play.control";
    public static final String SENSITIVITY = "play.sensitivity";
    public static final String LIVES = "play.lives";
    public static final String DILATION = "play.dilation";
    public static final String ADAPTIVE = "play.adaptive";
    public static final String DIFFICULTY = "play.difficulty";
    public static final String DROPS = "play.drops";

    public static final String REDUCE_MOTION = "access.reduceMotion";
    public static final String FLASH = "access.flash";
    public static final String CONTRAST = "access.contrast";
    public static final String COLOUR_SAFE = "access.colourSafe";

    public static final String ACTION_SEED = "data.seed";
    public static final String ACTION_RESET_HIGH = "data.resetHigh";
    public static final String ACTION_DEFAULTS = "data.defaults";

    public static final int CONTROL_BOTH = 0;
    public static final int CONTROL_KEYS = 1;
    public static final int CONTROL_MOUSE = 2;

    private static final Path FILE = Paths.get("settings.properties");

    private static final List<Def> DEFS = List.of(
            Def.percent(MASTER, Tab.AUDIO, "Master volume", 85, 0, 100, 5,
                    "Overall output level for music and effects."),
            Def.percent(MUSIC, Tab.AUDIO, "Music volume", 80, 0, 100, 5,
                    "The generated score: pad, bass, arpeggio and drums."),
            Def.percent(SFX, Tab.AUDIO, "Effects volume", 90, 0, 100, 5,
                    "Bricks, paddle, capsules and explosions. Bricks are tuned to the key."),
            Def.toggle(MUTE, Tab.AUDIO, "Mute everything", false,
                    "Silence the whole game. Also toggled in play with M."),

            Def.toggle(BLOOM, Tab.VIDEO, "Bloom", true,
                    "Soft glow around bright things. Costs about 5 ms a frame; B toggles it in play."),
            Def.percent(BLOOM_STRENGTH, Tab.VIDEO, "Bloom strength", 85, 0, 150, 5,
                    "How much glow bright pixels spread."),
            Def.percent(SHAKE, Tab.VIDEO, "Screen shake", 100, 0, 150, 10,
                    "Camera kick on impacts and explosions."),
            Def.choice(PARTICLES, Tab.VIDEO, "Particles", 2,
                    "Sparks and debris. Lower settings help on slow machines.",
                    "Off", "Low", "Normal", "High"),
            Def.toggle(TRAIL, Tab.VIDEO, "Ball trail", true,
                    "Motion trail behind each ball."),
            Def.toggle(STATS, Tab.VIDEO, "Show performance stats", false,
                    "Frame time, bloom cost and live object counts."),

            Def.choice(CONTROL, Tab.GAMEPLAY, "Control", CONTROL_BOTH,
                    "Which inputs move the paddle.",
                    "Mouse and keys", "Keyboard only", "Mouse only"),
            Def.percent(SENSITIVITY, Tab.GAMEPLAY, "Mouse sensitivity", 100, 50, 150, 5,
                    "How far the paddle travels for a given mouse movement."),
            Def.number(LIVES, Tab.GAMEPLAY, "Starting lives", 3, 1, 9,
                    "Balls you begin a run with, before any relics."),
            Def.toggle(DILATION, Tab.GAMEPLAY, "Time dilation", true,
                    "Slow time while a ball falls past the clutch line, spending the meter."),
            Def.toggle(ADAPTIVE, Tab.GAMEPLAY, "Adaptive difficulty", true,
                    "Estimate your skill each level and aim the next one just above it."),
            Def.choice(DIFFICULTY, Tab.GAMEPLAY, "Fixed difficulty", 2,
                    "Used only when adaptive difficulty is off.",
                    "Relaxed", "Steady", "Pressing", "Severe", "Ruthless"),
            Def.percent(DROPS, Tab.GAMEPLAY, "Capsule drop rate", 20, 0, 50, 5,
                    "Chance that breaking a brick drops a power-up capsule."),

            Def.toggle(REDUCE_MOTION, Tab.ACCESS, "Reduce motion", false,
                    "Turns off screen shake and flashes and thins out particles."),
            Def.toggle(FLASH, Tab.ACCESS, "Screen flashes", true,
                    "Full-screen colour flashes on explosions and lost balls."),
            Def.toggle(CONTRAST, Tab.ACCESS, "High contrast bricks", false,
                    "Brighter brick faces with heavier outlines."),
            Def.toggle(COLOUR_SAFE, Tab.ACCESS, "Colour-safe palettes", false,
                    "Restrict generated palettes to a blue and orange axis, "
                            + "which stays distinguishable for red-green colour blindness."),

            Def.action(ACTION_SEED, Tab.DATA, "Roll a new seed",
                    "A run is fully reproducible from its seed: same levels, palettes, key and relics."),
            Def.action(ACTION_RESET_HIGH, Tab.DATA, "Reset high score",
                    "Clears the saved best score in the Lastscore file."),
            Def.action(ACTION_DEFAULTS, Tab.DATA, "Restore defaults",
                    "Put every setting on this screen back to its original value."));

    private final Map<String, Integer> values = new LinkedHashMap<>();

    public Settings() {
        resetToDefaults();
        load();
    }

    public static List<Def> definitions() {
        return DEFS;
    }

    public static List<Def> forTab(Tab tab) {
        List<Def> out = new ArrayList<>();
        for (Def d : DEFS) {
            if (d.tab() == tab) {
                out.add(d);
            }
        }
        return out;
    }

    public static Def def(String key) {
        for (Def d : DEFS) {
            if (d.key().equals(key)) {
                return d;
            }
        }
        throw new IllegalArgumentException("unknown setting " + key);
    }

    // ---- access -------------------------------------------------------------

    public int get(String key) {
        Integer v = values.get(key);
        return v == null ? def(key).fallback() : v;
    }

    public boolean on(String key) {
        return get(key) != 0;
    }

    /** A percentage setting as a 0..1+ multiplier. */
    public double factor(String key) {
        return get(key) / 100.0;
    }

    public void set(String key, int value) {
        Def d = def(key);
        if (d.kind() == Kind.ACTION) {
            return;
        }
        values.put(key, Math.max(d.min(), Math.min(d.max(), value)));
    }

    /** Moves a setting by one step, wrapping toggles and clamping ranges. */
    public void nudge(String key, int direction) {
        Def d = def(key);
        switch (d.kind()) {
            case TOGGLE -> set(key, get(key) == 0 ? 1 : 0);
            case CHOICE -> {
                int n = d.choices().length;
                set(key, Math.floorMod(get(key) + direction, n));
            }
            case PERCENT, NUMBER -> set(key, get(key) + direction * d.step());
            default -> { }
        }
    }

    /** Human-readable current value, for the settings rows. */
    public String valueText(String key) {
        Def d = def(key);
        return switch (d.kind()) {
            case TOGGLE -> on(key) ? "ON" : "OFF";
            case PERCENT -> get(key) + "%";
            case NUMBER -> String.valueOf(get(key));
            case CHOICE -> d.choices()[Math.max(0, Math.min(d.choices().length - 1, get(key)))];
            case ACTION -> "";
        };
    }

    public void resetToDefaults() {
        values.clear();
        for (Def d : DEFS) {
            if (d.kind() != Kind.ACTION) {
                values.put(d.key(), d.fallback());
            }
        }
    }

    // ---- persistence --------------------------------------------------------

    public void load() {
        if (!Files.exists(FILE)) {
            return;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(in);
        } catch (IOException ignored) {
            return;
        }
        for (Def d : DEFS) {
            String raw = p.getProperty(d.key());
            if (raw == null) {
                continue;
            }
            try {
                set(d.key(), Integer.parseInt(raw.trim()));
            } catch (NumberFormatException ignored) {
                // Leave the default in place for anything hand-edited badly.
            }
        }
    }

    public void save() {
        Properties p = new Properties();
        for (Map.Entry<String, Integer> e : values.entrySet()) {
            p.setProperty(e.getKey(), String.valueOf(e.getValue()));
        }
        try (OutputStream out = Files.newOutputStream(FILE)) {
            p.store(out, "ARKANOID // RESONANCE settings");
        } catch (IOException ignored) {
            // A read-only directory should not stop the player changing settings.
        }
    }
}
