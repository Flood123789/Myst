package mystcraft.flood.gui

import mystcraft.flood.generation.AgeCurseManager
import mystcraft.flood.generation.AgeSubdimensionManager
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.generation.profile.TerrainTuningProfile
import mystcraft.flood.item.ModItems
import mystcraft.flood.item.SymbolPageItem
import mystcraft.flood.item.TerrainTuningBookData
import mystcraft.flood.network.ModMessages
import mystcraft.flood.registry.ModSymbols
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier

class EditingTableScreenHandler(
    syncId: Int,
    private val playerInventory: PlayerInventory,
    private val tableInventory: Inventory = SimpleInventory(17)
) : ScreenHandler(ModScreens.EDITING_TABLE_HANDLER, syncId) {

    companion object {
        const val SLOT_BOOK = 16

        val TIER1_ITEMS = mapOf(
            Items.DIAMOND to "terrainTurbulence",
            Items.GOLD_INGOT to "seaLevel",
            Items.IRON_INGOT to "caveDensity",
            Items.COPPER_INGOT to "biomeSize",
            Items.COAL to "verticalRange"
        )

        val TIER2_ITEMS = mapOf(
            Items.SPAWNER to "noMobs",
            Items.SPONGE to "noAquifers",
            Items.ENDER_PEARL to "caveWorld",
            Items.GOLDEN_SHOVEL to "superFlat"
        )

        const val CLEANSE_LAPIS_COST = 4
        const val CLEANSE_INK_COST = 1
        const val CLEANSE_AMETHYST_COST = 1
    }

    constructor(syncId: Int, playerInventory: PlayerInventory) : this(syncId, playerInventory, SimpleInventory(17))

    init {
        checkSize(tableInventory, 17)
        tableInventory.onOpen(playerInventory.player)

        for (i in 0 until 8) {
            addSlot(Slot(tableInventory, i, 8 + i * 18, 24))
        }
        for (i in 0 until 8) {
            addSlot(Slot(tableInventory, i + 8, 8 + i * 18, 42))
        }
        addSlot(object : Slot(tableInventory, SLOT_BOOK, 152, 33) {
            override fun canInsert(stack: ItemStack): Boolean = stack.isOf(ModItems.DESCRIPTIVE_BOOK)
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

    fun getPreviewTuning(): TerrainTuningProfile {
        val counts = mutableMapOf<String, Int>()
        for (slot in 0 until 16) {
            val stack = tableInventory.getStack(slot)
            if (stack.isEmpty) continue
            TIER1_ITEMS[stack.item]?.let { key ->
                counts[key] = (counts[key] ?: 0).plus(stack.count).coerceAtMost(16)
            }
            TIER2_ITEMS[stack.item]?.let { key ->
                counts[key] = 1
            }
        }
        return TerrainTuningProfile(
            terrainTurbulence = counts["terrainTurbulence"],
            seaLevel = counts["seaLevel"],
            caveDensity = counts["caveDensity"],
            biomeSize = counts["biomeSize"],
            verticalRange = counts["verticalRange"],
            superFlat = counts.containsKey("superFlat"),
            noMobs = counts.containsKey("noMobs"),
            caveWorld = counts.containsKey("caveWorld"),
            noAquifers = counts.containsKey("noAquifers")
        ).normalized()
    }

    fun getStoredBookTuning(): TerrainTuningProfile = TerrainTuningBookData.get(currentBook())

    fun hasEditableBook(): Boolean = currentBook().isOf(ModItems.DESCRIPTIVE_BOOK)

    fun hasLinkedBook(): Boolean = hasEditableBook() && currentBook().nbt?.contains("Age_ID") == true

    fun canApplyTuning(): Boolean = hasEditableBook() && currentBook().nbt?.contains("Age_ID") != true

    fun canCleanseCurse(): Boolean = hasLinkedBook() && hasCleansingMaterials()

    fun canApplyAction(): Boolean = canApplyTuning() || canCleanseCurse()

    fun actionLabel(): String = if (hasLinkedBook()) "Cleanse" else "Impress"

    fun countItem(item: Item): Int {
        var total = 0
        for (slot in 0 until 16) {
            val stack = tableInventory.getStack(slot)
            if (stack.item == item) {
                total += stack.count
            }
        }
        return total.coerceAtMost(16)
    }

    fun hasItem(item: Item): Boolean = countItem(item) > 0

    fun applyTuning(player: PlayerEntity) {
        val book = currentBook()
        if (!book.isOf(ModItems.DESCRIPTIVE_BOOK)) {
            player.sendMessage(Text.literal("Lay an unlinked descriptive book in the lower cradle first."), true)
            return
        }
        if (book.nbt?.contains("Age_ID") == true) {
            cleanseNextCurse(player)
            return
        }

        val tuning = getPreviewTuning()
        TerrainTuningBookData.set(book, tuning)
        consumeIngredientsFor(tuning)
        tableInventory.markDirty()
        sendContentUpdates()

        val summary = TerrainTuningBookData.summarize(tuning)
        if (summary.isEmpty()) {
            player.sendMessage(Text.literal("The script is returned to chance, with no deliberate tuning pressed into it."), true)
        } else {
            player.sendMessage(Text.literal("The table impresses a deliberate hand upon the descriptive book."), true)
        }
    }

    private fun cleanseNextCurse(player: PlayerEntity) {
        val serverPlayer = player as? net.minecraft.server.network.ServerPlayerEntity ?: return
        val server = serverPlayer.server
        val book = currentBook()
        val ageId = Identifier.tryParse(book.nbt?.getString("Age_ID") ?: "") ?: run {
            player.sendMessage(Text.literal("The linked Age cannot be read from this book.").formatted(Formatting.RED), false)
            return
        }
        val rootAgeId = AgeSubdimensionManager.rootIdOf(ageId)
        val profile = AgeProfileManager.getOrGenerateProfile(server, rootAgeId)
        val curse = AgeCurseManager.nextCurse(profile)

        if (curse == null) {
            player.sendMessage(Text.literal("The table finds no removable curse in this Age.").formatted(Formatting.YELLOW), false)
            return
        }

        if (!hasCleansingMaterials()) {
            player.sendMessage(
                Text.literal("Cleansing requires a Curse Cleansing page, an ink vial, $CLEANSE_LAPIS_COST lapis, and an amethyst shard.")
                    .formatted(Formatting.RED),
                false
            )
            return
        }

        val curseName = curse.label(profile)
        consumeCleansingMaterials()
        val result = AgeCurseManager.cleanseNext(profile)
        if (!result.changed) {
            player.sendMessage(Text.literal("The curse slips away before the table can catch it.").formatted(Formatting.YELLOW), false)
            return
        }

        AgeProfileManager.save(server, rootAgeId)
        ModMessages.syncAgeFamily(server, rootAgeId)
        tableInventory.markDirty()
        sendContentUpdates()

        val relief = if (result.instabilityReduced > 0) {
            " Instability fell by ${result.instabilityReduced}."
        } else {
            ""
        }
        player.sendMessage(
            Text.literal("The table lifts $curseName from ${rootAgeId.path}.$relief").formatted(Formatting.AQUA),
            false
        )
    }

    override fun quickMove(player: PlayerEntity, invSlot: Int): ItemStack {
        val slot = slots[invSlot]
        if (!slot.hasStack()) return ItemStack.EMPTY

        val original = slot.stack
        val copy = original.copy()
        val containerSlots = 17

        if (invSlot < containerSlots) {
            if (!insertItem(original, containerSlots, slots.size, true)) {
                return ItemStack.EMPTY
            }
        } else {
            if (original.isOf(ModItems.DESCRIPTIVE_BOOK)) {
                if (!insertItem(original, SLOT_BOOK, SLOT_BOOK + 1, false)) return ItemStack.EMPTY
            } else {
                if (!insertItem(original, 0, 16, false)) return ItemStack.EMPTY
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

    private fun currentBook(): ItemStack = tableInventory.getStack(SLOT_BOOK)

    private fun hasCleansingMaterials(): Boolean =
        hasCleansingPage() &&
            countItem(ModItems.INK_VIAL) >= CLEANSE_INK_COST &&
            countItem(Items.LAPIS_LAZULI) >= CLEANSE_LAPIS_COST &&
            countItem(Items.AMETHYST_SHARD) >= CLEANSE_AMETHYST_COST

    private fun hasCleansingPage(): Boolean {
        for (slot in 0 until 16) {
            if (isCleansingPage(tableInventory.getStack(slot))) return true
        }
        return false
    }

    private fun consumeIngredientsFor(tuning: TerrainTuningProfile) {
        val remaining = mutableMapOf<String, Int>()
        TIER1_ITEMS.values.forEach { key ->
            remaining[key] = when (key) {
                "terrainTurbulence" -> tuning.terrainTurbulence ?: 0
                "seaLevel" -> tuning.seaLevel ?: 0
                "caveDensity" -> tuning.caveDensity ?: 0
                "biomeSize" -> tuning.biomeSize ?: 0
                "verticalRange" -> tuning.verticalRange ?: 0
                else -> 0
            }
        }

        val tier2Needed = mutableSetOf<String>()
        if (tuning.noMobs) tier2Needed += "noMobs"
        if (tuning.noAquifers) tier2Needed += "noAquifers"
        if (tuning.caveWorld) tier2Needed += "caveWorld"
        if (tuning.superFlat) tier2Needed += "superFlat"

        for (slot in 0 until 16) {
            val stack = tableInventory.getStack(slot)
            if (stack.isEmpty) continue

            val tier1Key = TIER1_ITEMS[stack.item]
            if (tier1Key != null) {
                val budget = remaining[tier1Key] ?: 0
                if (budget <= 0) continue
                val toConsume = stack.count.coerceAtMost(budget)
                stack.decrement(toConsume)
                remaining[tier1Key] = budget - toConsume
                tableInventory.setStack(slot, stack)
                continue
            }

            val tier2Key = TIER2_ITEMS[stack.item]
            if (tier2Key != null && tier2Needed.remove(tier2Key)) {
                stack.decrement(1)
                tableInventory.setStack(slot, stack)
            }
        }
    }

    private fun consumeCleansingMaterials() {
        consumeFirstCleansingPage()
        consumeItem(ModItems.INK_VIAL, CLEANSE_INK_COST)
        consumeItem(Items.LAPIS_LAZULI, CLEANSE_LAPIS_COST)
        consumeItem(Items.AMETHYST_SHARD, CLEANSE_AMETHYST_COST)
    }

    private fun consumeFirstCleansingPage() {
        for (slot in 0 until 16) {
            val stack = tableInventory.getStack(slot)
            if (!isCleansingPage(stack)) continue
            stack.decrement(1)
            tableInventory.setStack(slot, stack)
            return
        }
    }

    private fun consumeItem(item: Item, amount: Int) {
        var remaining = amount
        for (slot in 0 until 16) {
            if (remaining <= 0) return
            val stack = tableInventory.getStack(slot)
            if (!stack.isOf(item)) continue
            val toConsume = stack.count.coerceAtMost(remaining)
            stack.decrement(toConsume)
            remaining -= toConsume
            tableInventory.setStack(slot, stack)
        }
    }

    private fun isCleansingPage(stack: ItemStack): Boolean =
        stack.item is SymbolPageItem &&
            stack.nbt?.getString("Symbol") == ModSymbols.CURSE_CLEANSING_SYMBOL.toString()
}
