package mystcraft.flood.generation

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.block.BlockState
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object DeferredTreePlacer {

    // LIMITER: Adjust this if you want it faster or slower.
    // 3 chunks per tick = 60 chunks per second. This is usually the "sweet spot."
    private const val CHUNKS_PER_TICK = 3 

    private val pendingBlocks = ConcurrentHashMap<String, ConcurrentHashMap<ChunkPos, CopyOnWriteArrayList<PendingBlock>>>()

    data class PendingBlock(val pos: BlockPos, val state: BlockState, val requireSoft: Boolean)

    fun add(world: ServerWorld, pos: BlockPos, state: BlockState, requireSoft: Boolean) {
        val worldId = world.registryKey.value.toString()
        val chunkPos = ChunkPos(pos)
        
        val worldMap = pendingBlocks.computeIfAbsent(worldId) { ConcurrentHashMap() }
        val chunkList = worldMap.computeIfAbsent(chunkPos) { CopyOnWriteArrayList() }
        
        chunkList.add(PendingBlock(pos, state, requireSoft))
    }

    fun register() {
        ServerTickEvents.END_WORLD_TICK.register { world ->
            val worldId = world.registryKey.value.toString()
            val worldMap = pendingBlocks[worldId] ?: return@register
            
            if (worldMap.isEmpty()) return@register

            var chunksProcessedThisTick = 0
            val chunksToRemove = mutableListOf<ChunkPos>()

            // We iterate through the keys (Chunk Positions)
            val chunkIterator = worldMap.keys().iterator()

            while (chunkIterator.hasNext() && chunksProcessedThisTick < CHUNKS_PER_TICK) {
                val chunkPos = chunkIterator.next()

                // Only process if the player has actually loaded this chunk
                if (world.isChunkLoaded(chunkPos.x, chunkPos.z)) {
                    val blocks = worldMap[chunkPos]
                    
                    if (blocks != null) {
                        for (blockData in blocks) {
                            val pos = blockData.pos
                            val state = blockData.state
                            
                            // Check existing block safely since the chunk is loaded
                            val existing = world.getBlockState(pos)
                            
                            if (existing.getHardness(world, pos) < 0.0f) continue
                            if (blockData.requireSoft && !existing.isAir && !existing.isReplaceable) continue
                            
                            // 2 = Flag to prevent neighbor updates (massive speed boost!)
                            // 3 = Update clients + neighbor updates. 
                            // We use 3 here to ensure lighting and visuals are correct.
                            world.setBlockState(pos, state, 3)
                        }
                        chunksToRemove.add(chunkPos)
                        chunksProcessedThisTick++
                    }
                }
            }

            // Remove chunks from the queue once finished
            chunksToRemove.forEach { worldMap.remove(it) }
        }
    }
}