package Arkanoid;

/**
 * Online estimate of how good the player currently is, used to aim the next
 * level's difficulty.
 *
 * <p>A fixed difficulty ramp is wrong for almost everybody: it is a wall for a
 * new player and a nap for a good one. This keeps a running estimate of skill
 * with an explicit uncertainty and updates it after every level with a
 * Kalman-style correction — a large uncertainty means a single level moves the
 * estimate a lot, and as evidence accumulates the estimate settles and stops
 * chasing noise. Uncertainty is then re-inflated slightly each level so the
 * model can still follow a player who is warming up or tiring.</p>
 *
 * <p>The generated difficulty targets slightly <em>above</em> the estimate,
 * which is where the interesting part of the flow channel sits: hard enough to
 * demand attention, not hard enough to feel unfair.</p>
 */
public final class SkillModel {

    /** Skill estimate, 0 (novice) to 1 (expert). */
    private double mu = 0.32;
    /** Standard deviation of that estimate. */
    private double sigma = 0.26;

    /** Bricks per second a confident player sustains; used to normalise pace. */
    private static final double REFERENCE_PACE = 1.35;

    private double lastObservation = 0.32;
    private int levelsObserved;

    /** Difficulty aimed at, 0..1. */
    public double difficulty() {
        // Aim just above current skill, and open the target up while we are still
        // unsure so an unknown player gets a level that can tell us something.
        return clamp01(mu + 0.10 + sigma * 0.25);
    }

    public double skill() {
        return mu;
    }

    public double confidence() {
        return clamp01(1 - sigma / 0.26);
    }

    public double lastObservation() {
        return lastObservation;
    }

    public int levelsObserved() {
        return levelsObserved;
    }

    /**
     * Folds one completed level into the estimate.
     *
     * @param deaths          balls lost during the level
     * @param seconds         wall-clock time taken
     * @param bricksDestroyed breakable bricks cleared
     * @param paddleAccuracy  share of paddle approaches that were caught, 0..1
     * @param bestCombo       longest combo in the level
     */
    public void observeLevel(int deaths, double seconds, int bricksDestroyed,
            double paddleAccuracy, int bestCombo) {
        double pace = clamp01(bricksDestroyed / Math.max(4.0, seconds) / REFERENCE_PACE);
        double survival = 1.0 / (1.0 + deaths * 1.25);
        double control = clamp01(paddleAccuracy);
        double flair = clamp01(bestCombo / 14.0);

        double z = 0.34 * survival + 0.28 * pace + 0.26 * control + 0.12 * flair;
        lastObservation = z;
        levelsObserved++;

        // Kalman gain: trust the observation in proportion to how unsure we are.
        double observationNoise = 0.20;
        double k = (sigma * sigma) / (sigma * sigma + observationNoise * observationNoise);
        mu = clamp01(mu + k * (z - mu));
        sigma = Math.sqrt(Math.max(1e-4, (1 - k) * sigma * sigma));

        // Let the model stay responsive: a player's form drifts within a run.
        sigma = Math.min(0.26, sigma + 0.035);
    }

    /** A hard failure is strong evidence on its own, applied without a full level. */
    public void observeDeath() {
        double z = Math.max(0, mu - 0.18);
        double k = (sigma * sigma) / (sigma * sigma + 0.30 * 0.30);
        mu = clamp01(mu + k * (z - mu));
        sigma = Math.min(0.26, sigma + 0.01);
    }

    /** Short label for the HUD. */
    public String band() {
        double d = difficulty();
        if (d < 0.25) {
            return "CALIBRATING";
        }
        if (d < 0.42) {
            return "STEADY";
        }
        if (d < 0.60) {
            return "PRESSING";
        }
        if (d < 0.78) {
            return "SEVERE";
        }
        return "RUTHLESS";
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
