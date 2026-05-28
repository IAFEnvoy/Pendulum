package com.iafenvoy.pendulum.util;

import com.iafenvoy.pendulum.script.PlayerSimulator;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;

/**
 * 自定义 Input 实现，参考 Baritone 的 PlayerMovementInput。
 * MC 每 tick 调用 input.tick() 时直接从这里读取 PlayerSimulator 的状态，
 * 解决 START_CLIENT_TICK 时序过早导致 forwardImpulse 被覆盖的问题。
 */
public class PendulumInput extends Input {

    public PendulumInput() {
        // Input 是无参构造
    }

    @Override
    public void tick(boolean isSneaking, float f) {
        PlayerSimulator sim = PlayerSimulator.getInstance();

        this.forwardImpulse = 0.0F;
        this.leftImpulse = 0.0F;

        if (sim.isForward()) this.forwardImpulse = 1.0F;
        if (sim.isBackward()) this.forwardImpulse -= 1.0F;
        if (sim.isLeft()) this.leftImpulse = 1.0F;
        if (sim.isRight()) this.leftImpulse -= 1.0F;

        this.jumping = sim.isJumpHold();
        this.shiftKeyDown = sim.isSneakHold();

        // 冲刺：通过 sprint 字段驱动
        if (sim.isSprinting()) {
            this.forwardImpulse *= 1.3F; // 冲刺时略微加速
        }
    }
}
