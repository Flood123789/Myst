package mystcraft.flood.generation.profile

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.AgeCompiler
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import net.minecraft.util.WorldSavePath
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

object AgeProfileManager {
    // In-memory cache to prevent constant disk I/O and handle live time
    private val profileCache = ConcurrentHashMap<Identifier, AgeProfile>()

    // The @JvmOverloads fixes the Java Mixin crashes!
    @JvmOverloads
    fun getOrGenerateProfile(server: MinecraftServer, ageId: Identifier, symbols: List<String> = emptyList()): AgeProfile {
        // Return from cache if already loaded
        profileCache[ageId]?.let { return it }

        val dir = server.getSavePath(WorldSavePath.ROOT).resolve("mystcraft_profiles")
        if (!Files.exists(dir)) Files.createDirectories(dir)

        val file = dir.resolve("${ageId.path}.json")
        val profile = if (Files.exists(file)) {
            AgeProfile.fromJson(Files.readString(file))
        } else {
            generateNewProfile(ageId, symbols).also { newProfile ->
                Files.writeString(file, newProfile.toJson())
                MystcraftReforged.LOGGER.info("Saved new AgeProfile to disk: ${file.fileName}")
            }
        }

        // Initialize live session time from the saved JSON value
        profile.time.liveTimeOfDay = profile.time.savedTime ?: 6000L
        
        profileCache[ageId] = profile
        return profile
    }

    private fun generateNewProfile(ageId: Identifier, symbols: List<String>): AgeProfile {
        // 1. RUN THE COMPILER
        val compiled = AgeCompiler.compile(symbols)
        
        // ==========================================
        // 1.5 SCAN FOR CUSTOM MODIFIERS (Dense Ores, etc.)
        // ==========================================
        val activeModifiers = mutableListOf<String>()
        var modifierInstability = 0

        for (symbol in symbols) {
            // Check for the dense ores page!
            if (symbol == "mystcraft-reforged:dense_ores" || symbol == "dense_ores") {
                if (!activeModifiers.contains("dense_ores")) {
                    activeModifiers.add("dense_ores")
                    modifierInstability += 75 // Massive Greed Tax!
                }
            }
            // You can easily add more pages here in the future!
            // if (symbol == "mystcraft:meteors") { activeModifiers.add("meteors"); modifierInstability += 50 }
        }

        for (symbol in symbols) {
            // Check for the dense ores page!
            if (symbol == "mystcraft-reforged:dense_ores" || symbol == "dense_ores") {
                if (!activeModifiers.contains("dense_ores")) {
                    activeModifiers.add("dense_ores")
                    modifierInstability += 75 // Massive Greed Tax!
                }
            }
            // Check for the giant trees page!
            if (symbol == "mystcraft-reforged:giant_trees" || symbol == "giant_trees") {
                if (!activeModifiers.contains("giant_trees")) {
                    activeModifiers.add("giant_trees")
                    modifierInstability += 15 // Roots tearing up the crust causes minor instability
                }
            }
            // Check for the crystal formations page!
            if (symbol == "mystcraft-reforged:crystal_formations" || symbol == "crystal_formations") {
                if (!activeModifiers.contains("crystal_formations")) {
                    activeModifiers.add("crystal_formations")
                    modifierInstability += 10 // Sharp crystals poking out of the ground adds a bit of chaos
                }
            }
        }

        val rand = Random(ageId.toString().hashCode().toLong())
        fun randColor() = java.awt.Color.HSBtoRGB(rand.nextFloat(), 0.5f + rand.nextFloat() * 0.5f, 0.7f + rand.nextFloat() * 0.3f) and 0xFFFFFF

        // 2. Map Terrain (If null, Random!)
        val terrain = when(compiled.terrainType) {
            "CAVE" -> TerrainType.CAVES
            "FLOATING_ISLANDS" -> TerrainType.FLOATING_ISLANDS
            "STANDARD" -> TerrainType.STANDARD
            else -> TerrainType.entries.random(rand) // RANDOM
        }

        // 3. Map Biomes (If empty, Random!)
        val biomeMode = if (compiled.biomes.size <= 1) BiomeMode.SINGLE else BiomeMode.WEIGHTED
        val biomesList = if (compiled.biomes.isEmpty()) {
            val randomBiomes = listOf("minecraft:plains", "minecraft:desert", "minecraft:forest", "minecraft:jungle", "minecraft:savanna", "minecraft:taiga", "minecraft:swamp", "minecraft:snowy_plains", "minecraft:badlands")
            mutableListOf(BiomeWeight(randomBiomes.random(rand), 100))
        } else {
            val weight = 100 / compiled.biomes.size
            compiled.biomes.map { BiomeWeight(it, weight) }.toMutableList()
        }

        // 4. Time & Weather
        val timeMode = compiled.timeMode ?: listOf("fast", "slow", "fixed", "normal", "normal", "normal").random(rand)
        val weatherMode = compiled.weatherMode ?: listOf("endless_rain", "endless_storm", "no_weather", "normal", "normal").random(rand)

        // ==========================================
        // 5. CALCULATE THE FINAL BILL
        // ==========================================
        var finalInstability = 0 + modifierInstability // Add the modifier penalty here!
        if (terrain == TerrainType.FLOATING_ISLANDS) finalInstability += 15
        if (terrain == TerrainType.CAVES) finalInstability += 5
        if (timeMode == "fast" || timeMode == "slow") finalInstability += 10
        if (timeMode == "fixed") finalInstability += 20
        if (weatherMode == "endless_storm") finalInstability += 15
        if (biomesList.size > 3) finalInstability += (biomesList.size - 3) * 10

        return AgeProfile(
            id = ageId.toString(),
            seed = rand.nextLong(),
            terrainType = terrain,
            colors = ColorSettings(
                sky = compiled.skyColor ?: randColor(),
                fog = compiled.fogColor ?: randColor(),
                water = compiled.waterColor ?: randColor(),
                grass = compiled.grassColor ?: randColor(),     
                foliage = compiled.foliageColor ?: randColor()  
            ),
            time = TimeSettings(
                sunCount = rand.nextInt(1, 4), 
                sunSize = rand.nextFloat() * 3f + 0.5f, 
                moonSize = rand.nextFloat() * 3f + 0.5f, 
                hasStars = rand.nextBoolean(), 
                fixedTime = if (timeMode == "fixed") rand.nextLong(0, 24000) else null,
                timeScale = when(timeMode) {
                    "fast" -> 5.0f
                    "slow" -> 0.2f
                    else -> 1.0f
                }
            ),
            weather = WeatherSettings(
                isEndlessRain = weatherMode == "endless_rain",
                isEndlessStorm = weatherMode == "endless_storm",
                noWeather = weatherMode == "no_weather"
            ),
            biomes = BiomeSet(
                mode = biomeMode,
                biomes = biomesList
            ),
            spawning = SpawnSettings(false, 1.0f, 1.0f),
            
            // Apply the final audited bill!
            stability = StabilityProfile(
                isStable = finalInstability <= 0,
                instabilityScore = finalInstability
            ),
            
            // Pass the active modifiers to the profile so the Chunk Generator can see them!
            modifiers = activeModifiers 
        )
    }

    fun saveAndUnload(server: MinecraftServer, ageId: Identifier) {
        val profile = profileCache.remove(ageId) ?: return
        // Commit live time to the saved field before writing to disk
        profile.time.savedTime = profile.time.liveTimeOfDay
        
        val dir = server.getSavePath(WorldSavePath.ROOT).resolve("mystcraft_profiles")
        val file = dir.resolve("${ageId.path}.json")
        Files.writeString(file, profile.toJson())
        MystcraftReforged.LOGGER.info("Saved and unloaded AgeProfile: ${file.fileName}")
    }
}