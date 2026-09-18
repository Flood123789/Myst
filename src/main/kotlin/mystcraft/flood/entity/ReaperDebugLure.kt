package mystcraft.flood.entity

import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * A world-space point that drone Reapers walk to, for testing movement by hand.
 *
 * The lure is deliberately short-lived and refreshed by the held item rather than toggled on and
 * off. That way putting the tool away, dying, or logging out all release the drones without
 * needing any cleanup path, and a stale lure can never strand a test subject walking at a wall
 * forever.
 */
object ReaperDebugLure {

    private data class Lure(val pos: BlockPos, var expiresAt: Long)

    private val byWorld = HashMap<String, Lure>()

    /** Ticks a lure survives without the holder refreshing it. */
    private const val LINGER_TICKS = 40L

    private fun key(world: World) = world.registryKey.value.toString()

    fun set(world: World, pos: BlockPos) {
        byWorld[key(world)] = Lure(pos, world.time + LINGER_TICKS)
    }

    /** Called each tick by the held tool to keep the current lure alive. */
    fun refresh(world: World) {
        byWorld[key(world)]?.expiresAt = world.time + LINGER_TICKS
    }

    fun clear(world: World) {
        byWorld.remove(key(world))
    }

    /** The active lure for this world, or null once it has lapsed. */
    fun get(world: World): BlockPos? {
        val lure = byWorld[key(world)] ?: return null
        if (world.time > lure.expiresAt) {
            byWorld.remove(key(world))
            return null
        }
        return lure.pos
    }
}
