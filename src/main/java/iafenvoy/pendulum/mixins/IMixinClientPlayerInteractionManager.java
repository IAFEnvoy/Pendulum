package iafenvoy.pendulum.mixins;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPlayerInteractionManager.class)
public interface IMixinClientPlayerInteractionManager {
    @Accessor("currentBreakingProgress")
    float getCurrentBreakingProgress();
}
