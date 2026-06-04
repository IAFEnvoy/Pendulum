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
     * Works across Fabric, Forge, and NeoForge via reflection.
     *
     * @param modId e.g. "sodium", "iris", "jei", "baritone"
     * @return true if the mod is present in the current mod list
     */
    public static boolean isModLoaded(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String modId = cx.toString(args[0]);
        // 1) Fabric Loader
        try {
            Class<?> fl = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object inst = fl.getMethod("getInstance").invoke(null);
            return (boolean) inst.getClass().getMethod("isModLoaded", String.class).invoke(inst, modId);
        } catch (ClassNotFoundException ignored) {
        } catch (Exception e) {
            LOGGER.warn("isModLoaded: Fabric check failed — {}", e.getMessage());
        }
        // 2) Forge Loader
        try {
            Class<?> fml = Class.forName("net.minecraftforge.fml.loading.FMLLoader");
            Object modList = fml.getMethod("getLoadingModList").invoke(null);
            Object modFile = modList.getClass().getMethod("getModFileById", String.class).invoke(modList, modId);
            return modFile != null;
        } catch (ClassNotFoundException ignored) {
        } catch (Exception e) {
            LOGGER.warn("isModLoaded: Forge check failed — {}", e.getMessage());
        }
        // 3) NeoForge Loader
        try {
            Class<?> fml = Class.forName("net.neoforged.fml.loading.FMLLoader");
            Object modList = fml.getMethod("getLoadingModList").invoke(null);
            Object modFile = modList.getClass().getMethod("getModFileById", String.class).invoke(modList, modId);
            return modFile != null;
        } catch (ClassNotFoundException ignored) {
        } catch (Exception e) {
            LOGGER.warn("isModLoaded: NeoForge check failed — {}", e.getMessage());
        }
        return false;
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
            switch (key) {
                case "allowBreak":
                    return PendulumConfig.INSTANCE.allowBreak.getValue();
                case "allowPlace":
                    return PendulumConfig.INSTANCE.allowPlace.getValue();
                case "allowAttack":
                    return PendulumConfig.INSTANCE.allowAttack.getValue();
                case "allowExecuteCommand":
                    return PendulumConfig.INSTANCE.allowExecuteCommand.getValue();
                case "allowSay":
                    return PendulumConfig.INSTANCE.allowSay.getValue();
                case "mcpEnabled":
                    return PendulumConfig.INSTANCE.mcpEnabled.getValue();
                case "syncUseAttack":
                    return PendulumConfig.INSTANCE.syncUseAttack.getValue();
                case "logJsErrors":
                    return PendulumConfig.INSTANCE.logJsErrors.getValue();
                case "breakTimeout":
                    return PendulumConfig.INSTANCE.breakTimeout.getValue();
                case "rayTraceDistance":
                    return PendulumConfig.INSTANCE.rayTraceDistance.getValue();
                case "tickIntervalMs":
                    return PendulumConfig.INSTANCE.tickIntervalMs.getValue();
                case "mcpPort":
                    return PendulumConfig.INSTANCE.mcpPort.getValue();
                default:
                    return null;
            }
        });
    }

    // ==================== Internal Helpers ====================

    private static void pendulumLogImpl(String level, String msg) {
        LOGGER.info("[Pendulum JS] {}", msg);
        StringBuilder capture = ScriptEngine.getInstance().mcpLogCapture;
        if (capture != null) {
            if (capture.length() > 0) capture.append("\n");
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
