package com.iafenvoy.pendulum.script;

import com.iafenvoy.pendulum.api.GuiAPI;
import com.iafenvoy.pendulum.api.InventoryAPI;
import com.iafenvoy.pendulum.api.PlayerAPI;
import com.iafenvoy.pendulum.api.WorldAPI;
import com.iafenvoy.pendulum.config.PendulumConfig;
import com.iafenvoy.pendulum.util.PendulumInput;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import dev.latvian.mods.rhino.*;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Rhino JavaScript engine wrapper.
 * Scripts execute on a separate thread; MC API calls are submitted to the game thread via a task queue, blocking until completion.
 * Inspired by Baritone's tick-driven model and ComputerCraft's threading.
 */
public final class ScriptEngine {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path SCRIPT_DIR = Paths.get("pendulum");

    private ScriptableObject scope;
    private boolean initialized;

    private final ExecutorService scriptThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Pendulum-Script");
        t.setDaemon(true);
        return t;
    });
    private final ConcurrentLinkedDeque<Runnable> gameTasks = new ConcurrentLinkedDeque<>();
    private volatile boolean running;
    private volatile String currentSource;
    private volatile Future<?> currentFuture;
    private volatile boolean mcpConnected;

    private ScriptEngine() {
    }

    private static final class Holder {
        static final ScriptEngine INSTANCE = new ScriptEngine();
    }

    public static ScriptEngine getInstance() {
        return Holder.INSTANCE;
    }

    public void setMcpConnected(boolean connected) {
        this.mcpConnected = connected;
    }

    public void initialize() {
        if (this.initialized) return;
        try {
            //? if >=1.21 {
            /*dev.latvian.mods.rhino.ContextFactory factory = new dev.latvian.mods.rhino.ContextFactory();
            Context cx = factory.enter();
            *///?} else {
            Context cx = Context.enter();
            //?}
            this.scope = cx.initStandardObjects();
            ScriptableObject mcObj = (ScriptableObject) cx.newObject(this.scope);
            mcObj.defineFunctionProperties(
                    cx,
                    MinecraftAPI.FUNCTION_NAMES.toArray(new String[0]),
                    MinecraftAPI.class, ScriptableObject.DONTENUM);
            ScriptableObject.putProperty(this.scope, "minecraft", mcObj, cx);
            ScriptableObject.putProperty(this.scope, "game", mcObj, cx);
            ScriptableObject.putProperty(this.scope, "mc", mcObj, cx);

            // mc.player sub-object — movement, rotation, interaction, player state
            ScriptableObject playerObj = (ScriptableObject) cx.newObject(this.scope);
            playerObj.defineFunctionProperties(
                    cx,
                    PlayerAPI.FUNCTION_NAMES.toArray(new String[0]),
                    MinecraftAPI.class, ScriptableObject.DONTENUM);
            ScriptableObject.putProperty(mcObj, "player", playerObj, cx);

            // mc.world sub-object — block/entity/environment queries
            ScriptableObject worldObj = (ScriptableObject) cx.newObject(this.scope);
            worldObj.defineFunctionProperties(
                    cx,
                    WorldAPI.FUNCTION_NAMES.toArray(new String[0]),
                    MinecraftAPI.class, ScriptableObject.DONTENUM);
            ScriptableObject.putProperty(mcObj, "world", worldObj, cx);

            // mc.inv sub-object — inventory & container
            ScriptableObject invObj = (ScriptableObject) cx.newObject(this.scope);
            invObj.defineFunctionProperties(
                    cx,
                    InventoryAPI.FUNCTION_NAMES.toArray(new String[0]),
                    MinecraftAPI.class, ScriptableObject.DONTENUM);
            ScriptableObject.putProperty(mcObj, "inv", invObj, cx);

            // mc.gui sub-object — screen interaction & container operations
            ScriptableObject guiObj = (ScriptableObject) cx.newObject(this.scope);
            guiObj.defineFunctionProperties(
                    cx,
                    GuiAPI.FUNCTION_NAMES.toArray(new String[0]),
                    GuiAPI.class, ScriptableObject.DONTENUM);
            ScriptableObject.putProperty(mcObj, "gui", guiObj, cx);

            // baritone object (optional; always registered, checked on call)
            ScriptableObject brObj = (ScriptableObject) cx.newObject(this.scope);
            brObj.defineFunctionProperties(
                    cx,
                    BaritoneAPI.FUNCTION_NAMES.toArray(new String[0]),
                    BaritoneAPI.class, ScriptableObject.DONTENUM);
            // goto is a Java reserved word; bind manually
            //? if >=1.21 {
            /*java.lang.reflect.Method gotoMethod = BaritoneAPI.class.getMethod("goto_", Context.class, Scriptable.class, Object[].class, Function.class);
            dev.latvian.mods.rhino.CachedClassStorage storage = cx.getCachedClassStorage(true);
            dev.latvian.mods.rhino.CachedClassInfo info = storage.get(BaritoneAPI.class);
            brObj.put(cx, "goto", brObj, new FunctionObject("goto", new dev.latvian.mods.rhino.CachedExecutableInfo(info, gotoMethod), brObj, cx));
            *///?} else {
            brObj.put(cx, "goto", brObj,
                    new FunctionObject("goto",
                            BaritoneAPI.class.getMethod("goto_", Context.class, Scriptable.class, Object[].class, Function.class),
                            brObj, cx));
            //?}
            ScriptableObject.putProperty(this.scope, "baritone", brObj, cx);
            ScriptableObject.putProperty(this.scope, "br", brObj, cx);

            ScriptableObject consoleObj = (ScriptableObject) cx.newObject(this.scope);
            consoleObj.defineFunctionProperties(cx, new String[]{"log"}, MinecraftAPI.class, ScriptableObject.DONTENUM);
            ScriptableObject.putProperty(this.scope, "console", consoleObj, cx);
            this.initialized = true;
            LOGGER.info("Pendulum ScriptEngine initialized.");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize", e);
        }
    }

    // ==================== Game Thread Task Submission ====================

    public static <T> T submitToGameThread(Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        getInstance().gameTasks.add(() -> {
            try {
                future.complete(task.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("aborted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    public static void submitToGameThread(Runnable task) {
        submitToGameThread(() -> {
            task.run();
            return null;
        });
    }

    /**
     * fire-and-forget: enqueue without blocking; suitable for simple state sets like movement
     */
    public static void runOnGameThread(Runnable task) {
        getInstance().gameTasks.add(task);
    }

    /**
     * Called from JS thread: wait for block breaking to complete
     */
    static boolean waitForBreak() {
        int timeoutTicks = PendulumConfig.INSTANCE.breakTimeout.getValue();
        long deadline = System.currentTimeMillis() + ((long) timeoutTicks * PendulumConfig.INSTANCE.tickIntervalMs.getValue());
        while (System.currentTimeMillis() < deadline) {
            boolean[] done = {false};
            submitToGameThread(() -> {
                Minecraft mc = Minecraft.getInstance();
                PlayerSimulator sim = PlayerSimulator.getInstance();
                BlockPos pos = sim.getBreakingPos();
                if (mc.level != null && pos != null && mc.level.getBlockState(pos).isAir()) {
                    done[0] = true;
                }
            });
            if (done[0]) return true;
            singleTickSleep();
        }
        return false;
    }

    /**
     * Make JS thread wait for one game tick
     */
    private static void singleTickSleep() {
        try {
            Thread.sleep((int) PendulumConfig.INSTANCE.tickIntervalMs.getValue());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Called from JS thread: wait for N game ticks
     */
    public static void waitTicks(int ticks) {
        for (int i = 0; i < ticks; i++) singleTickSleep();
    }

    // ==================== Game Thread Tick ====================

    public void onClientTick() {
        // Swap player.input when PlayerSimulator is active, restore when idle.
        // Replaces the LocalPlayer Mixin — does the same thing from the tick event.
        PlayerSimulator sim = PlayerSimulator.getInstance();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            if (sim.isActive()) {
                if (!(mc.player.input instanceof PendulumInput)) {
                    sim.setOriginalInput(mc.player.input);
                    mc.player.input = new PendulumInput();
                }
            } else {
                if (mc.player.input instanceof PendulumInput) {
                    mc.player.input = (Input) sim.getOriginalInput();
                    sim.setOriginalInput(null);
                }
            }
        }

        for (int i = 0; i < 50; i++) {
            Runnable task = this.gameTasks.poll();
            if (task == null) break;
            task.run();
        }
        this.applyBlockBreaking();
        this.applyItemUse();
        this.updateWindowTitle();
    }

    /**
     * Update the game window title to reflect Pendulum state.
     * When a script is running: "Minecraft 1.20.1 | 脚本运行中"
     * When MCP is connected: "Minecraft 1.20.1 | 已与智能体共享"
     * Like VS Code's title bar when remote connections are active.
     */
    private void updateWindowTitle() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getWindow() == null) return;

            // Read current title via reflection (Window has no public getter)
            String baseTitle = "Minecraft";
            try {
                java.lang.reflect.Field titleField = mc.getWindow().getClass().getDeclaredField("title");
                titleField.setAccessible(true);
                String currentTitle = (String) titleField.get(mc.getWindow());
                if (currentTitle != null && !currentTitle.isEmpty()) baseTitle = currentTitle;
            } catch (Exception ignored) {}

            String scriptSuffix = net.minecraft.client.resources.language.I18n.get("pendulum.title.script_running");
            String mcpSuffix = net.minecraft.client.resources.language.I18n.get("pendulum.title.mcp_shared");

            // Strip existing Pendulum suffix
            if (baseTitle.contains(" | " + scriptSuffix)) {
                baseTitle = baseTitle.replace(" | " + scriptSuffix, "");
            }
            if (baseTitle.contains(" | " + mcpSuffix)) {
                baseTitle = baseTitle.replace(" | " + mcpSuffix, "");
            }

            // Append new suffix based on priority (MCP > script running)
            if (this.mcpConnected) {
                mc.getWindow().setTitle(baseTitle + " | " + mcpSuffix);
            } else if (this.running) {
                mc.getWindow().setTitle(baseTitle + " | " + scriptSuffix);
            } else {
                mc.getWindow().setTitle(baseTitle);
            }
        } catch (Exception e) {
            // Title update is cosmetic — never crash the tick
        }
    }

    private void applyBlockBreaking() {
        PlayerSimulator sim = PlayerSimulator.getInstance();
        if (!sim.isBreaking()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || mc.level == null) return;
        BlockPos pos = sim.getBreakingPos();
        if (pos == null) return;
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) {
            mc.gameMode.stopDestroyBlock();
            sim.stopBreaking();
        } else {
            mc.gameMode.continueDestroyBlock(pos, sim.getBreakingDir());
        }
    }

    /**
     * Each tick: if PlayerSimulator is marked for continuous item use, call gameMode.useItem();
     * if just stopped using, call gameMode.releaseUsingItem().
     * Supports eating, bow-drawing, shield-blocking, and other hold-right-click actions.
     */
    private void applyItemUse() {
        PlayerSimulator sim = PlayerSimulator.getInstance();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;

        if (sim.consumeUseItemStart()) {
            mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
        }
        if (sim.consumeUseItemStop()) {
            mc.gameMode.releaseUsingItem(mc.player);
        }
    }

    // ==================== Execution Entry ====================

    public void exec(String code) {
        if (!this.initialized) this.initialize();
        if (this.running) {
            this.notifyScriptEnd("pendulum.command.already_running");
            return;
        }
        this.running = true;
        this.currentSource = "<command>";
        this.startScript(code, "<cmd>");
    }

    public void execFile(String relativePath) {
        if (!this.initialized) this.initialize();
        if (this.running) {
            this.notifyScriptEnd("pendulum.command.already_running");
            return;
        }
        Path filePath = SCRIPT_DIR.resolve(relativePath);
        if (!Files.exists(filePath)) {
            this.notifyScriptEnd("pendulum.command.file_not_found");
            return;
        }
        try {
            String code = Files.readString(filePath);
            this.running = true;
            this.currentSource = "file: " + relativePath;
            this.startScript(code, relativePath);
        } catch (Exception e) {
            LOGGER.error("Failed to read script file", e);
            this.notifyScriptEnd("pendulum.command.error_reading_file");
        }
    }

    private void startScript(String code, String sourceName) {
        this.currentFuture = this.scriptThread.submit(() -> {
            //? if >=1.21 {
            /*Context cx = new dev.latvian.mods.rhino.ContextFactory().enter();
            *///?} else {
            Context cx = Context.enter();
            //?}
            try {
                cx.evaluateString(this.scope, code, sourceName, 1, null);
                this.running = false;
                this.currentSource = null;
                this.notifyScriptEnd("pendulum.command.done");
            } catch (RhinoException e) {
                if (PendulumConfig.INSTANCE.logJsErrors.getValue()) {
                    LOGGER.error("JS Error: {}", e.getMessage());
                }
                this.running = false;
                this.currentSource = null;
                this.notifyScriptEnd("pendulum.command.error");
            } catch (Throwable t) {
                LOGGER.error("Unexpected error in script", t);
                this.running = false;
                this.currentSource = null;
                this.notifyScriptEnd("pendulum.command.error");
            }
        });
    }

    public void abort() {
        if (!this.running) return;
        this.running = false;
        this.currentSource = null;
        if (this.currentFuture != null) {
            this.currentFuture.cancel(true);
            this.currentFuture = null;
        }
        this.gameTasks.clear();
        PlayerSimulator.getInstance().stopAll();
        LOGGER.info("Script aborted.");
    }

    public boolean isRunning() {
        return this.running;
    }

    /**
     * MCP-specific: execute code and return JS expression value (instead of "Done.").
     */
    public void execWithCallback(String code, CompletableFuture<String> resultFuture) {
        if (!this.initialized) this.initialize();
        if (this.running) {
            resultFuture.complete("[Pendulum] Already running a script. Use /pendulum abort first.");
            return;
        }
        this.running = true;
        this.currentSource = "<mcp>";
        this.currentFuture = this.scriptThread.submit(() -> {
            //? if >=1.21 {
            /*Context cx = new dev.latvian.mods.rhino.ContextFactory().enter();
            *///?} else {
            Context cx = Context.enter();
            //?}
            try {
                Object result = cx.evaluateString(this.scope, code, "<mcp>", 1, null);
                String output = cx.toString(result);
                this.running = false;
                this.currentSource = null;
                resultFuture.complete(output);
            } catch (RhinoException e) {
                if (PendulumConfig.INSTANCE.logJsErrors.getValue()) {
                    LOGGER.error("JS Error: {}", e.getMessage());
                }
                this.running = false;
                this.currentSource = null;
                resultFuture.complete("Error: " + e.getMessage());
            } catch (Throwable t) {
                LOGGER.error("Unexpected error in script", t);
                this.running = false;
                this.currentSource = null;
                resultFuture.complete("Error: " + t.getMessage());
            }
        });
    }

    public String getStatus() {
        if (!this.running) return "pendulum.status.idle";
        return "pendulum.status.running";
    }

    public Path getScriptDir() {
        return SCRIPT_DIR.toAbsolutePath();
    }

    // ==================== Callbacks ====================

    private ScriptEndListener endListener;

    public interface ScriptEndListener {
        void onEnd(String message);
    }

    public void setScriptEndListener(ScriptEndListener listener) {
        this.endListener = listener;
    }

    private void notifyScriptEnd(String msg) {
        if (this.endListener != null) this.endListener.onEnd(msg);
    }
}

