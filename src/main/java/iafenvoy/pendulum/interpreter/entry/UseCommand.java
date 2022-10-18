package iafenvoy.pendulum.interpreter.entry;

import iafenvoy.pendulum.interpreter.PendulumInterpreter;
import iafenvoy.pendulum.interpreter.util.OptionalResult;
import iafenvoy.pendulum.interpreter.util.entry.HelpTextProvider;
import iafenvoy.pendulum.interpreter.util.entry.VoidCommandEntry;
import iafenvoy.pendulum.mixins.IMixinMinecraftClient;

public class UseCommand extends VoidCommandEntry implements HelpTextProvider {
    public UseCommand() {
        super("use");
    }

    @Override
    public OptionalResult<Object> execute(PendulumInterpreter interpreter, String command) {
        assert client.player != null;
        if (((IMixinMinecraftClient) client).getItemUseCooldown() == 0 && !client.player.isUsingItem())
            ((IMixinMinecraftClient) client).invokeDoItemUse();
        return new OptionalResult<>();
    }

    @Override
    public String getHelpText() {
        return "use | Use item or interact with block (like use key press).";
    }
}
