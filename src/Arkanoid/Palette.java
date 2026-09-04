package Arkanoid;

import java.awt.Color;

/**
 * Generated colour, built in the Oklab / OkLCH perceptual space.
 *
 * <p>Picking random RGB gives you the muddy, uneven colours the original game
 * had: {@code new Color(random * 0x1000000)} produces neon yellows next to
 * near-black browns because sRGB is not perceptually uniform. Oklab is, so
 * holding lightness and chroma constant while rotating hue gives a set of
 * colours that genuinely look like they belong to the same family — which is
 * what makes a procedurally coloured level read as designed rather than
 * random.</p>
 *
 * @see <a href="https://bottosson.github.io/posts/oklab/">Björn Ottosson, Oklab</a>
 */
public final class Palette {

    /** How the accent hues are spread around the base hue. */
    private enum Scheme { ANALOGOUS, TRIADIC, SPLIT_COMPLEMENT, TETRADIC }

    public final Color bgTop;
    public final Color bgBottom;
    public final Color nebula;
    public final Color wall;
    public final Color accent;
    public final Color accentSoft;
    public final Color ball;
    public final Color paddleTop;
    public final Color paddleBottom;
    /** One colour per brick tier, brightest last. */
    public final Color[] tiers;
    public final String name;

    private Palette(Color bgTop, Color bgBottom, Color nebula, Color wall, Color accent,
            Color accentSoft, Color ball, Color paddleTop, Color paddleBottom,
            Color[] tiers, String name) {
        this.bgTop = bgTop;
        this.bgBottom = bgBottom;
        this.nebula = nebula;
        this.wall = wall;
        this.accent = accent;
        this.accentSoft = accentSoft;
        this.ball = ball;
        this.paddleTop = paddleTop;
        this.paddleBottom = paddleBottom;
        this.tiers = tiers;
        this.name = name;
    }

    private static final String[] NAMES = {
        "EMBER", "CYAN DRIFT", "VIOLET HOUR", "SODIUM", "DEEP FIELD", "AURORA",
        "MAGMA", "GLACIER", "ORCHID", "SIGNAL", "TWILIGHT", "REACTOR",
    };

    /**
     * Builds a palette for a level.
     *
     * @param rng       stream to draw from
     * @param tierCount how many brick tiers need colours
     */
    public static Palette generate(Rng rng, int tierCount) {
        return generate(rng, tierCount, false);
    }

    /**
     * @param colourSafe restrict hues to a blue/orange axis, which stays
     *                   distinguishable under red-green colour blindness
     */
    public static Palette generate(Rng rng, int tierCount, boolean colourSafe) {
        double baseHue = rng.nextDouble() * 360;
        Scheme scheme = Scheme.values()[rng.nextInt(Scheme.values().length)];
        if (colourSafe) {
            // Anchor on blue and walk towards orange: the one hue pair that
            // survives every common form of colour vision deficiency.
            baseHue = 250 + rng.range(-12, 12);
            scheme = Scheme.ANALOGOUS;
        }

        double spread = colourSafe ? -118 : switch (scheme) {
            case ANALOGOUS -> rng.range(28, 55);
            case TRIADIC -> 120;
            case SPLIT_COMPLEMENT -> rng.range(140, 165);
            case TETRADIC -> 90;
        };

        // Bricks walk the hue wheel; lightness rises with tier so higher rows
        // read as "hotter" without any two tiers colliding.
        Color[] tiers = new Color[Math.max(1, tierCount)];
        for (int i = 0; i < tiers.length; i++) {
            double t = tiers.length == 1 ? 0.5 : i / (double) (tiers.length - 1);
            double hue = baseHue + spread * t * (scheme == Scheme.ANALOGOUS ? 1.6 : 1.0);
            double light = 0.62 + 0.20 * t;
            double chroma = 0.13 + 0.045 * Math.sin(Math.PI * t);
            tiers[i] = oklch(light, chroma, hue);
        }

        double accentHue = colourSafe ? 62 : baseHue + 180 + rng.range(-24, 24);

        // The background stays very dark and slightly desaturated so the bloom
        // from the bricks is the brightest thing on screen.
        Color bgTop = oklch(0.20, 0.045, baseHue + 200);
        Color bgBottom = oklch(0.09, 0.030, baseHue + 215);
        Color nebula = oklch(0.34, 0.075, baseHue + rng.range(150, 250));
        Color wall = oklch(0.32, 0.055, baseHue + 190);
        Color accent = oklch(0.86, 0.155, accentHue);
        Color accentSoft = oklch(0.62, 0.110, accentHue);
        Color ball = oklch(0.95, 0.055, accentHue + rng.range(-30, 30));
        Color paddleTop = oklch(0.88, 0.115, accentHue + 12);
        Color paddleBottom = oklch(0.52, 0.145, accentHue - 22);

        String name = NAMES[(int) (baseHue / 360.0 * NAMES.length) % NAMES.length];

        return new Palette(bgTop, bgBottom, nebula, wall, accent, accentSoft, ball,
                paddleTop, paddleBottom, tiers, name);
    }

    // ------------------------------------------------------------ colour maths

    /**
     * OkLCH to sRGB.
     *
     * @param l lightness, 0..1
     * @param c chroma, roughly 0..0.33
     * @param hDegrees hue angle in degrees
     */
    public static Color oklch(double l, double c, double hDegrees) {
        double h = Math.toRadians(hDegrees);
        return oklab(l, c * Math.cos(h), c * Math.sin(h));
    }

    /** Oklab to sRGB, gamut-clamped. */
    public static Color oklab(double lightness, double a, double b) {
        double l = lightness + 0.3963377774 * a + 0.2158037573 * b;
        double m = lightness - 0.1055613458 * a - 0.0638541728 * b;
        double s = lightness - 0.0894841775 * a - 1.2914855480 * b;

        l = l * l * l;
        m = m * m * m;
        s = s * s * s;

        double r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s;
        double g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s;
        double bb = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s;

        return new Color(toSrgb(r), toSrgb(g), toSrgb(bb));
    }

    private static int toSrgb(double linear) {
        double v = linear <= 0.0031308
                ? 12.92 * linear
                : 1.055 * Math.pow(Math.max(0, linear), 1 / 2.4) - 0.055;
        return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
    }

    /** Same colour at a new alpha. */
    public static Color alpha(Color c, double a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(),
                (int) Math.round(Math.max(0, Math.min(1, a)) * 255));
    }

    /** Perceptually reasonable brighten/darken by scaling in linear light. */
    public static Color scale(Color c, double factor) {
        return new Color(
                clamp255(c.getRed() * factor),
                clamp255(c.getGreen() * factor),
                clamp255(c.getBlue() * factor));
    }

    /** Linear blend between two colours. */
    public static Color mix(Color a, Color b, double t) {
        double u = Math.max(0, Math.min(1, t));
        return new Color(
                clamp255(a.getRed() + (b.getRed() - a.getRed()) * u),
                clamp255(a.getGreen() + (b.getGreen() - a.getGreen()) * u),
                clamp255(a.getBlue() + (b.getBlue() - a.getBlue()) * u));
    }

    private static int clamp255(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }
}
