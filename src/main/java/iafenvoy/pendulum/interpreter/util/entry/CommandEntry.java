package iafenvoy.pendulum.interpreter.util.entry;

import net.minecraft.client.MinecraftClient;

public interface CommandEntry {
    MinecraftClient client = MinecraftClient.getInstance();

    String getPrefix();
}
