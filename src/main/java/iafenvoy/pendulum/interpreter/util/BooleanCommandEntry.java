package iafenvoy.pendulum.interpreter.util;

import net.minecraft.client.MinecraftClient;

public interface BooleanCommandEntry extends CommandEntry{

    boolean execute(String command);
}
