package iafenvoy.pendulum.interpreter.entry;

import iafenvoy.pendulum.interpreter.PendulumInterpreter;
import iafenvoy.pendulum.interpreter.util.entry.VoidCommandEntry;
import net.minecraft.entity.player.PlayerInventory;

public class HotBarCommand implements VoidCommandEntry {
    @Override
    public String getPrefix() {
        return "hotbar";
    }

    @Override
    public void execute(PendulumInterpreter interpreter, String command) {
        int stack = Integer.parseInt(command);
        if (stack <= 0 || stack > PlayerInventory.getHotbarSize())
            throw new IndexOutOfBoundsException("Hotbar index should be 1-9!");
        assert client.player != null;
        client.player.inventory.selectedSlot = stack - 1;
    }
}
