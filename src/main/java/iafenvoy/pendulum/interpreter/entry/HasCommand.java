package iafenvoy.pendulum.interpreter.entry;

import com.google.common.collect.Lists;
import iafenvoy.pendulum.interpreter.PendulumInterpreter;
import iafenvoy.pendulum.interpreter.util.entry.BooleanCommandEntry;
import iafenvoy.pendulum.utils.ItemUtils;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.List;

public class HasCommand implements BooleanCommandEntry {
    @Override
    public String getPrefix() {
        return "has";
    }

    @Override
    public boolean execute(PendulumInterpreter interpreter, String command) {
        List<String> items = Lists.newArrayList(command.split(" "));
        if (items.size() != 1) throw new IllegalArgumentException("has command should have 1 arguments");
        Item target = ItemUtils.GetItemFromName(items.get(0));
        if (target == Items.AIR) return false;
        assert client.player != null;
        PlayerInventory inventory = client.player.inventory;
        for (int i = 0; i < inventory.size(); i++)
            if (inventory.getStack(i).getItem() == target)
                return true;
        return false;
    }
}
