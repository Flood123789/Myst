package mystcraft.flood.block.entity

import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtHelper
import net.minecraft.registry.Registries
import net.minecraft.util.math.Direction
import net.minecraft.util.math.BlockPos

class PaintedCrystalBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(ModBlockEntities.PAINTED_CRYSTAL, pos, state) {

    private val paintedFaces = mutableMapOf<Direction, BlockState>()

    fun getPaintedState(face: Direction): BlockState? = paintedFaces[face]

    fun setPaintedState(face: Direction, state: BlockState) {
        paintedFaces[face] = state
        markDirty()
        world?.updateListeners(pos, cachedState, cachedState, 3)
    }

    override fun writeNbt(nbt: NbtCompound) {
        super.writeNbt(nbt)
        val facesNbt = NbtCompound()
        paintedFaces.forEach { (face, state) ->
            facesNbt.put(face.asString(), NbtHelper.fromBlockState(state))
        }
        nbt.put(PAINTED_FACES_KEY, facesNbt)
    }

    override fun readNbt(nbt: NbtCompound) {
        super.readNbt(nbt)
        paintedFaces.clear()
        if (nbt.contains(PAINTED_FACES_KEY)) {
            val facesNbt = nbt.getCompound(PAINTED_FACES_KEY)
            Direction.entries.forEach { face ->
                if (facesNbt.contains(face.asString())) {
                    paintedFaces[face] = NbtHelper.toBlockState(
                        Registries.BLOCK.readOnlyWrapper,
                        facesNbt.getCompound(face.asString())
                    )
                }
            }
        }
    }

    override fun toUpdatePacket() = net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket.create(this)

    override fun toInitialChunkDataNbt(): NbtCompound = createNbt()

    companion object {
        const val PAINTED_FACES_KEY = "PaintedFaces"
    }
}
