package iafenvoy.pendulum.interpreter.entry;

import com.google.common.collect.Lists;
import com.ibm.icu.impl.Pair;
import iafenvoy.pendulum.interpreter.util.VoidCommandEntry;
import iafenvoy.pendulum.utils.ItemUtils;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;

public class CraftCommand implements VoidCommandEntry {
    @Override
    public String getPrefix() {
        return "craft";
    }

    public static int getItemLocation(PlayerInventory inventory, Item item) {
        if (item == Items.AIR) return -1;
        for (int i = 0; i < inventory.size(); i++)
            if (inventory.getStack(i).getItem() == item)
                return i;
        return -1;
    }

    @Override
    public void execute(String command) {
        new UseCommand().execute("");
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        List<String> items = Lists.newArrayList(command.split(" "));
        if (items.size() != 9) throw new IllegalArgumentException("craft command should have 9 arguments");
        if (!(client.currentScreen instanceof CraftingScreen))
            throw new IllegalStateException("craft command should be called when the crafting table screen is open");
        assert client.player != null;
        List<Pair<Integer, Item>> ingredients = new ArrayList<>();
        for (String item : items) {
            if (item.equals("~")) ingredients.add(Pair.of(-1, Items.AIR));
            else {
                Item i = ItemUtils.GetItemFromName(item);
                int l = getItemLocation(client.player.inventory, i);
                if (i == Items.AIR || l >= 0)
                    ingredients.add(Pair.of(l, i));
                else
                    throw new IllegalStateException("There is no item called " + item + " in the inventory");
            }
        }
        assert client.interactionManager != null;
        for (int i = 0; i < 9; i++) {
            System.out.println(ingredients.get(i).first + " " + ingredients.get(i).second);
            if (ingredients.get(i).second != Items.AIR) {
                client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, transferSlot(ingredients.get(i).first), 0, SlotActionType.PICKUP_ALL, client.player);
                client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, i + 1, 1, SlotActionType.PICKUP, client.player);
                client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, transferSlot(ingredients.get(i).first), 0, SlotActionType.PICKUP, client.player);
            }
        }
    }

    public static int transferSlot(int i) {
        if (0 <= i && i < 9) return i + 37;
        if (9 <= i && i < 36) return i + 1;
        return -1;
    }
}
