package mystcraft.flood.gui

import mystcraft.flood.block.entity.PrintingTableBlockEntity
import mystcraft.flood.item.AgeBookIntegrity
import mystcraft.flood.item.DescriptiveBookItem
import mystcraft.flood.item.ModItems
import mystcraft.flood.item.NotebookItem
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.screen.ArrayPropertyDelegate
import net.minecraft.screen.PropertyDelegate
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot
import net.minecraft.text.Text

class PrintingTableScreenHandler(
    syncId: Int,
    private val playerInventory: PlayerInventory,
    private val tableInventory: Inventory = SimpleInventory(3),
    private val propertyDelegate: PropertyDelegate = ArrayPropertyDelegate(1)
) : ScreenHandler(ModScreens.PRINTING_TABLE_HANDLER, syncId) {

    companion object {
        const val SLOT_SOURCE = 0
        const val SLOT_MATERIAL = 1
        const val SLOT_OUTPUT = 2
    }

    constructor(syncId: Int, playerInventory: PlayerInventory) : this(
        syncId,
        playerInventory,
        SimpleInventory(3),
        ArrayPropertyDelegate(1)
    )

    constructor(
        syncId: Int,
        playerInventory: PlayerInventory,
        blockEntity: PrintingTableBlockEntity,
        propertyDelegate: PropertyDelegate
    ) : this(syncId, playerInventory, blockEntity as Inventory, propertyDelegate)

    private var refreshingOutput = false

    init {
        checkSize(tableInventory, 3)
        tableInventory.onOpen(playerInventory.player)
        addProperties(propertyDelegate)

        addSlot(object : Slot(tableInventory, SLOT_SOURCE, 24, 35) {
            override fun canInsert(stack: ItemStack): Boolean =
                stack.isOf(ModItems.NOTEBOOK) ||
                    stack.isOf(ModItems.LINKING_BOOK) ||
                    stack.item is DescriptiveBookItem

            override fun getMaxItemCount(): Int = 1

            override fun markDirty() {
                super.markDirty()
                onContentChanged(tableInventory)
            }
        })

        addSlot(object : Slot(tableInventory, SLOT_MATERIAL, 80, 35) {
            override fun canInsert(stack: ItemStack): Boolean =
                stack.isOf(ModItems.PAGE) || stack.isOf(Items.LEATHER)

            override fun markDirty() {
                super.markDirty()
                onContentChanged(tableInventory)
            }
        })

        addSlot(object : Slot(tableInventory, SLOT_OUTPUT, 136, 35) {
            override fun canInsert(stack: ItemStack): Boolean = false
            override fun getMaxItemCount(): Int = 1

            override fun onTakeItem(player: PlayerEntity, stack: ItemStack) {
                super.onTakeItem(player, stack)
                finalizePrint()
            }
        })

        for (row in 0 until 3) {
            for (col in 0 until 9) {
                addSlot(Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 139 + row * 18))
            }
        }
        for (col in 0 until 9) {
            addSlot(Slot(playerInventory, col, 8 + col * 18, 197))
        }

        refreshOutput()
    }

    override fun onContentChanged(inventory: Inventory) {
        super.onContentChanged(inventory)
        if (!refreshingOutput) {
            if (printMode() != PrintMode.NOTEBOOK_PAGE) {
                selectedSymbolIndex = 0
            } else {
                clampSelectedIndex()
            }
            refreshOutput()
        }
    }

    fun canCycle(delta: Int): Boolean {
        val count = notebookSymbolCount()
        if (count <= 1) return false
        val next = selectedSymbolIndex + delta
        return next in 0 until count
    }

    fun cycleSelection(delta: Int) {
        val count = notebookSymbolCount()
        if (count <= 0) return
        val next = (selectedSymbolIndex + delta).coerceIn(0, count - 1)
        if (next != selectedSymbolIndex) {
            selectedSymbolIndex = next
            refreshOutput()
        }
    }

    fun selectedSymbolLabel(): String? {
        val symbols = NotebookItem.getSymbols(currentSource())
        return symbols.getOrNull(selectedSymbolIndex)?.let(NotebookItem::formatSymbol)
    }

    fun notebookSymbolCount(): Int = NotebookItem.getSymbols(currentSource()).size

    fun canPrint(): Boolean = !currentOutput().isEmpty

    fun statusLines(): List<String> = when (printMode()) {
        PrintMode.NONE -> listOf(
            "Set a notebook with empty pages, or a finished book with leather.",
            "The table only commits the work when you take the result."
        )
        PrintMode.NOTEBOOK_PAGE -> listOf(
            "Empty pages publish the chosen leaf as a loose symbol page.",
            selectedSymbolLabel()?.let { "Selected leaf: $it." } ?: "Choose a leaf to print."
        )
        PrintMode.BOOK_COPY -> listOf(
            "Leather copies the bound volume without disturbing the original.",
            "Each copy still shares the Age's fate until the final surviving book is lost."
        )
    }

    override fun quickMove(player: PlayerEntity, invSlot: Int): ItemStack {
        val slot = slots[invSlot]
        if (!slot.hasStack()) return ItemStack.EMPTY

        val original = slot.stack
        val copy = original.copy()
        val containerSlots = 3

        if (invSlot < containerSlots) {
            if (!insertItem(original, containerSlots, slots.size, true)) {
                return ItemStack.EMPTY
            }
        } else {
            when {
                original.isOf(ModItems.NOTEBOOK) || original.isOf(ModItems.LINKING_BOOK) || original.item is DescriptiveBookItem -> {
                    if (!insertItem(original, SLOT_SOURCE, SLOT_SOURCE + 1, false)) return ItemStack.EMPTY
                }
                original.isOf(ModItems.PAGE) || original.isOf(Items.LEATHER) -> {
                    if (!insertItem(original, SLOT_MATERIAL, SLOT_MATERIAL + 1, false)) return ItemStack.EMPTY
                }
                else -> return ItemStack.EMPTY
            }
        }

        if (original.isEmpty) {
            slot.stack = ItemStack.EMPTY
        } else {
            slot.markDirty()
        }

        slot.onTakeItem(player, original)
        return copy
    }

    override fun onClosed(player: PlayerEntity) {
        super.onClosed(player)
        tableInventory.onClose(player)
    }

    override fun canUse(player: PlayerEntity): Boolean = tableInventory.canPlayerUse(player)

    private var selectedSymbolIndex: Int
        get() = propertyDelegate.get(0)
        set(value) = propertyDelegate.set(0, value)

    private fun finalizePrint() {
        when (printMode()) {
            PrintMode.NOTEBOOK_PAGE -> consumeMaterial(1)
            PrintMode.BOOK_COPY -> consumeMaterial(1)
            PrintMode.NONE -> return
        }
        refreshOutput()
        sendContentUpdates()
    }

    private fun refreshOutput() {
        refreshingOutput = true
        try {
            val output = when (printMode()) {
                PrintMode.NOTEBOOK_PAGE -> buildPrintedSymbolPage()
                PrintMode.BOOK_COPY -> buildCopiedBook()
                PrintMode.NONE -> ItemStack.EMPTY
            }
            tableInventory.setStack(SLOT_OUTPUT, output)
        } finally {
            refreshingOutput = false
        }
    }

    private fun buildPrintedSymbolPage(): ItemStack {
        val symbols = NotebookItem.getSymbols(currentSource())
        val symbol = symbols.getOrNull(selectedSymbolIndex) ?: return ItemStack.EMPTY
        return NotebookItem.createPageStack(symbol)
    }

    private fun buildCopiedBook(): ItemStack {
        val source = currentSource()
        if (source.isEmpty) return ItemStack.EMPTY
        if (source.item is DescriptiveBookItem && source.nbt?.contains("Age_ID") == true) {
            AgeBookIntegrity.ensureTrackedLinkedBook(source)
        }
        return source.copy().also { it.count = 1 }
    }

    private fun printMode(): PrintMode {
        val source = currentSource()
        val material = currentMaterial()
        return when {
            source.isOf(ModItems.NOTEBOOK) &&
                NotebookItem.getSymbols(source).isNotEmpty() &&
                material.isOf(ModItems.PAGE) -> PrintMode.NOTEBOOK_PAGE

            (source.isOf(ModItems.LINKING_BOOK) || source.item is DescriptiveBookItem) &&
                material.isOf(Items.LEATHER) -> PrintMode.BOOK_COPY

            else -> PrintMode.NONE
        }
    }

    private fun consumeMaterial(amount: Int) {
        val material = currentMaterial()
        if (material.isEmpty) return
        material.decrement(amount)
        tableInventory.setStack(SLOT_MATERIAL, material)
    }

    private fun clampSelectedIndex() {
        val count = notebookSymbolCount()
        selectedSymbolIndex = if (count <= 0) 0 else selectedSymbolIndex.coerceIn(0, count - 1)
    }

    private fun currentSource(): ItemStack = tableInventory.getStack(SLOT_SOURCE)
    private fun currentMaterial(): ItemStack = tableInventory.getStack(SLOT_MATERIAL)
    private fun currentOutput(): ItemStack = tableInventory.getStack(SLOT_OUTPUT)

    private enum class PrintMode {
        NONE,
        NOTEBOOK_PAGE,
        BOOK_COPY
    }
}
