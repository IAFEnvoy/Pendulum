package iafenvoy.pendulum.mixins;

import iafenvoy.pendulum.interpreter.util.DataLoader;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class MixinClientPlayNetworkHandler {
    @Inject(method = "onBlockUpdate", at = @At("RETURN"))
    public void blockUpdate(BlockUpdateS2CPacket packet, CallbackInfo ci) {
        if (DataLoader.currentPos == null) return;
        if (packet.getPos().equals(DataLoader.currentPos))
            DataLoader.currentPos = null;
    }
}
