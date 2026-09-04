package Arkanoid;

/**
 * Wave Function Collapse — a constraint solver used here as a level generator.
 *
 * <p>Hand-authored grids give you exactly as many levels as you were willing to
 * type. Pure random fill gives you infinite levels that all look like noise. WFC
 * sits in between: you declare which tiles are allowed to touch which, and it
 * searches for a grid where every adjacency is legal. The output is infinite and
 * never repeats, but it is <em>structured</em>, because the constraints encode
 * what "structured" means.</p>
 *
 * <p>The tile alphabet here is deliberately structural rather than cosmetic —
 * {@code VOID}, {@code EDGE}, {@code FILL}, {@code CORE}, {@code SPUR}. The rule
 * that {@code VOID} may never touch {@code FILL} is what forces every solid mass
 * to be wrapped in an edge, and the rule that {@code CORE} may only touch
 * {@code FILL} or {@code CORE} is what buries the armoured bricks in the middle
 * of a shape instead of leaving them stranded in open space. {@link LevelGen}
 * then paints materials onto that skeleton.</p>
 *
 * <p>Standard algorithm: observe the lowest-entropy cell, collapse it by weight,
 * propagate arc consistency to a fixed point, repeat. A contradiction restarts
 * the whole grid with a fresh stream, which is cheap at this size.</p>
 */
public final class Wfc {

    public static final int VOID = 0;
    public static final int EDGE = 1;
    public static final int FILL = 2;
    public static final int CORE = 3;
    public static final int SPUR = 4;
    public static final int TILE_COUNT = 5;

    /**
     * {@code COMPATIBLE[a][b]} is true when tile {@code a} may sit next to tile
     * {@code b}. The relation is symmetric, so one table covers both axes.
     */
    private static final boolean[][] COMPATIBLE = new boolean[TILE_COUNT][TILE_COUNT];

    static {
        allow(VOID, VOID);
        allow(VOID, EDGE);
        allow(VOID, SPUR);
        allow(EDGE, EDGE);
        allow(EDGE, FILL);
        allow(EDGE, SPUR);
        allow(FILL, FILL);
        allow(FILL, CORE);
        allow(CORE, CORE);
        allow(SPUR, SPUR);
        // Deliberately absent: VOID-FILL, VOID-CORE, EDGE-CORE, SPUR-FILL, SPUR-CORE.
    }

    private static void allow(int a, int b) {
        COMPATIBLE[a][b] = true;
        COMPATIBLE[b][a] = true;
    }

    private final int w;
    private final int h;
    private final double[] weights;

    /** Bitmask of still-possible tiles per cell. */
    private final int[] domain;
    private final int[] stack;
    private int stackTop;

    /**
     * @param w       grid width
     * @param h       grid height
     * @param weights relative frequency per tile; higher means more common
     */
    public Wfc(int w, int h, double[] weights) {
        this.w = w;
        this.h = h;
        this.weights = weights.clone();
        this.domain = new int[w * h];
        this.stack = new int[w * h];
    }

    /**
     * Runs the solver.
     *
     * @return a {@code w * h} array of tile ids, or null if every attempt hit a
     *         contradiction
     */
    public int[] solve(Rng rng, int attempts) {
        for (int attempt = 0; attempt < attempts; attempt++) {
            int[] result = attemptSolve(rng);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private int[] attemptSolve(Rng rng) {
        final int all = (1 << TILE_COUNT) - 1;
        java.util.Arrays.fill(domain, all);
        stackTop = 0;

        // The bottom row sits closest to the paddle; keeping it free of buried
        // armour stops a run from opening with an unbreakable wall in your face.
        for (int x = 0; x < w; x++) {
            domain[idx(x, h - 1)] &= ~(1 << CORE);
        }
        if (!propagateAll()) {
            return null;
        }

        while (true) {
            int cell = lowestEntropy(rng);
            if (cell < 0) {
                break; // Fully collapsed.
            }
            int chosen = collapse(cell, rng);
            domain[cell] = 1 << chosen;
            push(cell);
            if (!propagate()) {
                return null;
            }
        }

        int[] out = new int[w * h];
        for (int i = 0; i < out.length; i++) {
            out[i] = Integer.numberOfTrailingZeros(domain[i]);
        }
        return out;
    }

    /**
     * Shannon entropy over the remaining weighted options, with a small random
     * jitter so ties break differently each run.
     */
    private int lowestEntropy(Rng rng) {
        double best = Double.MAX_VALUE;
        int bestCell = -1;
        for (int i = 0; i < domain.length; i++) {
            int d = domain[i];
            int count = Integer.bitCount(d);
            if (count <= 1) {
                continue;
            }
            double sum = 0;
            double sumLog = 0;
            for (int t = 0; t < TILE_COUNT; t++) {
                if ((d & (1 << t)) != 0) {
                    double weight = weights[t];
                    sum += weight;
                    sumLog += weight * Math.log(weight);
                }
            }
            double entropy = Math.log(sum) - sumLog / sum + rng.nextDouble() * 1e-3;
            if (entropy < best) {
                best = entropy;
                bestCell = i;
            }
        }
        return bestCell;
    }

    private int collapse(int cell, Rng rng) {
        int d = domain[cell];
        double[] w2 = new double[TILE_COUNT];
        for (int t = 0; t < TILE_COUNT; t++) {
            w2[t] = (d & (1 << t)) != 0 ? weights[t] : 0;
        }
        return rng.weighted(w2);
    }

    private boolean propagateAll() {
        stackTop = 0;
        for (int i = 0; i < domain.length; i++) {
            push(i);
        }
        return propagate();
    }

    /** Arc consistency to a fixed point. */
    private boolean propagate() {
        while (stackTop > 0) {
            int cell = stack[--stackTop];
            int cx = cell % w;
            int cy = cell / w;

            for (int dir = 0; dir < 4; dir++) {
                int nx = cx + (dir == 0 ? 1 : dir == 1 ? -1 : 0);
                int ny = cy + (dir == 2 ? 1 : dir == 3 ? -1 : 0);
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                    continue;
                }
                int n = idx(nx, ny);

                // Which tiles could the neighbour still be, given this cell?
                int permitted = 0;
                int d = domain[cell];
                for (int t = 0; t < TILE_COUNT; t++) {
                    if ((d & (1 << t)) == 0) {
                        continue;
                    }
                    for (int u = 0; u < TILE_COUNT; u++) {
                        if (COMPATIBLE[t][u]) {
                            permitted |= 1 << u;
                        }
                    }
                }

                int before = domain[n];
                int after = before & permitted;
                if (after == 0) {
                    return false; // Contradiction.
                }
                if (after != before) {
                    domain[n] = after;
                    push(n);
                }
            }
        }
        return true;
    }

    private void push(int cell) {
        // The stack is sized for the grid, and a cell already queued is harmless
        // to re-queue, but guard anyway so propagation can never overflow it.
        if (stackTop < stack.length) {
            stack[stackTop++] = cell;
        }
    }

    private int idx(int x, int y) {
        return y * w + x;
    }

    /**
     * Mirrors a half-grid into a full-width grid.
     *
     * <p>Every tile is compatible with itself, so the reflected seam is always a
     * legal adjacency and the mirrored result needs no re-solving. Symmetry is
     * most of what makes a generated layout read as designed.</p>
     */
    public static int[] mirror(int[] half, int halfW, int h, int fullW) {
        int[] out = new int[fullW * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < fullW; x++) {
                int sx = x < halfW ? x : fullW - 1 - x;
                sx = Math.min(halfW - 1, Math.max(0, sx));
                out[y * fullW + x] = half[y * halfW + sx];
            }
        }
        return out;
    }
}
