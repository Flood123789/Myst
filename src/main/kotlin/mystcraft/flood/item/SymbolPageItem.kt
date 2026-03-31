package mystcraft.flood.item

import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.util.Identifier

class SymbolPageItem(settings: Settings) : Item(settings) {
    override fun getName(stack: ItemStack): Text {
        val symbolId = stack.nbt?.getString("Symbol") ?: return super.getName(stack)
        // Clean up the name for the tooltip
        return Text.literal("Symbol Page: ${symbolId.split(":").last().replace("_", " ").replaceFirstChar { it.uppercase() }}")
    }
    
    companion object {
        fun createStack(symbolId: Identifier): ItemStack {
            val stack = ItemStack(ModItems.SYMBOL_PAGE)
            stack.orCreateNbt.putString("Symbol", symbolId.toString())
            return stack
        }
    }
}