package com.iafenvoy.pendulum.command;

import com.iafenvoy.pendulum.config.PendulumConfig;
import com.iafenvoy.pendulum.mcp.McpServer;
import com.iafenvoy.pendulum.script.MinecraftAPI;
import com.iafenvoy.pendulum.script.ScriptEngine;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class PendulumCommand {
    private PendulumCommand() {
    }

    public static <S> LiteralArgumentBuilder<S> create() {
        return LiteralArgumentBuilder.<S>literal("pendulum")
                .executes(ctx -> showHelp())
                .then(LiteralArgumentBuilder.<S>literal("help")
                        .executes(ctx -> showHelp()))
                .then(LiteralArgumentBuilder.<S>literal("execute")
                        .then(RequiredArgumentBuilder.<S, String>argument("code", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ScriptEngine.getInstance().exec(StringArgumentType.getString(ctx, "code"));
                                    return 1;
                                })))
                .then(LiteralArgumentBuilder.<S>literal("file")
                        .then(RequiredArgumentBuilder.<S, String>argument("path", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ScriptEngine.getInstance().execFile(StringArgumentType.getString(ctx, "path"));
                                    return 1;
                                })))
                .then(LiteralArgumentBuilder.<S>literal("abort")
                        .executes(ctx -> {
                            ScriptEngine.getInstance().abort();
                            display(Component.translatable("pendulum.command.aborted").withStyle(ChatFormatting.RED));
                            return 1;
                        }))
                .then(LiteralArgumentBuilder.<S>literal("status")
                        .executes(ctx -> {
                            display(Component.translatable(ScriptEngine.getInstance().getStatus()));
                            return 1;
                        }))
                .then(LiteralArgumentBuilder.<S>literal("dir")
                        .executes(ctx -> {
                            display(Component.translatable("pendulum.status.script_dir", ScriptEngine.getInstance().getScriptDir()));
                            return 1;
                        }))
                .then(LiteralArgumentBuilder.<S>literal("mcp")
                        .then(LiteralArgumentBuilder.<S>literal("start")
                                .executes(ctx -> startMcp()))
                        .then(LiteralArgumentBuilder.<S>literal("stop")
                                .executes(ctx -> {
                                    McpServer.getInstance().stop();
                                    display(Component.translatable("pendulum.mcp.stopped"));
                                    return 1;
                                }))
                        .then(LiteralArgumentBuilder.<S>literal("status")
                                .executes(ctx -> {
                                    McpServer mcp = McpServer.getInstance();
                                    display(mcp.isRunning()
                                            ? Component.translatable("pendulum.mcp.status_running", mcp.getPort())
                                            : Component.translatable("pendulum.mcp.not_running"));
                                    return 1;
                                })));
    }

    private static int showHelp() {
        display(Component.literal(MinecraftAPI.helpString()));
        return 1;
    }

    private static int startMcp() {
        int port = PendulumConfig.INSTANCE.mcpPort.getValue();
        McpServer mcp = McpServer.getInstance();
        if (mcp.isRunning()) {
            display(Component.translatable("pendulum.mcp.already_running", port));
            return 1;
        }
        mcp.start(port).thenAccept(ok -> display(ok
                ? Component.translatable("pendulum.mcp.started", port)
                : Component.translatable("pendulum.mcp.start_failed", "unknown")));
        return 1;
    }

    private static void display(Component message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(message, false);
        }
    }
}
