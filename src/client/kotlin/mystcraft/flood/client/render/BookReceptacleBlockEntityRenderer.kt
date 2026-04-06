package mystcraft.flood.client.render

import mystcraft.flood.block.entity.BookReceptacleBlockEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.block.entity.BlockEntityRenderer
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory
import net.minecraft.client.render.model.json.ModelTransformationMode
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.state.property.Properties
import net.minecraft.util.math.Direction
import net.minecraft.util.math.RotationAxis

class BookReceptacleBlockEntityRenderer(ctx: BlockEntityRendererFactory.Context) : BlockEntityRenderer<BookReceptacleBlockEntity> {
    
    override fun render(
        entity: BookReceptacleBlockEntity,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
        overlay: Int
    ) {
        val stack = entity.inventory.getStack(0)
        // If there's no book, don't draw anything!
        if (stack.isEmpty) return

        val facing = entity.cachedState.get(Properties.FACING)

        matrices.push()
        
        // 1. Move to the exact center of the block
        matrices.translate(0.5, 0.5, 0.5)

        // 2. Rotate the rendering matrix so "forward" matches the block's facing direction
        when (facing) {
            Direction.NORTH -> {} // Default
            Direction.SOUTH -> matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f))
            Direction.EAST -> matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(270f))
            Direction.WEST -> matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90f))
            Direction.UP -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f))
            Direction.DOWN -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f))
        }

        // 3. Move the book out to the front face (Z is forward/backward after our rotation).
        // A block is 1.0 wide, so the front face is at -0.5. We use -0.42 to embed it halfway in the slit.
        matrices.translate(0.0, 0.0, -0.42)

        // 4. Scale the book down so it fits nicely inside the texture's slit
        matrices.scale(0.5f, 0.5f, 0.5f)

        // 5. Draw the item!
        val itemRenderer = MinecraftClient.getInstance().itemRenderer
        itemRenderer.renderItem(
            stack,
            ModelTransformationMode.FIXED, // "FIXED" renders it flat like it's in an item frame
            light,
            overlay,
            matrices,
            vertexConsumers,
            entity.world,
            0
        )

        matrices.pop()
    }
}