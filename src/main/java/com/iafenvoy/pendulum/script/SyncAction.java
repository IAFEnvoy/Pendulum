package com.iafenvoy.pendulum.script;

import net.minecraft.core.BlockPos;

/**
 * Synchronous operations that require waiting across ticks.
 * Long-running methods in MinecraftAPI set this object; ScriptEngine checks completion each tick.
 */
public final class SyncAction {
    public enum Type {
        /**
         * Block breaking - wait for target to become air
         */
        BREAK_BLOCK,
        /**
         * Wait 1 tick for server to process
         */
        WAIT_TICK,
        /**
         * Wait 1 tick after block/item interaction
         */
        USE_ITEM,
        /**
         * Wait 1 tick after crafting/slot click
         */
        CONTAINER_CLICK
    }

    private final Type type;
    private final BlockPos targetPos;
    private int tickCounter;
    private final int maxTicks; // timeout fallback

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
     * Called each tick; returns true when action completes or times out.
     */
    public boolean tick() {
        this.tickCounter++;
        return this.tickCounter >= this.maxTicks;
    }

    public boolean isDone() {
        return this.tickCounter >= this.maxTicks;
    }
}
