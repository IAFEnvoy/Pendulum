package iafenvoy.pendulum;

import com.mojang.brigadier.arguments.StringArgumentType;
import iafenvoy.pendulum.interpreter.CoreCommandRegister;
import iafenvoy.pendulum.interpreter.PendulumRunner;
import iafenvoy.pendulum.interpreter.util.DataLoader;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;

public class Pendulum implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CoreCommandRegister.register();
        ClientCommandManager.DISPATCHER.register(ClientCommandManager.literal("pendulum").then(ClientCommandManager.argument("cmd", StringArgumentType.greedyString()).executes(ctx -> {
            PendulumRunner.pushCommands(StringArgumentType.getString(ctx, "cmd"));
            return 0;
        })));
        ClientCommandManager.DISPATCHER.register(ClientCommandManager.literal("commandDelta").then(ClientCommandManager.argument("time", StringArgumentType.word()).executes(ctx -> {
            DataLoader.sleepDelta = Integer.parseInt(StringArgumentType.getString(ctx, "time"));
            return 0;
        })));
    }
}
