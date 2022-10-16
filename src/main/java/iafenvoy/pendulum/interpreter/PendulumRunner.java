package iafenvoy.pendulum.interpreter;

import iafenvoy.pendulum.interpreter.util.OptionalResult;
import iafenvoy.pendulum.interpreter.util.TaskQueue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class PendulumRunner {
    private static final TaskQueue<String> queue = new TaskQueue<>(cmd -> {
        OptionalResult<Object> error = new PendulumInterpreter().interpret(cmd.split(";"));
        if (error.hasError()) {
            assert MinecraftClient.getInstance().player != null;
            MinecraftClient.getInstance().player.sendMessage(Text.of(error.getResult().getErrorMessage()), false);
        }
    });

    public static void pushCommands(String cmd) {
        queue.offer(cmd);
    }
}
