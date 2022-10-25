package iafenvoy.pendulum.interpreter;

import iafenvoy.pendulum.interpreter.util.OptionalResult;
import iafenvoy.pendulum.interpreter.util.TaskQueue;
import iafenvoy.pendulum.utils.ClientUtils;

public class PendulumRunner {
    private static TaskQueue<String> queue = create();

    public static void pushCommands(String cmd) {
        if (cmd.equals("stop")) {
            queue = create();
            System.out.println("recreate queue");
        } else if (cmd.equals("pause")) {
            queue.pauseThread();
            System.out.println("pause thread");
        }
        else if (cmd.equals("resume")) {
            queue.resumeThread();
            System.out.println("resume thread");
        }
        else
            queue.offer(cmd);
    }

    private static TaskQueue<String> create() {
        return new TaskQueue<>(cmd -> {
            OptionalResult<Object> error = new PendulumInterpreter().interpret(cmd.split(";"));
            if (error.hasError())
                ClientUtils.sendMessage(error.getResult().getErrorMessage());
        }, "Pendulum Interpreter");
    }
}
