package iafenvoy.pendulum.interpreter.entry;

import iafenvoy.pendulum.interpreter.util.VoidCommandEntry;
import iafenvoy.pendulum.mixins.IMixinMinecraftClient;

public class UseCommand implements VoidCommandEntry {
    @Override
    public String getPrefix() {
        return "use";
    }

    @Override
    public void execute(String command) {
        assert client.player != null;
        if (((IMixinMinecraftClient) client).getItemUseCooldown() == 0 && !client.player.isUsingItem())
            ((IMixinMinecraftClient) client).invokeDoItemUse();
    }
}
