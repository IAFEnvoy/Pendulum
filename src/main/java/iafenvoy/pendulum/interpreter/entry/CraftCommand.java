package iafenvoy.pendulum.interpreter.entry;

import com.google.common.collect.Lists;
import iafenvoy.pendulum.interpreter.util.DataLoader;
import iafenvoy.pendulum.interpreter.util.entry.VoidCommandEntry;
import iafenvoy.pendulum.utils.ItemUtils;
import iafenvoy.pendulum.utils.ThreadUtils;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.recipe.Recipe;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;

public class CraftCommand implements VoidCommandEntry {
    @Override
    public String getPrefix() {
        return "craft";
    }

    @Override
    public void execute(String command) {
        List<String> items = Lists.newArrayList(command.split(" "));
        if (items.size() != 1) throw new IllegalArgumentException("craft command should have 1 arguments");
        Item item = ItemUtils.GetItemFromName(items.get(0));
        List<Recipe<?>> recipes = new ArrayList<>(DataLoader.craftableRecipe);
        Recipe<?> target = null;
        for (Recipe<?> recipe : recipes)
            if (recipe.getOutput().getItem() == item)
                target = recipe;
        if (target == null || item == Items.AIR) throw new IllegalArgumentException("Not matched recipe was found!");
        assert client.interactionManager != null;
        assert client.player != null;
        client.interactionManager.clickRecipe(client.player.currentScreenHandler.syncId, target, false);
        ThreadUtils.sleep(DataLoader.sleepDelta);
        client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, 0, 0, SlotActionType.QUICK_MOVE, client.player);
    }
}
