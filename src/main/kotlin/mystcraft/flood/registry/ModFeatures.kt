package mystcraft.flood.registry

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.*
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature

object ModFeatures {

    // 1. Define the features once. 
    // MAKE SURE these lines do NOT have "Registry.register" on them!
    val DENSE_ORES: Feature<DefaultFeatureConfig> = DenseOresFeature(DefaultFeatureConfig.CODEC)
    val ABANDONED_ARCHIVE: Feature<DefaultFeatureConfig> = AbandonedArchiveFeature(DefaultFeatureConfig.CODEC)
    val STAR_FISSURE: Feature<DefaultFeatureConfig> = StarFissureFeature(DefaultFeatureConfig.CODEC)
    val GIANT_TREE: Feature<DefaultFeatureConfig> = GiantTreeFeature(DefaultFeatureConfig.CODEC)
    val CRYSTAL_FORMATIONS: Feature<DefaultFeatureConfig> = CrystalFormationFeature(DefaultFeatureConfig.CODEC)

    private var hasRegistered = false

    fun register() {
        // Safety check: if already registered, stop here!
        if (hasRegistered) return
        hasRegistered = true

        Registry.register(Registries.FEATURE, Identifier(MystcraftReforged.MOD_ID, "dense_ores"), DENSE_ORES)
        Registry.register(Registries.FEATURE, Identifier(MystcraftReforged.MOD_ID, "abandoned_archive"), ABANDONED_ARCHIVE)
        Registry.register(Registries.FEATURE, Identifier(MystcraftReforged.MOD_ID, "star_fissure"), STAR_FISSURE)
        Registry.register(Registries.FEATURE, Identifier(MystcraftReforged.MOD_ID, "giant_tree"), GIANT_TREE)
        Registry.register(Registries.FEATURE, Identifier(MystcraftReforged.MOD_ID, "crystal_formations"), CRYSTAL_FORMATIONS)

        MystcraftReforged.LOGGER.info("Successfully registered all worldgen features.")
    }
}