package Arkanoid;

import java.awt.Color;
import java.awt.Graphics;

public class PowerupBall extends Figure{
   
   
    private int speed;
    protected int radie = 5;
    Color color;
    /**
     * Konstruktor för PowerupBall
     * @param x x koordinat för PowerupBall som är ärvt från Figure klassen
     * @param y y koordinat för PowerupBall som är ärvt från Figure klassen
     * @param color Color för PowerupBall
     */
    public PowerupBall(int x, int y, Color color) {super( x,  y);
       
        this.color = color;

    }

    public int getRadie() {
        return radie;
    }

    public void setSpeed(int speed) {
        this.speed += speed;
    }
    
    @Override
    public void run(){
        setY(getY()+speed);
                
    
    }

    

    public void render(Graphics g) {
        g.setColor(color);
        g.fillOval(getX(), getY(), radie * 2, radie * 2);//Använder radien *2
       
    }

}
