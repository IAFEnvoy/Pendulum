package iafenvoy.pendulum.mixins;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ScreenHandler.class)
public class DebugScreenHandler {
    @Inject(method = "onSlotClick(IILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)Lnet/minecraft/item/ItemStack;",at=@At("HEAD"))
    public void clickSlot(int i, int j, SlotActionType actionType, PlayerEntity playerEntity, CallbackInfoReturnable<ItemStack> cir){
        System.out.println(i);
        System.out.println(j);
        System.out.println(actionType);
    }
}
