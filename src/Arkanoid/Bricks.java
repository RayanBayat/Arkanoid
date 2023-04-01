package Arkanoid;

import java.awt.Color;

import java.awt.Graphics;




public class Bricks extends Figure{

  
   
   boolean Crushed;
  
   private Color color;
    private int w, h;
/**
 * Konstruktor för klassen Bricks
 * @param x x koordinat för bricks som är ärvt från Figure klassen
 * @param y y koordinat för bricks som är ärvt från Figure klassen
 * @param w bredden för brickor
 * @param h höjden för brickor
 * @param color färgen för brickor
 */
    public Bricks(int x, int y, int w, int h, Color color) { super( x,  y);

        this.w = w;
        this.h = h;
        this.color = color;

    }

    public int getW() {
        return w;
    }

    public void setW(int w) {
        this.w = w;
    }

    public int getH() {
        return h;
    }

    public void setH(int h) {
        this.h = h;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public void draw(Graphics g) {
        if (!Crushed) {
            g.setColor(color);
              g.fillRect(getX(), getY(), getW(), getH());
              
        }
      
    }
}
