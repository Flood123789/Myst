package mystcraft.flood.client.gui

import mystcraft.flood.item.BookPreviewSnapshot
import net.minecraft.client.gui.DrawContext

object BookPreviewRenderer {
    fun drawPreview(context: DrawContext, preview: BookPreviewSnapshot, x: Int, y: Int, scale: Int) {
        for (py in 0 until preview.height) {
            for (px in 0 until preview.width) {
                val color = 0xFF000000.toInt() or preview.pixels[py * preview.width + px]
                context.fill(
                    x + px * scale,
                    y + py * scale,
                    x + (px + 1) * scale,
                    y + (py + 1) * scale,
                    color
                )
            }
        }
    }

    fun drawFallback(context: DrawContext, x: Int, y: Int, width: Int, height: Int, topColor: Int, bottomColor: Int) {
        context.fillGradient(x, y, x + width, y + height, 0xFF000000.toInt() or topColor, 0xFF000000.toInt() or bottomColor)
    }
}
