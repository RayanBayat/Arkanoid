package Arkanoid;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Turns a seed and a difficulty number into a playable board.
 *
 * <p>Pipeline:</p>
 * <ol>
 *   <li>{@link Wfc} solves a structural skeleton on a half-width grid.</li>
 *   <li>The skeleton is mirrored, which makes it look composed rather than
 *       sprayed, and costs nothing because every tile is self-compatible.</li>
 *   <li>Materials are painted on: an {@code EDGE} becomes a plain brick, a
 *       {@code CORE} becomes armour or an indestructible block, and explosives
 *       and regenerating bricks are sprinkled by difficulty.</li>
 *   <li>A flood fill proves every breakable brick is actually reachable. If a
 *       ring of indestructible blocks has sealed something in, the solids are
 *       demoted rather than shipping an unwinnable board.</li>
 *   <li>Each brick is tagged with a scale degree so the board is playable as an
 *       instrument — see {@link Music}.</li>
 * </ol>
 */
public final class LevelGen {

    public static final int COLS = 13;
    public static final int ROWS = 9;
    public static final double BRICK_W = 56;
    public static final double BRICK_H = 26;
    public static final double ORIGIN_X = (Field.W - COLS * BRICK_W) / 2.0;
    public static final double ORIGIN_Y = 104;

    /**
     * Board size window. Too few bricks is a level that ends before it starts;
     * too many is a solid slab that is slow to chew through and slow to draw.
     */
    private static final int MIN_BRICKS = 26;
    private static final int MAX_BRICKS = 74;

    private static final String[] ARCHETYPES = {
        "LATTICE", "MONOLITH", "CASCADE", "REDOUBT", "FILAMENT", "BASTION",
        "SPIRE", "DRIFT", "CRUCIBLE", "MERIDIAN",
    };

    /** Everything a generated level needs to be played and displayed. */
    public record Board(
            List<Bricks> bricks,
            Palette palette,
            String archetype,
            double ballSpeed,
            int breakableCount,
            double difficulty,
            long seed) {

        public String title() {
            return archetype + " / " + palette.name;
        }
    }

    private LevelGen() {
    }

    /**
     * Generates a level.
     *
     * @param runSeed    the run's master seed
     * @param level      1-based level number
     * @param difficulty 0..1, supplied by {@link SkillModel}
     */
    public static Board generate(long runSeed, int level, double difficulty) {
        return generate(runSeed, level, difficulty, false);
    }

    /**
     * @param colourSafe restrict the generated palette to colour-blind-safe hues
     */
    public static Board generate(long runSeed, int level, double difficulty,
            boolean colourSafe) {
        double d = Math.max(0, Math.min(1, difficulty));
        Rng master = new Rng(runSeed ^ (0x5DEECE66DL * level));

        Rng paletteRng = master.fork(0xC010112);
        Rng shapeRng = master.fork(0x51A9E);
        Rng paintRng = master.fork(0x9A17B);

        int tierCount = 5;
        Palette palette = Palette.generate(paletteRng, tierCount, colourSafe);
        String archetype = ARCHETYPES[shapeRng.nextInt(ARCHETYPES.length)];
        boolean mirrored = shapeRng.chance(0.82);

        int[] grid = solveSkeleton(shapeRng, d, mirrored);
        if (grid == null) {
            grid = fallbackGrid(d);
        }

        Bricks.Kind[] kinds = paint(grid, paintRng, d, level);
        ensureSolvable(kinds);

        List<Bricks> bricks = new ArrayList<>();
        int breakable = 0;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                Bricks.Kind kind = kinds[row * COLS + col];
                if (kind == null) {
                    continue;
                }
                int tier = tierFor(row, tierCount);
                Color color = colorFor(palette, kind, tier);
                int degree = scaleDegree(col, row);
                bricks.add(new Bricks(
                        ORIGIN_X + col * BRICK_W,
                        ORIGIN_Y + row * BRICK_H,
                        BRICK_W, BRICK_H, color, kind, tier, degree));
                if (kind != Bricks.Kind.SOLID) {
                    breakable++;
                }
            }
        }

        double speed = Math.min(680, 330 + 150 * d + level * 6);
        return new Board(bricks, palette, archetype, speed, breakable, d, runSeed);
    }

    // --------------------------------------------------------------- skeleton

    /**
     * Solves a skeleton whose brick count lands inside the target window.
     *
     * <p>Tile weights set density only indirectly — the adjacency constraints do
     * as much to decide it — so rather than guess a weight per difficulty this
     * closes the loop: solve, measure, nudge the {@code VOID} weight towards
     * whichever side of the window was missed, and try again. It converges in a
     * couple of attempts and keeps the closest near-miss as a fallback.</p>
     */
    private static int[] solveSkeleton(Rng rng, double d, boolean mirrored) {
        double bias = 0;
        int[] best = null;
        int bestMiss = Integer.MAX_VALUE;

        for (int attempt = 0; attempt < 14; attempt++) {
            double[] weights = {
                Math.max(0.30, 2.6 + 2.4 * (1 - d) + bias), // VOID
                1.70,                                        // EDGE
                0.75 + 1.50 * d,                             // FILL
                0.10 + 0.95 * d,                             // CORE
                0.65,                                        // SPUR
            };

            int halfW = mirrored ? (COLS + 1) / 2 : COLS;
            int[] solved = new Wfc(halfW, ROWS, weights).solve(rng, 8);
            if (solved == null) {
                bias -= 0.25; // a denser field has more legal configurations
                continue;
            }
            int[] grid = mirrored ? Wfc.mirror(solved, halfW, ROWS, COLS) : solved;

            int filled = 0;
            for (int v : grid) {
                if (v != Wfc.VOID) {
                    filled++;
                }
            }
            if (filled >= MIN_BRICKS && filled <= MAX_BRICKS) {
                return grid;
            }

            int miss = filled < MIN_BRICKS ? MIN_BRICKS - filled : filled - MAX_BRICKS;
            if (miss < bestMiss) {
                bestMiss = miss;
                best = grid;
            }
            bias += filled > MAX_BRICKS ? 0.55 : -0.55;
        }
        return best;
    }

    /** A plain block of bricks, used only if WFC somehow never converges. */
    private static int[] fallbackGrid(double d) {
        int[] grid = new int[COLS * ROWS];
        int rows = 4 + (int) Math.round(d * 3);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                boolean on = row < rows && !(col == 0 || col == COLS - 1);
                grid[row * COLS + col] = on
                        ? (row == 0 || row == rows - 1 ? Wfc.EDGE : Wfc.FILL)
                        : Wfc.VOID;
            }
        }
        return grid;
    }

    // ---------------------------------------------------------------- painting

    private static Bricks.Kind[] paint(int[] grid, Rng rng, double d, int level) {
        Bricks.Kind[] kinds = new Bricks.Kind[COLS * ROWS];

        double solidChance = level < 3 ? 0 : Math.min(0.34, 0.06 + 0.30 * d);
        double explosiveChance = level < 2 ? 0.02 : 0.05 + 0.07 * d;
        double regenChance = level < 4 ? 0 : 0.04 + 0.09 * d;
        double toughChance = 0.18 + 0.45 * d;

        for (int i = 0; i < grid.length; i++) {
            kinds[i] = switch (grid[i]) {
                case Wfc.VOID -> null;
                case Wfc.EDGE, Wfc.SPUR -> rng.chance(explosiveChance)
                        ? Bricks.Kind.EXPLOSIVE
                        : Bricks.Kind.NORMAL;
                case Wfc.FILL -> {
                    if (rng.chance(explosiveChance)) {
                        yield Bricks.Kind.EXPLOSIVE;
                    }
                    if (rng.chance(regenChance)) {
                        yield Bricks.Kind.REGEN;
                    }
                    yield rng.chance(toughChance) ? Bricks.Kind.TOUGH : Bricks.Kind.NORMAL;
                }
                case Wfc.CORE -> {
                    if (rng.chance(solidChance)) {
                        yield Bricks.Kind.SOLID;
                    }
                    yield rng.chance(0.55) ? Bricks.Kind.CORE : Bricks.Kind.TOUGH;
                }
                default -> null;
            };
        }
        return kinds;
    }

    /**
     * Demotes indestructible blocks if they have sealed any breakable brick away
     * from the rest of the board.
     *
     * <p>A flood fill from the border across every non-solid cell finds what the
     * ball can eventually touch, since breakable bricks all disappear in time and
     * only {@code SOLID} is permanent. Anything outside that component would be
     * an unwinnable level, so the solids are turned into armour instead.</p>
     */
    private static void ensureSolvable(Bricks.Kind[] kinds) {
        boolean[] seen = new boolean[kinds.length];
        Deque<Integer> queue = new ArrayDeque<>();

        for (int col = 0; col < COLS; col++) {
            enqueue(kinds, seen, queue, col, 0);
            enqueue(kinds, seen, queue, col, ROWS - 1);
        }
        for (int row = 0; row < ROWS; row++) {
            enqueue(kinds, seen, queue, 0, row);
            enqueue(kinds, seen, queue, COLS - 1, row);
        }

        while (!queue.isEmpty()) {
            int cell = queue.poll();
            int cx = cell % COLS;
            int cy = cell / COLS;
            enqueue(kinds, seen, queue, cx + 1, cy);
            enqueue(kinds, seen, queue, cx - 1, cy);
            enqueue(kinds, seen, queue, cx, cy + 1);
            enqueue(kinds, seen, queue, cx, cy - 1);
        }

        boolean sealed = false;
        for (int i = 0; i < kinds.length; i++) {
            if (kinds[i] != null && kinds[i] != Bricks.Kind.SOLID && !seen[i]) {
                sealed = true;
                break;
            }
        }
        if (sealed) {
            for (int i = 0; i < kinds.length; i++) {
                if (kinds[i] == Bricks.Kind.SOLID) {
                    kinds[i] = Bricks.Kind.CORE;
                }
            }
        }
    }

    private static void enqueue(Bricks.Kind[] kinds, boolean[] seen, Deque<Integer> queue,
            int x, int y) {
        if (x < 0 || y < 0 || x >= COLS || y >= ROWS) {
            return;
        }
        int i = y * COLS + x;
        if (seen[i] || kinds[i] == Bricks.Kind.SOLID) {
            return;
        }
        seen[i] = true;
        queue.add(i);
    }

    // ----------------------------------------------------------------- mapping

    private static int tierFor(int row, int tierCount) {
        // Higher rows are hotter colours.
        return Math.min(tierCount - 1, (ROWS - 1 - row) * tierCount / ROWS);
    }

    private static Color colorFor(Palette palette, Bricks.Kind kind, int tier) {
        return switch (kind) {
            case SOLID -> new Color(0x5A6472);
            case EXPLOSIVE -> Palette.mix(palette.tiers[tier], palette.accent, 0.75);
            case REGEN -> Palette.mix(palette.tiers[tier], palette.accentSoft, 0.55);
            case CORE -> Palette.scale(palette.tiers[tier], 0.78);
            default -> palette.tiers[tier];
        };
    }

    /**
     * Columns walk the scale, rows step by octave. A run cleared left to right
     * therefore ascends, and higher rows ring higher.
     */
    private static int scaleDegree(int col, int row) {
        int withinOctave = col * 7 / COLS;
        int octaveBand = (ROWS - 1 - row) / 3;
        return withinOctave + octaveBand * 7;
    }
}
