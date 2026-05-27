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

    private PlayerSimulator() {}

    public static PlayerSimulator getInstance() {
        return INSTANCE;
    }

    // ---- setters (被 JS 调用) ----

    public void setForward(boolean v) { this.forward = v; }
    public void setBackward(boolean v) { this.backward = v; }
    public void setLeft(boolean v) { this.left = v; }
    public void setRight(boolean v) { this.right = v; }
    public void setJump(boolean v) { this.jump = v; this.jumpHold = v; }
    public void setSneak(boolean v) { this.sneak = v; }
    public void setSprinting(boolean v) { this.sprint = v; }
    public void stopSprinting() { this.sprintOff = true; }
    public void setLookYaw(float yaw) { this.targetYaw = yaw; }
    public void setLookPitch(float pitch) { this.targetPitch = pitch; }

    /** 启用持续破坏方块模式 */
    public void setBreaking(BlockPos pos, Direction dir) {
        this.breakingPos = pos;
        this.breakingDir = dir;
        this.breaking = true;
    }

    /** 停止破坏（不清除移动状态） */
    public void stopBreaking() {
        this.breaking = false;
        this.breakingPos = null;
        this.breakingDir = null;
    }

    public void stopAll() {
        forward = backward = left = right = false;
        jump = false;
        jumpHold = false;
        sneak = false;
        sprint = false;
        sprintOff = false;
        breaking = false;
        breakingPos = null;
    }

    // ---- getters ----

    public boolean isForward() { return forward; }
    public boolean isBackward() { return backward; }
    public boolean isLeft() { return left; }
    public boolean isRight() { return right; }

    public boolean consumeJump() {
        boolean v = jump;
        jump = false;
        return v;
    }
    public boolean isJumpHold() { return jumpHold; }
    public boolean consumeSneak() {
        boolean v = sneak;
        sneak = false;
        return v;
    }
    public boolean consumeSprint() {
        boolean v = sprint;
        sprint = false;
        return v;
    }
    public boolean consumeSprintOff() {
        boolean v = sprintOff;
        sprintOff = false;
        return v;
    }
    public Float consumeTargetYaw() {
        Float v = targetYaw;
        targetYaw = null;
        return v;
    }
    public Float consumeTargetPitch() {
        Float v = targetPitch;
        targetPitch = null;
        return v;
    }

    /** 是否正在持续破坏方块模式 */
    public boolean isBreaking() { return breaking; }
    public BlockPos getBreakingPos() { return breakingPos; }
    public Direction getBreakingDir() { return breakingDir; }
}
