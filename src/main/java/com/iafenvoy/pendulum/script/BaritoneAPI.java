package com.iafenvoy.pendulum.script;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.List;

/**
 * 挂载到 JS 全局 baritone 对象上的所有函数。
 * Baritone 为可选前置，未加载时调用任何函数都会提示并停止脚本。
 */
public final class BaritoneAPI {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final List<String> FUNCTION_NAMES = Arrays.asList(
            // 寻路 & 移动
            "mine", "follow", "farm", "explore",
            "getToBlock", "build", "tunnel", "come", "axis",
            "thisWay", "surface",
            // 物品 & 交互
            "pickup", "click",
            // 控制
            "stop", "pause", "resume", "isActive", "isPaused",
            // 选区
            "select", "clearSelection", "selPos1", "selPos2",
            // 设置 & 信息
            "command", "setting", "find", "blacklist",
            "waypointSave", "waypointList", "waypointDelete",
            "sethome", "home",
            "proc", "eta",
            // 帮助
            "help"
    );

    private static void requireBaritone() {
        if (!BaritoneHelper.isLoaded()) {
            throw new RuntimeException(
                    net.minecraft.client.resources.language.I18n.get("pendulum.baritone.not_installed"));
        }
    }

    private static Object getBaritone() {
        requireBaritone();
        return ScriptEngine.submitToGameThread(BaritoneHelper::getPrimaryBaritone);
    }

    // ==================== 移动/寻路 ====================

    /**
     * baritone.goto(x, y, z) — 走到指定坐标
     */
    public static void goto_(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int x = ((Number) args[0]).intValue();
        int y = ((Number) args[1]).intValue();
        int z = ((Number) args[2]).intValue();
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "goto " + x + " " + y + " " + z));
    }

    // ==================== 挖掘 ====================

    /**
     * baritone.mine(blockId, count?) — 挖掘指定方块
     */
    public static void mine(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String blockId = Context.toString(args[0]);
        int count = args.length > 1 ? ((Number) args[1]).intValue() : Integer.MAX_VALUE;
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() -> {
            Object mineProcess = BaritoneHelper.getProcess(baritone, "getMineProcess");
            if (mineProcess == null) return;
            try {
                // IMineProcess.mineByName(int quantity, String... blocks)
                mineProcess.getClass().getMethod("mineByName", int.class, String[].class)
                        .invoke(mineProcess, count, new String[]{blockId});
            } catch (Exception e) {
                LOGGER.error("Failed to start mining", e);
            }
        });
    }

    // ==================== 跟随 ====================

    /**
     * baritone.follow(entityType?) — 跟随指定类型的实体
     */
    public static void follow(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String entityType = args.length > 0 ? Context.toString(args[0]) : null;
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() -> {
            Object followProcess = BaritoneHelper.getProcess(baritone, "getFollowProcess");
            if (followProcess == null) return;
            try {
                if (entityType != null) {
                    // follow(filter) — entityType 为简化的名称过滤
                    followProcess.getClass().getMethod("follow", java.util.function.Predicate.class)
                            .invoke(followProcess, (java.util.function.Predicate<Object>) e -> {
                                try {
                                    String key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                                            .getKey((net.minecraft.world.entity.EntityType<?>)
                                                    e.getClass().getMethod("getType").invoke(e)).toString();
                                    return key.equals(entityType);
                                } catch (Exception ex) {
                                    return false;
                                }
                            });
                } else {
                    // 跟随玩家
                    followProcess.getClass().getMethod("follow", java.util.function.Predicate.class)
                            .invoke(followProcess, (java.util.function.Predicate<Object>) e -> {
                                try {
                                    return e.getClass().getMethod("isLocalPlayer").invoke(e).equals(false)
                                            && e instanceof net.minecraft.world.entity.player.Player;
                                } catch (Exception ex) {
                                    return false;
                                }
                            });
                }
            } catch (Exception e) {
                LOGGER.error("Failed to start following", e);
            }
        });
    }

    // ==================== 农场 ====================

    /**
     * baritone.farm(range?) — 农场模式
     */
    public static void farm(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int range = args.length > 0 ? ((Number) args[0]).intValue() : 100;
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() -> {
            Object farmProcess = BaritoneHelper.getProcess(baritone, "getFarmProcess");
            if (farmProcess == null) return;
            try {
                // IFarmProcess.farm(int range, BlockPos pos)
                farmProcess.getClass().getMethod("farm", int.class, BlockPos.class)
                        .invoke(farmProcess, range, Minecraft.getInstance().player.blockPosition());
            } catch (Exception e) {
                try {
                    farmProcess.getClass().getMethod("farm").invoke(farmProcess);
                } catch (Exception e2) {
                    LOGGER.error("Failed to start farming", e2);
                }
            }
        });
    }

    // ==================== 探索 ====================

    /**
     * baritone.explore() — 开始探索
     */
    public static void explore(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() -> {
            Object exploreProcess = BaritoneHelper.getProcess(baritone, "getExploreProcess");
            if (exploreProcess == null) return;
            try {
                BlockPos pos = Minecraft.getInstance().player.blockPosition();
                exploreProcess.getClass().getMethod("explore", int.class, int.class)
                        .invoke(exploreProcess, pos.getX(), pos.getZ());
            } catch (Exception e) {
                LOGGER.error("Failed to start exploring", e);
            }
        });
    }

    // ==================== 移动到方块 ====================

    /**
     * baritone.getToBlock(blockId) — 走到最近的指定方块
     */
    public static void getToBlock(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String blockId = Context.toString(args[0]);
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() -> {
            Object process = BaritoneHelper.getProcess(baritone, "getGetToBlockProcess");
            if (process == null) return;
            try {
                Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new ResourceLocation(blockId));
                process.getClass().getMethod("getToBlock", Block.class).invoke(process, block);
            } catch (Exception e) {
                LOGGER.error("Failed to start getToBlock", e);
            }
        });
    }

    // ==================== 建筑 ====================

    /**
     * baritone.build(schematicName, x, y, z) — 建造schematic
     */
    public static void build(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String name = Context.toString(args[0]);
        int x = args.length > 1 ? ((Number) args[1]).intValue() : 0;
        int y = args.length > 2 ? ((Number) args[2]).intValue() : 0;
        int z = args.length > 3 ? ((Number) args[3]).intValue() : 0;
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() -> {
            Object buildProcess = BaritoneHelper.getProcess(baritone, "getBuilderProcess");
            if (buildProcess == null) return;
            try {
                java.io.File schematicDir = new java.io.File(
                        Minecraft.getInstance().gameDirectory, "schematics");
                java.io.File file = new java.io.File(schematicDir, name + ".schematic");
                if (!file.exists()) file = new java.io.File(schematicDir, name);
                buildProcess.getClass().getMethod("build", String.class, java.io.File.class, net.minecraft.core.Vec3i.class)
                        .invoke(buildProcess, name, file, new net.minecraft.core.Vec3i(x, y, z));
            } catch (Exception e) {
                try {
                    buildProcess.getClass().getMethod("build", String.class,
                                    Class.forName("baritone.api.schematic.ISchematic"), net.minecraft.core.Vec3i.class)
                            .invoke(buildProcess, name, null, new net.minecraft.core.Vec3i(x, y, z));
                } catch (Exception ignored) {
                }
                LOGGER.error("Failed to start building", e);
            }
        });
    }

    // ==================== 隧道 / 寻路辅助 ====================

    /**
     * baritone.tunnel() — 沿当前方向挖掘隧道
     */
    public static void tunnel(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "tunnel"));
    }

    /**
     * baritone.come() — 走到玩家摄像机位置
     */
    public static void come(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "come"));
    }

    /**
     * baritone.axis() — 前往最近的坐标轴 (X=0 或 Z=0)
     */
    public static void axis(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "axis"));
    }

    /**
     * baritone.thisWay() — 沿当前朝向持续前进
     */
    public static void thisWay(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "thisway"));
    }

    /**
     * baritone.surface() — 回到地表
     */
    public static void surface(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "surface"));
    }

    // ==================== 物品 / 交互 ====================

    /**
     * baritone.pickup() — 捡起附近掉落物
     */
    public static void pickup(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "pickup"));
    }

    /**
     * baritone.click() — 模拟点击（须先看向目标方块/实体）
     */
    public static void click(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "click"));
    }

    // ==================== 控制 ====================

    /**
     * baritone.stop() — 取消所有 baritone 行为
     */
    public static void stop(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() -> BaritoneHelper.cancelAll(baritone));
    }

    /**
     * baritone.pause() — 暂停 baritone 任务
     */
    public static void pause(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "pause"));
    }

    /**
     * baritone.resume() — 恢复暂停的任务
     */
    public static void resume(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "resume"));
    }

    /**
     * baritone.isActive() → boolean — 是否正在工作
     */
    public static boolean isActive(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return false;
        return ScriptEngine.submitToGameThread(() -> BaritoneHelper.isPathing(baritone));
    }

    /**
     * baritone.isPaused() → boolean — 是否处于暂停状态
     */
    public static boolean isPaused(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        requireBaritone();
        return ScriptEngine.submitToGameThread(() -> {
            try {
                Object baritone = BaritoneHelper.getPrimaryBaritone();
                if (baritone == null) return false;
                Object pathingBehavior = baritone.getClass().getMethod("getPathingBehavior").invoke(baritone);
                return (boolean) pathingBehavior.getClass().getMethod("isPaused").invoke(pathingBehavior);
            } catch (Exception e) {
                return false;
            }
        });
    }

    /**
     * baritone.command(cmd) — 执行一条 baritone 命令（如 "mine diamond_ore 64"）
     */
    public static void command(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String cmd = Context.toString(args[0]);
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() -> BaritoneHelper.executeCommand(baritone, cmd));
    }

    /**
     * baritone.setting(key, value) — 修改 baritone 设置
     */
    public static void setting(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String key = Context.toString(args[0]);
        Object value = args[1];
        Object baritone = getBaritone();
        if (baritone == null) return;
        Object settings = BaritoneHelper.getSettings();
        if (settings == null) return;
        ScriptEngine.submitToGameThread(() -> {
            Object converted;
            if (value instanceof Number) converted = ((Number) value).doubleValue();
            else if (value instanceof Boolean) converted = value;
            else converted = Context.toString(value);
            BaritoneHelper.setSetting(settings, key, converted);
        });
    }

    // ==================== 选区 ====================

    /**
     * baritone.select(x1, y1, z1, x2, y2, z2) — 设置选区
     */
    public static void select(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int x1 = ((Number) args[0]).intValue(), y1 = ((Number) args[1]).intValue(), z1 = ((Number) args[2]).intValue();
        int x2 = ((Number) args[3]).intValue(), y2 = ((Number) args[4]).intValue(), z2 = ((Number) args[5]).intValue();
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() -> {
            try {
                Object selectionManager = baritone.getClass().getMethod("getSelectionManager").invoke(baritone);
                selectionManager.getClass().getMethod("addSelection", BlockPos.class, BlockPos.class)
                        .invoke(selectionManager, new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2));
            } catch (Exception e) {
                LOGGER.error("Failed to set selection", e);
            }
        });
    }

    /**
     * baritone.clearSelection() — 清除选区
     */
    public static void clearSelection(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() -> {
            try {
                Object selectionManager = baritone.getClass().getMethod("getSelectionManager").invoke(baritone);
                selectionManager.getClass().getMethod("removeAllSelections").invoke(selectionManager);
            } catch (Exception e) {
                LOGGER.error("Failed to clear selection", e);
            }
        });
    }

    /**
     * baritone.selPos1(x?, y?, z?) — 设置选区 pos1（不传参 = 当前玩家位置）
     */
    public static void selPos1(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() -> {
            if (args.length >= 3) {
                int x = ((Number) args[0]).intValue(), y = ((Number) args[1]).intValue(), z = ((Number) args[2]).intValue();
                BaritoneHelper.executeCommand(baritone, "sel pos1 " + x + " " + y + " " + z);
            } else {
                BaritoneHelper.executeCommand(baritone, "sel pos1");
            }
        });
    }

    /**
     * baritone.selPos2(x?, y?, z?) — 设置选区 pos2
     */
    public static void selPos2(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() -> {
            if (args.length >= 3) {
                int x = ((Number) args[0]).intValue(), y = ((Number) args[1]).intValue(), z = ((Number) args[2]).intValue();
                BaritoneHelper.executeCommand(baritone, "sel pos2 " + x + " " + y + " " + z);
            } else {
                BaritoneHelper.executeCommand(baritone, "sel pos2");
            }
        });
    }

    // ==================== 信息 & 工具 ====================

    /**
     * baritone.find(blockId) — 搜索世界中指定方块的位置（显示在聊天栏）
     */
    public static void find(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String blockId = Context.toString(args[0]);
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "find " + blockId));
    }

    /**
     * baritone.blacklist() — 将准星对准的方块加入寻路黑名单
     */
    public static void blacklist(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "blacklist"));
    }

    // ==================== 路径点 / 回家 ====================

    /**
     * baritone.waypointSave(name) — 保存当前位置为路径点
     */
    public static void waypointSave(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String name = Context.toString(args[0]);
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "wp save " + name));
    }

    /**
     * baritone.waypointList() — 列出所有路径点
     */
    public static void waypointList(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "wp list"));
    }

    /**
     * baritone.waypointDelete(name) — 删除指定路径点
     */
    public static void waypointDelete(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String name = Context.toString(args[0]);
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "wp delete " + name));
    }

    /**
     * baritone.sethome(name) — 设置当前玩家位置为家
     */
    public static void sethome(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "sethome"));
    }

    /**
     * baritone.home(name?) — 回家
     */
    public static void home(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "home"));
    }

    // ==================== 进程 / 状态 ====================

    /**
     * baritone.proc() — 显示当前进程状态
     */
    public static void proc(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "proc"));
    }

    /**
     * baritone.eta() — 显示预计到达时间
     */
    public static void eta(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "eta"));
    }

    // ==================== 帮助 ====================

    public static void help(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String msg = "§6=== Baritone API ===\n" +
                "§e寻路:§r goto(x,y,z), come(), axis(), thisWay(), surface()\n" +
                "§e挖掘:§r mine('blockId', count?), tunnel()\n" +
                "§e跟随:§r follow('entityType'?), pickup()\n" +
                "§e农场:§r farm(range?)  探索:§r explore()\n" +
                "§e移动:§r getToBlock('blockId')\n" +
                "§e建筑:§r build('schematic', x?, y?, z?)\n" +
                "§e交互:§r click()\n" +
                "§e控制:§r stop(), pause(), resume(), isActive(), isPaused()\n" +
                "§e命令:§r command('baritone命令')\n" +
                "§e设置:§r setting('key', value)\n" +
                "§e选区:§r select(x1,y1,z1,x2,y2,z2), selPos1(), selPos2(), clearSelection()\n" +
                "§e工具:§r find('blockId'), blacklist()\n" +
                "§e路径点:§r waypointSave(name), waypointList(), waypointDelete(name)\n" +
                "§e家:§r sethome(), home()\n" +
                "§e状态:§r proc(), eta()\n" +
                "§e帮助:§r help()";
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(msg), false);
        }
    }
}
