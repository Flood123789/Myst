package mystcraft.flood.block

import mystcraft.flood.generation.profile.AgeProfileManager
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.DoorBlock
import net.minecraft.block.FenceBlock
import net.minecraft.block.LanternBlock
import net.minecraft.block.PaneBlock
import net.minecraft.block.TorchBlock
import net.minecraft.block.TrapdoorBlock
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.random.Random
import net.minecraft.world.World

/**
 * Shared spreading and shape-preservation behavior for every white-decay block variant.
 *
 * White decay can copy the geometry of the block it consumes, so slabs, stairs, fences, panes,
 * doors, lights, and walls delegate here instead of each implementing a different spread rule.
 * All mutations remain subject to [DecayManager]'s global tick budget.
 */
object WhiteDecayLogic {
    fun neighborUpdate(block: Block, state: BlockState, world: World, pos: BlockPos) {
        val serverWorld = world as? ServerWorld ?: return
        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, serverWorld.registryKey.value)
        if (profile.stability.isStable || !profile.stability.effectsEnabled || profile.stability.instabilityScore <= 0) return

        if (state.contains(WhiteDecayBlock.ACTIVE) && !state[WhiteDecayBlock.ACTIVE] && hasSpreadTarget(serverWorld, pos)) {
            serverWorld.setBlockState(pos, state.with(WhiteDecayBlock.ACTIVE, true), 3)
        }
    }

    fun randomTick(block: Block, state: BlockState, world: ServerWorld, pos: BlockPos, random: Random) {
        val profile = AgeProfileManager.getOrGenerateProfile(world.server, world.registryKey.value)
        if (profile.stability.isStable || !profile.stability.effectsEnabled || profile.stability.instabilityScore <= 0) return
        if (!DecayManager.requestPermission(2)) return

        val targets = Direction.entries
            .map { pos.offset(it) }
            .filter { canPetrify(world, it) }

        if (targets.isEmpty()) {
            if (state.contains(WhiteDecayBlock.ACTIVE) && state[WhiteDecayBlock.ACTIVE]) {
                world.setBlockState(pos, state.with(WhiteDecayBlock.ACTIVE, false), 3)
            }
            return
        }

        val target = targets[random.nextInt(targets.size)]
        val targetState = world.getBlockState(target)
        world.setBlockState(target, petrifiedStateFor(world, target, targetState), 3)

        if (!hasSpreadTarget(world, pos) && state.contains(WhiteDecayBlock.ACTIVE)) {
            world.setBlockState(pos, state.with(WhiteDecayBlock.ACTIVE, false), 3)
        }
    }

    fun hasSpreadTarget(world: ServerWorld, pos: BlockPos): Boolean =
        Direction.entries.any { canPetrify(world, pos.offset(it)) }

    fun canPetrify(world: ServerWorld, pos: BlockPos): Boolean {
        if (pos.y < world.bottomY || pos.y >= world.topY) return false
        val state = world.getBlockState(pos)
        if (state.isAir) return false
        if (isWhiteDecayState(state) || state.isOf(ModBlocks.BLACK_DECAY) || state.isOf(ModBlocks.STAR_FISSURE)) return false
        if (state.isOf(Blocks.BEDROCK) || state.isOf(Blocks.END_PORTAL_FRAME) || state.isOf(Blocks.END_PORTAL)) return false
        if (state.getHardness(world, pos) < 0.0f) return false
        return true
    }

    fun isWhiteDecayState(state: BlockState): Boolean = when (state.block) {
        ModBlocks.WHITE_DECAY,
        ModBlocks.WHITE_DECAY_SLAB,
        ModBlocks.WHITE_DECAY_STAIRS,
        ModBlocks.WHITE_DECAY_FENCE,
        ModBlocks.WHITE_DECAY_PANE,
        ModBlocks.WHITE_DECAY_TRAPDOOR,
        ModBlocks.WHITE_DECAY_DOOR,
        ModBlocks.WHITE_DECAY_TORCH,
        ModBlocks.WHITE_DECAY_WALL_TORCH,
        ModBlocks.WHITE_DECAY_LANTERN -> true
        else -> false
    }

    fun petrifiedStateFor(world: ServerWorld, pos: BlockPos, original: BlockState): BlockState {
        val target = when (WhiteDecayShapeRegistry.familyFor(original)) {
            WhiteDecayShapeRegistry.ShapeFamily.SLAB -> ModBlocks.WHITE_DECAY_SLAB.defaultState
            WhiteDecayShapeRegistry.ShapeFamily.STAIRS -> ModBlocks.WHITE_DECAY_STAIRS.defaultState
            WhiteDecayShapeRegistry.ShapeFamily.FENCE -> ModBlocks.WHITE_DECAY_FENCE.defaultState
            WhiteDecayShapeRegistry.ShapeFamily.PANE -> ModBlocks.WHITE_DECAY_PANE.defaultState
            WhiteDecayShapeRegistry.ShapeFamily.TRAPDOOR -> ModBlocks.WHITE_DECAY_TRAPDOOR.defaultState
            WhiteDecayShapeRegistry.ShapeFamily.DOOR -> ModBlocks.WHITE_DECAY_DOOR.defaultState
            WhiteDecayShapeRegistry.ShapeFamily.TORCH -> ModBlocks.WHITE_DECAY_TORCH.defaultState
            WhiteDecayShapeRegistry.ShapeFamily.WALL_TORCH -> ModBlocks.WHITE_DECAY_WALL_TORCH.defaultState
            WhiteDecayShapeRegistry.ShapeFamily.LANTERN -> ModBlocks.WHITE_DECAY_LANTERN.defaultState
            WhiteDecayShapeRegistry.ShapeFamily.FULL -> ModBlocks.WHITE_DECAY.defaultState
        }

        var result = copySupportedProperties(original, target)
            .with(WhiteDecayBlock.ACTIVE, true)

        if (result.block is FenceBlock || result.block is PaneBlock || result.block is DoorBlock || result.block is TrapdoorBlock) {
            result = Block.postProcessState(result, world, pos)
        }

        return result
    }

    private fun copySupportedProperties(source: BlockState, target: BlockState): BlockState {
        var result = target

        if (source.contains(Properties.WATERLOGGED) && result.contains(Properties.WATERLOGGED)) {
            result = result.with(Properties.WATERLOGGED, source[Properties.WATERLOGGED])
        }

        if (source.contains(Properties.SLAB_TYPE) && result.contains(Properties.SLAB_TYPE)) {
            result = result.with(Properties.SLAB_TYPE, source[Properties.SLAB_TYPE])
        }

        if (source.contains(Properties.HORIZONTAL_FACING) && result.contains(Properties.HORIZONTAL_FACING)) {
            result = result.with(Properties.HORIZONTAL_FACING, source[Properties.HORIZONTAL_FACING])
        }

        if (source.contains(Properties.BLOCK_HALF) && result.contains(Properties.BLOCK_HALF)) {
            result = result.with(Properties.BLOCK_HALF, source[Properties.BLOCK_HALF])
        }

        if (source.contains(Properties.STAIR_SHAPE) && result.contains(Properties.STAIR_SHAPE)) {
            result = result.with(Properties.STAIR_SHAPE, source[Properties.STAIR_SHAPE])
        }
        if (source.contains(Properties.OPEN) && result.contains(Properties.OPEN)) {
            result = result.with(Properties.OPEN, source[Properties.OPEN])
        }
        if (source.contains(Properties.POWERED) && result.contains(Properties.POWERED)) {
            result = result.with(Properties.POWERED, source[Properties.POWERED])
        }
        if (source.contains(Properties.DOOR_HINGE) && result.contains(Properties.DOOR_HINGE)) {
            result = result.with(Properties.DOOR_HINGE, source[Properties.DOOR_HINGE])
        }
        if (source.contains(Properties.DOUBLE_BLOCK_HALF) && result.contains(Properties.DOUBLE_BLOCK_HALF)) {
            result = result.with(Properties.DOUBLE_BLOCK_HALF, source[Properties.DOUBLE_BLOCK_HALF])
        }
        if (source.contains(Properties.HANGING) && result.contains(Properties.HANGING)) {
            result = result.with(Properties.HANGING, source[Properties.HANGING])
        }

        if (source.contains(Properties.NORTH) && result.contains(Properties.NORTH) && result.block !is FenceBlock && result.block !is PaneBlock) {
            result = result.with(Properties.NORTH, source[Properties.NORTH])
        }
        if (source.contains(Properties.SOUTH) && result.contains(Properties.SOUTH) && result.block !is FenceBlock && result.block !is PaneBlock) {
            result = result.with(Properties.SOUTH, source[Properties.SOUTH])
        }
        if (source.contains(Properties.EAST) && result.contains(Properties.EAST) && result.block !is FenceBlock && result.block !is PaneBlock) {
            result = result.with(Properties.EAST, source[Properties.EAST])
        }
        if (source.contains(Properties.WEST) && result.contains(Properties.WEST) && result.block !is FenceBlock && result.block !is PaneBlock) {
            result = result.with(Properties.WEST, source[Properties.WEST])
        }

        return result
    }
}
