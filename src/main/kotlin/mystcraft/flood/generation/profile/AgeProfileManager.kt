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
    private val profileCache = ConcurrentHashMap<Identifier, AgeProfile>()

    @JvmOverloads
    fun getOrGenerateProfile(server: MinecraftServer, ageId: Identifier, symbols: List<String> = emptyList()): AgeProfile {
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

        profile.time.liveTimeOfDay = profile.time.savedTime ?: 6000L
        profileCache[ageId] = profile
        return profile
    }

    private fun generateNewProfile(ageId: Identifier, symbols: List<String>): AgeProfile {
        val compiled = AgeCompiler.compile(symbols)
        
        // ==========================================
        // 1. SCAN FOR MODIFIERS, CELESTIALS & SPAWNING
        // ==========================================
        val activeModifiers = mutableListOf<String>()
        var modifierInstability = 0
        
        // Celestial Counters
        var sunNormal = 0
        var sunRed = 0
        var sunBlue = 0
        var moonCount = 0
        var starDensity = 1 // Default to 1 layer of stars
        
        // Spawning Counters
        var noMobs = false
        var hostileMult = 1.0f
        var passiveMult = 1.0f

        for (symbol in symbols) {
            val cleanSymbol = symbol.replace("mystcraft-reforged:", "")
            
            when (cleanSymbol) {
                // Mechanics
                "dense_ores" -> if (!activeModifiers.contains("dense_ores")) { activeModifiers.add("dense_ores"); modifierInstability += 75 }
                "giant_trees" -> if (!activeModifiers.contains("giant_trees")) { activeModifiers.add("giant_trees"); modifierInstability += 15 }
                "crystal_formations" -> if (!activeModifiers.contains("crystal_formations")) { activeModifiers.add("crystal_formations"); modifierInstability += 10 }
                
                // Suns
                "sun_normal" -> sunNormal++
                "sun_red" -> { sunRed++; modifierInstability += 10 } // Extra suns = heat/chaos
                "sun_blue" -> { sunBlue++; modifierInstability += 10 }
                
                // Moons
                "moon_normal", "moon_extra" -> { moonCount++; modifierInstability += 5 } // Tides get weird
                
                // Stars
                "stars_normal" -> starDensity = 1
                "stars_dense" -> starDensity++
                "no_stars" -> starDensity = 0
                
                // Spawning 
                "spawning_no_mobs" -> { noMobs = true; modifierInstability += 50 } // Peaceful worlds are unnatural and greedy
                "spawning_extra_hostile" -> { hostileMult += 1.0f; modifierInstability -= 15 } // Dangerous worlds are highly stable!
                "spawning_extra_passive" -> { passiveMult += 1.0f; modifierInstability += 25 } // Greed tax for infinite food
            }
        }

        val rand = Random(ageId.toString().hashCode().toLong())
        fun randColor() = java.awt.Color.HSBtoRGB(rand.nextFloat(), 0.5f + rand.nextFloat() * 0.5f, 0.7f + rand.nextFloat() * 0.3f) and 0xFFFFFF

        // Failsafes: If the player didn't specify any suns or moons, give them 1 standard one so the sky isn't pitch black.
        if (sunNormal == 0 && sunRed == 0 && sunBlue == 0) sunNormal = 1
        if (moonCount == 0) moonCount = 1

        // ==========================================
        // 2. MAP TERRAIN & BIOMES
        // ==========================================
        val terrain = when(compiled.terrainType) {
            "CAVE" -> TerrainType.CAVES
            "FLOATING_ISLANDS" -> TerrainType.FLOATING_ISLANDS
            "STANDARD" -> TerrainType.STANDARD
            else -> TerrainType.entries.random(rand) 
        }

        val biomeMode = if (compiled.biomes.size <= 1) BiomeMode.SINGLE else BiomeMode.WEIGHTED
        val biomesList = if (compiled.biomes.isEmpty()) {
            val randomBiomes = listOf("minecraft:plains", "minecraft:desert", "minecraft:forest", "minecraft:jungle", "minecraft:savanna", "minecraft:taiga", "minecraft:swamp", "minecraft:snowy_plains", "minecraft:badlands")
            mutableListOf(BiomeWeight(randomBiomes.random(rand), 100))
        } else {
            val weight = 100 / compiled.biomes.size
            compiled.biomes.map { BiomeWeight(it, weight) }.toMutableList()
        }

        val timeMode = compiled.timeMode ?: listOf("fast", "slow", "fixed", "normal", "normal", "normal").random(rand)
        val weatherMode = compiled.weatherMode ?: listOf("endless_rain", "endless_storm", "no_weather", "normal", "normal").random(rand)

        // ==========================================
        // 3. CALCULATE THE FINAL BILL
        // ==========================================
        var finalInstability = 0 + modifierInstability 
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
                sunNormalCount = sunNormal,
                sunRedCount = sunRed,
                sunBlueCount = sunBlue,
                sunSize = rand.nextFloat() * 1.5f + 0.5f, // Base size variance
                moonCount = moonCount,
                moonSize = rand.nextFloat() * 2f + 0.5f,
                starDensity = starDensity,
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
            spawning = SpawnSettings(
                noMobs = noMobs, 
                hostileMultiplier = hostileMult, 
                passiveMultiplier = passiveMult
            ),
            stability = StabilityProfile(
                isStable = finalInstability <= 0,
                instabilityScore = finalInstability
            ),
            modifiers = activeModifiers 
        )
    }

    fun saveAndUnload(server: MinecraftServer, ageId: Identifier) {
        val profile = profileCache.remove(ageId) ?: return
        profile.time.savedTime = profile.time.liveTimeOfDay
        
        val dir = server.getSavePath(WorldSavePath.ROOT).resolve("mystcraft_profiles")
        val file = dir.resolve("${ageId.path}.json")
        Files.writeString(file, profile.toJson())
        MystcraftReforged.LOGGER.info("Saved and unloaded AgeProfile: ${file.fileName}")
    }
}