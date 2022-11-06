package iafenvoy.pendulum.interpreter;

import iafenvoy.pendulum.interpreter.util.OptionalResult;
import iafenvoy.pendulum.interpreter.util.TaskQueue;

public class PendulumRunner {
    private static TaskQueue<String> queue = create();

    public static void pushCommands(String cmd) {
        if (cmd.equals("stop")) {
            queue = create();
            System.out.println("recreate queue");
        }else
            queue.offer(cmd);
    }

    private static TaskQueue<String> create() {
        return new TaskQueue<>(cmd -> {
            OptionalResult<Object> error = new PendulumInterpreter().interpret(cmd);
            if (error.hasError())
                error.printStackTrace();
        }, "Pendulum Interpreter");
    }
}
