package iafenvoy.pendulum.interpreter;

import java.util.ArrayDeque;
import java.util.Queue;

public class PendulumExecutor {
    private static final Queue<String> commandQueue = new ArrayDeque<>();
    private static boolean shouldRunning = true;
    private static final Thread thread = new Thread(() -> {
        while (shouldRunning) {
            if (commandQueue.isEmpty()) continue;
            String command = commandQueue.poll();
            new PendulumInterpreter().interpret(command.split(";"));
        }
    });

    public static void interpretInNewThread(String command) {
        if (!thread.isAlive()) thread.start();
        commandQueue.offer(command);
    }

    public interface Event {
        void execute();
    }
}
