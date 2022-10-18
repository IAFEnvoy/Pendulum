package iafenvoy.pendulum.interpreter.entry;

import iafenvoy.pendulum.interpreter.PendulumInterpreter;
import iafenvoy.pendulum.interpreter.util.OptionalResult;
import iafenvoy.pendulum.interpreter.util.entry.HelpTextProvider;
import iafenvoy.pendulum.interpreter.util.entry.VoidCommandEntry;
import net.minecraft.entity.player.PlayerInventory;

public class HotBarCommand extends VoidCommandEntry implements HelpTextProvider {
    public HotBarCommand() {
        super("hotbar");
    }

    @Override
    public OptionalResult<Object> execute(PendulumInterpreter interpreter, String command) {
        int stack = Integer.parseInt(command);
        if (stack <= 0 || stack > PlayerInventory.getHotbarSize())
            return new OptionalResult<>("Hotbar index should be 1-9!");
        assert client.player != null;
        client.player.inventory.selectedSlot = stack - 1;
        return new OptionalResult<>();
    }

    @Override
    public String getHelpText() {
        return "hotbar <1-9> | Change to the specific hot bar.";
    }
}
