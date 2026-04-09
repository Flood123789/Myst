package mystcraft.flood.generation.profile

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.AgeCompiler
import mystcraft.flood.generation.ExoticAgeThemes
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import net.minecraft.util.WorldSavePath
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

object AgeProfileManager {
    private val profileCache = ConcurrentHashMap<Identifier, AgeProfile>()

    private fun normalizeSymbol(symbol: String): String =
        symbol.lowercase().replace("mystcraft-reforged:", "")

    private fun weightedRandomTerrain(rand: Random): TerrainType {
        val pool = buildList {
            repeat(40) { add(TerrainType.STANDARD) }
            repeat(18) { add(TerrainType.FLOATING_ISLANDS) }
            repeat(16) { add(TerrainType.CAVES) }
            repeat(14) { add(TerrainType.FLAT) }
            repeat(3) { add(TerrainType.BIOSPHERES) }
            repeat(2) { add(TerrainType.CITIES) }
        }
        return pool.random(rand)
    }

    @JvmOverloads
    fun getOrGenerateProfile(server: MinecraftServer, ageId: Identifier, symbols: List<String> = emptyList()): AgeProfile {
        profileCache[ageId]?.let { return it }

        val dir = server.getSavePath(WorldSavePath.ROOT).resolve("mystcraft_profiles")
        if (!Files.exists(dir)) Files.createDirectories(dir)

        val file = dir.resolve("${ageId.path}.json")
        val profile = if (Files.exists(file)) {
            AgeProfile.fromJson(Files.readString(file))
        } else {
            // Pass the server down so we can read the live Biome Registry!
            generateNewProfile(server, ageId, symbols).also { newProfile ->
                Files.writeString(file, newProfile.toJson())
                MystcraftReforged.LOGGER.info("Saved new AgeProfile to disk: ${file.fileName}")
            }
        }

        profile.time.liveTimeOfDay = profile.time.savedTime ?: 6000L
        profileCache[ageId] = profile
        return profile
    }

    private fun generateNewProfile(server: MinecraftServer, ageId: Identifier, symbols: List<String>): AgeProfile {
        val compiled = AgeCompiler.compile(symbols)
        val rand = Random(ageId.toString().hashCode().toLong())
        val normalizedSymbols = symbols.map(::normalizeSymbol)
        val usedRandomPage = normalizedSymbols.any { it == "random" }
        val hasExplicitTerrainPage = normalizedSymbols.any { it.startsWith("terrain_") || it == "city" || it == "cities" || it == "biospheres" }
        val hasExplicitBiomePages = compiled.biomes.isNotEmpty() || compiled.biomeController != null
        val hasExplicitTimePage = compiled.timeMode != null
        val hasExplicitWeatherPage = compiled.weatherMode != null
        
        var modifierInstability = 0
        val activeModifiers = mutableListOf<String>()

        var hasSunPages = false
        var hasMoonPages = false
        var hasStarPages = false
        var hasSpawnPages = false

        var sunNormal = 0
        var sunRed = 0
        var sunBlue = 0
        var moonCount = 0
        var starDensity = 1 
        
        var noMobs = false
        var hostileMult = 1.0f
        var passiveMult = 1.0f

        // 1. SCAN EXPLICIT PAGES
        for (symbol in normalizedSymbols) {
            var cleanSymbol = symbol
            
            if (cleanSymbol == "random") {
                val wildcards = listOf(
                    "tendrils", "obelisks", "giant_trees", "crystal_formations", "dense_ores",
                    "spawning_no_mobs", "spawning_extra_hostile", "sun_red", "moon_extra"
                )
                cleanSymbol = wildcards.random(rand)
                modifierInstability += 5
            }

            when (cleanSymbol) {
                // === MODIFIERS ===
                "dense_ores" -> if (!activeModifiers.contains("dense_ores")) { activeModifiers.add("dense_ores"); modifierInstability += 75 }
                "giant_trees" -> if (!activeModifiers.contains("giant_trees")) { activeModifiers.add("giant_trees"); modifierInstability += 15 }
                "crystal_formations" -> if (!activeModifiers.contains("crystal_formations")) { activeModifiers.add("crystal_formations"); modifierInstability += 10 }
                "tendrils" -> if (!activeModifiers.contains("tendrils")) { activeModifiers.add("tendrils"); modifierInstability += 20 }
                "obelisks" -> if (!activeModifiers.contains("giant_obelisks")) { activeModifiers.add("giant_obelisks"); modifierInstability += 15 }
                ExoticAgeThemes.HEX -> if (!activeModifiers.contains(ExoticAgeThemes.HEX)) { activeModifiers.add(ExoticAgeThemes.HEX); modifierInstability += 20 }
                ExoticAgeThemes.WIRE_CELLS -> if (!activeModifiers.contains(ExoticAgeThemes.WIRE_CELLS)) { activeModifiers.add(ExoticAgeThemes.WIRE_CELLS); modifierInstability += 20 }
                ExoticAgeThemes.SEPARATORS -> if (!activeModifiers.contains(ExoticAgeThemes.SEPARATORS)) { activeModifiers.add(ExoticAgeThemes.SEPARATORS); modifierInstability += 20 }
                ExoticAgeThemes.CABLES -> if (!activeModifiers.contains(ExoticAgeThemes.CABLES)) { activeModifiers.add(ExoticAgeThemes.CABLES); modifierInstability += 20 }
                ExoticAgeThemes.FRACTAL_CUBES -> if (!activeModifiers.contains(ExoticAgeThemes.FRACTAL_CUBES)) { activeModifiers.add(ExoticAgeThemes.FRACTAL_CUBES); modifierInstability += 20 }
                ExoticAgeThemes.LIGHT_FISSURES -> if (!activeModifiers.contains(ExoticAgeThemes.LIGHT_FISSURES)) { activeModifiers.add(ExoticAgeThemes.LIGHT_FISSURES); modifierInstability += 20 }
                
                // === CELESTIAL ===
                "sun_normal" -> { sunNormal++; hasSunPages = true }
                "sun_red" -> { sunRed++; hasSunPages = true; modifierInstability += 10 }
                "sun_blue" -> { sunBlue++; hasSunPages = true; modifierInstability += 10 }
                
                "moon_normal", "moon_extra" -> { moonCount++; hasMoonPages = true; modifierInstability += 5 }
                
                "stars_normal" -> { starDensity = 1; hasStarPages = true }
                "stars_dense" -> { starDensity++; hasStarPages = true }
                "no_stars" -> { starDensity = 0; hasStarPages = true }
                
                // === SPAWNING ===
                "spawning_no_mobs" -> { noMobs = true; hasSpawnPages = true; modifierInstability += 50 }
                "spawning_extra_hostile" -> { hostileMult += 1.0f; hasSpawnPages = true; modifierInstability -= 15 }
                "spawning_extra_passive" -> { passiveMult += 1.0f; hasSpawnPages = true; modifierInstability += 25 }
            }
        }

        // 2. RANDOMIZE MISSING CATEGORIES
        if (!hasSunPages) {
            val roll = rand.nextFloat()
            if (roll < 0.4f) {
                sunNormal = 1
            } else {
                sunNormal = rand.nextInt(0, 3)
                sunRed = rand.nextInt(0, 2)
                sunBlue = rand.nextInt(0, 2)
            }
            if (sunNormal == 0 && sunRed == 0 && sunBlue == 0) sunNormal = 1
        }

        if (!hasMoonPages) {
            moonCount = if (rand.nextFloat() < 0.5f) 1 else rand.nextInt(2, 6)
        }

        if (!hasStarPages) {
            starDensity = if (rand.nextFloat() < 0.1f) 0 else rand.nextInt(1, 4)
        }

        if (!hasSpawnPages) {
            noMobs = rand.nextFloat() < 0.03f 
            if (!noMobs) {
                if (rand.nextFloat() < 0.1f) hostileMult = rand.nextFloat() * 3f + 1f
                if (rand.nextFloat() < 0.1f) passiveMult = rand.nextFloat() * 3f + 1f
            }
        }

        // ==========================================
        // 3. MAP TERRAIN & BIOMES
        // ==========================================
        val terrain = when(compiled.terrainType) {
            "CAVE" -> TerrainType.CAVES
            "FLOATING_ISLANDS" -> TerrainType.FLOATING_ISLANDS
            "BIOSPHERES" -> TerrainType.BIOSPHERES
            "CITIES" -> TerrainType.CITIES
            "STANDARD" -> TerrainType.STANDARD
            "FLAT" -> TerrainType.FLAT
            else -> weightedRandomTerrain(rand)
        }

        val finalBiomeMode = when (compiled.biomeController) {
            "CHECKERBOARD" -> BiomeMode.CHECKERBOARD
            "VANILLA" -> BiomeMode.VANILLA_DISTRIBUTION
            else -> if (compiled.biomes.size <= 1) BiomeMode.SINGLE else BiomeMode.WEIGHTED
        }

        val biomesList = if (compiled.biomes.isEmpty()) {
            // Dynamically fetch EVERY biome registered in the game right now (including Mods!)
            val allBiomes = server.registryManager.get(RegistryKeys.BIOME).keys.map { it.value.toString() }
            
            if (finalBiomeMode == BiomeMode.VANILLA_DISTRIBUTION) {
                // Leave it completely empty! This is the signal for the JSON Builder
                // to use the native "minecraft:overworld" multi-noise preset.
                mutableListOf<BiomeWeight>()
            } else if (finalBiomeMode == BiomeMode.CHECKERBOARD) {
                // Checkerboard needs specific biomes to tile, so we pick 3-5 random ones from the whole game
                val shuffled = allBiomes.shuffled(rand).take(rand.nextInt(3, 6))
                val weight = 100 / shuffled.size
                shuffled.map { BiomeWeight(it, weight) }.toMutableList()
            } else {
                // Default Single Biome behavior (but now it can pick modded biomes!)
                mutableListOf(BiomeWeight(allBiomes.random(rand), 100))
            }
        } else {
            val weight = 100 / compiled.biomes.size
            compiled.biomes.map { BiomeWeight(it, weight) }.toMutableList()
        }

        val timeMode = compiled.timeMode ?: listOf("fast", "slow", "fixed", "normal", "normal").random(rand)
        val weatherMode = compiled.weatherMode ?: listOf("endless_rain", "endless_storm", "no_weather", "normal", "normal").random(rand)

        // ==========================================
        // 4. CALCULATE THE FINAL INSTABILITY
        // ==========================================
        val basicDefinedCount = listOf(
            hasExplicitTerrainPage,
            hasExplicitBiomePages,
            hasExplicitTimePage,
            hasSunPages,
            hasMoonPages,
            hasStarPages,
            hasExplicitWeatherPage
        ).count { it }

        val missingBasicCount = 7 - basicDefinedCount
        val sparseAge = symbols.isEmpty() || basicDefinedCount <= 2
        val partiallyDefinedAge = basicDefinedCount in 3..4
        val isSingleBiomeAge = finalBiomeMode == BiomeMode.SINGLE && biomesList.size == 1
        val isOverworldLike =
            terrain == TerrainType.STANDARD &&
                !compiled.biomeController.equals("CHECKERBOARD", ignoreCase = true) &&
                timeMode !in setOf("fast", "slow", "fixed") &&
                weatherMode == "normal"

        var finalInstability = modifierInstability + compiled.conflictInstability

        finalInstability += when {
            sparseAge -> 34
            partiallyDefinedAge -> 16
            else -> 0
        }
        finalInstability += missingBasicCount * 6
        finalInstability -= basicDefinedCount * 4

        if (terrain == TerrainType.FLOATING_ISLANDS) finalInstability += 15
        if (terrain == TerrainType.CITIES) finalInstability += 38
        if (terrain == TerrainType.BIOSPHERES) finalInstability += 32
        if (terrain == TerrainType.FLAT) finalInstability += 6
        if (terrain == TerrainType.CAVES) finalInstability += 10

        if (timeMode == "fixed") finalInstability += 20
        if (timeMode == "fast" || timeMode == "slow") finalInstability += 8
        if (weatherMode == "endless_storm") finalInstability += 15
        if (weatherMode == "endless_rain") finalInstability += 6
        if (biomesList.size > 3) finalInstability += (biomesList.size - 3) * 10

        if (isSingleBiomeAge) finalInstability -= 14
        if (finalBiomeMode == BiomeMode.VANILLA_DISTRIBUTION) finalInstability -= 10
        if (isOverworldLike) finalInstability -= 16
        if (hasExplicitTerrainPage && terrain == TerrainType.STANDARD) finalInstability -= 8
        if (hasExplicitWeatherPage && weatherMode == "normal") finalInstability -= 4
        if (hasExplicitTimePage && timeMode == "normal") finalInstability -= 4

        finalInstability = finalInstability.coerceAtLeast(0)

        if (ExoticAgeThemes.fromModifiers(activeModifiers) == null && terrain != TerrainType.CITIES && terrain != TerrainType.BIOSPHERES) {
            val exoticChance = when {
                usedRandomPage -> 0.98f
                sparseAge -> 0.93f
                partiallyDefinedAge -> 0.62f
                finalInstability >= 70 -> 1.0f
                finalInstability >= 45 -> 0.72f
                finalInstability >= 20 -> 0.35f
                finalInstability > 0 -> 0.10f
                else -> 0.0f
            }

            if (rand.nextFloat() < exoticChance) {
                activeModifiers.add(ExoticAgeThemes.random(rand))
            }
        }

        fun randomRGB(): Int {
            val argb = java.awt.Color.HSBtoRGB(rand.nextFloat(), 0.5f + rand.nextFloat() * 0.5f, 0.7f + rand.nextFloat() * 0.3f)
            return argb and 0x00FFFFFF
        }

        return AgeProfile(
            id = ageId.toString(),
            seed = rand.nextLong(),
            terrainType = terrain,
            colors = ColorSettings(
                sky = compiled.skyColor ?: randomRGB(),
                fog = compiled.fogColor ?: randomRGB(),
                water = compiled.waterColor ?: randomRGB(),
                grass = compiled.grassColor ?: randomRGB(),     
                foliage = compiled.foliageColor ?: randomRGB()  
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
                mode = finalBiomeMode,
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
