package com.iafenvoy.pendulum.api;

import java.util.Arrays;
import java.util.List;

/**
 * Function names for the "mc.inv" JS sub-object.
 * All method implementations live in com.iafenvoy.pendulum.script.MinecraftAPI.
 */
public final class InventoryAPI {
    private InventoryAPI() {}

    public static final List<String> FUNCTION_NAMES = Arrays.asList(
            // Hotbar
            "selectHotbar", "getSelectedSlot",
            // Query
            "hasItem", "getItemInSlot", "getItemInHand", "getItemOffhand", "getAllItems",
            // Container
            "getContainerSize", "getContainerItem", "getContainerAllItems", "getContainerType",
            "clickSlot", "clickSlotRight", "moveItem", "quickMoveItem",
            // Crafting
            "craft", "craftAll"
    );
}
