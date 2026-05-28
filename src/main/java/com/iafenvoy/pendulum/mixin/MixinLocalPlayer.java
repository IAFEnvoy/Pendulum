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
 * 在 LocalPlayer 每 tick 开始时，如果 PlayerSimulator 有活动状态，
 * 就将 player.input 替换为自定义的 PendulumInput；模拟结束时恢复原始的 KeyboardInput。
 * 参考 Baritone 的 InputOverrideHandler.onTick 模式。
 */
@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer {

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickHead(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        PlayerSimulator sim = PlayerSimulator.getInstance();

        if (sim.isActive()) {
            // 脚本活跃 → 替换为自定义 Input（保存原始 KeyboardInput 以便恢复）
            if (!(self.input instanceof PendulumInput)) {
                sim.setOriginalInput(self.input);
                self.input = new PendulumInput();
            }
        } else {
            // 脚本空闲 → 恢复原始的 KeyboardInput
            Input original = (Input) sim.getOriginalInput();
            if (original != null && self.input instanceof PendulumInput) {
                self.input = original;
                sim.setOriginalInput(null);
            }
        }
    }
}
