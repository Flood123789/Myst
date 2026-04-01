package mystcraft.flood.gui

import mystcraft.flood.item.ModItems
import mystcraft.flood.item.SymbolPageItem
import mystcraft.flood.mixin.SlotAccessor
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtString
import net.minecraft.registry.Registries
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot

class BookBinderScreenHandler(
    syncId: Int,
    playerInventory: PlayerInventory,
    private val input: Inventory = SimpleInventory(57), // Slot 0 = Leather, Slots 1-56 = Pages
    private val output: Inventory = SimpleInventory(1)  // Slot 0 = Crafted Book
) : ScreenHandler(ModScreens.BOOK_BINDER_HANDLER, syncId) {

    private val pageSlots = mutableListOf<Slot>()
    private var isUpdating = false // Pause button to prevent infinite recursive loops

    // Custom Output Slot handles consuming resources when we grab the crafted book
    inner class BookOutputSlot(
        inventory: Inventory,
        index: Int,
        x: Int,
        y: Int
    ) : Slot(inventory, index, x, y) {
        
        override fun canInsert(stack: ItemStack): Boolean = false 

        // Only allow grabbing the book if there is actually Leather in the input!
        override fun canTakeItems(playerEntity: PlayerEntity): Boolean {
            return input.getStack(0).isOf(Items.LEATHER)
        }

        override fun onTakeItem(player: PlayerEntity, stack: ItemStack) {
            isUpdating = true 
            
            // 1. Consume 1 Leather
            input.removeStack(0, 1)
            
            // 2. Consume 1 of EVERY page currently in the grid
            for (i in 1 until 57) {
                if (!input.getStack(i).isEmpty) {
                    input.removeStack(i, 1)
                }
            }
            
            // 3. Let the game safely transfer the item to the cursor
            super.onTakeItem(player, stack)
            
            // 4. Update the table for any leftover materials
            isUpdating = false 
            updateBookOutput() 
        }
    }

    init {
        checkSize(input, 57)
        input.onOpen(playerInventory.player)
        output.onOpen(playerInventory.player)

        // === Slot 0: LEATHER INPUT ===
        this.addSlot(object : Slot(input, 0, 8, 27) {
            override fun canInsert(stack: ItemStack): Boolean = stack.isOf(Items.LEATHER)
            // CRITICAL FIX: Trigger logic when the slot changes!
            override fun markDirty() {
                super.markDirty()
                updateBookOutput()
            }
        })

        // === Slot 1: BOOK OUTPUT ===
        this.addSlot(BookOutputSlot(output, 0, 152, 27))

        // === Slots 2-57: HIDDEN PAGES ===
        for (i in 1 until 57) {
            val slot = object : Slot(input, i, -2000, -2000) {
                override fun canInsert(stack: ItemStack): Boolean = stack.item is SymbolPageItem
                override fun getMaxItemCount(): Int = 1
                // CRITICAL FIX: Trigger logic when any page changes!
                override fun markDirty() {
                    super.markDirty()
                    updateBookOutput()
                }
            }
            pageSlots.add(slot)
            this.addSlot(slot)
        }

        // === PLAYER INVENTORY ===
        val yOffset = 15
        for (m in 0 until 3) {
            for (l in 0 until 9) {
                this.addSlot(Slot(playerInventory, l + m * 9 + 9, 8 + l * 18, 84 + m * 18 + yOffset))
            }
        }
        for (m in 0 until 9) {
            this.addSlot(Slot(playerInventory, m, 8 + m * 18, 142 + yOffset))
        }

        scrollPages(0)
        updateBookOutput() // Initial check in case items are already in the block
    }

    // === THE CRAFTING LOGIC ===
    fun updateBookOutput() {
        if (isUpdating) return
        isUpdating = true

        val leather = input.getStack(0)
        
        if (leather.isOf(Items.LEATHER)) {
            val bookStack = ItemStack(ModItems.DESCRIPTIVE_BOOK) 
            val bookNbt = bookStack.orCreateNbt
            val pageList = NbtList()
            var symbolCount = 0

            // Scan all 56 slots for pages
            for (i in 1 until 57) {
                val pageStack = input.getStack(i)
                if (!pageStack.isEmpty && pageStack.item is SymbolPageItem) {
                    
                    var symbolId = "unknown"
                    if (pageStack.hasNbt() && pageStack.nbt!!.contains("symbol")) {
                        symbolId = pageStack.nbt!!.getString("symbol")
                    } else {
                        symbolId = Registries.ITEM.getId(pageStack.item).toString()
                    }
                    
                    pageList.add(NbtString.of(symbolId))
                    symbolCount++
                }
            }

            // Write the symbols to the book if there are any
            if (symbolCount > 0) {
                bookNbt.put("Pages", pageList)
            }
            
            output.setStack(0, bookStack)
        } else {
            output.setStack(0, ItemStack.EMPTY)
        }

        this.sendContentUpdates() // Force server to sync to client
        isUpdating = false
    }

    fun scrollPages(rowOffset: Int) {
        val columns = 7
        val visibleRows = 3

        for (i in 0 until pageSlots.size) {
            val slot = pageSlots[i]
            val currentRow = i / columns
            val accessor = slot as SlotAccessor

            if (currentRow >= rowOffset && currentRow < rowOffset + visibleRows) {
                val visibleRow = currentRow - rowOffset
                val col = i % columns
                accessor.setSlotX(26 + (col * 18))
                accessor.setSlotY(38 + (visibleRow * 18))
            } else {
                accessor.setSlotX(-2000)
                accessor.setSlotY(-2000)
            }
        }
    }

    override fun quickMove(player: PlayerEntity, invSlot: Int): ItemStack {
        var newStack = ItemStack.EMPTY
        val slot = slots[invSlot]
        
        if (slot.hasStack()) {
            val originalStack = slot.stack
            newStack = originalStack.copy()

            if (invSlot < 58) {
                // Moving FROM Binder TO Player Inventory
                if (!this.insertItem(originalStack, 58, this.slots.size, true)) return ItemStack.EMPTY
            } else {
                // Moving FROM Player Inventory TO Binder
                if (originalStack.isOf(Items.LEATHER)) {
                    if (!this.insertItem(originalStack, 0, 1, false)) return ItemStack.EMPTY
                }
                else if (originalStack.item is SymbolPageItem) {
                    for (i in 2 until 58) {
                        val pageSlot = slots[i]
                        if (!pageSlot.hasStack()) {
                            pageSlot.stack = originalStack.split(1)
                            pageSlot.markDirty() // This triggers updateBookOutput!
                            if (originalStack.isEmpty) break
                        }
                    }
                }
            }

            if (originalStack.count == newStack.count) return ItemStack.EMPTY

            if (originalStack.isEmpty) slot.stack = ItemStack.EMPTY
            else slot.markDirty()
            
            slot.onTakeItem(player, originalStack)
        }
        return newStack
    }

    override fun canUse(player: PlayerEntity): Boolean = this.input.canPlayerUse(player)

    override fun onClosed(player: PlayerEntity) {
        super.onClosed(player)
        // Erase the crafted book display so it doesn't get dropped for free
        output.setStack(0, ItemStack.EMPTY) 
    }
}