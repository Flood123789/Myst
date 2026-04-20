package mystcraft.flood.item

import mystcraft.flood.gui.NotebookScreenHandler
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory
import net.minecraft.client.item.TooltipContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtString
import net.minecraft.network.PacketByteBuf
import net.minecraft.screen.ScreenHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World
import net.minecraft.text.MutableText
import net.minecraft.util.Formatting

class NotebookItem(settings: Settings) : Item(settings.maxCount(1)) {
    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        val stack = user.getStackInHand(hand)
        if (!world.isClient && user is ServerPlayerEntity) {
            user.openHandledScreen(NotebookFactory(hand))
        }
        return TypedActionResult.success(stack, world.isClient)
    }

    override fun appendTooltip(stack: ItemStack, world: World?, tooltip: MutableList<Text>, context: TooltipContext) {
        val symbols = getSymbols(stack)
        getNotebookName(stack)?.let {
            tooltip.add(Text.literal(it).formatted(Formatting.AQUA))
        }
        tooltip.add(Text.literal("An ordered notebook for drafting Ages.").formatted(Formatting.GRAY))
        tooltip.add(Text.literal("Right-click to open, arrange, and name it.").formatted(Formatting.DARK_GRAY))
        tooltip.add(Text.literal("Stored Symbols: ${symbols.size}").formatted(Formatting.GRAY))
        symbols.take(4).forEachIndexed { index, symbol ->
            tooltip.add(Text.literal("${index + 1}. ${formatSymbol(symbol)}"))
        }
        if (symbols.size > 4) {
            tooltip.add(Text.literal("...and ${symbols.size - 4} more"))
        }
    }

    private class NotebookFactory(private val hand: Hand) : ExtendedScreenHandlerFactory {
        override fun getDisplayName(): Text = Text.literal("Notebook")

        override fun writeScreenOpeningData(player: ServerPlayerEntity, buf: PacketByteBuf) {
            buf.writeEnumConstant(hand)
        }

        override fun createMenu(syncId: Int, playerInventory: PlayerInventory, player: PlayerEntity): ScreenHandler {
            return NotebookScreenHandler(syncId, playerInventory, hand)
        }
    }

    companion object {
        private const val SYMBOLS_KEY = "Symbols"
        private const val MAX_SYMBOLS = 56
        private const val NOTEBOOK_NAME_KEY = "NotebookName"

        fun isNotebook(stack: ItemStack): Boolean = stack.isOf(ModItems.NOTEBOOK)

        fun getSymbols(stack: ItemStack): List<String> {
            val nbt = stack.nbt ?: return emptyList()
            if (!nbt.contains(SYMBOLS_KEY)) return emptyList()
            val list = nbt.getList(SYMBOLS_KEY, 8)
            return List(list.size) { index -> list.getString(index) }
        }

        fun setSymbols(stack: ItemStack, symbols: List<String>) {
            if (!isNotebook(stack)) return
            val cleaned = symbols.filter { it.isNotBlank() }.take(MAX_SYMBOLS)
            if (cleaned.isEmpty()) {
                stack.nbt?.remove(SYMBOLS_KEY)
                if (stack.nbt?.isEmpty == true) {
                    stack.nbt = null
                }
                return
            }

            val list = NbtList()
            cleaned.forEach { list.add(NbtString.of(it)) }
            stack.orCreateNbt.put(SYMBOLS_KEY, list)
        }

        fun getNotebookName(stack: ItemStack): String? =
            stack.nbt?.getString(NOTEBOOK_NAME_KEY)?.takeIf { it.isNotBlank() }

        fun setNotebookName(stack: ItemStack, name: String) {
            if (!isNotebook(stack)) return
            val trimmed = name.trim().take(64)
            if (trimmed.isBlank()) {
                stack.nbt?.remove(NOTEBOOK_NAME_KEY)
                stack.removeCustomName()
                if (stack.nbt?.isEmpty == true) {
                    stack.nbt = null
                }
                return
            }

            stack.orCreateNbt.putString(NOTEBOOK_NAME_KEY, trimmed)
            stack.setCustomName(Text.literal(trimmed))
        }

        fun createPageStack(symbolId: String): ItemStack {
            if (symbolId.startsWith("color_custom:#")) {
                val stack = SymbolPageItem.createStack(Identifier("mystcraft-reforged", "color_custom"))
                stack.setCustomName(Text.literal(symbolId.substringAfter(':')))
                return stack
            }
            return SymbolPageItem.createStack(Identifier(symbolId))
        }

        fun extractSymbol(pageStack: ItemStack): String? {
            var symbolId = pageStack.nbt?.getString("Symbol")?.takeIf { it.isNotBlank() } ?: return null
            if (symbolId == "mystcraft-reforged:color_custom" || symbolId == "color_custom") {
                val customName = pageStack.name.string
                if (customName.startsWith("#")) {
                    symbolId = "color_custom:$customName"
                }
            }
            return symbolId
        }

        fun formatSymbol(symbolId: String): String =
            symbolId.substringAfter(':').replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}
