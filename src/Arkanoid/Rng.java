package Arkanoid;

/**
 * xoshiro256** — a small, fast, high-quality PRNG.
 *
 * <p>{@link java.util.Random} is a 48-bit LCG with visible structure in the low
 * bits, and its stream cannot be forked. Everything generative in this game
 * (levels, palettes, music, relic offers) is derived from one run seed, so the
 * generator has to be deterministic, splittable, and good enough that
 * correlations do not show up as visible artefacts in a level layout.</p>
 *
 * <p>A run is fully reproducible from its seed: same seed, same levels, same
 * palettes, same key, same relic offers.</p>
 */
public final class Rng {

    private long s0;
    private long s1;
    private long s2;
    private long s3;

    private double spareGauss;
    private boolean hasSpare;

    public Rng(long seed) {
        setSeed(seed);
    }

    public void setSeed(long seed) {
        // SplitMix64 scrambles a single seed into four well-distributed words.
        long z = seed;
        s0 = splitMix(z += 0x9E3779B97F4A7C15L);
        s1 = splitMix(z += 0x9E3779B97F4A7C15L);
        s2 = splitMix(z += 0x9E3779B97F4A7C15L);
        s3 = splitMix(z + 0x9E3779B97F4A7C15L);
        if ((s0 | s1 | s2 | s3) == 0) {
            s0 = 0x9E3779B97F4A7C15L;
        }
        hasSpare = false;
    }

    private static long splitMix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public long nextLong() {
        long result = Long.rotateLeft(s1 * 5, 7) * 9;
        long t = s1 << 17;
        s2 ^= s0;
        s3 ^= s1;
        s1 ^= s2;
        s0 ^= s3;
        s2 ^= t;
        s3 = Long.rotateLeft(s3, 45);
        return result;
    }

    /** Uniform in [0, bound). */
    public int nextInt(int bound) {
        if (bound <= 0) {
            return 0;
        }
        return (int) Long.remainderUnsigned(nextLong() >>> 1, bound);
    }

    /** Uniform in [0, 1). */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    public boolean chance(double p) {
        return nextDouble() < p;
    }

    /** Uniform in [lo, hi). */
    public double range(double lo, double hi) {
        return lo + nextDouble() * (hi - lo);
    }

    /** Uniform integer in [lo, hi] inclusive. */
    public int rangeInt(int lo, int hi) {
        return hi <= lo ? lo : lo + nextInt(hi - lo + 1);
    }

    public <T> T pick(T[] items) {
        return items[nextInt(items.length)];
    }

    /** Standard normal, via Box-Muller with the second sample cached. */
    public double gauss() {
        if (hasSpare) {
            hasSpare = false;
            return spareGauss;
        }
        double u;
        double v;
        double s;
        do {
            u = nextDouble() * 2 - 1;
            v = nextDouble() * 2 - 1;
            s = u * u + v * v;
        } while (s >= 1 || s == 0);
        double f = Math.sqrt(-2.0 * Math.log(s) / s);
        spareGauss = v * f;
        hasSpare = true;
        return u * f;
    }

    /**
     * A new independent stream. Used so that, say, re-rolling the palette cannot
     * shift the level layout: each subsystem draws from its own child stream.
     */
    public Rng fork(long salt) {
        return new Rng(nextLong() ^ splitMix(salt));
    }

    /** Weighted choice over parallel arrays. */
    public int weighted(double[] weights) {
        double total = 0;
        for (double w : weights) {
            total += Math.max(0, w);
        }
        if (total <= 0) {
            return 0;
        }
        double roll = nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            roll -= Math.max(0, weights[i]);
            if (roll < 0) {
                return i;
            }
        }
        return weights.length - 1;
    }

    /**
     * Seeds are drawn from a 35-bit space so they fit exactly seven symbols of a
     * 32-character alphabet. That makes {@link #seedCode} reversible: a player
     * can read a code off the screen, type it back, and get the same run.
     */
    public static final long SEED_SPACE = 1L << 35;

    /** No I, O, 0 or 1: those are the pairs people mistype. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /** Renders a seed as a typable code, e.g. {@code "K7M-2QXR"}. */
    public static String seedCode(long seed) {
        long v = Math.floorMod(seed, SEED_SPACE);
        StringBuilder sb = new StringBuilder(8);
        for (int i = 6; i >= 0; i--) {
            sb.append(ALPHABET.charAt((int) ((v >>> (5 * i)) & 31)));
            if (i == 4) {
                sb.append('-');
            }
        }
        return sb.toString();
    }

    /**
     * Parses a seed code.
     *
     * @return the seed, or -1 if the text is not a valid code
     */
    public static long parseSeedCode(String text) {
        if (text == null) {
            return -1;
        }
        String clean = text.trim().toUpperCase(java.util.Locale.ROOT).replace("-", "");
        if (clean.length() != 7) {
            return -1;
        }
        long v = 0;
        for (int i = 0; i < clean.length(); i++) {
            int d = ALPHABET.indexOf(clean.charAt(i));
            if (d < 0) {
                return -1;
            }
            v = (v << 5) | d;
        }
        return v;
    }

    /** A fresh seed inside the codeable space. */
    public long nextSeed() {
        return Math.floorMod(nextLong(), SEED_SPACE);
    }
}
