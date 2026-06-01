package com.iafenvoy.pendulum.script;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 存储由 JS 脚本控制的玩家移动状态，每 tick 由 PendulumClient 同步到 player.input。
 */
public final class PlayerSimulator {
    private static final PlayerSimulator INSTANCE = new PlayerSimulator();

    // 持续移动（每 tick 生效）
    private boolean forward, backward, left, right;
    // 瞬时动作标志（处理完即清除）
    private boolean jump, sneak, sprint, sprintOff;
    // 旋转（每 tick 应用）
    private Float targetYaw, targetPitch;
    // 跳跃模式下持续按住
    private boolean jumpHold;
    // 持续破坏方块
    private BlockPos breakingPos;
    private Direction breakingDir;
    private boolean breaking;
    // 持续使用物品（吃东西/拉弓/举盾）
    private boolean useItem;
    private boolean useItemJustStarted;
    private boolean useItemJustStopped;
    // 保存原始的 KeyboardInput，以便模拟结束后恢复
    private Object originalInput;

    private PlayerSimulator() {
    }

    public static PlayerSimulator getInstance() {
        return INSTANCE;
    }

    // ---- setters (被 JS 调用) ----

    public void setForward(boolean v) {
        this.forward = v;
    }

    public void setBackward(boolean v) {
        this.backward = v;
    }

    public void setLeft(boolean v) {
        this.left = v;
    }

    public void setRight(boolean v) {
        this.right = v;
    }

    public void setJump(boolean v) {
        this.jump = v;
        this.jumpHold = v;
    }

    public void setSneak(boolean v) {
        this.sneak = v;
    }

    public void setSprinting(boolean v) {
        this.sprint = v;
    }

    public void stopSprinting() {
        this.sprintOff = true;
    }

    public void setLookYaw(float yaw) {
        this.targetYaw = yaw;
    }

    public void setLookPitch(float pitch) {
        this.targetPitch = pitch;
    }

    /**
     * 启用持续破坏方块模式
     */
    public void setBreaking(BlockPos pos, Direction dir) {
        this.breakingPos = pos;
        this.breakingDir = dir;
        this.breaking = true;
    }

    /**
     * 停止破坏（不清除移动状态）
     */
    public void stopBreaking() {
        this.breaking = false;
        this.breakingPos = null;
        this.breakingDir = null;
    }

    /**
     * 开始持续使用物品（吃东西 / 拉弓 / 举盾）
     */
    public void startUsingItem() {
        this.useItem = true;
        this.useItemJustStarted = true;
    }

    /**
     * 停止持续使用物品
     */
    public void stopUsingItem() {
        this.useItem = false;
        this.useItemJustStopped = true;
    }

    public void stopAll() {
        this.forward = this.backward = this.left = this.right = false;
        this.jump = false;
        this.jumpHold = false;
        this.sneak = false;
        this.sprint = false;
        this.sprintOff = false;
        this.breaking = false;
        this.breakingPos = null;
        this.useItem = false;
        this.useItemJustStopped = true;
    }

    // ---- getters ----

    public boolean isForward() {
        return this.forward;
    }

    public boolean isBackward() {
        return this.backward;
    }

    public boolean isLeft() {
        return this.left;
    }

    public boolean isRight() {
        return this.right;
    }

    /**
     * 是否有任何模拟活动（移动/破坏/使用物品），用于 Mixin 判断是否替换 input
     */
    public boolean isActive() {
        return this.forward || this.backward || this.left || this.right || this.jumpHold || this.sneak || this.sprint || this.breaking || this.useItem;
    }

    public boolean isSneakHold() {
        return this.sneak;
    }

    public boolean isSprinting() {
        return this.sprint;
    }

    public boolean consumeJump() {
        boolean v = this.jump;
        this.jump = false;
        return v;
    }

    public boolean isJumpHold() {
        return this.jumpHold;
    }

    public boolean consumeSneak() {
        boolean v = this.sneak;
        this.sneak = false;
        return v;
    }

    public boolean consumeSprint() {
        boolean v = this.sprint;
        this.sprint = false;
        return v;
    }

    public boolean consumeSprintOff() {
        boolean v = this.sprintOff;
        this.sprintOff = false;
        return v;
    }

    public Float consumeTargetYaw() {
        Float v = this.targetYaw;
        this.targetYaw = null;
        return v;
    }

    public Float consumeTargetPitch() {
        Float v = this.targetPitch;
        this.targetPitch = null;
        return v;
    }

    /**
     * 是否正在持续破坏方块模式
     */
    public boolean isBreaking() {
        return this.breaking;
    }

    public BlockPos getBreakingPos() {
        return this.breakingPos;
    }

    public Direction getBreakingDir() {
        return this.breakingDir;
    }

    /**
     * Mixin 用：保存/恢复玩家原始的 KeyboardInput
     */
    public void setOriginalInput(Object input) {
        this.originalInput = input;
    }

    public Object getOriginalInput() {
        return this.originalInput;
    }

    // ---- 持续使用物品 getters ----

    public boolean isUsingItem() {
        return this.useItem;
    }

    /**
     * 消费"刚刚开始使用"信号（ScriptEngine tick 读取后重置）
     */
    public boolean consumeUseItemStart() {
        boolean v = this.useItemJustStarted;
        this.useItemJustStarted = false;
        return v;
    }

    /**
     * 消费"刚刚停止使用"信号
     */
    public boolean consumeUseItemStop() {
        boolean v = this.useItemJustStopped;
        this.useItemJustStopped = false;
        return v;
    }
}
