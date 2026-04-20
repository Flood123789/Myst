package mystcraft.flood.gui

import mystcraft.flood.item.ModItems
import mystcraft.flood.item.NotebookItem
import mystcraft.flood.item.SymbolPageItem
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.network.PacketByteBuf
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot
import net.minecraft.util.Hand

class NotebookScreenHandler(
    syncId: Int,
    private val playerInventory: PlayerInventory,
    private val hand: Hand,
    private val notebookInventory: Inventory = SimpleInventory(56)
) : ScreenHandler(ModScreens.NOTEBOOK_HANDLER, syncId) {

    private val notebookSlotIndex: Int = if (hand == Hand.MAIN_HAND) playerInventory.selectedSlot else 40

    constructor(syncId: Int, playerInventory: PlayerInventory, buf: PacketByteBuf) : this(
        syncId,
        playerInventory,
        buf.readEnumConstant(Hand::class.java)
    )

    init {
        loadNotebook()

        for (row in 0 until 8) {
            for (col in 0 until 7) {
                val slotIndex = row * 7 + col
                addSlot(object : Slot(notebookInventory, slotIndex, 25 + col * 18, 18 + row * 18) {
                    override fun canInsert(stack: ItemStack): Boolean = stack.item is SymbolPageItem
                    override fun getMaxItemCount(): Int = 1
                })
            }
        }

        for (row in 0 until 3) {
            for (col in 0 until 9) {
                val index = col + row * 9 + 9
                addSlot(playerSlot(playerInventory, index, 8 + col * 18, 185 + row * 18))
            }
        }

        for (col in 0 until 9) {
            addSlot(playerSlot(playerInventory, col, 8 + col * 18, 243))
        }
    }

    override fun onContentChanged(inventory: Inventory) {
        super.onContentChanged(inventory)
        if (inventory === notebookInventory && !playerInventory.player.world.isClient) {
            persistNotebook()
        }
    }

    override fun quickMove(player: PlayerEntity, invSlot: Int): ItemStack {
        val slot = slots[invSlot]
        if (!slot.hasStack()) return ItemStack.EMPTY

        val original = slot.stack
        val copy = original.copy()
        val notebookSlotsEnd = 56

        if (invSlot < notebookSlotsEnd) {
            if (!insertItem(original, notebookSlotsEnd, slots.size, true)) {
                return ItemStack.EMPTY
            }
        } else {
            if (original.item !is SymbolPageItem) return ItemStack.EMPTY
            if (!insertItem(original, 0, notebookSlotsEnd, false)) {
                return ItemStack.EMPTY
            }
        }

        if (original.isEmpty) {
            slot.stack = ItemStack.EMPTY
        } else {
            slot.markDirty()
        }

        slot.onTakeItem(player, original)
        persistNotebook()
        return copy
    }

    override fun canUse(player: PlayerEntity): Boolean = currentNotebookStack().isOf(ModItems.NOTEBOOK)

    override fun onClosed(player: PlayerEntity) {
        super.onClosed(player)
        if (!player.world.isClient) {
            persistNotebook()
        }
    }

    fun getNotebookName(): String = NotebookItem.getNotebookName(currentNotebookStack()).orEmpty()

    fun setNotebookName(name: String) {
        NotebookItem.setNotebookName(currentNotebookStack(), name)
    }

    private fun playerSlot(inventory: PlayerInventory, index: Int, x: Int, y: Int): Slot {
        return if (hand == Hand.MAIN_HAND && index == notebookSlotIndex) {
            object : Slot(inventory, index, x, y) {
                override fun canTakeItems(playerEntity: PlayerEntity): Boolean = false
                override fun canInsert(stack: ItemStack): Boolean = false
            }
        } else {
            Slot(inventory, index, x, y)
        }
    }

    private fun loadNotebook() {
        val symbols = NotebookItem.getSymbols(currentNotebookStack())
        for (index in symbols.indices) {
            notebookInventory.setStack(index, NotebookItem.createPageStack(symbols[index]))
        }
    }

    private fun persistNotebook() {
        val symbols = buildList {
            for (slot in 0 until notebookInventory.size()) {
                val stack = notebookInventory.getStack(slot)
                if (stack.item is SymbolPageItem) {
                    NotebookItem.extractSymbol(stack)?.let(::add)
                }
            }
        }
        NotebookItem.setSymbols(currentNotebookStack(), symbols)
    }

    private fun currentNotebookStack(): ItemStack {
        val player = playerInventory.player
        return if (hand == Hand.MAIN_HAND) {
            playerInventory.getStack(notebookSlotIndex)
        } else {
            player.offHandStack
        }
    }
}
