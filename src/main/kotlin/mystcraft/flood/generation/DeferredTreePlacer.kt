package mystcraft.flood.generation

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.block.BlockState
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object DeferredTreePlacer {

    // This now handles any cross-chunk worldgen placement, not just trees.
    // LIMITER: 3 chunks per tick is usually a good balance between fill speed and hitching.
    private const val CHUNKS_PER_TICK = 3

    private val pendingPlacements = ConcurrentHashMap<String, ConcurrentHashMap<ChunkPos, CopyOnWriteArrayList<PendingPlacement>>>()

    data class PendingPlacement(val pos: BlockPos, val state: BlockState, val requireSoft: Boolean)

    fun add(world: ServerWorld, pos: BlockPos, state: BlockState, requireSoft: Boolean) {
        val worldId = world.registryKey.value.toString()
        val chunkPos = ChunkPos(pos)

        val worldMap = pendingPlacements.computeIfAbsent(worldId) { ConcurrentHashMap() }
        val chunkList = worldMap.computeIfAbsent(chunkPos) { CopyOnWriteArrayList() }

        chunkList.add(PendingPlacement(pos, state, requireSoft))
    }

    fun register() {
        ServerTickEvents.END_WORLD_TICK.register { world ->
            val worldId = world.registryKey.value.toString()
            val worldMap = pendingPlacements[worldId] ?: return@register

            if (worldMap.isEmpty()) return@register

            var chunksProcessedThisTick = 0
            val chunksToRemove = mutableListOf<ChunkPos>()
            val chunkIterator = worldMap.keys().iterator()

            while (chunkIterator.hasNext() && chunksProcessedThisTick < CHUNKS_PER_TICK) {
                val chunkPos = chunkIterator.next()

                if (world.isChunkLoaded(chunkPos.x, chunkPos.z)) {
                    val placements = worldMap[chunkPos]

                    if (placements != null) {
                        for (placement in placements) {
                            val pos = placement.pos
                            val state = placement.state
                            val existing = world.getBlockState(pos)

                            if (existing.getHardness(world, pos) < 0.0f) continue
                            if (placement.requireSoft && !existing.isAir && !existing.isReplaceable) continue

                            world.setBlockState(pos, state, 3)
                        }
                        chunksToRemove.add(chunkPos)
                        chunksProcessedThisTick++
                    }
                }
            }

            chunksToRemove.forEach { worldMap.remove(it) }
        }
    }
}
