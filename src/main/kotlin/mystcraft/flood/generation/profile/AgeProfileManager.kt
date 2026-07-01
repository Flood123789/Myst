package mystcraft.flood.generation.profile

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.AgeCompiler
import mystcraft.flood.generation.AgeCurseManager
import mystcraft.flood.generation.AgeSubdimensionManager
import mystcraft.flood.generation.AmbientAgeThemes
import mystcraft.flood.generation.ChaosAgeThemes
import mystcraft.flood.generation.ExoticAgeThemes
import mystcraft.flood.generation.HistoricAgeThemes
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import net.minecraft.util.WorldSavePath
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

object AgeProfileManager {
    private val profileCache = ConcurrentHashMap<Identifier, AgeProfile>()
    private val pendingTerrainTuning = ConcurrentHashMap<Identifier, TerrainTuningProfile>()

    private fun normalizeSymbol(symbol: String): String =
        symbol.lowercase().replace("mystcraft-reforged:", "")

    private fun weightedRandomTerrain(rand: Random): TerrainType {
        val pool = buildList {
            repeat(40) { add(TerrainType.STANDARD) }
            repeat(9) { add(TerrainType.BETA) }
            repeat(5) { add(TerrainType.ALPHA) }
            repeat(10) { add(TerrainType.AMPLIFIED) }
            repeat(18) { add(TerrainType.FLOATING_ISLANDS) }
            repeat(16) { add(TerrainType.CAVES) }
            repeat(14) { add(TerrainType.FLAT) }
            repeat(3) { add(TerrainType.BIOSPHERES) }
            repeat(2) { add(TerrainType.CITIES) }
            repeat(1) { add(TerrainType.VOID) }
        }
        return pool.random(rand)
    }

    private fun randomAgeEffectId(rand: Random): String? {
        val eligible = Registries.STATUS_EFFECT.ids.filter { id ->
            Registries.STATUS_EFFECT.get(id)?.isInstant == false
        }
        if (eligible.isEmpty()) return null
        return eligible.random(rand).toString()
    }

    @JvmOverloads
    fun getOrGenerateProfile(server: MinecraftServer, ageId: Identifier, symbols: List<String> = emptyList()): AgeProfile {
        val role = AgeSubdimensionManager.roleOf(ageId)
        val rootAgeId = AgeSubdimensionManager.rootIdOf(ageId)
        profileCache[ageId]?.let { cached ->
            if (role != AgeDimensionRole.OVERWORLD && rootAgeId != ageId) {
                val rootProfile = getOrGenerateProfile(server, rootAgeId)
                refreshDerivedProfile(ageId, role, rootAgeId, rootProfile, cached)
            }
            return cached
        }

        val dir = server.getSavePath(WorldSavePath.ROOT).resolve("mystcraft_profiles")
        if (!Files.exists(dir)) Files.createDirectories(dir)

        val file = profileFile(server, ageId)
        val profile = if (Files.exists(file)) {
            AgeProfile.fromJson(Files.readString(file))
        } else {
            // Pass the server down so we can read the live Biome Registry!
            generateNewProfile(server, ageId, symbols).also { newProfile ->
                Files.writeString(file, newProfile.toJson())
                MystcraftReforged.LOGGER.info("Saved new AgeProfile to disk: ${file.fileName}")
            }
        }

        AgeCurseManager.refresh(profile)

        if (role != AgeDimensionRole.OVERWORLD && rootAgeId != ageId) {
            val rootProfile = getOrGenerateProfile(server, rootAgeId)
            if (refreshDerivedProfile(ageId, role, rootAgeId, rootProfile, profile)) {
                Files.writeString(file, profile.toJson())
            }
        }

        profile.time.liveTimeOfDay = profile.time.savedTime ?: 6000L
        profileCache[ageId] = profile
        return profile
    }

    fun registerPendingTerrainTuning(ageId: Identifier, tuning: TerrainTuningProfile) {
        val normalized = tuning.normalized()
        if (!normalized.isEdited()) {
            pendingTerrainTuning.remove(ageId)
            return
        }
        pendingTerrainTuning[ageId] = normalized
    }

    private fun generateNewProfile(server: MinecraftServer, ageId: Identifier, symbols: List<String>): AgeProfile {
        val role = AgeSubdimensionManager.roleOf(ageId)
        val rootAgeId = AgeSubdimensionManager.rootIdOf(ageId)
        if (role != AgeDimensionRole.OVERWORLD && rootAgeId != ageId) {
            val rootProfile = getOrGenerateProfile(server, rootAgeId)
            return buildDerivedProfile(ageId, role, rootAgeId, rootProfile, null)
        }

        val terrainTuning = pendingTerrainTuning.remove(ageId)?.normalized() ?: TerrainTuningProfile()
        val compiled = AgeCompiler.compile(symbols)
        val rand = Random(ageId.toString().hashCode().toLong())
        val normalizedSymbols = symbols.map(::normalizeSymbol)
        val usedRandomPage = normalizedSymbols.any { it == "random" }
        val hasExplicitTerrainPage = normalizedSymbols.any { it.startsWith("terrain_") || it == "city" || it == "cities" || it == "biospheres" }
        val hasExplicitBiomePages = compiled.biomes.isNotEmpty() || compiled.biomeController != null
        val hasExplicitTimePage = compiled.timeMode != null
        val hasExplicitWeatherPage = compiled.weatherMode != null
        val hasExplicitGravityPage = normalizedSymbols.any { it == "low_gravity" }
        
        var modifierInstability = 0
        val activeModifiers = mutableListOf<String>()
        var selectedAgeEffectId = compiled.ageEffectId
        val explicitInstabilityFeaturePages = mutableSetOf<String>()
        var authoredSkyPageCount = 0

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
        var gravityScale = if (compiled.lowGravity) 0.45f else 1.0f

        // 1. SCAN EXPLICIT PAGES
        for (symbol in normalizedSymbols) {
            val originalSymbol = symbol
            var cleanSymbol = symbol
            
            if (cleanSymbol == "random") {
                val wildcards = listOf(
                    "tendrils", "obelisks", "giant_trees", "crystal_formations", "dense_ores",
                    HistoricAgeThemes.ANCIENT_BONES, HistoricAgeThemes.FORGOTTEN_RUINS,
                    HistoricAgeThemes.COLLAPSED_OBSERVATORY, HistoricAgeThemes.ANCIENT_AQUEDUCTS, HistoricAgeThemes.GATEWAY_RUINS,
                    AmbientAgeThemes.PAGE_STORMS, AmbientAgeThemes.MEMORY_BLOOMS, AmbientAgeThemes.STABLE_SANCTUARIES
                ) + ChaosAgeThemes.SKY + listOf(
                    ChaosAgeThemes.METEOR_SHOWERS, ChaosAgeThemes.SKY_SPHERES,
                    ChaosAgeThemes.PARTICLE_MOTES, ChaosAgeThemes.PARTICLE_ASH, ChaosAgeThemes.PARTICLE_SPORES, ChaosAgeThemes.PARTICLE_VOID,
                    "spawning_no_mobs", "spawning_extra_hostile", "sun_red", "moon_extra"
                )
                cleanSymbol = wildcards.random(rand)
                modifierInstability += 5
            }

            fun addSkyModifier(symbol: String, randomInstability: Int, authoredStability: Int, starMinimum: Int = 0) {
                if (activeModifiers.contains(symbol)) return
                activeModifiers.add(symbol)
                modifierInstability += if (originalSymbol == "random") randomInstability else -authoredStability
                if (originalSymbol != "random") authoredSkyPageCount++
                if (starMinimum > 0) {
                    starDensity = starDensity.coerceAtLeast(starMinimum)
                    hasStarPages = true
                }
            }

            when (cleanSymbol) {
                // === MODIFIERS ===
                "dense_ores" -> if (!activeModifiers.contains("dense_ores")) { activeModifiers.add("dense_ores"); modifierInstability += 75; if (originalSymbol != "random") explicitInstabilityFeaturePages.add("dense_ores") }
                "giant_trees" -> if (!activeModifiers.contains("giant_trees")) { activeModifiers.add("giant_trees"); modifierInstability += 15; if (originalSymbol != "random") explicitInstabilityFeaturePages.add("giant_trees") }
                "crystal_formations" -> if (!activeModifiers.contains("crystal_formations")) { activeModifiers.add("crystal_formations"); modifierInstability += 10; if (originalSymbol != "random") explicitInstabilityFeaturePages.add("crystal_formations") }
                "tendrils" -> if (!activeModifiers.contains("tendrils")) { activeModifiers.add("tendrils"); modifierInstability += 20; if (originalSymbol != "random") explicitInstabilityFeaturePages.add("tendrils") }
                "obelisks" -> if (!activeModifiers.contains("giant_obelisks")) { activeModifiers.add("giant_obelisks"); modifierInstability += 15; if (originalSymbol != "random") explicitInstabilityFeaturePages.add("obelisks") }
                HistoricAgeThemes.ANCIENT_BONES -> if (!activeModifiers.contains(HistoricAgeThemes.ANCIENT_BONES)) { activeModifiers.add(HistoricAgeThemes.ANCIENT_BONES); modifierInstability += 14; if (originalSymbol != "random") explicitInstabilityFeaturePages.add(HistoricAgeThemes.ANCIENT_BONES) }
                HistoricAgeThemes.FORGOTTEN_RUINS -> if (!activeModifiers.contains(HistoricAgeThemes.FORGOTTEN_RUINS)) { activeModifiers.add(HistoricAgeThemes.FORGOTTEN_RUINS); modifierInstability += 18; if (originalSymbol != "random") explicitInstabilityFeaturePages.add(HistoricAgeThemes.FORGOTTEN_RUINS) }
                HistoricAgeThemes.COLLAPSED_OBSERVATORY -> if (!activeModifiers.contains(HistoricAgeThemes.COLLAPSED_OBSERVATORY)) { activeModifiers.add(HistoricAgeThemes.COLLAPSED_OBSERVATORY); modifierInstability += 16; if (originalSymbol != "random") explicitInstabilityFeaturePages.add(HistoricAgeThemes.COLLAPSED_OBSERVATORY) }
                HistoricAgeThemes.ANCIENT_AQUEDUCTS -> if (!activeModifiers.contains(HistoricAgeThemes.ANCIENT_AQUEDUCTS)) { activeModifiers.add(HistoricAgeThemes.ANCIENT_AQUEDUCTS); modifierInstability += 14; if (originalSymbol != "random") explicitInstabilityFeaturePages.add(HistoricAgeThemes.ANCIENT_AQUEDUCTS) }
                HistoricAgeThemes.GATEWAY_RUINS -> if (!activeModifiers.contains(HistoricAgeThemes.GATEWAY_RUINS)) { activeModifiers.add(HistoricAgeThemes.GATEWAY_RUINS); modifierInstability += 16; if (originalSymbol != "random") explicitInstabilityFeaturePages.add(HistoricAgeThemes.GATEWAY_RUINS) }
                ExoticAgeThemes.HEX -> if (!activeModifiers.contains(ExoticAgeThemes.HEX)) { activeModifiers.add(ExoticAgeThemes.HEX); modifierInstability += 20; if (originalSymbol != "random") explicitInstabilityFeaturePages.add(ExoticAgeThemes.HEX) }
                ExoticAgeThemes.WIRE_CELLS -> if (!activeModifiers.contains(ExoticAgeThemes.WIRE_CELLS)) { activeModifiers.add(ExoticAgeThemes.WIRE_CELLS); modifierInstability += 20; if (originalSymbol != "random") explicitInstabilityFeaturePages.add(ExoticAgeThemes.WIRE_CELLS) }
                ExoticAgeThemes.SEPARATORS -> if (!activeModifiers.contains(ExoticAgeThemes.SEPARATORS)) { activeModifiers.add(ExoticAgeThemes.SEPARATORS); modifierInstability += 20; if (originalSymbol != "random") explicitInstabilityFeaturePages.add(ExoticAgeThemes.SEPARATORS) }
                ExoticAgeThemes.CABLES -> if (!activeModifiers.contains(ExoticAgeThemes.CABLES)) { activeModifiers.add(ExoticAgeThemes.CABLES); modifierInstability += 20; if (originalSymbol != "random") explicitInstabilityFeaturePages.add(ExoticAgeThemes.CABLES) }
                ExoticAgeThemes.FRACTAL_CUBES -> if (!activeModifiers.contains(ExoticAgeThemes.FRACTAL_CUBES)) { activeModifiers.add(ExoticAgeThemes.FRACTAL_CUBES); modifierInstability += 20; if (originalSymbol != "random") explicitInstabilityFeaturePages.add(ExoticAgeThemes.FRACTAL_CUBES) }
                ExoticAgeThemes.LIGHT_FISSURES -> if (!activeModifiers.contains(ExoticAgeThemes.LIGHT_FISSURES)) { activeModifiers.add(ExoticAgeThemes.LIGHT_FISSURES); modifierInstability += 20; if (originalSymbol != "random") explicitInstabilityFeaturePages.add(ExoticAgeThemes.LIGHT_FISSURES) }
                ExoticAgeThemes.VIRUS -> if (!activeModifiers.contains(ExoticAgeThemes.VIRUS)) { activeModifiers.add(ExoticAgeThemes.VIRUS); modifierInstability += 24; if (originalSymbol != "random") explicitInstabilityFeaturePages.add(ExoticAgeThemes.VIRUS) }
                AmbientAgeThemes.PAGE_STORMS -> if (!activeModifiers.contains(AmbientAgeThemes.PAGE_STORMS)) { activeModifiers.add(AmbientAgeThemes.PAGE_STORMS); modifierInstability += 18; if (originalSymbol != "random") explicitInstabilityFeaturePages.add(AmbientAgeThemes.PAGE_STORMS) }
                AmbientAgeThemes.MEMORY_BLOOMS -> if (!activeModifiers.contains(AmbientAgeThemes.MEMORY_BLOOMS)) { activeModifiers.add(AmbientAgeThemes.MEMORY_BLOOMS); modifierInstability += 10; if (originalSymbol != "random") explicitInstabilityFeaturePages.add(AmbientAgeThemes.MEMORY_BLOOMS) }
                AmbientAgeThemes.STABLE_SANCTUARIES -> if (!activeModifiers.contains(AmbientAgeThemes.STABLE_SANCTUARIES)) { activeModifiers.add(AmbientAgeThemes.STABLE_SANCTUARIES); modifierInstability += 8; if (originalSymbol != "random") explicitInstabilityFeaturePages.add(AmbientAgeThemes.STABLE_SANCTUARIES) }
                ChaosAgeThemes.SKY_RAINBOWS -> addSkyModifier(ChaosAgeThemes.SKY_RAINBOWS, 6, 6)
                ChaosAgeThemes.SKY_AURORAS -> addSkyModifier(ChaosAgeThemes.SKY_AURORAS, 8, 7)
                ChaosAgeThemes.SHOOTING_STARS -> addSkyModifier(ChaosAgeThemes.SHOOTING_STARS, 8, 7, 2)
                ChaosAgeThemes.COMETS -> addSkyModifier(ChaosAgeThemes.COMETS, 10, 7, 2)
                ChaosAgeThemes.SKY_RIFTS -> addSkyModifier(ChaosAgeThemes.SKY_RIFTS, 18, 9)
                ChaosAgeThemes.SKY_NEBULAE -> addSkyModifier(ChaosAgeThemes.SKY_NEBULAE, 9, 8, 2)
                ChaosAgeThemes.ECLIPSE_HALOS -> addSkyModifier(ChaosAgeThemes.ECLIPSE_HALOS, 10, 7)
                ChaosAgeThemes.STAR_GLYPHS -> addSkyModifier(ChaosAgeThemes.STAR_GLYPHS, 8, 8, 2)
                ChaosAgeThemes.HORIZON_MIRAGES -> addSkyModifier(ChaosAgeThemes.HORIZON_MIRAGES, 6, 6)
                ChaosAgeThemes.CRYSTAL_HALOS -> addSkyModifier(ChaosAgeThemes.CRYSTAL_HALOS, 9, 7)
                ChaosAgeThemes.VOID_FLECKS -> addSkyModifier(ChaosAgeThemes.VOID_FLECKS, 12, 7, 2)
                ChaosAgeThemes.SPIRAL_GALAXIES -> addSkyModifier(ChaosAgeThemes.SPIRAL_GALAXIES, 12, 8, 3)
                ChaosAgeThemes.FALLING_SKY_SHARDS -> addSkyModifier(ChaosAgeThemes.FALLING_SKY_SHARDS, 14, 8)
                ChaosAgeThemes.LIGHTNING_VEINS -> addSkyModifier(ChaosAgeThemes.LIGHTNING_VEINS, 12, 7)
                ChaosAgeThemes.LUMINOUS_COLUMNS -> addSkyModifier(ChaosAgeThemes.LUMINOUS_COLUMNS, 8, 6)
                ChaosAgeThemes.SKY_MONOLITHS -> addSkyModifier(ChaosAgeThemes.SKY_MONOLITHS, 12, 8)
                ChaosAgeThemes.PRISM_RINGS -> addSkyModifier(ChaosAgeThemes.PRISM_RINGS, 8, 7)
                ChaosAgeThemes.CHROMA_WAVES -> addSkyModifier(ChaosAgeThemes.CHROMA_WAVES, 7, 6)
                ChaosAgeThemes.ORBITAL_GRID -> addSkyModifier(ChaosAgeThemes.ORBITAL_GRID, 9, 7)
                ChaosAgeThemes.SKY_LANTERNS -> addSkyModifier(ChaosAgeThemes.SKY_LANTERNS, 6, 6)
                ChaosAgeThemes.FRACTURE_WEB -> addSkyModifier(ChaosAgeThemes.FRACTURE_WEB, 12, 7)
                ChaosAgeThemes.DREAM_VEILS -> addSkyModifier(ChaosAgeThemes.DREAM_VEILS, 6, 6)
                ChaosAgeThemes.SKY_BUBBLES -> addSkyModifier(ChaosAgeThemes.SKY_BUBBLES, 5, 5)
                ChaosAgeThemes.STARFALL_BLOOMS -> addSkyModifier(ChaosAgeThemes.STARFALL_BLOOMS, 10, 7, 2)
                ChaosAgeThemes.HORIZON_CROWNS -> addSkyModifier(ChaosAgeThemes.HORIZON_CROWNS, 7, 6)
                ChaosAgeThemes.CELESTIAL_SCRIPT -> addSkyModifier(ChaosAgeThemes.CELESTIAL_SCRIPT, 8, 7, 2)
                ChaosAgeThemes.GLASS_CONSTELLATIONS -> addSkyModifier(ChaosAgeThemes.GLASS_CONSTELLATIONS, 9, 7, 2)
                ChaosAgeThemes.RADIANT_WHIRLPOOLS -> addSkyModifier(ChaosAgeThemes.RADIANT_WHIRLPOOLS, 10, 7, 3)
                ChaosAgeThemes.BRIGHT_SKY -> { addSkyModifier(ChaosAgeThemes.BRIGHT_SKY, 6, 5); if (!hasSunPages) sunNormal = sunNormal.coerceAtLeast(1) }
                ChaosAgeThemes.DARK_SKY -> { addSkyModifier(ChaosAgeThemes.DARK_SKY, 10, 6); if (!hasStarPages) starDensity = starDensity.coerceAtLeast(3) }
                ChaosAgeThemes.METEOR_SHOWERS -> if (!activeModifiers.contains(ChaosAgeThemes.METEOR_SHOWERS)) { activeModifiers.add(ChaosAgeThemes.METEOR_SHOWERS); modifierInstability += 18 }
                ChaosAgeThemes.SKY_SPHERES -> if (!activeModifiers.contains(ChaosAgeThemes.SKY_SPHERES)) { activeModifiers.add(ChaosAgeThemes.SKY_SPHERES); modifierInstability += 14 }
                ChaosAgeThemes.PARTICLE_MOTES -> if (!activeModifiers.contains(ChaosAgeThemes.PARTICLE_MOTES)) { activeModifiers.add(ChaosAgeThemes.PARTICLE_MOTES); modifierInstability += 4 }
                ChaosAgeThemes.PARTICLE_ASH -> if (!activeModifiers.contains(ChaosAgeThemes.PARTICLE_ASH)) { activeModifiers.add(ChaosAgeThemes.PARTICLE_ASH); modifierInstability += 6 }
                ChaosAgeThemes.PARTICLE_SPORES -> if (!activeModifiers.contains(ChaosAgeThemes.PARTICLE_SPORES)) { activeModifiers.add(ChaosAgeThemes.PARTICLE_SPORES); modifierInstability += 5 }
                ChaosAgeThemes.PARTICLE_VOID -> if (!activeModifiers.contains(ChaosAgeThemes.PARTICLE_VOID)) { activeModifiers.add(ChaosAgeThemes.PARTICLE_VOID); modifierInstability += 8 }
                
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
        var terrain = when(compiled.terrainType) {
            "ALPHA" -> TerrainType.ALPHA
            "BETA" -> TerrainType.BETA
            "AMPLIFIED" -> TerrainType.AMPLIFIED
            "CAVE" -> TerrainType.CAVES
            "FLOATING_ISLANDS" -> TerrainType.FLOATING_ISLANDS
            "BIOSPHERES" -> TerrainType.BIOSPHERES
            "CITIES" -> TerrainType.CITIES
            "STANDARD" -> TerrainType.STANDARD
            "FLAT" -> TerrainType.FLAT
            "VOID" -> TerrainType.VOID
            else -> weightedRandomTerrain(rand)
        }

        terrain = when {
            terrainTuning.superFlat -> TerrainType.FLAT
            terrainTuning.caveWorld -> TerrainType.CAVES
            else -> terrain
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

        if (selectedAgeEffectId != null) {
            modifierInstability += 65
        } else if ((usedRandomPage || sparseAge) && rand.nextFloat() < 0.015f) {
            selectedAgeEffectId = randomAgeEffectId(rand)
            if (selectedAgeEffectId != null) {
                modifierInstability += 45
            }
        }

        if (compiled.lowGravity) {
            modifierInstability += 22
        } else if ((usedRandomPage || sparseAge) && rand.nextFloat() < 0.06f) {
            gravityScale = 0.45f
            modifierInstability += 14
        }

        var finalInstability = modifierInstability + compiled.conflictInstability

        finalInstability += when {
            sparseAge -> 34
            partiallyDefinedAge -> 16
            else -> 0
        }
        finalInstability += missingBasicCount * 6
        finalInstability -= basicDefinedCount * 4

        if (terrain == TerrainType.FLOATING_ISLANDS) finalInstability += 15
        if (terrain == TerrainType.BETA) finalInstability += 4
        if (terrain == TerrainType.ALPHA) finalInstability += 12
        if (terrain == TerrainType.AMPLIFIED) finalInstability += 20
        if (terrain == TerrainType.CITIES) finalInstability += 38
        if (terrain == TerrainType.BIOSPHERES) finalInstability += 32
        if (terrain == TerrainType.FLAT) finalInstability += 6
        if (terrain == TerrainType.CAVES) finalInstability += 10
        if (terrain == TerrainType.VOID) finalInstability -= 90

        if (timeMode == "fixed") finalInstability += 20
        if (timeMode == "fast" || timeMode == "slow") finalInstability += 8
        if (weatherMode == "endless_storm") finalInstability += 15
        if (weatherMode == "endless_rain") finalInstability += 6
        if (activeModifiers.contains(ChaosAgeThemes.BRIGHT_SKY) && activeModifiers.contains(ChaosAgeThemes.DARK_SKY)) finalInstability += 20
        if (biomesList.size > 3) finalInstability += (biomesList.size - 3) * 10
        finalInstability -= explicitInstabilityFeaturePages.size * 24
        finalInstability -= authoredSkyPageCount * 3

        if (isSingleBiomeAge) finalInstability -= 14
        if (finalBiomeMode == BiomeMode.VANILLA_DISTRIBUTION) finalInstability -= 10
        if (isOverworldLike) finalInstability -= 16
        if (hasExplicitTerrainPage && terrain == TerrainType.STANDARD) finalInstability -= 8
        if (hasExplicitWeatherPage && weatherMode == "normal") finalInstability -= 4
        if (hasExplicitTimePage && timeMode == "normal") finalInstability -= 4
        if (hasExplicitGravityPage.not() && gravityScale < 1.0f) finalInstability += 8

        finalInstability = finalInstability.coerceAtLeast(0)
        val authoredFeaturePages = explicitInstabilityFeaturePages.count { it != "dense_ores" }
        val authoredFeatureRarityFactor = (1.0f - authoredFeaturePages * 0.08f).coerceAtLeast(0.25f)

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
            } * authoredFeatureRarityFactor

            if (rand.nextFloat() < exoticChance) {
                activeModifiers.add(ExoticAgeThemes.random(rand))
            }
        }

        if (!activeModifiers.contains("crystal_formations")) {
            val crystalChance = when {
                usedRandomPage -> 0.62f
                sparseAge -> 0.34f
                finalInstability >= 70 -> 0.28f
                finalInstability >= 40 -> 0.16f
                else -> 0.0f
            } * authoredFeatureRarityFactor
            if (rand.nextFloat() < crystalChance) {
                activeModifiers.add("crystal_formations")
            }
        }

        if (!activeModifiers.contains(HistoricAgeThemes.ANCIENT_BONES) && terrain != TerrainType.BIOSPHERES) {
            val bonesChance = when {
                usedRandomPage -> 0.68f
                sparseAge -> 0.52f
                finalInstability >= 75 -> 0.58f
                finalInstability >= 45 -> 0.34f
                finalInstability >= 20 -> 0.16f
                else -> 0.0f
            } * authoredFeatureRarityFactor
            if (rand.nextFloat() < bonesChance) {
                activeModifiers.add(HistoricAgeThemes.ANCIENT_BONES)
            }
        }

        if (!activeModifiers.contains(HistoricAgeThemes.FORGOTTEN_RUINS) && terrain != TerrainType.BIOSPHERES && terrain != TerrainType.CITIES) {
            val ruinsChance = when {
                usedRandomPage -> 0.54f
                sparseAge -> 0.38f
                isOverworldLike && finalInstability >= 20 -> 0.28f
                finalInstability >= 70 -> 0.46f
                finalInstability >= 40 -> 0.24f
                finalInstability >= 18 -> 0.10f
                else -> 0.0f
            } * authoredFeatureRarityFactor
            if (rand.nextFloat() < ruinsChance) {
                activeModifiers.add(HistoricAgeThemes.FORGOTTEN_RUINS)
            }
        }

        if (!activeModifiers.contains(HistoricAgeThemes.COLLAPSED_OBSERVATORY) && terrain != TerrainType.BIOSPHERES && terrain != TerrainType.CITIES) {
            val observatoryChance = when {
                usedRandomPage -> 0.42f
                sparseAge -> 0.24f
                finalInstability >= 70 -> 0.34f
                finalInstability >= 38 -> 0.16f
                else -> 0.04f
            } * authoredFeatureRarityFactor
            if (rand.nextFloat() < observatoryChance) {
                activeModifiers.add(HistoricAgeThemes.COLLAPSED_OBSERVATORY)
            }
        }

        if (!activeModifiers.contains(HistoricAgeThemes.ANCIENT_AQUEDUCTS) && terrain != TerrainType.BIOSPHERES && terrain != TerrainType.CITIES && terrain != TerrainType.CAVES) {
            val aqueductChance = when {
                usedRandomPage -> 0.38f
                sparseAge -> 0.20f
                finalInstability >= 60 -> 0.24f
                finalInstability >= 30 -> 0.14f
                else -> 0.04f
            } * authoredFeatureRarityFactor
            if (rand.nextFloat() < aqueductChance) {
                activeModifiers.add(HistoricAgeThemes.ANCIENT_AQUEDUCTS)
            }
        }

        if (!activeModifiers.contains(HistoricAgeThemes.GATEWAY_RUINS) && terrain != TerrainType.BIOSPHERES && terrain != TerrainType.CAVES) {
            val gatewayChance = when {
                usedRandomPage -> 0.40f
                sparseAge -> 0.23f
                finalInstability >= 65 -> 0.28f
                finalInstability >= 35 -> 0.14f
                else -> 0.03f
            } * authoredFeatureRarityFactor
            if (rand.nextFloat() < gatewayChance) {
                activeModifiers.add(HistoricAgeThemes.GATEWAY_RUINS)
            }
        }

        if (!activeModifiers.contains(AmbientAgeThemes.PAGE_STORMS) && terrain != TerrainType.BIOSPHERES && terrain != TerrainType.CITIES) {
            val stormChance = when {
                usedRandomPage -> 0.56f
                sparseAge -> 0.30f
                finalInstability >= 80 -> 0.44f
                finalInstability >= 50 -> 0.22f
                else -> 0.0f
            } * authoredFeatureRarityFactor
            if (rand.nextFloat() < stormChance) {
                activeModifiers.add(AmbientAgeThemes.PAGE_STORMS)
            }
        }

        if (!activeModifiers.contains(AmbientAgeThemes.MEMORY_BLOOMS)) {
            val bloomChance = when {
                usedRandomPage -> 0.40f
                sparseAge -> 0.18f
                finalInstability >= 45 -> 0.26f
                finalInstability >= 18 -> 0.12f
                else -> 0.06f
            } * authoredFeatureRarityFactor
            if (rand.nextFloat() < bloomChance) {
                activeModifiers.add(AmbientAgeThemes.MEMORY_BLOOMS)
            }
        }

        if (!activeModifiers.contains(AmbientAgeThemes.STABLE_SANCTUARIES) && terrain != TerrainType.BIOSPHERES) {
            val sanctuaryChance = when {
                usedRandomPage -> 0.28f
                sparseAge -> 0.14f
                finalInstability in 18..65 -> 0.18f
                finalInstability < 18 -> 0.12f
                else -> 0.08f
            } * authoredFeatureRarityFactor
            if (rand.nextFloat() < sanctuaryChance) {
                activeModifiers.add(AmbientAgeThemes.STABLE_SANCTUARIES)
            }
        }

        run {
            val existingSkyCount = activeModifiers.count { it in ChaosAgeThemes.SKY }
            val missingSkyPressure = missingBasicCount +
                if (usedRandomPage) 3 else 0 +
                if (sparseAge) 2 else 0 +
                if (finalInstability >= 60) 1 else 0
            val desiredSkyPages = when {
                usedRandomPage -> rand.nextInt(3, 6)
                missingSkyPressure >= 8 -> rand.nextInt(3, 6)
                missingSkyPressure >= 6 -> rand.nextInt(2, 5)
                missingSkyPressure >= 4 -> if (rand.nextFloat() < 0.78f) rand.nextInt(1, 4) else 0
                missingSkyPressure >= 2 -> if (rand.nextFloat() < 0.42f) 1 else 0
                finalInstability >= 55 -> if (rand.nextFloat() < 0.36f) 1 else 0
                else -> if (rand.nextFloat() < 0.10f) 1 else 0
            }.coerceAtMost(if (usedRandomPage || sparseAge) 5 else 3)

            var skyCount = existingSkyCount
            var attempts = 0
            while (skyCount < desiredSkyPages && attempts < ChaosAgeThemes.SKY.size * 2) {
                attempts++
                val candidate = ChaosAgeThemes.randomSky(rand)
                if (candidate !in activeModifiers) {
                    activeModifiers.add(candidate)
                    skyCount++
                }
            }
        }

        if (activeModifiers.none { it in ChaosAgeThemes.TERRAIN } && terrain != TerrainType.BIOSPHERES && terrain != TerrainType.CITIES) {
            val terrainPersonalityChance = when {
                usedRandomPage -> 0.36f
                sparseAge -> 0.20f
                finalInstability >= 60 -> 0.24f
                finalInstability >= 30 -> 0.10f
                else -> 0.03f
            }
            if (rand.nextFloat() < terrainPersonalityChance) {
                activeModifiers.add(ChaosAgeThemes.randomTerrain(rand))
            }
        }

        if (activeModifiers.none { it in ChaosAgeThemes.PARTICLES }) {
            val particlePersonalityChance = when {
                usedRandomPage -> 0.48f
                sparseAge -> 0.28f
                partiallyDefinedAge -> 0.18f
                finalInstability >= 45 -> 0.22f
                else -> 0.06f
            }
            if (rand.nextFloat() < particlePersonalityChance) {
                activeModifiers.add(ChaosAgeThemes.randomParticles(rand))
            }
        }

        fun randomRGB(): Int {
            val argb = java.awt.Color.HSBtoRGB(rand.nextFloat(), 0.5f + rand.nextFloat() * 0.5f, 0.7f + rand.nextFloat() * 0.3f)
            return argb and 0x00FFFFFF
        }

        fun blendChannel(value: Int, target: Int, amount: Float): Int =
            (value + (target - value) * amount).toInt().coerceIn(0, 255)

        fun blendColor(color: Int, target: Int, amount: Float): Int {
            val red = blendChannel((color shr 16) and 0xFF, (target shr 16) and 0xFF, amount)
            val green = blendChannel((color shr 8) and 0xFF, (target shr 8) and 0xFF, amount)
            val blue = blendChannel(color and 0xFF, target and 0xFF, amount)
            return (red shl 16) or (green shl 8) or blue
        }

        fun atmosphereColor(color: Int): Int {
            var result = color
            if (activeModifiers.contains(ChaosAgeThemes.BRIGHT_SKY)) {
                result = blendColor(result, 0xFFF7CC, 0.34f)
            }
            if (activeModifiers.contains(ChaosAgeThemes.DARK_SKY)) {
                result = blendColor(result, 0x050713, 0.52f)
            }
            return result
        }

        val skyColor = atmosphereColor(compiled.skyColor ?: randomRGB())
        val fogColor = atmosphereColor(compiled.fogColor ?: randomRGB())
        val ambientColor = atmosphereColor(compiled.ambientColor ?: compiled.fogColor ?: randomRGB())
        val cloudColor = atmosphereColor(compiled.cloudColor ?: compiled.fogColor ?: randomRGB())

        return AgeProfile(
            id = ageId.toString(),
            seed = rand.nextLong(),
            terrainType = terrain,
            colors = ColorSettings(
                sky = skyColor,
                fog = fogColor,
                water = compiled.waterColor ?: randomRGB(),
                grass = compiled.grassColor ?: randomRGB(),     
                foliage = compiled.foliageColor ?: randomRGB(),
                ambient = ambientColor,
                cloud = cloudColor,
                fireLava = compiled.fireLavaColor ?: 0xFF6A00
            ),
            cloudHeight = compiled.cloudHeight ?: when (terrain) {
                TerrainType.FLOATING_ISLANDS -> 160.0f
                TerrainType.ALPHA, TerrainType.AMPLIFIED -> 208.0f
                TerrainType.CAVES, TerrainType.VOID -> 128.0f
                else -> 192.0f
            },
            time = TimeSettings(
                sunNormalCount = sunNormal,
                sunRedCount = sunRed,
                sunBlueCount = sunBlue,
                sunSize = rand.nextFloat() * 1.5f + 0.5f,
                moonCount = moonCount,
                moonSize = rand.nextFloat() * 1.5f + 0.5f,
                starDensity = starDensity,
                fixedTime = if (timeMode == "fixed") compiled.fixedTimeOfDay ?: rand.nextLong(0, 24000) else null,
                timeScale = when(timeMode) {
                    "fast" -> 5.0f * compiled.timeScaleMultiplier
                    "slow" -> 0.2f * compiled.timeScaleMultiplier
                    else -> 1.0f
                }
            ),
            weather = WeatherSettings(
                isEndlessRain = weatherMode == "endless_rain",
                isEndlessStorm = weatherMode == "endless_storm",
                noWeather = weatherMode == "no_weather",
                currentRaining = weatherMode == "endless_rain" || weatherMode == "endless_storm",
                currentThundering = weatherMode == "endless_storm",
                clearTicks = if (weatherMode == "normal") rand.nextInt(6000, 18000) else 0,
                rainTicks = 0,
                thunderTicks = 0
            ),
            biomes = BiomeSet(
                mode = finalBiomeMode,
                biomes = biomesList
            ),
            spawning = SpawnSettings(noMobs || terrainTuning.noMobs, hostileMult, passiveMult),
            ageEffect = AgeEffectProfile(selectedAgeEffectId, true),
            physics = PhysicsSettings(gravityScale),
            stability = StabilityProfile(finalInstability <= 0, finalInstability),
            terrainTuning = terrainTuning,
            modifiers = CopyOnWriteArrayList(activeModifiers)
        ).also(AgeCurseManager::refresh)
    }

    fun profileExists(server: MinecraftServer, ageId: Identifier): Boolean =
        Files.exists(profileFile(server, ageId))

    fun saveAndUnload(server: MinecraftServer, ageId: Identifier) {
        val profile = profileCache.remove(ageId) ?: return
        profile.time.savedTime = profile.time.liveTimeOfDay
        writeProfile(server, ageId, profile)
    }

    fun save(server: MinecraftServer, ageId: Identifier) {
        profileCache[ageId]?.let { profile ->
            profile.time.savedTime = profile.time.liveTimeOfDay
            writeProfile(server, ageId, profile)
        }
    }

    private fun writeProfile(server: MinecraftServer, ageId: Identifier, profile: AgeProfile) {
        val dir = server.getSavePath(WorldSavePath.ROOT).resolve("mystcraft_profiles")
        if (!Files.exists(dir)) Files.createDirectories(dir)
        val temporaryTimeOverride = profile.time.temporaryTimeOverride
        val temporaryClearTicks = profile.weather.temporaryClearTicks
        try {
            profile.time.temporaryTimeOverride = null
            profile.weather.temporaryClearTicks = 0
            Files.writeString(profileFile(server, ageId), profile.toJson())
        } finally {
            profile.time.temporaryTimeOverride = temporaryTimeOverride
            profile.weather.temporaryClearTicks = temporaryClearTicks
        }
    }

    private fun profileFile(server: MinecraftServer, ageId: Identifier) =
        server.getSavePath(WorldSavePath.ROOT)
            .resolve("mystcraft_profiles")
            .resolve("${ageId.path}.json")

    private fun refreshDerivedProfile(
        ageId: Identifier,
        role: AgeDimensionRole,
        rootAgeId: Identifier,
        rootProfile: AgeProfile,
        target: AgeProfile
    ): Boolean {
        val refreshed = buildDerivedProfile(ageId, role, rootAgeId, rootProfile, target)
        if (refreshed == target) {
            return false
        }

        target.terrainType = refreshed.terrainType
        target.cloudHeight = refreshed.cloudHeight
        target.colors.sky = refreshed.colors.sky
        target.colors.fog = refreshed.colors.fog
        target.colors.water = refreshed.colors.water
        target.colors.grass = refreshed.colors.grass
        target.colors.foliage = refreshed.colors.foliage
        target.colors.ambient = refreshed.colors.ambient
        target.colors.cloud = refreshed.colors.cloud
        target.colors.fireLava = refreshed.colors.fireLava
        target.time.sunNormalCount = refreshed.time.sunNormalCount
        target.time.sunRedCount = refreshed.time.sunRedCount
        target.time.sunBlueCount = refreshed.time.sunBlueCount
        target.time.sunSize = refreshed.time.sunSize
        target.time.moonCount = refreshed.time.moonCount
        target.time.moonSize = refreshed.time.moonSize
        target.time.starDensity = refreshed.time.starDensity
        target.time.fixedTime = refreshed.time.fixedTime
        target.time.timeScale = refreshed.time.timeScale
        target.time.savedTime = refreshed.time.savedTime
        target.time.temporaryTimeOverride = refreshed.time.temporaryTimeOverride
        target.time.liveTimeOfDay = refreshed.time.liveTimeOfDay
        target.time.timeAccumulator = refreshed.time.timeAccumulator
        target.weather.isEndlessRain = refreshed.weather.isEndlessRain
        target.weather.isEndlessStorm = refreshed.weather.isEndlessStorm
        target.weather.noWeather = refreshed.weather.noWeather
        target.weather.currentRaining = refreshed.weather.currentRaining
        target.weather.currentThundering = refreshed.weather.currentThundering
        target.weather.clearTicks = refreshed.weather.clearTicks
        target.weather.rainTicks = refreshed.weather.rainTicks
        target.weather.thunderTicks = refreshed.weather.thunderTicks
        target.weather.temporaryClearTicks = refreshed.weather.temporaryClearTicks
        target.biomes.mode = refreshed.biomes.mode
        target.biomes.biomes = refreshed.biomes.biomes.map { it.copy() }.toMutableList()
        target.spawning.noMobs = refreshed.spawning.noMobs
        target.spawning.hostileMultiplier = refreshed.spawning.hostileMultiplier
        target.spawning.passiveMultiplier = refreshed.spawning.passiveMultiplier
        target.ageEffect.effectId = refreshed.ageEffect.effectId
        target.ageEffect.enabled = refreshed.ageEffect.enabled
        target.physics.gravityScale = refreshed.physics.gravityScale
        target.ageState.isSacrificed = refreshed.ageState.isSacrificed
        target.ageState.sacrificedAt = refreshed.ageState.sacrificedAt
        target.ageState.sacrificedBy = refreshed.ageState.sacrificedBy
        target.ageState.displayName = refreshed.ageState.displayName
        target.ageState.parentAgeId = refreshed.ageState.parentAgeId
        target.ageState.dimensionRole = refreshed.ageState.dimensionRole
        target.curses = refreshed.curses.copy(
            activeCurses = CopyOnWriteArrayList(refreshed.curses.activeCurses),
            diagnosedCurses = CopyOnWriteArrayList(refreshed.curses.diagnosedCurses),
            cleansedCurses = CopyOnWriteArrayList(refreshed.curses.cleansedCurses)
        )
        target.stability.isStable = refreshed.stability.isStable
        target.stability.instabilityScore = refreshed.stability.instabilityScore
        target.stability.effectsEnabled = refreshed.stability.effectsEnabled
        target.terrainTuning.terrainTurbulence = refreshed.terrainTuning.terrainTurbulence
        target.terrainTuning.seaLevel = refreshed.terrainTuning.seaLevel
        target.terrainTuning.caveDensity = refreshed.terrainTuning.caveDensity
        target.terrainTuning.biomeSize = refreshed.terrainTuning.biomeSize
        target.terrainTuning.verticalRange = refreshed.terrainTuning.verticalRange
        target.terrainTuning.superFlat = refreshed.terrainTuning.superFlat
        target.terrainTuning.noMobs = refreshed.terrainTuning.noMobs
        target.terrainTuning.caveWorld = refreshed.terrainTuning.caveWorld
        target.terrainTuning.noAquifers = refreshed.terrainTuning.noAquifers
        target.modifiers = CopyOnWriteArrayList(refreshed.modifiers)
        return true
    }

    private fun buildDerivedProfile(
        ageId: Identifier,
        role: AgeDimensionRole,
        rootAgeId: Identifier,
        rootProfile: AgeProfile,
        existing: AgeProfile?
    ): AgeProfile {
        val displayName = derivedDisplayName(rootProfile, rootAgeId, role)
        val roleBiomes = when (role) {
            AgeDimensionRole.NETHER -> mutableListOf(
                BiomeWeight("minecraft:nether_wastes", 34),
                BiomeWeight("minecraft:crimson_forest", 22),
                BiomeWeight("minecraft:warped_forest", 18),
                BiomeWeight("minecraft:basalt_deltas", 13),
                BiomeWeight("minecraft:soul_sand_valley", 13)
            )
            AgeDimensionRole.END -> mutableListOf(
                BiomeWeight("minecraft:the_end", 45),
                BiomeWeight("minecraft:end_highlands", 25),
                BiomeWeight("minecraft:end_midlands", 15),
                BiomeWeight("minecraft:small_end_islands", 10),
                BiomeWeight("minecraft:end_barrens", 5)
            )
            AgeDimensionRole.OVERWORLD -> rootProfile.biomes.biomes.map { it.copy() }.toMutableList()
        }

        return AgeProfile(
            id = ageId.toString(),
            seed = rootProfile.seed,
            terrainType = rootProfile.terrainType,
            colors = rootProfile.colors.copy(),
            cloudHeight = rootProfile.cloudHeight,
            time = rootProfile.time.copy(
                liveTimeOfDay = rootProfile.time.savedTime ?: rootProfile.time.liveTimeOfDay,
                timeAccumulator = 0f
            ),
            weather = rootProfile.weather.copy(),
            biomes = BiomeSet(
                mode = when (role) {
                    AgeDimensionRole.NETHER, AgeDimensionRole.END -> BiomeMode.WEIGHTED
                    AgeDimensionRole.OVERWORLD -> rootProfile.biomes.mode
                },
                biomes = roleBiomes
            ),
            spawning = rootProfile.spawning.copy(),
            ageEffect = rootProfile.ageEffect.copy(),
            physics = rootProfile.physics.copy(),
            ageState = AgeState(
                isSacrificed = rootProfile.ageState.isSacrificed,
                sacrificedAt = rootProfile.ageState.sacrificedAt,
                sacrificedBy = rootProfile.ageState.sacrificedBy,
                surfaceSpawnX = existing?.ageState?.surfaceSpawnX,
                surfaceSpawnY = existing?.ageState?.surfaceSpawnY,
                surfaceSpawnZ = existing?.ageState?.surfaceSpawnZ,
                displayName = displayName,
                parentAgeId = rootAgeId.toString(),
                dimensionRole = role.name
            ),
            curses = rootProfile.curses.copy(
                activeCurses = CopyOnWriteArrayList(rootProfile.curses.activeCurses),
                diagnosedCurses = CopyOnWriteArrayList(rootProfile.curses.diagnosedCurses),
                cleansedCurses = CopyOnWriteArrayList(rootProfile.curses.cleansedCurses)
            ),
            stability = rootProfile.stability.copy(),
            terrainTuning = rootProfile.terrainTuning.copy(),
            modifiers = CopyOnWriteArrayList(rootProfile.modifiers)
        ).also(AgeCurseManager::refresh)
    }

    private fun derivedDisplayName(rootProfile: AgeProfile, rootAgeId: Identifier, role: AgeDimensionRole): String {
        val rootName = rootProfile.ageState.displayName
            ?.takeIf { it.isNotBlank() }
            ?: rootAgeId.path
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceFirstChar { it.uppercase() }
        return "$rootName ${role.label}"
    }
}
