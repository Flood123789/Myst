package mystcraft.flood.entity

import mystcraft.flood.MystcraftReforged
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.EntityType
import net.minecraft.entity.SpawnGroup
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

object ModEntities {
    val DESCRIPTIVE_BOOK_ANCHOR: EntityType<DescriptiveBookEntity> = Registry.register(
        Registries.ENTITY_TYPE,
        Identifier(MystcraftReforged.MOD_ID, "descriptive_book_anchor"),
        FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::DescriptiveBookEntity)
            .dimensions(EntityDimensions.fixed(0.5f, 0.65f))
            .trackRangeBlocks(32)
            .trackedUpdateRate(10)
            .build()
    )

    fun register() = Unit
}
