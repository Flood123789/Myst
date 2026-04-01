package mystcraft.flood.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.gui.BookBinderScreenHandler
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.entity.player.PlayerInventory
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

    override fun init() {
        backgroundWidth = 176
        backgroundHeight = 180
        super.init()
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2
        titleY = 6
        playerInventoryTitleY = backgroundHeight - 94
    }

    override fun drawBackground(context: DrawContext, delta: Float, mouseX: Int, mouseY: Int) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
        val x = (width - backgroundWidth) / 2
        val y = (height - backgroundHeight) / 2
        context.drawTexture(TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight)

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
}