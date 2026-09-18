package mystcraft.flood.client.render

import mystcraft.flood.entity.ParadoxReaperEntity
import mystcraft.flood.entity.ReaperSmoothing
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.util.hit.HitResult
import net.minecraft.world.RaycastContext

/**
 * Where a rider's eye actually goes, and how it gets there.
 *
 * Two problems live here, and they pull in opposite directions. The drawn body is carried by six
 * legs taking turns, so it wobbles at gait frequency — pleasant to watch from outside and unpleasant
 * to have your head bolted to. And a creature that walks on ceilings routinely puts its back, and
 * therefore its rider's eye, inside a block.
 *
 * Kept out of the mixin so the arithmetic can be read, changed, and reasoned about on its own.
 */
object ReaperMountCamera {

    private val offsetFilter = ReaperSmoothing.DampedVector(Vec3d.ZERO)
    private var lastClock = Double.NaN
    private var lastOccludedTime = Long.MIN_VALUE
    private var occluding = false

    /** True while the view is being held out in front of the creature rather than on its back. */
    val isDisplaced: Boolean get() = occluding

    @JvmStatic
    fun forget() {
        offsetFilter.reset(Vec3d.ZERO)
        lastClock = Double.NaN
        lastOccludedTime = Long.MIN_VALUE
        occluding = false
    }

    /**
     * The eye position for this frame.
     *
     * @param anchor where the eye belongs on the drawn body.
     * @param reference the same point derived from the creature's raw position instead of its
     *   drawn pose. Only the difference between the two is filtered, which is the whole trick:
     *   that difference is the procedural wobble and nothing else, so smoothing it leaves the
     *   creature's real travel — the part that answers to the player's own input — completely
     *   untouched. Filtering the final position instead would put lag on steering as well.
     */
    @JvmStatic
    fun resolve(
        reaper: ParadoxReaperEntity,
        anchor: Vec3d,
        reference: Vec3d,
        bodyUp: Vec3d,
        bodyForward: Vec3d,
        clock: Double
    ): Vec3d {
        val delta = advanceClock(clock)

        val smoothedAnchor = if (delta <= 0.0) {
            reference.add(offsetFilter.value)
        } else {
            val offset = anchor.subtract(reference)
            val omega = ReaperSmoothing.frequencyForHalfLife(OFFSET_HALF_LIFE_TICKS)
            reference.add(offsetFilter.advance(offset, omega, delta))
        }

        return applyOcclusionFallback(reaper, smoothedAnchor, bodyUp, bodyForward)
    }

    private fun advanceClock(clock: Double): Double {
        if (!lastClock.isFinite()) {
            lastClock = clock
            return 0.0
        }
        val delta = (clock - lastClock).coerceIn(0.0, 4.0)
        lastClock = clock
        return delta
    }

    /**
     * Moves the eye out in front of the creature's face whenever its usual seat is inside a block,
     * and keeps it there for a moment afterwards.
     *
     * The hold is the important half. Whether a point a hand's width from a wall is inside that
     * wall changes with every footfall, so a view that switched the instant the test flipped would
     * strobe between two positions several times a second — far worse than either one. Waiting out
     * a fixed period after the last obstruction means the creature has to genuinely leave the
     * tight spot before the view comes back.
     */
    private fun applyOcclusionFallback(
        reaper: ParadoxReaperEntity,
        anchor: Vec3d,
        bodyUp: Vec3d,
        bodyForward: Vec3d
    ): Vec3d {
        val now = reaper.world.time
        if (isInsideBlock(reaper, anchor)) {
            lastOccludedTime = now
            occluding = true
        } else if (occluding && now - lastOccludedTime >= HOLD_TICKS) {
            occluding = false
        }
        if (!occluding) return anchor

        // Out past the face and a little clear of the shell, then pulled back to whatever is
        // actually reachable so the fallback cannot bury itself in the next surface along.
        val body = reaper.mountVisualBody
        val wanted = body
            .add(bodyForward.multiply(FACE_FORWARD))
            .add(bodyUp.multiply(FACE_LIFT))
        return clipToOpenSpace(reaper, body, wanted)
    }

    /** Whether [point] sits inside collision geometry, which is what makes a view see through it. */
    private fun isInsideBlock(reaper: ParadoxReaperEntity, point: Vec3d): Boolean {
        val probe = Box(
            point.x - EYE_RADIUS, point.y - EYE_RADIUS, point.z - EYE_RADIUS,
            point.x + EYE_RADIUS, point.y + EYE_RADIUS, point.z + EYE_RADIUS
        )
        return !reaper.world.isSpaceEmpty(probe)
    }

    /** Pulls [wanted] back along the line from [from] until it is out of the walls. */
    private fun clipToOpenSpace(reaper: ParadoxReaperEntity, from: Vec3d, wanted: Vec3d): Vec3d {
        val hit = reaper.world.raycast(
            RaycastContext(
                from, wanted,
                RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, reaper
            )
        )
        if (hit.type != HitResult.Type.BLOCK) return wanted

        val toHit = hit.pos.subtract(from)
        val distance = toHit.length()
        if (distance <= CLIP_MARGIN) return from
        return from.add(toHit.multiply((distance - CLIP_MARGIN) / distance))
    }

    /**
     * Time constant for the wobble filter.
     *
     * Short enough that the head still reads as attached to the creature rather than trailing it,
     * long enough to sit well below the gait's own frequency, which is what the filter is for.
     */
    private const val OFFSET_HALF_LIFE_TICKS = 2.8

    /** Half a second, as asked for: long enough that a footfall cannot flip the view back. */
    private const val HOLD_TICKS = 10L

    /** How far in front of the face, and how far off the shell, the displaced view sits. */
    private const val FACE_FORWARD = 1.15
    private const val FACE_LIFT = 0.25

    /** Treated as the eye's own size when asking whether it is buried. */
    private const val EYE_RADIUS = 0.08

    /** Kept off any surface the fallback runs into. */
    private const val CLIP_MARGIN = 0.25
}
