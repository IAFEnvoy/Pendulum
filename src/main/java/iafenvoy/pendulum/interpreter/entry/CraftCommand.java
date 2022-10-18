package iafenvoy.pendulum.interpreter.entry;

import com.google.common.collect.Lists;
import iafenvoy.pendulum.interpreter.PendulumInterpreter;
import iafenvoy.pendulum.interpreter.util.DataLoader;
import iafenvoy.pendulum.interpreter.util.OptionalResult;
import iafenvoy.pendulum.interpreter.util.entry.HelpTextProvider;
import iafenvoy.pendulum.interpreter.util.entry.VoidCommandEntry;
import iafenvoy.pendulum.utils.ItemUtils;
import iafenvoy.pendulum.utils.ThreadUtils;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.recipe.Recipe;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;

public class CraftCommand extends VoidCommandEntry implements HelpTextProvider {
    public CraftCommand() {
        super("craft");
    }

    @Override
    public OptionalResult<Object> execute(PendulumInterpreter interpreter, String command) {
        List<String> items = Lists.newArrayList(command.split(" "));
        if (items.size() != 1) return new OptionalResult<>("craft command should have 1 arguments");
        Item item = ItemUtils.GetItemFromName(items.get(0));
        List<Recipe<?>> recipes = new ArrayList<>(DataLoader.craftableRecipe);
        Recipe<?> target = null;
        for (Recipe<?> recipe : recipes)
            if (recipe.getOutput().getItem() == item)
                target = recipe;
        if (target == null || item == Items.AIR) return new OptionalResult<>("Not matched recipe was found!");
        assert client.interactionManager != null;
        assert client.player != null;
        client.interactionManager.clickRecipe(client.player.currentScreenHandler.syncId, target, false);
        ThreadUtils.sleep(DataLoader.sleepDelta);
        client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, 0, 0, SlotActionType.QUICK_MOVE, client.player);
        return new OptionalResult<>();
    }

    @Override
    public String getHelpText() {
        return "craft <item> | Craft specific item once, fail while there is no enough materials.";
    }
}
