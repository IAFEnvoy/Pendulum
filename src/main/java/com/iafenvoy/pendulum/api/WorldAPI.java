package com.iafenvoy.pendulum.api;

import java.util.Arrays;
import java.util.List;

/**
 * Function names for the "mc.world" JS sub-object.
 * All method implementations live in com.iafenvoy.pendulum.script.MinecraftAPI.
 */
public final class WorldAPI {
    private WorldAPI() {}

    public static final List<String> FUNCTION_NAMES = Arrays.asList(
            // Block queries
            "getBlock", "isBlock", "isBlockByTag", "getBlockState",
            "findBlocks", "findBlocksByTag", "findBlocksInBox",
            // Crosshair & facing
            "facingBlock", "facingEntity", "getFacingBlock",
            // Entities
            "getNearbyEntities", "getNearbyPlayers",
            "getLookingEntity", "rayTrace",
            // Environment
            "getBiomeAt", "getLightLevel", "getDifficulty", "getDimension"
    );
}
