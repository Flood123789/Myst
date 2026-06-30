package mystcraft.flood.block.entity

import mystcraft.flood.network.ModMessages
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos

class BookStandBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(ModBlockEntities.BOOK_STAND, pos, state) {
    val inventory = SimpleInventory(1)

    override fun writeNbt(nbt: NbtCompound) {
        super.writeNbt(nbt)
        val book = inventory.getStack(0)
        nbt.putBoolean("HasBook", !book.isEmpty)
        if (!book.isEmpty) {
            val tag = NbtCompound()
            book.writeNbt(tag)
            nbt.put("Book", tag)
        }
    }

    override fun readNbt(nbt: NbtCompound) {
        super.readNbt(nbt)
        val hasBook = if (nbt.contains("HasBook")) nbt.getBoolean("HasBook") else nbt.contains("Book")
        inventory.setStack(0, if (hasBook && nbt.contains("Book")) ItemStack.fromNbt(nbt.getCompound("Book")) else ItemStack.EMPTY)
    }

    override fun toUpdatePacket(): net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket? =
        net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket.create(this)

    override fun toInitialChunkDataNbt(): NbtCompound = createNbt()

    fun hasBook(): Boolean = !inventory.getStack(0).isEmpty

    fun getBook(): ItemStack = inventory.getStack(0)

    fun setBook(stack: ItemStack) {
        inventory.setStack(0, stack)
        syncVisuals()
    }

    fun removeBook(): ItemStack {
        val stack = inventory.getStack(0).copy()
        inventory.setStack(0, ItemStack.EMPTY)
        syncVisuals()
        return stack
    }

    fun markBookDirty() {
        syncVisuals()
    }

    fun applySyncedBook(stack: ItemStack) {
        inventory.setStack(0, if (stack.isEmpty) ItemStack.EMPTY else stack.copy())
        markDirty()
    }

    private fun syncVisuals() {
        markDirty()
        val currentWorld = world ?: return
        currentWorld.updateListeners(pos, cachedState, cachedState, Block.NOTIFY_ALL or 8)
        (currentWorld as? ServerWorld)?.let { serverWorld ->
            serverWorld.chunkManager.markForUpdate(pos)
            ModMessages.sendBookStandSync(serverWorld, pos, inventory.getStack(0))
        }
    }
}
