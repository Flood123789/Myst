package mystcraft.flood.generation

import mystcraft.flood.generation.profile.AgeProfile
import mystcraft.flood.generation.profile.TerrainType
import net.minecraft.entity.effect.StatusEffectCategory
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier

object AgeCurseManager {
    const val HARMFUL_AGE_EFFECT = "harmful_age_effect"
    const val ENDLESS_STORM = "endless_storm"
    const val HOSTILE_SURGE = "hostile_surge"
    const val LIFELESS_SILENCE = "lifeless_silence"
    const val TIME_LOCK = "time_lock"
    const val LOW_GRAVITY = "low_gravity"
    const val ENDLESS_RAIN = "endless_rain"
    const val STARLESS_SKY = "starless_sky"

    val definitions: List<AgeCurseDefinition> = listOf(
        AgeCurseDefinition(
            id = HARMFUL_AGE_EFFECT,
            severity = 5,
            instabilityRelief = 24,
            isActive = { profile -> profile.ageEffect.enabled && harmfulAgeEffectName(profile) != null },
            label = { profile -> "Harmful Age Effect: ${harmfulAgeEffectName(profile) ?: "Unknown"}" },
            description = { profile ->
                "A harmful effect is written into the air: ${harmfulAgeEffectName(profile) ?: "unknown"}."
            },
            cleanse = { profile ->
                profile.ageEffect.effectId = null
                profile.ageEffect.enabled = false
            }
        ),
        AgeCurseDefinition(
            id = ENDLESS_STORM,
            severity = 4,
            instabilityRelief = 18,
            isActive = { profile -> profile.weather.isEndlessStorm },
            label = { "Endless Storm" },
            description = { "Thunder has been made a law instead of a season." },
            cleanse = { profile -> restoreOrdinaryWeather(profile) }
        ),
        AgeCurseDefinition(
            id = HOSTILE_SURGE,
            severity = 4,
            instabilityRelief = 16,
            isActive = { profile -> !profile.spawning.noMobs && profile.spawning.hostileMultiplier > 1.25f },
            label = { profile -> "Hostile Surge (${formatMultiplier(profile.spawning.hostileMultiplier)})" },
            description = { profile ->
                "Hostile life is overcalled at ${formatMultiplier(profile.spawning.hostileMultiplier)} its common measure."
            },
            cleanse = { profile -> profile.spawning.hostileMultiplier = 1.0f }
        ),
        AgeCurseDefinition(
            id = LIFELESS_SILENCE,
            severity = 3,
            instabilityRelief = 14,
            isActive = { profile -> profile.spawning.noMobs },
            label = { "Lifeless Silence" },
            description = { "The script forbids natural creatures from taking root." },
            cleanse = { profile ->
                profile.spawning.noMobs = false
                profile.terrainTuning.noMobs = false
            }
        ),
        AgeCurseDefinition(
            id = TIME_LOCK,
            severity = 3,
            instabilityRelief = 12,
            isActive = { profile -> profile.time.fixedTime != null && !profile.ageState.isSacrificed },
            label = { profile -> "Time Lock (${profile.time.fixedTime ?: 0})" },
            description = { profile -> "The heavens are nailed to tick ${profile.time.fixedTime ?: 0}." },
            cleanse = { profile ->
                profile.time.fixedTime = null
                profile.time.timeScale = 1.0f
            }
        ),
        AgeCurseDefinition(
            id = LOW_GRAVITY,
            severity = 2,
            instabilityRelief = 10,
            isActive = { profile -> profile.physics.gravityScale < 0.85f },
            label = { profile -> "Lightened Gravity (${formatMultiplier(profile.physics.gravityScale)})" },
            description = { profile ->
                "The world pulls at only ${formatMultiplier(profile.physics.gravityScale)} of its common weight."
            },
            cleanse = { profile -> profile.physics.gravityScale = 1.0f }
        ),
        AgeCurseDefinition(
            id = ENDLESS_RAIN,
            severity = 2,
            instabilityRelief = 8,
            isActive = { profile -> profile.weather.isEndlessRain && !profile.weather.isEndlessStorm },
            label = { "Endless Rain" },
            description = { "Rain falls without consent from the turning sky." },
            cleanse = { profile -> restoreOrdinaryWeather(profile) }
        ),
        AgeCurseDefinition(
            id = STARLESS_SKY,
            severity = 1,
            instabilityRelief = 6,
            isActive = { profile -> profile.time.starDensity <= 0 && profile.terrainType != TerrainType.VOID },
            label = { "Starless Sky" },
            description = { "The night has been stripped of its fixed lights." },
            cleanse = { profile -> profile.time.starDensity = 1 }
        )
    )

    fun refresh(profile: AgeProfile): List<AgeCurseDefinition> {
        val active = activeCurses(profile)
        replaceWith(profile.curses.activeCurses, active.map { it.id })
        profile.curses.diagnosedCurses.removeAll { it !in profile.curses.activeCurses && it !in profile.curses.cleansedCurses }
        return active
    }

    fun activeCurses(profile: AgeProfile): List<AgeCurseDefinition> =
        definitions
            .filter { definition -> !profile.ageState.isSacrificed && definition.isActive(profile) }
            .sortedWith(compareByDescending<AgeCurseDefinition> { it.severity }.thenBy { it.id })

    fun nextCurse(profile: AgeProfile): AgeCurseDefinition? = refresh(profile).firstOrNull()

    fun cleanseNext(profile: AgeProfile): CleanseResult {
        val curse = nextCurse(profile) ?: return CleanseResult(null, 0, false)
        val before = profile.stability.instabilityScore
        curse.cleanse(profile)
        if (!profile.curses.cleansedCurses.contains(curse.id)) {
            profile.curses.cleansedCurses.add(curse.id)
        }
        if (!profile.curses.diagnosedCurses.contains(curse.id)) {
            profile.curses.diagnosedCurses.add(curse.id)
        }

        profile.stability.instabilityScore = (profile.stability.instabilityScore - curse.instabilityRelief).coerceAtLeast(0)
        profile.stability.isStable = profile.stability.instabilityScore <= 0
        refresh(profile)
        return CleanseResult(curse, before - profile.stability.instabilityScore, true)
    }

    fun labelFor(id: String, profile: AgeProfile): String = when (id) {
        HARMFUL_AGE_EFFECT -> harmfulAgeEffectName(profile)?.let { "Harmful Age Effect: $it" } ?: "Harmful Age Effect"
        ENDLESS_STORM -> "Endless Storm"
        HOSTILE_SURGE -> "Hostile Surge"
        LIFELESS_SILENCE -> "Lifeless Silence"
        TIME_LOCK -> "Time Lock"
        LOW_GRAVITY -> "Lightened Gravity"
        ENDLESS_RAIN -> "Endless Rain"
        STARLESS_SKY -> "Starless Sky"
        else -> id.replace('_', ' ')
    }

    fun descriptions(profile: AgeProfile): List<String> =
        refresh(profile).map { it.description(profile) }

    private fun replaceWith(target: MutableList<String>, values: List<String>) {
        target.clear()
        target.addAll(values)
    }

    private fun harmfulAgeEffectName(profile: AgeProfile): String? {
        val id = Identifier.tryParse(profile.ageEffect.effectId ?: return null) ?: return null
        val effect = Registries.STATUS_EFFECT.get(id) ?: return null
        if (effect.category != StatusEffectCategory.HARMFUL) return null
        return effect.name.string.takeIf { it.isNotBlank() } ?: id.path.replace('_', ' ')
    }

    private fun restoreOrdinaryWeather(profile: AgeProfile) {
        profile.weather.isEndlessRain = false
        profile.weather.isEndlessStorm = false
        profile.weather.noWeather = false
        profile.weather.currentRaining = false
        profile.weather.currentThundering = false
        profile.weather.clearTicks = 12000
        profile.weather.rainTicks = 0
        profile.weather.thunderTicks = 0
    }

    private fun formatMultiplier(value: Float): String = "${"%.2f".format(value)}x"
}

data class AgeCurseDefinition(
    val id: String,
    val severity: Int,
    val instabilityRelief: Int,
    val isActive: (AgeProfile) -> Boolean,
    val label: (AgeProfile) -> String,
    val description: (AgeProfile) -> String,
    val cleanse: (AgeProfile) -> Unit
)

data class CleanseResult(
    val curse: AgeCurseDefinition?,
    val instabilityReduced: Int,
    val changed: Boolean
)
