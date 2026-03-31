package mystcraft.flood.item

import mystcraft.flood.MystcraftReforged
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

object ModItems {
    // I am updating these string IDs to match your .png filenames exactly
    val LINK_PANEL = register("linkpanel", Item(Item.Settings())) // Assuming you have linkpanel.png
    val INK_VIAL = register("ink_vial", Item(Item.Settings())) // I saw this in your screenshot
    val PAGE = register("page", Item(Item.Settings())) // I assume this is the one that loaded
    
    val SYMBOL_PAGE = register("symbol_page", SymbolPageItem(Item.Settings()))

    // Legacy Mystcraft called these agebook and linkingbook
    val DESCRIPTIVE_BOOK = register("agebook", DescriptiveBookItem(Item.Settings().maxCount(1)))
    val LINKING_BOOK = register("linkingbook", LinkingBookItem(Item.Settings().maxCount(1)))

    private fun <T : Item> register(name: String, item: T): T {
        return Registry.register(Registries.ITEM, Identifier(MystcraftReforged.MOD_ID, name), item)
    }

    fun registerModItems() {
        MystcraftReforged.LOGGER.info("Registering items for ${MystcraftReforged.MOD_ID}")
    }
}