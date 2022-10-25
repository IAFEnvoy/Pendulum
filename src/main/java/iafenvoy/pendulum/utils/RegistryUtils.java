package iafenvoy.pendulum.utils;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public class RegistryUtils {
    public static Item getItemByName(String in) {
        if (!in.contains(":")) return Registry.ITEM.get(new Identifier("minecraft", in));
        else return Registry.ITEM.get(new Identifier(in.split(":")[0], in.split(":")[1]));
    }

    public static Block getBlockByName(String in) {
        if (!in.contains(":")) return Registry.BLOCK.get(new Identifier("minecraft", in));
        else return Registry.BLOCK.get(new Identifier(in.split(":")[0], in.split(":")[1]));
    }

    public static EntityType<?> getEntityByName(String in) {
        if (!in.contains(":")) return Registry.ENTITY_TYPE.get(new Identifier("minecraft", in));
        else return Registry.ENTITY_TYPE.get(new Identifier(in.split(":")[0], in.split(":")[1]));
    }
}
