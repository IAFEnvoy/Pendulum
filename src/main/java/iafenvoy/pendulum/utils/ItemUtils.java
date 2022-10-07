package iafenvoy.pendulum.utils;

import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public class ItemUtils {
    public static Item GetItemFromName(String in) {
        Item item;
        if (!in.contains(":"))
            item = Registry.ITEM.get(new Identifier("minecraft", in));
        else
            item = Registry.ITEM.get(new Identifier(in.split(":")[0], in.split(":")[1]));
        return item;
    }
}
