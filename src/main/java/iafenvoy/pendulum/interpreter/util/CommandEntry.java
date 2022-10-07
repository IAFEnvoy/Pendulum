package iafenvoy.pendulum.interpreter.util;

import net.minecraft.client.MinecraftClient;

public interface CommandEntry {
    MinecraftClient client = MinecraftClient.getInstance();
    String getPrefix();
}
