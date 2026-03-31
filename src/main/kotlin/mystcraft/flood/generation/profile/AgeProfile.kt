package mystcraft.flood.generation.profile

import com.google.gson.Gson
import com.google.gson.GsonBuilder

data class AgeProfile(
    val id: String,
    val seed: Long,
    val terrainType: TerrainType,
    val colors: ColorSettings,
    val time: TimeSettings,
    val weather: WeatherSettings,
    val biomes: BiomeSet,
    val spawning: SpawnSettings,
    val stability: StabilityProfile
) {
    companion object {
        // NEW: Added .serializeNulls() so it always prints "fixedTime": null
        val GSON: Gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()
        
        fun fromJson(json: String): AgeProfile = GSON.fromJson(json, AgeProfile::class.java)
    }
    fun toJson(): String = GSON.toJson(this)
}

enum class TerrainType { 
    STANDARD, CAVES, FLOATING_ISLANDS, FLAT 
}

data class ColorSettings(
    val sky: Int, val fog: Int, val water: Int, val grass: Int, val foliage: Int
)

data class TimeSettings(
    val sunCount: Int,
    val sunSize: Float,
    val moonSize: Float,
    val hasStars: Boolean,
    val fixedTime: Long?,
    val timeScale: Float = 1.0f // NEW: 1.0 is normal, 0.5 is half speed, 2.0 is double
)

data class WeatherSettings(
    val isEndlessRain: Boolean, val isEndlessStorm: Boolean, val noWeather: Boolean
)

data class BiomeSet(
    val mode: BiomeMode, val specificBiomes: List<String>
)

enum class BiomeMode { 
    SINGLE, VANILLA_DISTRIBUTION, CHECKERBOARD 
}

data class StabilityProfile(
    val isStable: Boolean, val instabilityScore: Int
)

data class SpawnSettings(
    val noMobs: Boolean, val hostileMultiplier: Float, val passiveMultiplier: Float
)