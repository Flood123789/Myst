package mystcraft.flood.generation

import mystcraft.flood.generation.profile.AgeProfile
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.generation.profile.BiomeMode
import mystcraft.flood.generation.profile.TerrainType
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import net.minecraft.world.biome.source.FixedBiomeSource
import net.minecraft.world.dimension.DimensionOptions
import net.minecraft.world.gen.chunk.ChunkGenerator
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings
import net.minecraft.world.gen.chunk.NoiseChunkGenerator

object AgeBuilder {
    fun buildGenerator(server: MinecraftServer, ageId: Identifier): Pair<ChunkGenerator, AgeProfile> {
        val profile = AgeProfileManager.getOrGenerateProfile(server, ageId)
        val registries = server.registryManager

        // 1. Map the JSON TerrainType to Minecraft's actual settings
        val settingsRegistry = registries.get(RegistryKeys.CHUNK_GENERATOR_SETTINGS)
        val settingsKey = when (profile.terrainType) {
            TerrainType.CAVES -> ChunkGeneratorSettings.CAVES
            TerrainType.FLOATING_ISLANDS -> ChunkGeneratorSettings.FLOATING_ISLANDS
            else -> ChunkGeneratorSettings.OVERWORLD
        }
        val settingsEntry = settingsRegistry.getEntry(settingsKey).get()

        // 2. Build the Biome Source based on the JSON Mode
        val biomeRegistry = registries.get(RegistryKeys.BIOME)
        val mcRandom = net.minecraft.util.math.random.Random.create(profile.seed)
        
        val biomeSource = when (profile.biomes.mode) {
            BiomeMode.VANILLA_DISTRIBUTION -> {
                // Steal the Overworld's exact biome layout (includes all mods!)
                val dimRegistry = registries.get(RegistryKeys.DIMENSION)
                val overworldOptions = dimRegistry.get(DimensionOptions.OVERWORLD)
                
                // If we somehow can't find the overworld, fallback to a random single biome
                overworldOptions?.chunkGenerator?.biomeSource ?: FixedBiomeSource(biomeRegistry.getRandom(mcRandom).get())
            }
            else -> {
                // SINGLE or CHECKERBOARD (fallback to Single for now)
                val randomBiomeEntry = biomeRegistry.getRandom(mcRandom).get()
                FixedBiomeSource(randomBiomeEntry)
            }
        }

        return Pair(NoiseChunkGenerator(biomeSource, settingsEntry), profile)
    }
}