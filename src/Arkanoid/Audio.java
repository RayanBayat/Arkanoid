package Arkanoid;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * A real-time subtractive synthesiser, streamed to the sound card.
 *
 * <p>The previous version baked a handful of fixed {@code Clip}s at start-up,
 * which caps the game at replaying the same eight noises forever. This renders
 * every sample on the fly instead: oscillators with PolyBLEP anti-aliasing, an
 * ADSR per voice, a resonant state-variable filter, and a stereo delay on the
 * master bus. That means pitch, timbre and space are all parameters the game can
 * modulate continuously — so the ball's pitch can rise with the combo, the
 * filter can open as danger increases, and {@link Music} can play actual
 * harmony through the same 32 voices.</p>
 *
 * <p>Rendering happens on a dedicated max-priority daemon thread in small blocks.
 * If no audio device is available every entry point becomes a silent no-op.</p>
 */
public final class Audio {

    public static final int RATE = 44100;

    /** Frames per render block. 256 frames is ~5.8 ms of scheduling granularity. */
    private static final int BLOCK = 256;
    private static final int BLOCKS_BUFFERED = 6;
    private static final int VOICES = 32;

    private static final int SINE = 0;
    private static final int SAW = 1;
    private static final int SQUARE = 2;
    private static final int TRIANGLE = 3;
    private static final int NOISE = 4;

    /**
     * Instrument definitions. Fields are: waveform, attack, decay, sustain,
     * release (seconds), base filter cutoff (Hz), filter envelope depth (Hz),
     * resonance 0..1, detune in cents, pitch-envelope depth in semitones,
     * noise blend 0..1 and output level.
     */
    public enum Patch {
        //         wave      atk    dec   sus   rel   cut    env     q    det  pEnv noise lvl
        PAD(       SAW,     0.60,  1.20, 0.65, 1.80,  420,   700, 0.20,  14,  0,   0.00, 0.16),
        BASS(      SAW,     0.005, 0.18, 0.55, 0.22,  240,  1400, 0.42,   8,  0,   0.00, 0.34),
        SUB(       SINE,    0.008, 0.30, 0.40, 0.25,  600,     0, 0.00,   0,  0,   0.00, 0.40),
        ARP(       SQUARE,  0.002, 0.12, 0.20, 0.14, 1100,  2600, 0.38,   6,  0,   0.00, 0.17),
        PLUCK(     TRIANGLE,0.001, 0.16, 0.00, 0.16, 2600,  3200, 0.22,   4,  0,   0.00, 0.30),
        LEAD(      SAW,     0.004, 0.22, 0.45, 0.30, 1800,  3600, 0.34,  12,  0,   0.00, 0.22),
        BELL(      SINE,    0.001, 0.70, 0.00, 0.70, 5000,     0, 0.00,   3,  0,   0.00, 0.26),
        KICK(      SINE,    0.001, 0.22, 0.00, 0.10,  900,     0, 0.00,   0, 36,   0.05, 0.62),
        SNARE(     NOISE,   0.001, 0.16, 0.00, 0.10, 3200,  1200, 0.30,   0,  0,   1.00, 0.28),
        HAT(       NOISE,   0.001, 0.045,0.00, 0.04, 9000,     0, 0.10,   0,  0,   1.00, 0.15),
        ZAP(       SQUARE,  0.001, 0.09, 0.00, 0.06, 3400,  2000, 0.30,   0, 24,   0.10, 0.24),
        IMPACT(    NOISE,   0.001, 0.34, 0.00, 0.24,  700,  1800, 0.45,   0, 12,   0.85, 0.42),
        SWEEP(     SAW,     0.30,  0.60, 0.30, 0.60,  300,  5200, 0.50,  18,  0,   0.10, 0.20);

        final int wave;
        final double attack;
        final double decay;
        final double sustain;
        final double release;
        final double cutoff;
        final double filterEnv;
        final double resonance;
        final double detuneCents;
        final double pitchEnv;
        final double noiseMix;
        final double level;

        Patch(int wave, double attack, double decay, double sustain, double release,
                double cutoff, double filterEnv, double resonance, double detuneCents,
                double pitchEnv, double noiseMix, double level) {
            this.wave = wave;
            this.attack = attack;
            this.decay = decay;
            this.sustain = sustain;
            this.release = release;
            this.cutoff = cutoff;
            this.filterEnv = filterEnv;
            this.resonance = resonance;
            this.detuneCents = detuneCents;
            this.pitchEnv = pitchEnv;
            this.noiseMix = noiseMix;
            this.level = level;
        }
    }

    private record Event(Patch patch, double midi, double velocity, double hold, double pan) { }

    private static final ConcurrentLinkedQueue<Event> INBOX = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger PENDING = new AtomicInteger();
    private static final Voice[] VOICE_POOL = new Voice[VOICES];

    private static volatile boolean running;
    private static volatile boolean muted;
    private static volatile double masterGain = 0.85;
    /** Separate buses so the player can balance the score against the game. */
    private static volatile double musicGain = 0.80;
    private static volatile double sfxGain = 0.90;
    /** 0..1, raised by danger. Opens the master filter and wets the delay. */
    private static volatile double intensity = 0.35;

    private static SourceDataLine line;
    private static Thread thread;

    private static final int SINE_BITS = 12;
    private static final int SINE_SIZE = 1 << SINE_BITS;
    private static final double[] SINE_LUT = new double[SINE_SIZE + 1];

    static {
        for (int i = 0; i <= SINE_SIZE; i++) {
            SINE_LUT[i] = Math.sin(2 * Math.PI * i / SINE_SIZE);
        }
        for (int i = 0; i < VOICES; i++) {
            VOICE_POOL[i] = new Voice();
        }
    }

    private Audio() {
    }

    // -------------------------------------------------------------- lifecycle

    /** Opens the output line and starts rendering. Safe to call once. */
    public static void start() {
        if (running) {
            return;
        }
        try {
            AudioFormat fmt = new AudioFormat(RATE, 16, 2, true, false);
            line = AudioSystem.getSourceDataLine(fmt);
            line.open(fmt, BLOCK * BLOCKS_BUFFERED * 4);
            line.start();
            running = true;

            thread = new Thread(Audio::renderLoop, "audio");
            thread.setDaemon(true);
            thread.setPriority(Thread.MAX_PRIORITY);
            thread.start();
        } catch (Throwable t) {
            running = false;
            line = null;
        }
    }

    public static void stop() {
        running = false;
    }

    /** True once the output line is open and the render thread is alive. */
    public static boolean isRunning() {
        return running;
    }

    public static boolean isMuted() {
        return muted;
    }

    public static void toggleMute() {
        muted = !muted;
    }

    public static void setMuted(boolean value) {
        muted = value;
    }

    /** 0..1. Drives master filter opening and delay wetness. */
    public static void setIntensity(double v) {
        intensity = Math.max(0, Math.min(1, v));
    }

    public static void setMasterGain(double g) {
        masterGain = Math.max(0, Math.min(1.2, g));
    }

    public static void setMusicGain(double g) {
        musicGain = Math.max(0, Math.min(1.5, g));
    }

    public static void setSfxGain(double g) {
        sfxGain = Math.max(0, Math.min(1.5, g));
    }

    // ------------------------------------------------------------------- API

    /**
     * Triggers a note.
     *
     * @param patch    instrument
     * @param midi     pitch as a MIDI note number (fractional is fine)
     * @param velocity 0..1
     * @param hold     seconds to hold before the release stage
     * @param pan      -1 left to +1 right
     */
    public static void note(Patch patch, double midi, double velocity, double hold, double pan) {
        trigger(patch, midi, velocity * sfxGain, hold, pan);
    }

    /** As {@link #note}, but on the music bus. Used by {@link Music}. */
    public static void noteMusic(Patch patch, double midi, double velocity, double hold,
            double pan) {
        trigger(patch, midi, velocity * musicGain, hold, pan);
    }

    private static void trigger(Patch patch, double midi, double velocity, double hold,
            double pan) {
        if (!running || muted || velocity <= 0.001) {
            return;
        }
        // Never let a runaway caller grow the queue without bound.
        if (PENDING.get() > 192) {
            return;
        }
        PENDING.incrementAndGet();
        INBOX.add(new Event(patch, midi, Math.max(0, Math.min(1, velocity)), hold,
                Math.max(-1, Math.min(1, pan))));
    }

    public static void note(Patch patch, double midi, double velocity, double hold) {
        note(patch, midi, velocity, hold, 0);
    }

    public static double midiToHz(double midi) {
        return 440.0 * Math.pow(2, (midi - 69) / 12.0);
    }

    // ---------------------------------------------------------------- engine

    private static void renderLoop() {
        final float[] left = new float[BLOCK];
        final float[] right = new float[BLOCK];
        final byte[] out = new byte[BLOCK * 4];

        final int delayLen = RATE;
        final float[] delayL = new float[delayLen];
        final float[] delayR = new float[delayLen];
        int delayPos = 0;
        final int tapA = (int) (RATE * 0.263);
        final int tapB = (int) (RATE * 0.371);

        long framePos = 0;
        double noiseState = 12345.6789;

        while (running) {
            try {
                dispatchEvents();
                Music.tick(framePos, BLOCK);

                java.util.Arrays.fill(left, 0f);
                java.util.Arrays.fill(right, 0f);

                for (Voice v : VOICE_POOL) {
                    if (v.active) {
                        v.render(left, right, BLOCK);
                    }
                }

                double wet = 0.16 + 0.18 * intensity;
                double feedback = 0.30 + 0.14 * intensity;
                double gain = muted ? 0 : masterGain;

                for (int i = 0; i < BLOCK; i++) {
                    int ra = delayPos - tapA;
                    if (ra < 0) {
                        ra += delayLen;
                    }
                    int rb = delayPos - tapB;
                    if (rb < 0) {
                        rb += delayLen;
                    }

                    // Cross-fed taps give a wide ping-pong without a reverb.
                    float dl = delayR[ra];
                    float dr = delayL[rb];

                    float l = (float) (left[i] + dl * wet);
                    float r = (float) (right[i] + dr * wet);

                    delayL[delayPos] = (float) (left[i] + dl * feedback);
                    delayR[delayPos] = (float) (right[i] + dr * feedback);
                    delayPos++;
                    if (delayPos >= delayLen) {
                        delayPos = 0;
                    }

                    // Soft clip: cheap, and keeps a loud chord from crackling.
                    double sl = softClip(l * gain);
                    double sr = softClip(r * gain);

                    // A whisper of noise dither keeps quiet pads from quantising.
                    noiseState = noiseState * 16807 % 2147483647;
                    double d = (noiseState / 2147483647.0 - 0.5) * (1.0 / 32768.0);

                    int li = (int) (Math.max(-1, Math.min(1, sl + d)) * 32767);
                    int ri = (int) (Math.max(-1, Math.min(1, sr + d)) * 32767);

                    out[i * 4] = (byte) (li & 0xFF);
                    out[i * 4 + 1] = (byte) ((li >> 8) & 0xFF);
                    out[i * 4 + 2] = (byte) (ri & 0xFF);
                    out[i * 4 + 3] = (byte) ((ri >> 8) & 0xFF);
                }

                line.write(out, 0, out.length);
                framePos += BLOCK;
            } catch (Throwable t) {
                // A failing mixer must never take the game with it.
                running = false;
            }
        }
        try {
            if (line != null) {
                line.stop();
                line.close();
            }
        } catch (Throwable ignored) {
            // Shutting down anyway.
        }
    }

    private static void dispatchEvents() {
        Event e;
        while ((e = INBOX.poll()) != null) {
            PENDING.decrementAndGet();
            allocate().start(e);
        }
    }

    /** Steals the quietest voice when every slot is busy. */
    private static Voice allocate() {
        Voice best = null;
        double lowest = Double.MAX_VALUE;
        for (Voice v : VOICE_POOL) {
            if (!v.active) {
                return v;
            }
            double score = v.env * v.velocity + (v.stage == 3 ? 0 : 0.5);
            if (score < lowest) {
                lowest = score;
                best = v;
            }
        }
        return best;
    }

    private static double softClip(double x) {
        return x / (1.0 + Math.abs(x));
    }

    private static double sine(double phase) {
        double f = phase * SINE_SIZE;
        int i = (int) f;
        double frac = f - i;
        i &= SINE_SIZE - 1;
        return SINE_LUT[i] + (SINE_LUT[i + 1] - SINE_LUT[i]) * frac;
    }

    /**
     * PolyBLEP band-limited step correction.
     *
     * <p>A naive saw or square is a stack of aliased partials that fold back down
     * the spectrum and sound gritty and out of tune at high pitches. Subtracting
     * a polynomial approximation of the band-limited step around each
     * discontinuity removes most of that for a handful of arithmetic ops.</p>
     */
    private static double polyBlep(double t, double dt) {
        if (dt <= 0) {
            return 0;
        }
        if (t < dt) {
            double x = t / dt;
            return x + x - x * x - 1.0;
        }
        if (t > 1.0 - dt) {
            double x = (t - 1.0) / dt;
            return x * x + x + x + 1.0;
        }
        return 0;
    }

    /** One polyphonic voice: two detuned oscillators, ADSR, resonant lowpass. */
    private static final class Voice {

        private boolean active;
        private Patch patch;
        private double phase;
        private double phase2;
        private double baseFreq;
        private double velocity;
        private double panL = 1;
        private double panR = 1;

        private double env;
        private int stage;
        private double holdLeft;

        private double low;
        private double band;
        private double filterEnv;

        private long noise = 88172645463325252L;

        void start(Event e) {
            patch = e.patch();
            baseFreq = midiToHz(e.midi());
            velocity = e.velocity();
            holdLeft = e.hold();

            phase = 0;
            phase2 = 0.37;
            env = 0;
            stage = 0;
            filterEnv = 1;
            low = 0;
            band = 0;

            double p = e.pan();
            panL = Math.sqrt(Math.max(0, 1 - p) * 0.5 + 0.25);
            panR = Math.sqrt(Math.max(0, 1 + p) * 0.5 + 0.25);
            active = true;
        }

        void render(float[] left, float[] right, int n) {
            final double sr = RATE;
            final double detune = Math.pow(2, patch.detuneCents / 1200.0);
            final double amp = patch.level * velocity;

            for (int i = 0; i < n; i++) {
                // --- envelope -------------------------------------------------
                switch (stage) {
                    case 0 -> {
                        env += 1.0 / Math.max(1e-4, patch.attack) / sr;
                        if (env >= 1) {
                            env = 1;
                            stage = 1;
                        }
                    }
                    case 1 -> {
                        env -= (1.0 - patch.sustain) / Math.max(1e-4, patch.decay) / sr;
                        if (env <= patch.sustain) {
                            env = patch.sustain;
                            stage = 2;
                        }
                    }
                    case 2 -> {
                        holdLeft -= 1.0 / sr;
                        if (holdLeft <= 0) {
                            stage = 3;
                        }
                    }
                    default -> {
                        env -= Math.max(patch.sustain, 1e-3)
                                / Math.max(1e-4, patch.release) / sr;
                        if (env <= 0.0005) {
                            active = false;
                            return;
                        }
                    }
                }
                if (stage == 1 && patch.decay <= 0) {
                    stage = 2;
                }

                filterEnv -= filterEnv * (3.0 / sr) * 2.0;

                // --- pitch ----------------------------------------------------
                double freq = baseFreq;
                if (patch.pitchEnv > 0) {
                    freq *= Math.pow(2, patch.pitchEnv * filterEnv / 12.0);
                }
                double dt = freq / sr;
                double dt2 = freq * detune / sr;

                phase += dt;
                if (phase >= 1) {
                    phase -= 1;
                }
                phase2 += dt2;
                if (phase2 >= 1) {
                    phase2 -= 1;
                }

                // --- oscillators ----------------------------------------------
                double s;
                switch (patch.wave) {
                    case SAW -> s = 0.5 * ((2 * phase - 1 - polyBlep(phase, dt))
                            + (2 * phase2 - 1 - polyBlep(phase2, dt2)));
                    case SQUARE -> {
                        double a = (phase < 0.5 ? 1 : -1)
                                + polyBlep(phase, dt)
                                - polyBlep((phase + 0.5) % 1.0, dt);
                        double b = (phase2 < 0.5 ? 1 : -1)
                                + polyBlep(phase2, dt2)
                                - polyBlep((phase2 + 0.5) % 1.0, dt2);
                        s = 0.5 * (a + b) * 0.7;
                    }
                    case TRIANGLE -> s = (4 * Math.abs(phase - 0.5) - 1);
                    case NOISE -> s = 0;
                    default -> s = 0.5 * (sine(phase) + sine(phase2));
                }

                if (patch.noiseMix > 0) {
                    noise ^= noise << 13;
                    noise ^= noise >>> 7;
                    noise ^= noise << 17;
                    double nz = (noise >> 40) / 8388608.0;
                    s = s * (1 - patch.noiseMix) + nz * patch.noiseMix;
                }

                // --- resonant lowpass (Chamberlin state variable) --------------
                double cut = patch.cutoff + patch.filterEnv * filterEnv;
                cut = Math.max(40, Math.min(sr * 0.45, cut));
                double f = 2 * Math.sin(Math.PI * cut / sr);
                double q = 1.0 - Math.max(0, Math.min(0.95, patch.resonance));
                low += f * band;
                double high = s - low - q * band;
                band += f * high;
                double filtered = low;

                double value = filtered * env * amp;
                left[i] += (float) (value * panL);
                right[i] += (float) (value * panR);
            }
        }
    }
}
