package mystcraft.flood.generation.profile

import mystcraft.flood.MystcraftReforged
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import net.minecraft.util.WorldSavePath
import java.nio.file.Files
import kotlin.random.Random

object AgeProfileManager {
    fun getOrGenerateProfile(server: MinecraftServer, ageId: Identifier): AgeProfile {
        val dir = server.getSavePath(WorldSavePath.ROOT).resolve("mystcraft_profiles")
        if (!Files.exists(dir)) Files.createDirectories(dir)

        val file = dir.resolve("${ageId.path}.json")
        if (Files.exists(file)) {
            return AgeProfile.fromJson(Files.readString(file))
        }

        val generationSeed = ageId.toString().hashCode().toLong()
        val rand = Random(generationSeed)
        
        fun randColor(): Int {
            val h = rand.nextFloat()
            val s = 0.5f + rand.nextFloat() * 0.5f
            val b = 0.7f + rand.nextFloat() * 0.3f
            return java.awt.Color.HSBtoRGB(h, s, b) and 0xFFFFFF
        }

        val ageSeed = rand.nextLong() 
        
        // NEW: 80% chance for Multi-Biome (Vanilla/Modded distribution), 20% chance for Single Biome
        val biomeMode = if (rand.nextFloat() < 0.8f) BiomeMode.VANILLA_DISTRIBUTION else BiomeMode.SINGLE

        val fixedTimeVal = if (rand.nextFloat() < 0.2f) rand.nextLong(0, 24000) else null

        val profile = AgeProfile(
            id = ageId.toString(),
            seed = ageSeed,
            terrainType = TerrainType.entries.toTypedArray().random(rand),
            colors = ColorSettings(randColor(), randColor(), randColor(), randColor(), randColor()),
            time = TimeSettings(rand.nextInt(1, 4), rand.nextFloat() * 5f + 0.5f, rand.nextFloat() * 3f + 0.2f, rand.nextBoolean(), fixedTimeVal),
            weather = WeatherSettings(isEndlessRain = rand.nextFloat() < 0.1f, isEndlessStorm = rand.nextFloat() < 0.1f, noWeather = rand.nextFloat() < 0.2f),
            biomes = BiomeSet(biomeMode, emptyList()),
            spawning = SpawnSettings(noMobs = rand.nextFloat() < 0.1f, hostileMultiplier = 1.0f, passiveMultiplier = 1.0f), // NEW
            stability = StabilityProfile(isStable = true, instabilityScore = 0)
        )

        Files.writeString(file, profile.toJson())
        MystcraftReforged.LOGGER.info("Saved AgeProfile to disk: ${file.fileName}")
        return profile
    }
}