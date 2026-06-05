package com.iafenvoy.pendulum.script;

import com.iafenvoy.pendulum.config.PendulumConfig;
import com.mojang.logging.LogUtils;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Function;
import dev.latvian.mods.rhino.Scriptable;
import org.slf4j.Logger;

/**
 * Global config &amp; logging API registered on the {@code pendulum} JS global.
 * <p>
 * Methods: {@code log}, {@code warn}, {@code error}, {@code isModLoaded}, {@code getPermission}.
 * All output goes to the game log AND the MCP eval return string.
 */
public final class PendulumAPI {
    private static final Logger LOGGER = LogUtils.getLogger();

    private PendulumAPI() {
    }

    // ==================== Logging ====================

    public static void log(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        pendulumLogImpl("LOG", arrayToString(cx, args));
    }

    public static void warn(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        pendulumLogImpl("WARN", arrayToString(cx, args));
    }

    public static void error(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        pendulumLogImpl("ERROR", arrayToString(cx, args));
    }

    // ==================== Mod Detection ====================

    /**
     * Check if a mod is loaded by its mod ID.
     *
     * @param args e.g. "sodium", "iris", "jei", "baritone"
     * @return true if the mod is present in the current mod list
     */
    public static boolean isModLoaded(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String modId = cx.toString(args[0]);
        //? fabric {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(modId);
        //?} else forge {
        // return net.minecraftforge.fml.ModList.get().isLoaded(modId);
        //?} else neoforge {
        // return net.neoforged.fml.ModList.get().isLoaded(modId);
        //?} else {
        // return false;
        //?}
    }

    // ==================== Config Permissions ====================

    /**
     * Read a permission from PendulumConfig at runtime.
     * Supported keys:
     * allowBreak, allowPlace, allowAttack, allowExecuteCommand, allowSay,
     * mcpEnabled, syncUseAttack, logJsErrors,
     * breakTimeout, rayTraceDistance, tickIntervalMs, mcpPort
     *
     * @return boolean for booleans, number for numeric entries, or null if unknown
     */
    public static Object getPermission(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String key = cx.toString(args[0]);
        return ScriptEngine.submitToGameThread(() -> {
            return switch (key) {
                case "allowBreak" -> PendulumConfig.INSTANCE.allowBreak.getValue();
                case "allowPlace" -> PendulumConfig.INSTANCE.allowPlace.getValue();
                case "allowAttack" -> PendulumConfig.INSTANCE.allowAttack.getValue();
                case "allowExecuteCommand" -> PendulumConfig.INSTANCE.allowExecuteCommand.getValue();
                case "allowSay" -> PendulumConfig.INSTANCE.allowSay.getValue();
                case "mcpEnabled" -> PendulumConfig.INSTANCE.mcpEnabled.getValue();
                case "syncUseAttack" -> PendulumConfig.INSTANCE.syncUseAttack.getValue();
                case "logJsErrors" -> PendulumConfig.INSTANCE.logJsErrors.getValue();
                case "breakTimeout" -> PendulumConfig.INSTANCE.breakTimeout.getValue();
                case "rayTraceDistance" -> PendulumConfig.INSTANCE.rayTraceDistance.getValue();
                case "tickIntervalMs" -> PendulumConfig.INSTANCE.tickIntervalMs.getValue();
                case "mcpPort" -> PendulumConfig.INSTANCE.mcpPort.getValue();
                default -> null;
            };
        });
    }

    // ==================== Internal Helpers ====================

    private static void pendulumLogImpl(String level, String msg) {
        LOGGER.info("[Pendulum JS] {}", msg);
        StringBuilder capture = ScriptEngine.getInstance().mcpLogCapture;
        if (capture != null) {
            if (!capture.isEmpty()) capture.append("\n");
            capture.append("[").append(level).append("] ").append(msg);
        }
    }

    private static String arrayToString(Context cx, Object[] args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(" ");
            sb.append(cx.toString(args[i]));
        }
        return sb.toString();
    }
}
