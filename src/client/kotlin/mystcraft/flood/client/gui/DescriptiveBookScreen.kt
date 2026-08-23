package mystcraft.flood.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.client.cache.ClientAgeCache
import mystcraft.flood.item.BookPreviewData
import mystcraft.flood.item.DisplayedBookHelper
import mystcraft.flood.network.ModMessages
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.item.ItemStack
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.text.TextColor
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos

/**
 * Read-only book UI for an authored or linked Descriptive Book.
 * Content comes from [DescriptiveBookSurveyBuilder]; activation and renaming are requests sent to
 * the authoritative server rather than direct edits to the client-side ItemStack copy.
 */
class DescriptiveBookScreen(
    private val stack: ItemStack,
    private val hand: Hand?,
    private val standPos: BlockPos?
) : Screen(Text.literal("Descriptive Book")) {

    private var spreadIndex = 0
    private var nameField: TextFieldWidget? = null
    private var lastSentName = ""

    override fun init() {
        super.init()
        val left = (width - BOOK_WIDTH) / 2
        val top = (height - BOOK_HEIGHT) / 2
        val initialName = currentEditableName()

        val field = TextFieldWidget(textRenderer, left + 24, top + 18, 120, 12, Text.literal("Age Name"))
        field.setMaxLength(64)
        field.setDrawsBackground(false)
        field.setEditableColor(TEXT_COLOR)
        field.setUneditableColor(TEXT_MUTED)
        field.setSuggestion("Name this Age")
        field.text = initialName
        field.setChangedListener(::onNameChanged)
        lastSentName = initialName.trim()
        nameField = addDrawableChild(field)
    }

    override fun shouldPause(): Boolean = false

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        val survey = DescriptiveBookSurveyBuilder.build(currentStack())
        clampSpread(survey)
        nameField?.let { field ->
            field.visible = spreadIndex == 0
            field.setSuggestion(if (field.text.isBlank()) "Name this Age" else null)
        }

        val left = (width - BOOK_WIDTH) / 2
        val top = (height - BOOK_HEIGHT) / 2

        drawBookShell(context, left, top)
        if (spreadIndex == 0) {
            drawCover(context, survey, left, top)
        } else {
            drawSpread(context, survey, left, top)
        }

        drawFooter(context, survey, left, top)
        super.render(context, mouseX, mouseY, delta)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button)
        }

        if (nameField?.mouseClicked(mouseX, mouseY, button) == true) {
            setFocused(nameField)
            return true
        }

        val survey = DescriptiveBookSurveyBuilder.build(stack)
        clampSpread(survey)
        val left = (width - BOOK_WIDTH) / 2
        val top = (height - BOOK_HEIGHT) / 2
        val x = mouseX.toInt()
        val y = mouseY.toInt()

        if (spreadIndex == 0 && contains(REFRESH_X, REFRESH_Y, REFRESH_WIDTH, REFRESH_HEIGHT, left, top, x, y)) {
            refreshPreview()
            return true
        }

        if (spreadIndex == 0 && contains(PANEL_X, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT, left, top, x, y)) {
            if (survey.canActivate) {
                activateBook()
            }
            return true
        }

        if (contains(0, 0, 156, 195, left, top, x, y)) {
            turnLeft()
            return true
        }

        if (contains(158, 0, 154, 195, left, top, x, y)) {
            turnRight(survey)
            return true
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val field = nameField
        val client = MinecraftClient.getInstance()
        if (field != null && field.isFocused && client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
            return true
        }

        if (field?.keyPressed(keyCode, scanCode, modifiers) == true) {
            return true
        }

        val survey = DescriptiveBookSurveyBuilder.build(stack)
        clampSpread(survey)

        return when (keyCode) {
            263, 262 -> {
                if (keyCode == 263) turnLeft() else turnRight(survey)
                true
            }
            257, 335 -> {
                if (spreadIndex == 0 && survey.canActivate) {
                    activateBook()
                    true
                } else {
                    super.keyPressed(keyCode, scanCode, modifiers)
                }
            }
            else -> super.keyPressed(keyCode, scanCode, modifiers)
        }
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        if (nameField?.charTyped(chr, modifiers) == true) {
            return true
        }
        return super.charTyped(chr, modifiers)
    }

    private fun drawBookShell(context: DrawContext, left: Int, top: Int) {
        RenderSystem.enableBlend()
        context.drawTexture(COVER_TEXTURE, left, top + 7, 152f, 0f, 34, 192, 256, 256)
        context.drawTexture(COVER_TEXTURE, left + 34, top + 7, 49f, 0f, 103, 192, 256, 256)
        context.drawTexture(COVER_TEXTURE, left + 137, top + 7, 45f, 0f, 4, 192, 256, 256)
        context.drawTexture(COVER_TEXTURE, left + 141, top + 7, 0f, 0f, 186, 192, 256, 256)
        context.drawTexture(COVER_TEXTURE, left, top + 7, 186f, 0f, 34, 192, 256, 256)
        context.drawTexture(COVER_TEXTURE, left + 293, top + 7, 186f, 0f, 34, 192, 256, 256)
        RenderSystem.disableBlend()
    }

    private fun drawCover(context: DrawContext, survey: DescriptiveBookSurvey, left: Int, top: Int) {
        RenderSystem.enableBlend()
        context.drawTexture(PAGE_RIGHT_TEXTURE, left + 163, top, 0f, 0f, 156, 195, 256, 256)
        RenderSystem.disableBlend()
        drawPreviewPanel(context, survey, left, top)

        drawNameplate(context, left, top)
        context.drawText(textRenderer, survey.title, left + 40, top + 38, TEXT_COLOR, false)
        drawParagraphs(context, survey.subtitle, left + 40, top + 52, 100, 50, TEXT_MUTED)
        drawParagraphs(context, survey.coverParagraphs, left + 40, top + 76, 104, 76, TEXT_COLOR)

        val panelHint = if (survey.canActivate) {
            "Touch panel to cross"
        } else {
            "The panel is inert"
        }
        val hintWidth = textRenderer.getWidth(panelHint)
        context.drawText(textRenderer, panelHint, left + PANEL_X + (PANEL_WIDTH - hintWidth) / 2, top + PANEL_Y + PANEL_HEIGHT + 7, TEXT_COLOR, false)

        val refreshLabel = if (currentStack().nbt?.contains("Age_ID") == true) "[Refresh Image]" else "[Sky Card]"
        val refreshWidth = textRenderer.getWidth(refreshLabel)
        context.drawText(textRenderer, refreshLabel, left + REFRESH_X + (REFRESH_WIDTH - refreshWidth) / 2, top + REFRESH_Y + 3, if (currentStack().nbt?.contains("Age_ID") == true) TEXT_COLOR else TEXT_MUTED, false)
    }

    private fun drawPreviewPanel(context: DrawContext, survey: DescriptiveBookSurvey, left: Int, top: Int) {
        val preview = BookPreviewData.read(currentStack())
        if (preview != null) {
            val scale = 3
            val previewWidth = preview.width * scale
            val previewHeight = preview.height * scale
            context.fill(PANEL_X + left, PANEL_Y + top, PANEL_X + PANEL_WIDTH + left, PANEL_Y + PANEL_HEIGHT + top, 0xFF1B1B1B.toInt())
            BookPreviewRenderer.drawPreview(
                context,
                preview,
                left + PANEL_X + (PANEL_WIDTH - previewWidth) / 2,
                top + PANEL_Y + (PANEL_HEIGHT - previewHeight) / 2,
                scale
            )
        } else {
            BookPreviewRenderer.drawFallback(
                context,
                left + PANEL_X,
                top + PANEL_Y,
                PANEL_WIDTH,
                PANEL_HEIGHT,
                survey.panelTopColor,
                survey.panelBottomColor
            )
        }
        context.drawBorder(left + PANEL_X, top + PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT, 0x74533B)
    }

    private fun drawNameplate(context: DrawContext, left: Int, top: Int) {
        val fieldX = left + 24
        val fieldY = top + 18
        val outerX = fieldX - 4
        val outerY = fieldY - 7
        val outerWidth = 128
        val outerHeight = 22

        context.fill(outerX, outerY, outerX + outerWidth, outerY + outerHeight, 0xCFE3D0BB.toInt())
        context.drawBorder(outerX, outerY, outerWidth, outerHeight, 0x8A6A5038.toInt())
        context.fill(fieldX - 1, fieldY - 1, fieldX + 121, fieldY + 13, 0x70F5EAD2)
        context.drawText(textRenderer, "Age Name", fieldX, fieldY - 9, TEXT_MUTED, false)
    }

    private fun drawSpread(context: DrawContext, survey: DescriptiveBookSurvey, left: Int, top: Int) {
        RenderSystem.enableBlend()
        context.drawTexture(PAGE_LEFT_TEXTURE, left + 7, top, 0f, 0f, 156, 195, 256, 256)
        context.drawTexture(PAGE_RIGHT_FULL_TEXTURE, left + 163, top, 0f, 0f, 156, 195, 256, 256)
        RenderSystem.disableBlend()

        val leftPage = survey.pages.getOrNull((spreadIndex - 1) * 2)
        val rightPage = survey.pages.getOrNull((spreadIndex - 1) * 2 + 1)

        drawSurveyPage(context, leftPage, left + 19, top + 18, 118)
        drawSurveyPage(context, rightPage, left + 175, top + 18, 118)
    }

    private fun drawSurveyPage(context: DrawContext, page: DescriptiveBookSurveyPage?, x: Int, y: Int, width: Int) {
        if (page == null) return
        context.drawText(textRenderer, page.title, x, y, TEXT_COLOR, false)
        drawParagraphs(context, page.paragraphs, x, y + 14, width, 145, TEXT_COLOR)
    }

    private fun drawParagraphs(
        context: DrawContext,
        paragraphs: String,
        x: Int,
        y: Int,
        width: Int,
        maxHeight: Int,
        color: Int
    ) {
        drawParagraphs(context, listOf(paragraphs), x, y, width, maxHeight, color)
    }

    private fun drawParagraphs(
        context: DrawContext,
        paragraphs: List<String>,
        x: Int,
        y: Int,
        width: Int,
        maxHeight: Int,
        color: Int
    ) {
        var cursorY = y
        for (paragraph in paragraphs.filter { it.isNotBlank() }) {
            val wrapped = textRenderer.wrapLines(styledParagraph(paragraph), width)
            for (line in wrapped) {
                if (cursorY > y + maxHeight) return
                context.drawText(textRenderer, line, x, cursorY, color, false)
                cursorY += textRenderer.fontHeight
            }
            cursorY += 4
        }
    }

    private fun styledParagraph(paragraph: String): Text {
        var cursor = 0
        val text: MutableText = Text.empty()

        for (match in HEX_PATTERN.findAll(paragraph)) {
            val start = match.range.first
            val endExclusive = match.range.last + 1
            if (start > cursor) {
                text.append(Text.literal(paragraph.substring(cursor, start)))
            }

            val token = match.value
            val rgb = token.substring(1).toIntOrNull(16)
            if (rgb == null) {
                text.append(Text.literal(token))
            } else {
                text.append(
                    Text.literal(token).styled { style ->
                        style.withColor(TextColor.fromRgb(rgb)).withBold(true)
                    }
                )
            }
            cursor = endExclusive
        }

        if (cursor < paragraph.length) {
            text.append(Text.literal(paragraph.substring(cursor)))
        }

        return text
    }

    private fun drawFooter(context: DrawContext, survey: DescriptiveBookSurvey, left: Int, top: Int) {
        val lastSpread = lastSpreadIndex(survey)
        val label = "${spreadIndex}/$lastSpread"
        val labelWidth = textRenderer.getWidth(label)
        context.drawText(textRenderer, label, left + 165 - labelWidth / 2, top + 185, TEXT_COLOR, false)

        if (spreadIndex > 0) {
            context.drawText(textRenderer, "<", left + 16, top + 183, TEXT_COLOR, false)
        }
        if (spreadIndex < lastSpread) {
            context.drawText(textRenderer, ">", left + 305, top + 183, TEXT_COLOR, false)
        }
    }

    private fun activateBook() {
        val buf = PacketByteBufs.create()
        val openedFromStand = standPos != null
        buf.writeBoolean(openedFromStand)
        if (openedFromStand) {
            buf.writeBlockPos(standPos)
        } else {
            buf.writeEnumConstant(hand ?: Hand.MAIN_HAND)
        }
        ClientPlayNetworking.send(ModMessages.ACTIVATE_DESCRIPTIVE_BOOK, buf)
        close()
    }

    private fun onNameChanged(rawValue: String) {
        val trimmedValue = rawValue.trim().take(64)
        nameField?.setSuggestion(if (trimmedValue.isBlank()) "Name this Age" else null)
        if (trimmedValue == lastSentName) return
        lastSentName = trimmedValue

        DisplayedBookHelper.applyAgeBookName(stack, trimmedValue)

        val buf = PacketByteBufs.create()
        val openedFromStand = standPos != null
        buf.writeBoolean(openedFromStand)
        if (openedFromStand) {
            buf.writeBlockPos(standPos)
        } else {
            buf.writeEnumConstant(hand ?: Hand.MAIN_HAND)
        }
        buf.writeString(trimmedValue, 64)
        ClientPlayNetworking.send(ModMessages.RENAME_DESCRIPTIVE_BOOK, buf)
    }

    private fun refreshPreview() {
        if (currentStack().nbt?.contains("Age_ID") != true) return
        val buf = PacketByteBufs.create()
        val openedFromStand = standPos != null
        buf.writeBoolean(openedFromStand)
        if (openedFromStand) {
            buf.writeBlockPos(standPos)
        } else {
            buf.writeEnumConstant(hand ?: Hand.MAIN_HAND)
        }
        ClientPlayNetworking.send(ModMessages.UPDATE_DESCRIPTIVE_BOOK_PREVIEW, buf)
    }

    private fun currentEditableName(): String {
        val nbt = stack.nbt
        val draftName = nbt?.getString("Age_Name")?.trim().orEmpty()
        if (draftName.isNotBlank()) return draftName

        val ageId = nbt?.getString("Age_ID")?.takeIf { it.isNotBlank() }?.let(Identifier::tryParse)
        if (ageId != null) {
            val profileName = ClientAgeCache.getProperties(ageId)?.ageState?.displayName?.trim().orEmpty()
            if (profileName.isNotBlank()) return profileName
        }

        return ""
    }

    private fun currentStack(): ItemStack {
        val client = MinecraftClient.getInstance()
        val live = hand?.let { heldHand -> client.player?.getStackInHand(heldHand) }
        return if (live != null && !live.isEmpty) live else stack
    }

    private fun turnLeft() {
        if (spreadIndex > 0) {
            spreadIndex--
        }
    }

    private fun turnRight(survey: DescriptiveBookSurvey) {
        val lastSpread = lastSpreadIndex(survey)
        if (spreadIndex < lastSpread) {
            spreadIndex++
        }
    }

    private fun clampSpread(survey: DescriptiveBookSurvey) {
        spreadIndex = spreadIndex.coerceIn(0, lastSpreadIndex(survey))
    }

    private fun lastSpreadIndex(survey: DescriptiveBookSurvey): Int = (survey.pages.size + 1) / 2

    private fun contains(localX: Int, localY: Int, boxWidth: Int, boxHeight: Int, left: Int, top: Int, x: Int, y: Int): Boolean =
        x in (left + localX)..<(left + localX + boxWidth) && y in (top + localY)..<(top + localY + boxHeight)

    companion object {
        private const val BOOK_WIDTH = 327
        private const val BOOK_HEIGHT = 199
        private const val PANEL_X = 173
        private const val PANEL_Y = 20
        private const val PANEL_WIDTH = 132
        private const val PANEL_HEIGHT = 83
        private const val REFRESH_X = 196
        private const val REFRESH_Y = 111
        private const val REFRESH_WIDTH = 90
        private const val REFRESH_HEIGHT = 14
        private const val TEXT_COLOR = 0x2C2016
        private const val TEXT_MUTED = 0x5C4731

        private val COVER_TEXTURE = Identifier(MystcraftReforged.MOD_ID, "textures/gui/bookui_cover.png")
        private val PAGE_LEFT_TEXTURE = Identifier(MystcraftReforged.MOD_ID, "textures/gui/bookui_pagel.png")
        private val PAGE_RIGHT_TEXTURE = Identifier(MystcraftReforged.MOD_ID, "textures/gui/bookui_pager.png")
        private val PAGE_RIGHT_FULL_TEXTURE = Identifier(MystcraftReforged.MOD_ID, "textures/gui/bookui_rpage_full.png")
        private val HEX_PATTERN = Regex("#[0-9A-Fa-f]{6}")
    }
}
