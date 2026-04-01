package mystcraft.flood.generation.profile

import mystcraft.flood.MystcraftReforged
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import net.minecraft.util.WorldSavePath
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

object AgeProfileManager {
    // In-memory cache to prevent constant disk I/O and handle live time
    private val profileCache = ConcurrentHashMap<Identifier, AgeProfile>()

    fun getOrGenerateProfile(server: MinecraftServer, ageId: Identifier): AgeProfile {
        // Return from cache if already loaded
        profileCache[ageId]?.let { return it }

        val dir = server.getSavePath(WorldSavePath.ROOT).resolve("mystcraft_profiles")
        if (!Files.exists(dir)) Files.createDirectories(dir)

        val file = dir.resolve("${ageId.path}.json")
        val profile = if (Files.exists(file)) {
            AgeProfile.fromJson(Files.readString(file))
        } else {
            generateNewProfile(ageId)
        }

        // Initialize live session time from the saved JSON value
        profile.time.liveTimeOfDay = profile.time.savedTime ?: 6000L
        
        profileCache[ageId] = profile
        return profile
    }

    private fun generateNewProfile(ageId: Identifier): AgeProfile {
        val rand = Random(ageId.toString().hashCode().toLong())
        fun randColor() = java.awt.Color.HSBtoRGB(rand.nextFloat(), 0.5f + rand.nextFloat() * 0.5f, 0.7f + rand.nextFloat() * 0.3f) and 0xFFFFFF

        return AgeProfile(
            id = ageId.toString(),
            seed = rand.nextLong(),
            terrainType = TerrainType.entries.random(rand),
            colors = ColorSettings(randColor(), randColor(), randColor(), randColor(), randColor()),
            time = TimeSettings(rand.nextInt(1, 4), rand.nextFloat() * 5f + 0.5f, rand.nextFloat() * 3f + 0.2f, rand.nextBoolean(), null),
            weather = WeatherSettings(false, false, true),
            biomes = BiomeSet(BiomeMode.VANILLA_DISTRIBUTION, emptyList()),
            spawning = SpawnSettings(false, 1.0f, 1.0f),
            stability = StabilityProfile(true, 0)
        )
    }

    fun saveAndUnload(server: MinecraftServer, ageId: Identifier) {
        val profile = profileCache.remove(ageId) ?: return
        // Commit live time to the saved field before writing to disk
        profile.time.savedTime = profile.time.liveTimeOfDay
        
        val dir = server.getSavePath(WorldSavePath.ROOT).resolve("mystcraft_profiles")
        val file = dir.resolve("${ageId.path}.json")
        Files.writeString(file, profile.toJson())
    }
}