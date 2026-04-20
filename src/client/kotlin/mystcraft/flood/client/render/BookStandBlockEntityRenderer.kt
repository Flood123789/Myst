package mystcraft.flood.client.render

import mystcraft.flood.block.entity.BookStandBlockEntity
import mystcraft.flood.item.DisplayedBookHelper
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.block.entity.BlockEntityRenderer
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory
import net.minecraft.client.render.model.json.ModelTransformationMode
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.state.property.Properties
import net.minecraft.util.math.RotationAxis

class BookStandBlockEntityRenderer(ctx: BlockEntityRendererFactory.Context) : BlockEntityRenderer<BookStandBlockEntity> {
    private val textRenderer: TextRenderer = ctx.textRenderer

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

        renderBook(entity, stack, matrices, vertexConsumers, light, overlay)
        renderLabel(entity, stack, matrices, vertexConsumers, light)
    }

    private fun renderBook(
        entity: BookStandBlockEntity,
        stack: net.minecraft.item.ItemStack,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
        overlay: Int
    ) {
        val facing = entity.cachedState.get(Properties.HORIZONTAL_FACING)

        matrices.push()
        matrices.translate(0.5, 0.90, 0.5)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(facing.asRotation()))
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(61f))
        matrices.scale(0.72f, 0.72f, 0.72f)

        MinecraftClient.getInstance().itemRenderer.renderItem(
            stack,
            ModelTransformationMode.FIXED,
            light,
            overlay,
            matrices,
            vertexConsumers,
            entity.world,
            0
        )

        matrices.pop()
    }

    private fun renderLabel(
        entity: BookStandBlockEntity,
        stack: net.minecraft.item.ItemStack,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int
    ) {
        val label = DisplayedBookHelper.getDisplayName(stack) ?: return
        val client = MinecraftClient.getInstance()
        val camera = client.cameraEntity ?: return
        if (camera.squaredDistanceTo(entity.pos.x + 0.5, entity.pos.y + 0.5, entity.pos.z + 0.5) > 256.0) return

        matrices.push()
        matrices.translate(0.5, 1.42, 0.5)
        matrices.multiply(client.entityRenderDispatcher.rotation)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f))
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
}
