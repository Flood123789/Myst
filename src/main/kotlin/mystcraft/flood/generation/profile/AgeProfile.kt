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
    val stability: StabilityProfile,
    val modifiers: MutableList<String> = mutableListOf() 
) {
    companion object {
        val GSON: Gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()
        fun fromJson(json: String): AgeProfile = GSON.fromJson(json, AgeProfile::class.java)
    }
    fun toJson(): String = GSON.toJson(this)
}

enum class TerrainType { STANDARD, CAVES, FLOATING_ISLANDS, FLAT, BIOSPHERES, CITIES }

enum class BiomeMode { SINGLE, VANILLA_DISTRIBUTION, CHECKERBOARD, WEIGHTED }

data class BiomeWeight(var biomeId: String, var weight: Int)

data class BiomeSet(
    var mode: BiomeMode,
    var biomes: MutableList<BiomeWeight>
)

data class ColorSettings(val sky: Int, val fog: Int, val water: Int, val grass: Int, val foliage: Int)

data class TimeSettings(
    // === NEW: Expanded Celestial Data ===
    var sunNormalCount: Int = 1,
    var sunRedCount: Int = 0,
    var sunBlueCount: Int = 0,
    var sunSize: Float = 1.0f,
    
    var moonCount: Int = 1,
    var moonSize: Float = 1.0f,
    
    var starDensity: Int = 1, // 0 = none, 1 = normal, 2+ = dense/layered
    
    var fixedTime: Long? = null,
    var timeScale: Float = 1.0f,
    var savedTime: Long? = null,
    @Transient var liveTimeOfDay: Long = 6000L,
    @Transient var timeAccumulator: Float = 0f
)

data class WeatherSettings(
    var isEndlessRain: Boolean,
    var isEndlessStorm: Boolean,
    var noWeather: Boolean
)

data class StabilityProfile(var isStable: Boolean, var instabilityScore: Int)

data class SpawnSettings(
    // === NEW: Modifiable multipliers ===
    var noMobs: Boolean = false, 
    var hostileMultiplier: Float = 1.0f, 
    var passiveMultiplier: Float = 1.0f
)