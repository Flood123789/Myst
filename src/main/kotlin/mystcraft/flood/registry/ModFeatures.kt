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
    
    val TENDRILS: Feature<DefaultFeatureConfig> = TendrilsFeature(DefaultFeatureConfig.CODEC)
    val GIANT_OBELISKS: Feature<DefaultFeatureConfig> = GiantObeliskFeature(DefaultFeatureConfig.CODEC)
    val FLOATING_CASTLE: Feature<DefaultFeatureConfig> = FloatingCastleFeature(DefaultFeatureConfig.CODEC)

    // === NEW FEATURES ADDED HERE ===
    val CITY_GRID: Feature<DefaultFeatureConfig> = CityGridFeature(DefaultFeatureConfig.CODEC)
    val BIOSPHERE: Feature<DefaultFeatureConfig> = BiosphereFeature(DefaultFeatureConfig.CODEC)
    val EXOTIC_SURFACE: Feature<DefaultFeatureConfig> = ExoticSurfaceFeature(DefaultFeatureConfig.CODEC)
    val ANCIENT_REMAINS: Feature<DefaultFeatureConfig> = AncientRemainsFeature(DefaultFeatureConfig.CODEC)
    val FORGOTTEN_RUINS: Feature<DefaultFeatureConfig> = ForgottenRuinsFeature(DefaultFeatureConfig.CODEC)
    val COLLAPSED_OBSERVATORY: Feature<DefaultFeatureConfig> = CollapsedObservatoryFeature(DefaultFeatureConfig.CODEC)
    val ANCIENT_AQUEDUCTS: Feature<DefaultFeatureConfig> = AncientAqueductFeature(DefaultFeatureConfig.CODEC)
    val GATEWAY_RUINS: Feature<DefaultFeatureConfig> = GatewayRuinsFeature(DefaultFeatureConfig.CODEC)
    val PAGE_STORMS: Feature<DefaultFeatureConfig> = PageStormFeature(DefaultFeatureConfig.CODEC)
    val MEMORY_BLOOMS: Feature<DefaultFeatureConfig> = MemoryBloomFeature(DefaultFeatureConfig.CODEC)
    val STABLE_SANCTUARIES: Feature<DefaultFeatureConfig> = StableSanctuaryFeature(DefaultFeatureConfig.CODEC)
    val METEOR_SHOWERS: Feature<DefaultFeatureConfig> = MeteorShowerFeature(DefaultFeatureConfig.CODEC)
    val SKY_SPHERES: Feature<DefaultFeatureConfig> = SkySphereFeature(DefaultFeatureConfig.CODEC)

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
        Registry.register(Registries.FEATURE, Identifier(MystcraftReforged.MOD_ID, "floating_castle"), FLOATING_CASTLE)
        
        Registry.register(Registries.FEATURE, Identifier(MystcraftReforged.MOD_ID, "tendrils"), TENDRILS)
        Registry.register(Registries.FEATURE, Identifier(MystcraftReforged.MOD_ID, "giant_obelisks"), GIANT_OBELISKS)

        // === NEW REGISTRY CALLS ADDED HERE ===
        Registry.register(Registries.FEATURE, Identifier(MystcraftReforged.MOD_ID, "city_grid"), CITY_GRID)
        Registry.register(Registries.FEATURE, Identifier(MystcraftReforged.MOD_ID, "biosphere"), BIOSPHERE)
        Registry.register(Registries.FEATURE, Identifier(MystcraftReforged.MOD_ID, "exotic_surface"), EXOTIC_SURFACE)
        Registry.register(Registries.FEATURE, Identifier(MystcraftReforged.MOD_ID, "ancient_remains"), ANCIENT_REMAINS)
        Registry.register(Registries.FEATURE, Identifier(MystcraftReforged.MOD_ID, "forgotten_ruins"), FORGOTTEN_RUINS)
        Registry.register(Registries.FEATURE, Identifier(MystcraftReforged.MOD_ID, "collapsed_observatory"), COLLAPSED_OBSERVATORY)
        Registry.register(Registries.FEATURE, Identifier(MystcraftReforged.MOD_ID, "ancient_aqueducts"), ANCIENT_AQUEDUCTS)
        Registry.register(Registries.FEATURE, Identifier(MystcraftReforged.MOD_ID, "gateway_ruins"), GATEWAY_RUINS)
        Registry.register(Registries.FEATURE, Identifier(MystcraftReforged.MOD_ID, "page_storms"), PAGE_STORMS)
        Registry.register(Registries.FEATURE, Identifier(MystcraftReforged.MOD_ID, "memory_blooms"), MEMORY_BLOOMS)
        Registry.register(Registries.FEATURE, Identifier(MystcraftReforged.MOD_ID, "stable_sanctuaries"), STABLE_SANCTUARIES)
        Registry.register(Registries.FEATURE, Identifier(MystcraftReforged.MOD_ID, "meteor_showers"), METEOR_SHOWERS)
        Registry.register(Registries.FEATURE, Identifier(MystcraftReforged.MOD_ID, "sky_spheres"), SKY_SPHERES)

        MystcraftReforged.LOGGER.info("Successfully registered all worldgen features.")
    }
}
