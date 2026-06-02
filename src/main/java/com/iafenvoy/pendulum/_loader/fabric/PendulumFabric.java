package com.iafenvoy.pendulum._loader.fabric;

//? if fabric {

import com.iafenvoy.pendulum.config.PendulumConfig;
import com.iafenvoy.pendulum.mcp.McpServer;
import com.iafenvoy.pendulum.script.MinecraftAPI;
import com.iafenvoy.pendulum.script.ScriptEngine;
import com.iafenvoy.jupiter.ConfigManager;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

public final class PendulumFabric implements ClientModInitializer {
    public static final String MOD_ID = "pendulum";
    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitializeClient() {
        LOGGER.info("Pendulum client initializing...");

        // Register config
        ConfigManager.getInstance().registerConfigHandler(PendulumConfig.INSTANCE);

        ScriptEngine.getInstance().initialize();

        // Script end callback — msg can be a translation key or raw text
        ScriptEngine.getInstance().setScriptEndListener(msg -> {
            if (Minecraft.getInstance().player != null) {
                Component prefix = Component.literal("§e[Pendulum] §r");
                Component body;
                if (msg.startsWith("pendulum.")) {
                    body = Component.translatable(msg);
                } else {
                    body = Component.literal(msg);
                }
                Minecraft.getInstance().player.displayClientMessage(prefix.copy().append(body), false);
            }
        });

        // Register /pendulum command
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var cmd = ClientCommandManager.literal("pendulum")
                    .executes(ctx -> {
                        String help = MinecraftAPI.helpString();
                        if (Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.displayClientMessage(
                                    Component.literal(help), false);
                        }
                        return 1;
                    })
                    .then(ClientCommandManager.literal("help")
                            .executes(ctx -> {
                                String help = MinecraftAPI.helpString();
                                if (Minecraft.getInstance().player != null) {
                                    Minecraft.getInstance().player.displayClientMessage(
                                            Component.literal(help), false);
                                }
                                return 1;
                            }))
                    .then(ClientCommandManager.literal("execute")
                            .then(ClientCommandManager.argument("code",
                                            com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        String code = com.mojang.brigadier.arguments.StringArgumentType
                                                .getString(ctx, "code");
                                        ScriptEngine.getInstance().exec(code);
                                        return 1;
                                    })))
                    .then(ClientCommandManager.literal("file")
                            .then(ClientCommandManager.argument("path",
                                            com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        String path = com.mojang.brigadier.arguments.StringArgumentType
                                                .getString(ctx, "path");
                                        ScriptEngine.getInstance().execFile(path);
                                        return 1;
                                    })))
                    .then(ClientCommandManager.literal("abort")
                            .executes(ctx -> {
                                ScriptEngine.getInstance().abort();
                                if (Minecraft.getInstance().player != null) {
                                    Minecraft.getInstance().player.displayClientMessage(
                                            Component.translatable("pendulum.command.aborted").withStyle(net.minecraft.ChatFormatting.RED), false);
                                }
                                return 1;
                            }))
                    .then(ClientCommandManager.literal("status")
                            .executes(ctx -> {
                                String key = ScriptEngine.getInstance().getStatus();
                                if (Minecraft.getInstance().player != null) {
                                    Minecraft.getInstance().player.displayClientMessage(
                                            Component.translatable(key), false);
                                }
                                return 1;
                            }))
                    .then(ClientCommandManager.literal("dir")
                            .executes(ctx -> {
                                String dir = ScriptEngine.getInstance().getScriptDir().toString();
                                if (Minecraft.getInstance().player != null) {
                                    Minecraft.getInstance().player.displayClientMessage(
                                            Component.translatable("pendulum.status.script_dir", dir), false);
                                }
                                return 1;
                            }))
                    .then(ClientCommandManager.literal("mcp")
                            .then(ClientCommandManager.literal("start")
                                    .executes(ctx -> {
                                        int port = PendulumConfig.INSTANCE.mcpPort.getValue();
                                        McpServer mcp = McpServer.getInstance();
                                        if (mcp.isRunning()) {
                                            if (Minecraft.getInstance().player != null)
                                                Minecraft.getInstance().player.displayClientMessage(
                                                        Component.translatable("pendulum.mcp.already_running", port), false);
                                            return 1;
                                        }
                                        mcp.start(port).thenAccept(ok -> {
                                            if (Minecraft.getInstance().player != null)
                                                Minecraft.getInstance().player.displayClientMessage(
                                                        ok ? Component.translatable("pendulum.mcp.started", port)
                                                                : Component.translatable("pendulum.mcp.start_failed", "unknown"),
                                                        false);
                                        });
                                        return 1;
                                    }))
                            .then(ClientCommandManager.literal("stop")
                                    .executes(ctx -> {
                                        McpServer.getInstance().stop();
                                        if (Minecraft.getInstance().player != null)
                                            Minecraft.getInstance().player.displayClientMessage(
                                                    Component.translatable("pendulum.mcp.stopped"), false);
                                        return 1;
                                    }))
                            .then(ClientCommandManager.literal("status")
                                    .executes(ctx -> {
                                        McpServer mcp = McpServer.getInstance();
                                        Component msg;
                                        if (mcp.isRunning()) {
                                            msg = Component.translatable("pendulum.mcp.status_running", mcp.getPort());
                                        } else {
                                            msg = Component.translatable("pendulum.mcp.not_running");
                                        }
                                        if (Minecraft.getInstance().player != null)
                                            Minecraft.getInstance().player.displayClientMessage(msg, false);
                                        return 1;
                                    })));

            dispatcher.register(cmd);
        });

        // Each tick: script task processing + continuous break drive
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            ScriptEngine.getInstance().onClientTick();
            McpServer.onClientTick();
        });

        // Auto-start MCP if configured
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

//?}
