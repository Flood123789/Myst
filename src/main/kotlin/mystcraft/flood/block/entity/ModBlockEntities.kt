package mystcraft.flood.block.entity

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.block.ModBlocks
import mystcraft.flood.block.StarFissureBlockEntity
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

object ModBlockEntities {
    
    // 1. The Book Binder (Notice the parenthesis closes right after .build()!)
    val BOOK_BINDER_ENTITY: BlockEntityType<BookBinderBlockEntity> = Registry.register(
        Registries.BLOCK_ENTITY_TYPE,
        Identifier(MystcraftReforged.MOD_ID, "book_binder_entity"),
        FabricBlockEntityTypeBuilder.create(::BookBinderBlockEntity, ModBlocks.BOOK_BINDER).build()
    )

    val CRYSTAL_PORTAL: BlockEntityType<CrystalPortalBlockEntity> = Registry.register(
        Registries.BLOCK_ENTITY_TYPE,
        Identifier(MystcraftReforged.MOD_ID, "crystal_portal_entity"),
        FabricBlockEntityTypeBuilder.create(::CrystalPortalBlockEntity, ModBlocks.CRYSTAL_PORTAL).build()
    )

    val BOOK_RECEPTACLE: BlockEntityType<BookReceptacleBlockEntity> = Registry.register(
        Registries.BLOCK_ENTITY_TYPE,
        Identifier(MystcraftReforged.MOD_ID, "book_receptacle_entity"),
        FabricBlockEntityTypeBuilder.create(::BookReceptacleBlockEntity, ModBlocks.BOOK_RECEPTACLE).build()
    )

    val BOOK_STAND: BlockEntityType<BookStandBlockEntity> = Registry.register(
        Registries.BLOCK_ENTITY_TYPE,
        Identifier(MystcraftReforged.MOD_ID, "bookstand_entity"),
        FabricBlockEntityTypeBuilder.create(::BookStandBlockEntity, ModBlocks.BOOK_STAND).build()
    )

    // 2. Declare the Star Fissure variable completely on its own
    lateinit var STAR_FISSURE: BlockEntityType<StarFissureBlockEntity>

    fun registerBlockEntities() {
        MystcraftReforged.LOGGER.info("Registering Block Entities for ${MystcraftReforged.MOD_ID}")
        MystcraftReforged.LOGGER.info("Registering Block Entities for ${MystcraftReforged.MOD_ID}")
        
        // 3. Assign the Star Fissure value when the mod boots up
        STAR_FISSURE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier(MystcraftReforged.MOD_ID, "star_fissure"),
            FabricBlockEntityTypeBuilder.create(::StarFissureBlockEntity, ModBlocks.STAR_FISSURE).build()
        )
    }
}
