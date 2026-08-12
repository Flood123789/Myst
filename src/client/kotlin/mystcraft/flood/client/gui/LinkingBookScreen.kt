package mystcraft.flood.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.item.BookPreviewData
import mystcraft.flood.network.ModMessages
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.Identifier

import mystcraft.flood.item.DisplayedBookHelper
import net.minecraft.client.gui.widget.TextFieldWidget

class LinkingBookScreen(
    private val stack: ItemStack,
    private val hand: Hand
) : Screen(Text.literal("Linking Book")) {

    private var nameField: TextFieldWidget? = null
    private var lastSentName = ""

    override fun init() {
        super.init()
        val left = (width - BOOK_WIDTH) / 2
        val top = (height - BOOK_HEIGHT) / 2
        val initialName = DisplayedBookHelper.getAgeBookName(currentStack())

        val field = TextFieldWidget(textRenderer, left + 20, top + 18, 120, 12, Text.literal("Book Name"))
        field.setMaxLength(64)
        field.setDrawsBackground(false)
        field.setEditableColor(TEXT_COLOR)
        field.setUneditableColor(TEXT_MUTED)
        field.setSuggestion("Name this book")
        field.text = initialName
        field.setChangedListener(::onNameChanged)
        lastSentName = initialName.trim()
        nameField = addDrawableChild(field)
    }

    override fun shouldPause(): Boolean = false

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        nameField?.setSuggestion(if (nameField?.text.orEmpty().isBlank()) "Name this book" else null)
        val left = (width - BOOK_WIDTH) / 2
        val top = (height - BOOK_HEIGHT) / 2
        drawBookShell(context, left, top)
        drawLeftPage(context, left, top)
        drawRightPage(context, left, top)
        super.render(context, mouseX, mouseY, delta)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button)

        if (nameField?.mouseClicked(mouseX, mouseY, button) == true) {
            setFocused(nameField)
            return true
        }

        val left = (width - BOOK_WIDTH) / 2
        val top = (height - BOOK_HEIGHT) / 2
        val x = mouseX.toInt()
        val y = mouseY.toInt()

        if (contains(PANEL_X, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT, left, top, x, y) && isLinked()) {
            activateBook()
            return true
        }

        if (contains(REFRESH_X, REFRESH_Y, REFRESH_WIDTH, REFRESH_HEIGHT, left, top, x, y) && isLinked()) {
            refreshPreview()
            return true
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun onNameChanged(rawValue: String) {
        val trimmedValue = rawValue.trim().take(64)
        nameField?.setSuggestion(if (trimmedValue.isBlank()) "Name this book" else null)
        if (trimmedValue == lastSentName) return
        lastSentName = trimmedValue

        DisplayedBookHelper.applyAgeBookName(currentStack(), trimmedValue)

        val buf = PacketByteBufs.create()
        buf.writeBoolean(false)
        buf.writeEnumConstant(hand)
        buf.writeString(trimmedValue, 64)
        ClientPlayNetworking.send(ModMessages.RENAME_DESCRIPTIVE_BOOK, buf)
    }

    private fun drawBookShell(context: DrawContext, left: Int, top: Int) {
        RenderSystem.enableBlend()
        context.drawTexture(COVER_TEXTURE, left + 7, top, 0f, 0f, 156, 195, 256, 256)
        context.drawTexture(PAGE_RIGHT_TEXTURE, left + 163, top, 0f, 0f, 156, 195, 256, 256)
        RenderSystem.disableBlend()
    }

    private fun drawLeftPage(context: DrawContext, left: Int, top: Int) {
        val customName = DisplayedBookHelper.getAgeBookName(currentStack())

        val body = buildList {
            if (isLinked()) {
                val dest = if (customName.isNotBlank()) "$customName (${destinationName()})" else destinationName()
                add("This book is bound to $dest.")
                add("Its anchor lies at ${locationLabel()}.")
                add("Touch the painted panel to cross.")
                add("Use the refresh mark if the view in the leaves has grown stale.")
            } else {
                add("This book has not yet been bound to a place.")
                add("Hold it in the world and use it once to set its anchor.")
            }
        }

        var y = top + 36
        for (paragraph in body) {
            val wrapped = textRenderer.wrapLines(Text.literal(paragraph), 120)
            for (line in wrapped) {
                context.drawText(textRenderer, line, left + 20, y, TEXT_COLOR, false)
                y += textRenderer.fontHeight
            }
            y += 4
        }
    }

    private fun drawRightPage(context: DrawContext, left: Int, top: Int) {
        RenderSystem.enableBlend()
        context.drawTexture(PAGE_RIGHT_TEXTURE, left + 163, top, 0f, 0f, 156, 195, 256, 256)
        RenderSystem.disableBlend()

        val preview = BookPreviewData.read(currentStack())
        context.fill(PANEL_X + left, PANEL_Y + top, PANEL_X + PANEL_WIDTH + left, PANEL_Y + PANEL_HEIGHT + top, 0xFF1A2744.toInt())
        if (preview != null) {
            val scale = 3
            val previewWidth = preview.width * scale
            val previewHeight = preview.height * scale
            BookPreviewRenderer.drawPreview(
                context,
                preview,
                left + PANEL_X + (PANEL_WIDTH - previewWidth) / 2,
                top + PANEL_Y + (PANEL_HEIGHT - previewHeight) / 2,
                scale
            )
        } else {
            val topColor = if (isLinked()) 0x78A7FF else 0x5A4630
            val bottomColor = if (isLinked()) 0xC6E3FF else 0x241A12
            BookPreviewRenderer.drawFallback(context, left + PANEL_X, top + PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT, topColor, bottomColor)
        }
        context.drawBorder(left + PANEL_X, top + PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT, 0x74533B)

        val refreshLabel = if (isLinked()) "[Refresh Image]" else "[No Image]"
        val refreshX = left + REFRESH_X + (REFRESH_WIDTH - textRenderer.getWidth(refreshLabel)) / 2
        context.drawText(textRenderer, refreshLabel, refreshX, top + REFRESH_Y + 3, if (isLinked()) TEXT_COLOR else TEXT_MUTED, false)
    }

    private fun activateBook() {
        val buf = PacketByteBufs.create()
        buf.writeEnumConstant(hand)
        ClientPlayNetworking.send(ModMessages.ACTIVATE_LINKING_BOOK, buf)
        close()
    }

    private fun refreshPreview() {
        val buf = PacketByteBufs.create()
        buf.writeEnumConstant(hand)
        ClientPlayNetworking.send(ModMessages.UPDATE_LINKING_BOOK_PREVIEW, buf)
    }

    private fun isLinked(): Boolean = currentStack().nbt?.contains("Dimension") == true

    private fun currentStack(): ItemStack {
        val live = MinecraftClient.getInstance().player?.getStackInHand(hand)
        return if (live != null && !live.isEmpty) live else stack
    }

    private fun destinationName(): String {
        val dim = currentStack().nbt?.getString("Dimension")?.takeIf { it.isNotBlank() } ?: return "an unknown place"
        return Identifier.tryParse(dim)?.path?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: dim
    }

    private fun locationLabel(): String {
        val nbt = currentStack().nbt ?: return "unknown coordinates"
        val x = nbt.getDouble("PosX").toInt()
        val y = nbt.getDouble("PosY").toInt()
        val z = nbt.getDouble("PosZ").toInt()
        return "$x, $y, $z"
    }

    private fun contains(localX: Int, localY: Int, boxWidth: Int, boxHeight: Int, left: Int, top: Int, x: Int, y: Int): Boolean =
        x in (left + localX)..<(left + localX + boxWidth) && y in (top + localY)..<(left + localY + boxHeight)

    companion object {
        private const val BOOK_WIDTH = 327
        private const val BOOK_HEIGHT = 199
        private const val PANEL_X = 173
        private const val PANEL_Y = 20
        private const val PANEL_WIDTH = 132
        private const val PANEL_HEIGHT = 83
        private const val REFRESH_X = 190
        private const val REFRESH_Y = 111
        private const val REFRESH_WIDTH = 98
        private const val REFRESH_HEIGHT = 14
        private const val TEXT_COLOR = 0x2C2016
        private const val TEXT_MUTED = 0x5C4731

        private val COVER_TEXTURE = Identifier(MystcraftReforged.MOD_ID, "textures/gui/bookui_pagel.png")
        private val PAGE_RIGHT_TEXTURE = Identifier(MystcraftReforged.MOD_ID, "textures/gui/bookui_rpage_full.png")
    }
}
