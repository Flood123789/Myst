package mystcraft.flood.entity

import mystcraft.flood.MystcraftReforged
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
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

    /**
     * Registered at the greater form's size; the lesser variant narrows its own hitbox through
     * ParadoxReaperEntity.getDimensions. The update rate is faster than a normal mob because the
     * client rig plants its feet against interpolated positions, and stale movement data shows
     * up immediately as sliding legs.
     */
    val PARADOX_REAPER: EntityType<ParadoxReaperEntity> = Registry.register(
        Registries.ENTITY_TYPE,
        Identifier(MystcraftReforged.MOD_ID, "paradox_reaper"),
        FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, ::ParadoxReaperEntity)
            .dimensions(ParadoxReaperEntity.GREATER_DIMENSIONS)
            .trackRangeBlocks(48)
            .trackedUpdateRate(2)
            .build()
    )

    val LITTLE_ANOMALY: EntityType<LittleAnomalyEntity> = Registry.register(
        Registries.ENTITY_TYPE,
        Identifier(MystcraftReforged.MOD_ID, "little_anomaly"),
        FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, ::LittleAnomalyEntity)
            // Keep interaction/collision proportional to the reduced familiar render instead of
            // leaving an invisible full-size lesser-Reaper box around the smaller companion.
            .dimensions(EntityDimensions.fixed(0.18f, 0.14f))
            .trackRangeBlocks(32)
            .trackedUpdateRate(3)
            .build()
    )

    fun register() {
        FabricDefaultAttributeRegistry.register(PARADOX_REAPER, ParadoxReaperEntity.createReaperAttributes())
        FabricDefaultAttributeRegistry.register(LITTLE_ANOMALY, LittleAnomalyEntity.createAttributes())
    }
}
