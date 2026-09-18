package mystcraft.flood.entity

import net.minecraft.util.math.Vec3d
import kotlin.math.abs

data class ReaperMountMotion(val direction: Vec3d, val throttle: Double)

/** Shared mount rules for both sizes of permanently bound Reaper. */
object ReaperMounting {
    private const val SEAT_HEIGHT_RATIO = 0.78

    /**
     * How far above the drawn body's centre a rider sits, in blocks at greater proportions.
     *
     * The shell reaches roughly 0.41 blocks above that centre, so this seats the rider a little
     * way down into the lattice rather than perched on top of it. Sitting exactly on the surface
     * leaves a visible gap, because a mounted player is drawn from their feet and their sitting
     * pose puts the body above that point.
     */
    private const val SEAT_ABOVE_BODY = 0.24

    fun canOwnerMount(permanentCompanion: Boolean, ownerMatches: Boolean, occupied: Boolean): Boolean =
        permanentCompanion && ownerMatches && !occupied

    /**
     * Fallback seat height, measured from the creature's own origin.
     *
     * Used only where the drawn pose is unavailable — on the server, and on the first frame after
     * a rider mounts. It cannot be right in general: the rendered body rides a solved leg stance
     * whose height varies with the ground, so a fixed fraction of the hitbox floats free of the
     * shell as soon as the creature crosses anything uneven. See [seatOnBody].
     */
    fun seatOffset(entityHeight: Float): Double = entityHeight * SEAT_HEIGHT_RATIO

    /**
     * The seat position on a body that has actually been drawn.
     *
     * [bodyCentre] is the centre of the rendered shell and [up] the axis it is drawn against, so
     * this lands the rider on the creature on a wall or a ceiling exactly as it does on a floor.
     */
    fun seatOnBody(bodyCentre: Vec3d, up: Vec3d, scale: Double): Vec3d =
        bodyCentre.add(up.multiply(SEAT_ABOVE_BODY * scale))
}

/** Converts ordinary rider input into motion tangent to whichever surface the Reaper occupies. */
object ReaperMountSteering {
    fun resolve(
        view: Vec3d,
        up: Vec3d,
        fallbackForward: Vec3d,
        forwardInput: Float,
        sidewaysInput: Float
    ): ReaperMountMotion {
        if (abs(forwardInput) < 1.0e-3f && abs(sidewaysInput) < 1.0e-3f) {
            return ReaperMountMotion(Vec3d.ZERO, 0.0)
        }

        val forward = SurfaceCling.projectOntoPlane(view, up, fallbackForward)
        val side = up.crossProduct(forward).normalize()
        var motion = forward.multiply(forwardInput.toDouble()).add(side.multiply(sidewaysInput.toDouble()))
        val magnitude = motion.length().coerceAtMost(1.0)
        if (magnitude < 1.0e-6) return ReaperMountMotion(Vec3d.ZERO, 0.0)

        // Backing a creature this large is deliberate rather than a full-speed reversal.
        val reverseFactor = if (forwardInput < 0.0f) 0.58 else 1.0
        motion = motion.normalize()
        return ReaperMountMotion(motion, magnitude * reverseFactor)
    }
}
