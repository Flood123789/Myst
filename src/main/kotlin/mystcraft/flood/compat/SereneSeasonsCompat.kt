package mystcraft.flood.compat

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.profile.AgeProfile
import mystcraft.flood.generation.profile.AgeProfileManager
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.world.GameRules
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections

/**
 * Optional Serene Seasons bridge.
 *
 * This class deliberately uses reflection so Serene Seasons and GlitchCore are
 * never required to load Mystcraft Reforged. Serene Seasons keeps one calendar
 * per dimension, but only whitelists the Overworld and advances its client
 * calendar at 1x by default. This bridge opts loaded Ages in and owns their
 * calendar rate so it matches the authored Mystcraft clock.
 */
object SereneSeasonsCompat {
    private const val SERENE_SEASONS_MOD_ID = "sereneseasons"
    private val reportedFailures = Collections.synchronizedSet(mutableSetOf<String>())

    private val installed: Boolean by lazy {
        FabricLoader.getInstance().isModLoaded(SERENE_SEASONS_MOD_ID)
    }

    private val access: Access? by lazy {
        if (!installed) null else runCatching(::createAccess)
            .onFailure { reportFailure("initialize", it) }
            .getOrNull()
    }

    fun isInstalled(): Boolean = installed

    fun isAvailable(): Boolean = access != null

    fun tick(world: ServerWorld, profile: AgeProfile) {
        if (world.registryKey.value.namespace != MystcraftReforged.MOD_ID) return
        val bridge = access ?: return

        try {
            bridge.setDimensionEnabled(world.registryKey.value, profile.seasons.enabled)
            bridge.pinDayTime(world)
            if (!profile.seasons.enabled) return

            val savedData = bridge.getSavedData(world)
            var cycleTicks = bridge.readCycleTicks(savedData)
            var forceSync = false

            if (!profile.seasons.initialized) {
                cycleTicks = world.random.nextInt(12) * bridge.subSeasonDuration
                profile.seasons.initialized = true
                profile.seasons.tickAccumulator = 0f
                bridge.writeCycleTicks(savedData, cycleTicks)
                AgeProfileManager.save(world.server, world.registryKey.value)
                forceSync = true
            }

            if (bridge.shouldAdvance(world)) {
                val advanced = advanceSeasonClock(
                    cycleTicks = cycleTicks,
                    accumulator = profile.seasons.tickAccumulator,
                    timeScale = profile.time.timeScale,
                    frozen = profile.time.visibleTimeFrozen,
                    cycleDuration = bridge.cycleDuration
                )
                profile.seasons.tickAccumulator = advanced.accumulator
                if (advanced.cycleTicks != cycleTicks) {
                    cycleTicks = advanced.cycleTicks
                    bridge.writeCycleTicks(savedData, cycleTicks)
                }
            }

            if (forceSync || world.server.ticks % 20 == 0) {
                bridge.sendUpdate(world)
            }
        } catch (error: Throwable) {
            reportFailure("tick", error)
        }
    }

    fun pinDayTime(world: ServerWorld) {
        val bridge = access ?: return
        runCatching { bridge.pinDayTime(world) }
            .onFailure { reportFailure("pin Age time", it) }
    }

    fun setDimensionEnabled(dimensionId: Identifier, enabled: Boolean): Boolean {
        val bridge = access ?: return false
        return runCatching { bridge.setDimensionEnabled(dimensionId, enabled) }
            .onFailure { reportFailure("update dimension whitelist", it) }
            .getOrDefault(false)
    }

    fun setSeason(world: ServerWorld, profile: AgeProfile, seasonName: String): String? {
        val ordinal = seasonOrdinal(seasonName) ?: return null
        val bridge = access ?: return null

        return runCatching {
            bridge.setDimensionEnabled(world.registryKey.value, profile.seasons.enabled)
            val savedData = bridge.getSavedData(world)
            bridge.writeCycleTicks(savedData, ordinal * bridge.subSeasonDuration)
            profile.seasons.initialized = true
            profile.seasons.tickAccumulator = 0f
            AgeProfileManager.save(world.server, world.registryKey.value)
            bridge.pinDayTime(world)
            bridge.sendUpdate(world)
            SUB_SEASON_NAMES[ordinal]
        }.onFailure { reportFailure("set season", it) }
            .getOrNull()
    }

    fun currentSeason(world: ServerWorld): String? {
        val bridge = access ?: return null
        return runCatching {
            val ticks = bridge.readCycleTicks(bridge.getSavedData(world))
            val ordinal = Math.floorMod(ticks / bridge.subSeasonDuration, SUB_SEASON_NAMES.size)
            SUB_SEASON_NAMES[ordinal]
        }.onFailure { reportFailure("read season", it) }
            .getOrNull()
    }

    fun cycleDuration(): Int? = access?.cycleDuration

    private fun createAccess(): Access {
        val handlerClass = Class.forName("sereneseasons.season.SeasonHandler")
        val savedDataClass = Class.forName("sereneseasons.season.SeasonSavedData")
        val seasonTimeClass = Class.forName("sereneseasons.season.SeasonTime")
        val modConfigClass = Class.forName("sereneseasons.init.ModConfig")

        val getSavedData = handlerClass.methods.single { it.name == "getSeasonSavedData" && it.parameterCount == 1 }
        val sendUpdate = handlerClass.methods.single { it.name == "sendSeasonUpdate" && it.parameterCount == 1 }
        val lastDayTimes = handlerClass.getField("lastDayTimes").get(null) as MutableMap<Any, Long>
        val cycleTicks = savedDataClass.getField("seasonCycleTicks")

        val zero = seasonTimeClass.getField("ZERO").get(null)
        val subSeasonDuration = seasonTimeClass.getMethod("getSubSeasonDuration").invoke(zero) as Int
        val cycleDuration = seasonTimeClass.getMethod("getCycleDuration").invoke(zero) as Int

        val seasonsConfig = modConfigClass.getField("seasons").get(null)
            ?: error("Serene Seasons config was not initialized")
        val whitelist = seasonsConfig.javaClass.getField("whitelistedDimensions")
        val progressOffline = seasonsConfig.javaClass.getField("progressSeasonWhileOffline")
        @Suppress("UNCHECKED_CAST")
        val seasonCycleRule = runCatching {
            Class.forName("sereneseasons.api.SSGameRules").getField("RULE_DOSEASONCYCLE").get(null)
                as? GameRules.Key<GameRules.BooleanRule>
        }.getOrNull()

        require(subSeasonDuration > 0 && cycleDuration > 0) {
            "Serene Seasons reported an invalid calendar duration"
        }

        return Access(
            getSavedData = getSavedData,
            sendUpdate = sendUpdate,
            lastDayTimes = lastDayTimes,
            cycleTicks = cycleTicks,
            seasonsConfig = seasonsConfig,
            whitelist = whitelist,
            progressOffline = progressOffline,
            seasonCycleRule = seasonCycleRule,
            subSeasonDuration = subSeasonDuration,
            cycleDuration = cycleDuration
        )
    }

    private fun reportFailure(operation: String, error: Throwable) {
        if (reportedFailures.add(operation)) {
            val cause = error.cause ?: error
            MystcraftReforged.LOGGER.warn(
                "Serene Seasons compatibility could not {}: {}",
                operation,
                cause.message ?: cause.javaClass.simpleName
            )
        }
    }

    private class Access(
        private val getSavedData: Method,
        private val sendUpdate: Method,
        private val lastDayTimes: MutableMap<Any, Long>,
        private val cycleTicks: Field,
        private val seasonsConfig: Any,
        private val whitelist: Field,
        private val progressOffline: Field,
        private val seasonCycleRule: GameRules.Key<GameRules.BooleanRule>?,
        val subSeasonDuration: Int,
        val cycleDuration: Int
    ) {
        fun getSavedData(world: ServerWorld): Any =
            getSavedData.invoke(null, world) ?: error("Serene Seasons returned no saved calendar")

        fun readCycleTicks(savedData: Any): Int = cycleTicks.getInt(savedData)

        fun writeCycleTicks(savedData: Any, ticks: Int) {
            cycleTicks.setInt(savedData, Math.floorMod(ticks, cycleDuration))
            findMethodInHierarchy(savedData.javaClass, setOf("markDirty", "setDirty", "method_80"), 0)
                ?.invoke(savedData)
                ?: error("Could not mark the Serene Seasons calendar dirty")
        }

        fun sendUpdate(world: ServerWorld) {
            sendUpdate.invoke(null, world)
        }

        fun pinDayTime(world: ServerWorld) {
            lastDayTimes[world] = world.timeOfDay
        }

        fun setDimensionEnabled(dimensionId: Identifier, enabled: Boolean): Boolean {
            val id = dimensionId.toString()
            synchronized(seasonsConfig) {
                val configured = (whitelist.get(seasonsConfig) as? List<*>)
                    ?.map(Any?::toString)
                    ?.toMutableList()
                    ?: mutableListOf()
                val changed = if (enabled) {
                    if (id in configured) false else {
                        configured.add(id)
                        true
                    }
                } else {
                    configured.removeAll { it == id }
                }
                if (changed) whitelist.set(seasonsConfig, configured)
                return changed
            }
        }

        fun shouldAdvance(world: ServerWorld): Boolean {
            if (!progressOffline.getBoolean(seasonsConfig) && world.server.playerManager.playerList.isEmpty()) {
                return false
            }
            val rule = seasonCycleRule ?: return true
            return world.gameRules.getBoolean(rule)
        }
    }

    private fun findMethodInHierarchy(
        initialClass: Class<*>,
        names: Set<String>,
        parameterCount: Int
    ): Method? {
        var current: Class<*>? = initialClass
        while (current != null) {
            current.declaredMethods.firstOrNull { it.name in names && it.parameterCount == parameterCount }?.let {
                it.isAccessible = true
                return it
            }
            current = current.superclass
        }
        return null
    }

    private val SUB_SEASON_NAMES = listOf(
        "early_spring",
        "mid_spring",
        "late_spring",
        "early_summer",
        "mid_summer",
        "late_summer",
        "early_autumn",
        "mid_autumn",
        "late_autumn",
        "early_winter",
        "mid_winter",
        "late_winter"
    )
}
