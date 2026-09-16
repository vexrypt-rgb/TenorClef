package adris.altoclef.multiversion;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.world.World;

import java.util.List;

public class RecipeVer {



    public static ItemStack getOutput(Recipe<?> recipe, World world) {
        //#if MC >= 11904
        return recipe.getResult(world.getRegistryManager());
        //#else
        //$$ return recipe.getOutput();
        //#endif
    }

    public static List<Ingredient> getIngredients(Recipe<?> recipe) {
        //#if MC >= 11605
        return recipe.getIngredients();
        //#else
        //$$ return recipe.getPreviewInputs();
        //#endif
    }


}