package iafenvoy.pendulum;

import com.mojang.brigadier.arguments.StringArgumentType;
import iafenvoy.pendulum.interpreter.CommandEntryManager;
import iafenvoy.pendulum.interpreter.PendulumExecutor;
import iafenvoy.pendulum.interpreter.PendulumInterpreter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;

public class Pendulum implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CommandEntryManager.initialize();
        PendulumExecutor.start();
        ClientCommandManager.DISPATCHER.register(ClientCommandManager.literal("pendulum").then(ClientCommandManager.argument("cmd", StringArgumentType.greedyString()).executes(ctx -> {
            new Thread(()->{
                String command = StringArgumentType.getString(ctx, "cmd");
                PendulumInterpreter.InterpretResult error = PendulumInterpreter.interpret(command);
                if (error != PendulumInterpreter.InterpretResult.EMPTY)
                    System.out.println(error.getErrorMessage());
            }).start();
            return 0;
        })));
    }
}
