package mystcraft.flood.generation.physics

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.Vec3d
import kotlin.math.abs

object LowGravityPhysics {
    private const val VANILLA_GRAVITY_PER_TICK = 0.08
    private const val VANILLA_AIR_DRAG = 0.91
    private const val VANILLA_AIR_CONTROL = 0.02f
    private const val FANTASY_AIR_DRAG_AT_MIN_GRAVITY = 0.985
    private const val EXTRA_AIR_CONTROL_AT_MIN_GRAVITY = 0.18f

    fun apply(player: PlayerEntity, gravityScale: Float) {
        if (gravityScale >= 0.999f) return
        if (player.isCreative || player.isSpectator || player.isOnGround) return
        if (player.abilities.flying || player.isFallFlying || player.isClimbing || player.hasVehicle()) return
        if (player.isTouchingWater || player.isSubmergedInWater || player.isInLava) return

        applyAirControl(player, gravityScale)

        val currentVelocity = player.velocity
        val gravityCompensation = VANILLA_GRAVITY_PER_TICK * (1.0 - gravityScale.toDouble())
        val adjustedY = currentVelocity.y + gravityCompensation

        // Reduce air drag without turning it into thrust. This keeps jumps arcing
        // forward in low gravity instead of stalling out mid-hop.
        val targetHorizontalDrag = lerp(
            FANTASY_AIR_DRAG_AT_MIN_GRAVITY,
            VANILLA_AIR_DRAG,
            gravityScale.toDouble()
        )
        val horizontalMultiplier = targetHorizontalDrag / VANILLA_AIR_DRAG
        val adjustedX = currentVelocity.x * horizontalMultiplier
        val adjustedZ = currentVelocity.z * horizontalMultiplier

        player.velocity = Vec3d(adjustedX, adjustedY, adjustedZ)
        player.velocityModified = true

        if (adjustedY < 0.0) {
            player.fallDistance *= gravityScale
        }
    }

    fun applyAfterVanilla(player: PlayerEntity, gravityScale: Float, previousVelocity: Vec3d) {
        if (gravityScale >= 0.999f) return
        if (player.isCreative || player.isSpectator || player.isOnGround) return
        if (player.abilities.flying || player.isFallFlying || player.isClimbing || player.hasVehicle()) return
        if (player.isTouchingWater || player.isSubmergedInWater || player.isInLava) return

        val gravityCompensation = VANILLA_GRAVITY_PER_TICK * (1.0 - gravityScale.toDouble())
        val currentVelocity = player.velocity

        // Galacticraft feels smooth because it reconciles against pre-vanilla motion
        // instead of piling another correction on top after the frame is already over.
        player.velocity = Vec3d(
            previousVelocity.x + (currentVelocity.x - previousVelocity.x) * 0.15,
            previousVelocity.y + gravityCompensation,
            previousVelocity.z + (currentVelocity.z - previousVelocity.z) * 0.15
        )

        applyAirControl(player, gravityScale)

        val drag = lerp(
            FANTASY_AIR_DRAG_AT_MIN_GRAVITY,
            VANILLA_AIR_DRAG,
            gravityScale.toDouble()
        )
        val adjusted = player.velocity
        player.velocity = Vec3d(adjusted.x * drag, adjusted.y, adjusted.z * drag)
        player.velocityModified = true

        if (player.velocity.y < 0.0) {
            player.fallDistance *= gravityScale
        }
    }

    private fun applyAirControl(player: PlayerEntity, gravityScale: Float) {
        val sidewaysInput = player.sidewaysSpeed
        val forwardInput = player.forwardSpeed
        if (abs(sidewaysInput) < 0.001f && abs(forwardInput) < 0.001f) return

        val controlScale = ((1.0f - gravityScale) / 0.55f).coerceIn(0.0f, 1.25f)
        val extraAirControl = EXTRA_AIR_CONTROL_AT_MIN_GRAVITY * controlScale
        if (extraAirControl <= VANILLA_AIR_CONTROL * 0.25f) return

        player.updateVelocity(extraAirControl, Vec3d(sidewaysInput.toDouble(), 0.0, forwardInput.toDouble()))
    }

    private fun lerp(start: Double, end: Double, delta: Double): Double =
        start + (end - start) * delta.coerceIn(0.0, 1.0)
}
