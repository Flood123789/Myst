package mystcraft.flood.item

import mystcraft.flood.MystcraftReforged
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.minecraft.item.ItemGroup
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.text.Text
import net.minecraft.util.Identifier

object ModItemGroups {
    val MYSTCRAFT_GROUP: ItemGroup = Registry.register(
        Registries.ITEM_GROUP,
        Identifier(MystcraftReforged.MOD_ID, "mystcraft_group"),
        FabricItemGroup.builder()
            .displayName(Text.translatable("itemgroup.mystcraft-reforged.mystcraft_group"))
            .icon { ItemStack(ModItems.LINKING_BOOK) } // This will load the icon now
            .entries { _, entries ->
                entries.add(ModItems.INK_VIAL)
                entries.add(ModItems.LINK_PANEL)
                entries.add(ModItems.PAGE)
                entries.add(ModItems.SYMBOL_PAGE)
                entries.add(ModItems.DESCRIPTIVE_BOOK)
                entries.add(ModItems.LINKING_BOOK)
            }
            .build()
    )

    fun registerItemGroups() {}
}