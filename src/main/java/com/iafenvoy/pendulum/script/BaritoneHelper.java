package com.iafenvoy.pendulum.script;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Detects whether Baritone is loaded, and provides thread-safe reflective access.
 * Baritone is optional; all methods safely return false/empty when not loaded.
 */
public final class BaritoneHelper {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final boolean LOADED;
    private static Object baritoneProvider; // IBaritoneProvider
    private static Object settings;

    static {
        boolean loaded = false;
        try {
            Class.forName("baritone.api.BaritoneAPI");
            loaded = true;
            LOGGER.info("Baritone detected — baritone API enabled.");
        } catch (ClassNotFoundException e) {
            LOGGER.info("Baritone not found — baritone API disabled.");
        }
        LOADED = loaded;
    }

    private BaritoneHelper() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    /**
     * Get the primary Baritone instance (call on game thread)
     */
    public static Object getPrimaryBaritone() {
        if (!LOADED) return null;
        try {
            if (baritoneProvider == null) {
                Class<?> api = Class.forName("baritone.api.BaritoneAPI");
                baritoneProvider = api.getMethod("getProvider").invoke(null);
            }
            return baritoneProvider.getClass().getMethod("getPrimaryBaritone").invoke(baritoneProvider);
        } catch (Exception e) {
            LOGGER.error("Failed to get Baritone instance", e);
            return null;
        }
    }

    /**
     * Get Baritone Settings
     */
    public static Object getSettings() {
        if (!LOADED) return null;
        try {
            if (settings == null) {
                Class<?> api = Class.forName("baritone.api.BaritoneAPI");
                settings = api.getMethod("getSettings").invoke(null);
            }
            return settings;
        } catch (Exception e) {
            return null;
        }
    }

    /**
 * Get process (e.g. mineProcess / followProcess / buildProcess)
     */
    public static Object getProcess(Object baritone, String processMethod) {
        try {
            return baritone.getClass().getMethod(processMethod).invoke(baritone);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Execute baritone command
     */
    public static boolean executeCommand(Object baritone, String command) {
        try {
            Object commandManager = baritone.getClass().getMethod("getCommandManager").invoke(baritone);
            return (boolean) commandManager.getClass().getMethod("execute", String.class).invoke(commandManager, command);
        } catch (Exception e) {
            LOGGER.error("Failed to execute baritone command: {}", command, e);
            return false;
        }
    }

    /**
     * Cancel all baritone actions
     */
    public static void cancelAll(Object baritone) {
        try {
            Object pathingBehavior = baritone.getClass().getMethod("getPathingBehavior").invoke(baritone);
            pathingBehavior.getClass().getMethod("cancelEverything").invoke(pathingBehavior);
        } catch (Exception ignored) {
        }
    }

    /**
     * Check if baritone is in pathing state
     */
    public static boolean isPathing(Object baritone) {
        try {
            Object pathingBehavior = baritone.getClass().getMethod("getPathingBehavior").invoke(baritone);
            return (boolean) pathingBehavior.getClass().getMethod("isPathing").invoke(pathingBehavior);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Set a Baritone Settings property value
     */
    @SuppressWarnings("unchecked")
    public static boolean setSetting(Object settings, String key, Object value) {
        try {
            // Settings - use reflection to get Setting field
            Class<?> settingsClass = settings.getClass();
            java.lang.reflect.Field field = settingsClass.getField(key);
            Object setting = field.get(settings);
            // Setting.value = newValue
            Method setValue = setting.getClass().getMethod("set", Object.class);
            setValue.invoke(setting, value);
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to set baritone setting '{}'", key, e);
            return false;
        }
    }

    /**
     * Get setting value
     */
    public static Object getSetting(Object settings, String key) {
        try {
            java.lang.reflect.Field field = settings.getClass().getField(key);
            Object setting = field.get(settings);
            return setting.getClass().getMethod("get").invoke(setting);
        } catch (Exception e) {
            return null;
        }
    }
}
