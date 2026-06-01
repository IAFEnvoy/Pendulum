package com.iafenvoy.pendulum.script;

import net.minecraft.core.BlockPos;

/**
 * 需要跨 tick 等待完成的同步操作。
 * MinecraftAPI 中的耗时方法会设置此对象，由 ScriptEngine 在每 tick 检查完成状态。
 */
public final class SyncAction {
    public enum Type {
        /**
         * 破坏方块 — 等待目标位置变为空气
         */
        BREAK_BLOCK,
        /**
         * 等待 1 tick 让服务端处理
         */
        WAIT_TICK,
        /**
         * 与方块/物品交互后等待 1 tick
         */
        USE_ITEM,
        /**
         * 合成/点击槽位后等待 1 tick
         */
        CONTAINER_CLICK
    }

    private final Type type;
    private final BlockPos targetPos;
    private int tickCounter;
    private final int maxTicks; // 超时兜底

    private SyncAction(Type type, BlockPos targetPos, int maxTicks) {
        this.type = type;
        this.targetPos = targetPos;
        this.maxTicks = maxTicks;
    }

    public static SyncAction breakBlock(BlockPos pos, int timeoutTicks) {
        return new SyncAction(Type.BREAK_BLOCK, pos, timeoutTicks);
    }

    public static SyncAction waitTick(int ticks) {
        SyncAction a = new SyncAction(Type.WAIT_TICK, null, ticks);
        a.tickCounter = 0;
        return a;
    }

    public static SyncAction useItem() {
        return new SyncAction(Type.USE_ITEM, null, 40);
    }

    public static SyncAction containerClick() {
        return new SyncAction(Type.CONTAINER_CLICK, null, 40);
    }

    public Type getType() {
        return this.type;
    }

    public BlockPos getTargetPos() {
        return this.targetPos;
    }

    /**
     * 每 tick 调用一次，返回 true 表示动作完成或超时
     */
    public boolean tick() {
        this.tickCounter++;
        return this.tickCounter >= this.maxTicks;
    }

    public boolean isDone() {
        return this.tickCounter >= this.maxTicks;
    }
}
