package com.iafenvoy.pendulum.api;

import java.util.Arrays;
import java.util.List;

/**
 * Function names for the "mc.player" JS sub-object.
 * All method implementations live in com.iafenvoy.pendulum.script.MinecraftAPI.
 */
public final class PlayerAPI {
    private PlayerAPI() {}

    public static final List<String> FUNCTION_NAMES = Arrays.asList(
            // Movement
            "forward", "back", "left", "right", "stop",
            "jump", "sneak", "sprint", "stopSprint",
            // Rotation
            "lookAt", "setYaw", "setPitch", "getYaw", "getPitch",
            // Position
            "getX", "getY", "getZ",
            // State
            "getPlayerHealth", "getPlayerHunger", "getPlayerArmor",
            "getAttackCooldown", "getReachDistance",
            "canReach", "canSeeBlock",
            // Interaction
            "attack", "use", "useItem", "startUse", "stopUse",
            "breakBlock", "breakBlockAt", "placeBlock", "placeBlockAt", "jumpAndPlaceBelow",
            "swapHands", "drop", "dropAll", "pickBlock"
    );
}
