package iafenvoy.pendulum.mixins;

import iafenvoy.pendulum.interpreter.util.DataLoader;
import net.minecraft.client.gui.screen.recipebook.RecipeBookResults;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(RecipeBookResults.class)
public class MixinRecipeBookResults {
    @Inject(method = "setResults", at = @At("HEAD"))
    public void onSetResult(List<RecipeResultCollection> list, boolean resetCurrentPage, CallbackInfo ci) {
        for (RecipeResultCollection c : list)
            DataLoader.craftableRecipe.addAll(c.getResults(true));
    }
}
