package mystcraft.flood.generation.profile

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser

data class AgeProfile(
    val id: String,
    val seed: Long,
    var terrainType: TerrainType,
    val colors: ColorSettings,
    val time: TimeSettings,
    val weather: WeatherSettings,
    val biomes: BiomeSet,
    val spawning: SpawnSettings,
    val ageEffect: AgeEffectProfile = AgeEffectProfile(),
    val physics: PhysicsSettings = PhysicsSettings(),
    val ageState: AgeState = AgeState(),
    val stability: StabilityProfile,
    val modifiers: MutableList<String> = mutableListOf() 
) {
    companion object {
        val GSON: Gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()
        fun fromJson(json: String): AgeProfile {
            val root = JsonParser.parseString(json).asJsonObject
            val profile = GSON.fromJson(root, AgeProfile::class.java)

            if (!root.has("ageEffect")) {
                profile.ageEffect.effectId = null
                profile.ageEffect.enabled = true
            } else if (!root.getAsJsonObject("ageEffect").has("enabled")) {
                profile.ageEffect.enabled = true
            }

            if (root.has("stability") && !root.getAsJsonObject("stability").has("effectsEnabled")) {
                profile.stability.effectsEnabled = true
            }

            if (!root.has("physics")) {
                profile.physics.gravityScale = 1.0f
            }

            if (!root.has("ageState")) {
                profile.ageState.isSacrificed = false
                profile.ageState.sacrificedAt = null
                profile.ageState.sacrificedBy = null
            }

            return profile
        }
    }
    fun toJson(): String = GSON.toJson(this)
}

enum class TerrainType { STANDARD, CAVES, FLOATING_ISLANDS, FLAT, BIOSPHERES, CITIES, VOID }

enum class BiomeMode { SINGLE, VANILLA_DISTRIBUTION, CHECKERBOARD, WEIGHTED }

data class BiomeWeight(var biomeId: String, var weight: Int)

data class BiomeSet(
    var mode: BiomeMode,
    var biomes: MutableList<BiomeWeight>
)

data class ColorSettings(var sky: Int, var fog: Int, var water: Int, var grass: Int, var foliage: Int)

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

data class AgeEffectProfile(
    var effectId: String? = null,
    var enabled: Boolean = true
)

data class PhysicsSettings(
    var gravityScale: Float = 1.0f
)

data class AgeState(
    var isSacrificed: Boolean = false,
    var sacrificedAt: Long? = null,
    var sacrificedBy: String? = null
)

data class StabilityProfile(
    var isStable: Boolean,
    var instabilityScore: Int,
    var effectsEnabled: Boolean = true
)

data class SpawnSettings(
    // === NEW: Modifiable multipliers ===
    var noMobs: Boolean = false, 
    var hostileMultiplier: Float = 1.0f, 
    var passiveMultiplier: Float = 1.0f
)
