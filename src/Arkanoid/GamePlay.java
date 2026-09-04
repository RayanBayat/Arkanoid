package Arkanoid;

import java.awt.BasicStroke;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * The game: run structure, simulation, rendering and input.
 *
 * <p>Everything content-shaped is generated. {@link LevelGen} builds the board,
 * {@link Palette} colours it, {@link Music} scores it, {@link SkillModel} aims
 * its difficulty, and {@link Relic} decides what a particular run can do. A run
 * is fully reproducible from its seed.</p>
 */
@SuppressWarnings("serial") // An AWT Canvas is Serializable; a live game canvas never is.
public final class GamePlay extends Canvas implements Runnable, KeyListener,
        MouseListener, MouseMotionListener {

    private static final int W = Field.W;
    private static final int H = Field.H;
    private static final int HUD_H = Field.HUD_H;
    private static final int WALL = Field.WALL;
    private static final int CEIL = Field.CEIL;
    private static final double PADDLE_Y = Field.PADDLE_Y;

    private static final double PHYSICS_STEP = 1.0 / 120.0;
    private static final long FRAME_NANOS = 1_000_000_000L / 144L;

    private static final Path SCORE_FILE = Paths.get("Lastscore");

    /** Enables the level-skip key. Run with -Darkanoid.debug=true to test transitions. */
    private static final boolean DEBUG = Boolean.getBoolean("arkanoid.debug");

    /** Below this line, a falling ball can trigger time dilation. */
    private static final double CLUTCH_LINE = H * 0.80;

    private enum State { TITLE, SETTINGS, ABOUT, READY, PLAYING, PAUSED, LEVEL_CLEAR, DRAFT, GAME_OVER }

    // ------------------------------------------------------------------ fonts

    private static final String UI_FAMILY =
            pickFamily("Bahnschrift", "Segoe UI Semibold", "Segoe UI", "Trebuchet MS");
    private static final String NUM_FAMILY =
            pickFamily("Consolas", "Lucida Console", "DejaVu Sans Mono");

    private final Font hugeFont = new Font(UI_FAMILY, Font.BOLD, 74);
    private final Font bigFont = new Font(UI_FAMILY, Font.BOLD, 40);
    private final Font midFont = new Font(UI_FAMILY, Font.BOLD, 23);
    private final Font smallFont = new Font(UI_FAMILY, Font.PLAIN, 15);
    private final Font tinyFont = new Font(UI_FAMILY, Font.PLAIN, 13);
    private final Font labelFont = new Font(UI_FAMILY, Font.BOLD, 11);
    private final Font valueFont = new Font(NUM_FAMILY, Font.BOLD, 25);
    private final Font badgeFont = new Font(NUM_FAMILY, Font.BOLD, 14);
    private final Font popupFont = new Font(NUM_FAMILY, Font.BOLD, 17);

    // ------------------------------------------------------------ presentation

    private final double scale;
    private final BufferedImage scene;
    private final Bloom bloom;
    private final BrickSprites brickSprites = new BrickSprites();
    private final Backdrop backdrop = new Backdrop(W, H);
    private Thread thread;
    private volatile boolean running;

    private double frameMillis = 8;
    private int slowFrames;
    private int faults;
    private double bloomMillis;
    private double lastTimingLog;

    // ------------------------------------------------------------------ state

    private State state = State.TITLE;
    private final Settings settings = new Settings();
    private final MenuScreen menu = new MenuScreen();
    private double stateTime;
    private double time;

    private long runSeed;
    private Rng runRng = new Rng(1);
    private final SkillModel skill = new SkillModel();
    private final Relic.Loadout loadout = new Relic.Loadout();
    private List<Relic> offers = new ArrayList<>();
    private int offerIndex;

    private LevelGen.Board board;
    private Palette palette = Palette.generate(new Rng(1), 5);
    private final List<Bricks> bricks = new ArrayList<>();
    private final Physics.Grid grid = new Physics.Grid();
    private final Physics.Hit hit = new Physics.Hit();
    private final List<Bricks> scratchBricks = new ArrayList<>();

    private final List<Ball> balls = new ArrayList<>();
    /**
     * Balls spawned mid-simulation. Relics like SPLIT_SHOT and the multiball
     * capsule can create balls from deep inside a collision callback, which used
     * to mutate {@link #balls} while it was being iterated and kill the game
     * thread. Spawns land here and are folded in at a safe point.
     */
    private final List<Ball> pendingBalls = new ArrayList<>();
    private final List<PowerupBall> powerups = new ArrayList<>();
    private final List<Bullet> bullets = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<Popup> popups = new ArrayList<>();
    private final List<Shockwave> shockwaves = new ArrayList<>();
    private final EnumMap<PowerupBall.Kind, Double> timers =
            new EnumMap<>(PowerupBall.Kind.class);

    private Paddle paddle = new Paddle(W / 2.0, PADDLE_Y);

    private int score;
    private int highScore;
    private int lives;
    private int level;
    private int combo;
    private int bestCombo;
    private int runBestCombo;
    private int bricksBroken;
    private int bricksSinceSplit;
    private int bricksSinceSiphon;
    private boolean newRecord;
    private boolean phoenixReady;
    private double baseSpeed = 340;

    private int levelDeaths;
    private double levelTime;
    private int levelBricks;
    private int paddleApproaches;
    private int paddleCatches;

    private double clutch = 1;
    private double dilation = 1;
    private double shake;
    private double flashAlpha;
    private Color flashColor = Color.WHITE;
    private String banner = "";
    private String bannerSub = "";
    private double bannerTime;

    // ------------------------------------------------------------------ input

    private volatile boolean leftDown;
    private volatile boolean rightDown;
    private volatile boolean fireDown;
    private volatile boolean mouseControl;
    private volatile double mouseX = W / 2.0;
    private volatile double mouseY;
    private final ConcurrentLinkedQueue<Integer> actions = new ConcurrentLinkedQueue<>();

    // ----------------------------------------------------------------- set-up

    private GamePlay(double scale) {
        this.scale = scale;
        this.scene = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        this.bloom = new Bloom(W, H);

        setPreferredSize(new Dimension((int) Math.round(W * scale), (int) Math.round(H * scale)));
        setIgnoreRepaint(true);
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);
        setCursor(blankCursor());

        highScore = loadHighScore();
        applySettings();
        newSeed();
    }

    /** Builds the window and starts everything. */
    public static void init() {
        double s = 1.0;
        try {
            Rectangle b = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            s = Math.min(1.0, Math.min((b.height - 48) / (double) H, b.width / (double) W));
            s = Math.max(0.5, s);
        } catch (Throwable ignored) {
            // Odd display setup; 1:1 is a safe default.
        }

        final double scale = s;
        SwingUtilities.invokeLater(() -> {
            GamePlay game = new GamePlay(scale);

            JFrame frame = new JFrame("ARKANOID // RESONANCE");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            frame.setIgnoreRepaint(true);
            frame.add(game);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            game.requestFocusInWindow();

            Runtime.getRuntime().addShutdownHook(new Thread(game::saveHighScore));
            Audio.start();
            Music.setEnabled(true);
            game.start();
        });
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this, "game-loop");
        thread.start();
    }

    // ------------------------------------------------------------------- loop

    @Override
    public void run() {
        long last = System.nanoTime();
        double accumulator = 0;

        while (running) {
            long now = System.nanoTime();
            double elapsed = (now - last) / 1_000_000_000.0;
            last = now;
            if (elapsed > 0.25) {
                elapsed = 0.25;
            }

            accumulator += elapsed;
            int steps = 0;
            while (accumulator >= PHYSICS_STEP && steps < 8) {
                try {
                    update(PHYSICS_STEP);
                } catch (RuntimeException ex) {
                    reportFault("update", ex);
                }
                accumulator -= PHYSICS_STEP;
                steps++;
            }

            long beforeRender = System.nanoTime();
            renderSafely();
            double ms = (System.nanoTime() - beforeRender) / 1_000_000.0;
            frameMillis += (ms - frameMillis) * 0.05;
            adaptQuality();

            if (DEBUG && time - lastTimingLog > 3.0) {
                lastTimingLog = time;
                System.out.printf("[timing] frame %.2f ms  bloom %.2f ms (%s)  bricks %d  "
                        + "sprites %d  balls %d  parts %d%n",
                        frameMillis, bloomMillis, bloom.isEnabled() ? "on" : "off",
                        bricks.size(), brickSprites.size(), balls.size(), particles.size());
            }

            long sleep = (now + FRAME_NANOS - System.nanoTime()) / 1_000_000L;
            if (sleep > 0) {
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    running = false;
                }
            }
        }
    }

    private void renderSafely() {
        try {
            render();
        } catch (RuntimeException ex) {
            reportFault("render", ex);
        }
    }

    /**
     * Keeps the loop alive after an unexpected failure.
     *
     * <p>An exception escaping the game thread used to kill it outright, which
     * leaves the window on screen but frozen and gives the player no idea what
     * happened. Logging and carrying on is strictly better: worst case the frame
     * is wrong, best case it was a transient and play continues.</p>
     */
    private void reportFault(String phase, RuntimeException ex) {
        if (faults < 5) {
            System.err.println("[arkanoid] recovered from a fault during " + phase + ":");
            ex.printStackTrace();
        }
        faults++;
    }

    /** Drops the bloom pass if this machine cannot keep up with it. */
    private void adaptQuality() {
        if (!bloom.isEnabled()) {
            return;
        }
        // Judge the bloom pass on its own cost, not on total frame time: a slow
        // frame caused by something else should not switch off the glow.
        if (frameMillis > 13.5 && bloomMillis > 5.0) {
            slowFrames++;
            if (slowFrames > 240) {
                bloom.setEnabled(false);
                showBanner("PERFORMANCE MODE", "bloom off - press B to force it back on");
            }
        } else {
            slowFrames = Math.max(0, slowFrames - 2);
        }
    }

    // ----------------------------------------------------------------- update

    private void update(double dt) {
        handleActions();
        time += dt;
        stateTime += dt;

        shake = Math.max(0, shake - dt * 26);
        flashAlpha = Math.max(0, flashAlpha - dt * 2.2);
        bannerTime = Math.max(0, bannerTime - dt);

        for (Iterator<Particle> it = particles.iterator(); it.hasNext();) {
            Particle p = it.next();
            p.update(dt);
            if (p.isDead()) {
                it.remove();
            }
        }
        for (Iterator<Popup> it = popups.iterator(); it.hasNext();) {
            Popup p = it.next();
            p.update(dt);
            if (p.life <= 0) {
                it.remove();
            }
        }
        for (Iterator<Shockwave> it = shockwaves.iterator(); it.hasNext();) {
            Shockwave s = it.next();
            s.update(dt);
            if (s.life <= 0) {
                it.remove();
            }
        }
        for (Bricks b : bricks) {
            b.update(dt);
        }

        switch (state) {
            case READY -> {
                movePaddle(dt);
                carryHeldBalls();
            }
            case PLAYING -> updatePlaying(dt);
            case LEVEL_CLEAR -> {
                if (stateTime > 1.9) {
                    beginDraft();
                }
            }
            default -> { }
        }
        updateMusicIntensity();
    }

    private void updatePlaying(double dt) {
        updateDilation(dt);
        double sim = dt * dilation;
        levelTime += dt;

        movePaddle(dt);
        tickTimers(sim);
        carryHeldBalls();

        if (fireDown && paddle.tryFire()) {
            bullets.add(new Bullet(paddle.left() + 8, paddle.top() - 8));
            bullets.add(new Bullet(paddle.right() - 8, paddle.top() - 8));
            Audio.note(Audio.Patch.ZAP, 84, 0.30, 0.02, panOf(paddle.getX()));
        }

        // Indexed rather than an iterator: collision callbacks below can spawn
        // balls, and this loop must tolerate that without exploding.
        for (int i = 0; i < balls.size(); i++) {
            Ball b = balls.get(i);
            if (b.isHeld()) {
                continue;
            }
            applyCurve(b, sim);
            moveBall(b, sim);
            hitPaddle(b);
            if (b.getY() - Ball.RADIUS > H) {
                balls.remove(i--);
                Audio.note(Audio.Patch.SWEEP, 34, 0.35, 0.25, panOf(b.getX()));
            }
        }
        flushPendingBalls();

        if (balls.isEmpty()) {
            loseBall();
            return;
        }

        for (Iterator<Bullet> it = bullets.iterator(); it.hasNext();) {
            Bullet s = it.next();
            s.update(sim);
            if (s.getY() < CEIL - 20) {
                it.remove();
                continue;
            }
            boolean consumed = false;
            for (Bricks br : bricks) {
                if (br.isAlive() && s.hits(br)) {
                    strike(br, loadout.ballDamage(), s.getX(), s.getY());
                    consumed = true;
                    break;
                }
            }
            if (consumed) {
                it.remove();
            }
        }

        boolean magnet = loadout.has(Relic.MAGNETISM);
        for (Iterator<PowerupBall> it = powerups.iterator(); it.hasNext();) {
            PowerupBall p = it.next();
            p.update(sim);
            if (magnet) {
                double dx = paddle.getX() - p.getX();
                p.setX(p.getX() + Math.max(-90, Math.min(90, dx)) * sim * 1.4);
            }
            if (p.getY() - PowerupBall.H > H) {
                it.remove();
            } else if (p.caughtBy(paddle)) {
                collect(p);
                it.remove();
            }
        }

        flushPendingBalls();

        bricks.removeIf(b -> {
            if (b.isFinished()) {
                grid.remove(b);
                return true;
            }
            return false;
        });

        boolean anyBreakable = false;
        for (Bricks b : bricks) {
            if (b.isBreakable()) {
                anyBreakable = true;
                break;
            }
        }
        if (!anyBreakable) {
            levelClear();
        }
    }

    /**
     * Time dilation. When a ball is falling past the clutch line the world slows
     * and the meter drains, which turns the moment that used to be a coin flip
     * into a save you can actually make.
     */
    private void updateDilation(double dt) {
        if (!settings.on(Settings.DILATION)) {
            dilation += (1.0 - dilation) * Math.min(1, dt * 12);
            return;
        }
        boolean danger = false;
        for (Ball b : balls) {
            if (!b.isHeld() && b.getVy() > 0 && b.getY() > CLUTCH_LINE) {
                danger = true;
                break;
            }
        }
        double target = 1.0;
        if (danger && clutch > 0.02) {
            target = 0.44 / loadout.dilationMultiplier();
            clutch = Math.max(0, clutch - dt * 0.40);
        } else {
            clutch = Math.min(1, clutch + dt * 0.10 * loadout.dilationMultiplier());
        }
        dilation += (target - dilation) * Math.min(1, dt * 12);
    }

    /** SINGULARITY bends the ball towards whatever mass is left. */
    private void applyCurve(Ball b, double dt) {
        if (!loadout.has(Relic.SINGULARITY) || bricks.isEmpty()) {
            return;
        }
        double cx = 0;
        double cy = 0;
        int n = 0;
        for (Bricks br : bricks) {
            if (br.isAlive() && br.isBreakable()) {
                cx += br.centerX();
                cy += br.centerY();
                n++;
            }
        }
        if (n == 0) {
            return;
        }
        cx /= n;
        cy /= n;
        double dx = cx - b.getX();
        double dy = cy - b.getY();
        double len = Math.hypot(dx, dy);
        if (len < 1) {
            return;
        }
        double pull = 150 * dt;
        double speed = b.speed();
        b.setVelocity(b.getVx() + dx / len * pull, b.getVy() + dy / len * pull);
        b.setSpeed(speed);
    }

    /**
     * Advances one ball with continuous collision, resolving a few contacts per
     * step so a corner cannot swallow the rest of the frame.
     */
    private void moveBall(Ball b, double dt) {
        double remaining = dt;
        for (int iteration = 0; iteration < 4 && remaining > 1e-6; iteration++) {
            double dx = b.getVx() * remaining;
            double dy = b.getVy() * remaining;

            if (Physics.sweep(grid, b.getX(), b.getY(), dx, dy, Ball.RADIUS, hit)) {
                double t = Math.max(0, hit.t - 1e-4);
                b.setX(b.getX() + dx * t);
                b.setY(b.getY() + dy * t);

                Bricks br = hit.brick;
                boolean pierced = b.consumePierce();
                if (!pierced) {
                    double dot = b.getVx() * hit.normalX + b.getVy() * hit.normalY;
                    if (dot < 0) {
                        b.setVelocity(b.getVx() - 2 * dot * hit.normalX,
                                b.getVy() - 2 * dot * hit.normalY);
                    }
                    b.enforceMinAngle();
                }
                strike(br, loadout.ballDamage(), b.getX(), b.getY());
                remaining *= (1 - t);
                if (pierced) {
                    // Keep travelling through the brick instead of stopping dead.
                    b.setX(b.getX() + b.getVx() * remaining * 0.25);
                    b.setY(b.getY() + b.getVy() * remaining * 0.25);
                    remaining *= 0.75;
                }
            } else {
                b.setX(b.getX() + dx);
                b.setY(b.getY() + dy);
                remaining = 0;
            }
        }
        b.pushTrail();
        bounceWalls(b);
    }

    private void bounceWalls(Ball b) {
        if (b.getX() - Ball.RADIUS < WALL) {
            b.setX(WALL + Ball.RADIUS);
            b.setVelocity(Math.abs(b.getVx()), b.getVy());
            wallTick(b);
        } else if (b.getX() + Ball.RADIUS > W - WALL) {
            b.setX(W - WALL - Ball.RADIUS);
            b.setVelocity(-Math.abs(b.getVx()), b.getVy());
            wallTick(b);
        }
        if (b.getY() - Ball.RADIUS < CEIL) {
            b.setY(CEIL + Ball.RADIUS);
            b.setVelocity(b.getVx(), Math.abs(b.getVy()));
            wallTick(b);
        }
    }

    private void wallTick(Ball b) {
        Audio.note(Audio.Patch.HAT, 78, 0.16, 0.01, panOf(b.getX()));
        for (int i = 0; i < 3; i++) {
            particles.add(new Particle(b.getX(), b.getY(),
                    runRng.gauss() * 40, runRng.gauss() * 40,
                    0.22, 2.2, palette.accentSoft));
        }
    }

    private void movePaddle(double dt) {
        int control = settings.get(Settings.CONTROL);
        boolean keysEnabled = control != Settings.CONTROL_MOUSE;
        boolean mouseEnabled = control != Settings.CONTROL_KEYS;
        double dir = 0;
        if (keysEnabled && leftDown) {
            dir -= 1;
        }
        if (keysEnabled && rightDown) {
            dir += 1;
        }
        if (dir != 0) {
            mouseControl = false;
            paddle.setVx(dir * Paddle.SPEED * loadout.paddleSpeedMultiplier());
        } else {
            paddle.setVx(0);
        }
        paddle.update(dt);
        if (mouseEnabled && mouseControl) {
            double sensitivity = settings.factor(Settings.SENSITIVITY);
            paddle.setX(W / 2.0 + (mouseX - W / 2.0) * sensitivity);
        }
        paddle.clamp(WALL, W - WALL);
    }

    private void carryHeldBalls() {
        for (Ball b : balls) {
            if (b.isHeld()) {
                double half = paddle.getWidth() / 2 - Ball.RADIUS;
                double off = Math.max(-half, Math.min(half, b.getHoldOffset()));
                b.setX(paddle.getX() + off);
                b.setY(paddle.top() - Ball.RADIUS - 1);
                b.snapTrail();
            }
        }
    }

    private void hitPaddle(Ball b) {
        if (b.isHeld() || b.getVy() <= 0) {
            return;
        }
        double top = paddle.top();
        if (b.getY() + Ball.RADIUS < top || b.getY() - Ball.RADIUS > top + Paddle.HEIGHT) {
            return;
        }
        if (b.getX() < paddle.left() - Ball.RADIUS || b.getX() > paddle.right() + Ball.RADIUS) {
            return;
        }

        paddleApproaches++;
        paddleCatches++;

        b.setY(top - Ball.RADIUS);
        double t = (b.getX() - paddle.getX()) / (paddle.getWidth() / 2 + Ball.RADIUS);
        t = Math.max(-1, Math.min(1, t));
        double angle = Math.toRadians(-90 + t * 62);
        double speed = Math.max(baseSpeed, Math.min(Ball.MAX_SPEED, b.speed() * 1.012));
        b.setVelocity(Math.cos(angle) * speed, Math.sin(angle) * speed);
        b.enforceMinAngle();
        b.resetPierce(loadout.has(Relic.PIERCE) ? 1 : 0);

        if (!loadout.has(Relic.METRONOME)) {
            combo = 0;
        }
        if (paddle.isSticky()) {
            b.hold(b.getX() - paddle.getX());
        }

        for (int i = 0; i < 7; i++) {
            particles.add(new Particle(b.getX(), top,
                    runRng.gauss() * 70, -50 - runRng.nextDouble() * 100,
                    0.3 + runRng.nextDouble() * 0.22, 3, palette.paddleTop));
        }
        shake = Math.max(shake, 2);
        Audio.note(Audio.Patch.KICK, 40, 0.34, 0.02, panOf(b.getX()));
    }

    // ----------------------------------------------------------------- bricks

    /** Applies damage and everything that follows from it. */
    private void strike(Bricks br, int damage, double atX, double atY) {
        if (!br.isAlive()) {
            return;
        }
        if (br.isSolid()) {
            br.hit(damage);
            Audio.note(Audio.Patch.IMPACT, 30, 0.22, 0.05, panOf(atX));
            shake = Math.max(shake, 3);
            return;
        }

        if (!br.hit(damage)) {
            Audio.note(Audio.Patch.ZAP, 52 + br.getScaleDegree(), 0.22, 0.03, panOf(atX));
            shake = Math.max(shake, 2);
            for (int i = 0; i < 5; i++) {
                particles.add(new Particle(atX, atY, runRng.gauss() * 90, runRng.gauss() * 90,
                        0.28, 2.5, br.getColor()));
            }
            return;
        }
        destroy(br);
    }

    /**
     * Finalises a killed brick: score, particles, the note it plays, drops, and
     * any chain reaction it sets off.
     */
    private void destroy(Bricks br) {
        combo++;
        bestCombo = Math.max(bestCombo, combo);
        runBestCombo = Math.max(runBestCombo, combo);
        bricksBroken++;
        levelBricks++;

        int gain = (int) Math.round(br.baseValue() * Math.max(1, combo)
                * loadout.scoreMultiplier());
        score += gain;
        popups.add(new Popup(br.centerX(), br.centerY(), "+" + gain, br.getColor()));

        // The board is tuned: each brick is a scale degree, and a long combo
        // lifts the run by an octave. Clearing well plays a melody.
        int octaveLift = Math.min(2, combo / 7);
        Audio.note(Audio.Patch.PLUCK,
                Music.scaleNote(br.getScaleDegree(), 1 + octaveLift),
                0.45 + Math.min(0.4, combo * 0.03), 0.10, panOf(br.centerX()));

        burst(br);
        clutch = Math.min(1, clutch + 0.05);

        if (runRng.chance(0.20)) {
            powerups.add(new PowerupBall(br.centerX(), br.centerY(),
                    PowerupBall.Kind.random(runRng)));
        }

        grid.remove(br);
        shake = Math.max(shake, 4);

        if (br.getKind() == Bricks.Kind.EXPLOSIVE) {
            explode(br);
        }
        if (loadout.has(Relic.ECHO)) {
            scratchBricks.clear();
            grid.neighbours(br, scratchBricks, 1);
            if (!scratchBricks.isEmpty()) {
                Bricks victim = scratchBricks.get(runRng.nextInt(scratchBricks.size()));
                if (victim.isBreakable() && victim.hit(1)) {
                    destroy(victim);
                }
            }
        }

        if (loadout.has(Relic.SPLIT_SHOT)) {
            bricksSinceSplit++;
            if (bricksSinceSplit >= 6) {
                bricksSinceSplit = 0;
                spawnExtraBall();
            }
        }
        if (loadout.has(Relic.SIPHON)) {
            bricksSinceSiphon++;
            if (bricksSinceSiphon >= 45) {
                bricksSinceSiphon = 0;
                lives++;
                popups.add(new Popup(paddle.getX(), PADDLE_Y - 40, "SIPHON +1", palette.accent));
                Audio.note(Audio.Patch.BELL, 84, 0.5, 0.4, 0);
            }
        }
    }

    /** Explosive bricks damage a radius, and set off other explosives. */
    private void explode(Bricks source) {
        double radius = LevelGen.BRICK_W * 1.35 * loadout.blastRadiusMultiplier();
        shockwaves.add(new Shockwave(source.centerX(), source.centerY(), radius * 1.5,
                Palette.mix(source.getColor(), Color.WHITE, 0.4)));
        shake = Math.max(shake, 11);
        flash(Palette.mix(source.getColor(), Color.WHITE, 0.5), 0.22);
        Audio.note(Audio.Patch.IMPACT, 28, 0.85, 0.16, panOf(source.centerX()));

        for (int i = 0; i < 34; i++) {
            double a = runRng.nextDouble() * Math.PI * 2;
            double sp = 120 + runRng.nextDouble() * 420;
            particles.add(new Particle(source.centerX(), source.centerY(),
                    Math.cos(a) * sp, Math.sin(a) * sp,
                    0.4 + runRng.nextDouble() * 0.5, 2 + runRng.nextDouble() * 5,
                    Palette.mix(source.getColor(), Color.WHITE, runRng.nextDouble() * 0.6)));
        }

        List<Bricks> caught = new ArrayList<>();
        for (Bricks b : bricks) {
            if (b == source || !b.isAlive() || !b.isBreakable()) {
                continue;
            }
            double dx = b.centerX() - source.centerX();
            double dy = b.centerY() - source.centerY();
            if (dx * dx + dy * dy <= radius * radius) {
                caught.add(b);
            }
        }
        for (Bricks b : caught) {
            if (b.isAlive() && b.hit(2)) {
                destroy(b);
            }
        }
    }

    private void burst(Bricks br) {
        for (int i = 0; i < 18; i++) {
            double a = runRng.nextDouble() * Math.PI * 2;
            double sp = 70 + runRng.nextDouble() * 240;
            particles.add(new Particle(br.centerX(), br.centerY(),
                    Math.cos(a) * sp, Math.sin(a) * sp - 60,
                    0.45 + runRng.nextDouble() * 0.45,
                    2 + runRng.nextDouble() * 4, br.getColor()));
        }
        shockwaves.add(new Shockwave(br.centerX(), br.centerY(), 46, br.getColor()));
    }

    // -------------------------------------------------------------- power-ups

    private void collect(PowerupBall p) {
        PowerupBall.Kind k = p.getKind();
        popups.add(new Popup(p.getX(), p.getY() - 16, k.label(), k.color()));
        flash(k.color(), 0.20);

        // A little arpeggio in key: up for a boon, down for a bane.
        for (int i = 0; i < 3; i++) {
            int deg = k.isGood() ? i * 2 : 4 - i * 2;
            Audio.note(Audio.Patch.BELL, Music.scaleNote(deg, 2), 0.34, 0.16, panOf(p.getX()));
        }

        switch (k) {
            case EXPAND -> {
                timers.remove(PowerupBall.Kind.SHRINK);
                timers.put(k, 14.0);
                paddle.setTargetWidth(Paddle.WIDE_WIDTH * loadout.paddleWidthMultiplier());
            }
            case SHRINK -> {
                timers.remove(PowerupBall.Kind.EXPAND);
                timers.put(k, 9.0);
                paddle.setTargetWidth(Paddle.NARROW_WIDTH * loadout.paddleWidthMultiplier());
            }
            case CATCH -> {
                timers.put(k, 14.0);
                paddle.setSticky(true);
            }
            case LASER -> {
                timers.put(k, 12.0);
                paddle.setLaser(true);
            }
            case MULTI -> multiball();
            case SLOW -> scaleBalls(0.74);
            case FAST -> scaleBalls(1.26);
            case LIFE -> lives++;
            default -> { }
        }
    }

    private void tickTimers(double dt) {
        for (Iterator<Map.Entry<PowerupBall.Kind, Double>> it = timers.entrySet().iterator();
                it.hasNext();) {
            Map.Entry<PowerupBall.Kind, Double> e = it.next();
            double left = e.getValue() - dt;
            if (left <= 0) {
                expire(e.getKey());
                it.remove();
            } else {
                e.setValue(left);
            }
        }
    }

    private void expire(PowerupBall.Kind k) {
        switch (k) {
            case EXPAND, SHRINK ->
                    paddle.setTargetWidth(Paddle.BASE_WIDTH * loadout.paddleWidthMultiplier());
            case CATCH -> {
                if (!loadout.has(Relic.STICKY_FINGERS)) {
                    paddle.setSticky(false);
                }
                for (Ball b : balls) {
                    if (b.isHeld()) {
                        b.release(Math.max(baseSpeed, 320), Math.toRadians(-90));
                    }
                }
            }
            case LASER -> {
                if (!loadout.has(Relic.ARSENAL)) {
                    paddle.setLaser(false);
                }
            }
            default -> { }
        }
    }

    private void multiball() {
        int split = loadout.multiballSplit();
        List<Ball> extra = new ArrayList<>();
        for (Ball b : balls) {
            if (balls.size() + pendingBalls.size() + extra.size() >= 12) {
                break;
            }
            double speed = Math.max(b.speed(), baseSpeed);
            double angle = b.isHeld() ? Math.toRadians(-90) : Math.atan2(b.getVy(), b.getVx());
            for (int i = 1; i < split; i++) {
                double delta = (i - (split - 1) / 2.0) * 0.40;
                Ball n = new Ball(b.getX(), b.getY(), 0, 0);
                n.release(speed, angle + delta);
                n.enforceMinAngle();
                extra.add(n);
            }
        }
        pendingBalls.addAll(extra);
        if (state == State.READY && !extra.isEmpty()) {
            setState(State.PLAYING);
        }
    }

    private void spawnExtraBall() {
        if (balls.size() + pendingBalls.size() >= 12 || balls.isEmpty()) {
            return;
        }
        Ball from = balls.get(runRng.nextInt(balls.size()));
        Ball n = new Ball(from.getX(), from.getY(), 0, 0);
        n.release(Math.max(from.speed(), baseSpeed),
                Math.atan2(from.getVy(), from.getVx()) + runRng.range(-0.7, 0.7));
        n.enforceMinAngle();
        pendingBalls.add(n);
        Audio.note(Audio.Patch.BELL, Music.scaleNote(4, 2), 0.3, 0.2, 0);
    }

    private void flushPendingBalls() {
        if (!pendingBalls.isEmpty()) {
            balls.addAll(pendingBalls);
            pendingBalls.clear();
        }
    }

    private void scaleBalls(double factor) {
        for (Ball b : balls) {
            b.setSpeed(Math.max(260, Math.min(Ball.MAX_SPEED, b.speed() * factor)));
        }
    }

    // ------------------------------------------------------------------- flow

    private void newSeed() {
        runSeed = System.nanoTime() ^ (System.currentTimeMillis() << 21);
        runRng = new Rng(runSeed);
        palette = Palette.generate(new Rng(runSeed).fork(3), 5);
        backdrop.rebuild(palette, runSeed);
        brickSprites.clear();
        Music.newSection(new Rng(runSeed).fork(11), 1);
    }

    private void startRun() {
        runRng = new Rng(runSeed);
        loadout.clear();
        score = 0;
        level = 1;
        combo = 0;
        bestCombo = 0;
        runBestCombo = 0;
        bricksBroken = 0;
        bricksSinceSplit = 0;
        bricksSinceSiphon = 0;
        newRecord = false;
        lives = settings.get(Settings.LIVES);
        clutch = 1;
        loadLevel();
    }

    private void loadLevel() {
        board = LevelGen.generate(runSeed, level, skill.difficulty());
        palette = board.palette();

        bricks.clear();
        bricks.addAll(board.bricks());
        grid.rebuild(bricks);
        brickSprites.clear(); // the palette changed, so every baked face is stale
        backdrop.rebuild(palette, runSeed ^ (level * 2654435761L));

        balls.clear();
        powerups.clear();
        bullets.clear();
        particles.clear();
        shockwaves.clear();
        timers.clear();

        baseSpeed = board.ballSpeed() * loadout.ballSpeedMultiplier();
        paddle.clearEffects();
        paddle.setTargetWidth(Paddle.BASE_WIDTH * loadout.paddleWidthMultiplier());
        paddle.setX(W / 2.0);
        if (loadout.has(Relic.ARSENAL)) {
            paddle.setLaser(true);
        }
        if (loadout.has(Relic.STICKY_FINGERS)) {
            paddle.setSticky(true);
        }

        phoenixReady = loadout.has(Relic.PHOENIX);
        levelDeaths = 0;
        levelTime = 0;
        levelBricks = 0;
        paddleApproaches = 0;
        paddleCatches = 0;
        bestCombo = 0;
        combo = 0;

        Music.newSection(runRng.fork(level), level);
        spawnBall();
        setState(State.READY);
        showBanner("LEVEL " + level, board.title());
    }

    private void spawnBall() {
        balls.clear();
        pendingBalls.clear();
        Ball b = new Ball(paddle.getX(), paddle.top() - Ball.RADIUS - 1, 0, 0);
        b.hold(0);
        balls.add(b);
    }

    private void launchBalls() {
        boolean launched = false;
        for (Ball b : balls) {
            if (b.isHeld()) {
                b.release(baseSpeed, Math.toRadians(-90 + runRng.range(-18, 18)));
                b.resetPierce(loadout.has(Relic.PIERCE) ? 1 : 0);
                launched = true;
            }
        }
        if (launched) {
            Audio.note(Audio.Patch.LEAD, Music.scaleNote(0, 2), 0.4, 0.12, 0);
        }
        if (state == State.READY) {
            setState(State.PLAYING);
        }
    }

    private void loseBall() {
        if (phoenixReady) {
            phoenixReady = false;
            spawnBall();
            setState(State.READY);
            showBanner("PHOENIX", "the ball returns");
            flash(new Color(0xFFB454), 0.4);
            Audio.note(Audio.Patch.BELL, Music.scaleNote(0, 3), 0.6, 0.6, 0);
            return;
        }

        lives--;
        levelDeaths++;
        combo = 0;
        paddleApproaches++;
        clutch = Math.min(1, clutch + 0.4);
        powerups.clear();
        bullets.clear();
        timers.clear();
        paddle.clearEffects();
        paddle.setTargetWidth(Paddle.BASE_WIDTH * loadout.paddleWidthMultiplier());
        if (loadout.has(Relic.ARSENAL)) {
            paddle.setLaser(true);
        }
        if (loadout.has(Relic.STICKY_FINGERS)) {
            paddle.setSticky(true);
        }
        shake = 16;
        flash(new Color(255, 70, 70), 0.5);
        skill.observeDeath();

        for (int i = 0; i < 3; i++) {
            Audio.note(Audio.Patch.LEAD, Music.scaleNote(4 - i * 2, 1), 0.4, 0.14, 0);
        }

        if (lives <= 0) {
            newRecord = score > highScore;
            saveHighScore();
            setState(State.GAME_OVER);
            for (int i = 0; i < 4; i++) {
                Audio.note(Audio.Patch.PAD, Music.scaleNote(-i, 0), 0.4, 1.6, 0);
            }
        } else {
            spawnBall();
            setState(State.READY);
            showBanner("BALL LOST", lives + " REMAINING");
        }
    }

    private void levelClear() {
        int bonus = 1200 + lives * 500 + level * 150;
        score += bonus;
        popups.add(new Popup(W / 2.0, H * 0.55, "CLEAR BONUS +" + bonus, palette.accent));

        double accuracy = paddleApproaches == 0 ? 0.7 : paddleCatches / (double) paddleApproaches;
        skill.observeLevel(levelDeaths, levelTime, levelBricks, accuracy, bestCombo);

        powerups.clear();
        bullets.clear();
        for (int i = 0; i < 150; i++) {
            double a = runRng.nextDouble() * Math.PI * 2;
            double sp = 90 + runRng.nextDouble() * 380;
            particles.add(new Particle(W / 2.0, H * 0.42,
                    Math.cos(a) * sp, Math.sin(a) * sp - 130,
                    0.8 + runRng.nextDouble() * 0.9, 3 + runRng.nextDouble() * 4,
                    palette.tiers[runRng.nextInt(palette.tiers.length)]));
        }
        flash(palette.accent, 0.4);

        int degree = Music.currentChordDegree();
        for (int i = 0; i < 4; i++) {
            Audio.note(Audio.Patch.BELL, Music.scaleNote(degree + i * 2, 2),
                    0.5, 0.5, (i - 1.5) * 0.4);
        }
        setState(State.LEVEL_CLEAR);
    }

    private void beginDraft() {
        offers = Relic.offer(runRng.fork(level * 7919L), loadout, 3);
        offerIndex = 0;
        if (offers.isEmpty()) {
            nextLevel();
            return;
        }
        setState(State.DRAFT);
    }

    private void takeOffer(int index) {
        if (state != State.DRAFT || index < 0 || index >= offers.size()) {
            return;
        }
        Relic taken = offers.get(index);
        loadout.add(taken);
        if (taken == Relic.SPARE) {
            lives++;
        }
        Audio.note(Audio.Patch.BELL, Music.scaleNote(0, 2), 0.55, 0.5, 0);
        Audio.note(Audio.Patch.BELL, Music.scaleNote(4, 2), 0.45, 0.6, 0.3);
        nextLevel();
    }

    private void nextLevel() {
        level++;
        loadLevel();
    }

    private void updateMusicIntensity() {
        double danger = 0;
        for (Ball b : balls) {
            danger = Math.max(danger, (b.getY() - CEIL) / (double) (H - CEIL));
        }
        double progress = board == null || board.breakableCount() == 0
                ? 0
                : 1 - remainingBreakable() / (double) board.breakableCount();

        double target = switch (state) {
            case TITLE -> 0.24;
            case DRAFT, LEVEL_CLEAR -> 0.34;
            case PAUSED -> 0.20;
            case GAME_OVER -> 0.12;
            default -> 0.30 + 0.32 * progress + 0.28 * danger + (combo > 6 ? 0.10 : 0);
        };
        Music.setIntensity(target);
    }

    private int remainingBreakable() {
        int n = 0;
        for (Bricks b : bricks) {
            if (b.isBreakable()) {
                n++;
            }
        }
        return n;
    }

    private void setState(State s) {
        state = s;
        stateTime = 0;
    }

    private void showBanner(String main, String sub) {
        banner = main;
        bannerSub = sub;
        bannerTime = 2.0;
    }

    private void flash(Color c, double alpha) {
        flashColor = c;
        flashAlpha = Math.max(flashAlpha, alpha);
    }

    private double panOf(double x) {
        return Math.max(-1, Math.min(1, (x / W) * 2 - 1)) * 0.75;
    }

    // ------------------------------------------------------------- high score

    private int loadHighScore() {
        try {
            for (String line : Files.readAllLines(SCORE_FILE, StandardCharsets.UTF_8)) {
                String t = line.trim();
                if (!t.isEmpty()) {
                    return Integer.parseInt(t);
                }
            }
        } catch (IOException | NumberFormatException ignored) {
            // No file yet, or someone typed in it.
        }
        return 0;
    }

    private void saveHighScore() {
        highScore = Math.max(highScore, score);
        if (highScore <= 0) {
            return;
        }
        try {
            Files.write(SCORE_FILE,
                    (highScore + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // A read-only directory should not interrupt play.
        }
    }

    // ----------------------------------------------------------------- render

    private void render() {
        BufferStrategy bs = getBufferStrategy();
        if (bs == null) {
            createBufferStrategy(3);
            return;
        }

        Graphics2D sg = scene.createGraphics();
        try {
            drawScene(sg);
        } finally {
            sg.dispose();
        }

        do {
            do {
                Graphics2D g = (Graphics2D) bs.getDrawGraphics();
                try {
                    g.scale(scale, scale);
                    g.drawImage(scene, 0, 0, null);
                    long beforeBloom = System.nanoTime();
                    bloom.apply(g, scene, 0.55, settings.factor(Settings.BLOOM_STRENGTH));
                    bloomMillis += ((System.nanoTime() - beforeBloom) / 1_000_000.0
                            - bloomMillis) * 0.05;
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    drawUi(g);
                } finally {
                    g.dispose();
                }
            } while (bs.contentsRestored());
            bs.show();
        } while (bs.contentsLost());
    }

    /** Everything that should glow. Drawn into the offscreen buffer. */
    private void drawScene(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        drawBackground(g);

        AffineTransform base = g.getTransform();
        if (shake > 0.1) {
            g.translate(runRng.gauss() * shake * 0.35, runRng.gauss() * shake * 0.35);
        }

        if (state == State.TITLE) {
            drawAttract(g);
        } else {
            for (Bricks b : bricks) {
                b.render(g, time, brickSprites);
            }
            for (Shockwave s : shockwaves) {
                s.render(g);
            }
            for (Particle p : particles) {
                p.render(g);
            }
            for (PowerupBall p : powerups) {
                p.render(g, badgeFont);
            }
            for (Bullet s : bullets) {
                s.render(g);
            }
            paddle.render(g, time);
            for (Ball b : balls) {
                b.render(g, palette.ball, true);
            }
            for (Popup p : popups) {
                p.render(g, popupFont);
            }
        }

        g.setTransform(base);
        drawWalls(g);

        if (flashAlpha > 0.002) {
            g.setColor(Palette.alpha(flashColor, Math.min(0.6, flashAlpha)));
            g.fillRect(0, 0, W, H);
        }
    }

    private void drawBackground(Graphics2D g) {
        backdrop.draw(g);

        // The only part still drawn live: a soft pool of light under the paddle,
        // which has to follow it.
        if (state != State.TITLE) {
            float r = 240;
            g.setPaint(new RadialGradientPaint(new Point2D.Double(paddle.getX(), PADDLE_Y), r,
                    new float[] {0f, 1f},
                    new Color[] {Palette.alpha(palette.accentSoft, 0.16),
                            Palette.alpha(palette.accentSoft, 0)}));
            g.fill(new Ellipse2D.Double(paddle.getX() - r, PADDLE_Y - r, r * 2, r * 2));
        }
    }

    private void drawWalls(Graphics2D g) {
        Color deep = Palette.scale(palette.wall, 0.35);
        g.setPaint(new GradientPaint(0, 0, palette.wall, WALL, 0, deep));
        g.fillRect(0, CEIL - 10, WALL, H - CEIL + 10);
        g.setPaint(new GradientPaint(W - WALL, 0, deep, W, 0, palette.wall));
        g.fillRect(W - WALL, CEIL - 10, WALL, H - CEIL + 10);
        g.setPaint(new GradientPaint(0, CEIL - 10, palette.wall, 0, CEIL, deep));
        g.fillRect(0, CEIL - 10, W, 10);

        g.setColor(Palette.alpha(palette.accent, 0.35));
        g.setStroke(new BasicStroke(1.4f));
        g.drawLine(WALL, CEIL, W - WALL, CEIL);
        g.drawLine(WALL, CEIL, WALL, H);
        g.drawLine(W - WALL - 1, CEIL, W - WALL - 1, H);
    }

    private void drawAttract(Graphics2D g) {
        for (int i = 0; i < 7; i++) {
            double px = W / 2.0 + Math.sin(time * 0.27 + i * 1.1) * W * 0.36;
            double py = H * 0.55 + Math.cos(time * 0.21 + i * 1.7) * H * 0.26;
            float r = 44 + i * 8;
            Color c = palette.tiers[i % palette.tiers.length];
            g.setPaint(new RadialGradientPaint(new Point2D.Double(px, py), r,
                    new float[] {0f, 1f},
                    new Color[] {Palette.alpha(c, 0.34), Palette.alpha(c, 0)}));
            g.fill(new Ellipse2D.Double(px - r, py - r, r * 2, r * 2));
        }
    }

    // --------------------------------------------------------------------- UI

    private void drawUi(Graphics2D g) {
        if (state != State.SETTINGS && state != State.ABOUT) {
            drawHud(g);
        }
        switch (state) {
            case TITLE -> drawTitle(g);
            case SETTINGS -> menu.drawSettings(g, settings, palette,
                    Rng.seedCode(runSeed), highScore, time);
            case ABOUT -> menu.drawAbout(g, palette, Rng.seedCode(runSeed), time);
            case READY -> {
                if (bannerTime <= 0) {
                    text(g, "PRESS SPACE TO LAUNCH", midFont, Palette.alpha(Color.WHITE, 0.85),
                            W / 2, (int) (H * 0.63), 1);
                }
            }
            case PAUSED -> {
                scrim(g, 190);
                glowText(g, "PAUSED", bigFont, Color.WHITE, palette.accent,
                        W / 2, (int) (H * 0.42), 1);
                text(g, "P or ENTER resume    ESC title    B bloom    M mute",
                        smallFont, new Color(180, 205, 235), W / 2, (int) (H * 0.42) + 40, 1);
                drawRunSummary(g, (int) (H * 0.52));
            }
            case LEVEL_CLEAR -> {
                scrim(g, 130);
                glowText(g, "LEVEL COMPLETE", bigFont, Color.WHITE, palette.accent,
                        W / 2, (int) (H * 0.40), 1);
                text(g, board == null ? "" : board.title(), midFont,
                        Palette.alpha(palette.accentSoft, 0.95), W / 2, (int) (H * 0.40) + 40, 1);
            }
            case DRAFT -> drawDraft(g);
            case GAME_OVER -> drawGameOver(g);
            default -> { }
        }

        if (bannerTime > 0 && state != State.TITLE && state != State.DRAFT) {
            float a = (float) Math.min(1, bannerTime / 0.5);
            glowText(g, banner, bigFont, Palette.alpha(Color.WHITE, a),
                    Palette.alpha(palette.accent, a), W / 2, (int) (H * 0.40), 1);
            text(g, bannerSub, midFont, Palette.alpha(palette.accentSoft, a * 0.9),
                    W / 2, (int) (H * 0.40) + 38, 1);
        }
    }

    private void drawHud(Graphics2D g) {
        g.setColor(new Color(4, 7, 15, 236));
        g.fillRect(0, 0, W, HUD_H);
        g.setColor(Palette.alpha(palette.accent, 0.35));
        g.drawLine(0, HUD_H, W, HUD_H);

        label(g, "SCORE", 24, 24, Palette.alpha(palette.accentSoft, 0.9), 0);
        text(g, String.format("%08d", Math.max(0, score)), valueFont, Color.WHITE, 24, 50, 0);

        label(g, "HIGH", W - 24, 24, Palette.alpha(palette.accentSoft, 0.9), 2);
        text(g, String.format("%08d", Math.max(0, highScore)), valueFont,
                score > highScore ? new Color(0xFFD166) : new Color(190, 205, 230),
                W - 24, 50, 2);

        if (state == State.TITLE) {
            label(g, "SEED", W / 2, 24, Palette.alpha(palette.accentSoft, 0.9), 1);
            text(g, Rng.seedCode(runSeed), midFont, new Color(170, 225, 255), W / 2, 50, 1);
            return;
        }

        label(g, "LEVEL " + level + "  -  " + skill.band(), W / 2, 24,
                Palette.alpha(palette.accentSoft, 0.9), 1);
        text(g, board == null ? "" : board.title(), midFont, new Color(180, 225, 255),
                W / 2, 50, 1);

        for (int i = 0; i < Math.min(lives, 9); i++) {
            double cx = 28 + i * 20;
            double cy = H - 24;
            g.setColor(Palette.alpha(palette.ball, 0.35));
            g.fill(new Ellipse2D.Double(cx - 8, cy - 8, 16, 16));
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Double(cx - 5, cy - 5, 10, 10));
        }
        if (lives > 9) {
            text(g, "x" + lives, badgeFont, Color.WHITE, 28 + 9 * 20, H - 19, 0);
        }

        drawClutchMeter(g);

        int bx = W - 24;
        if (combo >= 2) {
            text(g, "COMBO x" + combo, midFont, new Color(0xFFD166), bx, H - 18, 2);
            bx -= 140;
        }
        for (Map.Entry<PowerupBall.Kind, Double> e : timers.entrySet()) {
            text(g, e.getKey().badge() + " " + (int) Math.ceil(e.getValue()), badgeFont,
                    e.getKey().color(), bx, H - 20, 2);
            bx -= 48;
        }

        drawRelicStrip(g);

        if (DEBUG) {
            text(g, String.format("%.1f ms  bloom:%s  balls:%d  bricks:%d  skill:%.2f",
                    frameMillis, bloom.isEnabled() ? "on" : "off", balls.size(),
                    bricks.size(), skill.skill()) + String.format("  bloom %.1f ms", bloomMillis),
                    tinyFont, new Color(120, 220, 160), 24, HUD_H + 40, 0);
        }
        if (settings.on(Settings.STATS) && !DEBUG) {
            text(g, String.format("%.1f ms  bloom %.1f ms  balls %d  bricks %d",
                    frameMillis, bloomMillis, balls.size(), bricks.size()), tinyFont,
                    new Color(120, 220, 160), 24, HUD_H + 40, 0);
        }
    }

    private void drawClutchMeter(Graphics2D g) {
        double barW = 150;
        double x = W / 2.0 - barW / 2;
        double y = H - 26;
        g.setColor(new Color(255, 255, 255, 30));
        g.fill(new RoundRectangle2D.Double(x, y, barW, 6, 4, 4));
        Color c = dilation < 0.9 ? new Color(0x8FE3FF) : Palette.alpha(palette.accent, 0.75);
        g.setColor(c);
        g.fill(new RoundRectangle2D.Double(x, y, barW * clutch, 6, 4, 4));
        label(g, dilation < 0.9 ? "DILATION" : "CLUTCH", W / 2, (int) y - 6,
                Palette.alpha(c, 0.9), 1);
    }

    private void drawRelicStrip(Graphics2D g) {
        int i = 0;
        for (Map.Entry<Relic, Integer> e : loadout.all().entrySet()) {
            int x = 24 + i * 16;
            int y = HUD_H + 14;
            g.setColor(Palette.alpha(e.getKey().rarity.color, 0.85));
            g.fill(new Ellipse2D.Double(x, y, 9, 9));
            if (e.getValue() > 1) {
                g.setColor(Color.WHITE);
                g.setFont(labelFont);
                g.drawString(String.valueOf(e.getValue()), x + 10, y + 9);
            }
            i++;
        }
    }

    private void drawTitle(Graphics2D g) {
        glowText(g, "ARKANOID", hugeFont, Color.WHITE, palette.accent, W / 2, (int) (H * 0.24), 1);
        label(g, "R E S O N A N C E", W / 2, (int) (H * 0.24) + 30,
                Palette.alpha(palette.accentSoft, 0.95), 1);

        if (Math.sin(time * 3.2) > -0.35) {
            glowText(g, "PRESS ENTER TO BEGIN A RUN", midFont, Color.WHITE,
                    palette.tiers[3], W / 2, (int) (H * 0.40), 1);
        }

        String[] lines = {
            "Every level is generated. Every run is different.",
            "Bricks are tuned to the key - clearing them plays the melody.",
            "Draft a relic after each level and build a run.",
            "",
            "MOUSE or LEFT/RIGHT  move          SPACE  launch and fire",
            "P pause    M mute    B bloom    R reroll seed    ESC quit",
            "S settings                    A about",
        };
        int y = (int) (H * 0.50);
        for (String s : lines) {
            text(g, s, smallFont, new Color(175, 200, 228), W / 2, y, 1);
            y += 24;
        }

        y += 16;
        text(g, "SEED  " + Rng.seedCode(runSeed), midFont, new Color(0xFFD166), W / 2, y, 1);
        text(g, "same seed, same levels, same relics", tinyFont,
                new Color(140, 165, 195), W / 2, y + 22, 1);
        text(g, Music.keyName() + "   " + Math.round(Music.tempo()) + " BPM"
                + (Audio.isRunning() ? "" : "   (no audio device)"),
                tinyFont, Palette.alpha(palette.accentSoft, 0.85), W / 2, y + 46, 1);

        text(g, "HIGH SCORE  " + highScore, midFont, new Color(0xFFD166),
                W / 2, (int) (H * 0.93), 1);
    }

    private void drawDraft(Graphics2D g) {
        scrim(g, 214);
        glowText(g, "CHOOSE A RELIC", bigFont, Color.WHITE, palette.accent,
                W / 2, (int) (H * 0.19), 1);
        text(g, "it lasts the rest of the run", smallFont, new Color(170, 195, 225),
                W / 2, (int) (H * 0.19) + 28, 1);

        int cardW = 220;
        int cardH = 262;
        int gap = 20;
        int totalW = offers.size() * cardW + (offers.size() - 1) * gap;
        int x0 = (W - totalW) / 2;
        int y0 = (int) (H * 0.30);

        for (int i = 0; i < offers.size(); i++) {
            Relic r = offers.get(i);
            int x = x0 + i * (cardW + gap);
            if (hovering(x, y0, cardW, cardH)) {
                offerIndex = i;
            }
            boolean hot = i == offerIndex;

            RoundRectangle2D card = new RoundRectangle2D.Double(x, y0, cardW, cardH, 16, 16);
            g.setColor(hot ? new Color(22, 30, 48, 250) : new Color(12, 17, 30, 235));
            g.fill(card);
            g.setStroke(new BasicStroke(hot ? 2.6f : 1.4f));
            g.setColor(Palette.alpha(r.rarity.color, hot ? 1.0 : 0.5));
            g.draw(card);
            g.setStroke(new BasicStroke(1f));

            label(g, r.rarity.name(), x + cardW / 2, y0 + 30,
                    Palette.alpha(r.rarity.color, 0.95), 1);

            int cy = y0 + 76;
            g.setColor(Palette.alpha(r.rarity.color, 0.22));
            g.fill(new Ellipse2D.Double(x + cardW / 2.0 - 26, cy - 26, 52, 52));
            g.setColor(r.rarity.color);
            g.setStroke(new BasicStroke(2f));
            g.draw(new Ellipse2D.Double(x + cardW / 2.0 - 26, cy - 26, 52, 52));
            g.setStroke(new BasicStroke(1f));
            text(g, String.valueOf(r.title.charAt(0)), bigFont, Color.WHITE,
                    x + cardW / 2, cy + 14, 1);

            text(g, r.title, midFont, Color.WHITE, x + cardW / 2, y0 + 142, 1);
            wrapped(g, r.description, smallFont, new Color(180, 200, 225),
                    x + 16, y0 + 174, cardW - 32, 20);

            if (loadout.has(r)) {
                text(g, "held x" + loadout.count(r), tinyFont,
                        Palette.alpha(r.rarity.color, 0.9), x + cardW / 2, y0 + cardH - 16, 1);
            }
            text(g, String.valueOf(i + 1), labelFont, new Color(150, 175, 205),
                    x + 14, y0 + 22, 0);
        }

        text(g, "1 / 2 / 3   or   LEFT RIGHT + ENTER   or   click", smallFont,
                new Color(165, 190, 220), W / 2, y0 + cardH + 42, 1);
        drawRunSummary(g, y0 + cardH + 82);
    }

    private void drawRunSummary(Graphics2D g, int y) {
        text(g, "RUN " + Rng.seedCode(runSeed) + "     level " + level
                + "     bricks " + bricksBroken + "     best combo x" + runBestCombo,
                smallFont, new Color(150, 175, 205), W / 2, y, 1);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Relic, Integer> e : loadout.all().entrySet()) {
            if (sb.length() > 0) {
                sb.append("   ");
            }
            sb.append(e.getKey().title);
            if (e.getValue() > 1) {
                sb.append(" x").append(e.getValue());
            }
        }
        if (sb.length() > 0) {
            wrapped(g, sb.toString(), tinyFont, Palette.alpha(palette.accentSoft, 0.9),
                    W / 2 - 320, y + 26, 640, 18);
        }
    }

    private void drawGameOver(Graphics2D g) {
        scrim(g, 214);
        glowText(g, "RUN OVER", hugeFont, Color.WHITE, new Color(255, 90, 110),
                W / 2, (int) (H * 0.30), 1);
        text(g, "FINAL SCORE  " + score, bigFont, new Color(0xFFD166),
                W / 2, (int) (H * 0.30) + 64, 1);
        text(g, "reached level " + level + "     " + bricksBroken
                + " bricks     best combo x" + runBestCombo,
                smallFont, new Color(175, 200, 228), W / 2, (int) (H * 0.30) + 98, 1);

        if (newRecord) {
            glowText(g, "NEW HIGH SCORE", midFont, new Color(255, 235, 150),
                    new Color(255, 180, 40), W / 2, (int) (H * 0.30) + 136, 1);
        } else {
            text(g, "high score  " + highScore, midFont, new Color(160, 185, 215),
                    W / 2, (int) (H * 0.30) + 136, 1);
        }

        drawRunSummary(g, (int) (H * 0.60));

        if (Math.sin(stateTime * 4) > -0.3) {
            text(g, "ENTER  new run       R  replay this seed       ESC  title", midFont,
                    new Color(210, 232, 255), W / 2, (int) (H * 0.80), 1);
        }
    }

    // -------------------------------------------------------- drawing helpers

    private boolean hovering(int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private void scrim(Graphics2D g, int alpha) {
        g.setColor(new Color(3, 5, 12, alpha));
        g.fillRect(0, 0, W, H);
    }

    /** @param align 0 left, 1 centred, 2 right */
    private void text(Graphics2D g, String s, Font f, Color c, int x, int y, int align) {
        g.setFont(f);
        int w = g.getFontMetrics().stringWidth(s);
        int px = align == 1 ? x - w / 2 : align == 2 ? x - w : x;
        g.setColor(new Color(0, 0, 0, 150));
        g.drawString(s, px + 2, y + 2);
        g.setColor(c);
        g.drawString(s, px, y);
    }

    private void wrapped(Graphics2D g, String s, Font f, Color c, int x, int y, int w, int lh) {
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        StringBuilder line = new StringBuilder();
        int cy = y;
        for (String word : s.split(" ")) {
            String probe = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(probe) > w && line.length() > 0) {
                text(g, line.toString(), f, c, x + w / 2, cy, 1);
                line = new StringBuilder(word);
                cy += lh;
            } else {
                line = new StringBuilder(probe);
            }
        }
        if (line.length() > 0) {
            text(g, line.toString(), f, c, x + w / 2, cy, 1);
        }
    }

    private void glowText(Graphics2D g, String s, Font f, Color core, Color glow,
            int x, int y, int align) {
        g.setFont(f);
        int w = g.getFontMetrics().stringWidth(s);
        int px = align == 1 ? x - w / 2 : align == 2 ? x - w : x;
        for (int r = 8; r >= 2; r -= 3) {
            g.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(),
                    Math.max(8, glow.getAlpha() / 9)));
            for (int dx = -r; dx <= r; dx += r) {
                for (int dy = -r; dy <= r; dy += r) {
                    if (dx != 0 || dy != 0) {
                        g.drawString(s, px + dx, y + dy);
                    }
                }
            }
        }
        g.setColor(core);
        g.drawString(s, px, y);
    }

    private void label(Graphics2D g, String s, int x, int y, Color c, int align) {
        g.setFont(labelFont);
        FontMetrics fm = g.getFontMetrics();
        final int tracking = 3;
        int w = -tracking;
        for (int i = 0; i < s.length(); i++) {
            w += fm.charWidth(s.charAt(i)) + tracking;
        }
        int px = align == 1 ? x - w / 2 : align == 2 ? x - w : x;
        g.setColor(c);
        for (int i = 0; i < s.length(); i++) {
            g.drawString(String.valueOf(s.charAt(i)), px, y);
            px += fm.charWidth(s.charAt(i)) + tracking;
        }
    }

    private static String pickFamily(String... names) {
        try {
            Set<String> have = new HashSet<>(Arrays.asList(GraphicsEnvironment
                    .getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            for (String n : names) {
                if (have.contains(n)) {
                    return n;
                }
            }
        } catch (Throwable ignored) {
            // Fall through to the logical family.
        }
        return Font.SANS_SERIF;
    }

    private Cursor blankCursor() {
        try {
            BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            return Toolkit.getDefaultToolkit().createCustomCursor(img, new Point(0, 0), "blank");
        } catch (Throwable t) {
            return Cursor.getDefaultCursor();
        }
    }

    /** Drifting text for score gains and pick-up names. */
    private static final class Popup extends Figure {

        private static final double MAX_LIFE = 1.0;

        private double life = MAX_LIFE;
        private final String text;
        private final Color color;

        Popup(double x, double y, String text, Color color) {
            super(x, y);
            this.text = text;
            this.color = color;
        }

        @Override
        public void update(double dt) {
            y -= 46 * dt;
            life -= dt;
        }

        void render(Graphics2D g, Font f) {
            float a = (float) Math.max(0, Math.min(1, life / MAX_LIFE));
            g.setFont(f);
            int w = g.getFontMetrics().stringWidth(text);
            g.setColor(new Color(0, 0, 0, (int) (150 * a)));
            g.drawString(text, (float) (x - w / 2.0) + 1.5f, (float) y + 1.5f);
            g.setColor(Palette.alpha(color, a));
            g.drawString(text, (float) (x - w / 2.0), (float) y);
        }
    }

    /** An expanding ring, used for impacts and explosions. */
    private static final class Shockwave extends Figure {

        private final double maxRadius;
        private final Color color;
        private double life = 1;

        Shockwave(double x, double y, double maxRadius, Color color) {
            super(x, y);
            this.maxRadius = maxRadius;
            this.color = color;
        }

        @Override
        public void update(double dt) {
            life -= dt * 2.4;
        }

        void render(Graphics2D g) {
            double t = 1 - Math.max(0, life);
            double r = maxRadius * (1 - Math.pow(1 - t, 2));
            float a = (float) Math.max(0, life) * 0.8f;
            g.setColor(Palette.alpha(color, a));
            g.setStroke(new BasicStroke((float) (3.5 * Math.max(0, life))));
            g.draw(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
            g.setStroke(new BasicStroke(1f));
        }
    }

    // ------------------------------------------------------------------ input

    private void handleActions() {
        Integer code;
        while ((code = actions.poll()) != null) {
            switch (code) {
                case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> confirm();
                case KeyEvent.VK_1 -> takeOffer(0);
                case KeyEvent.VK_2 -> takeOffer(1);
                case KeyEvent.VK_3 -> takeOffer(2);
                case KeyEvent.VK_P -> {
                    if (state == State.PLAYING) {
                        setState(State.PAUSED);
                    } else if (state == State.PAUSED) {
                        setState(State.PLAYING);
                    }
                }
                case KeyEvent.VK_M -> {
                    settings.set(Settings.MUTE, settings.on(Settings.MUTE) ? 0 : 1);
                    settings.save();
                    applySettings();
                }
                case KeyEvent.VK_B -> {
                    settings.set(Settings.BLOOM, settings.on(Settings.BLOOM) ? 0 : 1);
                    settings.save();
                    applySettings();
                }
                case KeyEvent.VK_S -> {
                    if (state == State.TITLE) {
                        menu.reset();
                        setState(State.SETTINGS);
                    }
                }
                case KeyEvent.VK_A -> {
                    if (state == State.TITLE) {
                        setState(State.ABOUT);
                    }
                }
                case KeyEvent.VK_TAB -> {
                    if (state == State.SETTINGS) {
                        menu.moveTab(1);
                    }
                }
                case KeyEvent.VK_R -> {
                    if (state == State.TITLE) {
                        newSeed();
                    } else if (state == State.GAME_OVER) {
                        startRun();
                    }
                }
                case KeyEvent.VK_K -> {
                    if (DEBUG && state == State.PLAYING) {
                        levelClear();
                    }
                }
                case KeyEvent.VK_ESCAPE -> escape();
                default -> { }
            }
        }
    }

    private void confirm() {
        switch (state) {
            case TITLE -> startRun();
            case READY -> launchBalls();
            case PAUSED -> setState(State.PLAYING);
            case DRAFT -> takeOffer(offerIndex);
            case GAME_OVER -> {
                newSeed();
                startRun();
            }
            case SETTINGS -> activateSetting();
            case ABOUT -> setState(State.TITLE);
            default -> { }
        }
    }

    private void escape() {
        saveHighScore();
        if (state == State.SETTINGS || state == State.ABOUT) {
            settings.save();
            setState(State.TITLE);
            return;
        }
        if (state == State.TITLE) {
            Audio.stop();
            System.exit(0);
        }
        setState(State.TITLE);
        newSeed();
    }

    private void changeSetting(int direction) {
        Settings.Def def = menu.selected();
        if (def == null || def.kind() == Settings.Kind.ACTION) {
            return;
        }
        settings.nudge(def.key(), direction);
        settings.save();
        applySettings();
    }

    private void activateSetting() {
        Settings.Def def = menu.selected();
        if (def == null) {
            return;
        }
        if (def.kind() != Settings.Kind.ACTION) {
            changeSetting(1);
            return;
        }
        switch (def.key()) {
            case Settings.ACTION_SEED -> newSeed();
            case Settings.ACTION_RESET_HIGH -> {
                highScore = 0;
                saveHighScore();
            }
            case Settings.ACTION_DEFAULTS -> {
                settings.resetToDefaults();
                settings.save();
                applySettings();
            }
            default -> { }
        }
    }

    private void applySettings() {
        Audio.setMasterGain(settings.factor(Settings.MASTER));
        Audio.setMusicGain(settings.factor(Settings.MUSIC));
        Audio.setSfxGain(settings.factor(Settings.SFX));
        Audio.setMuted(settings.on(Settings.MUTE));
        bloom.setEnabled(settings.on(Settings.BLOOM));
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int c = e.getKeyCode();
        if (state == State.SETTINGS) {
            if (c == KeyEvent.VK_UP || c == KeyEvent.VK_W) {
                menu.move(-1);
            } else if (c == KeyEvent.VK_DOWN) {
                menu.move(1);
            } else if (c == KeyEvent.VK_LEFT) {
                changeSetting(-1);
            } else if (c == KeyEvent.VK_RIGHT) {
                changeSetting(1);
            }
        }
        if (c == KeyEvent.VK_LEFT || c == KeyEvent.VK_A) {
            leftDown = true;
            if (state == State.DRAFT) {
                offerIndex = Math.max(0, offerIndex - 1);
            }
        }
        if (c == KeyEvent.VK_RIGHT || c == KeyEvent.VK_D) {
            rightDown = true;
            if (state == State.DRAFT) {
                offerIndex = Math.min(offers.size() - 1, offerIndex + 1);
            }
        }
        if (c == KeyEvent.VK_SPACE) {
            fireDown = true;
        }
        actions.offer(c);
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int c = e.getKeyCode();
        if (c == KeyEvent.VK_LEFT || c == KeyEvent.VK_A) {
            leftDown = false;
        }
        if (c == KeyEvent.VK_RIGHT || c == KeyEvent.VK_D) {
            rightDown = false;
        }
        if (c == KeyEvent.VK_SPACE) {
            fireDown = false;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // The game reacts to key codes, not typed characters.
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        mouseX = e.getX() / scale;
        mouseY = e.getY() / scale;
        if (state == State.SETTINGS) {
            menu.pointAtTab(mouseX, mouseY);
            menu.pointAt(mouseX, mouseY);
        } else if (state != State.DRAFT) {
            mouseControl = true;
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        mouseMoved(e);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        requestFocusInWindow();
        mouseX = e.getX() / scale;
        mouseY = e.getY() / scale;
        if (state == State.SETTINGS) {
            if (menu.pointAtTab(mouseX, mouseY)) {
                return;
            }
            menu.pointAt(mouseX, mouseY);
        }
        actions.offer(KeyEvent.VK_ENTER);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        // Unused.
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        // Unused.
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        // Unused.
    }

    @Override
    public void mouseExited(MouseEvent e) {
        // Unused.
    }
}
