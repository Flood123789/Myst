package mystcraft.flood.generation.instability

import java.util.UUID

/** Hard per-player cooldown layered under the probabilistic natural Reaper spawn roll. */
object ReaperSpawnCadence {
    private val nextSpawnTick = mutableMapOf<UUID, Int>()

    fun isReady(player: UUID, serverTick: Int): Boolean = serverTick >= (nextSpawnTick[player] ?: Int.MIN_VALUE)

    fun markSpawned(player: UUID, serverTick: Int, cooldownSeconds: Int) {
        nextSpawnTick[player] = serverTick + cooldownSeconds.coerceAtLeast(0) * 20
    }

    fun clear() {
        nextSpawnTick.clear()
    }
}
