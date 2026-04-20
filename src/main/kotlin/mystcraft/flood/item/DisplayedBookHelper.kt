package mystcraft.flood.item

import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.world.World

object DisplayedBookHelper {
    fun isDisplayableBook(stack: ItemStack): Boolean {
        if (stack.item === ModItems.DESCRIPTIVE_BOOK || stack.item === ModItems.LINKING_BOOK) {
            return true
        }

        val nbt = stack.nbt ?: return false
        return nbt.contains("Age_ID") || nbt.contains("Dimension")
    }

    fun activate(world: World, player: ServerPlayerEntity, stack: ItemStack) {
        when (val item = stack.item) {
            is DescriptiveBookItem -> item.activate(world, player, stack)
            is LinkingBookItem -> item.activate(world, player, stack)
            else -> {
                val nbt = stack.nbt ?: return
                when {
                    nbt.contains("Age_ID") -> (ModItems.DESCRIPTIVE_BOOK as DescriptiveBookItem).activate(world, player, stack)
                    nbt.contains("Dimension") -> (ModItems.LINKING_BOOK as LinkingBookItem).activate(world, player, stack)
                }
            }
        }
    }

    fun getDisplayName(stack: ItemStack): Text? {
        if (!isDisplayableBook(stack)) return null

        if (stack.hasCustomName()) {
            return stack.name
        }

        val nbt = stack.nbt ?: return stack.name
        return when {
            nbt.contains("Age_Name") && nbt.getString("Age_Name").isNotBlank() -> Text.literal(nbt.getString("Age_Name"))
            nbt.contains("Age_ID") -> {
                val raw = nbt.getString("Age_ID")
                val path = Identifier.tryParse(raw)?.path ?: raw
                Text.literal(path)
            }
            nbt.contains("Dimension") -> {
                val raw = nbt.getString("Dimension")
                val path = Identifier.tryParse(raw)?.path ?: raw
                Text.literal(path)
            }
            else -> stack.name
        }
    }
}
