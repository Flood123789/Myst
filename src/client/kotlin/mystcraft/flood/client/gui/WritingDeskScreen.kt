package mystcraft.flood.client.gui

import mystcraft.flood.gui.WritingDeskScreenHandler
import mystcraft.flood.network.ModMessages
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.text.Text

class WritingDeskScreen(
    handler: WritingDeskScreenHandler,
    inventory: PlayerInventory,
    title: Text
) : HandledScreen<WritingDeskScreenHandler>(handler, inventory, title) {

    private var nameField: TextFieldWidget? = null
    private var transcribeButton: ButtonWidget? = null
    private var lastSentName = ""

    override fun init() {
        backgroundWidth = 176
        backgroundHeight = 222
        super.init()

        titleX = 8
        titleY = 6
        playerInventoryTitleY = 125

        val field = TextFieldWidget(textRenderer, x + 24, y + 9, 128, 12, Text.literal("Age Name"))
        field.setMaxLength(64)
        field.setDrawsBackground(false)
        field.setEditableColor(0xFFEADCC0.toInt())
        field.setUneditableColor(0xFF8D816E.toInt())
        field.text = handler.getBookName()
        field.setChangedListener(::onNameChanged)
        lastSentName = field.text
        nameField = addDrawableChild(field)

        transcribeButton = addDrawableChild(
            ButtonWidget.builder(Text.literal("Transcribe Leaves")) {
                val buf = PacketByteBufs.create()
                buf.writeVarInt(handler.syncId)
                ClientPlayNetworking.send(ModMessages.WRITING_DESK_TRANSCRIBE, buf)
            }.dimensions(x + 42, y + 74, 92, 20).build()
        )
    }

    override fun handledScreenTick() {
        super.handledScreenTick()
        val field = nameField
        if (field != null && !field.isFocused) {
            val desired = handler.getBookName()
            if (field.text != desired) {
                field.text = desired
                lastSentName = desired
            }
        }
        field?.setEditable(handler.slots[WritingDeskScreenHandler.SLOT_BOOK].hasStack())
        transcribeButton?.active = handler.canTranscribe()
    }

    override fun drawBackground(context: DrawContext, delta: Float, mouseX: Int, mouseY: Int) {
        val left = x
        val top = y

        context.fill(left, top, left + backgroundWidth, top + backgroundHeight, 0xFF513A25.toInt())
        context.drawBorder(left, top, backgroundWidth, backgroundHeight, 0xFF2A1D12.toInt())

        context.fill(left + 6, top + 18, left + backgroundWidth - 6, top + 118, 0xFFDBC49A.toInt())
        context.drawBorder(left + 6, top + 18, backgroundWidth - 12, 100, 0xFF7E6646.toInt())

        context.fill(left + 23, top + 8, left + 153, top + 22, 0x8832261A.toInt())
        context.drawBorder(left + 23, top + 8, 130, 14, 0xFF8E7758.toInt())

        context.fill(left + 21, top + 35, left + 39, top + 53, 0xAA3A2D20.toInt())
        context.drawBorder(left + 21, top + 35, 18, 18, 0xFF8E7758.toInt())
        context.fill(left + 119, top + 35, left + 137, top + 53, 0xAA3A2D20.toInt())
        context.drawBorder(left + 119, top + 35, 18, 18, 0xFF8E7758.toInt())

        context.fill(left + 6, top + 124, left + backgroundWidth - 6, top + backgroundHeight - 7, 0xFFD3BE96.toInt())
        context.drawBorder(left + 6, top + 124, backgroundWidth - 12, backgroundHeight - 131, 0xFF7A6347.toInt())
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        super.render(context, mouseX, mouseY, delta)

        val left = x
        val top = y
        val bookStatus = when {
            !handler.slots[WritingDeskScreenHandler.SLOT_BOOK].hasStack() -> "Set a descriptive book in the left cradle."
            handler.isLinkedBook() -> "This book is linked. You may rename it here, but its written leaves are fixed."
            else -> "This book is unlinked. Name it here, then copy ordered leaves from the notebook."
        }
        val notebookStatus = if (handler.slots[WritingDeskScreenHandler.SLOT_NOTEBOOK].hasStack()) {
            "Notebook leaves ready: ${handler.notebookSymbolCount()}."
        } else {
            "Set a notebook in the right cradle."
        }

        context.drawText(textRenderer, Text.literal("Writing Desk"), left + 8, top + 6, 0xFFF8E8C6.toInt(), false)
        context.drawText(textRenderer, Text.literal("Descriptive Book"), left + 44, top + 40, 0xFF2A1D12.toInt(), false)
        context.drawText(textRenderer, Text.literal("Notebook"), left + 119, top + 58, 0xFF2A1D12.toInt(), false)
        context.drawText(textRenderer, Text.literal(bookStatus), left + 14, top + 96, 0xFF3D2E1E.toInt(), false)
        context.drawText(textRenderer, Text.literal(notebookStatus), left + 14, top + 108, 0xFF3D2E1E.toInt(), false)

        var lineY = top + 44
        handler.notebookPreview().forEach { preview ->
            context.drawText(textRenderer, Text.literal(preview), left + 140, lineY, 0xFF3D2E1E.toInt(), false)
            lineY += 10
        }

        drawMouseoverTooltip(context, mouseX, mouseY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (nameField?.mouseClicked(mouseX, mouseY, button) == true) {
            setFocused(nameField)
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (nameField?.keyPressed(keyCode, scanCode, modifiers) == true) {
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        if (nameField?.charTyped(chr, modifiers) == true) {
            return true
        }
        return super.charTyped(chr, modifiers)
    }

    private fun onNameChanged(value: String) {
        if (value == lastSentName) return
        lastSentName = value

        val buf = PacketByteBufs.create()
        buf.writeVarInt(handler.syncId)
        buf.writeString(value, 64)
        ClientPlayNetworking.send(ModMessages.WRITING_DESK_SET_AGE_NAME, buf)
    }
}
