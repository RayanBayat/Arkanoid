package Arkanoid;

/**
 * Base class for everything that lives in the playfield.
 *
 * <p>Coordinates are doubles rather than ints so that motion stays smooth and
 * frame-rate independent: every entity is advanced by a delta time in seconds
 * instead of "one pixel per tick".</p>
 */
public abstract class Figure {

    protected double x;
    protected double y;

    protected Figure(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Advances this entity.
     *
     * @param dt elapsed time in seconds since the previous step
     */
    public abstract void update(double dt);

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }
}
