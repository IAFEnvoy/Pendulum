package iafenvoy.pendulum.interpreter.util;

import net.minecraft.recipe.Recipe;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class DataLoader {
    public static final List<Recipe<?>> craftableRecipe = new ArrayList<>();
    public static int sleepDelta = 500;
    public static BlockPos currentPos = null;
    public static Callback callback = null;
}
