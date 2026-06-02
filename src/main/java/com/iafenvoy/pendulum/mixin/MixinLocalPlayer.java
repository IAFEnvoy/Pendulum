package com.iafenvoy.pendulum.mixin;

import com.iafenvoy.pendulum.script.PlayerSimulator;
import com.iafenvoy.pendulum.util.PendulumInput;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * At each LocalPlayer tick start, if PlayerSimulator has active state,
 * replace player.input with custom PendulumInput; restore original KeyboardInput when simulation ends.
 * Referenced from Baritone's InputOverrideHandler.onTick pattern.
 */
@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer {

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickHead(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        PlayerSimulator sim = PlayerSimulator.getInstance();

        if (sim.isActive()) {
            // Script active -> replace with custom Input (save original KeyboardInput for restoration)
            if (!(self.input instanceof PendulumInput)) {
                sim.setOriginalInput(self.input);
                self.input = new PendulumInput();
            }
        } else {
            // Script idle -> restore original KeyboardInput
            Input original = (Input) sim.getOriginalInput();
            if (original != null && self.input instanceof PendulumInput) {
                self.input = original;
                sim.setOriginalInput(null);
            }
        }
    }
}
