package iafenvoy.pendulum.interpreter;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;

public class PendulumExecutor {
    private static final Queue<Event> queue = new ArrayDeque<>();
    private static boolean isRunning = false;

    public static void schedule(Event event) {
        queue.offer(event);
    }

    public static void start() {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                if (!queue.isEmpty() && !isRunning)
                    new Thread(() -> {
                        while (!queue.isEmpty())
                            queue.poll().execute();
                        isRunning = false;
                    }).start();
            }
        };
        new Timer().schedule(task,1000);
    }

    public interface Event {
        void execute();
    }
}
