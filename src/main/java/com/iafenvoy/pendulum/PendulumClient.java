package com.iafenvoy.pendulum;

import com.iafenvoy.pendulum.script.MinecraftAPI;
import com.iafenvoy.pendulum.script.ScriptEngine;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

public final class PendulumClient implements ClientModInitializer {
    public static final String MOD_ID = "pendulum";
    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitializeClient() {
        LOGGER.info("Pendulum client initializing...");

        ScriptEngine.getInstance().initialize();

        // 脚本结束回调
        ScriptEngine.getInstance().setScriptEndListener(msg -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§e[Pendulum] §r" + msg), false);
            }
        });

        // 注册 /pendulum 指令
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
                                            Component.literal("§cScript aborted."), false);
                                }
                                return 1;
                            }))
                    .then(ClientCommandManager.literal("status")
                            .executes(ctx -> {
                                String status = ScriptEngine.getInstance().getStatus();
                                if (Minecraft.getInstance().player != null) {
                                    Minecraft.getInstance().player.displayClientMessage(
                                            Component.literal(status), false);
                                }
                                return 1;
                            }))
                    .then(ClientCommandManager.literal("dir")
                            .executes(ctx -> {
                                String dir = ScriptEngine.getInstance().getScriptDir().toString();
                                if (Minecraft.getInstance().player != null) {
                                    Minecraft.getInstance().player.displayClientMessage(
                                            Component.literal("§eScript dir: §r" + dir), false);
                                }
                                return 1;
                            }));

            dispatcher.register(cmd);
        });

        // 每 tick：脚本任务处理 + 持续破坏驱动
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            ScriptEngine.getInstance().onClientTick();
        });

        LOGGER.info("Pendulum client initialized.");
    }
}
