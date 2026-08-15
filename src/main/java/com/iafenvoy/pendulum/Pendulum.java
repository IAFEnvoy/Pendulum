package com.iafenvoy.pendulum;

import com.iafenvoy.jupiter.ConfigManager;
import com.iafenvoy.pendulum.config.PendulumConfig;
import com.iafenvoy.pendulum.mcp.McpServer;
import com.iafenvoy.pendulum.script.ScriptEngine;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

public final class Pendulum {
    public static final String MOD_ID = "pendulum";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static boolean initialized;

    private Pendulum() {
    }

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        LOGGER.info("Pendulum client initializing...");
        ConfigManager.getInstance().registerConfigHandler(PendulumConfig.INSTANCE);
        ScriptEngine.getInstance().initialize();

        ScriptEngine.getInstance().setScriptEndListener(msg -> {
            if (Minecraft.getInstance().player != null) {
                Component prefix = Component.literal("§e[Pendulum] §r");
                Component body = msg.startsWith("pendulum.")
                        ? Component.translatable(msg)
                        : Component.literal(msg);
                Minecraft.getInstance().player.displayClientMessage(prefix.copy().append(body), false);
            }
        });

        if (PendulumConfig.INSTANCE.mcpEnabled.getValue()) {
            int port = PendulumConfig.INSTANCE.mcpPort.getValue();
            McpServer.getInstance().start(port).thenAccept(ok -> {
                if (ok) LOGGER.info("MCP server auto-started on port {}", port);
                else LOGGER.warn("MCP server auto-start failed");
            });
        }

        LOGGER.info("Pendulum client initialized.");
    }
}
