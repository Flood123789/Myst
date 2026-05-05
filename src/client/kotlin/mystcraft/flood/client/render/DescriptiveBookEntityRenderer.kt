package mystcraft.flood.client.render

import mystcraft.flood.entity.DescriptiveBookEntity
import mystcraft.flood.item.DisplayedBookHelper
import mystcraft.flood.item.ModItems
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.model.BookModel
import net.minecraft.client.render.entity.model.EntityModelLayers
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.RotationAxis

class DescriptiveBookEntityRenderer(ctx: EntityRendererFactory.Context) : EntityRenderer<DescriptiveBookEntity>(ctx) {

    private val bookModel: BookModel = BookModel(ctx.getPart(EntityModelLayers.BOOK))

    init {
        shadowRadius = 0.18f
    }

    override fun getTexture(entity: DescriptiveBookEntity): Identifier {
        val name = if (entity.getStoredBook().item === ModItems.LINKING_BOOK) "linkbook" else "agebook"
        return resolveTexture("textures/entity/$name.png", FALLBACK_BOOK)
    }

    override fun render(
        entity: DescriptiveBookEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int
    ) {
        val stack = entity.getStoredBook()
        if (stack.isEmpty) return

        matrices.push()
        matrices.translate(0.0, 0.0625, 0.0)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw - 90f))
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90f))
        matrices.scale(0.85f, 0.85f, 0.85f)

        // Tint the cover red while the book is hurt so the player knows hits land.
        val flash = entity.getHurtFlash()
        val r = 1f
        val g = 1f - flash * 0.7f
        val b = 1f - flash * 0.7f

        val vc = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(getTexture(entity)))
        bookModel.setPageAngles(0f, 0.1f, 0.9f, 1.2f)
        bookModel.render(matrices, vc, light, OverlayTexture.DEFAULT_UV, r, g, b, 1f)
        matrices.pop()

        renderLabel(entity, stack, matrices, vertexConsumers)
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
    }

    private fun renderLabel(
        entity: DescriptiveBookEntity,
        stack: net.minecraft.item.ItemStack,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider
    ) {
        val label: Text = DisplayedBookHelper.getDisplayName(stack) ?: return
        val client = MinecraftClient.getInstance()
        val camera = client.cameraEntity ?: return
        if (camera.squaredDistanceTo(entity) > 256.0) return

        matrices.push()
        matrices.translate(0.0, 0.55, 0.0)
        matrices.multiply(dispatcher.rotation)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f))
        val scale = 0.025f
        matrices.scale(-scale, -scale, scale)

        val tr = client.textRenderer
        val width = tr.getWidth(label)
        tr.draw(
            label,
            (-width / 2).toFloat(),
            0f,
            0xF6E7B0,
            false,
            matrices.peek().positionMatrix,
            vertexConsumers,
            TextRenderer.TextLayerType.SEE_THROUGH,
            0x55000000,
            15728880
        )
        matrices.pop()
    }

    private fun resolveTexture(path: String, fallback: Identifier): Identifier {
        val custom = Identifier("mystcraft-reforged", path)
        return if (MinecraftClient.getInstance().resourceManager.getResource(custom).isPresent) custom else fallback
    }

    companion object {
        // Drop a 64×32 PNG at assets/mystcraft-reforged/textures/entity/<name>.png
        // (linkbook.png / agebook.png) to override the vanilla fallback.
        private val FALLBACK_BOOK = Identifier("minecraft", "textures/entity/enchanting_table_book.png")
    }
}
