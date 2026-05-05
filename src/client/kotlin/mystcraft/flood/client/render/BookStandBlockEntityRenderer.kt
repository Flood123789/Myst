package mystcraft.flood.client.render

import mystcraft.flood.block.entity.BookStandBlockEntity
import mystcraft.flood.item.DisplayedBookHelper
import mystcraft.flood.item.ModItems
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.block.entity.BlockEntityRenderer
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory
import net.minecraft.client.render.entity.model.BookModel
import net.minecraft.client.render.entity.model.EntityModelLayers
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemStack
import net.minecraft.state.property.Properties
import net.minecraft.util.Identifier
import net.minecraft.util.math.Direction
import net.minecraft.util.math.RotationAxis

class BookStandBlockEntityRenderer(ctx: BlockEntityRendererFactory.Context) : BlockEntityRenderer<BookStandBlockEntity> {
    private val textRenderer: TextRenderer = ctx.textRenderer
    private val standModel: BookstandModel = BookstandModel(ctx.getLayerModelPart(ModEntityModelLayers.BOOKSTAND))
    private val bookModel: BookModel = BookModel(ctx.getLayerModelPart(EntityModelLayers.BOOK))

    override fun rendersOutsideBoundingBox(blockEntity: BookStandBlockEntity): Boolean = true

    override fun render(
        entity: BookStandBlockEntity,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
        overlay: Int
    ) {
        val facing = entity.cachedState.get(Properties.HORIZONTAL_FACING)
        renderStand(matrices, vertexConsumers, light, overlay, facing)

        val stack = entity.getBook()
        if (!stack.isEmpty) {
            renderBook(stack, matrices, vertexConsumers, light, overlay, facing)
            renderLabel(entity, stack, matrices, vertexConsumers)
        }
    }

    private fun renderStand(
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
        overlay: Int,
        facing: Direction
    ) {
        matrices.push()
        // Center on top of the block, then flip upright (Techne model authored Y-down).
        matrices.translate(0.5, 0.5, 0.5)
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180f))
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(facing.asRotation()))
        matrices.scale(0.0625f, 0.0625f, 0.0625f)
        val tex = resolveTexture("textures/entity/bookstand.png", FALLBACK_STAND)
        val vc = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(tex))
        standModel.render(matrices, vc, light, overlay)
        matrices.pop()
    }

    private fun renderBook(
        stack: ItemStack,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
        overlay: Int,
        facing: Direction
    ) {
        val name = if (stack.item === ModItems.LINKING_BOOK) "linkbook" else "agebook"
        val tex = resolveTexture("textures/entity/$name.png", FALLBACK_BOOK)

        matrices.push()
        matrices.translate(0.5, 0.6875, 0.5)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - facing.asRotation()))
        // Tip the book to lay open on the slanted arms.
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(120f))
        matrices.scale(0.8f, 0.8f, 0.8f)

        val vc = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(tex))
        bookModel.setPageAngles(0f, 0.1f, 0.9f, 1.05f)
        bookModel.render(matrices, vc, light, overlay, 1f, 1f, 1f, 1f)
        matrices.pop()
    }

    private fun renderLabel(
        entity: BookStandBlockEntity,
        stack: ItemStack,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider
    ) {
        val label = DisplayedBookHelper.getDisplayName(stack) ?: return
        val client = MinecraftClient.getInstance()
        val camera = client.cameraEntity ?: return
        if (camera.squaredDistanceTo(entity.pos.x + 0.5, entity.pos.y + 0.5, entity.pos.z + 0.5) > 256.0) return

        matrices.push()
        matrices.translate(0.5, 1.42, 0.5)
        matrices.multiply(client.entityRenderDispatcher.rotation)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f))
        val scale = 0.028f
        matrices.scale(-scale, -scale, scale)

        val width = textRenderer.getWidth(label)
        textRenderer.draw(
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
        // Until you drop in a 64×32 entity texture, fall back to recognisable stand-ins.
        private val FALLBACK_BOOK = Identifier("minecraft", "textures/entity/enchanting_table_book.png")
        private val FALLBACK_STAND = Identifier("mystcraft-reforged", "textures/block/bookstand.png")
    }
}
