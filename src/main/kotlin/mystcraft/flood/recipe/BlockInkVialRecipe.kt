package mystcraft.flood.recipe

import mystcraft.flood.block.ModBlocks
import mystcraft.flood.item.CrystalPaint
import mystcraft.flood.item.ModItems
import net.minecraft.inventory.RecipeInputInventory
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemStack
import net.minecraft.recipe.RecipeSerializer
import net.minecraft.recipe.SpecialCraftingRecipe
import net.minecraft.recipe.book.CraftingRecipeCategory
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.EmptyBlockView
import net.minecraft.world.World

class BlockInkVialRecipe(id: Identifier, category: CraftingRecipeCategory) : SpecialCraftingRecipe(id, category) {
    override fun matches(inventory: RecipeInputInventory, world: World): Boolean =
        findIngredients(inventory) != null

    override fun craft(inventory: RecipeInputInventory, registryManager: DynamicRegistryManager): ItemStack {
        val blockItem = findIngredients(inventory) ?: return ItemStack.EMPTY
        val state = blockItem.block.defaultState
        return ItemStack(ModItems.BLOCK_INK_VIAL).also { result ->
            CrystalPaint.setSample(
                result,
                state,
                state.getMapColor(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).color
            )
        }
    }

    override fun fits(width: Int, height: Int): Boolean = width * height >= 2

    override fun getSerializer(): RecipeSerializer<*> = ModRecipes.BLOCK_INK_VIAL

    private fun findIngredients(inventory: RecipeInputInventory): BlockItem? {
        var vialFound = false
        var blockItem: BlockItem? = null

        for (slot in 0 until inventory.size()) {
            val stack = inventory.getStack(slot)
            if (stack.isEmpty) continue
            when {
                stack.isOf(ModItems.INK_VIAL) && !vialFound -> vialFound = true
                stack.item is BlockItem && blockItem == null -> blockItem = stack.item as BlockItem
                else -> return null
            }
        }

        val candidate = blockItem ?: return null
        val state = candidate.block.defaultState
        if (!vialFound || !CrystalPaint.canSample(state)) return null
        if (state.isOf(ModBlocks.CRYSTAL_BLOCK) ||
            state.isOf(ModBlocks.BOOK_RECEPTACLE) ||
            state.isOf(ModBlocks.CRYSTAL_PORTAL)
        ) return null
        return candidate
    }
}
