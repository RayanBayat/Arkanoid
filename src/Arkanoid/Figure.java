package Arkanoid;



public class Figure {
    
    protected int x, y;
    
/**
 * Konstruktor för klassen Figure
 * @param x x koordinat för figure
 * @param y y koordinat för figure
 */
    public Figure(int x, int y) {
        this.x = x;
        this.y = y;

    }

    public void run() {
        this.x *= x;
        this.y *= y;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }
}
