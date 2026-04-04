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
    // THE FIX 1: A list to hold any special mechanic pages (like "dense_ores", "meteors", "charged_creepers")
    val modifiers: MutableList<String> = mutableListOf() 
) {
    companion object {
        val GSON: Gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()
        fun fromJson(json: String): AgeProfile = GSON.fromJson(json, AgeProfile::class.java)
    }
    fun toJson(): String = GSON.toJson(this)
}

enum class TerrainType { STANDARD, CAVES, FLOATING_ISLANDS, FLAT }

enum class BiomeMode { SINGLE, VANILLA_DISTRIBUTION, CHECKERBOARD, WEIGHTED }

data class BiomeWeight(var biomeId: String, var weight: Int)

data class BiomeSet(
    var mode: BiomeMode,
    var biomes: MutableList<BiomeWeight>
)

data class ColorSettings(val sky: Int, val fog: Int, val water: Int, val grass: Int, val foliage: Int)

data class TimeSettings(
    var sunCount: Int,
    var sunSize: Float,
    var moonSize: Float,
    var hasStars: Boolean,
    var fixedTime: Long?,
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

// THE FIX 2: Changed from 'val' to 'var' so the Book Binder can calculate and add penalties!
data class StabilityProfile(var isStable: Boolean, var instabilityScore: Int)

data class SpawnSettings(val noMobs: Boolean, val hostileMultiplier: Float, val passiveMultiplier: Float)