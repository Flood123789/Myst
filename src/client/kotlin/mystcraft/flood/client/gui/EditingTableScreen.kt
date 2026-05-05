package mystcraft.flood.client.gui

import mystcraft.flood.gui.EditingTableScreenHandler
import mystcraft.flood.item.TerrainTuningBookData
import mystcraft.flood.network.ModMessages
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.text.Text

class EditingTableScreen(
    handler: EditingTableScreenHandler,
    inventory: PlayerInventory,
    title: Text
) : HandledScreen<EditingTableScreenHandler>(handler, inventory, title) {

    private var applyButton: ButtonWidget? = null

    override fun init() {
        backgroundWidth = 176
        backgroundHeight = 222
        super.init()

        titleX = 8
        titleY = 6
        playerInventoryTitleY = 125

        applyButton = addDrawableChild(
            ButtonWidget.builder(Text.literal("Impress")) {
                val buf = PacketByteBufs.create()
                buf.writeVarInt(handler.syncId)
                ClientPlayNetworking.send(ModMessages.EDITING_TABLE_APPLY, buf)
            }.dimensions(x + 102, y + 74, 66, 20).build()
        )
    }

    override fun handledScreenTick() {
        super.handledScreenTick()
        applyButton?.active = handler.canApplyAction()
        applyButton?.setMessage(Text.literal(handler.actionLabel()))
    }

    override fun drawBackground(context: DrawContext, delta: Float, mouseX: Int, mouseY: Int) {
        val left = x
        val top = y

        context.fill(left, top, left + backgroundWidth, top + backgroundHeight, 0xFF4A3324.toInt())
        context.drawBorder(left, top, backgroundWidth, backgroundHeight, 0xFF27180E.toInt())

        context.fill(left + 6, top + 18, left + 151, top + 66, 0xFFDCC8A5.toInt())
        context.drawBorder(left + 6, top + 18, 145, 48, 0xFF8E7758.toInt())
        context.fill(left + 151, top + 18, left + 169, top + 36, 0xAA3A2D20.toInt())
        context.drawBorder(left + 151, top + 18, 18, 18, 0xFF8E7758.toInt())

        repeat(8) { col ->
            val slotX = left + 7 + col * 18
            context.fill(slotX, top + 23, slotX + 18, top + 41, 0xAA3A2D20.toInt())
            context.drawBorder(slotX, top + 23, 18, 18, 0xFF8E7758.toInt())
            context.fill(slotX, top + 41, slotX + 18, top + 59, 0xAA3A2D20.toInt())
            context.drawBorder(slotX, top + 41, 18, 18, 0xFF8E7758.toInt())
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
        val previewLines = TerrainTuningBookData.summarize(handler.getPreviewTuning())
        val storedLines = TerrainTuningBookData.summarize(handler.getStoredBookTuning())

        context.drawText(textRenderer, Text.literal("Editing Table"), left + 8, top + 6, 0xFFF8E8C6.toInt(), false)
        context.drawText(textRenderer, Text.literal("Loose Materials"), left + 8, top + 78, 0xFF2A1D12.toInt(), false)
        context.drawText(textRenderer, Text.literal(if (handler.hasLinkedBook()) "Linked Age Cleansing" else "Book's Bound Tuning"), left + 8, top + 90, 0xFF2A1D12.toInt(), false)

        val bookStatus = when {
            !handler.hasEditableBook() -> "Set an unlinked descriptive book in the right cradle."
            handler.canApplyTuning() -> "These materials will be pressed into the waiting script."
            handler.hasLinkedBook() && handler.canCleanseCurse() -> "The table will lift the next named curse from the linked Age."
            handler.hasLinkedBook() -> "Use a cleansing page, ink, lapis, and amethyst to lift one curse."
            else -> "This book is already linked, and its world is past the point of first shaping."
        }
        context.drawText(textRenderer, Text.literal(bookStatus), left + 8, top + 108, 0xFF3D2E1E.toInt(), false)

        var lineY = top + 78
        previewLines.take(2).forEach { line ->
            context.drawText(textRenderer, Text.literal(line), left + 86, lineY, 0xFF3D2E1E.toInt(), false)
            lineY += 10
        }

        lineY = top + 90
        if (handler.hasLinkedBook()) {
            context.drawText(textRenderer, Text.literal("1 cleansing page + ink"), left + 86, lineY, 0xFF3D2E1E.toInt(), false)
            context.drawText(textRenderer, Text.literal("4 lapis + amethyst"), left + 86, lineY + 10, 0xFF3D2E1E.toInt(), false)
        } else if (storedLines.isEmpty()) {
            context.drawText(textRenderer, Text.literal("No previous tuning is bound into this book."), left + 86, lineY, 0xFF3D2E1E.toInt(), false)
        } else {
            storedLines.take(2).forEach { line ->
                context.drawText(textRenderer, Text.literal(line), left + 86, lineY, 0xFF3D2E1E.toInt(), false)
                lineY += 10
            }
        }

        drawMouseoverTooltip(context, mouseX, mouseY)
    }
}
