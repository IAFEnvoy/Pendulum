package iafenvoy.pendulum.interpreter;

import iafenvoy.pendulum.interpreter.util.OptionalResult;
import iafenvoy.pendulum.interpreter.util.TaskQueue;
import iafenvoy.pendulum.utils.ClientUtils;

public class PendulumRunner {
    private static final TaskQueue<String> queue = new TaskQueue<>(cmd -> {
        OptionalResult<Object> error = new PendulumInterpreter().interpret(cmd.split(";"));
        if (error.hasError())
            ClientUtils.sendMessage(error.getResult().getErrorMessage());
    }, "Pendulum Interpreter");

    public static void pushCommands(String cmd) {
        queue.offer(cmd);
    }

    public static void stop() {
        queue.clear();
        queue.pauseThread();
    }

    public static boolean isSuspending() {
        return queue.isEmpty();
    }
}
