package iafenvoy.pendulum.interpreter.entry;

import com.google.common.collect.Lists;
import iafenvoy.pendulum.interpreter.PendulumInterpreter;
import iafenvoy.pendulum.interpreter.util.OptionalResult;
import iafenvoy.pendulum.interpreter.util.entry.BooleanCommandEntry;
import iafenvoy.pendulum.interpreter.util.entry.HelpTextProvider;
import iafenvoy.pendulum.utils.RegistryUtils;
import iafenvoy.pendulum.utils.NumberUtils;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.List;

public class HasCommand extends BooleanCommandEntry implements HelpTextProvider {
    public HasCommand() {
        super("has");
    }

    @Override
    public OptionalResult<Boolean> execute(PendulumInterpreter interpreter, String command) {
        List<String> items = Lists.newArrayList(command.split(" "));
        if (items.size() == 0) throw new IllegalArgumentException("has command should have 1-2 arguments");
        Item target = RegistryUtils.getItemByName(items.get(0));
        int count = 1;
        if (items.size() >= 2) count = NumberUtils.parseInt(items.get(1));
        if (target == Items.AIR) return new OptionalResult<>("The item can't be found");
        assert client.player != null;
        PlayerInventory inventory = client.player.inventory;
        int haveCount = 0;
        for (int i = 0; i < inventory.size(); i++)
            if (inventory.getStack(i).getItem() == target) {
                haveCount += inventory.getStack(i).getCount();
                if (haveCount >= count) return new OptionalResult<>(true);
            }
        return new OptionalResult<>(false);
    }

    @Override
    public String getHelpText() {
        return "has <item> <count=1> | Check if there has specified quantity of item.";
    }
}
