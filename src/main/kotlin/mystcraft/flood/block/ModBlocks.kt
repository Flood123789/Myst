package mystcraft.flood.block

import mystcraft.flood.MystcraftReforged
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.fabricmc.fabric.api.`object`.builder.v1.block.FabricBlockSettings
import net.minecraft.block.AbstractBlock
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.block.GlassBlock // ADDED
import net.minecraft.block.MapColor
import net.minecraft.item.BlockItem
import net.minecraft.item.Item
import net.minecraft.item.ItemGroups
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.sound.BlockSoundGroup // ADDED
import net.minecraft.util.Identifier

object ModBlocks {
    
    // Standard Mystcraft Book Binder
    val BOOK_BINDER = registerBlock("book_binder", BookBinderBlock(FabricBlockSettings.copyOf(Blocks.OAK_PLANKS)))

    // NEW: The Crystal Block (Translucent, glows, sounds like Amethyst)
    val CRYSTAL_BLOCK = registerBlock("crystal_block", GlassBlock(
        AbstractBlock.Settings.create()
            .mapColor(MapColor.DIAMOND_BLUE) // A nice mystical cyan/blue
            .strength(1.5f)
            .luminance { 7 } // Soft magical glow
            .nonOpaque()     // Essential for glass-type rendering
            .sounds(BlockSoundGroup.AMETHYST_BLOCK) // That nice crystal "chime" sound
    ))
    
    // Instability Decay
    val BLACK_DECAY = registerBlock("black_decay", DecayBlock(
        AbstractBlock.Settings.create()
            .mapColor(MapColor.BLACK)
            .ticksRandomly() 
            .strength(-1.0f, 3600000.0f) 
            .dropsNothing()
            .nonOpaque()
    ))

    // The Star Fissure (End Portal visual target)
    val STAR_FISSURE = registerBlock("star_fissure", StarFissureBlock(
        AbstractBlock.Settings.create()
            .strength(-1.0f, 3600000.0f)
            .dropsNothing()
            .noCollision()
            .nonOpaque()
            .luminance { 15 }
    ))
    val BOOK_RECEPTACLE = registerBlock("book_receptacle", BookReceptacleBlock(
        AbstractBlock.Settings.create().strength(2.0f).nonOpaque()
    ))
    
    val CRYSTAL_PORTAL = registerBlock("crystal_portal", CrystalPortalBlock(
        AbstractBlock.Settings.create()
            .noCollision()
            .dropsNothing()
            // Hardness -1.0f = Unbreakable by mining. Resistance 0.0f = Explodes instantly.
            .strength(-1.0f, 0.0f) 
            .luminance { 10 }
            .nonOpaque()
    ))

    private fun registerBlock(name: String, block: Block): Block {
        registerBlockItem(name, block)
        return Registry.register(Registries.BLOCK, Identifier(MystcraftReforged.MOD_ID, name), block)
    }

    private fun registerBlockItem(name: String, block: Block): Item {
        return Registry.register(Registries.ITEM, Identifier(MystcraftReforged.MOD_ID, name), BlockItem(block, Item.Settings()))
    }

    fun registerModBlocks() {
        MystcraftReforged.LOGGER.info("Registering Blocks for ${MystcraftReforged.MOD_ID}")
        
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register { entries ->
            entries.add(BOOK_BINDER)
            entries.add(CRYSTAL_BLOCK) // Added to the creative tab
        }
    }
}