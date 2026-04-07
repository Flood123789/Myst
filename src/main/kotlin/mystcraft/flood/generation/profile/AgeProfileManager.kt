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
        val rand = Random(ageId.toString().hashCode().toLong())
        
        var modifierInstability = 0
        val activeModifiers = mutableListOf<String>()

        // Tracker flags to see if the player explicitly defined these categories
        var hasSunPages = false
        var hasMoonPages = false
        var hasStarPages = false
        var hasSpawnPages = false

        // Celestial Counters
        var sunNormal = 0
        var sunRed = 0
        var sunBlue = 0
        var moonCount = 0
        var starDensity = 1 
        
        // Spawning Vars
        var noMobs = false
        var hostileMult = 1.0f
        var passiveMult = 1.0f

        // 1. SCAN EXPLICIT PAGES
        for (symbol in symbols) {
            val cleanSymbol = symbol.replace("mystcraft-reforged:", "")
            when (cleanSymbol) {
                "dense_ores" -> if (!activeModifiers.contains("dense_ores")) { activeModifiers.add("dense_ores"); modifierInstability += 75 }
                "giant_trees" -> if (!activeModifiers.contains("giant_trees")) { activeModifiers.add("giant_trees"); modifierInstability += 15 }
                "crystal_formations" -> if (!activeModifiers.contains("crystal_formations")) { activeModifiers.add("crystal_formations"); modifierInstability += 10 }
                
                "sun_normal" -> { sunNormal++; hasSunPages = true }
                "sun_red" -> { sunRed++; hasSunPages = true; modifierInstability += 10 }
                "sun_blue" -> { sunBlue++; hasSunPages = true; modifierInstability += 10 }
                
                "moon_normal", "moon_extra" -> { moonCount++; hasMoonPages = true; modifierInstability += 5 }
                
                "stars_normal" -> { starDensity = 1; hasStarPages = true }
                "stars_dense" -> { starDensity++; hasStarPages = true }
                "no_stars" -> { starDensity = 0; hasStarPages = true }
                
                "spawning_no_mobs" -> { noMobs = true; hasSpawnPages = true; modifierInstability += 50 }
                "spawning_extra_hostile" -> { hostileMult += 1.0f; hasSpawnPages = true; modifierInstability -= 15 }
                "spawning_extra_passive" -> { passiveMult += 1.0f; hasSpawnPages = true; modifierInstability += 25 }
            }
        }

        // 2. RANDOMIZE MISSING CATEGORIES (The "Chaos Engine")
        // If the player didn't specify, we roll the dice but add a small instability penalty.

        // --- RANDOMIZE SUNS ---
        if (!hasSunPages) {
            // 40% chance for 1 sun, 60% chance for multiple/weird suns
            val roll = rand.nextFloat()
            if (roll < 0.4f) {
                sunNormal = 1
            } else {
                sunNormal = rand.nextInt(0, 3)
                sunRed = rand.nextInt(0, 2)
                sunBlue = rand.nextInt(0, 2)
            }
            // Ensure there is at least one light source
            if (sunNormal == 0 && sunRed == 0 && sunBlue == 0) sunNormal = 1
            modifierInstability += 5 
        }

        // --- RANDOMIZE MOONS ---
        if (!hasMoonPages) {
            // 50% chance for 1 moon, 50% chance for 2-5 moons
            moonCount = if (rand.nextFloat() < 0.5f) 1 else rand.nextInt(2, 6)
            modifierInstability += 5
        }

        if (!hasStarPages) {
            starDensity = if (rand.nextFloat() < 0.1f) 0 else rand.nextInt(1, 4)
            modifierInstability += 2 // Vagueness Tax
        }

        if (!hasSpawnPages) {
            noMobs = rand.nextFloat() < 0.03f // Rare peaceful world
            if (!noMobs) {
                // Rare chance for a "Horde World" if not specified
                if (rand.nextFloat() < 0.1f) hostileMult = rand.nextFloat() * 3f + 1f
                if (rand.nextFloat() < 0.1f) passiveMult = rand.nextFloat() * 3f + 1f
            }
            modifierInstability += 10 // Spawning randomness is dangerous!
        }

        // ==========================================
        // 3. MAP TERRAIN & BIOMES
        // ==========================================
        val terrain = when(compiled.terrainType) {
            "CAVE" -> TerrainType.CAVES
            "FLOATING_ISLANDS" -> TerrainType.FLOATING_ISLANDS
            "STANDARD" -> TerrainType.STANDARD
            else -> TerrainType.entries.random(rand) 
        }

        val biomesList = if (compiled.biomes.isEmpty()) {
            val randomBiomes = listOf("minecraft:plains", "minecraft:desert", "minecraft:forest", "minecraft:jungle", "minecraft:savanna", "minecraft:taiga", "minecraft:swamp", "minecraft:snowy_plains", "minecraft:badlands")
            mutableListOf(BiomeWeight(randomBiomes.random(rand), 100))
        } else {
            val weight = 100 / compiled.biomes.size
            compiled.biomes.map { BiomeWeight(it, weight) }.toMutableList()
        }

        val timeMode = compiled.timeMode ?: listOf("fast", "slow", "fixed", "normal", "normal").random(rand)
        val weatherMode = compiled.weatherMode ?: listOf("endless_rain", "endless_storm", "no_weather", "normal", "normal").random(rand)

        // ==========================================
        // 4. CALCULATE THE FINAL INSTABILITY
        // ==========================================
        var finalInstability = 0 + modifierInstability + compiled.conflictInstability 
        if (terrain == TerrainType.FLOATING_ISLANDS) finalInstability += 15
        if (timeMode == "fixed") finalInstability += 20
        if (weatherMode == "endless_storm") finalInstability += 15
        if (biomesList.size > 3) finalInstability += (biomesList.size - 3) * 10

        return AgeProfile(
            id = ageId.toString(),
            seed = rand.nextLong(),
            terrainType = terrain,
            colors = ColorSettings(
                sky = compiled.skyColor ?: java.awt.Color.HSBtoRGB(rand.nextFloat(), 0.5f + rand.nextFloat() * 0.5f, 0.7f + rand.nextFloat() * 0.3f) and 0xFFFFFF,
                fog = compiled.fogColor ?: java.awt.Color.HSBtoRGB(rand.nextFloat(), 0.5f + rand.nextFloat() * 0.5f, 0.7f + rand.nextFloat() * 0.3f) and 0xFFFFFF,
                water = compiled.waterColor ?: 0x3F76E4,
                grass = compiled.grassColor ?: 0x91BD59,     
                foliage = compiled.foliageColor ?: 0x77AB2F  
            ),
            time = TimeSettings(
                sunNormalCount = sunNormal,
                sunRedCount = sunRed,
                sunBlueCount = sunBlue,
                sunSize = rand.nextFloat() * 1.5f + 0.5f,
                moonCount = moonCount,
                moonSize = rand.nextFloat() * 1.5f + 0.5f,
                starDensity = starDensity,
                fixedTime = if (timeMode == "fixed") rand.nextLong(0, 24000) else null,
                timeScale = when(timeMode) {
                    "fast" -> 5.0f * compiled.timeScaleMultiplier
                    "slow" -> 0.2f * compiled.timeScaleMultiplier
                    else -> 1.0f
                }
            ),
            weather = WeatherSettings(
                isEndlessRain = weatherMode == "endless_rain",
                isEndlessStorm = weatherMode == "endless_storm",
                noWeather = weatherMode == "no_weather"
            ),
            biomes = BiomeSet(
                mode = if (compiled.biomes.size <= 1) BiomeMode.SINGLE else BiomeMode.WEIGHTED,
                biomes = biomesList
            ),
            spawning = SpawnSettings(noMobs, hostileMult, passiveMult),
            stability = StabilityProfile(finalInstability <= 0, finalInstability),
            modifiers = activeModifiers 
        )
    }

    fun saveAndUnload(server: MinecraftServer, ageId: Identifier) {
        val profile = profileCache.remove(ageId) ?: return
        profile.time.savedTime = profile.time.liveTimeOfDay
        val dir = server.getSavePath(WorldSavePath.ROOT).resolve("mystcraft_profiles")
        Files.writeString(dir.resolve("${ageId.path}.json"), profile.toJson())
    }
}