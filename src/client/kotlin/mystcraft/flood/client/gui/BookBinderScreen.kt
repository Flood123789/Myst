package mystcraft.flood.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.gui.BookBinderScreenHandler
import mystcraft.flood.network.ModMessages
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import kotlin.math.roundToInt

class BookBinderScreen(
    handler: BookBinderScreenHandler,
    inventory: PlayerInventory,
    title: Text
) : HandledScreen<BookBinderScreenHandler>(handler, inventory, title) {

    companion object {
        private val TEXTURE = Identifier(MystcraftReforged.MOD_ID, "textures/gui/pagebinder.png")
    }

    private var scrollRow = 0
    private val maxScrollRow = 5
    private var isDragging = false
    private var ageNameField: TextFieldWidget? = null
    private var lastSentAgeName = ""

    override fun init() {
        backgroundWidth = 176
        backgroundHeight = 180
        super.init()
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2
        titleY = 6
        playerInventoryTitleY = backgroundHeight - 94

        val ageField = TextFieldWidget(textRenderer, x + 26, y + 8, 126, 12, Text.literal("Age Name"))
        ageField.setMaxLength(64)
        ageField.setDrawsBackground(false)
        ageField.setEditableColor(0xFFFFFF)
        ageField.setUneditableColor(0x777777)
        ageField.setChangedListener(::onAgeNameChanged)
        ageNameField = addDrawableChild(ageField)
    }

    override fun drawBackground(context: DrawContext, delta: Float, mouseX: Int, mouseY: Int) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
        val x = (width - backgroundWidth) / 2
        val y = (height - backgroundHeight) / 2
        context.drawTexture(TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight)

        // Header age-name box (blue outline + black interior)
        context.fill(x + 26, y + 8, x + 152, y + 20, 0xFF000000.toInt())
        context.drawBorder(x + 25, y + 7, 128, 14, 0xFF245CFF.toInt())

        // Main black panel behind the page slots
        context.fill(x + 25, y + 37, x + 153, y + 93, 0xFF0C0C0C.toInt())
        context.drawBorder(x + 25, y + 37, 128, 56, 0xFF1F1F1F.toInt())

        // Draw the grey slider box
        val trackX = x + 154
        val trackY = y + 38
        val trackHeight = 54
        val thumbHeight = 15
        val scrollPercent = scrollRow.toFloat() / maxScrollRow.toFloat()
        val thumbY = trackY + (scrollPercent * (trackHeight - thumbHeight)).toInt()
        
        context.fill(trackX, thumbY, trackX + 10, thumbY + thumbHeight, 0xFF888888.toInt())
        context.fill(trackX + 1, thumbY + 1, trackX + 9, thumbY + thumbHeight - 1, 0xFFCCCCCC.toInt())
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        super.render(context, mouseX, mouseY, delta)
        drawMouseoverTooltip(context, mouseX, mouseY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (amount > 0 && scrollRow > 0) scrollRow--
        else if (amount < 0 && scrollRow < maxScrollRow) scrollRow++
        
        handler.scrollPages(scrollRow)
        return true
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (ageNameField?.mouseClicked(mouseX, mouseY, button) == true) {
            setFocused(ageNameField)
            return true
        }

        val trackX = x + 154
        val trackY = y + 38
        if (mouseX >= trackX && mouseX <= trackX + 10 && mouseY >= trackY && mouseY <= trackY + 54) {
            isDragging = true
            updateScroll(mouseY)
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (isDragging) {
            updateScroll(mouseY)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (isDragging) {
            isDragging = false
            return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val field = ageNameField
        val client = MinecraftClient.getInstance()
        if (field != null && field.isFocused && client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
            return true
        }

        if (ageNameField?.keyPressed(keyCode, scanCode, modifiers) == true) {
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        if (ageNameField?.charTyped(chr, modifiers) == true) {
            return true
        }
        return super.charTyped(chr, modifiers)
    }

    private fun updateScroll(mouseY: Double) {
        val trackY = y + 38
        val trackHeight = 54
        val thumbHeight = 15
        
        var percentage = (mouseY - trackY - (thumbHeight / 2.0)) / (trackHeight - thumbHeight)
        percentage = percentage.coerceIn(0.0, 1.0)
        
        val newScrollRow = (percentage * maxScrollRow).roundToInt()
        if (newScrollRow != scrollRow) {
            scrollRow = newScrollRow
            handler.scrollPages(scrollRow)
        }
    }

    private fun onAgeNameChanged(value: String) {
        if (value == lastSentAgeName) return
        lastSentAgeName = value

        val buf = PacketByteBufs.create()
        buf.writeVarInt(handler.syncId)
        buf.writeString(value, 64)
        ClientPlayNetworking.send(ModMessages.BOOK_BINDER_SET_AGE_NAME, buf)
    }
}
