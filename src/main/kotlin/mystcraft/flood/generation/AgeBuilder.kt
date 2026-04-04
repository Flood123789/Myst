// src/main/kotlin/mystcraft/flood/generation/AgeBuilder.kt
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
import net.minecraft.world.biome.source.FixedBiomeSource
import net.minecraft.world.biome.source.MultiNoiseBiomeSource
import net.minecraft.world.biome.source.util.MultiNoiseUtil
import net.minecraft.world.gen.chunk.ChunkGenerator
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings
import net.minecraft.world.gen.chunk.NoiseChunkGenerator

object AgeBuilder {
    // Added 'symbols' here so it can pass it to the profile manager
    fun buildGenerator(server: MinecraftServer, ageId: Identifier, symbols: List<String> = emptyList()): Pair<ChunkGenerator, AgeProfile> {
        val profile = AgeProfileManager.getOrGenerateProfile(server, ageId, symbols)
        val registries = server.registryManager

        // 1. Terrain Settings
        val settingsRegistry = registries.get(RegistryKeys.CHUNK_GENERATOR_SETTINGS)
        val settingsKey = when (profile.terrainType) {
            TerrainType.CAVES -> ChunkGeneratorSettings.CAVES
            TerrainType.FLOATING_ISLANDS -> ChunkGeneratorSettings.FLOATING_ISLANDS
            else -> ChunkGeneratorSettings.OVERWORLD // STANDARD and FLAT fallback
        }
        val settingsEntry = settingsRegistry.getEntry(settingsKey).get()

        // 2. Biome Source Generation
        val biomeRegistry = registries.get(RegistryKeys.BIOME)

        val biomeSource = if (profile.biomes.mode == BiomeMode.SINGLE || profile.biomes.biomes.size == 1) {
            val targetId = Identifier(profile.biomes.biomes.first().biomeId)
            val biomeEntry = biomeRegistry.getEntry(RegistryKey.of(RegistryKeys.BIOME, targetId)).orElseGet {
                biomeRegistry.getRandom(net.minecraft.util.math.random.Random.create(profile.seed)).get()
            }
            FixedBiomeSource(biomeEntry)
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

        return Pair(NoiseChunkGenerator(biomeSource, settingsEntry), profile)
    }
}