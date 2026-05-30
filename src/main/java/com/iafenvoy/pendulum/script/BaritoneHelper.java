package com.iafenvoy.pendulum.script;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * 检测 Baritone 是否已加载，并提供线程安全的反射调用。
 * Baritone 为可选前置，未加载时所有方法安全返回 false/空值。
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
     * 获取主 Baritone 实例（在游戏线程调用）
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
     * 获取 Baritone Settings
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
     * 获取进程（如 mineProcess / followProcess / buildProcess 等）
     */
    public static Object getProcess(Object baritone, String processMethod) {
        try {
            return baritone.getClass().getMethod(processMethod).invoke(baritone);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 执行 baritone 命令
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
     * 取消所有 baritone 行为
     */
    public static void cancelAll(Object baritone) {
        try {
            Object pathingBehavior = baritone.getClass().getMethod("getPathingBehavior").invoke(baritone);
            pathingBehavior.getClass().getMethod("cancelEverything").invoke(pathingBehavior);
        } catch (Exception ignored) {
        }
    }

    /**
     * 检查 baritone 是否处于 pathing 状态
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
     * 设置 Baritone Settings 中的某个属性值
     */
    @SuppressWarnings("unchecked")
    public static boolean setSetting(Object settings, String key, Object value) {
        try {
            // Settings 用反射获取 Setting 字段
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
     * 获取设置值
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
