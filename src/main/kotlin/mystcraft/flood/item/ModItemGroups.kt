package mystcraft.flood.item

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.block.ModBlocks // THE FIX: Imported ModBlocks!
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.minecraft.item.ItemGroup
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.text.Text
import net.minecraft.util.Identifier

object ModItemGroups {
    // === 1. Main Mystcraft Tab (Tools & Ingredients) ===
    val MYSTCRAFT_GROUP: ItemGroup = Registry.register(
        Registries.ITEM_GROUP,
        Identifier(MystcraftReforged.MOD_ID, "mystcraft_group"),
        FabricItemGroup.builder()
            .displayName(Text.translatable("itemgroup.mystcraft-reforged.mystcraft_group"))
            .icon { ItemStack(ModItems.LINKING_BOOK) }
            .entries { _, entries ->
                entries.add(ModItems.INK_VIAL)
                entries.add(ModItems.LINK_PANEL)
                entries.add(ModItems.PAGE)
                entries.add(ModItems.LOST_PAGE)
                entries.add(ModItems.GUIDE_BOOK)
                entries.add(ModItems.DESCRIPTIVE_BOOK)
                entries.add(ModItems.LINKING_BOOK)
                entries.add(ModItems.NOTEBOOK)
                entries.add(ModBlocks.BOOK_BINDER)
                entries.add(ModBlocks.WRITING_DESK)
                entries.add(ModBlocks.EDITING_TABLE)
                entries.add(ModBlocks.PRINTING_TABLE)
                entries.add(ModBlocks.BOOK_STAND)
                entries.add(ModBlocks.BOOK_RECEPTACLE)
                entries.add(ModBlocks.CRYSTAL_BLOCK)
                entries.add(ModBlocks.CRYSTAL_PORTAL)
                entries.add(ModBlocks.STAR_FISSURE)
                entries.add(ModBlocks.BLACK_DECAY)
                entries.add(ModBlocks.WHITE_DECAY)
            }
            .build()
    )

    // === 2. Dedicated Symbol Pages Tab ===
    // We create a RegistryKey so our dynamic scanner can find this specific tab later
    val MYSTCRAFT_PAGES_KEY: RegistryKey<ItemGroup> = RegistryKey.of(
        RegistryKeys.ITEM_GROUP,
        Identifier(MystcraftReforged.MOD_ID, "mystcraft_pages")
    )

    val MYSTCRAFT_PAGES_GROUP: ItemGroup = Registry.register(
        Registries.ITEM_GROUP,
        MYSTCRAFT_PAGES_KEY.value,
        FabricItemGroup.builder()
            .displayName(Text.translatable("itemgroup.mystcraft-reforged.mystcraft_pages"))
            .icon { ItemStack(ModItems.PAGE) } // Using the blank page as the tab icon
            // Notice there is no .entries block here. 
            // The scanner will populate this tab dynamically after all mods load!
            .build()
    )

    val MYSTCRAFT_EFFECTS_KEY: RegistryKey<ItemGroup> = RegistryKey.of(
        RegistryKeys.ITEM_GROUP,
        Identifier(MystcraftReforged.MOD_ID, "mystcraft_effect_pages")
    )

    val MYSTCRAFT_EFFECTS_GROUP: ItemGroup = Registry.register(
        Registries.ITEM_GROUP,
        MYSTCRAFT_EFFECTS_KEY.value,
        FabricItemGroup.builder()
            .displayName(Text.translatable("itemgroup.mystcraft-reforged.mystcraft_effect_pages"))
            .icon { SymbolPageItem.createStack(Identifier(MystcraftReforged.MOD_ID, "age_effect")) }
            .build()
    )

    fun registerItemGroups() {
        // Simply calling this function triggers the object initialization above
        MystcraftReforged.LOGGER.info("Registering Item Groups for ${MystcraftReforged.MOD_ID}")
    }
}
