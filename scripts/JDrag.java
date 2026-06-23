import java.awt.Robot;
import java.awt.event.InputEvent;

public class JDrag {
    public static void main(String[] args) throws Exception {
        int fx = Integer.parseInt(args[0]);
        int fy = Integer.parseInt(args[1]);
        int tx = Integer.parseInt(args[2]);
        int ty = Integer.parseInt(args[3]);
        int steps = args.length > 4 ? Integer.parseInt(args[4]) : 20;

        Robot r = new Robot();
        r.setAutoDelay(30);

        r.mouseMove(fx, fy);
        Thread.sleep(200);
        r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(150);
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            int x = (int) (fx + (tx - fx) * t);
            int y = (int) (fy + (ty - fy) * t);
            r.mouseMove(x, y);
        }
        Thread.sleep(150);
        r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(200);
        System.out.println("drag " + fx + "," + fy + " -> " + tx + "," + ty);
    }
}
