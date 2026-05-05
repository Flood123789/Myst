package mystcraft.flood.block

import mystcraft.flood.MystcraftReforged
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.fabricmc.fabric.api.`object`.builder.v1.block.FabricBlockSettings
import net.minecraft.block.AbstractBlock
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.block.GlassBlock
import net.minecraft.block.MapColor
import net.minecraft.item.BlockItem
import net.minecraft.item.Item
import net.minecraft.item.ItemGroups
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.sound.BlockSoundGroup
import net.minecraft.util.Identifier

object ModBlocks {
    val BOOK_BINDER = registerBlock("book_binder", BookBinderBlock(FabricBlockSettings.copyOf(Blocks.OAK_PLANKS)))
    val WRITING_DESK = registerBlock("writingdesk", WritingDeskBlock(FabricBlockSettings.copyOf(Blocks.OAK_PLANKS)))
    val EDITING_TABLE = registerBlock("editing_table", EditingTableBlock(FabricBlockSettings.copyOf(Blocks.OAK_PLANKS)))
    val PRINTING_TABLE = registerBlock("printing_table", PrintingTableBlock(FabricBlockSettings.copyOf(Blocks.OAK_PLANKS)))
    val BOOK_STAND = registerBlock("bookstand", BookStandBlock(FabricBlockSettings.copyOf(Blocks.OAK_PLANKS).nonOpaque()))

    val CRYSTAL_BLOCK = registerBlock("crystal_block", GlassBlock(
        AbstractBlock.Settings.create()
            .mapColor(MapColor.DIAMOND_BLUE)
            .strength(1.5f)
            .requiresTool()
            .luminance { 7 }
            .nonOpaque()
            .sounds(BlockSoundGroup.AMETHYST_BLOCK)
    ))

    val BLACK_DECAY = registerBlock("black_decay", DecayBlock(
        AbstractBlock.Settings.create()
            .mapColor(MapColor.BLACK)
            .ticksRandomly()
            .strength(-1.0f, 3600000.0f)
            .dropsNothing()
            .nonOpaque()
    ))

    val WHITE_DECAY = registerBlock("white_decay", WhiteDecayBlock(
        AbstractBlock.Settings.create()
            .mapColor(MapColor.WHITE_GRAY)
            .ticksRandomly()
            .strength(-1.0f, 3600000.0f)
            .dropsNothing()
            .nonOpaque()
    ))
    val WHITE_DECAY_SLAB = registerBlock("white_decay_slab", WhiteDecaySlabBlock(
        AbstractBlock.Settings.create()
            .mapColor(MapColor.WHITE_GRAY)
            .ticksRandomly()
            .strength(-1.0f, 3600000.0f)
            .dropsNothing()
            .nonOpaque()
    ))
    val WHITE_DECAY_STAIRS = registerBlock("white_decay_stairs", WhiteDecayStairsBlock(
        Blocks.QUARTZ_STAIRS.defaultState,
        AbstractBlock.Settings.create()
            .mapColor(MapColor.WHITE_GRAY)
            .ticksRandomly()
            .strength(-1.0f, 3600000.0f)
            .dropsNothing()
            .nonOpaque()
    ))
    val WHITE_DECAY_FENCE = registerBlock("white_decay_fence", WhiteDecayFenceBlock(
        AbstractBlock.Settings.copy(Blocks.OAK_FENCE)
            .mapColor(MapColor.WHITE_GRAY)
            .ticksRandomly()
            .strength(-1.0f, 3600000.0f)
            .dropsNothing()
    ))
    val WHITE_DECAY_PANE = registerBlock("white_decay_pane", WhiteDecayPaneBlock(
        AbstractBlock.Settings.copy(Blocks.GLASS_PANE)
            .mapColor(MapColor.WHITE_GRAY)
            .ticksRandomly()
            .strength(-1.0f, 3600000.0f)
            .dropsNothing()
    ))
    val WHITE_DECAY_TRAPDOOR = registerBlock("white_decay_trapdoor", WhiteDecayTrapdoorBlock(
        AbstractBlock.Settings.copy(Blocks.OAK_TRAPDOOR)
            .mapColor(MapColor.WHITE_GRAY)
            .ticksRandomly()
            .strength(-1.0f, 3600000.0f)
            .dropsNothing(),
        net.minecraft.block.BlockSetType.OAK
    ))
    val WHITE_DECAY_DOOR = registerBlock("white_decay_door", WhiteDecayDoorBlock(
        AbstractBlock.Settings.copy(Blocks.OAK_DOOR)
            .mapColor(MapColor.WHITE_GRAY)
            .ticksRandomly()
            .strength(-1.0f, 3600000.0f)
            .dropsNothing(),
        net.minecraft.block.BlockSetType.OAK
    ))
    val WHITE_DECAY_TORCH = registerBlock("white_decay_torch", WhiteDecayTorchBlock(
        AbstractBlock.Settings.copy(Blocks.TORCH)
            .mapColor(MapColor.WHITE_GRAY)
            .ticksRandomly()
            .strength(-1.0f, 3600000.0f)
            .dropsNothing()
            .luminance { 0 }
    ))
    val WHITE_DECAY_LANTERN = registerBlock("white_decay_lantern", WhiteDecayLanternBlock(
        AbstractBlock.Settings.copy(Blocks.LANTERN)
            .mapColor(MapColor.WHITE_GRAY)
            .ticksRandomly()
            .strength(-1.0f, 3600000.0f)
            .dropsNothing()
            .luminance { 0 }
    ))
    val WHITE_DECAY_WALL_TORCH = registerBlockWithoutItem("white_decay_wall_torch", WhiteDecayWallTorchBlock(
        AbstractBlock.Settings.copy(Blocks.WALL_TORCH)
            .mapColor(MapColor.WHITE_GRAY)
            .ticksRandomly()
            .strength(-1.0f, 3600000.0f)
            .dropsNothing()
            .luminance { 0 }
    ))

    val STAR_FISSURE = registerBlock("star_fissure", StarFissureBlock(
        AbstractBlock.Settings.create()
            .strength(-1.0f, 3600000.0f)
            .dropsNothing()
            .noCollision()
            .nonOpaque()
            .luminance { 15 }
    ))
    val BOOK_RECEPTACLE = registerBlock("book_receptacle", BookReceptacleBlock(
        FabricBlockSettings.copyOf(CRYSTAL_BLOCK)
    ))

    val CRYSTAL_PORTAL = registerBlock("crystal_portal", CrystalPortalBlock(
        AbstractBlock.Settings.create()
            .noCollision()
            .dropsNothing()
            .strength(-1.0f, 0.0f)
            .luminance { 10 }
            .nonOpaque()
    ))

    private fun registerBlock(name: String, block: Block): Block {
        registerBlockItem(name, block)
        return Registry.register(Registries.BLOCK, Identifier(MystcraftReforged.MOD_ID, name), block)
    }

    private fun registerBlockWithoutItem(name: String, block: Block): Block {
        return Registry.register(Registries.BLOCK, Identifier(MystcraftReforged.MOD_ID, name), block)
    }

    private fun registerBlockItem(name: String, block: Block): Item {
        return Registry.register(Registries.ITEM, Identifier(MystcraftReforged.MOD_ID, name), BlockItem(block, Item.Settings()))
    }

    fun registerModBlocks() {
        MystcraftReforged.LOGGER.info("Registering Blocks for ${MystcraftReforged.MOD_ID}")

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register { entries ->
            entries.add(BOOK_BINDER)
            entries.add(WRITING_DESK)
            entries.add(EDITING_TABLE)
            entries.add(PRINTING_TABLE)
            entries.add(BOOK_STAND)
            entries.add(CRYSTAL_BLOCK)
            entries.add(BOOK_RECEPTACLE)
        }
    }
}
