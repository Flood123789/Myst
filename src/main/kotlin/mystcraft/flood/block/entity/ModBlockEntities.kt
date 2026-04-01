package mystcraft.flood.block.entity

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.block.ModBlocks
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

object ModBlockEntities {
    val BOOK_BINDER_ENTITY: BlockEntityType<BookBinderBlockEntity> = Registry.register(
        Registries.BLOCK_ENTITY_TYPE,
        Identifier(MystcraftReforged.MOD_ID, "book_binder_entity"),
        FabricBlockEntityTypeBuilder.create(::BookBinderBlockEntity, ModBlocks.BOOK_BINDER).build()
    )

    fun registerBlockEntities() {
        MystcraftReforged.LOGGER.info("Registering Block Entities for ${MystcraftReforged.MOD_ID}")
    }
}