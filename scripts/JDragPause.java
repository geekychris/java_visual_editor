import java.awt.Robot;
import java.awt.event.InputEvent;

// Drag from (fx,fy) to (mx,my), pause N seconds, then continue to (tx,ty) and release.
// During the pause, the mouse stays pressed at (mx,my) — useful for capturing mid-drag.
public class JDragPause {
    public static void main(String[] args) throws Exception {
        int fx = Integer.parseInt(args[0]);
        int fy = Integer.parseInt(args[1]);
        int mx = Integer.parseInt(args[2]);
        int my = Integer.parseInt(args[3]);
        int tx = Integer.parseInt(args[4]);
        int ty = Integer.parseInt(args[5]);
        int pauseMs = Integer.parseInt(args[6]);

        Robot r = new Robot();
        r.setAutoDelay(20);

        r.mouseMove(fx, fy);
        Thread.sleep(200);
        r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(150);

        // Drag to mid-point in steps
        int steps = 16;
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            int x = (int) (fx + (mx - fx) * t);
            int y = (int) (fy + (my - fy) * t);
            r.mouseMove(x, y);
        }
        Thread.sleep(100);
        System.out.println("paused at " + mx + "," + my);

        // Pause — mouse stays pressed
        Thread.sleep(pauseMs);

        // Continue to end
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            int x = (int) (mx + (tx - mx) * t);
            int y = (int) (my + (ty - my) * t);
            r.mouseMove(x, y);
        }
        Thread.sleep(150);
        r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(200);
        System.out.println("released at " + tx + "," + ty);
    }
}
