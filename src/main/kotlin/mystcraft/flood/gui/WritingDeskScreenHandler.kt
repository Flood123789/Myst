package mystcraft.flood.gui

import mystcraft.flood.generation.AgeSubdimensionManager
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.item.DescriptiveBookItem
import mystcraft.flood.item.DisplayedBookHelper
import mystcraft.flood.item.ModItems
import mystcraft.flood.item.NotebookItem
import mystcraft.flood.network.ModMessages
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtString
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot
import net.minecraft.text.Text
import net.minecraft.util.Identifier

class WritingDeskScreenHandler(
    syncId: Int,
    private val playerInventory: PlayerInventory,
    private val deskInventory: Inventory = SimpleInventory(2)
) : ScreenHandler(ModScreens.WRITING_DESK_HANDLER, syncId) {

    companion object {
        const val SLOT_BOOK = 0
        const val SLOT_NOTEBOOK = 1
    }

    constructor(syncId: Int, playerInventory: PlayerInventory) : this(syncId, playerInventory, SimpleInventory(2))

    init {
        checkSize(deskInventory, 2)
        deskInventory.onOpen(playerInventory.player)

        addSlot(object : Slot(deskInventory, SLOT_BOOK, 30, 36) {
            override fun canInsert(stack: ItemStack): Boolean = stack.item is DescriptiveBookItem || stack.isOf(ModItems.LINKING_BOOK)
            override fun getMaxItemCount(): Int = 1
        })

        addSlot(object : Slot(deskInventory, SLOT_NOTEBOOK, 128, 36) {
            override fun canInsert(stack: ItemStack): Boolean = stack.isOf(ModItems.NOTEBOOK)
            override fun getMaxItemCount(): Int = 1
        })

        for (row in 0 until 3) {
            for (col in 0 until 9) {
                addSlot(Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 139 + row * 18))
            }
        }

        for (col in 0 until 9) {
            addSlot(Slot(playerInventory, col, 8 + col * 18, 197))
        }
    }

    fun getBookName(): String = DisplayedBookHelper.getAgeBookName(currentBook())

    fun setDraftAgeName(rawName: String) {
        val book = currentBook()
        if (book.isEmpty) return

        val trimmed = rawName.trim().take(64)
        DisplayedBookHelper.applyAgeBookName(book, trimmed)

        val ageId = book.nbt?.getString("Age_ID")?.takeIf { it.isNotBlank() }?.let(Identifier::tryParse)
        val server = playerInventory.player.server
        if (ageId != null && server != null) {
            val rootAgeId = AgeSubdimensionManager.rootIdOf(ageId)
            val profile = AgeProfileManager.getOrGenerateProfile(server, rootAgeId)
            profile.ageState.displayName = trimmed.ifBlank { null }
            ModMessages.syncAgeFamily(server, rootAgeId)
        }

        deskInventory.markDirty()
        sendContentUpdates()
    }

    fun canTranscribe(): Boolean =
        currentBook().isOf(ModItems.DESCRIPTIVE_BOOK) &&
            currentBook().nbt?.contains("Age_ID") != true &&
            NotebookItem.getSymbols(currentNotebook()).isNotEmpty()

    fun transcribeNotebook(player: PlayerEntity) {
        val book = currentBook()
        if (!book.isOf(ModItems.DESCRIPTIVE_BOOK)) {
            player.sendMessage(Text.literal("A descriptive book must rest in the left cradle."), true)
            return
        }
        if (book.nbt?.contains("Age_ID") == true) {
            player.sendMessage(Text.literal("Linked Ages may be renamed here, but their written leaves are already fixed."), true)
            return
        }

        val notebook = currentNotebook()
        if (!notebook.isOf(ModItems.NOTEBOOK)) {
            player.sendMessage(Text.literal("Set a notebook in the right cradle to transcribe its leaves."), true)
            return
        }

        val symbols = NotebookItem.getSymbols(notebook)
        if (symbols.isEmpty()) {
            player.sendMessage(Text.literal("That notebook carries no ordered leaves yet."), true)
            return
        }

        val pageList = NbtList()
        symbols.forEach { pageList.add(NbtString.of(it)) }
        book.orCreateNbt.put("Pages", pageList)

        if (DisplayedBookHelper.getAgeBookName(book).isBlank()) {
            NotebookItem.getNotebookName(notebook)?.let { DisplayedBookHelper.applyAgeBookName(book, it) }
        }

        deskInventory.markDirty()
        sendContentUpdates()
        player.sendMessage(Text.literal("The notebook's ordered leaves are copied into the descriptive book."), true)
    }

    fun notebookSymbolCount(): Int = NotebookItem.getSymbols(currentNotebook()).size

    fun notebookPreview(limit: Int = 6): List<String> =
        NotebookItem.getSymbols(currentNotebook()).take(limit).map(NotebookItem::formatSymbol)

    fun isLinkedBook(): Boolean = currentBook().nbt?.contains("Age_ID") == true

    override fun quickMove(player: PlayerEntity, invSlot: Int): ItemStack {
        val slot = slots[invSlot]
        if (!slot.hasStack()) return ItemStack.EMPTY

        val original = slot.stack
        val copy = original.copy()
        val containerSlots = 2

        if (invSlot < containerSlots) {
            if (!insertItem(original, containerSlots, slots.size, true)) {
                return ItemStack.EMPTY
            }
        } else {
            when {
                original.item is DescriptiveBookItem -> {
                    if (!insertItem(original, SLOT_BOOK, SLOT_BOOK + 1, false)) return ItemStack.EMPTY
                }
                original.isOf(ModItems.NOTEBOOK) -> {
                    if (!insertItem(original, SLOT_NOTEBOOK, SLOT_NOTEBOOK + 1, false)) return ItemStack.EMPTY
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
        deskInventory.onClose(player)
    }

    override fun canUse(player: PlayerEntity): Boolean = deskInventory.canPlayerUse(player)

    private fun currentBook(): ItemStack = deskInventory.getStack(SLOT_BOOK)
    private fun currentNotebook(): ItemStack = deskInventory.getStack(SLOT_NOTEBOOK)
}
