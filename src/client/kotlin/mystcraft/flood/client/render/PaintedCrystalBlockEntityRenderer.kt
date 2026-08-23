package mystcraft.flood.client.render

import mystcraft.flood.block.entity.PaintedCrystalBlockEntity
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.RenderLayers
import net.minecraft.client.render.block.entity.BlockEntityRenderer
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.Direction
import net.minecraft.util.math.random.Random

class PaintedCrystalBlockEntityRenderer(ctx: BlockEntityRendererFactory.Context) :
    BlockEntityRenderer<PaintedCrystalBlockEntity> {

    private val blockRenderManager = ctx.renderManager

    override fun render(
        entity: PaintedCrystalBlockEntity,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
        overlay: Int
    ) {
        matrices.push()
        matrices.translate(0.5, 0.5, 0.5)
        matrices.scale(1.002f, 1.002f, 1.002f)
        matrices.translate(-0.5, -0.5, -0.5)

        Direction.entries.forEach { face ->
            val paintedState = entity.getPaintedState(face) ?: return@forEach
            val model = blockRenderManager.getModel(paintedState)
            val quads = model.getQuads(paintedState, face, Random.create(42L))
            val consumer = vertexConsumers.getBuffer(RenderLayers.getEntityBlockLayer(paintedState, false))
            quads.forEach { quad ->
                val tint = if (quad.hasColor()) {
                    MinecraftClient.getInstance().blockColors.getColor(
                        paintedState,
                        entity.world,
                        entity.pos,
                        quad.colorIndex
                    )
                } else {
                    -1
                }
                val red = (tint shr 16 and 0xFF) / 255.0f
                val green = (tint shr 8 and 0xFF) / 255.0f
                val blue = (tint and 0xFF) / 255.0f
                consumer.quad(matrices.peek(), quad, red, green, blue, light, overlay)
            }
        }
        matrices.pop()
    }
}
