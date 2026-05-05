package mystcraft.flood.item

import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.world.World

object DisplayedBookHelper {
    fun applyAgeBookName(stack: ItemStack, rawName: String) {
        val trimmed = rawName.trim().take(64)
        if (trimmed.isBlank()) {
            stack.nbt?.remove("Age_Name")
            stack.removeCustomName()
            if (stack.nbt?.isEmpty == true) {
                stack.nbt = null
            }
            return
        }

        stack.orCreateNbt.putString("Age_Name", trimmed)
        stack.setCustomName(Text.literal(trimmed))
    }

    fun isDisplayableBook(stack: ItemStack): Boolean {
        if (stack.item === ModItems.DESCRIPTIVE_BOOK || stack.item === ModItems.LINKING_BOOK) {
            return true
        }

        val nbt = stack.nbt ?: return false
        return nbt.contains("Age_ID") || nbt.contains("Dimension")
    }

    fun isDescriptiveBook(stack: ItemStack): Boolean {
        if (stack.item === ModItems.DESCRIPTIVE_BOOK) {
            return true
        }
        return stack.nbt?.contains("Age_ID") == true
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

        val nbt = stack.nbt ?: return stack.name
        return when {
            nbt.contains("Age_Name") && nbt.getString("Age_Name").isNotBlank() -> Text.literal(nbt.getString("Age_Name"))
            stack.hasCustomName() -> stack.name
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

    fun getAgeBookName(stack: ItemStack): String =
        when {
            stack.nbt?.contains("Age_Name") == true -> stack.nbt!!.getString("Age_Name")
            stack.hasCustomName() -> stack.name.string
            else -> ""
        }
}
