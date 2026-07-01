package mystcraft.flood.generation

import net.minecraft.registry.Registries
import net.minecraft.util.Identifier

// This class MUST match what AgeProfileManager expects!
data class CompiledAgeData(
    var terrainType: String? = null,
    val biomes: MutableList<String> = mutableListOf(),
    var biomeController: String? = null, // <--- Added Biome Controller
    var timeMode: String? = null,
    var fixedTimeOfDay: Long? = null,
    var timeScaleMultiplier: Float = 1.0f,
    var weatherMode: String? = null,
    
    var skyColor: Int? = null,
    var fogColor: Int? = null,
    var waterColor: Int? = null,
    var grassColor: Int? = null,
    var foliageColor: Int? = null,
    var ambientColor: Int? = null,
    var cloudColor: Int? = null,
    var fireLavaColor: Int? = null,
    var cloudHeight: Float? = null,
    var ageEffectId: String? = null,
    var lowGravity: Boolean = false,
    
    var conflictInstability: Int = 0 // Tracks "bad grammar" penalties
)

object AgeCompiler {
    fun compile(symbols: List<String>): CompiledAgeData {
        val data = CompiledAgeData()
        
        // Memory banks for sequential grammar
        val pendingColors = mutableListOf<Int>()
        val ageEffectCandidates = mutableListOf<String>()
        var ageEffectTargetCount = 0
        
        // Conflict trackers to handle winners/losers at the end
        val terrains = mutableListOf<String>()
        val times = mutableListOf<String>()
        val weathers = mutableListOf<String>()
        val biomeControllers = mutableListOf<String>() // <--- Tracker for controllers
        
        for (symbol in symbols) {
            var clean = symbol.lowercase().replace("mystcraft-reforged:", "")
            
            // --- THE WILDCARD INTERCEPTOR ---
            if (clean == "random") {
                // Pick a random chaotic feature for the compiler to inject!
                val wildcards = listOf(
                    "floating_islands", "amplified", "alpha", "beta", "cave", "flat", "biospheres", "cities",
                    "time_fast", "time_fixed", 
                    "weather_storm", "weather_rain", "weather_normal",
                    "red", "purple", "black", "green",
                    "biome_checkerboard", "biome_vanilla", "low_gravity",
                    HistoricAgeThemes.COLLAPSED_OBSERVATORY, HistoricAgeThemes.ANCIENT_AQUEDUCTS, HistoricAgeThemes.GATEWAY_RUINS,
                    AmbientAgeThemes.PAGE_STORMS, AmbientAgeThemes.MEMORY_BLOOMS, AmbientAgeThemes.STABLE_SANCTUARIES
                ) + ChaosAgeThemes.SKY + listOf(
                    ChaosAgeThemes.METEOR_SHOWERS, ChaosAgeThemes.SKY_SPHERES,
                    ChaosAgeThemes.PARTICLE_MOTES, ChaosAgeThemes.PARTICLE_ASH, ChaosAgeThemes.PARTICLE_SPORES, ChaosAgeThemes.PARTICLE_VOID
                )
                clean = if (kotlin.random.Random.nextFloat() < 0.01f) "void" else wildcards.random()
                data.conflictInstability += 5 // A small "Chaos Tax" for using wildcard pages
            }
            
            // 1. Parse Custom Hex Color from Anvil ("color_custom:#FF00AA")
            if (clean.startsWith("color_custom:#")) {
                val hex = clean.substringAfter("#")
                try {
                    // Convert Hex string to Int
                    pendingColors.add(hex.toInt(16))
                } catch (e: Exception) {
                    data.conflictInstability += 10 // Heavy penalty for broken hex code!
                }
                continue
            }
            
            when {
                // === COLORS (Modifiers) ===
                clean.contains("red") -> pendingColors.add(0xFF0000)
                clean.contains("blue") -> pendingColors.add(0x0000FF)
                clean.contains("green") -> pendingColors.add(0x00FF00)
                clean.contains("black") -> pendingColors.add(0x000000)
                clean.contains("white") -> pendingColors.add(0xFFFFFF)
                clean.contains("yellow") -> pendingColors.add(0xFFFF00)
                clean.contains("purple") -> pendingColors.add(0x800080)
                
                // === TARGETS (They "consume" the colors in memory) ===
                clean.contains("color_sky") -> { 
                    data.skyColor = pendingColors.lastOrNull()
                    if (pendingColors.size > 1) data.conflictInstability += (pendingColors.size - 1) * 10
                    pendingColors.clear() 
                }
                clean.contains("color_fog") -> { 
                    data.fogColor = pendingColors.lastOrNull()
                    if (pendingColors.size > 1) data.conflictInstability += (pendingColors.size - 1) * 10
                    pendingColors.clear() 
                }
                clean.contains("color_water") -> { data.waterColor = pendingColors.lastOrNull(); pendingColors.clear() }
                clean.contains("color_grass") -> { data.grassColor = pendingColors.lastOrNull(); pendingColors.clear() }
                clean.contains("color_foliage") -> { data.foliageColor = pendingColors.lastOrNull(); pendingColors.clear() }
                clean.contains("color_ambient") -> { data.ambientColor = pendingColors.lastOrNull(); pendingColors.clear() }
                clean.contains("color_cloud") -> { data.cloudColor = pendingColors.lastOrNull(); pendingColors.clear() }
                clean.contains("color_fire_lava") -> { data.fireLavaColor = pendingColors.lastOrNull(); pendingColors.clear() }
                clean.contains("cloud_height_low") -> data.cloudHeight = 96.0f
                clean.contains("cloud_height_normal") -> data.cloudHeight = 192.0f
                clean.contains("cloud_height_high") -> data.cloudHeight = 256.0f

                // === AGE EFFECTS ===
                clean == "age_effect" -> {
                    ageEffectTargetCount++
                }
                
                // === TERRAIN TYPES ===
                clean == "terrain_floating_islands" || clean == "floating_islands" -> terrains.add("FLOATING_ISLANDS")
                clean == "terrain_alpha" || clean == "alpha" -> terrains.add("ALPHA")
                clean == "terrain_beta" || clean == "beta" -> terrains.add("BETA")
                clean == "terrain_amplified" || clean == "amplified" -> terrains.add("AMPLIFIED")
                clean == "terrain_caves" || clean == "cave" || clean == "caves" -> terrains.add("CAVE")
                clean == "terrain_biospheres" || clean == "biospheres" || clean == "biosphere" -> terrains.add("BIOSPHERES")
                clean == "terrain_cities" || clean == "cities" || clean == "city" -> terrains.add("CITIES")
                clean == "terrain_standard" || clean == "standard" -> terrains.add("STANDARD")
                clean == "terrain_flat" || clean == "flat" -> terrains.add("FLAT")
                clean == "terrain_void" || clean == "void" -> terrains.add("VOID")
                
                // === TIME MODES ===
                clean.contains("time_fast") -> times.add("fast")
                clean.contains("time_slow") -> times.add("slow")
                clean.contains("time_day") -> times.add("fixed:1000")
                clean.contains("time_noon") -> times.add("fixed:6000")
                clean.contains("time_night") -> times.add("fixed:13000")
                clean.contains("time_midnight") -> times.add("fixed:18000")
                clean.contains("time_fixed") -> times.add("fixed")
                
                // === WEATHER MODES ===
                clean.contains("weather_rain") -> weathers.add("endless_rain")
                clean.contains("weather_storm") || clean.contains("thunder") -> weathers.add("endless_storm")
                clean.contains("weather_clear") || clean.contains("no_weather") -> weathers.add("no_weather")
                clean.contains("weather_normal") -> weathers.add("normal")
                clean == "low_gravity" -> data.lowGravity = true

                // === BIOME CONTROLLERS ===
                clean.contains("checkerboard") -> biomeControllers.add("CHECKERBOARD")
                clean.contains("vanilla") || clean.contains("native") -> biomeControllers.add("VANILLA")
                
                // === STATUS EFFECT SYMBOLS ===
                isStatusEffectId(clean) -> ageEffectCandidates.add(clean)

                // === BIOMES ===
                clean.contains(":") -> data.biomes.add(clean)
            }
        }
        
        // ----------------------------------------------------
        // CONFLICT RESOLUTION ENGINE
        // ----------------------------------------------------
        
        // Unused modifiers cause instability (Grammar Leaks!)
        if (pendingColors.isNotEmpty()) {
            data.conflictInstability += pendingColors.size * 25
        }

        if (ageEffectTargetCount > 0) {
            val selectedEffect = ageEffectCandidates.lastOrNull()
            if (selectedEffect == null) {
                data.conflictInstability += ageEffectTargetCount * 20
            } else {
                if (ageEffectCandidates.distinct().size > 1) {
                    data.conflictInstability += (ageEffectCandidates.distinct().size - 1) * 12
                }
                if (ageEffectTargetCount > 1) {
                    data.conflictInstability += (ageEffectTargetCount - 1) * 8
                }
                data.ageEffectId = selectedEffect
            }
        } else if (ageEffectCandidates.isNotEmpty()) {
            data.conflictInstability += ageEffectCandidates.size * 15
        }
        
        // Terrain Conflict: Floating Islands AND Caves? Pick 1, add instability
        if (terrains.isNotEmpty()) {
            if (terrains.distinct().size > 1) {
                data.conflictInstability += (terrains.distinct().size - 1) * 30
            }
            data.terrainType = terrains.last()
        }

        // Biome Controller Conflict
        if (biomeControllers.isNotEmpty()) {
            if (biomeControllers.distinct().size > 1) {
                data.conflictInstability += 20 // Grammar conflict: can't be vanilla distribution AND checkerboard
            }
            data.biomeController = biomeControllers.last()
        }
        
        // Weather Conflict
        if (weathers.isNotEmpty()) {
            if (weathers.distinct().size > 1) {
                data.conflictInstability += (weathers.distinct().size - 1) * 15
            }
            data.weatherMode = weathers.last()
        }
        
        // Time Conflict: Fast + Slow fighting? Chaos.
        val timeModes = times.map { it.substringBefore(":") }
        val fastCount = timeModes.count { it == "fast" }
        val slowCount = timeModes.count { it == "slow" }
        
        if (times.isNotEmpty()) {
            val distinctTimes = timeModes.distinct()
            if (distinctTimes.size > 1) {
                data.conflictInstability += (distinctTimes.size - 1) * 20
            }
            if (fastCount > 0 && slowCount > 0) {
                data.conflictInstability += 50 // Massive shear!
            }

            data.timeMode = timeModes.last()
            data.fixedTimeOfDay = times.last()
                .substringAfter(":", "")
                .takeIf { it.isNotBlank() }
                ?.toLongOrNull()
            when (data.timeMode) {
                "fast" -> {
                    data.timeScaleMultiplier = fastCount.toFloat()
                    if (fastCount > 1) data.conflictInstability += fastCount * 15
                }
                "slow" -> {
                    data.timeScaleMultiplier = 1.0f / slowCount.toFloat()
                    if (slowCount > 1) data.conflictInstability += slowCount * 15
                }
            }
        }
        
        return data
    }

    private fun isStatusEffectId(symbol: String): Boolean {
        val id = Identifier.tryParse(symbol) ?: return false
        return Registries.STATUS_EFFECT.containsId(id)
    }
}
