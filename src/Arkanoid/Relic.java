package Arkanoid;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Permanent run modifiers, drafted between levels.
 *
 * <p>This is the change that turns a score-attack into a game with runs. Levels
 * are generated, so replay value has to come from somewhere other than
 * memorising layouts — it comes from the build. After each level you pick one of
 * three offers, they stack for the rest of the run, and they interact:
 * {@code HEAVY} plus {@code CHAIN_REACTION} turns explosives into a demolition
 * build, {@code METRONOME} plus {@code KEEN_EDGE} turns the whole board into one
 * enormous combo, {@code PRISM} plus {@code SPLIT_SHOT} floods the screen with
 * balls.</p>
 *
 * <p>Offers are drawn from the run's seeded stream, so a seed reproduces the
 * whole run including which relics you were shown.</p>
 */
public enum Relic {

    // ---- common -------------------------------------------------------------
    SPARE("SPARE BALL", "Start the run with an extra life.",
            Rarity.COMMON, true),
    WIDE_LOAD("WIDE LOAD", "Paddle is 18% wider.",
            Rarity.COMMON, true),
    HEAVY("HEAVY SHOT", "The ball deals +1 damage.",
            Rarity.COMMON, true),
    KEEN_EDGE("KEEN EDGE", "+25% score from combos.",
            Rarity.COMMON, true),
    MAGNETISM("MAGNETISM", "Capsules drift towards the paddle.",
            Rarity.COMMON, false),
    SOFT_HANDS("SOFT HANDS", "Paddle moves 22% faster.",
            Rarity.COMMON, true),

    // ---- uncommon -----------------------------------------------------------
    SPLIT_SHOT("SPLIT SHOT", "Every 6th brick broken spawns another ball.",
            Rarity.UNCOMMON, false),
    CHAIN_REACTION("CHAIN REACTION", "Explosive bricks blast 70% further.",
            Rarity.UNCOMMON, true),
    PIERCE("PIERCE", "The first brick of each volley does not deflect the ball.",
            Rarity.UNCOMMON, false),
    OVERTUNE("OVERTUNE", "+12% ball speed, +30% score.",
            Rarity.UNCOMMON, true),
    SIPHON("SIPHON", "Every 45 bricks broken grants a life.",
            Rarity.UNCOMMON, false),
    ARSENAL("ARSENAL", "Begin every level with the laser cannon.",
            Rarity.UNCOMMON, false),
    STICKY_FINGERS("STICKY FINGERS", "Begin every level with a sticky paddle.",
            Rarity.UNCOMMON, false),
    ECHO("ECHO", "Breaking a brick also damages one neighbour.",
            Rarity.UNCOMMON, false),
    ADRENALINE("ADRENALINE", "The ball accelerates as the board empties.",
            Rarity.UNCOMMON, false),

    // ---- rare ---------------------------------------------------------------
    METRONOME("METRONOME", "Your combo no longer resets on the paddle.",
            Rarity.RARE, false),
    PHOENIX("PHOENIX", "Once per level, your last ball returns instead of dying.",
            Rarity.RARE, false),
    PRISM("PRISM", "Multiball splits into five instead of three.",
            Rarity.RARE, false),
    SINGULARITY("SINGULARITY", "The ball curves towards the densest cluster.",
            Rarity.RARE, false),
    DILATION("DILATION", "Time slows harder, and the clutch meter refills faster.",
            Rarity.RARE, true);

    public enum Rarity {
        COMMON(new Color(0xB6C2D9), 1.00),
        UNCOMMON(new Color(0x62D2A2), 0.55),
        RARE(new Color(0xFFB454), 0.22);

        public final Color color;
        public final double weight;

        Rarity(Color color, double weight) {
            this.color = color;
            this.weight = weight;
        }
    }

    public final String title;
    public final String description;
    public final Rarity rarity;
    /** Whether taking it again does something useful. */
    public final boolean stacks;

    Relic(String title, String description, Rarity rarity, boolean stacks) {
        this.title = title;
        this.description = description;
        this.rarity = rarity;
        this.stacks = stacks;
    }

    /** The relics held in a run, and the queries the simulation makes of them. */
    public static final class Loadout {

        private final Map<Relic, Integer> held = new EnumMap<>(Relic.class);

        public void add(Relic r) {
            held.merge(r, 1, Integer::sum);
        }

        public int count(Relic r) {
            return held.getOrDefault(r, 0);
        }

        public boolean has(Relic r) {
            return count(r) > 0;
        }

        public void clear() {
            held.clear();
        }

        public Map<Relic, Integer> all() {
            return held;
        }

        public int distinctCount() {
            return held.size();
        }

        // ---- derived numbers the game asks for --------------------------------

        public double paddleWidthMultiplier() {
            return Math.pow(1.18, count(WIDE_LOAD));
        }

        public double paddleSpeedMultiplier() {
            return Math.pow(1.22, count(SOFT_HANDS));
        }

        public double ballSpeedMultiplier() {
            return Math.pow(1.12, count(OVERTUNE));
        }

        public double scoreMultiplier() {
            return Math.pow(1.30, count(OVERTUNE)) * Math.pow(1.25, count(KEEN_EDGE));
        }

        public int ballDamage() {
            return 1 + count(HEAVY);
        }

        public double blastRadiusMultiplier() {
            return Math.pow(1.70, count(CHAIN_REACTION));
        }

        public int multiballSplit() {
            return has(PRISM) ? 5 : 3;
        }

        public int bonusLives() {
            return count(SPARE);
        }

        public double dilationMultiplier() {
            return Math.pow(1.55, count(DILATION));
        }
    }

    /**
     * Draws {@code n} distinct offers, weighted by rarity.
     *
     * <p>Relics that do nothing when repeated are excluded once held, so late
     * drafts stay meaningful instead of degenerating into dead picks.</p>
     */
    public static List<Relic> offer(Rng rng, Loadout held, int n) {
        List<Relic> pool = new ArrayList<>();
        for (Relic r : values()) {
            if (held.has(r) && !r.stacks) {
                continue;
            }
            pool.add(r);
        }

        List<Relic> picked = new ArrayList<>();
        for (int i = 0; i < n && !pool.isEmpty(); i++) {
            double[] weights = new double[pool.size()];
            for (int j = 0; j < pool.size(); j++) {
                weights[j] = pool.get(j).rarity.weight;
            }
            int idx = rng.weighted(weights);
            picked.add(pool.remove(idx));
        }
        return picked;
    }
}
