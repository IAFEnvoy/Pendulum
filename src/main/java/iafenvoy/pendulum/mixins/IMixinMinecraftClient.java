package iafenvoy.pendulum.mixins;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftClient.class)
public interface IMixinMinecraftClient {
    @Accessor("itemUseCooldown")
    int getItemUseCooldown();

    @Invoker("doItemUse")
    void invokeDoItemUse();

    @Invoker("handleBlockBreaking")
    void invokeHandleBlockBreaking(boolean doBreak);

    @Accessor("attackCooldown")
    void setAttackCooldown(int attackCooldown);
}
