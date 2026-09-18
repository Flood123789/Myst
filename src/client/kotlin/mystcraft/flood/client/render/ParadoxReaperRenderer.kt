package mystcraft.flood.client.render

import mystcraft.flood.entity.ParadoxReaperEntity
import mystcraft.flood.entity.SurfaceCling
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.LivingEntityRenderer
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.RotationAxis
import net.minecraft.util.math.Vec3d
import org.joml.Matrix3f
import org.joml.Matrix4f

/**
 * Renders a Paradox Reaper from solved IK rather than from a posed model.
 *
 * Every frame this builds a basis aligned to the surface the creature clings to, hands that
 * basis to the creature's [ReaperLegRig] so feet can be planted in world space, and then draws
 * each limb segment between the resulting joint positions. Nothing is keyframed; the pose is a
 * pure function of where the body is and where its feet last landed.
 *
 * Two passes make up the look. The lattice shell and legs are drawn translucent from the glass
 * texture; the nebula core, eyes, and orbiting cubes come from the emissive texture, which is
 * what reads as light trapped inside glass rather than as a flat glowing box. The emissive pass
 * uses a translucent-emissive layer rather than the additive "eyes" layer on purpose: the eyes
 * layer only writes colour, so a core sitting behind the shell's near face would be rejected by
 * the depth test and vanish.
 *
 * A dormant Reaper is deliberately dim, and its cubes are folded away inside the nebula entirely.
 * They burst outward the moment it registers a player, which is the player-facing tell for the
 * whole alertness system: cubes out means it knows. The scare therefore lands on the transition
 * rather than being given away by the silhouette from across a dark cave.
 *
 * Extending [EntityRenderer] rather than LivingEntityRenderer is deliberate: the vanilla living
 * renderer applies a Y-down flip and a model-driven pose pipeline, both of which would have to
 * be undone here.
 *
 * Note that no 1/16 scale is applied anywhere below. ModelPart.Cuboid divides its vertex
 * positions by 16 as it renders, so model units become blocks on their own; multiplying by 1/16
 * here as well shrinks the whole creature by a further factor of sixteen. Every scale call in
 * this class is therefore a plain multiplier on geometry that is already in blocks, and the bone
 * part is authored exactly UNITS_PER_BLOCK tall so its Y scale reads directly as a length.
 */
class ParadoxReaperRenderer(ctx: EntityRendererFactory.Context) :
    LivingEntityRenderer<ParadoxReaperEntity, ParadoxReaperModel<ParadoxReaperEntity>>(
        ctx,
        ParadoxReaperModel<ParadoxReaperEntity>(ctx.getPart(ModEntityModelLayers.PARADOX_REAPER)),
        0.55f
    ) {

    init {
        // A single value covers both variants; EntityRenderer reads this field rather than
        // calling back per entity, so it cannot vary with the tracked variant.
        shadowRadius = 0.55f
    }

    override fun getTexture(entity: ParadoxReaperEntity): Identifier = TEXTURE

    override fun render(
        entity: ParadoxReaperEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int
    ) {
        val rig = rigFor(entity)
        val scale = when {
            entity.isLesser -> LESSER_SCALE
            else -> 1.0
        }

        // The matrix stack sits at the entity's interpolated position, so every world-space
        // point the rig produces has to be rebased against that same interpolated origin.
        val matrixOrigin = Vec3d(
            MathHelper.lerp(tickDelta.toDouble(), entity.prevX, entity.x),
            MathHelper.lerp(tickDelta.toDouble(), entity.prevY, entity.y),
            MathHelper.lerp(tickDelta.toDouble(), entity.prevZ, entity.z)
        )

        val clock = entity.age.toDouble() + tickDelta
        val visualOrigin = rig.smoothVisualOrigin(matrixOrigin, clock)

        // Facing comes from where the creature is actually travelling, not from its yaw.
        // Yaw is a single horizontal angle, which carries almost no information once the body is
        // on a wall: a Reaper climbing straight up has a yaw pointing into the wall, and
        // projecting that onto the wall plane degenerates. The result was a body whose forward
        // axis flipped around arbitrarily, which is what left the legs laid out with one reaching
        // ahead and the rest trailing. Motion is synced already and is unambiguous on any face.
        val up = rig.smoothVisualUp(normalise(entity.renderNormal, UP_FALLBACK), clock)
        val motion = Vec3d(entity.x - entity.prevX, entity.y - entity.prevY, entity.z - entity.prevZ)
        val planarMotion = motion.subtract(up.multiply(motion.dotProduct(up)))

        val travelForward = rig.resolveForward(planarMotion, up, clock)

        // Travel steers future footholds. The rendered body is resolved from the planted stance
        // after the rig advances, so locomotion does not rotate the shell out from under its feet.
        val travelLateral = up.crossProduct(travelForward)

        val contactWorld = contactPoint(entity, visualOrigin, up)
        rig.update(entity, contactWorld, travelForward, travelLateral, up, clock, scale)
        if (rig.birthOrigin == null) rig.birthOrigin = contactWorld

        // Foot placement stays keyed to the true face normal above, because probing straight into
        // a block face is what finds real ground. Everything drawn, though, uses the leaned
        // vector the rig fits through the planted feet, so the body banks over terrain instead of
        // staying rigidly square to whichever of six axes it happens to be clinging to.
        val leanUp = normalise(rig.bodyUp, up)
        val bodyForward = SurfaceCling.projectOntoPlane(rig.bodyForward, up, travelForward)
        val leanForward = SurfaceCling.projectOntoPlane(bodyForward, leanUp, bodyForward)
        val leanLateral = leanUp.crossProduct(leanForward)

        val bodyWorld = contactWorld.add(up.multiply(rig.bodyLift))
        entity.updateMountVisualPose(bodyWorld, leanUp, leanForward)

        val overlay = OverlayTexture.packUv(
            OverlayTexture.getU(0.0f),
            OverlayTexture.getV(entity.hurtTime > 0)
        )

        val ignition = entity.huntGlow
        val glassAlpha = DORMANT_ALPHA + (HUNTING_ALPHA - DORMANT_ALPHA) * ignition
        val glowLevel = DORMANT_GLOW + (HUNTING_GLOW - DORMANT_GLOW) * ignition
        val wounded = entity.hurtTime > 0

        // A freshly summoned lesser form swells out of its rift instead of appearing whole.
        val bodyScale = scale * emergeFactor(entity)

        // Strictly separated passes, and never two live buffers at once. None of these
        // layers is preallocated in BufferBuilderStorage, so an Immediate returns the same
        // fallback BufferBuilder for both and flushes whenever the layer changes. Holding both
        // references and interleaving writes therefore pours one pass's geometry into the
        // other's batch, which is what made the nebula vanish inside an intact shell.
        //
        // Emissive goes first: it writes colour only, so drawing it before the depth-writing
        // shell is what lets the core stay visible through the glass.
        run {
            val nebula = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(NEBULA_TEXTURE))
            renderEtherealAura(
                matrices, nebula, overlay, bodyWorld.subtract(matrixOrigin),
                leanLateral, leanUp, leanForward, bodyScale, clock, ignition, glowLevel
            )
            renderCore(
                matrices, nebula, overlay, bodyWorld.subtract(matrixOrigin),
                leanLateral, leanUp, leanForward, bodyScale, glowLevel, wounded
            )
            renderOrbiters(
                matrices, nebula, overlay, bodyWorld.subtract(matrixOrigin),
                leanLateral, leanUp, leanForward, clock, scale, ignition, glowLevel
            )
            renderArrivalRift(matrices, nebula, overlay, entity, rig, matrixOrigin, leanLateral, leanUp, leanForward, clock, scale)
        }

        run {
            val glow = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(GLOW_TEXTURE))
            renderFaceGlow(
                matrices, glow, overlay, bodyWorld.subtract(matrixOrigin),
                leanLateral, leanUp, leanForward, bodyScale, glowLevel, wounded
            )
            renderSpectralLegs(
                matrices, glow, overlay, rig, bodyWorld, matrixOrigin,
                leanLateral, leanUp, leanForward, ignition, wounded
            )
            renderMotes(
                matrices, glow, overlay, bodyWorld.subtract(matrixOrigin),
                leanLateral, leanUp, leanForward, clock, scale, ignition, glowLevel
            )
        }

        run {
            val glass = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE))
            val glassLight = spectralLight(light)
            renderShell(
                matrices, glass, glassLight, overlay, bodyWorld.subtract(matrixOrigin),
                leanLateral, leanUp, leanForward, bodyScale, glassAlpha, wounded
            )
            renderLegs(matrices, glass, glassLight, overlay, rig, bodyWorld, matrixOrigin, leanLateral, leanUp, leanForward, glassAlpha, wounded)
        }

        // Do not call LivingEntityRenderer here: the live creature has already been drawn by the
        // surface-aligned IK pipeline above. Extending it only exposes the conventional hidden
        // model hierarchy to interoperability tools such as Whackdolls.
    }

    // ---------------------------------------------------------------- body

    /** The saturated suspended nebula. Emissive pass. */
    private fun renderCore(
        matrices: MatrixStack,
        glow: VertexConsumer,
        overlay: Int,
        offset: Vec3d,
        lateral: Vec3d,
        up: Vec3d,
        forward: Vec3d,
        scale: Double,
        glowLevel: Float,
        wounded: Boolean
    ) {
        matrices.push()
        matrices.translate(offset.x, offset.y, offset.z)
        applyBasis(matrices, lateral, up, forward)
        matrices.scale(scale.toFloat(), scale.toFloat(), scale.toFloat())

        val g = glowLevel
        val gg = if (wounded) g else g * 0.85f
        model.core.render(matrices, glow, FULL_BRIGHT, overlay, g, gg, g, 1f)
        model.headCore.render(matrices, glow, FULL_BRIGHT, overlay, g, gg, g, 1f)

        matrices.pop()
    }

    /**
     * Two faint, counter-rotating nebula volumes extend beyond the hard core. Their low-alpha
     * overlap softens the cuboid silhouette and makes the energy appear to churn independently
     * inside the lattice instead of being painted onto one stationary box.
     */
    private fun renderEtherealAura(
        matrices: MatrixStack,
        nebula: VertexConsumer,
        overlay: Int,
        offset: Vec3d,
        lateral: Vec3d,
        up: Vec3d,
        forward: Vec3d,
        scale: Double,
        clock: Double,
        ignition: Float,
        glowLevel: Float
    ) {
        val pulse = 1.0 + Math.sin(clock * 0.075) * 0.055
        val alpha = AURA_DORMANT_ALPHA + (AURA_HUNTING_ALPHA - AURA_DORMANT_ALPHA) * ignition
        for (layer in 0..1) {
            val direction = if (layer == 0) 1.0 else -0.73
            val layerScale = scale * pulse * (1.10 + layer * 0.12)
            matrices.push()
            matrices.translate(offset.x, offset.y, offset.z)
            applyBasis(matrices, lateral, up, forward)
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation((clock * 0.012 * direction).toFloat()))
            matrices.multiply(RotationAxis.POSITIVE_Z.rotation((clock * 0.008 * -direction + layer * 0.55).toFloat()))
            matrices.scale(layerScale.toFloat(), layerScale.toFloat(), layerScale.toFloat())
            val brightness = glowLevel * if (layer == 0) 0.72f else 0.52f
            model.core.render(matrices, nebula, FULL_BRIGHT, overlay, brightness, brightness, 1f, alpha)
            matrices.pop()
        }
    }

    /** Hard eyes and mint-white fang tips laid over the softer nebula pass. */
    private fun renderFaceGlow(
        matrices: MatrixStack,
        glow: VertexConsumer,
        overlay: Int,
        offset: Vec3d,
        lateral: Vec3d,
        up: Vec3d,
        forward: Vec3d,
        scale: Double,
        glowLevel: Float,
        wounded: Boolean
    ) {
        matrices.push()
        matrices.translate(offset.x, offset.y, offset.z)
        applyBasis(matrices, lateral, up, forward)
        matrices.scale(scale.toFloat(), scale.toFloat(), scale.toFloat())

        val g = glowLevel
        val green = if (wounded) g * 0.45f else g
        model.eyes.render(matrices, glow, FULL_BRIGHT, overlay, 0.82f * g, green, g, 1f)
        model.fangTips.forEach { it.render(matrices, glow, FULL_BRIGHT, overlay, 0.62f * g, green, g, 0.92f) }
        matrices.pop()
    }

    /** The lattice cage and head. Glass pass. */
    private fun renderShell(
        matrices: MatrixStack,
        glass: VertexConsumer,
        light: Int,
        overlay: Int,
        offset: Vec3d,
        lateral: Vec3d,
        up: Vec3d,
        forward: Vec3d,
        scale: Double,
        glassAlpha: Float,
        wounded: Boolean
    ) {
        matrices.push()
        matrices.translate(offset.x, offset.y, offset.z)
        applyBasis(matrices, lateral, up, forward)
        matrices.scale(scale.toFloat(), scale.toFloat(), scale.toFloat())

        val red = if (wounded) 1.0f else 0.72f
        val green = if (wounded) 0.42f else 0.94f
        val blue = if (wounded) 0.45f else 1.0f
        model.shell.render(matrices, glass, light, overlay, red, green, blue, glassAlpha)
        model.head.render(matrices, glass, light, overlay, red, green, blue, glassAlpha)
        model.fangs.forEach { it.render(matrices, glass, light, overlay, red, green, blue, glassAlpha * 0.92f) }

        matrices.pop()
    }

    // ---------------------------------------------------------------- legs

    private fun renderLegs(
        matrices: MatrixStack,
        glass: VertexConsumer,
        light: Int,
        overlay: Int,
        rig: ReaperLegRig,
        bodyWorld: Vec3d,
        originWorld: Vec3d,
        lateral: Vec3d,
        up: Vec3d,
        forward: Vec3d,
        glassAlpha: Float,
        wounded: Boolean
    ) {
        val scale = rig.scale
        val red = if (wounded) 1.0f else 0.72f
        val green = if (wounded) 0.45f else 0.94f
        val blue = if (wounded) 0.45f else 1.0f

        for (leg in rig.legs) {
            val hipWorld = bodyWorld
                .add(forward.multiply(leg.hipForward * scale))
                .add(lateral.multiply(leg.hipRight * scale))
                .add(up.multiply(leg.hipUp * scale))

            // The surface normal is the pole, so every bend peaks away from whatever the
            // creature is standing on. That is what keeps the spider profile intact upside down
            // on a ceiling as well as on a floor.
            leg.chain.scale = scale
            leg.chain.solve(hipWorld, leg.foot, up)

            for (segment in 0 until leg.chain.segmentCount) {
                val from = leg.chain.joints[segment].subtract(originWorld)
                val to = leg.chain.joints[segment + 1].subtract(originWorld)
                renderBone(
                    matrices, glass, light, overlay, from, to,
                    scale, SEGMENT_THICKNESS[segment], red, green, blue, glassAlpha
                )
            }
        }
    }

    /** A narrow cyan filament inside each glass leg, visible most strongly while engaged. */
    private fun renderSpectralLegs(
        matrices: MatrixStack,
        glow: VertexConsumer,
        overlay: Int,
        rig: ReaperLegRig,
        bodyWorld: Vec3d,
        originWorld: Vec3d,
        lateral: Vec3d,
        up: Vec3d,
        forward: Vec3d,
        ignition: Float,
        wounded: Boolean
    ) {
        val scale = rig.scale
        val alpha = LEG_GLOW_DORMANT + (LEG_GLOW_HUNTING - LEG_GLOW_DORMANT) * ignition
        val green = if (wounded) 0.38f else 0.86f
        for (leg in rig.legs) {
            val hipWorld = bodyWorld
                .add(forward.multiply(leg.hipForward * scale))
                .add(lateral.multiply(leg.hipRight * scale))
                .add(up.multiply(leg.hipUp * scale))
            leg.chain.scale = scale
            leg.chain.solve(hipWorld, leg.foot, up)
            for (segment in 0 until leg.chain.segmentCount) {
                renderBone(
                    matrices, glow, FULL_BRIGHT, overlay,
                    leg.chain.joints[segment].subtract(originWorld),
                    leg.chain.joints[segment + 1].subtract(originWorld),
                    scale, SEGMENT_THICKNESS[segment] * LEG_GLOW_THICKNESS,
                    0.32f, green, 1.0f, alpha
                )
            }
        }
    }

    /**
     * Draws the reusable bone part stretched between two points.
     *
     * The part is authored one block tall along +Y, so aligning it is a yaw/pitch pair followed
     * by a Y scale equal to the segment length in blocks.
     */
    private fun renderBone(
        matrices: MatrixStack,
        buffer: VertexConsumer,
        light: Int,
        overlay: Int,
        from: Vec3d,
        to: Vec3d,
        scale: Double,
        thickness: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float
    ) {
        val delta = to.subtract(from)
        val length = delta.length()
        if (length < 1.0e-5) return

        val direction = delta.multiply(1.0 / length)
        val horizontal = MathHelper.sqrt((direction.x * direction.x + direction.z * direction.z).toFloat()).toDouble()
        val boneYaw = MathHelper.atan2(direction.x, direction.z).toFloat()
        val bonePitch = MathHelper.atan2(horizontal, direction.y).toFloat()
        val girth = (thickness * scale).toFloat()

        matrices.push()
        matrices.translate(from.x, from.y, from.z)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(boneYaw))
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(bonePitch))
        matrices.scale(girth, length.toFloat(), girth)
        model.bone.render(matrices, buffer, light, overlay, red, green, blue, alpha)
        matrices.pop()
    }

    // ---------------------------------------------------------------- orbiting cubes

    /**
     * The drifting cubes from the design. Two counter-rotating, counter-tilted rings, so from
     * most angles some cubes cross in front of the body and some behind it, which reads as a
     * shell of debris rather than a flat halo. They sit in tight and dim while the Reaper is
     * dormant, then swing wide and bright once it has a target.
     */
    private fun renderOrbiters(
        matrices: MatrixStack,
        glow: VertexConsumer,
        overlay: Int,
        bodyOffset: Vec3d,
        lateral: Vec3d,
        up: Vec3d,
        forward: Vec3d,
        clock: Double,
        scale: Double,
        ignition: Float,
        glowLevel: Float
    ) {
        // Dormant, the cubes are folded inside the nebula and invisible. Waking flings them out
        // past their resting orbit before they settle, so the tell reads as a burst rather than
        // as a fade-in. This is the single clearest signal that a Reaper has noticed you.
        val emergence = easeOutBack(ignition)
        if (emergence <= 1.0e-3) return

        val radius = HUNTING_ORBIT * emergence * scale
        val spin = clock * ORBIT_SPEED

        for (index in 0 until ORBITER_COUNT) {
            val ring = index % 2
            val phase = spin * (if (ring == 0) 1.0 else -0.78) + index * (TAU / ORBITER_COUNT)
            val tilt = if (ring == 0) 0.38 else -0.62

            // Ring plane: lateral tipped toward the surface normal, crossed with forward.
            val axisA = lateral.multiply(Math.cos(tilt)).add(up.multiply(Math.sin(tilt))).normalize()
            val bob = Math.sin(clock * 0.09 + index) * 0.10 * scale

            val offset = axisA.multiply(Math.cos(phase) * radius)
                .add(forward.multiply(Math.sin(phase) * radius))
                .add(up.multiply(bob))

            matrices.push()
            matrices.translate(bodyOffset.x + offset.x, bodyOffset.y + offset.y, bodyOffset.z + offset.z)
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation((phase * 1.7).toFloat()))
            matrices.multiply(RotationAxis.POSITIVE_X.rotation((phase * 1.1).toFloat()))
            val cube = (scale * emergence).toFloat()
            matrices.scale(cube, cube, cube)
            model.orbiter.render(matrices, glow, FULL_BRIGHT, overlay, glowLevel, glowLevel, glowLevel, 1f)
            matrices.pop()
        }
    }

    /**
     * Tiny star fragments that never fully disappear. Unlike the large alert orbiters these stay
     * close and faint while dormant, giving the silhouette a slow spectral shimmer without
     * spoiling the dramatic cube burst when the Reaper notices a player.
     */
    private fun renderMotes(
        matrices: MatrixStack,
        glow: VertexConsumer,
        overlay: Int,
        bodyOffset: Vec3d,
        lateral: Vec3d,
        up: Vec3d,
        forward: Vec3d,
        clock: Double,
        scale: Double,
        ignition: Float,
        glowLevel: Float
    ) {
        val spread = (MOTE_DORMANT_RADIUS + (MOTE_HUNTING_RADIUS - MOTE_DORMANT_RADIUS) * ignition) * scale
        for (index in 0 until MOTE_COUNT) {
            val seed = index * 2.399963229728653
            val drift = clock * (0.010 + (index % 4) * 0.0025)
            val phase = seed + drift * if (index % 2 == 0) 1.0 else -1.0
            val radial = spread * (0.58 + (index % 5) * 0.105)
            val lift = Math.sin(seed * 1.7 + clock * 0.021) * spread * 0.62
            val offset = lateral.multiply(Math.cos(phase) * radial)
                .add(forward.multiply(Math.sin(phase) * radial))
                .add(up.multiply(lift))
            val twinkle = (0.55 + 0.45 * Math.sin(clock * 0.19 + seed)).toFloat()
            val moteScale = (scale * (0.20 + (index % 3) * 0.07) * (0.82 + ignition * 0.25)).toFloat()

            matrices.push()
            matrices.translate(bodyOffset.x + offset.x, bodyOffset.y + offset.y, bodyOffset.z + offset.z)
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation((phase * 1.9).toFloat()))
            matrices.scale(moteScale, moteScale, moteScale)
            model.orbiter.render(
                matrices, glow, FULL_BRIGHT, overlay,
                glowLevel * 0.62f, glowLevel * 0.92f, glowLevel, MOTE_ALPHA * twinkle
            )
            matrices.pop()
        }
    }

    // ---------------------------------------------------------------- arrival rift

    /**
     * The nebula rift a summoned lesser form drops out of. It anchors to where the creature first
     * appeared rather than to the creature, so the Reaper visibly falls away from it, and it
     * irises open then collapses across [ParadoxReaperEntity.PORTAL_TICKS].
     */
    private fun renderArrivalRift(
        matrices: MatrixStack,
        glow: VertexConsumer,
        overlay: Int,
        entity: ParadoxReaperEntity,
        rig: ReaperLegRig,
        originWorld: Vec3d,
        lateral: Vec3d,
        up: Vec3d,
        forward: Vec3d,
        clock: Double,
        scale: Double
    ) {
        if (!entity.isLesser) return
        val life = entity.age.toDouble()
        if (life > ParadoxReaperEntity.PORTAL_TICKS) return

        val birth = rig.birthOrigin ?: return
        val progress = (life / ParadoxReaperEntity.PORTAL_TICKS).coerceIn(0.0, 1.0)
        val openness = Math.sin(progress * Math.PI)
        if (openness < 1.0e-3) return

        val riftScale = (RIFT_SCALE * openness * scale).toFloat()
        val offset = birth.add(up.multiply(0.4 * scale)).subtract(originWorld)

        matrices.push()
        matrices.translate(offset.x, offset.y, offset.z)
        applyBasis(matrices, lateral, up, forward)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation((clock * 0.18).toFloat()))
        matrices.scale(riftScale, riftScale, riftScale)
        model.core.render(matrices, glow, FULL_BRIGHT, overlay, 1f, 0.9f, 1f, 1f)
        matrices.pop()
    }

    // ---------------------------------------------------------------- helpers

    /** Overshoots slightly past 1 before settling, so the cubes visibly burst outward. */
    private fun easeOutBack(t: Float): Double {
        val x = t.toDouble().coerceIn(0.0, 1.0)
        val c1 = 1.70158
        val c3 = c1 + 1.0
        val d = x - 1.0
        return 1.0 + c3 * d * d * d + c1 * d * d
    }

    /** Swell from a fraction of full size as a summon clears its rift. */
    private fun emergeFactor(entity: ParadoxReaperEntity): Double {
        if (!entity.isLesser) return 1.0
        val life = entity.age.toDouble()
        if (life >= ParadoxReaperEntity.PORTAL_TICKS) return 1.0
        val progress = (life / ParadoxReaperEntity.PORTAL_TICKS).coerceIn(0.0, 1.0)
        return EMERGE_START + (1.0 - EMERGE_START) * progress
    }

    /**
     * Applies a rotation whose columns are the supplied axes. Both the position and normal
     * matrices are updated; skipping the normal matrix leaves lighting stuck in world
     * orientation, which is glaring on a creature that spends its time upside down.
     */
    private fun applyBasis(matrices: MatrixStack, lateral: Vec3d, up: Vec3d, forward: Vec3d) {
        val basis = Matrix4f(
            lateral.x.toFloat(), lateral.y.toFloat(), lateral.z.toFloat(), 0f,
            up.x.toFloat(), up.y.toFloat(), up.z.toFloat(), 0f,
            forward.x.toFloat(), forward.y.toFloat(), forward.z.toFloat(), 0f,
            0f, 0f, 0f, 1f
        )
        val entry = matrices.peek()
        entry.positionMatrix.mul(basis)
        entry.normalMatrix.mul(Matrix3f(basis))
    }

    /**
     * The point on the clung surface directly beneath the creature.
     *
     * The hitbox extends from the entity position along world +Y regardless of orientation, so
     * on a ceiling the contact face is the top of the box and on a wall it is one of the sides.
     */
    private fun contactPoint(entity: ParadoxReaperEntity, originWorld: Vec3d, up: Vec3d): Vec3d {
        val centre = originWorld.add(0.0, entity.height / 2.0, 0.0)
        val extent = if (kotlin.math.abs(up.y) > 0.5) entity.height / 2.0 else entity.width / 2.0
        return centre.subtract(up.multiply(extent))
    }

    /**
     * Raises the block-light component to a floor so the glass never renders as flat black.
     *
     * The shell and limbs are meant to read as lit from within, and in an unlit place they were
     * coming out as solid black bars rather than faint glass.
     */
    private fun spectralLight(light: Int): Int {
        val block = LightmapTextureManager.getBlockLightCoordinates(light)
        val sky = LightmapTextureManager.getSkyLightCoordinates(light)
        return LightmapTextureManager.pack(Math.max(block, MIN_BLOCK_LIGHT), sky)
    }

    private fun normalise(vector: Vec3d, fallback: Vec3d): Vec3d =
        if (vector.lengthSquared() > 1.0e-6) vector.normalize() else fallback

    /** Renderers are shared between entities; the rig is not, so it lives on the entity. */
    private fun rigFor(entity: ParadoxReaperEntity): ReaperLegRig {
        val existing = entity.legRigCache as? ReaperLegRig
        if (existing != null) return existing
        val created = ReaperLegRig()
        entity.legRigCache = created
        return created
    }

    private companion object {
        val TEXTURE = Identifier("mystcraft-reforged", "textures/entity/paradox_reaper.png")
        val GLOW_TEXTURE = Identifier("mystcraft-reforged", "textures/entity/paradox_reaper_glow.png")
        val NEBULA_TEXTURE = Identifier("mystcraft-reforged", "textures/entity/paradox_reaper_nebula_v2.png")
        val UP_FALLBACK = Vec3d(0.0, 1.0, 0.0)

        const val FULL_BRIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE

        /** Floor on the glass pass's block light, so the body is never a black silhouette. */
        const val MIN_BLOCK_LIGHT = 7

        const val LESSER_SCALE = 0.5

        /** Femur, tibia, tarsus: the limb tapers to a needle point at the foot. */
        val SEGMENT_THICKNESS = floatArrayOf(0.85f, 0.58f, 0.34f)

        const val DORMANT_ALPHA = 0.36f
        const val HUNTING_ALPHA = 0.70f
        // Dormant means non-emissive. The minimum-lit translucent shell is still faintly
        // readable as wet glass, but the nebula, eyes, fangs, motes, and cubes no longer glow.
        const val DORMANT_GLOW = 0.0f
        const val HUNTING_GLOW = 1.0f

        const val AURA_DORMANT_ALPHA = 0.10f
        const val AURA_HUNTING_ALPHA = 0.27f
        const val LEG_GLOW_DORMANT = 0.0f
        const val LEG_GLOW_HUNTING = 0.58f
        const val LEG_GLOW_THICKNESS = 0.42f

        const val ORBITER_COUNT = 8
        /** Cubes live inside the nebula while dormant; only HUNTING_ORBIT is a real radius. */
        const val DORMANT_ORBIT = 0.0
        const val HUNTING_ORBIT = 1.25
        const val ORBIT_SPEED = 0.055
        const val TAU = Math.PI * 2.0

        const val MOTE_COUNT = 14
        const val MOTE_DORMANT_RADIUS = 0.72
        const val MOTE_HUNTING_RADIUS = 1.72
        const val MOTE_ALPHA = 0.72f

        const val RIFT_SCALE = 2.6
        const val EMERGE_START = 0.45
    }
}
