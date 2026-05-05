package mystcraft.flood.client.gui

import mystcraft.flood.gui.PrintingTableScreenHandler
import mystcraft.flood.network.ModMessages
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.text.Text

class PrintingTableScreen(
    handler: PrintingTableScreenHandler,
    inventory: PlayerInventory,
    title: Text
) : HandledScreen<PrintingTableScreenHandler>(handler, inventory, title) {

    private var previousButton: ButtonWidget? = null
    private var nextButton: ButtonWidget? = null

    override fun init() {
        backgroundWidth = 176
        backgroundHeight = 222
        super.init()

        titleX = 8
        titleY = 6
        playerInventoryTitleY = 125

        previousButton = addDrawableChild(
            ButtonWidget.builder(Text.literal("<")) {
                cycleSelection(-1)
            }.dimensions(x + 18, y + 74, 20, 20).build()
        )

        nextButton = addDrawableChild(
            ButtonWidget.builder(Text.literal(">")) {
                cycleSelection(1)
            }.dimensions(x + 138, y + 74, 20, 20).build()
        )
    }

    override fun handledScreenTick() {
        super.handledScreenTick()
        previousButton?.active = handler.canCycle(-1)
        nextButton?.active = handler.canCycle(1)
    }

    override fun drawBackground(context: DrawContext, delta: Float, mouseX: Int, mouseY: Int) {
        val left = x
        val top = y

        context.fill(left, top, left + backgroundWidth, top + backgroundHeight, 0xFF4B3426.toInt())
        context.drawBorder(left, top, backgroundWidth, backgroundHeight, 0xFF27180E.toInt())

        context.fill(left + 6, top + 18, left + backgroundWidth - 6, top + 66, 0xFFDCC8A5.toInt())
        context.drawBorder(left + 6, top + 18, backgroundWidth - 12, 48, 0xFF8E7758.toInt())

        listOf(23, 79, 135).forEach { slotX ->
            context.fill(left + slotX, top + 34, left + slotX + 18, top + 52, 0xAA3A2D20.toInt())
            context.drawBorder(left + slotX, top + 34, 18, 18, 0xFF8E7758.toInt())
        }

        context.fill(left + 6, top + 92, left + backgroundWidth - 6, top + 118, 0xFFE7D4AE.toInt())
        context.drawBorder(left + 6, top + 92, backgroundWidth - 12, 26, 0xFF8B7355.toInt())

        context.fill(left + 6, top + 124, left + backgroundWidth - 6, top + backgroundHeight - 7, 0xFFD3BE96.toInt())
        context.drawBorder(left + 6, top + 124, backgroundWidth - 12, backgroundHeight - 131, 0xFF7A6347.toInt())
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        super.render(context, mouseX, mouseY, delta)

        val left = x
        val top = y

        context.drawText(textRenderer, Text.literal("Printing Table"), left + 8, top + 6, 0xFFF8E8C6.toInt(), false)
        context.drawText(textRenderer, Text.literal("Source"), left + 20, top + 22, 0xFF2A1D12.toInt(), false)
        context.drawText(textRenderer, Text.literal("Medium"), left + 74, top + 22, 0xFF2A1D12.toInt(), false)
        context.drawText(textRenderer, Text.literal("Result"), left + 131, top + 22, 0xFF2A1D12.toInt(), false)

        val selectedLeaf = handler.selectedSymbolLabel()
        val previewLine = when {
            selectedLeaf != null -> "Selected leaf: $selectedLeaf"
            else -> "No notebook leaf is selected."
        }
        context.drawText(textRenderer, Text.literal(previewLine), left + 44, top + 80, 0xFF2A1D12.toInt(), false)

        var lineY = top + 96
        handler.statusLines().forEach { line ->
            context.drawText(textRenderer, Text.literal(line), left + 10, lineY, 0xFF3D2E1E.toInt(), false)
            lineY += 10
        }

        drawMouseoverTooltip(context, mouseX, mouseY)
    }

    private fun cycleSelection(delta: Int) {
        val buf = PacketByteBufs.create()
        buf.writeVarInt(handler.syncId)
        buf.writeVarInt(delta)
        ClientPlayNetworking.send(ModMessages.PRINTING_TABLE_CYCLE_SELECTION, buf)
    }
}
