package Arkanoid;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferStrategy;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import static java.lang.Math.abs;
import java.util.ArrayList;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

public final class GamePlay extends Arkanoid implements Runnable, KeyListener {

    Random dice = new Random(); //Kallar in random klassen

    
    ArrayList<Bricks> blocks = new ArrayList<Bricks>(); // gör en lista av brickor
    Paddle paddle; // deklarerar paddlen
    Ball ball; // deklarerar bollen
    ArrayList<PowerupBall> powerUp = new ArrayList<>(); // gör en lista av powerups
   
    

    boolean isPowerUp = false; // deklarerar is powerup
    boolean powerupcollision = false; // deklarerar powerupcollision
    Thread thread = new Thread(); // kallar in en tråd
    int amountblocks;
    int finalscore;
    int Temporarlyscore;
    int streak;
    int highscore;
    int fontSize;
    int Storfont;
   

    public void reset() {
        /*
        Reset motoden återställer spelet
        
        1. Tar bort alla brickor som finns
        2. Skapar ny boll, ger den koordinat värderna och positionen
        3. Skapar ny paddle, ger den koordinat värderna och positionen
        4. Sätter antal blocks kvar till 48
        5. Sätter slutgiltiga poänget till 0
        6. Stätter poänget som ökar med multiplier till 0
        7. Sätter multimlier till 0
        8, 9 och 10 ändrar storleken på fonten
        11 Skapar och lägger till nya brickor på spelplanet
         */

        blocks.removeAll(blocks);
        ball = new Ball(10 + dice.nextInt(550), 300, Color.BLACK);
        paddle = new Paddle(250, 550, 136, 20, Color.BLACK);
        amountblocks = 48;
        finalscore = 0;
        Temporarlyscore = 0;
        streak = 0;
        highscore = 0;
        fontSize = 20;
        Storfont = 30;
        

        for (int i = 0; i < 10; i++) {
            blocks.add(new Bricks((i * 60 + 2), 25, 60, 25, new Color((int) (Math.random() * 0x1000000))));

        }
        for (int i = 1; i < 9; i++) {
            blocks.add(new Bricks((i * 60 + 2), 50, 60, 25, new Color((int) (Math.random() * 0x1000000))));
        }
        for (int i = 2; i < 8; i++) {
            blocks.add(new Bricks((i * 60 + 2), 75, 60, 25, new Color((int) (Math.random() * 0x1000000))));
        }
        for (int i = 3; i < 7; i++) {
            blocks.add(new Bricks((i * 60 + 2), 100, 60, 25, new Color((int) (Math.random() * 0x1000000))));

        }
        for (int i = 4; i < 6; i++) {
            blocks.add(new Bricks((i * 60 + 2), 125, 60, 25, new Color((int) (Math.random() * 0x1000000))));

        }
        for (int i = 4; i < 6; i++) {
            blocks.add(new Bricks((i * 60 + 2), 150, 60, 25, new Color((int) (Math.random() * 0x1000000))));

        }
        for (int i = 2; i < 8; i++) {
            blocks.add(new Bricks((i * 60 + 2), 175, 60, 25, new Color((int) (Math.random() * 0x1000000))));

        }
        for (int i = 0; i < 10; i++) {
            blocks.add(new Bricks((i * 60 + 2), 200, 60, 25, new Color((int) (Math.random() * 0x1000000))));

        }
        addKeyListener(this);
        setFocusable(true);

    }

    /**
     * Gameplay metoden anropar reset metoden
     */
    public GamePlay() {
        reset();

    }

    /**
     * Man gör en objekt av gameplay klassen i init
     * gör en ny JFrame
     * Gör det synligt
     * Lägger in gameplay klassen in i Jframe
     * Tilldelar värde aå att krysset stänger av fönstret
     * sätter koordinater och storleken på Jframe
     * sätter så att man inte ska kunna ändra på fönstrets storlek
     * Startar gameplay
     */
    public static void init() {
        GamePlay game = new GamePlay();

        JFrame frame = new JFrame("Prog2slutprojekt");

        
        frame.add(game);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setBounds(1, 1, 600, 700);
        frame.setResizable(false);

        frame.setVisible(true);
        game.start();

    }

    /**
     * Metoden startar tråden
     */
    public synchronized void start() {

        thread = new Thread(this);

        thread.start();

    }

    /**
     * Metoden renderar all object i spelet 1,2 och 3 deklarerar fonten 4,5,6,7
     * och 8 skapar en bufferstrategi för att spelet ska kunna rendera den
     * senaste framen 8 och 9 skapar en objekt av grafiken och ger den en arv
     * 10,11,12 och 13 ritar ut siffrorna för poäng, streak och tillämpar fonten
     * 14 - 19 ritar ut texten ifall man har vunnit eller förlorad
     * 20 - 24 renderar paddeln,bollen, brickorna och powerups i en for loop 
     * 25, tar bort object som har ritats
     * 26, ritar ut spelplanen som innehåller allt ovanför
     */
    public void render() {

        Font f = new Font("Comic Sans MS", Font.BOLD, fontSize);
        Font fStor = new Font("Bungee", Font.BOLD, Storfont);
       

        BufferStrategy bs = this.getBufferStrategy();
        if (bs == null) {
            createBufferStrategy(2);
            return;
        }

        Graphics g = bs.getDrawGraphics();
        super.paint(g);//

        g.setFont(f);
        g.drawString("Score:" + finalscore, 1, 20);

        g.drawString("Streak: x " + streak, 200, 500);
        g.setFont(fStor);
        if (amountblocks == 0) {

            g.drawString("You won!", 100, 300);
            g.drawString("Press Enter to Reset or Esc to exit", 1, 600);

        }
        if (ball.getY() > 700) {

            g.drawString("You lost!", 100, 300);
            g.drawString("Press Enter to Reset or Esc to exit", 1, 600);
        }


        paddle.render(g);

        for (Bricks block : blocks) {
            block.draw(g);
        }

        for (PowerupBall p : powerUp) {
            p.render(g);
        }


        ball.render(g);
        g.dispose();
        bs.show();

    }
    /**
     * i run metod anropas alla klasser som ska flytta på objekter
     * alla metoder anropas i en while loop som alltid är true
     * Här anropas thread sleep vilken gör spel hastigheten optimal för olika datorer
     */
    @Override
    public void run() {
         try {
                readscore();
            } catch (IOException ex) {
               
            }
        while (true) {
           
            render();
            ball.run();
            paddle.run();
            for (PowerupBall p : powerUp) {
                p.run();
            }

            kollisionPaddle();
            kollisionbrick();
            action();
            kollisionPowerup();
            writescore();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

  /**
   * Metoden skapar olika powerups om bollen kolliderar med en bricka, den väljer en slumpvis tal mellan 1 till 10
   * Om siffran blir 1,2,3 eller 4 skapas en ny objekt av klassen powerupball och sedan renderas den och faller ner
   */
    public void action() {

        if (isPowerUp) {
            isPowerUp = false;
            int powerups = 1 + dice.nextInt(10);
            if (powerups == 1) {
                PowerupBall b = new PowerupBall(10 + dice.nextInt(550), -100, Color.RED);
                b.setY(1);
                b.setSpeed(5);
                powerUp.add(b);

            }
            if (powerups == 2) {

                PowerupBall b = new PowerupBall(10 + dice.nextInt(550), -100, Color.YELLOW);
                b.setY(1);
                b.setSpeed(2);
                powerUp.add(b);

            }
            if (powerups == 3) {
                PowerupBall c = new PowerupBall(10 + dice.nextInt(550), -100, Color.GREEN);
                c.setY(1);
                c.setSpeed(6);
                powerUp.add(c);

            }
            if (powerups == 4) {
                PowerupBall c = new PowerupBall(10 + dice.nextInt(550), -100, Color.BLUE);
                c.setY(1);
                c.setSpeed(3);
                powerUp.add(c);

            }
        }

    }
/**
 * Metoden kollar om paddeln har träffat en powerup
 * Absolutbeloppet mellan paddelns och powerupsbollarnas central x punkt räknas
 * Absolutbeloppet mellan paddelns och powerupsbollarnas central y punkt räknas
 * Om x absolutbeloppet är mindre än halva paddelns bredd och y absolutbloppet är mindre eller lika med halva paddelns höjd plus bollarnas radie får man en kollision
 * Bollarnas färger avgör vilka powerups eller poerdowns de ger genom if satsen
 * Detta görs om för varje boll i en for loop och sedan breakas loopen
 */
    private void kollisionPowerup() {
        for (PowerupBall p : powerUp) {

            double BallDistancex = abs((p.getX() + p.getRadie()) - (paddle.getX() + (paddle.getWidth() / 2)));
            double BallDistancey = abs((p.getY() - p.getRadie()) - (paddle.getY() - (paddle.getHeight() / 2)));

            if (BallDistancex < (paddle.getWidth() / 2) && BallDistancey <= ((paddle.getHeight() / 2) + p.getRadie())) {
                

                if (p.color == Color.RED) {
                    paddle.setY(650);

                    powerUp.remove(p);
                } else if (p.color == Color.GREEN) {
                    paddle.setY(350);

                    powerUp.remove(p);
                } else if (p.color == Color.BLUE) {
                    paddle.setWidth(50);

                    powerUp.remove(p);
                } else if (p.color == Color.YELLOW) {
                    paddle.setWidth(250);

                    powerUp.remove(p);
                }

                break;
            }
        }
    }
/**
 * Metoden kollar om bollen kollidera med paddlen
 * 1, Absolutbeloppet mellan paddelns och powerupsbollarnas central x punkt räknas
 * 2, Absolutbeloppet mellan paddelns och powerupsbollarnas central y punkt räknas
 * 3, första if satsens villkor är om x aboslutbelloppen är mellan halva och en fjärde del av paddlens bredd och om y absolutbeloppet är mindre än halva paddlens höjd plus raide och om bollens speedxkollision är mellan 1 och -1
 * 4, om första if satsens krav uppfylls multipliceras bollens y hastighet med -1 och speedx for kollision ökas med 1 och o´poäng streaken återställs
 * 5, andra if satsens villkor är om x absolutbelloppen är mellan 0 och halva paddelns bredd och om speedxfor kollison inte är mellan 3 och -3
 * 6, om alla uppfylls sänks speedxforkollision med 2, bollens y hastighet multipliceras med -1 och poäng streken återställs
 * 7, Sista i satsen kollar om bollen träffar sidor av paddle och om kraven uppfylls multipliceras båda x och y speed med -1 och poäng streaken återställs
 */
    private void kollisionPaddle() {

        double BallDistancex = abs((ball.getX() + ball.getRadie()) - (paddle.getX() + (paddle.getWidth() / 2)));
        double BallDistancey = abs((ball.getY() - ball.getRadie()) - (paddle.getY() - (paddle.getHeight() / 2)));

        if (BallDistancex <= paddle.getWidth() / 2 && BallDistancex >= paddle.getWidth() / 4 && BallDistancey <= ((paddle.getHeight() / 2) + ball.getRadie())
                && ball.getSpeedXforcollision() <= 1 && ball.getSpeedXforcollision() >= -1) {

            ball.setSpeedY(-1);

            ball.setSpeedXforcollision(+2);
            Temporarlyscore = 0;
            streak = 0;
           
        } else if (BallDistancex <= paddle.getWidth() / 2 && BallDistancex >= 0 && BallDistancey <= ((paddle.getHeight() / 2) + ball.getRadie())) {
            if (ball.getSpeedXforcollision() >= 3 || ball.getSpeedXforcollision() <= -3) {
                ball.setSpeedXforcollision(-2);

            }

            ball.setSpeedY(-1);

            Temporarlyscore = 0;
            streak = 0;
           

        } else if (BallDistancex <= ((paddle.getWidth() / 2) + ball.getRadie()) && BallDistancey < ((paddle.getHeight() / 2) + ball.getRadie())) {
            ball.setSpeedY(-1);
            ball.setSpeedX(-1);
            Temporarlyscore = 0;
            streak = 0;
          

        }

    }
    /**
     * Metod kollar om bollen kolliderar med brickorna i genom en for loop
     * Absolutbeloppet mellan paddelns och powerupsbollarnas central x punkt räknas
     * Absolutbeloppet mellan paddelns och powerupsbollarnas central y punkt räknas
     * första if satsens krav är att xabsolut belopp ska vara mindre eller lika med havla brickans bredd plus radie och y absolutbelopp ska vara mindre än eller lika med halva brickans höjd plus bollens radie
     * Om första if satsens krav uppfylls försvinner brickan, power up blir true och poängen ökar. x hastigheten på bollen multipliceras med -1
     * Andra if satsen kollar om bollen träffar brickans under eller ovan sida 
     * Om den uppfylls multipliceras bollens y hastighet med -1, brickans försvinner och poängen ökar, samt powerup blir true
     */

    private void kollisionbrick() {
        for (Bricks block : blocks) {

            double circleDistancexx = abs((ball.getX() + ball.getRadie()) - (block.getX() + (block.getW() / 2)));
            double circleDistanceyy = abs((ball.getY() + ball.getRadie()) - (block.getY() + (block.getH() / 2)));

            if (circleDistancexx <= (block.getW() / 2 + ball.getRadie()) && circleDistanceyy <= ((block.getH() / 2) + ball.getRadie())) {

                ball.setSpeedX(-1);
                finalscore += Temporarlyscore;
                block.Crushed = true;
                blocks.remove(block);
                amountblocks--;
                streak += 1;
                Temporarlyscore += streak * 100;
               
                isPowerUp = true;
                break;

            } else if (circleDistancexx < (block.getW() / 2) && circleDistanceyy <= (block.getH())) {

                ball.setSpeedY(-1);
                finalscore += Temporarlyscore;

                block.Crushed = true;
                blocks.remove(block);
                amountblocks--;
                streak += 1;
                Temporarlyscore += streak * 100;
                
                isPowerUp = true;
                break;
            }

        }
    }
    /**
     *  Metoden har i uppgift att skriva in senaste poänget i en fil och spara den
     * Man har gjort en object av FileWriter
     * Man har gjort en object av printwriter
     * Printwriter hämtar värde från finalscore och skriver in det i filen
     */
    public void writescore(){
       
           
        try {
           FileWriter   fw = new FileWriter("Lastscore");
             PrintWriter pw = new PrintWriter(fw);
             
               pw.println(finalscore);
            pw.close();
            
        } catch (IOException ex) {
          JOptionPane.showMessageDialog(null,"No highscore found!");
        }
           
            
          
        
    }
    /**
     * Läser in från filen
     * @throws IOException Försöker läsa från filen
     * Man gör en object av klassen Filereader som försöker hitta filen som har namngets
     * Man gör en objekt av BUfferedreader som läser inuti filen
     * genom while metoden läser man filen tills filen är slut
     */
    public void readscore() throws IOException{
        
        
        try {
           
           FileReader fr = new FileReader("Lastscore");
            BufferedReader br = new BufferedReader(fr);
            String poang;
            while((poang = br.readLine()) !=null){
                JOptionPane.showMessageDialog(null,"This is the last score: " + poang + "\n Try and beat it!");
            }   br.close();
        } catch (FileNotFoundException ex) {
            System.out.println("File doesn't exist!");
        } 
    }

    @Override
    public void keyTyped(KeyEvent ke) {

    }
    /**
     * Metoden utför funktion beroende på vad användaren matar in i tanget bordet.
     * @param ke Den tar emot inmatning från tangentbordet
     * Första ifatsens startar om spelet om bollen har kommit under spel planen och användaren trycker enter eller om det är inga brickor kvar och användaren trycker på enter
     * Andra ifsatsen stänger spelet om bollen har kommit under spel planen och användaren trycker enter eller om det är inga brickor kvar och användaren trycker på enter
     * Tredje ifsatsen flyttar på paddeln till höger om den är inom spel planet och man trycker på högerpil knapp
     * Fjärde ifsatsen gör mot satsen till tredje
     */
    @Override
    public void keyPressed(KeyEvent ke) {
        if (ke.getKeyCode() == KeyEvent.VK_ENTER && ball.getY() > 600 || amountblocks == 0 && ke.getKeyCode() == KeyEvent.VK_ENTER) {
            reset();

            finalscore = 0;

        }
        if (ke.getKeyCode() == KeyEvent.VK_ESCAPE && ball.getY() > 600 || amountblocks == 0 && ke.getKeyCode() == KeyEvent.VK_ESCAPE) {
            System.exit(0);
        }

        if (ke.getKeyCode() == KeyEvent.VK_RIGHT && paddle.getX() < (getWidth() - paddle.getWidth())) {
            paddle.setSpeedX(+5);
        }
        if (ke.getKeyCode() == KeyEvent.VK_LEFT && paddle.getX() > 0) {
            paddle.setSpeedX(-5);

        }

    }
/**
 * Metoden utför funktion beroende på vilken tangetbord användaren släpper
 * @param ke Den tar emot inmatning från tangentbordet
 * Första metoden sätter paddens x speed till 0 om man släpper höger pil tangenten
 * Andra metoden gör samma för vänster
 */
    @Override
    public void keyReleased(KeyEvent ke) {
        if (ke.getKeyCode() == KeyEvent.VK_RIGHT && paddle.getX() < (getWidth() - paddle.getWidth())) {
            paddle.setSpeedX(0);
        }
        if (ke.getKeyCode() == KeyEvent.VK_LEFT && paddle.getX() > 0) {
            paddle.setSpeedX(0);

        }
    }
}
