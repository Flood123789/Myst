package mystcraft.flood.item

import mystcraft.flood.MystcraftReforged
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.item.Item
import net.minecraft.item.ItemGroups
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

object ModItems {
    // === Core Crafting Ingredients ===
    val LINK_PANEL = register("linkpanel", Item(Item.Settings()))
    val INK_VIAL = register("ink_vial", Item(Item.Settings()))
    val PAGE = register("page", Item(Item.Settings())) 
    
    // === The Dynamic Symbol Base ===
    val SYMBOL_PAGE = register("symbol_page", SymbolPageItem(Item.Settings()))
    val LOST_PAGE = Registry.register(Registries.ITEM, Identifier(MystcraftReforged.MOD_ID, "lost_page"), LostPageItem(Item.Settings()))

    // === The Books ===
    val GUIDE_BOOK = register("guide_book", GuideBookItem(Item.Settings().maxCount(1)))
    val DESCRIPTIVE_BOOK = register("agebook", DescriptiveBookItem(Item.Settings().maxCount(1)))
    val LINKING_BOOK = register("linkingbook", LinkingBookItem(Item.Settings().maxCount(1)))

    private fun <T : Item> register(name: String, item: T): T {
        return Registry.register(Registries.ITEM, Identifier(MystcraftReforged.MOD_ID, name), item)
    }

    fun registerModItems() {
        MystcraftReforged.LOGGER.info("Registering items for ${MystcraftReforged.MOD_ID}")

        // Inject ingredients into the Creative Menu
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register { entries ->
            entries.add(LINK_PANEL)
            entries.add(INK_VIAL)
            entries.add(PAGE)
            
            // Note: We don't add SYMBOL_PAGE here directly. 
            // Your ModSymbols.kt scanner will add all the dynamic NBT variants of it!
        }

        // Inject books into the Tools Creative Menu
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register { entries ->
            entries.add(GUIDE_BOOK)
            entries.add(DESCRIPTIVE_BOOK)
            entries.add(LINKING_BOOK)
        }
    }
}
