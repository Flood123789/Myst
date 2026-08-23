package mystcraft.flood.client.cache

import mystcraft.flood.generation.interpolateAgeTime
import net.minecraft.util.Identifier
import java.util.concurrent.ConcurrentHashMap

/**
 * A dimension-keyed client clock for Mystcraft Ages.
 *
 * Vanilla time packets can express only running at 1x or frozen. Keeping an
 * anchor per Age lets portal renders query the correct fractional/accelerated
 * timeline without borrowing the active dimension's day/night state.
 */
object ClientAgeTimeCache {
    private data class Anchor(
        val visibleTime: Long,
        val timeScale: Float,
        val frozen: Boolean,
        val clientTick: Long
    )

    private val anchors = ConcurrentHashMap<Identifier, Anchor>()
    private var clientTick: Long = 0L

    fun tick() {
        clientTick++
    }

    fun update(id: Identifier, visibleTime: Long, timeScale: Float, frozen: Boolean) {
        anchors[id] = Anchor(visibleTime, timeScale.coerceAtLeast(0.0f), frozen, clientTick)
    }

    fun getVisibleTime(id: Identifier): Long? {
        val anchor = anchors[id] ?: return null
        return interpolateAgeTime(
            anchor.visibleTime,
            anchor.timeScale,
            anchor.frozen,
            clientTick - anchor.clientTick
        )
    }

    /** True once the server-authoritative Mystcraft clock has arrived for this Age. */
    fun hasAuthoritativeTime(id: Identifier): Boolean = anchors.containsKey(id)

    fun clear() {
        anchors.clear()
        clientTick = 0L
    }
}
