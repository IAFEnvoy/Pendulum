package com.iafenvoy.pendulum.script;

import com.iafenvoy.pendulum.config.PendulumConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 挂载到 JS 全局 minecraft 对象上的所有函数。
 * 所有方法签名必须为 (Context, Scriptable, Object[], Function)。
 */
public final class MinecraftAPI {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Minecraft MC = Minecraft.getInstance();

    /**
     * minecraft 对象上暴露的所有函数名
     */
    public static final List<String> FUNCTION_NAMES = Arrays.asList(
            // 移动
            "forward", "back", "left", "right", "stop",
            "jump", "sneak", "sprint", "stopSprint",
            "lookAt", "setYaw", "setPitch", "getYaw", "getPitch",
            "getX", "getY", "getZ",
            // 交互
            "use", "attack", "breakBlock", "breakBlockAt", "swapHands",
            "drop", "dropAll", "pickBlock",
            // 物品栏
            "selectHotbar", "getSelectedSlot", "hasItem",
            // GUI
            "closeGui", "isGuiOpen", "getGuiTitle",
            "clickSlot", "clickSlotRight",
            "craft", "craftAll",
            // 世界
            "facingBlock", "facingEntity", "getFacingBlock",
            "getBlock", "isBlock", "isBlockByTag",
            "findBlocks", "findBlocksByTag", "findBlocksInBox",
            "getNearbyEntities", "getNearbyPlayers", "rayTrace",
            // 聊天
            "say", "log",
            // 文件/控制
            "execFile", "getScriptDir", "waitTick",
            // 辅助
            "help"
    );

    // ==================== 移动 ====================

    /**
     * forward() — 一直按住前；forward(ticks) — 前进 ticks 刻后自动停止
     */
    public static void forward(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        if (args.length > 0) {
            moveFor("forward", ((Number) args[0]).intValue());
        } else {
            moveHold("forward");
        }
    }

    /**
     * back() — 一直按住后；back(ticks) — 后退 ticks 刻后自动停止
     */
    public static void back(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        if (args.length > 0) {
            moveFor("back", ((Number) args[0]).intValue());
        } else {
            moveHold("back");
        }
    }

    /**
     * left() — 一直按住左；left(ticks) — 左移 ticks 刻后自动停止
     */
    public static void left(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        if (args.length > 0) {
            moveFor("left", ((Number) args[0]).intValue());
        } else {
            moveHold("left");
        }
    }

    /**
     * right() — 一直按住右；right(ticks) — 右移 ticks 刻后自动停止
     */
    public static void right(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        if (args.length > 0) {
            moveFor("right", ((Number) args[0]).intValue());
        } else {
            moveHold("right");
        }
    }

    private static void moveHold(String dir) {
        ScriptEngine.runOnGameThread(() -> {
            PlayerSimulator sim = PlayerSimulator.getInstance();
            sim.stopAll();
            switch (dir) {
                case "forward":
                    sim.setForward(true);
                    break;
                case "back":
                    sim.setBackward(true);
                    break;
                case "left":
                    sim.setLeft(true);
                    break;
                case "right":
                    sim.setRight(true);
                    break;
            }
        });
    }

    private static void moveFor(String dir, int ticks) {
        ScriptEngine.runOnGameThread(() -> {
            PlayerSimulator sim = PlayerSimulator.getInstance();
            sim.stopAll();
            switch (dir) {
                case "forward":
                    sim.setForward(true);
                    break;
                case "back":
                    sim.setBackward(true);
                    break;
                case "left":
                    sim.setLeft(true);
                    break;
                case "right":
                    sim.setRight(true);
                    break;
            }
        });
        ScriptEngine.waitTicks(ticks);
        ScriptEngine.runOnGameThread(() -> PlayerSimulator.getInstance().stopAll());
    }

    public static void stop(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        ScriptEngine.runOnGameThread(() -> PlayerSimulator.getInstance().stopAll());
    }

    public static void jump(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        boolean hold = args.length > 0 && Context.toBoolean(args[0]);
        ScriptEngine.runOnGameThread(() -> PlayerSimulator.getInstance().setJump(true));
    }

    public static void sneak(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        boolean v = args.length == 0 || Context.toBoolean(args[0]);
        ScriptEngine.runOnGameThread(() -> PlayerSimulator.getInstance().setSneak(v));
    }

    public static void sprint(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        boolean v = args.length == 0 || Context.toBoolean(args[0]);
        ScriptEngine.runOnGameThread(() -> PlayerSimulator.getInstance().setSprinting(v));
    }

    public static void stopSprint(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        ScriptEngine.runOnGameThread(() -> PlayerSimulator.getInstance().stopSprinting());
    }

    public static void lookAt(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        double x = ((Number) args[0]).doubleValue();
        double y = ((Number) args[1]).doubleValue();
        double z = ((Number) args[2]).doubleValue();
        ScriptEngine.runOnGameThread(() -> {
            if (MC.player == null) return;
            double dx = x - MC.player.getX();
            double dy = y - MC.player.getEyeY();
            double dz = z - MC.player.getZ();
            double hDist = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
            float pitch = (float) (-Math.toDegrees(Math.atan2(dy, hDist)));
            MC.player.setYRot(yaw);
            MC.player.setXRot(pitch);
            PlayerSimulator sim = PlayerSimulator.getInstance();
            sim.setLookYaw(yaw);
            sim.setLookPitch(pitch);
        });
    }

    public static void setYaw(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        float yaw = ((Number) args[0]).floatValue();
        ScriptEngine.runOnGameThread(() -> {
            if (MC.player != null) MC.player.setYRot(yaw);
            PlayerSimulator.getInstance().setLookYaw(yaw);
        });
    }

    public static void setPitch(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        float pitch = ((Number) args[0]).floatValue();
        ScriptEngine.runOnGameThread(() -> {
            if (MC.player != null) MC.player.setXRot(pitch);
            PlayerSimulator.getInstance().setLookPitch(pitch);
        });
    }

    public static double getYaw(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        if (MC.player == null) return 0;
        return ScriptEngine.submitToGameThread(() -> (double) MC.player.getYRot());
    }

    public static double getPitch(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        if (MC.player == null) return 0;
        return ScriptEngine.submitToGameThread(() -> (double) MC.player.getXRot());
    }

    public static double getX(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.submitToGameThread(() -> MC.player != null ? MC.player.getX() : 0);
    }

    public static double getY(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.submitToGameThread(() -> MC.player != null ? MC.player.getY() : 0);
    }

    public static double getZ(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.submitToGameThread(() -> MC.player != null ? MC.player.getZ() : 0);
    }

    // ==================== 交互 ====================

    /**
     * 使用物品/与方块交互
     */
    public static void use(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        if (MC.player == null || MC.gameMode == null) return;
        ScriptEngine.submitToGameThread(() -> MC.gameMode.useItem(MC.player, InteractionHand.MAIN_HAND));
        if (PendulumConfig.INSTANCE.syncUseAttack()) {
            ScriptEngine.waitTicks(1);
        }
    }

    /**
     * 攻击实体
     */
    public static void attack(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        if (MC.player == null || MC.gameMode == null) return;
        ScriptEngine.submitToGameThread(() -> MC.gameMode.attack(MC.player, MC.crosshairPickEntity));
        if (PendulumConfig.INSTANCE.syncUseAttack()) {
            ScriptEngine.waitTicks(2);
        }
    }

    /**
     * 破坏视线对准的方块，持续按住直到破坏（同步等待）。注意射线可能打到障碍物，精准破坏推荐 breakBlockAt
     */
    public static boolean breakBlock(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        BlockPos[] posHolder = new BlockPos[1];
        net.minecraft.core.Direction[] dirHolder = new net.minecraft.core.Direction[1];
        double rayDist = PendulumConfig.INSTANCE.rayTraceDistance();
        ScriptEngine.submitToGameThread(() -> {
            if (MC.player == null || MC.gameMode == null || MC.level == null) return;
            Vec3 start = MC.player.getEyePosition();
            Vec3 look = MC.player.getLookAngle();
            Vec3 end = start.add(look.x * rayDist, look.y * rayDist, look.z * rayDist);
            ClipContext ctx = new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, MC.player);
            BlockHitResult bhr = MC.level.clip(ctx);
            if (bhr.getType() != HitResult.Type.MISS) {
                posHolder[0] = bhr.getBlockPos();
                dirHolder[0] = bhr.getDirection();
            }
        });
        if (posHolder[0] == null) return false;
        return doBreak(posHolder[0], dirHolder[0]);
    }

    /**
     * 直接破坏指定坐标的方块（无需射线追踪，不会挖错）。配合 findBlocks 使用。同步等待。
     */
    public static boolean breakBlockAt(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int x = ((Number) args[0]).intValue();
        int y = ((Number) args[1]).intValue();
        int z = ((Number) args[2]).intValue();
        final BlockPos pos = new BlockPos(x, y, z);
        net.minecraft.core.Direction[] dirHolder = new net.minecraft.core.Direction[1];
        ScriptEngine.submitToGameThread(() -> {
            if (MC.player == null) return;
            Vec3 eye = MC.player.getEyePosition();
            net.minecraft.core.Direction best = net.minecraft.core.Direction.NORTH;
            double bestDist = Double.MAX_VALUE;
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                double cx2 = pos.getX() + 0.5 + d.getStepX() * 0.5;
                double cy2 = pos.getY() + 0.5 + d.getStepY() * 0.5;
                double cz2 = pos.getZ() + 0.5 + d.getStepZ() * 0.5;
                double dist = eye.distanceToSqr(cx2, cy2, cz2);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = d;
                }
            }
            dirHolder[0] = best;
        });
        return doBreak(pos, dirHolder[0]);
    }

    private static boolean doBreak(BlockPos pos, net.minecraft.core.Direction dir) {
        ScriptEngine.submitToGameThread(() -> {
            PlayerSimulator.getInstance().setBreaking(pos, dir);
            MC.gameMode.startDestroyBlock(pos, dir);
        });
        return ScriptEngine.waitForBreak();
    }

    public static void swapHands(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        if (MC.player == null) return;
        ScriptEngine.submitToGameThread(() -> {
            ItemStack main = MC.player.getItemInHand(InteractionHand.MAIN_HAND);
            ItemStack off = MC.player.getItemInHand(InteractionHand.OFF_HAND);
            MC.player.setItemInHand(InteractionHand.MAIN_HAND, off);
            MC.player.setItemInHand(InteractionHand.OFF_HAND, main);
        });
    }

    public static void drop(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        ScriptEngine.submitToGameThread(() -> MC.player.drop(false));
    }

    public static void dropAll(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        ScriptEngine.submitToGameThread(() -> MC.player.drop(true));
    }

    public static void pickBlock(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        if (MC.player == null || MC.gameMode == null) return;
        ScriptEngine.submitToGameThread(() ->
                MC.gameMode.handlePickItem(MC.player.getInventory().selected));
    }

    // ==================== 物品栏 ====================

    public static void selectHotbar(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int slot = ((Number) args[0]).intValue() - 1;
        ScriptEngine.submitToGameThread(() -> {
            if (MC.player != null && slot >= 0 && slot < 9)
                MC.player.getInventory().selected = slot;
        });
    }

    public static double getSelectedSlot(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.submitToGameThread(() ->
                MC.player != null ? (double) (MC.player.getInventory().selected + 1) : 0);
    }

    public static boolean hasItem(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String itemName = Context.toString(args[0]);
        int required = args.length > 1 ? ((Number) args[1]).intValue() : 1;
        return ScriptEngine.submitToGameThread(() -> {
            if (MC.player == null) return false;
            Item target = BuiltInRegistries.ITEM.get(new ResourceLocation(itemName));
            int count = 0;
            Inventory inv = MC.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++)
                if (inv.getItem(i).is(target)) count += inv.getItem(i).getCount();
            return count >= required;
        });
    }

    // ==================== GUI ====================

    public static void closeGui(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        ScriptEngine.submitToGameThread(() -> MC.setScreen(null));
    }

    public static boolean isGuiOpen(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.submitToGameThread(() -> MC.screen != null);
    }

    public static String getGuiTitle(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.submitToGameThread(() -> {
            if (MC.screen == null) return "";
            Component title = MC.screen.getTitle();
            return title != null ? title.getString() : "";
        });
    }

    /**
     * 左键点击槽位，等待 1 tick 完成
     */
    public static void clickSlot(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int slotId = ((Number) args[0]).intValue();
        int button = args.length > 1 ? ((Number) args[1]).intValue() : 0;
        ScriptEngine.submitToGameThread(() -> {
            if (!(MC.screen instanceof AbstractContainerScreen<?> screen)) return;
            if (MC.player == null || MC.gameMode == null) return;
            MC.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slotId, button,
                    net.minecraft.world.inventory.ClickType.PICKUP, MC.player);
        });
        ScriptEngine.waitTicks(1);
    }

    /**
     * 右键点击槽位，等待 1 tick 完成
     */
    public static void clickSlotRight(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int slotId = ((Number) args[0]).intValue();
        ScriptEngine.submitToGameThread(() -> {
            if (!(MC.screen instanceof AbstractContainerScreen<?> screen)) return;
            if (MC.player == null || MC.gameMode == null) return;
            MC.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slotId, 1,
                    net.minecraft.world.inventory.ClickType.PICKUP, MC.player);
        });
        ScriptEngine.waitTicks(1);
    }

    /**
     * 合成一次，等待 1 tick 完成
     */
    public static void craft(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        ScriptEngine.submitToGameThread(() -> {
            if (!(MC.screen instanceof AbstractContainerScreen<?> screen)) return;
            if (MC.player == null || MC.gameMode == null) return;
            MC.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, 0, 0,
                    net.minecraft.world.inventory.ClickType.PICKUP, MC.player);
        });
        ScriptEngine.waitTicks(1);
    }

    /**
     * 合成全部，等待 1 tick 完成
     */
    public static void craftAll(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        ScriptEngine.submitToGameThread(() -> {
            if (!(MC.screen instanceof AbstractContainerScreen<?> screen)) return;
            if (MC.player == null || MC.gameMode == null) return;
            MC.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, 0, 0,
                    net.minecraft.world.inventory.ClickType.QUICK_MOVE, MC.player);
        });
        ScriptEngine.waitTicks(1);
    }

    // ==================== 世界 ====================

    public static boolean facingBlock(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String name = Context.toString(args[0]);
        return ScriptEngine.submitToGameThread(() -> {
            if (MC.hitResult == null || MC.hitResult.getType() != HitResult.Type.BLOCK) return false;
            BlockHitResult bhr = (BlockHitResult) MC.hitResult;
            ResourceLocation rl = new ResourceLocation(name);
            return BuiltInRegistries.BLOCK.getKey(MC.level.getBlockState(bhr.getBlockPos()).getBlock()).equals(rl);
        });
    }

    public static boolean facingEntity(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String name = Context.toString(args[0]);
        return ScriptEngine.submitToGameThread(() -> {
            if (MC.hitResult == null || MC.hitResult.getType() != HitResult.Type.ENTITY) return false;
            EntityHitResult ehr = (EntityHitResult) MC.hitResult;
            ResourceLocation rl = new ResourceLocation(name);
            return BuiltInRegistries.ENTITY_TYPE.getKey(ehr.getEntity().getType()).equals(rl);
        });
    }

    public static String getFacingBlock(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.submitToGameThread(() -> {
            if (MC.hitResult == null || MC.hitResult.getType() != HitResult.Type.BLOCK) return "";
            BlockHitResult bhr = (BlockHitResult) MC.hitResult;
            return BuiltInRegistries.BLOCK.getKey(MC.level.getBlockState(bhr.getBlockPos()).getBlock()).toString();
        });
    }

    public static String getBlock(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int x = ((Number) args[0]).intValue();
        int y = ((Number) args[1]).intValue();
        int z = ((Number) args[2]).intValue();
        return ScriptEngine.submitToGameThread(() -> {
            if (MC.level == null) return "air";
            return BuiltInRegistries.BLOCK.getKey(MC.level.getBlockState(new BlockPos(x, y, z)).getBlock()).toString();
        });
    }

    // ==================== 世界查询 ====================

    /**
     * 以玩家为中心，在球体半径内查找指定方块 ID，返回 [{x,y,z}, ...]。
     * findBlocks('minecraft:diamond_ore', 16)
     */
    public static Scriptable findBlocks(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String blockId = Context.toString(args[0]);
        int radius = args.length > 1 ? ((Number) args[1]).intValue() : 16;
        // 游戏线程采集数据，JS 线程构造 Rhino 对象
        List<int[]> positions = ScriptEngine.submitToGameThread(() -> {
            List<int[]> list = new ArrayList<>();
            if (MC.level == null || MC.player == null) return list;
            Block target = BuiltInRegistries.BLOCK.get(new ResourceLocation(blockId));
            BlockPos center = MC.player.blockPosition();
            int r2 = radius * radius;
            for (int dx = -radius; dx <= radius; dx++)
                for (int dy = -radius; dy <= radius; dy++)
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx * dx + dy * dy + dz * dz > r2) continue;
                        BlockPos pos = center.offset(dx, dy, dz);
                        if (MC.level.getBlockState(pos).is(target))
                            list.add(new int[]{pos.getX(), pos.getY(), pos.getZ()});
                    }
            return list;
        });
        NativeArray result = (NativeArray) cx.newArray(thisObj, 0);
        for (int[] p : positions) {
            Scriptable obj = cx.newObject(thisObj);
            obj.put("x", obj, p[0]);
            obj.put("y", obj, p[1]);
            obj.put("z", obj, p[2]);
            result.put(result.size(), result, obj);
        }
        return result;
    }

    /**
     * 以玩家为中心，在球体半径内查找指定 Tag 的方块，返回 [{x,y,z}, ...]。
     */
    public static Scriptable findBlocksByTag(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String tagStr = Context.toString(args[0]);
        int radius = args.length > 1 ? ((Number) args[1]).intValue() : 16;
        List<int[]> positions = ScriptEngine.submitToGameThread(() -> {
            List<int[]> list = new ArrayList<>();
            if (MC.level == null || MC.player == null) return list;
            TagKey<Block> tag = TagKey.create(BuiltInRegistries.BLOCK.key(), new ResourceLocation(tagStr));
            BlockPos center = MC.player.blockPosition();
            int r2 = radius * radius;
            for (int dx = -radius; dx <= radius; dx++)
                for (int dy = -radius; dy <= radius; dy++)
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx * dx + dy * dy + dz * dz > r2) continue;
                        BlockPos pos = center.offset(dx, dy, dz);
                        if (MC.level.getBlockState(pos).is(tag))
                            list.add(new int[]{pos.getX(), pos.getY(), pos.getZ()});
                    }
            return list;
        });
        NativeArray result = (NativeArray) cx.newArray(thisObj, 0);
        for (int[] p : positions) {
            Scriptable obj = cx.newObject(thisObj);
            obj.put("x", obj, p[0]);
            obj.put("y", obj, p[1]);
            obj.put("z", obj, p[2]);
            result.put(result.size(), result, obj);
        }
        return result;
    }

    /**
     * 在矩形区域内查找指定方块（未指定 blockId 则返回所有非空气方块）。
     */
    public static Scriptable findBlocksInBox(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int x1 = ((Number) args[0]).intValue();
        int y1 = ((Number) args[1]).intValue();
        int z1 = ((Number) args[2]).intValue();
        int x2 = ((Number) args[3]).intValue();
        int y2 = ((Number) args[4]).intValue();
        int z2 = ((Number) args[5]).intValue();
        String blockId = args.length > 6 ? Context.toString(args[6]) : null;
        List<Object[]> entries = ScriptEngine.submitToGameThread(() -> {
            List<Object[]> list = new ArrayList<>();
            if (MC.level == null) return list;
            Block target = blockId != null ? BuiltInRegistries.BLOCK.get(new ResourceLocation(blockId)) : null;
            int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
            int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
            int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
            for (int x = minX; x <= maxX; x++)
                for (int y = minY; y <= maxY; y++)
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = MC.level.getBlockState(pos);
                        if (target != null ? state.is(target) : !state.isAir())
                            list.add(new Object[]{x, y, z, target == null ? BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString() : null});
                    }
            return list;
        });
        NativeArray result = (NativeArray) cx.newArray(thisObj, 0);
        for (Object[] e : entries) {
            Scriptable obj = cx.newObject(thisObj);
            obj.put("x", obj, e[0]);
            obj.put("y", obj, e[1]);
            obj.put("z", obj, e[2]);
            if (e[3] != null) obj.put("block", obj, e[3]);
            result.put(result.size(), result, obj);
        }
        return result;
    }

    /**
     * 判断 (x,y,z) 处方块是否匹配给定 ID
     */
    public static boolean isBlock(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int x = ((Number) args[0]).intValue();
        int y = ((Number) args[1]).intValue();
        int z = ((Number) args[2]).intValue();
        String blockId = Context.toString(args[3]);
        return ScriptEngine.submitToGameThread(() -> {
            if (MC.level == null) return false;
            Block target = BuiltInRegistries.BLOCK.get(new ResourceLocation(blockId));
            return MC.level.getBlockState(new BlockPos(x, y, z)).is(target);
        });
    }

    /**
     * 判断 (x,y,z) 处方块是否匹配给定 Tag
     */
    public static boolean isBlockByTag(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int x = ((Number) args[0]).intValue();
        int y = ((Number) args[1]).intValue();
        int z = ((Number) args[2]).intValue();
        String tagStr = Context.toString(args[3]);
        return ScriptEngine.submitToGameThread(() -> {
            if (MC.level == null) return false;
            TagKey<Block> tag = TagKey.create(BuiltInRegistries.BLOCK.key(), new ResourceLocation(tagStr));
            return MC.level.getBlockState(new BlockPos(x, y, z)).is(tag);
        });
    }

    /**
     * 获取附近实体列表，可选过滤类型。
     * getNearbyEntities(radius=16, 'entityType'?)
     * 返回 [{name, type, x, y, z, distance}, ...]
     */
    public static Scriptable getNearbyEntities(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        double radius = ((Number) args[0]).doubleValue();
        String typeFilter = args.length > 1 ? Context.toString(args[1]) : null;
        List<Object[]> entities = ScriptEngine.submitToGameThread(() -> {
            List<Object[]> list = new ArrayList<>();
            if (MC.level == null || MC.player == null) return list;
            Vec3 eye = MC.player.getEyePosition();
            AABB box = new AABB(eye.x - radius, eye.y - radius, eye.z - radius,
                    eye.x + radius, eye.y + radius, eye.z + radius);
            for (Entity e : MC.level.getEntities(MC.player, box, e -> {
                if (e instanceof Player) return false;
                if (typeFilter == null || typeFilter.isEmpty()) return true;
                return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals(typeFilter);
            })) {
                list.add(new Object[]{
                        e.getName().getString(),
                        BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString(),
                        e.getX(), e.getY(), e.getZ(), e.distanceTo(MC.player)
                });
            }
            return list;
        });
        NativeArray result = (NativeArray) cx.newArray(thisObj, 0);
        for (Object[] e : entities) {
            Scriptable obj = cx.newObject(thisObj);
            obj.put("name", obj, e[0]);
            obj.put("type", obj, e[1]);
            obj.put("x", obj, e[2]);
            obj.put("y", obj, e[3]);
            obj.put("z", obj, e[4]);
            obj.put("distance", obj, e[5]);
            result.put(result.size(), result, obj);
        }
        return result;
    }

    /**
     * 获取附近玩家列表。
     */
    public static Scriptable getNearbyPlayers(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        double radius = ((Number) args[0]).doubleValue();
        List<Object[]> players = ScriptEngine.submitToGameThread(() -> {
            List<Object[]> list = new ArrayList<>();
            if (MC.level == null || MC.player == null) return list;
            for (Player p : MC.level.players()) {
                if (p == MC.player) continue;
                double d = p.distanceTo(MC.player);
                if (d <= radius)
                    list.add(new Object[]{p.getName().getString(), p.getX(), p.getY(), p.getZ(), d});
            }
            return list;
        });
        NativeArray result = (NativeArray) cx.newArray(thisObj, 0);
        for (Object[] p : players) {
            Scriptable obj = cx.newObject(thisObj);
            obj.put("name", obj, p[0]);
            obj.put("x", obj, p[1]);
            obj.put("y", obj, p[2]);
            obj.put("z", obj, p[3]);
            obj.put("distance", obj, p[4]);
            result.put(result.size(), result, obj);
        }
        return result;
    }

    /**
     * 从玩家视角发射射线，返回第一个碰撞。
     * rayTrace(maxDist=5.0) → {type:'block'|'entity'|'miss', x,y,z, face?, entityName?}
     */
    public static Scriptable rayTrace(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        double maxDist = args.length > 0 ? ((Number) args[0]).doubleValue() : 5.0;
        java.util.Map<String, Object> hitData = ScriptEngine.submitToGameThread(() -> {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            if (MC.level == null || MC.player == null) {
                map.put("type", "miss");
                return map;
            }
            Vec3 start = MC.player.getEyePosition();
            Vec3 look = MC.player.getLookAngle();
            Vec3 end = start.add(look.x * maxDist, look.y * maxDist, look.z * maxDist);

            ClipContext ctx = new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, MC.player);
            BlockHitResult blockHit = MC.level.clip(ctx);

            AABB sweep = MC.player.getBoundingBox().expandTowards(look.scale(maxDist)).inflate(1.0);
            EntityHitResult entityHit = null;
            double entityDist = maxDist;
            for (Entity e : MC.level.getEntities(MC.player, sweep, Entity::isPickable)) {
                AABB bb = e.getBoundingBox().inflate(0.3);
                var cr = bb.clip(start, end);
                if (cr.isPresent()) {
                    double d = start.distanceTo(cr.get());
                    if (d < entityDist) {
                        entityDist = d;
                        entityHit = new EntityHitResult(e, cr.get());
                    }
                }
            }

            double blockDist = blockHit.getType() != HitResult.Type.MISS
                    ? start.distanceTo(blockHit.getLocation()) : Double.MAX_VALUE;

            if (entityHit != null && entityDist < blockDist) {
                map.put("type", "entity");
                map.put("x", entityHit.getLocation().x);
                map.put("y", entityHit.getLocation().y);
                map.put("z", entityHit.getLocation().z);
                map.put("entityName", entityHit.getEntity().getName().getString());
                map.put("entityType", BuiltInRegistries.ENTITY_TYPE.getKey(entityHit.getEntity().getType()).toString());
                map.put("distance", entityDist);
            } else if (blockHit.getType() != HitResult.Type.MISS) {
                map.put("type", "block");
                map.put("x", blockHit.getBlockPos().getX());
                map.put("y", blockHit.getBlockPos().getY());
                map.put("z", blockHit.getBlockPos().getZ());
                map.put("face", blockHit.getDirection().getName());
                map.put("distance", blockDist);
            } else {
                map.put("type", "miss");
            }
            return map;
        });
        Scriptable result = cx.newObject(thisObj);
        for (java.util.Map.Entry<String, Object> e : hitData.entrySet())
            result.put(e.getKey(), result, e.getValue());
        return result;
    }

    // ==================== 聊天 ====================

    public static void say(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String msg = Context.toString(args[0]);
        ScriptEngine.submitToGameThread(() -> {
            if (MC.player != null) MC.player.connection.sendChat(msg);
        });
    }

    public static void log(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String msg = Context.toString(args[0]);
        ScriptEngine.submitToGameThread(() -> {
            if (MC.player != null)
                MC.player.displayClientMessage(Component.literal("§e[Pendulum] §r").append(msg), false);
        });
    }

    // 供 console.log 使用
    public static void consoleLog(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(" ");
            sb.append(Context.toString(args[i]));
        }
        LOGGER.info("[JS] {}", sb);
    }

    // ==================== 文件/控制 ====================

    /**
     * 暂停脚本执行 ticks 个游戏刻（默认 1）
     */
    public static void waitTick(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int ticks = args.length > 0 ? ((Number) args[0]).intValue() : 1;
        ScriptEngine.waitTicks(ticks);
    }

    public static void execFile(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String path = Context.toString(args[0]);
        ScriptEngine.getInstance().execFile(path);
    }

    public static String getScriptDir(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.getInstance().getScriptDir().toString();
    }

    // ==================== 帮助（供 JS 和命令共同使用） ====================

    public static String helpString() {
        return "§6" + net.minecraft.client.resources.language.I18n.get("pendulum.help.title") + "\n" +
                "§e" + net.minecraft.client.resources.language.I18n.get("pendulum.help.movement") + "\n" +
                "  §7" + net.minecraft.client.resources.language.I18n.get("pendulum.help.movement2") + "\n" +
                "§e" + net.minecraft.client.resources.language.I18n.get("pendulum.help.rotation") + "\n" +
                "§e" + net.minecraft.client.resources.language.I18n.get("pendulum.help.position") + "\n" +
                "§e" + net.minecraft.client.resources.language.I18n.get("pendulum.help.interaction") + "\n" +
                "§e" + net.minecraft.client.resources.language.I18n.get("pendulum.help.inventory") + "\n" +
                "§e" + net.minecraft.client.resources.language.I18n.get("pendulum.help.gui") + "\n" +
                "§e" + net.minecraft.client.resources.language.I18n.get("pendulum.help.crafting") + "\n" +
                "§e" + net.minecraft.client.resources.language.I18n.get("pendulum.help.world_query") + "\n" +
                "  §7" + net.minecraft.client.resources.language.I18n.get("pendulum.help.world_query2") + "\n" +
                "  §7" + net.minecraft.client.resources.language.I18n.get("pendulum.help.world_query3") + "\n" +
                "§e" + net.minecraft.client.resources.language.I18n.get("pendulum.help.facing") + "\n" +
                "§e" + net.minecraft.client.resources.language.I18n.get("pendulum.help.chat") + "\n" +
                "§e" + net.minecraft.client.resources.language.I18n.get("pendulum.help.files") + "\n" +
                "§e" + net.minecraft.client.resources.language.I18n.get("pendulum.help.baritone") + "\n" +
                "§e" + net.minecraft.client.resources.language.I18n.get("pendulum.help.control");
    }

    public static void help(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String msg = helpString();
        ScriptEngine.submitToGameThread(() -> {
            if (MC.player != null) MC.player.displayClientMessage(Component.literal(msg), false);
        });
    }
}
