package mystcraft.flood.block

import mystcraft.flood.MystcraftReforged
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.fabricmc.fabric.api.`object`.builder.v1.block.FabricBlockSettings
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.item.BlockItem
import net.minecraft.item.Item
import net.minecraft.item.ItemGroups
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

object ModBlocks {
    // Creates the block, copying the strength/sounds of a standard Wood block
    val BOOK_BINDER = registerBlock("book_binder", BookBinderBlock(FabricBlockSettings.copyOf(Blocks.OAK_PLANKS)))

    private fun registerBlock(name: String, block: Block): Block {
        registerBlockItem(name, block)
        return Registry.register(Registries.BLOCK, Identifier(MystcraftReforged.MOD_ID, name), block)
    }

    private fun registerBlockItem(name: String, block: Block): Item {
        return Registry.register(Registries.ITEM, Identifier(MystcraftReforged.MOD_ID, name), BlockItem(block, Item.Settings()))
    }

    fun registerModBlocks() {
        MystcraftReforged.LOGGER.info("Registering Blocks for ${MystcraftReforged.MOD_ID}")
        
        // Add it to your custom Mystcraft tab or standard Functional tab
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register { entries ->
            entries.add(BOOK_BINDER)
        }
    }
}