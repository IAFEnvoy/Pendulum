package com.iafenvoy.pendulum.util;

import com.iafenvoy.pendulum.script.PlayerSimulator;
import net.minecraft.client.player.Input;

/**
 * Custom Input implementation, based on Baritone's PlayerMovementInput.
 * When MC calls input.tick() each tick, it reads PlayerSimulator state directly,
 * solving the issue of START_CLIENT_TICK firing too early causing forwardImpulse overwrite.
 */
public class PendulumInput extends Input {

    public PendulumInput() {
        // Input uses no-arg constructor
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

        // Sprint: driven through sprint field
        if (sim.isSprinting()) {
            this.forwardImpulse *= 1.3F; // Slightly faster when sprinting
        }
    }
}
