package Arkanoid;

import java.awt.Color;

import java.awt.Graphics;

public class Paddle extends Figure {

    private int w, h;
    private int speedX = 0;
    Color color;

    /**
     * Konstruktor för paddle klassen
     * @param x x koordinat för paddlen som är ärvt från Figure klassen
     * @param y y koordinat för paddlen som är ärvt för figure klassen
     * @param w bredden för paddeln
     * @param h Höjden för paddlen
     * @param color Färgen för paddlen
     */
    public Paddle(int x, int y, int w, int h, Color color) {
        super(x, y);

        this.w = w;
        this.h = h;
        this.color = color;

    }

    /**
     * Skickar bredden
     * @return Skickar w när den anropas
     */
    public int getWidth() {
        return w;
    }

    /**
     * Tar emot en ny värde för bredd
     * @param width Sätter beredden när den blir anropad
     */

    public void setWidth(int width) {
        this.w = width;
    }
    /**
     * Skickar Höjden 
     * @return Skickar h när den anropas
     */
    public int getHeight() {
        return h;
    }
    /**
     * Tar emot en ny värde för höjden
     * @param height  Sätter Höjden när den blir anropad
     */
    public void setHeight(int height) {
        this.h = height;
    }
    /**
     * har speed x i sig
     * @return skickar iväg speedx när den kallas
     */
     
    public int getSpeedX() {
        return speedX;
    }
    /**
     * Tar emot en ny värde för speedx
     * @param speedX  Sätter speedx när den blir anropad
     */
    public void setSpeedX(int speedX) {
        this.speedX = speedX;
    }
    /**
     * Sätter paddeln i rörelse
     * tar värdet på x koordinat och ökar den med speedx
     * låter inte paddeln gå utanför spelbanan
     */
    public void run() {
        setX(getX() + speedX);

        if (getX() < 0 || getX() > 600 - getWidth()) {
            speedX = 0;
        }
    }
    /**
     * Ritar ut paddeln
     * @param g den ritar ut när den blir anropad
     * den kommer att rita ut en rektangel med färg inuti
     */
    public void render(Graphics g) {
        g.setColor(color);
        g.fillRect(getX(), getY(), getWidth(), getHeight());

    }

}
