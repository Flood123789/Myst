package mystcraft.flood.recipe

import mystcraft.flood.MystcraftReforged
import net.minecraft.recipe.RecipeSerializer
import net.minecraft.recipe.SpecialRecipeSerializer
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

object ModRecipes {
    val BLOCK_INK_VIAL: RecipeSerializer<BlockInkVialRecipe> = Registry.register(
        Registries.RECIPE_SERIALIZER,
        Identifier(MystcraftReforged.MOD_ID, "block_ink_vial"),
        SpecialRecipeSerializer(::BlockInkVialRecipe)
    )

    fun register() {
        MystcraftReforged.LOGGER.info("Registering recipes for ${MystcraftReforged.MOD_ID}")
    }
}
