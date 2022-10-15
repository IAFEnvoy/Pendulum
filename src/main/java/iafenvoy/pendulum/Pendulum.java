package iafenvoy.pendulum;

import com.mojang.brigadier.arguments.StringArgumentType;
import iafenvoy.pendulum.interpreter.CoreCommandRegister;
import iafenvoy.pendulum.interpreter.PendulumInterpreter;
import iafenvoy.pendulum.interpreter.util.DataLoader;
import iafenvoy.pendulum.interpreter.util.OptionalResult;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class Pendulum implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CoreCommandRegister.register();
        ClientCommandManager.DISPATCHER.register(ClientCommandManager.literal("pendulum").then(ClientCommandManager.argument("cmd", StringArgumentType.greedyString()).executes(ctx -> {
            new Thread(() -> {
                String command = StringArgumentType.getString(ctx, "cmd");
                OptionalResult<Object> error = new PendulumInterpreter().interpret(command.split(";"));
                if (error.hasError()) {
                    assert MinecraftClient.getInstance().player != null;
                    MinecraftClient.getInstance().player.sendMessage(Text.of(error.getResult().getErrorMessage()), false);
                }
            }).start();
            return 0;
        })));
        ClientCommandManager.DISPATCHER.register(ClientCommandManager.literal("commandDelta").then(ClientCommandManager.argument("time", StringArgumentType.word()).executes(ctx -> {
            DataLoader.sleepDelta = Integer.parseInt(StringArgumentType.getString(ctx, "time"));
            return 0;
        })));
    }
}
