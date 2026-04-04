package mystcraft.flood.generation

object AgeCompiler {
    
    data class CompiledAge(
        var terrainType: String? = null,
        var skyColor: Int? = null,
        var waterColor: Int? = null,
        var fogColor: Int? = null,
        var grassColor: Int? = null,     
        var foliageColor: Int? = null,   
        var timeMode: String? = null,
        var weatherMode: String? = null,
        val biomes: MutableList<String> = mutableListOf(),
        var instabilityScore: Int = 0 // <--- THE CHAOS TRACKER
    )

    fun compile(symbols: List<String>): CompiledAge {
        val age = CompiledAge()
        
        var pendingTarget: String? = null
        var pendingColor: Int? = null

        for (symbol in symbols) {
            val id = symbol.lowercase()

            val target = when {
                id.contains("sky") -> "sky"
                id.contains("water") -> "water"
                id.contains("fog") -> "fog"
                id.contains("grass") -> "grass"
                id.contains("foliage") -> "foliage"
                else -> null
            }

            val hex = parseColor(id)

            if (target != null) pendingTarget = target
            if (hex != null) pendingColor = hex

            if (pendingTarget != null && pendingColor != null) {
                when (pendingTarget) {
                    "sky" -> age.skyColor = pendingColor
                    "water" -> age.waterColor = pendingColor
                    "fog" -> age.fogColor = pendingColor
                    "grass" -> age.grassColor = pendingColor
                    "foliage" -> age.foliageColor = pendingColor
                }
                pendingTarget = null
                pendingColor = null
            }

            // === TERRAIN (Add Instability for exotic terrain!) ===
            if (id.contains("floating_island")) {
                age.terrainType = "FLOATING_ISLANDS"
                age.instabilityScore += 15 // Floating islands are inherently unstable
            }
            else if (id.contains("cave")) {
                age.terrainType = "CAVE"
                age.instabilityScore += 5
            }
            else if (id.contains("standard") || id.contains("normal")) age.terrainType = "STANDARD"
            else if (id.contains("flat")) age.terrainType = "FLAT"

            // === TIME ===
            else if (id.contains("time_fast")) { age.timeMode = "fast"; age.instabilityScore += 10 }
            else if (id.contains("time_slow")) { age.timeMode = "slow"; age.instabilityScore += 10 }
            else if (id.contains("time_fixed") || id.contains("time_day") || id.contains("time_night")) { 
                age.timeMode = "fixed"
                age.instabilityScore += 20 // Stopping time is dangerous!
            }

            // === WEATHER ===
            else if (id.contains("weather_rain")) age.weatherMode = "endless_rain"
            else if (id.contains("weather_storm") || id.contains("thunder")) {
                age.weatherMode = "endless_storm"
                age.instabilityScore += 15
            }
            else if (id.contains("weather_clear") || id.contains("no_weather")) age.weatherMode = "no_weather"

            // === BIOMES ===
            else if (id.startsWith("minecraft:") && !id.contains("color")) {
                age.biomes.add(id)
            } else {
                val possibleBiome = id.substringAfter(":").replace("_page", "").replace("page_", "")
                val commonBiomes = listOf("plains", "desert", "forest", "jungle", "savanna", "taiga", "swamp", "badlands", "ocean", "river", "mushroom_fields", "sunflower_plains", "snowy_plains")
                
                if (possibleBiome in commonBiomes) {
                    age.biomes.add("minecraft:$possibleBiome")
                } else if (id.contains("plains")) {
                    age.biomes.add("minecraft:plains")
                }
            }
        }

        // BIOME CHAOS: If you jam too many biomes together, the dimension fractures!
        if (age.biomes.size > 3) {
            age.instabilityScore += (age.biomes.size - 3) * 10 
        }

        return age
    }

    private fun parseColor(id: String): Int? {
        if (id.contains("sky") || id.contains("water") || id.contains("fog") || id.contains("grass") || id.contains("foliage")) return null
        
        return when {
            id.contains("red") -> 0xFF0000
            id.contains("blue") -> 0x0000FF
            id.contains("green") -> 0x00FF00
            id.contains("black") -> 0x000000
            id.contains("white") -> 0xFFFFFF
            id.contains("yellow") -> 0xFFFF00
            id.contains("purple") -> 0x800080
            else -> null
        }
    }
}