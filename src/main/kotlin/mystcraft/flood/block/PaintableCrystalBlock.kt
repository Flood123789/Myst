package mystcraft.flood.block

import mystcraft.flood.block.entity.PaintedCrystalBlockEntity
import net.minecraft.block.Block
import net.minecraft.block.BlockRenderType
import net.minecraft.block.BlockState
import net.minecraft.block.BlockWithEntity
import net.minecraft.block.ShapeContext
import net.minecraft.block.entity.BlockEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView

/** Crystal that keeps its portal-frame behavior while optionally wearing another block's model. */
class PaintableCrystalBlock(settings: Settings) : BlockWithEntity(settings) {
    override fun createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
        PaintedCrystalBlockEntity(pos, state)

    @Deprecated("Deprecated in Java")
    override fun getRenderType(state: BlockState): BlockRenderType = BlockRenderType.MODEL

    @Deprecated("Deprecated in Java")
    override fun getCameraCollisionShape(
        state: BlockState,
        world: BlockView,
        pos: BlockPos,
        context: ShapeContext
    ): VoxelShape = net.minecraft.util.shape.VoxelShapes.empty()

    @Deprecated("Deprecated in Java")
    override fun getAmbientOcclusionLightLevel(state: BlockState, world: BlockView, pos: BlockPos): Float = 1.0f

    @Deprecated("Deprecated in Java")
    override fun isTransparent(state: BlockState, world: BlockView, pos: BlockPos): Boolean = true

    @Deprecated("Deprecated in Java")
    override fun isSideInvisible(state: BlockState, stateFrom: BlockState, direction: Direction): Boolean =
        stateFrom.isOf(this) || super.isSideInvisible(state, stateFrom, direction)
}
