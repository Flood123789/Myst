package mystcraft.flood.generation

import com.mojang.datafixers.util.Pair as DFPair
import mystcraft.flood.generation.profile.AgeProfile
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.generation.profile.BiomeMode
import mystcraft.flood.generation.profile.TerrainType
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import net.minecraft.world.biome.Biome
import net.minecraft.world.biome.source.CheckerboardBiomeSource
import net.minecraft.world.biome.source.FixedBiomeSource
import net.minecraft.world.biome.source.MultiNoiseBiomeSource
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterLists
import net.minecraft.world.biome.source.util.MultiNoiseUtil
import net.minecraft.world.gen.chunk.ChunkGenerator
import net.minecraft.world.gen.chunk.FlatChunkGenerator
import net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings
import net.minecraft.world.gen.chunk.NoiseChunkGenerator

object AgeBuilder {
    
    fun buildGenerator(server: MinecraftServer, ageId: Identifier, symbols: List<String> = emptyList()): Pair<ChunkGenerator, AgeProfile> {
        val profile = AgeProfileManager.getOrGenerateProfile(server, ageId, symbols)
        val registries = server.registryManager
        val biomeRegistry = registries.get(RegistryKeys.BIOME)

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

        // ==========================================
        // 1. BIOME SOURCE GENERATION
        // ==========================================
        val biomeSource = when {
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
                    CheckerboardBiomeSource(net.minecraft.registry.entry.RegistryEntryList.of(validBiomes), 3)
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
                if (profile.biomes.biomes.isEmpty()) {
                    FixedBiomeSource(biomeRegistry.getRandom(net.minecraft.util.math.random.Random.create(profile.seed)).get())
                } else {
                    val totalWeight = profile.biomes.biomes.sumOf { it.weight }.toFloat()
                    val entries = mutableListOf<DFPair<MultiNoiseUtil.NoiseHypercube, net.minecraft.registry.entry.RegistryEntry<Biome>>>()
                    var currentTemp = -1.0f 

                    for (b in profile.biomes.biomes) {
                        val targetId = Identifier(b.biomeId)
                        val biomeEntry = biomeRegistry.getEntry(RegistryKey.of(RegistryKeys.BIOME, targetId)).orElse(null)
                        if (biomeEntry != null) {
                            val fraction = b.weight / totalWeight
                            val rangeSize = fraction * 2.0f 
                            val nextTemp = currentTemp + rangeSize
                            val hypercube = MultiNoiseUtil.NoiseHypercube(
                                MultiNoiseUtil.ParameterRange.of(currentTemp, nextTemp), 
                                MultiNoiseUtil.ParameterRange.of(-1.0f, 1.0f), 
                                MultiNoiseUtil.ParameterRange.of(-1.0f, 1.0f), 
                                MultiNoiseUtil.ParameterRange.of(-1.0f, 1.0f), 
                                MultiNoiseUtil.ParameterRange.of(-1.0f, 1.0f), 
                                MultiNoiseUtil.ParameterRange.of(-1.0f, 1.0f), 
                                0L 
                            )
                            entries.add(DFPair.of(hypercube, biomeEntry))
                            currentTemp = nextTemp
                        }
                    }
                    MultiNoiseBiomeSource.create(MultiNoiseUtil.Entries(entries))
                }
            }
        }
        }

        // ==========================================
        // 2. FINAL GENERATOR ASSEMBLY
        // ==========================================

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

        // ==========================================
        // 3. TERRAIN SETTINGS
        // ==========================================
        val settingsRegistry = registries.get(RegistryKeys.CHUNK_GENERATOR_SETTINGS)
        val settingsKey = when (profile.terrainType) {
            TerrainType.ALPHA -> ChunkGeneratorSettings.AMPLIFIED
            TerrainType.BETA -> ChunkGeneratorSettings.OVERWORLD
            TerrainType.AMPLIFIED -> ChunkGeneratorSettings.AMPLIFIED
            TerrainType.CAVES -> ChunkGeneratorSettings.CAVES
            TerrainType.FLOATING_ISLANDS -> ChunkGeneratorSettings.FLOATING_ISLANDS
            else -> ChunkGeneratorSettings.OVERWORLD // STANDARD and CITIES fallback
        }
        val settingsEntry = settingsRegistry.getEntry(settingsKey).get()

        return Pair(NoiseChunkGenerator(biomeSource, settingsEntry), profile)
    }
}
