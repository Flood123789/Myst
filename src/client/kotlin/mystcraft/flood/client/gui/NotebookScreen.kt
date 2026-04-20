package mystcraft.flood.client.gui

import mystcraft.flood.network.ModMessages
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import mystcraft.flood.gui.NotebookScreenHandler
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.text.Text

class NotebookScreen(
    handler: NotebookScreenHandler,
    inventory: PlayerInventory,
    title: Text
) : HandledScreen<NotebookScreenHandler>(handler, inventory, title) {
    private var nameField: TextFieldWidget? = null
    private var lastSentNotebookName = ""

    override fun init() {
        backgroundWidth = 176
        backgroundHeight = 269
        super.init()
        titleX = 8
        titleY = 6
        playerInventoryTitleY = 173

        val field = TextFieldWidget(textRenderer, x + 47, y + 4, 120, 12, Text.literal("Notebook Name"))
        field.setMaxLength(64)
        field.setDrawsBackground(false)
        field.setEditableColor(0xFFF8E8C6.toInt())
        field.setUneditableColor(0xFF7A6347.toInt())
        field.text = handler.getNotebookName()
        field.setChangedListener(::onNotebookNameChanged)
        lastSentNotebookName = field.text
        nameField = addDrawableChild(field)
    }

    override fun drawBackground(context: DrawContext, delta: Float, mouseX: Int, mouseY: Int) {
        val left = x
        val top = y

        context.fill(left, top, left + backgroundWidth, top + backgroundHeight, 0xFFB79E76.toInt())
        context.drawBorder(left, top, backgroundWidth, backgroundHeight, 0xFF5E4732.toInt())
        context.fill(left + 45, top + 3, left + 169, top + 17, 0xAA3A2D20.toInt())
        context.drawBorder(left + 45, top + 3, 124, 14, 0xFF8E7758.toInt())

        context.fill(left + 5, top + 15, left + backgroundWidth - 5, top + 166, 0xFFE7D4AE.toInt())
        context.drawBorder(left + 5, top + 15, backgroundWidth - 10, 151, 0xFF8B7355.toInt())

        context.fill(left + 5, top + 180, left + backgroundWidth - 5, top + backgroundHeight - 9, 0xFFD3BE96.toInt())
        context.drawBorder(left + 5, top + 180, backgroundWidth - 10, 80, 0xFF7A6347.toInt())

        repeat(8) { row ->
            repeat(7) { col ->
                val slotX = left + 24 + col * 18
                val slotY = top + 17 + row * 18
                context.fill(slotX, slotY, slotX + 18, slotY + 18, 0xAA3A2D20.toInt())
                context.drawBorder(slotX, slotY, 18, 18, 0xFF8E7758.toInt())
            }
        }

        repeat(3) { row ->
            repeat(9) { col ->
                val slotX = left + 7 + col * 18
                val slotY = top + 182 + row * 18
                context.fill(slotX, slotY, slotX + 18, slotY + 18, 0xAA564A3A.toInt())
                context.drawBorder(slotX, slotY, 18, 18, 0xFF8E7758.toInt())
            }
        }

        repeat(9) { col ->
            val slotX = left + 7 + col * 18
            val slotY = top + 240
            context.fill(slotX, slotY, slotX + 18, slotY + 18, 0xAA564A3A.toInt())
            context.drawBorder(slotX, slotY, 18, 18, 0xFF8E7758.toInt())
        }
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        super.render(context, mouseX, mouseY, delta)
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

    private fun onNotebookNameChanged(value: String) {
        if (value == lastSentNotebookName) return
        lastSentNotebookName = value

        val buf = PacketByteBufs.create()
        buf.writeVarInt(handler.syncId)
        buf.writeString(value, 64)
        ClientPlayNetworking.send(ModMessages.NOTEBOOK_SET_NAME, buf)
    }
}
