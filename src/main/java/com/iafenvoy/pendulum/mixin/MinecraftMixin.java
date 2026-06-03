package com.iafenvoy.pendulum.mixin;

//? !forge {
import com.iafenvoy.pendulum.script.ScriptEngine;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//FIXME::Mixin not available on forge side
@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "createTitle", at = @At(value = "INVOKE", target = "Ljava/lang/StringBuilder;toString()Ljava/lang/String;"))
    private void appendTitle(CallbackInfoReturnable<String> cir, @Local StringBuilder sb) {
        ScriptEngine.getInstance().updateWindowTitle(sb);
    }
}
