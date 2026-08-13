package mystcraft.flood.client.compat

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.client.cache.ClientAgeCache
import mystcraft.flood.client.cache.ClientAgeTimeCache
import mystcraft.flood.compat.SereneSeasonsCompat
import mystcraft.flood.compat.advanceSeasonClock
import net.minecraft.client.MinecraftClient
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.BlockPos
import java.lang.reflect.Method
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/** Keeps Serene Seasons' client-side interpolation on the active Age's rate. */
object SereneSeasonsClientCompat {
    private data class ClientClock(var cycleTicks: Int, var accumulator: Float = 0f)

    private val clocks = ConcurrentHashMap<Any, ClientClock>()
    private val reportedFailures = Collections.synchronizedSet(mutableSetOf<String>())
    private val access: Access? by lazy {
        if (!SereneSeasonsCompat.isInstalled()) null else runCatching(::createAccess)
            .onFailure { reportFailure("initialize", it) }
            .getOrNull()
    }

    fun tick(client: MinecraftClient) {
        val world = client.world ?: return
        val id = world.registryKey.value
        if (id.namespace != MystcraftReforged.MOD_ID) return
        val profile = ClientAgeCache.getProperties(id) ?: return
        val bridge = access ?: return

        try {
            val whitelistChanged = SereneSeasonsCompat.setDimensionEnabled(id, profile.seasons.enabled)
            if (whitelistChanged) client.worldRenderer.reload()
            if (!profile.seasons.enabled) {
                clocks.remove(world.registryKey)
                return
            }

            val observed = bridge.clientCycleTicks[world.registryKey] ?: return
            val cycleDuration = SereneSeasonsCompat.cycleDuration() ?: return
            val state = clocks[world.registryKey]
            if (state == null) {
                clocks[world.registryKey] = ClientClock(observed)
                return
            }

            // Serene Seasons normally adds one before this callback. A value
            // other than that expected tick is an authoritative server sync.
            val expectedNativeTick = Math.floorMod(state.cycleTicks + 1, cycleDuration)
            if (observed != expectedNativeTick) {
                state.cycleTicks = observed
                state.accumulator = 0f
            }

            val rate = ClientAgeTimeCache.getClockRate(id)
            val advanced = advanceSeasonClock(
                cycleTicks = state.cycleTicks,
                accumulator = state.accumulator,
                timeScale = rate?.timeScale ?: profile.time.timeScale,
                frozen = rate?.frozen ?: profile.time.visibleTimeFrozen,
                cycleDuration = cycleDuration
            )
            state.cycleTicks = advanced.cycleTicks
            state.accumulator = advanced.accumulator
            bridge.clientCycleTicks[world.registryKey] = state.cycleTicks
        } catch (error: Throwable) {
            reportFailure("tick", error)
        }
    }

    fun applyGrassColor(originalColor: Int, world: ClientWorld?, pos: BlockPos?): Int =
        applySeasonColor("applySeasonalGrassColouring", originalColor, world, pos)

    fun applyFoliageColor(originalColor: Int, world: ClientWorld?, pos: BlockPos?): Int =
        applySeasonColor("applySeasonalFoliageColouring", originalColor, world, pos)

    fun clear() {
        clocks.clear()
    }

    private fun applySeasonColor(
        methodName: String,
        originalColor: Int,
        world: ClientWorld?,
        pos: BlockPos?
    ): Int {
        if (world == null || pos == null) return originalColor
        val profile = ClientAgeCache.getProperties(world.registryKey.value) ?: return originalColor
        if (!profile.seasons.enabled) return originalColor
        val bridge = access ?: return originalColor

        return runCatching {
            val seasonState = bridge.getSeasonState.invoke(null, world)
            val biome = world.getBiome(pos)
            val tropical = bridge.usesTropicalSeasons.invoke(null, biome) as Boolean
            val colorProvider = if (tropical) {
                bridge.getTropicalSeason.invoke(seasonState)
            } else {
                bridge.getSubSeason.invoke(seasonState)
            }
            val colorMethod = if (methodName == "applySeasonalGrassColouring") {
                bridge.applyGrassColor
            } else {
                bridge.applyFoliageColor
            }
            colorMethod.invoke(null, colorProvider, biome, originalColor) as Int
        }.onFailure { reportFailure("apply seasonal color", it) }
            .getOrDefault(originalColor)
    }

    @Suppress("UNCHECKED_CAST")
    private fun createAccess(): Access {
        val clientHandler = Class.forName("sereneseasons.season.SeasonHandlerClient")
        val seasonHelper = Class.forName("sereneseasons.api.season.SeasonHelper")
        val seasonState = Class.forName("sereneseasons.api.season.ISeasonState")
        val seasonColorUtil = Class.forName("sereneseasons.util.SeasonColorUtil")
        val clientCycleTicks = clientHandler.getField("clientSeasonCycleTicks").get(null) as MutableMap<Any, Int>

        return Access(
            clientCycleTicks = clientCycleTicks,
            getSeasonState = seasonHelper.methods.single { it.name == "getSeasonState" && it.parameterCount == 1 },
            usesTropicalSeasons = seasonHelper.methods.single { it.name == "usesTropicalSeasons" && it.parameterCount == 1 },
            getSubSeason = seasonState.getMethod("getSubSeason"),
            getTropicalSeason = seasonState.getMethod("getTropicalSeason"),
            applyGrassColor = seasonColorUtil.methods.single {
                it.name == "applySeasonalGrassColouring" && it.parameterCount == 3
            },
            applyFoliageColor = seasonColorUtil.methods.single {
                it.name == "applySeasonalFoliageColouring" && it.parameterCount == 3
            }
        )
    }

    private fun reportFailure(operation: String, error: Throwable) {
        if (reportedFailures.add(operation)) {
            val cause = error.cause ?: error
            MystcraftReforged.LOGGER.warn(
                "Serene Seasons client compatibility could not {}: {}",
                operation,
                cause.message ?: cause.javaClass.simpleName
            )
        }
    }

    private data class Access(
        val clientCycleTicks: MutableMap<Any, Int>,
        val getSeasonState: Method,
        val usesTropicalSeasons: Method,
        val getSubSeason: Method,
        val getTropicalSeason: Method,
        val applyGrassColor: Method,
        val applyFoliageColor: Method
    )
}
