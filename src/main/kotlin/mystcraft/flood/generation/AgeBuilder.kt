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
    fun buildGenerator(server: MinecraftServer, ageId: Identifier): Pair<ChunkGenerator, AgeProfile> {
        val profile = AgeProfileManager.getOrGenerateProfile(server, ageId)
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
            // SINGLE MODE
            val targetId = Identifier(profile.biomes.biomes.first().biomeId)
            val biomeEntry = biomeRegistry.getEntry(RegistryKey.of(RegistryKeys.BIOME, targetId)).orElseGet {
                biomeRegistry.getRandom(net.minecraft.util.math.random.Random.create(profile.seed)).get()
            }
            FixedBiomeSource(biomeEntry)
        } else {
            // WEIGHTED MODE (The Slicer Trick)
            val totalWeight = profile.biomes.biomes.sumOf { it.weight }.toFloat()
            val entries = mutableListOf<DFPair<MultiNoiseUtil.NoiseHypercube, net.minecraft.registry.entry.RegistryEntry<Biome>>>()

            var currentTemp = -1.0f // Noise axis starts at -1.0

            for (b in profile.biomes.biomes) {
                val targetId = Identifier(b.biomeId)
                val biomeEntry = biomeRegistry.getEntry(RegistryKey.of(RegistryKeys.BIOME, targetId)).orElse(null)

                if (biomeEntry != null) {
                    val fraction = b.weight / totalWeight
                    val rangeSize = fraction * 2.0f // 2.0 is the full distance from -1.0 to 1.0
                    val nextTemp = currentTemp + rangeSize

                    // Create a slice on the Temperature axis. Leave all other axes at max width [-1.0 to 1.0]
                    val hypercube = MultiNoiseUtil.NoiseHypercube(
                        MultiNoiseUtil.ParameterRange.of(currentTemp, nextTemp), // The slice!
                        MultiNoiseUtil.ParameterRange.of(-1.0f, 1.0f), // Humidity
                        MultiNoiseUtil.ParameterRange.of(-1.0f, 1.0f), // Continentalness
                        MultiNoiseUtil.ParameterRange.of(-1.0f, 1.0f), // Erosion
                        MultiNoiseUtil.ParameterRange.of(-1.0f, 1.0f), // Depth
                        MultiNoiseUtil.ParameterRange.of(-1.0f, 1.0f), // Weirdness
                        0L // Offset
                    )

                    entries.add(DFPair.of(hypercube, biomeEntry))
                    currentTemp = nextTemp
                }
            }
            
            // FIXED: Using Fabric's 'Entries' mapping instead of Mojang's 'ParameterList'
            MultiNoiseBiomeSource.create(MultiNoiseUtil.Entries(entries))
        }

        return Pair(NoiseChunkGenerator(biomeSource, settingsEntry), profile)
    }
}