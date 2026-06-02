package com.iafenvoy.pendulum.script;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Stores player movement state controlled by JS scripts, synced to player.input each tick.
 */
public final class PlayerSimulator {
    private static final PlayerSimulator INSTANCE = new PlayerSimulator();

    // Continuous movement (applied each tick)
    private boolean forward, backward, left, right;
    // Instant action flags (cleared after processing)
    private boolean jump, sneak, sprint, sprintOff;
    // Rotation (applied each tick)
    private Float targetYaw, targetPitch;
    // Held while in jump mode
    private boolean jumpHold;
    // Continuous block breaking
    private BlockPos breakingPos;
    private Direction breakingDir;
    private boolean breaking;
    // Continuous item use (eating/bow/shield)
    private boolean useItem;
    private boolean useItemJustStarted;
    private boolean useItemJustStopped;
    // Save original KeyboardInput for restoration after simulation ends
    private Object originalInput;

    private PlayerSimulator() {
    }

    public static PlayerSimulator getInstance() {
        return INSTANCE;
    }

    // ---- setters (called by JS) ----

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
     * Enable continuous block breaking mode
     */
    public void setBreaking(BlockPos pos, Direction dir) {
        this.breakingPos = pos;
        this.breakingDir = dir;
        this.breaking = true;
    }

    /**
     * Stop breaking (does not clear movement state)
     */
    public void stopBreaking() {
        this.breaking = false;
        this.breakingPos = null;
        this.breakingDir = null;
    }

    /**
     * Start continuous item use (eating / bow / shield)
     */
    public void startUsingItem() {
        this.useItem = true;
        this.useItemJustStarted = true;
    }

    /**
     * Stop continuous item use
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
     * Whether any simulation is active (movement/breaking/item use), used by Mixin to decide input override.
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
     * Whether in continuous block breaking mode.
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
     * For Mixin: save/restore players original KeyboardInput.
     */
    public void setOriginalInput(Object input) {
        this.originalInput = input;
    }

    public Object getOriginalInput() {
        return this.originalInput;
    }

    // ---- continuous item use getters ----

    public boolean isUsingItem() {
        return this.useItem;
    }

    /**
     * Consume start-use signal (read by ScriptEngine tick then reset).
     */
    public boolean consumeUseItemStart() {
        boolean v = this.useItemJustStarted;
        this.useItemJustStarted = false;
        return v;
    }

    /**
     * Consume stop-use signal.
     */
    public boolean consumeUseItemStop() {
        boolean v = this.useItemJustStopped;
        this.useItemJustStopped = false;
        return v;
    }
}
