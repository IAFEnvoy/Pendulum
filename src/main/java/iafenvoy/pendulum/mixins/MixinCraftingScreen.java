package iafenvoy.pendulum.mixins;

import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CraftingScreen.class)
public abstract class MixinCraftingScreen extends HandledScreen<CraftingScreenHandler> {
    public MixinCraftingScreen(CraftingScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }
}
