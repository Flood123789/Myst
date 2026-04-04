package mystcraft.flood.registry

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.DenseOresFeature
import mystcraft.flood.generation.StarFissureFeature
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature

object ModFeatures {
    // Instantiate our custom classes
    val DENSE_ORES: Feature<DefaultFeatureConfig> = DenseOresFeature(DefaultFeatureConfig.CODEC)
    val STAR_FISSURE: Feature<DefaultFeatureConfig> = StarFissureFeature(DefaultFeatureConfig.CODEC)

    fun register() {
        // Register them to the Minecraft Engine
        Registry.register(Registries.FEATURE, Identifier(MystcraftReforged.MOD_ID, "dense_ores"), DENSE_ORES)
        Registry.register(Registries.FEATURE, Identifier(MystcraftReforged.MOD_ID, "star_fissure"), STAR_FISSURE)
    }
}