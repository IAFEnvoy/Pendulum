package com.iafenvoy.pendulum.script;

import com.iafenvoy.pendulum.config.PendulumConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.mozilla.javascript.*;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Rhino JavaScript 引擎封装。
 * 脚本在独立线程执行，MC API 调用通过任务队列提交到游戏线程并阻塞等待结果。
 * 参考 Baritone 的 tick-driven + ComputerCraft 的线程模型。
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

    private ScriptEngine() {
    }

    private static final class Holder {
        static final ScriptEngine INSTANCE = new ScriptEngine();
    }

    public static ScriptEngine getInstance() {
        return Holder.INSTANCE;
    }

    public void initialize() {
        if (this.initialized) return;
        try {
            Context cx = Context.enter();
            this.scope = cx.initStandardObjects();
            ScriptableObject mcObj = (ScriptableObject) cx.newObject(this.scope);
            mcObj.defineFunctionProperties(
                    MinecraftAPI.FUNCTION_NAMES.toArray(new String[0]),
                    MinecraftAPI.class, ScriptableObject.DONTENUM);
            ScriptableObject.putProperty(this.scope, "minecraft", mcObj);
            ScriptableObject.putProperty(this.scope, "game", mcObj);
            ScriptableObject.putProperty(this.scope, "mc", mcObj);

            // baritone 对象（可选前置，始终注册，调用时检查）
            ScriptableObject brObj = (ScriptableObject) cx.newObject(this.scope);
            brObj.defineFunctionProperties(
                    BaritoneAPI.FUNCTION_NAMES.toArray(new String[0]),
                    BaritoneAPI.class, ScriptableObject.DONTENUM);
            // goto 是 Java 保留字，手动绑定
            brObj.put("goto", brObj,
                    new FunctionObject("goto",
                            BaritoneAPI.class.getMethod("goto_", Context.class, Scriptable.class, Object[].class, Function.class),
                            brObj));
            ScriptableObject.putProperty(this.scope, "baritone", brObj);
            ScriptableObject.putProperty(this.scope, "br", brObj);

            ScriptableObject consoleObj = (ScriptableObject) cx.newObject(this.scope);
            consoleObj.defineFunctionProperties(new String[]{"log"}, MinecraftAPI.class, ScriptableObject.DONTENUM);
            ScriptableObject.putProperty(this.scope, "console", consoleObj);
            this.initialized = true;
            LOGGER.info("Pendulum ScriptEngine initialized.");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize", e);
        } finally {
            Context.exit();
        }
    }

    // ==================== 游戏线程任务提交 ====================

    static <T> T submitToGameThread(Supplier<T> task) {
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

    static void submitToGameThread(Runnable task) {
        submitToGameThread(() -> {
            task.run();
            return null;
        });
    }

    /**
     * fire-and-forget：只入队不阻塞，适合 move 等简单状态设置
     */
    static void runOnGameThread(Runnable task) {
        getInstance().gameTasks.add(task);
    }

    /**
     * 在 JS 线程调用：等待方块破坏完成
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
     * 让 JS 线程等待一个游戏 tick
     */
    private static void singleTickSleep() {
        try {
            Thread.sleep((int) PendulumConfig.INSTANCE.tickIntervalMs.getValue());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 在 JS 线程调用：等待 ticks 个游戏刻
     */
    static void waitTicks(int ticks) {
        for (int i = 0; i < ticks; i++) singleTickSleep();
    }

    // ==================== 游戏线程 tick ====================

    public void onClientTick() {
        for (int i = 0; i < 50; i++) {
            Runnable task = this.gameTasks.poll();
            if (task == null) break;
            task.run();
        }
        this.applyBlockBreaking();
        this.applyItemUse();
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
     * 每 tick 检查：如果 PlayerSimulator 标记为持续使用物品，调用 gameMode.useItem()；
     * 如果刚停止使用，调用 gameMode.releaseUsingItem()。
     * 支持吃东西、拉弓、举盾等需要长按右键的操作。
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

    // ==================== 执行入口 ====================

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
            Context cx = Context.enter();
            try {
                cx.setLanguageVersion(Context.VERSION_ES6);
                cx.setOptimizationLevel(-1);
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
            } finally {
                Context.exit();
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
     * MCP 专用：执行代码并返回 JS 表达式的值（而非 "Done."）。
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
            Context cx = Context.enter();
            try {
                cx.setLanguageVersion(Context.VERSION_ES6);
                cx.setOptimizationLevel(-1);
                Object result = cx.evaluateString(this.scope, code, "<mcp>", 1, null);
                String output = Context.toString(result);
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
            } finally {
                Context.exit();
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

    // ==================== 回调 ====================

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

