package com.iafenvoy.pendulum.script;

import com.iafenvoy.pendulum.config.PendulumConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
 * 鎸傝浇鍒?JS 鍏ㄥ眬 minecraft 瀵硅薄涓婄殑鎵€鏈夊嚱鏁般€?
 * 鎵€鏈夋柟娉曠鍚嶅繀椤讳负 (Context, Scriptable, Object[], Function)銆?
 */
public final class MinecraftAPI {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Minecraft MC = Minecraft.getInstance();

    /**
     * minecraft 瀵硅薄涓婃毚闇茬殑鎵€鏈夊嚱鏁板悕
     */
    public static final List<String> FUNCTION_NAMES = Arrays.asList(
            // 绉诲姩
            "forward", "back", "left", "right", "stop",
            "jump", "sneak", "sprint", "stopSprint",
            "lookAt", "setYaw", "setPitch", "getYaw", "getPitch",
            "getX", "getY", "getZ",
            // 浜や簰
            "use", "attack", "breakBlock", "breakBlockAt", "swapHands",
            "drop", "dropAll", "pickBlock", "placeBlock", "placeBlockAt", "jumpAndPlaceBelow",
            "startUse", "stopUse", "useItem",
            // 鐗╁搧鏍?
            "selectHotbar", "getSelectedSlot", "hasItem",
            "getItemInSlot", "getItemInHand", "getItemOffhand", "getAllItems",
            // GUI
            "closeGui", "isGuiOpen", "getGuiTitle",
            "clickSlot", "clickSlotRight",
            "craft", "craftAll",
            "getContainerSize", "getContainerItem", "getContainerAllItems", "getContainerType",
            "getGuiElements",
            "moveItem", "quickMoveItem",
            // 涓栫晫
            "facingBlock", "facingEntity", "getFacingBlock",
            "getBlock", "isBlock", "isBlockByTag", "getBlockState",
            "findBlocks", "findBlocksByTag", "findBlocksInBox",
            "getNearbyEntities", "getNearbyPlayers", "rayTrace",
            "getLookingEntity",
            // 鐜╁鐘舵€?
            "getPlayerHealth", "getPlayerHunger", "getPlayerArmor",
            "getAttackCooldown", "getReachDistance", "canReach", "canSeeBlock",
            "getBiomeAt", "getLightLevel", "getDifficulty", "getDimension",
            // 鑱婂ぉ/鎸囦护
            "say", "log", "executeCommand",
            // 鏂囦欢/鎺у埗
            "execFile", "getScriptDir", "waitTick",
            // 杈呭姪
            "help"
    );

    // ==================== 绉诲姩 ====================

    /**
     * forward() 鈥?涓€鐩存寜浣忓墠锛沠orward(ticks) 鈥?鍓嶈繘 ticks 鍒诲悗鑷姩鍋滄
     */
    public static void forward(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        if (args.length > 0) {
            moveFor("forward", ((Number) args[0]).intValue());
        } else {
            moveHold("forward");
        }
    }

    /**
     * back() 鈥?涓€鐩存寜浣忓悗锛沚ack(ticks) 鈥?鍚庨€€ ticks 鍒诲悗鑷姩鍋滄
     */
    public static void back(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        if (args.length > 0) {
            moveFor("back", ((Number) args[0]).intValue());
        } else {
            moveHold("back");
        }
    }

    /**
     * left() 鈥?涓€鐩存寜浣忓乏锛沴eft(ticks) 鈥?宸︾Щ ticks 鍒诲悗鑷姩鍋滄
     */
    public static void left(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        if (args.length > 0) {
            moveFor("left", ((Number) args[0]).intValue());
        } else {
            moveHold("left");
        }
    }

    /**
     * right() 鈥?涓€鐩存寜浣忓彸锛況ight(ticks) 鈥?鍙崇Щ ticks 鍒诲悗鑷姩鍋滄
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

    // ==================== 浜や簰 ====================

    /**
     * 使用物品/与方块交互（单击，不保持）
     * 如需吃东西/拉弓/举盾等长按操作，请用 useItem(ticks) 或 startUse() / stopUse()。
     */
    public static void use(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        if (MC.player == null || MC.gameMode == null) return;
        ScriptEngine.submitToGameThread(() -> MC.gameMode.useItem(MC.player, InteractionHand.MAIN_HAND));
        if (PendulumConfig.INSTANCE.syncUseAttack.getValue()) {
            ScriptEngine.waitTicks(1);
        }
    }

    /**
     * startUse() — 开始长按右键（吃东西/拉弓/举盾持续），需配合 stopUse() 停止。
     * 推荐使用 useItem(ticks) 一步到位。
     */
    public static void startUse(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        ScriptEngine.runOnGameThread(() -> PlayerSimulator.getInstance().startUsingItem());
    }

    /**
     * stopUse() — 停止长按右键，释放物品（如射箭、吃完食物、放下盾牌）。
     */
    public static void stopUse(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        ScriptEngine.runOnGameThread(() -> PlayerSimulator.getInstance().stopUsingItem());
    }

    /**
     * useItem(ticks) — 持续使用物品 ticks 刻后自动停止。
     * 例如：mc.useItem(32) 按住右键 32 tick（约 1.6 秒），足够吃完大多数食物。
     * 拉弓需要约 20 tick 蓄满力，盾牌可以 mc.useItem(100) 长时间举盾。
     * 返回是否成功开始使用。
     */
    public static boolean useItem(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        if (MC.player == null || MC.gameMode == null) return false;
        int ticks = args.length > 0 ? ((Number) args[0]).intValue() : 32;
        ScriptEngine.runOnGameThread(() -> PlayerSimulator.getInstance().startUsingItem());
        ScriptEngine.waitTicks(ticks);
        ScriptEngine.runOnGameThread(() -> PlayerSimulator.getInstance().stopUsingItem());
        return true;
    }

    /**
     * 鏀诲嚮瀹炰綋
     */
    public static void attack(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        if (!PendulumConfig.INSTANCE.allowAttack.getValue()) return;
        if (MC.player == null || MC.gameMode == null) return;
        // 1.20.1 的 attack 要求非 null entity，否则 NPE
        ScriptEngine.submitToGameThread(() -> {
            if (MC.crosshairPickEntity != null)
                MC.gameMode.attack(MC.player, MC.crosshairPickEntity);
        });
        if (PendulumConfig.INSTANCE.syncUseAttack.getValue()) {
            ScriptEngine.waitTicks(2);
        }
    }

    /**
     * 鐮村潖瑙嗙嚎瀵瑰噯鐨勬柟鍧楋紝鎸佺画鎸変綇鐩村埌鐮村潖锛堝悓姝ョ瓑寰咃級銆傛敞鎰忓皠绾垮彲鑳芥墦鍒伴殰纰嶇墿锛岀簿鍑嗙牬鍧忔帹鑽?breakBlockAt
     */
    public static boolean breakBlock(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        if (!PendulumConfig.INSTANCE.allowBreak.getValue()) return false;
        BlockPos[] posHolder = new BlockPos[1];
        net.minecraft.core.Direction[] dirHolder = new net.minecraft.core.Direction[1];
        double rayDist = PendulumConfig.INSTANCE.rayTraceDistance.getValue();
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
     * 鐩存帴鐮村潖鎸囧畾鍧愭爣鐨勬柟鍧楋紙鏃犻渶灏勭嚎杩借釜锛屼笉浼氭寲閿欙級銆傞厤鍚?findBlocks 浣跨敤銆傚悓姝ョ瓑寰呫€?
     */
    public static boolean breakBlockAt(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        if (!PendulumConfig.INSTANCE.allowBreak.getValue()) return false;
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

    /**
     * 在视线对准的方块上放置方块（从快捷栏或指定槽位），同步等待 3 tick。
     * 注意：依赖准星目标 MC.hitResult，可能因视线投射到非预期方块而放偏。
     * 推荐：精准放置用 placeBlockAt(x,y,z)，脚下放置用 jumpAndPlaceBelow()。
     */
    public static boolean placeBlock(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        if (!PendulumConfig.INSTANCE.allowPlace.getValue()) return false;
        if (MC.player == null || MC.gameMode == null) return false;
        int slot = args.length > 0 ? ((Number) args[0]).intValue() - 1 : MC.player.getInventory().selected;
        ScriptEngine.submitToGameThread(() -> {
            if (slot >= 0 && slot < 9) MC.player.getInventory().selected = slot;
            MC.gameMode.useItemOn(MC.player, InteractionHand.MAIN_HAND,
                    MC.hitResult != null && MC.hitResult.getType() == HitResult.Type.BLOCK
                            ? (BlockHitResult) MC.hitResult : null);
        });
        ScriptEngine.waitTicks(3);
        return true;
    }

    /**
     * placeBlockAt(x, y, z, slot?)  — 精确放置方块到指定坐标（无视视线投射）。
     * 自动计算最近的相邻面进行放置，同步等待 3 tick。返回是否成功。
     * 推荐：能用 baritone 就用 baritone（br 对象），更稳定可靠。
     */
    public static boolean placeBlockAt(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        if (!PendulumConfig.INSTANCE.allowPlace.getValue()) return false;
        if (MC.player == null || MC.gameMode == null || MC.level == null) return false;
        int x = ((Number) args[0]).intValue();
        int y = ((Number) args[1]).intValue();
        int z = ((Number) args[2]).intValue();
        int slot = args.length > 3 ? ((Number) args[3]).intValue() - 1 : -1;
        final BlockPos placePos = new BlockPos(x, y, z);

        // Check target is air
        boolean[] canPlace = {false};
        net.minecraft.core.Direction[] bestFace = new net.minecraft.core.Direction[1];
        BlockPos[] bestNeighbor = new BlockPos[1];

        ScriptEngine.submitToGameThread(() -> {
            if (!MC.level.getBlockState(placePos).isAir()) return;
            // Select slot if specified
            if (slot >= 0 && slot < 9) MC.player.getInventory().selected = slot;
            // Find the best adjacent face to click
            Vec3 eye = MC.player.getEyePosition();
            double bestDist = Double.MAX_VALUE;
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                BlockPos neighbor = placePos.relative(d.getOpposite());
                if (MC.level.getBlockState(neighbor).isAir()) continue;
                double cx2 = neighbor.getX() + 0.5 + d.getStepX() * 0.5;
                double cy2 = neighbor.getY() + 0.5 + d.getStepY() * 0.5;
                double cz2 = neighbor.getZ() + 0.5 + d.getStepZ() * 0.5;
                double dist = eye.distanceToSqr(cx2, cy2, cz2);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestFace[0] = d;
                    bestNeighbor[0] = neighbor;
                }
            }
            if (bestFace[0] != null) canPlace[0] = true;
        });

        if (!canPlace[0] || bestFace[0] == null || bestNeighbor[0] == null) return false;

        ScriptEngine.submitToGameThread(() -> {
            net.minecraft.core.Direction face = bestFace[0];
            BlockPos neighbor = bestNeighbor[0];
            Vec3 hitVec = new Vec3(
                    neighbor.getX() + 0.5 + face.getStepX() * 0.5,
                    neighbor.getY() + 0.5 + face.getStepY() * 0.5,
                    neighbor.getZ() + 0.5 + face.getStepZ() * 0.5);
            BlockHitResult bhr = new BlockHitResult(hitVec, face, neighbor, false);
            MC.gameMode.useItemOn(MC.player, InteractionHand.MAIN_HAND, bhr);
        });
        ScriptEngine.waitTicks(3);

        return ScriptEngine.submitToGameThread(() -> !MC.level.getBlockState(placePos).isAir());
    }

    /**
     * jumpAndPlaceBelow()  — 跳起并在脚下放置方块（自动向下看，无视视线投射问题）。
     * 返回是否成功。适合造桥、填坑等场景。
     */
    public static boolean jumpAndPlaceBelow(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        if (!PendulumConfig.INSTANCE.allowPlace.getValue()) return false;
        if (MC.player == null || MC.gameMode == null || MC.level == null) return false;

        // Jump
        ScriptEngine.runOnGameThread(() -> {
            MC.player.jumpFromGround();
            MC.player.setXRot(90);
            PlayerSimulator.getInstance().setLookPitch(90);
        });
        ScriptEngine.waitTicks(2);

        // Compute target position
        BlockPos[] targetHolder = new BlockPos[1];
        ScriptEngine.submitToGameThread(() -> {
            int tx = (int) Math.floor(MC.player.getX());
            int ty = (int) Math.floor(MC.player.getY() - 0.5);
            int tz = (int) Math.floor(MC.player.getZ());
            targetHolder[0] = new BlockPos(tx, ty, tz);
        });

        if (targetHolder[0] == null) return false;

        BlockPos below = targetHolder[0];
        // Click the TOP face of the block below; new block goes to below.relative(UP) = feet position
        BlockPos placePos = below.relative(net.minecraft.core.Direction.UP);

        ScriptEngine.submitToGameThread(() -> {
            Vec3 hitVec = new Vec3(below.getX() + 0.5, below.getY() + 1.0, below.getZ() + 0.5);
            BlockHitResult bhr = new BlockHitResult(hitVec, net.minecraft.core.Direction.UP, below, false);
            MC.gameMode.useItemOn(MC.player, InteractionHand.MAIN_HAND, bhr);
        });
        ScriptEngine.waitTicks(3);

        return ScriptEngine.submitToGameThread(() -> !MC.level.getBlockState(placePos).isAir());
    }

    // ==================== 鐗╁搧鏍?====================

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

    /**
     * 鑾峰彇鎸囧畾妲戒綅鐨勭墿鍝佽鎯?({id,count,maxCount,durability,maxDurability,name})
     */
    public static Scriptable getItemInSlot(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int slot = ((Number) args[0]).intValue();
        return ScriptEngine.submitToGameThread(() -> {
            if (MC.player == null) return null;
            if (slot < 0 || slot >= MC.player.getInventory().getContainerSize()) return null;
            return itemStackToObject(cx, thisObj, MC.player.getInventory().getItem(slot));
        });
    }

    /**
     * 鑾峰彇鎵嬩笂鐗╁搧璇︽儏
     */
    public static Scriptable getItemInHand(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.submitToGameThread(() ->
                MC.player != null ? itemStackToObject(cx, thisObj, MC.player.getItemInHand(InteractionHand.MAIN_HAND)) : null);
    }

    /**
     * 鑾峰彇鍓墜鐗╁搧璇︽儏
     */
    public static Scriptable getItemOffhand(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.submitToGameThread(() ->
                MC.player != null ? itemStackToObject(cx, thisObj, MC.player.getItemInHand(InteractionHand.OFF_HAND)) : null);
    }

    /**
     * 鑾峰彇鏁翠釜鑳屽寘 [{slot,id,count,...}]
     */
    public static Scriptable getAllItems(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.submitToGameThread(() -> {
            if (MC.player == null) return cx.newArray(thisObj, 0);
            NativeArray result = (NativeArray) cx.newArray(thisObj, 0);
            Inventory inv = MC.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (!stack.isEmpty()) {
                    Scriptable obj = itemStackToObject(cx, thisObj, stack);
                    if (obj != null) {
                        obj.put("slot", obj, i);
                        result.put(result.size(), result, obj);
                    }
                }
            }
            return result;
        });
    }

    private static Scriptable itemStackToObject(Context cx, Scriptable scope, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Scriptable obj = cx.newObject(scope);
        obj.put("id", obj, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        obj.put("count", obj, stack.getCount());
        obj.put("maxCount", obj, stack.getMaxStackSize());
        obj.put("durability", obj, stack.getDamageValue());
        obj.put("maxDurability", obj, stack.getMaxDamage());
        obj.put("name", obj, stack.getHoverName().getString());
        return obj;
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
     * 宸﹂敭鐐瑰嚮妲戒綅锛岀瓑寰?1 tick 瀹屾垚
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
     * 鍙抽敭鐐瑰嚮妲戒綅锛岀瓑寰?1 tick 瀹屾垚
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
     * 鍚堟垚涓€娆★紝绛夊緟 1 tick 瀹屾垚
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
     * 鍚堟垚鍏ㄩ儴锛岀瓑寰?1 tick 瀹屾垚
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

    /**
     * 鑾峰彇褰撳墠瀹瑰櫒鐨勬Ы浣嶆暟
     */
    public static double getContainerSize(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.submitToGameThread(() ->
                (double) (MC.screen instanceof AbstractContainerScreen<?> s ? s.getMenu().slots.size() : 0));
    }

    /**
     * 鑾峰彇瀹瑰櫒鎸囧畾妲戒綅鐨勭墿鍝?
     */
    public static Scriptable getContainerItem(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int slot = ((Number) args[0]).intValue();
        return ScriptEngine.submitToGameThread(() -> {
            if (!(MC.screen instanceof AbstractContainerScreen<?> s)) return null;
            if (slot < 0 || slot >= s.getMenu().slots.size()) return null;
            return itemStackToObject(cx, thisObj, s.getMenu().getSlot(slot).getItem());
        });
    }

    /**
     * 鑾峰彇瀹瑰櫒鎵€鏈夐潪绌烘Ы浣?[{slot,id,count,...}]
     */
    public static Scriptable getContainerAllItems(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.submitToGameThread(() -> {
            NativeArray result = (NativeArray) cx.newArray(thisObj, 0);
            if (!(MC.screen instanceof AbstractContainerScreen<?> s)) return result;
            for (int i = 0; i < s.getMenu().slots.size(); i++) {
                ItemStack stack = s.getMenu().getSlot(i).getItem();
                if (!stack.isEmpty()) {
                    Scriptable obj = itemStackToObject(cx, thisObj, stack);
                    if (obj != null) {
                        obj.put("slot", obj, i);
                        result.put(result.size(), result, obj);
                    }
                }
            }
            return result;
        });
    }

    /**
     * 鑾峰彇瀹瑰櫒绫诲瀷鍚? "crafting_table"/"chest"/"furnace"/"anvil"/"enchanting"/"none"/"unknown"
     */
    public static String getContainerType(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.submitToGameThread(() -> {
            if (MC.screen == null) return "none";
            String cn = MC.screen.getClass().getName().toLowerCase();
            if (cn.contains("crafting")) return "crafting_table";
            if (cn.contains("furnace") || cn.contains("blast") || cn.contains("smoker")) return "furnace";
            if (cn.contains("chest") || cn.contains("shulker") || cn.contains("barrel")) return "chest";
            if (cn.contains("enchant")) return "enchanting";
            if (cn.contains("anvil")) return "anvil";
            if (cn.contains("inventory") || cn.contains("container")) return "container";
            return "unknown";
        });
    }

    /**
     * 获取当前 Screen 上的非物品槽 GUI 控件列表（按钮、文本、图片等）。
     * 返回 [{type, id?, text?, x, y, width, height}, ...]
     */
    public static Scriptable getGuiElements(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.submitToGameThread(() -> {
            NativeArray results = (NativeArray) cx.newArray(thisObj, 0);
            if (MC.screen == null) return results;
            for (var child : MC.screen.children()) {
                Scriptable obj = buildGuiElementObject(cx, thisObj, child);
                if (obj != null) results.put(results.size(), results, obj);
            }
            return results;
        });
    }

    private static Scriptable buildGuiElementObject(Context cx, Scriptable scope, Object widget) {
        Scriptable obj = cx.newObject(scope);
        Class<?> clazz = widget.getClass();
        obj.put("type", obj, clazz.getSimpleName());
        // AbstractWidget members
        try {
            var xField = findField(clazz, "x", "getX", "field_22786");
            var yField = findField(clazz, "y", "getY", "field_22787");
            var wField = findField(clazz, "width", "getWidth", "field_22788");
            var hField = findField(clazz, "height", "getHeight", "field_22789");
            if (xField != null) obj.put("x", obj, ((Number) xField.get(widget)).intValue());
            if (yField != null) obj.put("y", obj, ((Number) yField.get(widget)).intValue());
            if (wField != null) obj.put("width", obj, ((Number) wField.get(widget)).intValue());
            if (hField != null) obj.put("height", obj, ((Number) hField.get(widget)).intValue());
        } catch (Exception ignored) {
        }
        // Text
        try {
            var msgField = findField(clazz, "message", "getMessage", "field_22791");
            if (msgField != null) {
                Object msg = msgField.get(widget);
                obj.put("text", obj, msg instanceof net.minecraft.network.chat.Component c ? c.getString() : msg.toString());
            }
        } catch (Exception ignored) {
        }
        // Id for buttons (optional)
        try {
            var idField = findField(clazz, "id");
            if (idField != null) obj.put("id", obj, idField.get(widget).toString());
        } catch (Exception ignored) {
        }
        return obj;
    }

    private static java.lang.reflect.Field findField(Class<?> clazz, String... candidates) {
        for (String name : candidates) {
            try {
                java.lang.reflect.Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        // Walk superclass
        if (clazz.getSuperclass() != null) return findField(clazz.getSuperclass(), candidates);
        // Try getter methods
        for (String name : candidates) {
            if (name.startsWith("get")) {
                try {
                    java.lang.reflect.Method m = clazz.getMethod(name);
                    // Not a field lookup — we'd need a different approach
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        return null;
    }

    /**
     * 鍦ㄥ鍣ㄥ唴绉诲姩鐗╁搧锛堝乏閿偣鍑?fromSlot锛屽啀鐐瑰嚮 toSlot锛?
     */
    public static void moveItem(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int from = ((Number) args[0]).intValue();
        int to = ((Number) args[1]).intValue();
        ScriptEngine.submitToGameThread(() -> {
            if (!(MC.screen instanceof AbstractContainerScreen<?> s)) return;
            if (MC.player == null || MC.gameMode == null) return;
            int cid = s.getMenu().containerId;
            MC.gameMode.handleInventoryMouseClick(cid, from, 0, net.minecraft.world.inventory.ClickType.PICKUP, MC.player);
            MC.gameMode.handleInventoryMouseClick(cid, to, 0, net.minecraft.world.inventory.ClickType.PICKUP, MC.player);
        });
        ScriptEngine.waitTicks(1);
    }

    /**
     * Shift+鐐瑰嚮蹇€熺Щ鍔ㄧ墿鍝侊紙瀹瑰櫒鈫旇儗鍖咃級
     */
    public static void quickMoveItem(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int slot = ((Number) args[0]).intValue();
        ScriptEngine.submitToGameThread(() -> {
            if (!(MC.screen instanceof AbstractContainerScreen<?> s)) return;
            if (MC.player == null || MC.gameMode == null) return;
            MC.gameMode.handleInventoryMouseClick(s.getMenu().containerId, slot, 0,
                    net.minecraft.world.inventory.ClickType.QUICK_MOVE, MC.player);
        });
        ScriptEngine.waitTicks(1);
    }

    // ==================== 涓栫晫 ====================

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

    /**
     * 鑾峰彇鏂瑰潡瀹屾暣鐘舵€?({id, properties: {facing: 'north', ...}})
     */
    public static Scriptable getBlockState(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int x = ((Number) args[0]).intValue();
        int y = ((Number) args[1]).intValue();
        int z = ((Number) args[2]).intValue();
        return ScriptEngine.submitToGameThread(() -> {
            if (MC.level == null) return null;
            BlockState state = MC.level.getBlockState(new BlockPos(x, y, z));
            Scriptable obj = cx.newObject(thisObj);
            obj.put("id", obj, BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
            Scriptable props = cx.newObject(thisObj);
            for (var entry : state.getValues().entrySet()) {
                props.put(entry.getKey().getName(), props, entry.getValue().toString());
            }
            obj.put("properties", obj, props);
            return obj;
        });
    }

    /**
     * 鑾峰彇鍑嗘槦瀵瑰噯鐨勫疄浣撹鎯?
     */
    public static Scriptable getLookingEntity(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.submitToGameThread(() -> {
            if (MC.player == null || MC.hitResult == null || MC.hitResult.getType() != HitResult.Type.ENTITY)
                return null;
            EntityHitResult ehr = (EntityHitResult) MC.hitResult;
            Entity e = ehr.getEntity();
            Scriptable obj = cx.newObject(thisObj);
            obj.put("type", obj, BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
            obj.put("name", obj, e.getName().getString());
            obj.put("x", obj, e.getX());
            obj.put("y", obj, e.getY());
            obj.put("z", obj, e.getZ());
            obj.put("distance", obj, MC.player.distanceTo(e));
            if (e instanceof net.minecraft.world.entity.LivingEntity le) {
                obj.put("health", obj, le.getHealth());
                obj.put("maxHealth", obj, le.getMaxHealth());
                obj.put("isAlive", obj, le.isAlive());
            }
            return obj;
        });
    }

    // ==================== 涓栫晫鏌ヨ ====================

    /**
     * 浠ョ帺瀹朵负涓績锛屽湪鐞冧綋鍗婂緞鍐呮煡鎵炬寚瀹氭柟鍧?ID锛岃繑鍥?[{x,y,z}, ...]銆?
     * findBlocks('minecraft:diamond_ore', 16)
     */
    public static Scriptable findBlocks(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String blockId = Context.toString(args[0]);
        int radius = args.length > 1 ? ((Number) args[1]).intValue() : 16;
        // 娓告垙绾跨▼閲囬泦鏁版嵁锛孞S 绾跨▼鏋勯€?Rhino 瀵硅薄
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
     * 浠ョ帺瀹朵负涓績锛屽湪鐞冧綋鍗婂緞鍐呮煡鎵炬寚瀹?Tag 鐨勬柟鍧楋紝杩斿洖 [{x,y,z}, ...]銆?
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
     * 鍦ㄧ煩褰㈠尯鍩熷唴鏌ユ壘鎸囧畾鏂瑰潡锛堟湭鎸囧畾 blockId 鍒欒繑鍥炴墍鏈夐潪绌烘皵鏂瑰潡锛夈€?
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
     * 鍒ゆ柇 (x,y,z) 澶勬柟鍧楁槸鍚﹀尮閰嶇粰瀹?ID
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
     * 鍒ゆ柇 (x,y,z) 澶勬柟鍧楁槸鍚﹀尮閰嶇粰瀹?Tag
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
     * 鑾峰彇闄勮繎瀹炰綋鍒楄〃锛屽彲閫夎繃婊ょ被鍨嬨€?
     * getNearbyEntities(radius=16, 'entityType'?)
     * 杩斿洖 [{name, type, x, y, z, distance}, ...]
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
     * 鑾峰彇闄勮繎鐜╁鍒楄〃銆?
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
     * 浠庣帺瀹惰瑙掑彂灏勫皠绾匡紝杩斿洖绗竴涓鎾炪€?
     * rayTrace(maxDist=5.0) 鈫?{type:'block'|'entity'|'miss', x,y,z, face?, entityName?}
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

    // ==================== 鐜╁鐘舵€?====================

    public static double getPlayerHealth(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.submitToGameThread(() -> MC.player != null ? MC.player.getHealth() : 0);
    }

    public static double getPlayerHunger(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.submitToGameThread(() -> MC.player != null ? (double) MC.player.getFoodData().getFoodLevel() : 0);
    }

    public static double getPlayerArmor(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.submitToGameThread(() -> MC.player != null ? (double) MC.player.getArmorValue() : 0);
    }

    public static double getAttackCooldown(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.submitToGameThread(() -> MC.player != null ? MC.player.getAttackStrengthScale(0.5f) : 0);
    }

    public static double getReachDistance(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.submitToGameThread(() ->
                MC.player != null ? MC.gameMode.getPickRange() : 4.5);
    }

    /**
     * 鐜╁鑳藉惁澶熷埌 (x,y,z) 鈥?璺濈鍒ゆ柇
     */
    public static boolean canReach(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        double x = ((Number) args[0]).doubleValue();
        double y = ((Number) args[1]).doubleValue();
        double z = ((Number) args[2]).doubleValue();
        return ScriptEngine.submitToGameThread(() -> {
            if (MC.player == null) return false;
            double dist = MC.player.getEyePosition().distanceTo(new Vec3(x + 0.5, y + 0.5, z + 0.5));
            return dist <= MC.gameMode.getPickRange() + 1.0;
        });
    }

    /**
     * 鐜╁鑳藉惁鐪嬪埌鏂瑰潡锛堣绾挎棤閬尅锛?
     */
    public static boolean canSeeBlock(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int x = ((Number) args[0]).intValue();
        int y = ((Number) args[1]).intValue();
        int z = ((Number) args[2]).intValue();
        return ScriptEngine.submitToGameThread(() -> {
            if (MC.level == null || MC.player == null) return false;
            Vec3 start = MC.player.getEyePosition();
            Vec3 end = new Vec3(x + 0.5, y + 0.5, z + 0.5);
            ClipContext ctx = new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, MC.player);
            BlockHitResult bhr = MC.level.clip(ctx);
            return bhr.getType() != HitResult.Type.MISS && bhr.getBlockPos().equals(new BlockPos(x, y, z));
        });
    }

    // ==================== 鐜淇℃伅 ====================

    public static String getBiomeAt(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int x = ((Number) args[0]).intValue();
        int y = ((Number) args[1]).intValue();
        int z = ((Number) args[2]).intValue();
        return ScriptEngine.submitToGameThread(() -> {
            if (MC.level == null) return "unknown";
            var biome = MC.level.getBiome(new BlockPos(x, y, z));
            return biome.unwrapKey().map(k -> k.location().toString()).orElse("unknown");
        });
    }

    public static double getLightLevel(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int x = ((Number) args[0]).intValue();
        int y = ((Number) args[1]).intValue();
        int z = ((Number) args[2]).intValue();
        return ScriptEngine.submitToGameThread(() ->
                MC.level != null ? (double) MC.level.getMaxLocalRawBrightness(new BlockPos(x, y, z)) : 0);
    }

    public static String getDifficulty(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.submitToGameThread(() ->
                MC.level != null ? MC.level.getDifficulty().getKey() : "unknown");
    }

    public static String getDimension(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.submitToGameThread(() ->
                MC.level != null ? MC.level.dimension().location().toString() : "unknown");
    }

    // ==================== 鑱婂ぉ ====================

    public static void say(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        if (!PendulumConfig.INSTANCE.allowSay.getValue()) {
            ScriptEngine.submitToGameThread(() -> {
                if (MC.player != null)
                    MC.player.displayClientMessage(Component.literal("§c[Pendulum] Chat disabled by config permission."), false);
            });
            return;
        }
        String msg = Context.toString(args[0]);
        ScriptEngine.submitToGameThread(() -> {
            if (MC.player != null) MC.player.connection.sendChat(msg);
        });
    }

    /**
     * 鎵ц瀹㈡埛绔懡浠わ紙濡?/give /tp /gamemode 绛夛級
     */
    public static void executeCommand(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        if (!PendulumConfig.INSTANCE.allowExecuteCommand.getValue()) {
            ScriptEngine.submitToGameThread(() -> {
                if (MC.player != null)
                    MC.player.displayClientMessage(Component.literal("§c[Pendulum] Commands disabled by config permission."), false);
            });
            return;
        }
        String cmd = Context.toString(args[0]);
        ScriptEngine.submitToGameThread(() -> {
            if (MC.player != null) MC.player.connection.sendCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
        });
    }

    public static void log(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String msg = Context.toString(args[0]);
        ScriptEngine.submitToGameThread(() -> {
            if (MC.player != null)
                MC.player.displayClientMessage(Component.literal("§e[Pendulum] §r").append(Component.literal(msg)), false);
        });
    }

    // 渚?console.log 浣跨敤
    public static void consoleLog(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(" ");
            sb.append(Context.toString(args[i]));
        }
        LOGGER.info("[JS] {}", sb);
    }

    // ==================== 鏂囦欢/鎺у埗 ====================

    /**
     * 鏆傚仠鑴氭湰鎵ц ticks 涓父鎴忓埢锛堥粯璁?1锛?
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

    // ==================== 甯姪锛堜緵 JS 鍜屽懡浠ゅ叡鍚屼娇鐢級 ====================

    public static String helpString() {
        return "搂6" + net.minecraft.client.resources.language.I18n.get("pendulum.help.title") + "\n" +
                "搂e" + net.minecraft.client.resources.language.I18n.get("pendulum.help.movement") + "\n" +
                "  搂7" + net.minecraft.client.resources.language.I18n.get("pendulum.help.movement2") + "\n" +
                "搂e" + net.minecraft.client.resources.language.I18n.get("pendulum.help.rotation") + "\n" +
                "搂e" + net.minecraft.client.resources.language.I18n.get("pendulum.help.position") + "\n" +
                "搂e" + net.minecraft.client.resources.language.I18n.get("pendulum.help.interaction") + "\n" +
                "搂e" + net.minecraft.client.resources.language.I18n.get("pendulum.help.inventory") + "\n" +
                "搂e" + net.minecraft.client.resources.language.I18n.get("pendulum.help.gui") + "\n" +
                "搂e" + net.minecraft.client.resources.language.I18n.get("pendulum.help.crafting") + "\n" +
                "搂e" + net.minecraft.client.resources.language.I18n.get("pendulum.help.world_query") + "\n" +
                "  搂7" + net.minecraft.client.resources.language.I18n.get("pendulum.help.world_query2") + "\n" +
                "  搂7" + net.minecraft.client.resources.language.I18n.get("pendulum.help.world_query3") + "\n" +
                "搂e" + net.minecraft.client.resources.language.I18n.get("pendulum.help.facing") + "\n" +
                "搂e" + net.minecraft.client.resources.language.I18n.get("pendulum.help.chat") + "\n" +
                "搂e" + net.minecraft.client.resources.language.I18n.get("pendulum.help.files") + "\n" +
                "搂e" + net.minecraft.client.resources.language.I18n.get("pendulum.help.baritone") + "\n" +
                "搂e" + net.minecraft.client.resources.language.I18n.get("pendulum.help.control");
    }

    public static void help(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String msg = helpString();
        ScriptEngine.submitToGameThread(() -> {
            if (MC.player != null) MC.player.displayClientMessage(Component.literal(msg), false);
        });
    }
}

