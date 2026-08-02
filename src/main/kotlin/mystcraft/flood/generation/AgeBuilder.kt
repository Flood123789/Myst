package mystcraft.flood.generation

import com.mojang.datafixers.util.Pair as DFPair
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.profile.AgeProfile
import mystcraft.flood.generation.profile.AgeDimensionRole
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.generation.profile.BiomeMode
import mystcraft.flood.generation.profile.TerrainType
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import net.minecraft.world.World
import net.minecraft.world.biome.Biome
import net.minecraft.world.biome.source.CheckerboardBiomeSource
import net.minecraft.world.biome.source.FixedBiomeSource
import net.minecraft.world.biome.source.MultiNoiseBiomeSource
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterLists
import net.minecraft.world.biome.source.TheEndBiomeSource
import net.minecraft.world.biome.source.util.MultiNoiseUtil
import net.minecraft.world.gen.chunk.ChunkGenerator
import net.minecraft.world.gen.chunk.FlatChunkGenerator
import net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings
import net.minecraft.world.gen.chunk.NoiseChunkGenerator

/**
 * Converts a persisted [AgeProfile] into Minecraft's runtime biome source and chunk generator.
 *
 * [AgeProfileManager] decides *what* the Age contains; this class decides *which engine objects*
 * implement it. Derived Nether/End realms prefer rebuilding the live vanilla/datapack generator
 * so compatibility replacements are retained instead of hard-coding a vanilla preset.
 */
object AgeBuilder {
    
    fun buildGenerator(server: MinecraftServer, ageId: Identifier, symbols: List<String> = emptyList()): Pair<ChunkGenerator, AgeProfile> {
        val profile = AgeProfileManager.getOrGenerateProfile(server, ageId, symbols)
        val role = AgeSubdimensionManager.roleOf(ageId)
        val registries = server.registryManager
        val biomeRegistry = registries.get(RegistryKeys.BIOME)
        val inheritedGenerator = inheritedDimensionGenerator(server, role, profile.terrainType)

        if (profile.ageState.isSacrificed) {
            val voidBiomeId = if (profile.biomes.biomes.isNotEmpty()) {
                Identifier(profile.biomes.biomes.first().biomeId)
            } else {
                Identifier("minecraft:plains")
            }
            val biomeEntry = biomeRegistry.getEntry(RegistryKey.of(RegistryKeys.BIOME, voidBiomeId)).orElseGet {
                biomeRegistry.getEntry(RegistryKey.of(RegistryKeys.BIOME, Identifier("minecraft:plains"))).orElseThrow()
            }
            return Pair(BlankAgeChunkGenerator(FixedBiomeSource(biomeEntry)), profile)
        }

        // Pick the biome distribution independently from terrain generation. Several terrain
        // types can share one distribution, and a datapack may replace the inherited source.
        val selectedBiomeSource = when {
            profile.biomes.inheritDimensionSource && inheritedGenerator != null -> inheritedGenerator.biomeSource
            role == AgeDimensionRole.NETHER -> {
                weightedBiomeSource(profile, biomeRegistry, "minecraft:nether_wastes")
            }
            role == AgeDimensionRole.END -> {
                weightedBiomeSource(profile, biomeRegistry, "minecraft:the_end")
            }
            profile.terrainType == TerrainType.ALPHA -> ClassicBiomeSources.alphaSource(biomeRegistry)
            profile.terrainType == TerrainType.BETA -> ClassicBiomeSources.betaSource(biomeRegistry)
            else -> when (profile.biomes.mode) {
            BiomeMode.VANILLA_DISTRIBUTION -> {
                val parameterRegistry = registries.get(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                val overworldPreset = parameterRegistry.getEntry(MultiNoiseBiomeSourceParameterLists.OVERWORLD).get()
                MultiNoiseBiomeSource.create(overworldPreset)
            }
            BiomeMode.CHECKERBOARD -> {
                val validBiomes = profile.biomes.biomes.mapNotNull { b ->
                    val targetId = Identifier(b.biomeId)
                    biomeRegistry.getEntry(RegistryKey.of(RegistryKeys.BIOME, targetId)).orElse(null)
                }
                if (validBiomes.isNotEmpty()) {
                    CheckerboardBiomeSource(
                        net.minecraft.registry.entry.RegistryEntryList.of(validBiomes),
                        AgeTerrainTuning.checkerboardScale(profile.terrainTuning.biomeSize)
                    )
                } else {
                    FixedBiomeSource(biomeRegistry.getRandom(net.minecraft.util.math.random.Random.create(profile.seed)).get())
                }
            }
            BiomeMode.SINGLE -> {
                val targetId = if (profile.biomes.biomes.isNotEmpty()) Identifier(profile.biomes.biomes.first().biomeId) else Identifier("minecraft:plains")
                val biomeEntry = biomeRegistry.getEntry(RegistryKey.of(RegistryKeys.BIOME, targetId)).orElseGet {
                    biomeRegistry.getRandom(net.minecraft.util.math.random.Random.create(profile.seed)).get()
                }
                FixedBiomeSource(biomeEntry)
            }
            BiomeMode.WEIGHTED -> {
                weightedBiomeSource(profile, biomeRegistry, "minecraft:plains")
            }
        }
        }
        val biomeSource = if (
            role == AgeDimensionRole.OVERWORLD &&
            profile.terrainType !in setOf(TerrainType.NETHER, TerrainType.END, TerrainType.VOID, TerrainType.BIOSPHERES)
        ) {
            val plains = biomeRegistry.getEntry(RegistryKey.of(RegistryKeys.BIOME, Identifier("minecraft:plains"))).orElse(null)
            if (plains != null) StrongholdCompatibleBiomeSource(selectedBiomeSource, net.minecraft.registry.entry.RegistryEntryList.of(plains)) else selectedBiomeSource
        } else {
            selectedBiomeSource
        }

        // Assemble the final generator only after the biome source is settled; custom generators
        // wrap or replace vanilla generation but still report the selected source to Minecraft.

        if (role == AgeDimensionRole.NETHER || role == AgeDimensionRole.END) {
            return Pair(
                inheritedGenerator?.let { rebuildInheritedGenerator(it, biomeSource, profile) }
                    ?: vanillaRoleGenerator(registries, role, biomeSource),
                profile
            )
        }

        if (profile.terrainType == TerrainType.VOID) {
            val voidBiomeId = if (profile.biomes.biomes.isNotEmpty()) {
                Identifier(profile.biomes.biomes.first().biomeId)
            } else {
                Identifier("minecraft:plains")
            }
            val biomeEntry = biomeRegistry.getEntry(RegistryKey.of(RegistryKeys.BIOME, voidBiomeId)).orElseGet {
                biomeRegistry.getEntry(RegistryKey.of(RegistryKeys.BIOME, Identifier("minecraft:plains"))).orElseThrow()
            }
            return Pair(BlankAgeChunkGenerator(FixedBiomeSource(biomeEntry)), profile)
        }

        if (profile.terrainType == TerrainType.FLAT) {
            val flatConfig = FlatChunkGeneratorConfig.getDefaultConfig(
                registries.getWrapperOrThrow(RegistryKeys.BIOME),
                registries.getWrapperOrThrow(RegistryKeys.STRUCTURE_SET),
                registries.getWrapperOrThrow(RegistryKeys.PLACED_FEATURE)
            )
            flatConfig.enableFeatures()
            return Pair(FlatChunkGenerator(flatConfig), profile)
        }

        if (profile.terrainType == TerrainType.BIOSPHERES) {
            return Pair(BiosphereChunkGenerator(BiosphereBiomeSource.fromRegistry(profile.seed, biomeRegistry)), profile)
        }

        if (profile.terrainType == TerrainType.NETHER || profile.terrainType == TerrainType.END) {
            val terrainRole = if (profile.terrainType == TerrainType.NETHER) AgeDimensionRole.NETHER else AgeDimensionRole.END
            return Pair(
                inheritedGenerator?.let { rebuildInheritedGenerator(it, biomeSource, profile) }
                    ?: vanillaRoleGenerator(registries, terrainRole, biomeSource),
                profile
            )
        }

        // A Standard Age starts from the server's live Overworld generator. This preserves
        // datapack and mod replacements (for example Terralith) while still allowing an
        // authored biome source and per-Age seed/tuning when the generator is noise-based.
        if (profile.terrainType == TerrainType.STANDARD && inheritedGenerator != null) {
            return Pair(rebuildInheritedGenerator(inheritedGenerator, biomeSource, profile), profile)
        }

        // ==========================================
        // 3. TERRAIN SETTINGS
        // ==========================================
        val settingsRegistry = registries.get(RegistryKeys.CHUNK_GENERATOR_SETTINGS)
        val settingsKey = when (profile.terrainType) {
            TerrainType.STANDARD -> when (role) {
                AgeDimensionRole.NETHER -> ChunkGeneratorSettings.NETHER
                AgeDimensionRole.END -> ChunkGeneratorSettings.END
                AgeDimensionRole.OVERWORLD -> if (
                    profile.biomes.mode == BiomeMode.VANILLA_DISTRIBUTION &&
                    (profile.terrainTuning.biomeSize ?: 0) >= 11
                ) {
                    ChunkGeneratorSettings.LARGE_BIOMES
                } else {
                    ChunkGeneratorSettings.OVERWORLD
                }
            }
            TerrainType.ALPHA -> ChunkGeneratorSettings.AMPLIFIED
            TerrainType.BETA -> ChunkGeneratorSettings.OVERWORLD
            TerrainType.AMPLIFIED -> ChunkGeneratorSettings.AMPLIFIED
            TerrainType.CAVES -> ChunkGeneratorSettings.CAVES
            TerrainType.FLOATING_ISLANDS -> ChunkGeneratorSettings.FLOATING_ISLANDS
            TerrainType.NETHER -> ChunkGeneratorSettings.NETHER
            TerrainType.END -> ChunkGeneratorSettings.END
            else -> ChunkGeneratorSettings.OVERWORLD // STANDARD and CITIES fallback
        }
        val effectiveSettingsKey = when (role) {
            AgeDimensionRole.NETHER -> ChunkGeneratorSettings.NETHER
            AgeDimensionRole.END -> ChunkGeneratorSettings.END
            AgeDimensionRole.OVERWORLD -> settingsKey
        }
        val settingsEntry = settingsRegistry.getEntry(effectiveSettingsKey).get()

        // A Cities Age must keep vanilla aquifers. Disabling them does not dry the world out: the
        // aquifer-less sampler fills every non-solid block below the sea level with the settings'
        // default fluid, and every non-solid block below y=-54 with lava. On Overworld settings that
        // floods the entire cave system, and the resulting flowing fluid forces synchronous chunk
        // loads from the server thread until the tick loop stalls. City chunks are dried out by
        // LostCityChunkGenerator.flattenCityChunk instead, which is scoped to the city footprint.
        val tunedSettings = if (role == AgeDimensionRole.OVERWORLD) {
            AgeTerrainTuning.withTuning(settingsEntry, profile.terrainTuning)
        } else {
            settingsEntry
        }

        val baseGenerator = NoiseChunkGenerator(biomeSource, tunedSettings)
        val chunkGenerator = if (profile.terrainType == TerrainType.CITIES) {
            LostCityChunkGenerator(baseGenerator, biomeSource, profile.seed)
        } else {
            baseGenerator
        }
        return Pair(chunkGenerator, profile)
    }

    private fun inheritedDimensionGenerator(
        server: MinecraftServer,
        role: AgeDimensionRole,
        terrainType: TerrainType
    ): ChunkGenerator? {
        val worldKey = when {
            role == AgeDimensionRole.NETHER || terrainType == TerrainType.NETHER -> World.NETHER
            role == AgeDimensionRole.END || terrainType == TerrainType.END -> World.END
            terrainType == TerrainType.STANDARD -> World.OVERWORLD
            else -> return null
        }
        return server.getWorld(worldKey)?.chunkManager?.chunkGenerator
    }

    private fun rebuildInheritedGenerator(
        inherited: ChunkGenerator,
        biomeSource: net.minecraft.world.biome.source.BiomeSource,
        profile: AgeProfile
    ): ChunkGenerator {
        if (inherited is NoiseChunkGenerator) {
            val settings = if (profile.terrainTuning.isEdited()) {
                AgeTerrainTuning.withTuning(inherited.settings, profile.terrainTuning)
            } else {
                inherited.settings
            }
            return NoiseChunkGenerator(biomeSource, settings)
        }

        if (!profile.biomes.inheritDimensionSource) {
            MystcraftReforged.LOGGER.warn(
                "Age {} requested a biome override, but inherited generator {} does not expose noise settings; preserving the modded generator",
                profile.id,
                inherited.javaClass.name
            )
        }
        return inherited
    }

    private fun vanillaRoleGenerator(
        registries: net.minecraft.registry.DynamicRegistryManager,
        role: AgeDimensionRole,
        biomeSource: net.minecraft.world.biome.source.BiomeSource
    ): ChunkGenerator {
        val settingsRegistry = registries.get(RegistryKeys.CHUNK_GENERATOR_SETTINGS)
        val settingsKey = if (role == AgeDimensionRole.NETHER) ChunkGeneratorSettings.NETHER else ChunkGeneratorSettings.END
        return NoiseChunkGenerator(biomeSource, settingsRegistry.getEntry(settingsKey).orElseThrow())
    }

    private fun weightedBiomeSource(profile: AgeProfile, biomeRegistry: Registry<Biome>, fallbackId: String): net.minecraft.world.biome.source.BiomeSource {
        val fallback = biomeRegistry.getEntry(RegistryKey.of(RegistryKeys.BIOME, Identifier(fallbackId))).orElseGet {
            biomeRegistry.getRandom(net.minecraft.util.math.random.Random.create(profile.seed)).orElseThrow()
        }
        val valid = profile.biomes.biomes.mapNotNull { weighted ->
            biomeRegistry.getEntry(RegistryKey.of(RegistryKeys.BIOME, Identifier(weighted.biomeId))).orElse(null)
                ?.let { weighted to it }
        }
        if (valid.isEmpty()) return FixedBiomeSource(fallback)

        val totalWeight = valid.sumOf { it.first.weight.coerceAtLeast(1) }.toFloat()
        val entries = mutableListOf<DFPair<MultiNoiseUtil.NoiseHypercube, net.minecraft.registry.entry.RegistryEntry<Biome>>>()
        var currentTemp = -1.0f
        for ((weighted, biome) in valid) {
            val nextTemp = (currentTemp + weighted.weight.coerceAtLeast(1) / totalWeight * 2.0f).coerceAtMost(1.0f)
            entries.add(
                DFPair.of(
                    MultiNoiseUtil.NoiseHypercube(
                        MultiNoiseUtil.ParameterRange.of(currentTemp, nextTemp),
                        MultiNoiseUtil.ParameterRange.of(-1.0f, 1.0f),
                        MultiNoiseUtil.ParameterRange.of(-1.0f, 1.0f),
                        MultiNoiseUtil.ParameterRange.of(-1.0f, 1.0f),
                        MultiNoiseUtil.ParameterRange.of(-1.0f, 1.0f),
                        MultiNoiseUtil.ParameterRange.of(-1.0f, 1.0f),
                        0L
                    ),
                    biome
                )
            )
            currentTemp = nextTemp
        }
        return MultiNoiseBiomeSource.create(MultiNoiseUtil.Entries(entries))
    }
}
