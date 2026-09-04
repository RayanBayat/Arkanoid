package Arkanoid;

/**
 * Playfield geometry, in logical units.
 *
 * <p>These live apart from {@link GamePlay} so the generators can lay a board out
 * without depending on the renderer. The surface is a fixed size and is drawn
 * through a uniform scale, so no coordinate maths ever has to care about the
 * real window size.</p>
 */
public final class Field {

    public static final int W = 800;
    public static final int H = 900;

    /** Height of the heads-up display strip along the top. */
    public static final int HUD_H = 64;
    /** Thickness of the side walls. */
    public static final int WALL = 14;
    /** The y the ball bounces off at the top of the playfield. */
    public static final int CEIL = 78;
    /** Centre line of the paddle. */
    public static final double PADDLE_Y = H - 64;

    private Field() {
    }
}
