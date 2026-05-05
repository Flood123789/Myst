package mystcraft.flood.block.entity

import mystcraft.flood.gui.PrintingTableScreenHandler
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventories
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.screen.ArrayPropertyDelegate
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.screen.PropertyDelegate
import net.minecraft.screen.ScreenHandler
import net.minecraft.text.Text
import net.minecraft.util.collection.DefaultedList
import net.minecraft.util.math.BlockPos

class PrintingTableBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(ModBlockEntities.PRINTING_TABLE, pos, state),
    NamedScreenHandlerFactory,
    Inventory {

    private val inventory = DefaultedList.ofSize(3, ItemStack.EMPTY)
    private val properties = object : ArrayPropertyDelegate(1) {
        override fun get(index: Int): Int = when (index) {
            0 -> selectedSymbolIndex
            else -> 0
        }

        override fun set(index: Int, value: Int) {
            if (index == 0) {
                selectedSymbolIndex = value.coerceAtLeast(0)
            }
        }
    }

    var selectedSymbolIndex: Int = 0
        set(value) {
            field = value.coerceAtLeast(0)
            markDirty()
        }

    override fun createMenu(syncId: Int, playerInventory: PlayerInventory, player: PlayerEntity): ScreenHandler =
        PrintingTableScreenHandler(syncId, playerInventory, this, properties)

    override fun getDisplayName(): Text = Text.translatable("block.mystcraft-reforged.printing_table")

    override fun readNbt(nbt: NbtCompound) {
        super.readNbt(nbt)
        Inventories.readNbt(nbt, inventory)
        selectedSymbolIndex = nbt.getInt("SelectedSymbolIndex")
    }

    override fun writeNbt(nbt: NbtCompound) {
        super.writeNbt(nbt)
        Inventories.writeNbt(nbt, inventory)
        nbt.putInt("SelectedSymbolIndex", selectedSymbolIndex)
    }

    override fun clear() = inventory.clear()
    override fun size(): Int = inventory.size
    override fun isEmpty(): Boolean = inventory.all { it.isEmpty }
    override fun getStack(slot: Int): ItemStack = inventory[slot]
    override fun removeStack(slot: Int, amount: Int): ItemStack = Inventories.splitStack(inventory, slot, amount)
    override fun removeStack(slot: Int): ItemStack = Inventories.removeStack(inventory, slot)

    override fun setStack(slot: Int, stack: ItemStack) {
        inventory[slot] = stack
        if (stack.count > maxCountPerStack) {
            stack.count = maxCountPerStack
        }
        markDirty()
    }

    override fun canPlayerUse(player: PlayerEntity): Boolean = true

    override fun markDirty() {
        super<BlockEntity>.markDirty()
        if (world != null && !world!!.isClient) {
            world!!.updateListeners(pos, cachedState, cachedState, 3)
        }
    }
}
