package mystcraft.flood.block

import mystcraft.flood.block.entity.BookStandBlockEntity
import mystcraft.flood.item.DisplayedBookHelper
import mystcraft.flood.network.ModMessages
import net.minecraft.block.Block
import net.minecraft.block.BlockEntityProvider
import net.minecraft.block.BlockRenderType
import net.minecraft.block.BlockState
import net.minecraft.block.BlockWithEntity
import net.minecraft.block.ShapeContext
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemStack
import net.minecraft.state.StateManager
import net.minecraft.state.property.Properties
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView
import net.minecraft.world.World

class BookStandBlock(settings: Settings) : BlockWithEntity(settings), BlockEntityProvider {
    init {
        defaultState = stateManager.defaultState.with(Properties.HORIZONTAL_FACING, Direction.NORTH)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(Properties.HORIZONTAL_FACING)
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState =
        defaultState.with(Properties.HORIZONTAL_FACING, ctx.horizontalPlayerFacing.opposite)

    override fun createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
        BookStandBlockEntity(pos, state)

    override fun getRenderType(state: BlockState): BlockRenderType = BlockRenderType.ENTITYBLOCK_ANIMATED

    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        SHAPE

    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        val blockEntity = world.getBlockEntity(pos) as? BookStandBlockEntity ?: return ActionResult.PASS
        val heldStack = player.getStackInHand(hand)

        if (!blockEntity.hasBook()) {
            if (!DisplayedBookHelper.isDisplayableBook(heldStack)) {
                return ActionResult.PASS
            }

            if (world.isClient) {
                return ActionResult.SUCCESS
            }

            val inserted = heldStack.copy()
            inserted.count = 1
            blockEntity.setBook(inserted)
            world.updateListeners(pos, state, state, Block.NOTIFY_ALL or 8)
            if (!player.isCreative) {
                heldStack.decrement(1)
            }
            return ActionResult.SUCCESS
        }

        val displayedBook = blockEntity.getBook()
        if (world.isClient) {
            return ActionResult.SUCCESS
        }

        if (player.isSneaking) {
            val removed = blockEntity.removeBook()
            world.updateListeners(pos, state, state, Block.NOTIFY_ALL or 8)
            if (heldStack.isEmpty) {
                player.setStackInHand(hand, removed)
            } else if (!player.giveItemStack(removed)) {
                player.dropItem(removed, false)
            }
            return ActionResult.SUCCESS
        }

        val serverPlayer = player as? net.minecraft.server.network.ServerPlayerEntity ?: return ActionResult.SUCCESS
        if (DisplayedBookHelper.isDescriptiveBook(displayedBook)) {
            ModMessages.sendOpenDescriptiveBook(serverPlayer, displayedBook.copy(), standPos = pos)
            return ActionResult.SUCCESS
        }
        DisplayedBookHelper.activate(world, serverPlayer, displayedBook.copy())
        return ActionResult.SUCCESS
    }

    override fun onStateReplaced(state: BlockState, world: World, pos: BlockPos, newState: BlockState, moved: Boolean) {
        if (!state.isOf(newState.block)) {
            val blockEntity = world.getBlockEntity(pos) as? BookStandBlockEntity
            if (blockEntity != null) {
                val book = blockEntity.removeBook()
                if (!book.isEmpty) {
                    world.spawnEntity(ItemEntity(world, pos.x + 0.5, pos.y + 0.75, pos.z + 0.5, book))
                }
            }
            super.onStateReplaced(state, world, pos, newState, moved)
        }
    }

    companion object {
        private val SHAPE = Block.createCuboidShape(2.0, 0.0, 2.0, 14.0, 13.0, 14.0)
    }
}
