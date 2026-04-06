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
        if (stack.isEmpty) return

        val facing = entity.cachedState.get(Properties.FACING)

        matrices.push()
        
        // 1. Start at the exact dead-center of the block space
        matrices.translate(0.5, 0.5, 0.5)

        // 2. Move outward from the center towards the wall the slab is attached to,
        // and rotate the camera so +Z is facing directly outward from the wall.
        // A slab is 6/16 thick (0.375). The exact center of the slit is 0.3125 away from the block center!
        when (facing) {
            Direction.SOUTH -> { // Attached to the NORTH wall, facing SOUTH
                matrices.translate(0.0, 0.0, -0.3125) 
                // No rotation needed, +Z is already South
            }
            Direction.NORTH -> { // Attached to the SOUTH wall, facing NORTH
                matrices.translate(0.0, 0.0, 0.3125)
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f))
            }
            Direction.EAST -> { // Attached to the WEST wall, facing EAST
                matrices.translate(-0.3125, 0.0, 0.0)
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90f))
            }
            Direction.WEST -> { // Attached to the EAST wall, facing WEST
                matrices.translate(0.3125, 0.0, 0.0)
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90f))
            }
            Direction.UP -> { // Attached to the BOTTOM floor, facing UP
                matrices.translate(0.0, -0.3125, 0.0)
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f))
            }
            Direction.DOWN -> { // Attached to the TOP ceiling, facing DOWN
                matrices.translate(0.0, 0.3125, 0.0)
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f))
            }
        }

        // 3. The matrix is now perfectly centered inside the 6-pixel deep slit, pointing outward!
        // Rotate Y by 90 degrees so the spine of the book faces the player.
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90f))
        
        // 4. Scale it down to fit
        matrices.scale(0.55f, 0.55f, 0.55f)

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
}