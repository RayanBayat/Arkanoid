package Arkanoid;

/**
 * The generative, adaptive soundtrack.
 *
 * <p>There is no music file. Each level picks a root note and a mode, generates
 * a chord progression with a weighted Markov chain over diatonic degrees, and
 * plays it live through {@link Audio}. Layers are gated by an intensity value
 * the game feeds in — a calm board is a pad and a bass, a nearly-lost ball adds
 * arpeggios, drums and double-time hats — so the score follows the match instead
 * of looping underneath it.</p>
 *
 * <p>The important half is the other direction: bricks are tuned. Every brick
 * carries a scale degree, so breaking one plays a note that is <em>in key</em>
 * with whatever is currently playing, and a long combo walks up the scale.
 * Playing well composes a melody.</p>
 *
 * <p>{@link #tick} runs on the audio thread; everything the game thread touches
 * is volatile and read into locals once per block.</p>
 */
public final class Music {

    /** Modes, as semitone offsets from the root. */
    public static final int[][] MODES = {
        {0, 2, 3, 5, 7, 8, 10},   // Aeolian - default minor
        {0, 2, 3, 5, 7, 9, 10},   // Dorian - minor but hopeful
        {0, 1, 3, 5, 7, 8, 10},   // Phrygian - tense
        {0, 2, 4, 6, 7, 9, 11},   // Lydian - bright, floating
        {0, 2, 4, 5, 7, 9, 10},   // Mixolydian - warm
        {0, 2, 3, 5, 7, 8, 11},   // Harmonic minor - dramatic
        {0, 2, 3, 7, 8, 7, 10},   // Hirajoshi-ish - sparse, eastern
    };

    public static final String[] MODE_NAMES = {
        "AEOLIAN", "DORIAN", "PHRYGIAN", "LYDIAN", "MIXOLYDIAN", "HARMONIC MINOR", "HIRAJOSHI",
    };

    private static final String[] NOTE_NAMES = {
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B",
    };

    private static final int STEPS_PER_BAR = 16;

    // ---- written by the game thread, read by the audio thread ----
    private static volatile int root = 45;          // A2
    private static volatile int mode;
    private static volatile double intensity = 0.3;
    private static volatile double bpm = 96;
    private static volatile boolean enabled;
    private static volatile int[] progression = {0, 5, 3, 4};

    // ---- audio-thread only ----
    private static long nextStepFrame;
    private static int step;
    private static int bar;
    private static int arpIndex;

    private Music() {
    }

    // ------------------------------------------------------------ game thread

    /** Rolls a new key, mode and progression for a level. */
    public static void newSection(Rng rng, int level) {
        int newRoot = 40 + rng.nextInt(9);
        int newMode = rng.nextInt(MODES.length);

        // A weighted Markov walk over scale degrees. The weights encode ordinary
        // functional pull (tonic wants subdominant or dominant, dominant wants
        // to resolve) so the progression sounds intentional rather than random.
        double[][] transitions = {
            //     I     ii    iii   IV    V     vi    vii
            {0.05, 0.14, 0.12, 0.26, 0.24, 0.15, 0.04}, // from I
            {0.10, 0.04, 0.10, 0.14, 0.42, 0.12, 0.08}, // from ii
            {0.14, 0.10, 0.04, 0.30, 0.16, 0.22, 0.04}, // from iii
            {0.22, 0.10, 0.08, 0.05, 0.36, 0.15, 0.04}, // from IV
            {0.44, 0.06, 0.08, 0.10, 0.05, 0.23, 0.04}, // from V
            {0.16, 0.16, 0.10, 0.28, 0.20, 0.05, 0.05}, // from vi
            {0.46, 0.06, 0.10, 0.12, 0.20, 0.06, 0.00}, // from vii
        };

        int length = rng.chance(0.35) ? 8 : 4;
        int[] prog = new int[length];
        int degree = 0;
        for (int i = 0; i < length; i++) {
            prog[i] = degree;
            degree = rng.weighted(transitions[degree]);
        }
        prog[0] = 0; // Always anchor the phrase on the tonic.

        root = newRoot;
        mode = newMode;
        progression = prog;
        bpm = Math.min(148, 92 + level * 3.0);
    }

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    /** 0..1. Gates the layers and drives the master filter in {@link Audio}. */
    public static void setIntensity(double v) {
        double clamped = Math.max(0, Math.min(1, v));
        intensity = clamped;
        Audio.setIntensity(clamped);
    }

    public static String keyName() {
        return NOTE_NAMES[Math.floorMod(root, 12)] + " " + MODE_NAMES[mode];
    }

    public static double tempo() {
        return bpm;
    }

    /**
     * MIDI pitch for a scale degree, wrapping octaves.
     *
     * @param degree may be negative or beyond the scale length; it wraps and
     *               transposes by octaves as it goes
     */
    public static int scaleNote(int degree, int octave) {
        int[] scale = MODES[mode];
        int len = scale.length;
        int oct = Math.floorDiv(degree, len) + octave;
        int idx = Math.floorMod(degree, len);
        return root + scale[idx] + oct * 12;
    }

    /** Chord tones for a progression degree, as scale degrees stacked in thirds. */
    private static int[] chordDegrees(int rootDegree) {
        return new int[] {rootDegree, rootDegree + 2, rootDegree + 4};
    }

    /** The chord currently sounding, so gameplay notes can lock onto it. */
    public static int currentChordDegree() {
        int[] prog = progression;
        return prog[Math.floorMod(bar, prog.length)];
    }

    // ----------------------------------------------------------- audio thread

    /**
     * Advances the musical clock. Called once per audio block.
     *
     * @param framePos global sample position at the start of the block
     * @param frames   block length in frames
     */
    static void tick(long framePos, int frames) {
        if (!enabled) {
            nextStepFrame = framePos;
            return;
        }
        double localIntensity = intensity;
        int[] prog = progression;
        double stepFrames = Audio.RATE * 60.0 / bpm / 4.0;

        if (nextStepFrame < framePos) {
            nextStepFrame = framePos;
        }
        while (nextStepFrame < framePos + frames) {
            playStep(step, prog, localIntensity);
            step++;
            if (step >= STEPS_PER_BAR) {
                step = 0;
                bar++;
            }
            nextStepFrame += (long) stepFrames;
        }
    }

    private static void playStep(int s, int[] prog, double energy) {
        int degree = prog[Math.floorMod(bar, prog.length)];
        int[] chord = chordDegrees(degree);
        double beat = 60.0 / bpm;

        // --- pad: one sustained chord per bar -----------------------------
        if (s == 0) {
            for (int i = 0; i < chord.length; i++) {
                double pan = (i - 1) * 0.45;
                Audio.noteMusic(Audio.Patch.PAD, scaleNote(chord[i], 1),
                        0.34 + 0.16 * energy, beat * 3.4, pan);
            }
        }

        // --- bass --------------------------------------------------------
        if (s == 0 || s == 6 || s == 8 || s == 14) {
            double vel = s == 0 ? 0.85 : 0.55;
            Audio.noteMusic(Audio.Patch.BASS, scaleNote(degree, -1), vel, beat * 0.35, 0);
            if (s == 0) {
                Audio.noteMusic(Audio.Patch.SUB, scaleNote(degree, -2), 0.5, beat * 0.6, 0);
            }
        }

        // --- arpeggio ----------------------------------------------------
        if (energy > 0.22 && s % 2 == 0) {
            int tone = chord[arpIndex % chord.length];
            int oct = (arpIndex / chord.length) % 2 + 1;
            arpIndex++;
            Audio.noteMusic(Audio.Patch.ARP, scaleNote(tone, oct),
                    0.22 + 0.22 * energy, beat * 0.2,
                    Math.sin(arpIndex * 0.7) * 0.6);
        }

        // --- drums -------------------------------------------------------
        if (energy > 0.40) {
            if (s == 0 || s == 8 || (energy > 0.7 && s == 11)) {
                Audio.noteMusic(Audio.Patch.KICK, 36, 0.9, 0.02, 0);
            }
            if (s == 4 || s == 12) {
                Audio.noteMusic(Audio.Patch.SNARE, 62, 0.55 + 0.2 * energy, 0.02, 0.1);
            }
            int hatEvery = energy > 0.78 ? 1 : 2;
            if (s % hatEvery == 0) {
                Audio.noteMusic(Audio.Patch.HAT, 80, s % 4 == 0 ? 0.28 : 0.16, 0.01,
                        (s % 4) * 0.2 - 0.3);
            }
        }

        // --- tension: a tritone shimmer when things are dire --------------
        if (energy > 0.86 && s == 15) {
            Audio.noteMusic(Audio.Patch.BELL, scaleNote(degree + 3, 2), 0.20, beat * 0.5, 0.4);
        }
    }
}
