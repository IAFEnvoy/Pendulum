package com.iafenvoy.pendulum._loader.forge;

//? if forge {

/*import com.iafenvoy.pendulum.config.PendulumConfig;
import com.iafenvoy.pendulum.mcp.McpServer;
import com.iafenvoy.pendulum.script.MinecraftAPI;
import com.iafenvoy.pendulum.script.ScriptEngine;
import com.iafenvoy.jupiter.ConfigManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

@Mod("pendulum")
public final class PendulumForge {
    public static final String MOD_ID = "pendulum";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PendulumForge() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        init();
    }

    private void init() {
        LOGGER.info("Pendulum client initializing...");

        // Register config
        ConfigManager.getInstance().registerConfigHandler(PendulumConfig.INSTANCE);

        ScriptEngine.getInstance().initialize();

        // Script end callback
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

    @SubscribeEvent
    public void onRegisterCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        var cmd = Commands.literal("pendulum")
                .executes(ctx -> {
                    String help = MinecraftAPI.helpString();
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.displayClientMessage(
                                Component.literal(help), false);
                    }
                    return 1;
                })
                .then(Commands.literal("help")
                        .executes(ctx -> {
                            String help = MinecraftAPI.helpString();
                            if (Minecraft.getInstance().player != null) {
                                Minecraft.getInstance().player.displayClientMessage(
                                        Component.literal(help), false);
                            }
                            return 1;
                        }))
                .then(Commands.literal("execute")
                        .then(Commands.argument("code", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String code = StringArgumentType.getString(ctx, "code");
                                    ScriptEngine.getInstance().exec(code);
                                    return 1;
                                })))
                .then(Commands.literal("file")
                        .then(Commands.argument("path", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String path = StringArgumentType.getString(ctx, "path");
                                    ScriptEngine.getInstance().execFile(path);
                                    return 1;
                                })))
                .then(Commands.literal("abort")
                        .executes(ctx -> {
                            ScriptEngine.getInstance().abort();
                            if (Minecraft.getInstance().player != null) {
                                Minecraft.getInstance().player.displayClientMessage(
                                        Component.translatable("pendulum.command.aborted").withStyle(net.minecraft.ChatFormatting.RED), false);
                            }
                            return 1;
                        }))
                .then(Commands.literal("status")
                        .executes(ctx -> {
                            String key = ScriptEngine.getInstance().getStatus();
                            if (Minecraft.getInstance().player != null) {
                                Minecraft.getInstance().player.displayClientMessage(
                                        Component.translatable(key), false);
                            }
                            return 1;
                        }))
                .then(Commands.literal("dir")
                        .executes(ctx -> {
                            String dir = ScriptEngine.getInstance().getScriptDir().toString();
                            if (Minecraft.getInstance().player != null) {
                                Minecraft.getInstance().player.displayClientMessage(
                                        Component.translatable("pendulum.status.script_dir", dir), false);
                            }
                            return 1;
                        }))
                .then(Commands.literal("mcp")
                        .then(Commands.literal("start")
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
                        .then(Commands.literal("stop")
                                .executes(ctx -> {
                                    McpServer.getInstance().stop();
                                    if (Minecraft.getInstance().player != null)
                                        Minecraft.getInstance().player.displayClientMessage(
                                                Component.translatable("pendulum.mcp.stopped"), false);
                                    return 1;
                                }))
                        .then(Commands.literal("status")
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
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            ScriptEngine.getInstance().onClientTick();
        }
    }
}

*///?}
