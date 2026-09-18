package mystcraft.flood.client.render

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.entity.LittleAnomalyEntity
import mystcraft.flood.entity.ModEntities
import mystcraft.flood.entity.ParadoxReaperEntity
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.LivingEntityRenderer
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier
import java.util.WeakHashMap

/**
 * Reuses the original lesser-Reaper renderer exactly while the real gameplay entity remains a
 * tame, non-hostile [LittleAnomalyEntity]. The proxy is client-only and never enters the world;
 * it only carries the movement and render state expected by the procedural planted-foot rig.
 */
class LittleAnomalyRenderer(context: EntityRendererFactory.Context) :
    LivingEntityRenderer<LittleAnomalyEntity, ParadoxReaperModel<LittleAnomalyEntity>>(
        context,
        ParadoxReaperModel<LittleAnomalyEntity>(context.getPart(ModEntityModelLayers.PARADOX_REAPER)),
        FAMILIAR_SHADOW_RADIUS
    ) {

    private val originalRenderer = ParadoxReaperRenderer(context)
    private val proxies = WeakHashMap<LittleAnomalyEntity, ParadoxReaperEntity>()

    override fun getTexture(entity: LittleAnomalyEntity): Identifier = TEXTURE

    override fun render(
        entity: LittleAnomalyEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        light: Int
    ) {
        val proxy = proxies.getOrPut(entity) {
            ParadoxReaperEntity(ModEntities.PARADOX_REAPER, entity.world).also {
                it.applyVariant(true)
                it.setFriendlyRenderAirborne()
            }
        }

        proxy.refreshPositionAndAngles(entity.x, entity.y, entity.z, entity.yaw, entity.pitch)
        proxy.prevX = entity.prevX
        proxy.prevY = entity.prevY
        proxy.prevZ = entity.prevZ
        proxy.prevYaw = entity.prevYaw
        proxy.prevPitch = entity.prevPitch
        proxy.bodyYaw = entity.bodyYaw
        proxy.prevBodyYaw = entity.prevBodyYaw
        proxy.headYaw = entity.headYaw
        proxy.prevHeadYaw = entity.prevHeadYaw
        proxy.velocity = entity.velocity
        proxy.age = entity.age
        proxy.hurtTime = entity.hurtTime
        proxy.deathTime = entity.deathTime
        proxy.setFriendlyRenderAirborne()

        val engaged = !entity.isSitting && (
            entity.velocity.lengthSquared() > 0.001 || entity.hurtTime > 0 || entity.target != null
        )
        proxy.setRenderEngagement(if (engaged) 1.0f else 0.0f)

        // The familiar used to render at the full lesser-Reaper size, which lets a companion
        // hovering near the camera occupy much too much of the view. Uniformly scale the entire
        // procedural rig around its entity origin so the body, planted feet, effects, and arrival
        // rift retain exactly the same proportions.
        matrices.push()
        matrices.scale(FAMILIAR_RENDER_SCALE, FAMILIAR_RENDER_SCALE, FAMILIAR_RENDER_SCALE)
        originalRenderer.render(proxy, yaw, tickDelta, matrices, consumers, light)
        matrices.pop()
    }

    private companion object {
        val TEXTURE = Identifier(MystcraftReforged.MOD_ID, "textures/entity/paradox_reaper.png")
        // ParadoxReaperRenderer applies its 0.5 lesser scale after this, for a final 0.14 scale.
        const val FAMILIAR_RENDER_SCALE = 0.28f
        const val FAMILIAR_SHADOW_RADIUS = 0.07f
    }
}
