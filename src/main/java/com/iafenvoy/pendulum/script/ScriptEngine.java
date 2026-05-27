package com.iafenvoy.pendulum.script;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
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

    private ScriptEngine() {}

    private static final class Holder {
        static final ScriptEngine INSTANCE = new ScriptEngine();
    }

    public static ScriptEngine getInstance() {
        return Holder.INSTANCE;
    }

    public void initialize() {
        if (initialized) return;
        try {
            Context cx = Context.enter();
            scope = cx.initStandardObjects();
            ScriptableObject mcObj = (ScriptableObject) cx.newObject(scope);
            mcObj.defineFunctionProperties(
                    MinecraftAPI.FUNCTION_NAMES.toArray(new String[0]),
                    MinecraftAPI.class, ScriptableObject.DONTENUM);
            ScriptableObject.putProperty(scope, "minecraft", mcObj);
            ScriptableObject.putProperty(scope, "game", mcObj);
            ScriptableObject.putProperty(scope, "mc", mcObj);

            // baritone 对象（可选前置，始终注册，调用时检查）
            ScriptableObject brObj = (ScriptableObject) cx.newObject(scope);
            brObj.defineFunctionProperties(
                    BaritoneAPI.FUNCTION_NAMES.toArray(new String[0]),
                    BaritoneAPI.class, ScriptableObject.DONTENUM);
            // goto 是 Java 保留字，手动绑定
            brObj.put("goto", brObj,
                    new FunctionObject("goto",
                            BaritoneAPI.class.getMethod("goto_", Context.class, Scriptable.class, Object[].class, Function.class),
                            brObj));
            ScriptableObject.putProperty(scope, "baritone", brObj);
            ScriptableObject.putProperty(scope, "br", brObj);

            ScriptableObject consoleObj = (ScriptableObject) cx.newObject(scope);
            consoleObj.defineFunctionProperties(new String[]{"log"}, MinecraftAPI.class, ScriptableObject.DONTENUM);
            ScriptableObject.putProperty(scope, "console", consoleObj);
            initialized = true;
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
            try { future.complete(task.get()); }
            catch (Throwable t) { future.completeExceptionally(t); }
        });
        try { return future.get(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException("aborted", e); }
        catch (ExecutionException e) { throw new RuntimeException(e.getCause()); }
    }

    static void submitToGameThread(Runnable task) {
        submitToGameThread(() -> { task.run(); return null; });
    }

    /** fire-and-forget：只入队不阻塞，适合 move 等简单状态设置 */
    static void runOnGameThread(Runnable task) {
        getInstance().gameTasks.add(task);
    }

    /** 在 JS 线程调用：等待方块破坏完成 */
    static boolean waitForBreak() {
        long deadline = System.currentTimeMillis() + 10000L;
        while (System.currentTimeMillis() < deadline) {
            boolean[] done = {false};
            submitToGameThread(() -> {
                Minecraft mc = Minecraft.getInstance();
                PlayerSimulator sim = PlayerSimulator.getInstance();
                if (mc.level != null && mc.level.getBlockState(sim.getBreakingPos()).isAir()) {
                    done[0] = true;
                }
            });
            if (done[0]) return true;
            singleTickSleep();
        }
        return false;
    }

    /** 让 JS 线程等待一个游戏 tick */
    private static void singleTickSleep() {
        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /** 在 JS 线程调用：等待 ticks 个游戏刻 */
    static void waitTicks(int ticks) {
        for (int i = 0; i < ticks; i++) singleTickSleep();
    }

    // ==================== 游戏线程 tick ====================

    public void onClientTick() {
        for (int i = 0; i < 50; i++) {
            Runnable task = gameTasks.poll();
            if (task == null) break;
            task.run();
        }
        applyBlockBreaking();
    }

    private void applyBlockBreaking() {
        PlayerSimulator sim = PlayerSimulator.getInstance();
        if (!sim.isBreaking()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || mc.level == null) return;
        BlockState state = mc.level.getBlockState(sim.getBreakingPos());
        if (state.isAir()) {
            mc.gameMode.stopDestroyBlock();
            sim.stopBreaking();
        } else {
            mc.gameMode.continueDestroyBlock(sim.getBreakingPos(), sim.getBreakingDir());
        }
    }

    // ==================== 执行入口 ====================

    public void exec(String code) {
        if (!initialized) initialize();
        if (running) { notifyScriptEnd("§cA script is already running. Use /pendulum abort first."); return; }
        running = true;
        currentSource = "<command>";
        startScript(code, "<cmd>");
    }

    public void execFile(String relativePath) {
        if (!initialized) initialize();
        if (running) { notifyScriptEnd("§cA script is already running. Use /pendulum abort first."); return; }
        Path filePath = SCRIPT_DIR.resolve(relativePath);
        if (!Files.exists(filePath)) {
            notifyScriptEnd("File not found: " + filePath.toAbsolutePath());
            return;
        }
        try {
            String code = Files.readString(filePath);
            running = true;
            currentSource = "file: " + relativePath;
            startScript(code, relativePath);
        } catch (Exception e) {
            LOGGER.error("Failed to read script file", e);
            notifyScriptEnd("Error reading file: " + e.getMessage());
        }
    }

    private void startScript(String code, String sourceName) {
        currentFuture = scriptThread.submit(() -> {
            Context cx = Context.enter();
            try {
                cx.setLanguageVersion(Context.VERSION_ES6);
                cx.setOptimizationLevel(-1);
                cx.evaluateString(scope, code, sourceName, 1, null);
                running = false;
                currentSource = null;
                notifyScriptEnd("Done.");
            } catch (RhinoException e) {
                LOGGER.error("JS Error: {}", e.getMessage());
                running = false;
                currentSource = null;
                notifyScriptEnd("Error: " + e.getMessage());
            } catch (Throwable t) {
                LOGGER.error("Unexpected error in script", t);
                running = false;
                currentSource = null;
                notifyScriptEnd("Error: " + t.getMessage());
            } finally {
                Context.exit();
            }
        });
    }

    public void abort() {
        if (!running) return;
        running = false;
        currentSource = null;
        if (currentFuture != null) { currentFuture.cancel(true); currentFuture = null; }
        gameTasks.clear();
        PlayerSimulator.getInstance().stopAll();
        LOGGER.info("Script aborted.");
    }

    public boolean isRunning() { return running; }

    public String getStatus() {
        if (!running) return "§7Idle — no script running.";
        return "§eRunning: §f" + (currentSource != null ? currentSource : "?");
    }

    public Path getScriptDir() { return SCRIPT_DIR.toAbsolutePath(); }

    // ==================== 回调 ====================

    private ScriptEndListener endListener;

    public interface ScriptEndListener { void onEnd(String message); }

    public void setScriptEndListener(ScriptEndListener listener) { this.endListener = listener; }

    private void notifyScriptEnd(String msg) {
        if (endListener != null) endListener.onEnd(msg);
    }
}

