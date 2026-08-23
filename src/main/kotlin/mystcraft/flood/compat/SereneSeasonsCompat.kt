package mystcraft.flood.compat

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.server.MystcraftSeasonData
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.world.PersistentState
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Optional Serene Seasons bridge. All references to Serene Seasons are reflective so the mod is
 * never needed to load or play Mystcraft Reforged.
 *
 * Serene Seasons advances from `World.getTimeOfDay()`. Mystcraft's world mixin already returns the
 * Age's scaled clock there, so once an Age is whitelisted Serene Seasons automatically follows
 * fast, slow, and stopped Age time without consulting the Overworld.
 */
object SereneSeasonsCompat {
    private const val SERENE_SEASONS_MOD_ID = "sereneseasons"
    private val allowedDimensions = ConcurrentHashMap.newKeySet<String>()
    private var serverFailureLogged = false
    private var clientFailureLogged = false

    val isAvailable: Boolean
        get() = FabricLoader.getInstance().isModLoaded(SERENE_SEASONS_MOD_ID)

    fun tick(world: ServerWorld) {
        if (!isAvailable) return

        try {
            allowDimension(world.registryKey.value)
            val state = MystcraftSeasonData.get(world)
            if (!state.initialized) {
                val index = MystcraftSeasons.randomIndex(world.seed, world.registryKey.value.toString())
                setSeasonIndexInternal(world, index)
                state.initialized = true
                state.frozenSeasonTicks = seasonTicks(world)
                state.markDirty()
                MystcraftReforged.LOGGER.info(
                    "Serene Seasons initialized {} in {}",
                    MystcraftSeasons.nameOf(index),
                    world.registryKey.value
                )
            } else if (!state.cycleEnabled) {
                val data = savedData(world)
                if (seasonTicksField().getInt(data) != state.frozenSeasonTicks) {
                    seasonTicksField().setInt(data, state.frozenSeasonTicks)
                    (data as PersistentState).markDirty()
                    sendSeasonUpdateMethod().invoke(null, world)
                }
            }
        } catch (error: Exception) {
            logServerFailure(error)
        } catch (error: LinkageError) {
            logServerFailure(error)
        }
    }

    /** Enables Serene Seasons' client-side colors and hooks for the currently loaded Age. */
    fun tickClient(dimensionId: Identifier?) {
        if (!isAvailable || dimensionId?.namespace != MystcraftReforged.MOD_ID) return
        try {
            allowDimension(dimensionId)
        } catch (error: Exception) {
            if (!clientFailureLogged) {
                clientFailureLogged = true
                MystcraftReforged.LOGGER.warn(
                    "Serene Seasons client compatibility could not whitelist Mystcraft Ages.",
                    error
                )
            }
        } catch (error: LinkageError) {
            if (!clientFailureLogged) {
                clientFailureLogged = true
                MystcraftReforged.LOGGER.warn(
                    "Serene Seasons client compatibility could not whitelist Mystcraft Ages.",
                    error
                )
            }
        }
    }

    /** Config objects are rebuilt between integrated/dedicated server sessions. */
    fun clear() {
        allowedDimensions.clear()
        serverFailureLogged = false
        clientFailureLogged = false
    }

    fun setSeason(world: ServerWorld, name: String): Boolean {
        if (!isAvailable) return false
        val index = MystcraftSeasons.indexOf(name) ?: return false
        return runServerOperation {
            allowDimension(world.registryKey.value)
            setSeasonIndexInternal(world, index)
            MystcraftSeasonData.get(world).also {
                it.initialized = true
                it.frozenSeasonTicks = seasonTicks(world)
                it.markDirty()
            }
        }
    }

    fun getSeason(world: ServerWorld): String? {
        if (!isAvailable) return null
        return try {
            allowDimension(world.registryKey.value)
            val ticks = seasonTicks(world)
            MystcraftSeasons.nameOf(ticks / subSeasonDuration())
        } catch (error: Exception) {
            logServerFailure(error)
            null
        } catch (error: LinkageError) {
            logServerFailure(error)
            null
        }
    }

    fun setSeasonCycleEnabled(world: ServerWorld, enabled: Boolean): Boolean {
        if (!isAvailable) return false
        return runServerOperation {
            allowDimension(world.registryKey.value)
            MystcraftSeasonData.get(world).also {
                it.initialized = true
                it.cycleEnabled = enabled
                it.frozenSeasonTicks = seasonTicks(world)
                it.markDirty()
            }
        }
    }

    fun isSeasonCycleEnabled(world: ServerWorld): Boolean? {
        if (!isAvailable) return null
        return try {
            MystcraftSeasonData.get(world).cycleEnabled
        } catch (error: Exception) {
            logServerFailure(error)
            null
        } catch (error: LinkageError) {
            logServerFailure(error)
            null
        }
    }

    private fun setSeasonIndexInternal(world: ServerWorld, index: Int) {
        val data = savedData(world)
        seasonTicksField().setInt(data, Math.floorMod(index, MystcraftSeasons.subSeasonNames.size) * subSeasonDuration())
        (data as PersistentState).markDirty()
        sendSeasonUpdateMethod().invoke(null, world)
    }

    private fun allowDimension(dimensionId: Identifier) {
        val id = dimensionId.toString()
        if (id in allowedDimensions) return

        val modConfig = Class.forName("sereneseasons.init.ModConfig")
        val seasons = modConfig.getField("seasons").get(null)
        val whitelistField = seasons.javaClass.getField("whitelistedDimensions")
        val current = whitelistField.get(seasons)
        @Suppress("UNCHECKED_CAST")
        val dimensions = current as Collection<Any?>
        if (dimensions.any { it == id }) {
            allowedDimensions.add(id)
            return
        }

        try {
            @Suppress("UNCHECKED_CAST")
            (current as MutableCollection<Any?>).add(id)
        } catch (_: UnsupportedOperationException) {
            whitelistField.set(seasons, dimensions.toMutableList().also { it.add(id) })
        }
        allowedDimensions.add(id)
    }

    private fun savedData(world: ServerWorld): Any = seasonHandlerClass().methods.first {
        it.name == "getSeasonSavedData" && it.parameterCount == 1
    }.invoke(null, world)

    private fun seasonTicks(world: ServerWorld): Int = seasonTicksField().getInt(savedData(world))

    private fun seasonTicksField(): Field = Class.forName("sereneseasons.season.SeasonSavedData")
        .getField("seasonCycleTicks")

    private fun subSeasonDuration(): Int {
        val seasonTime = Class.forName("sereneseasons.season.SeasonTime")
        val zero = seasonTime.getField("ZERO").get(null)
        return seasonTime.getMethod("getSubSeasonDuration").invoke(zero) as Int
    }

    private fun seasonHandlerClass(): Class<*> = Class.forName("sereneseasons.season.SeasonHandler")

    private fun sendSeasonUpdateMethod(): Method = seasonHandlerClass().methods.first {
        it.name == "sendSeasonUpdate" && it.parameterCount == 1
    }

    private inline fun runServerOperation(operation: () -> Unit): Boolean = try {
        operation()
        true
    } catch (error: Exception) {
        logServerFailure(error)
        false
    } catch (error: LinkageError) {
        logServerFailure(error)
        false
    }

    private fun logServerFailure(error: Throwable) {
        if (serverFailureLogged) return
        serverFailureLogged = true
        MystcraftReforged.LOGGER.warn(
            "Serene Seasons compatibility could not access the installed mod's season API. Continuing without compatibility.",
            error
        )
    }
}
