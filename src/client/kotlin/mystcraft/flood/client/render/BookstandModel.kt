package mystcraft.flood.client.render

import net.minecraft.client.model.ModelPart
import net.minecraft.client.model.ModelPartBuilder
import net.minecraft.client.model.ModelTransform
import net.minecraft.client.model.TexturedModelData
import net.minecraft.client.model.ModelData
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.util.math.MatrixStack

class BookstandModel(root: ModelPart) {

    private val leftArm: ModelPart = root.getChild("left_arm")
    private val rightArm: ModelPart = root.getChild("right_arm")
    private val post: ModelPart = root.getChild("post")
    private val base: ModelPart = root.getChild("base")

    fun render(matrices: MatrixStack, vertices: VertexConsumer, light: Int, overlay: Int) {
        leftArm.render(matrices, vertices, light, overlay)
        rightArm.render(matrices, vertices, light, overlay)
        post.render(matrices, vertices, light, overlay)
        base.render(matrices, vertices, light, overlay)
    }

    companion object {
        fun getTexturedModelData(): TexturedModelData {
            val data = ModelData()
            val root = data.root

            // Original ModelBookstand was authored at scale 1/16 (unit = 1 block-pixel).
            // Pivots placed so the wood model sits flush with the block top.
            // base: (0,0) addBox(-2.5, 0, -2.5, 5, 3, 5) at pivot (0, 5, 0)
            root.addChild(
                "base",
                ModelPartBuilder.create().uv(0, 0)
                    .cuboid(-2.5f, 0f, -2.5f, 5f, 3f, 5f),
                ModelTransform.pivot(0f, 5f, 0f)
            )

            // post: (0,8) addBox(-0.5, 0, -0.5, 1, 6, 1) at pivot (0, 0, 0)
            // post extends from pivot down 6 units stacking on the base.
            root.addChild(
                "post",
                ModelPartBuilder.create().uv(0, 8)
                    .cuboid(-0.5f, -1f, -0.5f, 1f, 6f, 1f),
                ModelTransform.pivot(0f, 0f, 0f)
            )

            // leftarm: uv (4,8) addBox(-0.5, -0.5, -1.5, 6, 1, 3) pivot (0.25, 0, -0.25), rot (π/6, 0, -π/12)
            root.addChild(
                "left_arm",
                ModelPartBuilder.create().uv(4, 8).mirrored()
                    .cuboid(-0.5f, -0.5f, -1.5f, 6f, 1f, 3f),
                ModelTransform.of(0.25f, 0f, -0.25f, ROT_30, 0f, -ROT_15)
            )

            // rightarm: uv (4,8) addBox(-0.5, -0.5, -1.5, 6, 1, 3) pivot (-0.25, 0, -0.25), rot (-π/6, π, π/12)
            root.addChild(
                "right_arm",
                ModelPartBuilder.create().uv(4, 8)
                    .cuboid(-0.5f, -0.5f, -1.5f, 6f, 1f, 3f),
                ModelTransform.of(-0.25f, 0f, -0.25f, -ROT_30, Math.PI.toFloat(), ROT_15)
            )

            return TexturedModelData.of(data, 64, 32)
        }

        private const val ROT_30 = 0.5235988f
        private const val ROT_15 = 0.2617994f
    }
}
