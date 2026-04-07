package mystcraft.flood.generation

// This class MUST match what AgeProfileManager expects!
data class CompiledAgeData(
    var terrainType: String? = null,
    val biomes: MutableList<String> = mutableListOf(),
    var timeMode: String? = null,
    var timeScaleMultiplier: Float = 1.0f,
    var weatherMode: String? = null,
    
    var skyColor: Int? = null,
    var fogColor: Int? = null,
    var waterColor: Int? = null,
    var grassColor: Int? = null,
    var foliageColor: Int? = null,
    
    var conflictInstability: Int = 0 // Tracks "bad grammar" penalties
)

object AgeCompiler {
    fun compile(symbols: List<String>): CompiledAgeData {
        val data = CompiledAgeData()
        
        // Memory banks for sequential grammar
        val pendingColors = mutableListOf<Int>()
        
        // Conflict trackers to handle winners/losers at the end
        val terrains = mutableListOf<String>()
        val times = mutableListOf<String>()
        val weathers = mutableListOf<String>()
        
        for (symbol in symbols) {
            val clean = symbol.lowercase().replace("mystcraft-reforged:", "")
            
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
                
                // === TERRAIN TYPES ===
                clean.contains("floating_islands") -> terrains.add("FLOATING_ISLANDS")
                clean.contains("cave") -> terrains.add("CAVE")
                clean.contains("standard") || clean.contains("normal") -> terrains.add("STANDARD")
                clean.contains("flat") -> terrains.add("FLAT")
                
                // === TIME MODES ===
                clean.contains("time_fast") -> times.add("fast")
                clean.contains("time_slow") -> times.add("slow")
                clean.contains("time_fixed") || clean.contains("time_day") || clean.contains("time_night") -> times.add("fixed")
                
                // === WEATHER MODES ===
                clean.contains("weather_rain") -> weathers.add("endless_rain")
                clean.contains("weather_storm") || clean.contains("thunder") -> weathers.add("endless_storm")
                clean.contains("weather_clear") || clean.contains("no_weather") -> weathers.add("no_weather")
                
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
        
        // Terrain Conflict: Floating Islands AND Caves? Pick 1, add instability
        if (terrains.isNotEmpty()) {
            if (terrains.distinct().size > 1) {
                data.conflictInstability += (terrains.size - 1) * 30 
            }
            data.terrainType = terrains.random() 
        }
        
        // Weather Conflict
        if (weathers.isNotEmpty()) {
            if (weathers.distinct().size > 1) {
                data.conflictInstability += (weathers.size - 1) * 15
            }
            data.weatherMode = weathers.random()
        }
        
        // Time Conflict: Fast + Slow fighting? Chaos.
        val fastCount = times.count { it == "fast" }
        val slowCount = times.count { it == "slow" }
        
        if (fastCount > 0 && slowCount > 0) {
            data.conflictInstability += 50 // Massive shear!
            data.timeMode = times.random()
        } else if (fastCount > 0) {
            data.timeMode = "fast"
            data.timeScaleMultiplier = fastCount.toFloat() // Stack the speed!
            if (fastCount > 1) data.conflictInstability += fastCount * 15
        } else if (slowCount > 0) {
            data.timeMode = "slow"
            data.timeScaleMultiplier = 1.0f / slowCount.toFloat() // Stack the slowness!
            if (slowCount > 1) data.conflictInstability += slowCount * 15
        } else if (times.isNotEmpty()) {
            data.timeMode = times.random()
        }
        
        return data
    }
}