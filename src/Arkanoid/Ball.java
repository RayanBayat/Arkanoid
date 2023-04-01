package Arkanoid;

import java.awt.Color;

import java.awt.Graphics;



public class Ball extends Figure{
   
    
   
   
    private int speedXforcollision = 1, speedYforcollision = 1; 
    private int speedX = 2 *speedXforcollision, speedY = 2* speedYforcollision;
    private final int radie = 10; // deklarerar radien
    private final Color color;
   /**
    * Konstruktor för klassen Ball
    * @param x x koordinat för bollen som är ärvt från Figure klassen
    * @param y y koordinat för bollen som är ärvt från Figure klassen
    * @param color Färgen på bollen
    */
    public Ball(int x, int y, Color color) {super( x,  y);
        
        this.color = color;

        
    }
    /**
     * sätter bollen i rörelse
     * hämtar x och y kordinater och sedan adderas de med produkten av speed x eller y med speedxforcollision eller y
     * if satsen låter inte bollen gå utanför banan förutom nedåt
     */
    public void run(){
        setX(getX()+(speedX *speedXforcollision));
        setY(getY()+speedY *speedYforcollision);
        if (getX()< 0 || getX()> 575) {
            speedX = speedX * -1;
        }if (getY() < 0 ) {
             speedY = -1 * speedY;
        }
    }


    public void setSpeedX(int speedX) {
        this.speedX *= speedX;
    }

    public void setSpeedY(int speedY) {
        this.speedY *= speedY;
    }

    public int getSpeedX() {
        return speedX;
    }

    public int getSpeedY() {
        return speedY;
    }

    public int getRadie() {
        return radie;
    }
    public int getSpeedXforcollision() {
        return speedXforcollision;
    }

    public void setSpeedXforcollision(int speedXforcollision) {
        this.speedXforcollision += speedXforcollision;
    }

    public int getSpeedYforcollision() {
        return speedYforcollision;
    }

    public void setSpeedYforcollision(int speedYforcollision) {
        this.speedYforcollision += speedYforcollision;
    }
    /**
     * Ritar ut bollen
     * @param g den ritar ut när den blir anropad
     * den kommer att rita ut en cirkel med färg inuti
     */
    public void render(Graphics g) {
        g.setColor(color);
        g.fillOval(getX(), getY(), getRadie() * 2, getRadie() * 2);
    }

}
