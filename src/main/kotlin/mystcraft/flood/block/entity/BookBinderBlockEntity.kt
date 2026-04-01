package mystcraft.flood.block.entity

import mystcraft.flood.gui.BookBinderScreenHandler
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventories
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.screen.ScreenHandler
import net.minecraft.text.Text
import net.minecraft.util.collection.DefaultedList
import net.minecraft.util.math.BlockPos

class BookBinderBlockEntity(pos: BlockPos, state: BlockState) : 
    BlockEntity(ModBlockEntities.BOOK_BINDER_ENTITY, pos, state), 
    NamedScreenHandlerFactory, Inventory {

    // The 5 slots that match your GUI
    private var inventory: DefaultedList<ItemStack> = DefaultedList.ofSize(58, ItemStack.EMPTY)

    // === GUI CONNECTION ===
    override fun createMenu(syncId: Int, playerInventory: PlayerInventory, player: PlayerEntity): ScreenHandler {
        // This links the Block to the GUI we built in the last step!
        return BookBinderScreenHandler(syncId, playerInventory, this)
    }

    override fun getDisplayName(): Text = Text.translatable("block.mystcraft-reforged.book_binder")

    // === SAVE & LOAD ===
    override fun readNbt(nbt: NbtCompound) {
        super.readNbt(nbt)
        Inventories.readNbt(nbt, inventory)
    }

    override fun writeNbt(nbt: NbtCompound) {
        super.writeNbt(nbt)
        Inventories.writeNbt(nbt, inventory)
    }

    // === INVENTORY LOGIC ===
    override fun clear() = inventory.clear()
    override fun size(): Int = inventory.size
    override fun isEmpty(): Boolean = inventory.all { it.isEmpty }
    override fun getStack(slot: Int): ItemStack = inventory[slot]
    override fun removeStack(slot: Int, amount: Int): ItemStack = Inventories.splitStack(inventory, slot, amount)
    override fun removeStack(slot: Int): ItemStack = Inventories.removeStack(inventory, slot)
    override fun setStack(slot: Int, stack: ItemStack) {
        inventory[slot] = stack
        if (stack.count > maxCountPerStack) stack.count = maxCountPerStack
        markDirty()
    }
    override fun canPlayerUse(player: PlayerEntity): Boolean = true
}