package mystcraft.flood.entity

import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

/**
 * Adhesion math for creatures that treat any solid face as a floor.
 *
 * A clinging creature carries its own "up" vector: the normal of whatever surface it is
 * standing on. Standing on ground means up is [Direction.UP]; hanging from a ceiling means up is
 * [Direction.DOWN]; on a wall it points horizontally out of that wall. Every other system here
 * (locomotion, yaw, leg placement) is written against that vector rather than against world Y,
 * which is what lets one code path walk floors, walls, and ceilings without special cases.
 *
 * Support is detected by pushing the creature's own bounding box a short distance along -up and
 * asking whether that space is occupied. Using the real box rather than a point means overhangs
 * and partial blocks behave the way the collision system already says they should.
 */
object SurfaceCling {

    /** How far to push the hitbox looking for a surface. Slightly less than half a block. */
    const val PROBE_DISTANCE = 0.34

    /** Search order is stable so a creature does not jitter between two equally valid faces. */
    private val SEARCH_ORDER = arrayOf(
        Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN
    )

    fun vectorOf(face: Direction): Vec3d = Vec3d.of(face.vector)

    /**
     * Finds a face to cling to, or null when the creature is in open air.
     *
     * [preferred] is checked first and wins ties. That hysteresis matters: without it a creature
     * in an inside corner flips between the floor and the wall every tick, and the legs jitter.
     */
    fun findSupport(world: World, box: Box, preferred: Direction?): Direction? {
        if (preferred != null && hasSurface(world, box, preferred)) return preferred
        for (face in SEARCH_ORDER) {
            if (face == preferred) continue
            if (hasSurface(world, box, face)) return face
        }
        return null
    }

    /**
     * The face of the nearest surface within [maxDistance], or null in genuinely open air.
     *
     * Unlike [findSupport] this looks well beyond gripping range. It exists so an airborne
     * creature can reach for something to land on: dropped into open air near a ceiling, a
     * Reaper would otherwise simply fall, because nothing was ever within the short probe that
     * [findSupport] uses to confirm a grip.
     */
    fun findNearbySupport(world: World, box: Box, maxDistance: Double): Direction? {
        val steps = Math.max(1, Math.ceil(maxDistance / PROBE_DISTANCE).toInt())
        for (step in 1..steps) {
            val distance = Math.min(maxDistance, step * PROBE_DISTANCE)
            for (face in SEARCH_ORDER) {
                val toSurface = vectorOf(face).multiply(-distance)
                if (!world.isSpaceEmpty(box.offset(toSurface))) return face
            }
        }
        return null
    }

    /** True when solid geometry sits on the -[face] side of [box], i.e. [face] is a valid "up". */
    fun hasSurface(world: World, box: Box, face: Direction): Boolean {
        val toSurface = vectorOf(face).multiply(-PROBE_DISTANCE)
        return !world.isSpaceEmpty(box.offset(toSurface))
    }

    /**
     * Builds the creature's forward direction inside the plane of the surface it clings to.
     *
     * Yaw stays the authoritative facing because vanilla already syncs it. Projecting the yaw
     * vector onto the surface plane keeps facing continuous as the creature rounds an edge. The
     * projection degenerates only when yaw points straight along the normal (e.g. facing "into"
     * a wall the creature is standing on), so [fallback] supplies a stable direction there.
     */
    fun forwardOnSurface(up: Vec3d, yawDegrees: Float, fallback: Vec3d): Vec3d {
        val yawRadians = yawDegrees * MathHelper.RADIANS_PER_DEGREE
        val facing = Vec3d(-MathHelper.sin(yawRadians).toDouble(), 0.0, MathHelper.cos(yawRadians).toDouble())
        return projectOntoPlane(facing, up, fallback)
    }

    /** Removes the [up] component of [vector], renormalising; returns [fallback] if nothing remains. */
    fun projectOntoPlane(vector: Vec3d, up: Vec3d, fallback: Vec3d): Vec3d {
        val flattened = vector.subtract(up.multiply(vector.dotProduct(up)))
        if (flattened.lengthSquared() > 1.0e-6) return flattened.normalize()

        val flattenedFallback = fallback.subtract(up.multiply(fallback.dotProduct(up)))
        if (flattenedFallback.lengthSquared() > 1.0e-6) return flattenedFallback.normalize()

        // Both candidates were parallel to the normal; any perpendicular axis will do.
        val seed = if (kotlin.math.abs(up.y) > 0.9) Vec3d(1.0, 0.0, 0.0) else Vec3d(0.0, 1.0, 0.0)
        return seed.subtract(up.multiply(seed.dotProduct(up))).normalize()
    }

    /**
     * Yaw, in degrees, that reproduces [forward] once projected back onto the plane of [up].
     *
     * Locomotion steers in world space, but the entity still stores a scalar yaw, so steering
     * output has to be converted back. On near-vertical surfaces the horizontal component of
     * forward can vanish, so the surface's own tilt is used to keep a usable heading.
     */
    fun yawFor(forward: Vec3d, up: Vec3d): Float {
        var heading = forward
        if (heading.x * heading.x + heading.z * heading.z < 1.0e-6) {
            // Facing straight up or down a wall: borrow the direction the surface leans.
            heading = up.multiply(-1.0)
            if (heading.x * heading.x + heading.z * heading.z < 1.0e-6) return 0.0f
        }
        return (MathHelper.atan2(-heading.x, heading.z) * MathHelper.DEGREES_PER_RADIAN).toFloat()
    }

    /**
     * Turns [current] toward [target] by at most [maxRadians], at a constant angular rate.
     *
     * This rotates about the axis between the two vectors rather than interpolating between them
     * componentwise. A plain lerp looks fine at ninety degrees but fails outright at a hundred
     * and eighty: the midpoint of two opposed vectors is the origin, which renormalises straight
     * back to the start and leaves the value stuck at [current] permanently. For a clinging
     * creature that case is reachable in ordinary play — dropping off a ledge onto the underside
     * of an overhang goes floor to ceiling with no wall in between — and the creature would
     * render the wrong way up for the rest of its life.
     */
    @JvmStatic
    fun rotateToward(current: Vec3d, target: Vec3d, maxRadians: Double): Vec3d {
        val dot = current.dotProduct(target).coerceIn(-1.0, 1.0)
        val angle = kotlin.math.acos(dot)
        if (angle < 1.0e-4) return target

        var axis = current.crossProduct(target)
        if (axis.lengthSquared() < 1.0e-8) {
            // Exactly opposed, so every axis turns us there; pick one deterministically.
            val seed = if (kotlin.math.abs(current.y) > 0.9) Vec3d(1.0, 0.0, 0.0) else Vec3d(0.0, 1.0, 0.0)
            axis = current.crossProduct(seed)
        }
        axis = axis.normalize()

        return rotateAround(current, axis, kotlin.math.min(angle, maxRadians)).normalize()
    }

    /** Rodrigues rotation of [vector] about a unit [axis]. */
    @JvmStatic
    fun rotateAround(vector: Vec3d, axis: Vec3d, radians: Double): Vec3d {
        val cos = kotlin.math.cos(radians)
        val sin = kotlin.math.sin(radians)
        return vector.multiply(cos)
            .add(axis.crossProduct(vector).multiply(sin))
            .add(axis.multiply(axis.dotProduct(vector) * (1.0 - cos)))
    }

    /**
     * Carries a walking direction across a corner while rotating [fromNormal] onto [toNormal].
     *
     * Direct target steering degenerates at exactly the important moment: after a floor crawler
     * meets a wall, a target beyond the wall points straight into the new surface and projects to
     * zero. Transporting the old heading through the same quarter-turn makes it continue upward;
     * the inverse transition carries it over a ledge and down the outside face.
     */
    fun transportDirection(direction: Vec3d, fromNormal: Vec3d, toNormal: Vec3d): Vec3d {
        val from = fromNormal.normalize()
        val to = toNormal.normalize()
        val dot = from.dotProduct(to).coerceIn(-1.0, 1.0)
        if (dot > 1.0 - 1.0e-8) return projectOntoPlane(direction, to, direction)

        var axis = from.crossProduct(to)
        if (axis.lengthSquared() < 1.0e-8) {
            val seed = if (kotlin.math.abs(from.y) > 0.9) Vec3d(1.0, 0.0, 0.0) else Vec3d(0.0, 1.0, 0.0)
            axis = from.crossProduct(seed)
        }
        val rotated = rotateAround(direction, axis.normalize(), kotlin.math.acos(dot))
        return projectOntoPlane(rotated, to, direction)
    }

    /** Axis-aligned face that [vector] points most strongly along. */
    fun dominantFace(vector: Vec3d): Direction {
        val ax = kotlin.math.abs(vector.x)
        val ay = kotlin.math.abs(vector.y)
        val az = kotlin.math.abs(vector.z)
        return when {
            ax >= ay && ax >= az -> if (vector.x > 0) Direction.EAST else Direction.WEST
            ay >= az -> if (vector.y > 0) Direction.UP else Direction.DOWN
            else -> if (vector.z > 0) Direction.SOUTH else Direction.NORTH
        }
    }

    /** Surface normal entered at an inside corner while travelling along [current]. */
    fun concaveTransitionFace(current: Direction, motion: Vec3d): Direction? {
        if (motion.lengthSquared() < 1.0e-6) return null
        val candidate = dominantFace(motion).opposite
        return candidate.takeIf { it != current }
    }
}
