package mystcraft.flood.client.render

import net.minecraft.client.model.ModelData
import net.minecraft.client.model.ModelPart
import net.minecraft.client.model.ModelPartBuilder
import net.minecraft.client.model.ModelPartData
import net.minecraft.client.model.ModelTransform
import net.minecraft.client.model.TexturedModelData
import net.minecraft.client.render.entity.model.EntityModel
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.util.math.MatrixStack
import mystcraft.flood.entity.ParadoxReaperEntity
import net.minecraft.entity.LivingEntity

/**
 * Geometry for the Paradox Reaper: cubes and rectangular prisms only, per the design.
 *
 * The body is two nested shells. [shell] and [head] are drawn from the translucent lattice
 * texture; [core] and [headCore] sit inside them and are drawn from the emissive texture, which
 * is what produces the "nebula suspended inside glass" read rather than a flat glowing box.
 *
 * There is no leg hierarchy here on purpose. Limb segments are drawn from a single reusable
 * [bone] part that the renderer positions once per segment from solved IK endpoints, so the
 * model file carries no pose information at all, only shapes.
 *
 * Parts are authored Y-up in world orientation rather than in the usual Y-down entity model
 * convention, because the renderer places them with a surface-aligned basis instead of the
 * standard flip that LivingEntityRenderer applies.
 */
class ParadoxReaperModel<T : LivingEntity>(root: ModelPart) : EntityModel<T>() {

    val shell: ModelPart = root.getChild("shell")
    val core: ModelPart = root.getChild("core")
    val head: ModelPart = root.getChild("head")
    val headCore: ModelPart = root.getChild("head_core")
    val eyes: ModelPart = root.getChild("eyes")
    val fangs: List<ModelPart> = listOf(root.getChild("fang_left"), root.getChild("fang_right"))
    val fangTips: List<ModelPart> = listOf(root.getChild("fang_tip_left"), root.getChild("fang_tip_right"))

    /** One block long, standing on the origin along +Y. Scaled per segment by the renderer. */
    val bone: ModelPart = root.getChild("bone")

    /** A single drifting cube; drawn once per orbiter at a different transform each time. */
    val orbiter: ModelPart = root.getChild("orbiter")

    /**
     * A conventional joint hierarchy used only by model-inspection ragdoll mods. The live
     * renderer keeps using its world-space IK rig, while Whackdolls can discover this root by
     * reflection and turn the body plus all eighteen leg segments into real articulated bones.
     */
    private val ragdollRig: ModelPart = root.getChild("ragdoll_rig")

    @Suppress("unused")
    fun whackdollsRigRoot(): ModelPart = ragdollRig

    override fun setAngles(
        entity: T,
        limbAngle: Float,
        limbDistance: Float,
        animationProgress: Float,
        headYaw: Float,
        headPitch: Float
    ) = Unit

    override fun render(
        matrices: MatrixStack,
        vertices: VertexConsumer,
        light: Int,
        overlay: Int,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float
    ) {
        ragdollRig.render(matrices, vertices, light, overlay, red, green, blue, alpha)
    }

    companion object {
        /** Model units per block. A bone is authored exactly one block tall. */
        const val UNITS_PER_BLOCK = 16.0f

        fun getTexturedModelData(): TexturedModelData {
            val data = ModelData()
            val root = data.root

            // Local axes: +X lateral, +Y away from the clung surface, +Z forward.

            // Outer lattice cage, centred on the body origin.
            root.addChild(
                "shell",
                ModelPartBuilder.create().uv(0, 40)
                    .cuboid(-6.5f, -6.5f, -9f, 13f, 13f, 13f),
                ModelTransform.NONE
            )

            // Nebula suspended inside the cage.
            root.addChild(
                "core",
                ModelPartBuilder.create().uv(0, 78)
                    .cuboid(-4f, -4f, -6.5f, 8f, 8f, 8f),
                ModelTransform.NONE
            )

            root.addChild(
                "head",
                ModelPartBuilder.create().uv(0, 104)
                    .cuboid(-4f, -5.5f, 3.5f, 8f, 7f, 8f),
                ModelTransform.NONE
            )

            root.addChild(
                "head_core",
                ModelPartBuilder.create().uv(76, 40)
                    .cuboid(-2.5f, -4f, 4.5f, 5f, 4f, 5f),
                ModelTransform.NONE
            )

            // Two hard white points on the face; drawn on the emissive layer so they glare.
            root.addChild(
                "eyes",
                ModelPartBuilder.create()
                    .uv(60, 28).cuboid(-3f, -2f, 11.6f, 2f, 2f, 1f)
                    .uv(60, 28).cuboid(1f, -2f, 11.6f, 2f, 2f, 1f),
                ModelTransform.NONE
            )

            // Long translucent fangs tucked under the face. Each points down and slightly
            // forward, with a small inward cant so the pair frames the mouth instead of reading
            // as another set of straight legs. Tip geometry is separate for an emissive pass.
            addFang(root, "left", -2.35f, 0.11f)
            addFang(root, "right", 2.35f, -0.11f)

            root.addChild(
                "bone",
                ModelPartBuilder.create().uv(60, 0)
                    .cuboid(-1f, 0f, -1f, 2f, UNITS_PER_BLOCK, 2f),
                ModelTransform.NONE
            )

            root.addChild(
                "orbiter",
                ModelPartBuilder.create().uv(60, 20)
                    .cuboid(-1.1f, -1.1f, -1.1f, 2.2f, 2.2f, 2.2f),
                ModelTransform.NONE
            )

            addRagdollRig(root)

            return TexturedModelData.of(data, 128, 128)
        }

        private fun addRagdollRig(root: ModelPartData) {
            val rig = root.addChild("ragdoll_rig", ModelPartBuilder.create(), ModelTransform.NONE)
            val body = rig.addChild(
                "body",
                ModelPartBuilder.create().uv(0, 40)
                    .cuboid(-6.5f, -6.5f, -9f, 13f, 13f, 13f),
                ModelTransform.NONE
            )
            body.addChild(
                "head",
                ModelPartBuilder.create().uv(0, 104)
                    .cuboid(-4f, -5.5f, 0f, 8f, 7f, 8f),
                ModelTransform.pivot(0f, 0f, 3.5f)
            )

            addRagdollLeg(body, "front", -1f, 4.0f)
            addRagdollLeg(body, "front", 1f, 4.0f)
            addRagdollLeg(body, "middle", -1f, 0.0f)
            addRagdollLeg(body, "middle", 1f, 0.0f)
            addRagdollLeg(body, "rear", -1f, -4.0f)
            addRagdollLeg(body, "rear", 1f, -4.0f)
        }

        private fun addRagdollLeg(body: ModelPartData, row: String, side: Float, z: Float) {
            val sideName = if (side < 0f) "left" else "right"
            val start = if (side < 0f) -9f else 0f
            val upper = body.addChild(
                "${sideName}_${row}_leg_upper",
                ModelPartBuilder.create().uv(60, 0).cuboid(start, -1f, -1f, 9f, 2f, 2f),
                ModelTransform.pivot(side * 5.5f, 0f, z)
            )
            val lower = upper.addChild(
                "${sideName}_${row}_leg_lower",
                ModelPartBuilder.create().uv(60, 0).cuboid(start, -0.8f, -0.8f, 9f, 1.6f, 1.6f),
                ModelTransform.pivot(side * 9f, 0f, 0f)
            )
            lower.addChild(
                "${sideName}_${row}_leg_foot",
                ModelPartBuilder.create().uv(60, 0).cuboid(start, -0.55f, -0.55f, 9f, 1.1f, 1.1f),
                ModelTransform.pivot(side * 9f, 0f, 0f)
            )
        }

        private fun addFang(root: net.minecraft.client.model.ModelPartData, side: String, x: Float, roll: Float) {
            val transform = ModelTransform.of(x, -2.4f, 11.15f, -0.18f, 0f, roll)
            root.addChild(
                "fang_$side",
                ModelPartBuilder.create().uv(60, 0)
                    .cuboid(-0.62f, -4.2f, -0.62f, 1.24f, 4.4f, 1.24f),
                transform
            )
            root.addChild(
                "fang_tip_$side",
                ModelPartBuilder.create().uv(60, 28)
                    .cuboid(-0.48f, -4.2f, -0.48f, 0.96f, 1.25f, 0.96f),
                transform
            )
        }
    }
}
