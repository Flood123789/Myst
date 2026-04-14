package mystcraft.flood.block

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.registry.Registries
import net.minecraft.state.property.Properties
import java.util.concurrent.ConcurrentHashMap

object WhiteDecayShapeRegistry {
    enum class ShapeFamily {
        FULL,
        SLAB,
        STAIRS,
        FENCE,
        PANE,
        TRAPDOOR,
        DOOR,
        TORCH,
        WALL_TORCH,
        LANTERN
    }

    private val families = ConcurrentHashMap<Block, ShapeFamily>()

    fun warmUp() {
        Registries.BLOCK.forEach { block ->
            families.putIfAbsent(block, classify(block.defaultState))
        }
    }

    fun familyFor(state: BlockState): ShapeFamily =
        families.computeIfAbsent(state.block) { classify(state) }

    private fun classify(state: BlockState): ShapeFamily = when {
        state.contains(Properties.SLAB_TYPE) -> ShapeFamily.SLAB
        state.contains(Properties.STAIR_SHAPE) && state.contains(Properties.BLOCK_HALF) -> ShapeFamily.STAIRS
        state.contains(Properties.NORTH) && state.contains(Properties.EAST) &&
            state.contains(Properties.SOUTH) && state.contains(Properties.WEST) &&
            state.contains(Properties.WATERLOGGED) &&
            state.block is net.minecraft.block.FenceBlock -> ShapeFamily.FENCE
        state.contains(Properties.NORTH) && state.contains(Properties.EAST) &&
            state.contains(Properties.SOUTH) && state.contains(Properties.WEST) &&
            state.contains(Properties.WATERLOGGED) &&
            state.block is net.minecraft.block.PaneBlock -> ShapeFamily.PANE
        state.block is net.minecraft.block.WallTorchBlock -> ShapeFamily.WALL_TORCH
        state.block is net.minecraft.block.TorchBlock -> ShapeFamily.TORCH
        state.block is net.minecraft.block.LanternBlock -> ShapeFamily.LANTERN
        state.block is net.minecraft.block.DoorBlock -> ShapeFamily.DOOR
        state.block is net.minecraft.block.TrapdoorBlock -> ShapeFamily.TRAPDOOR
        else -> ShapeFamily.FULL
    }
}
