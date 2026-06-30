package mystcraft.flood.client.render

import mystcraft.flood.block.entity.BookStandBlockEntity
import mystcraft.flood.item.DisplayedBookHelper
import mystcraft.flood.item.ModItems
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.WorldRenderer
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
        val stack = entity.getBook()
        if (stack.isEmpty) return

        val facing = entity.cachedState.get(Properties.HORIZONTAL_FACING)
        renderBook(entity, stack, matrices, vertexConsumers, light, overlay, facing)
        renderLabel(entity, stack, matrices, vertexConsumers)
    }

    private fun renderBook(
        entity: BookStandBlockEntity,
        stack: ItemStack,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
        overlay: Int,
        facing: Direction
    ) {
        val renderLight = entity.world?.let { world ->
            WorldRenderer.getLightmapCoordinates(world, entity.pos.up())
        } ?: light

        matrices.push()
        matrices.translate(0.5, 0.84, 0.5)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()))
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(80f))
        matrices.scale(0.78f, 0.78f, 0.78f)

        val vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(textureFor(stack)))
        bookModel.setPageAngles(0f, 0.08f, 0.85f, 1.15f)
        bookModel.render(matrices, vertexConsumer, renderLight, overlay, 1f, 1f, 1f, 1f)
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

    private fun textureFor(stack: ItemStack): Identifier =
        if (stack.item === ModItems.LINKING_BOOK) LINKING_BOOK_TEXTURE else DESCRIPTIVE_BOOK_TEXTURE

    companion object {
        private val DESCRIPTIVE_BOOK_TEXTURE = Identifier("mystcraft-reforged", "textures/entity/agebook.png")
        private val LINKING_BOOK_TEXTURE = Identifier("mystcraft-reforged", "textures/entity/linkbook.png")
    }
}
