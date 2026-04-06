package mystcraft.flood.block

import mystcraft.flood.block.entity.BookReceptacleBlockEntity
import net.minecraft.block.Block
import net.minecraft.block.BlockRenderType
import net.minecraft.block.BlockState
import net.minecraft.block.BlockWithEntity
import net.minecraft.block.ShapeContext
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemPlacementContext
import net.minecraft.state.StateManager
import net.minecraft.state.property.Properties
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.ItemScatterer
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.world.BlockView
import net.minecraft.world.World

class BookReceptacleBlock(settings: Settings) : BlockWithEntity(settings) {

    init {
        // Now uses full 6-axis FACING!
        defaultState = stateManager.defaultState.with(Properties.FACING, Direction.NORTH)
    }

    @Deprecated("Deprecated in Java")
    override fun getRenderType(state: BlockState): BlockRenderType {
        return BlockRenderType.MODEL
    }

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape {
        return when (state.get(Properties.FACING)) {
            Direction.NORTH -> Block.createCuboidShape(0.0, 0.0, 10.0, 16.0, 16.0, 16.0)
            Direction.SOUTH -> Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 6.0)
            Direction.EAST ->  Block.createCuboidShape(0.0, 0.0, 0.0, 6.0, 16.0, 16.0)
            Direction.WEST ->  Block.createCuboidShape(10.0, 0.0, 0.0, 16.0, 16.0, 16.0)
            Direction.UP ->    Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 6.0, 16.0) // On Floor
            Direction.DOWN ->  Block.createCuboidShape(0.0, 10.0, 0.0, 16.0, 16.0, 16.0) // On Ceiling
            else -> VoxelShapes.fullCube()
        }
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(Properties.FACING)
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState {
        // Snaps its back to whatever block face you clicked on!
        return defaultState.with(Properties.FACING, ctx.side)
    }

    override fun createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return BookReceptacleBlockEntity(pos, state)
    }

    @Deprecated("Deprecated in Java")
    override fun onStateReplaced(state: BlockState, world: World, pos: BlockPos, newState: BlockState, moved: Boolean) {
        if (!state.isOf(newState.block)) {
            val be = world.getBlockEntity(pos) as? BookReceptacleBlockEntity
            if (be != null) {
                val bookStack = be.inventory.getStack(0).copy()
                if (!bookStack.isEmpty) {
                    ItemScatterer.spawn(world, pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), bookStack)
                }
                be.extinguishPortal()
            }
            super.onStateReplaced(state, world, pos, newState, moved)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        if (world.isClient) return ActionResult.SUCCESS

        val be = world.getBlockEntity(pos) as? BookReceptacleBlockEntity ?: return ActionResult.PASS
        val stackInHand = player.getStackInHand(hand)

        if (be.hasBook()) {
            val extractedBook = be.removeBook() 
            
            // Pop the book outward using full 3D facing velocity!
            val facing = state.get(Properties.FACING)
            val spawnX = pos.x + 0.5 + (facing.offsetX * 0.6)
            val spawnY = pos.y + 0.5 + (facing.offsetY * 0.6)
            val spawnZ = pos.z + 0.5 + (facing.offsetZ * 0.6)
            
            val itemEntity = ItemEntity(world, spawnX, spawnY, spawnZ, extractedBook)
            itemEntity.velocity = Vec3d(facing.offsetX * 0.15, facing.offsetY * 0.15 + 0.1, facing.offsetZ * 0.15)
            world.spawnEntity(itemEntity)
            
            return ActionResult.SUCCESS
        } else if (!stackInHand.isEmpty) { 
            be.insertBook(stackInHand.split(1))
            return ActionResult.SUCCESS
        }
        return ActionResult.PASS
    }
}