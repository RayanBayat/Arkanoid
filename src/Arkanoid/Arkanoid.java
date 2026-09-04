package Arkanoid;

/**
 * Entry point.
 *
 * <p>Build and run from the project root (the working directory is where the
 * {@code Lastscore} high-score file is kept):</p>
 * <pre>
 *   javac -d out src/Arkanoid/*.java
 *   java  -cp out Arkanoid.Arkanoid
 * </pre>
 */
public final class Arkanoid {

    private Arkanoid() {
        // Not instantiable: this class only starts the game.
    }

    public static void main(String[] args) {
        GamePlay.init();
    }
}
