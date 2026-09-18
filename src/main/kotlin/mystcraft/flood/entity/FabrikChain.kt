package mystcraft.flood.entity

import net.minecraft.util.math.Vec3d

/**
 * A multi-joint limb solved with FABRIK (Forward And Backward Reaching Inverse Kinematics).
 *
 * FABRIK is the iterative method described by Aristidou and Lasenby (2011): rather than solving
 * angles trigonometrically, it walks the chain backward from the target and forward from the
 * root, snapping each joint back onto its segment length as it goes. It generalises to any number
 * of segments, which a closed-form two-bone solve cannot.
 *
 * What it does not do is pick a *shape*. Plain FABRIK converges to whatever solution is nearest
 * its starting pose, and a three-segment leg spanning barely half its own length has enormous
 * freedom, so left to itself it settles into flat accordion folds that look nothing like a leg.
 * The usual answer is to warm-start from the previous frame and nudge, but a weak nudge does not
 * hold a shape and a strong one fights the solver.
 *
 * So the pose is reseeded from scratch every solve, from a [bowProfile] describing where each
 * joint belongs relative to the straight line between root and tip. Because that seed is a pure
 * function of the two endpoints, it is perfectly stable frame to frame without any warm start:
 * endpoints move smoothly, so the pose does too, and the limb can never flip between mirrored
 * solutions. FABRIK then only has to correct segment lengths, which takes a handful of passes.
 */
class FabrikChain(
    private val lengths: DoubleArray,
    private val bowProfile: DoubleArray = evenProfile(lengths.size)
) {

    init {
        require(bowProfile.size == lengths.size + 1) {
            "bow profile needs one weight per joint (${lengths.size + 1}), got ${bowProfile.size}"
        }
    }

    /** Joint positions, root first, tip last. One more entry than there are segments. */
    val joints: Array<Vec3d> = Array(lengths.size + 1) { Vec3d.ZERO }

    val segmentCount: Int get() = lengths.size

    /** Uniform multiplier on every segment, so one authored chain serves both body sizes. */
    var scale: Double = 1.0

    val totalLength: Double get() = lengths.sum() * scale

    fun lengthOf(segment: Int): Double = lengths[segment] * scale

    /** Fraction of the total length reached by each joint, used to space the seed. */
    private val travelled: DoubleArray = DoubleArray(lengths.size + 1).also { fractions ->
        val total = lengths.sum()
        var running = 0.0
        for (index in lengths.indices) {
            running += lengths[index]
            fractions[index + 1] = running / total
        }
    }

    /**
     * Poses the chain, then corrects it. [pole] is the direction the limb should bend toward,
     * normally the surface normal of whatever the creature is standing on.
     */
    fun solve(
        root: Vec3d,
        target: Vec3d,
        pole: Vec3d,
        iterations: Int = DEFAULT_ITERATIONS,
        tolerance: Double = DEFAULT_TOLERANCE
    ) {
        val span = target.subtract(root)
        val distance = span.length()

        // Unreachable: no bend gets there, so lay the chain out straight rather than spending the
        // whole iteration budget converging on the same answer.
        if (distance > totalLength) {
            val direction = if (distance > 1.0e-9) span.multiply(1.0 / distance) else Vec3d(0.0, 1.0, 0.0)
            var cursor = root
            joints[0] = root
            for (segment in 0 until segmentCount) {
                cursor = cursor.add(direction.multiply(lengthOf(segment)))
                joints[segment + 1] = cursor
            }
            return
        }

        seed(root, span, distance, pole)

        repeat(iterations) {
            // Backward pass: pin the tip to the target and walk back to the root.
            joints[segmentCount] = target
            for (index in segmentCount - 1 downTo 0) {
                joints[index] = towards(joints[index + 1], joints[index], lengthOf(index))
            }

            // Forward pass: pin the root back where it belongs and walk out to the tip.
            joints[0] = root
            for (index in 1..segmentCount) {
                joints[index] = towards(joints[index - 1], joints[index], lengthOf(index - 1))
            }

            // Checked after a pass, never before: a freshly seeded chain already has its tip on
            // the target, so testing first would exit immediately and leave the seed's segment
            // lengths uncorrected.
            if (joints[segmentCount].squaredDistanceTo(target) <= tolerance * tolerance) return
        }
    }

    /**
     * Lays the joints along the root-to-tip line, displaced by [bowProfile] scaled to whatever
     * bow makes the resulting polyline as long as the chain itself.
     *
     * The bow runs along the component of the pole *perpendicular* to that line, never the pole
     * itself. Bowing along the raw pole means that whenever the tip happens to lie near the pole
     * axis the seed is exactly collinear, and a collinear chain is a symmetric stationary point
     * that no number of FABRIK passes can fold out of.
     */
    private fun seed(root: Vec3d, span: Vec3d, distance: Double, pole: Vec3d) {
        val axis = if (distance > 1.0e-9) span.multiply(1.0 / distance) else Vec3d(0.0, 0.0, 1.0)
        val bowDirection = perpendicularTo(pole, axis)

        var low = 0.0
        var high = totalLength
        repeat(BOW_BISECTIONS) {
            val mid = (low + high) * 0.5
            if (polylineLength(root, span, bowDirection, mid) < totalLength) low = mid else high = mid
        }

        val bow = (low + high) * 0.5
        for (index in joints.indices) {
            joints[index] = pointAt(root, span, bowDirection, bow, index)
        }
    }

    private fun pointAt(root: Vec3d, span: Vec3d, bowDirection: Vec3d, bow: Double, index: Int): Vec3d =
        root.add(span.multiply(travelled[index])).add(bowDirection.multiply(bowProfile[index] * bow))

    private fun polylineLength(root: Vec3d, span: Vec3d, bowDirection: Vec3d, bow: Double): Double {
        var total = 0.0
        var previous = pointAt(root, span, bowDirection, bow, 0)
        for (index in 1..segmentCount) {
            val point = pointAt(root, span, bowDirection, bow, index)
            total += point.distanceTo(previous)
            previous = point
        }
        return total
    }

    /** The point [distance] away from [from], in the direction of [toward]. */
    private fun towards(from: Vec3d, toward: Vec3d, distance: Double): Vec3d {
        val delta = toward.subtract(from)
        val length = delta.length()
        if (length < 1.0e-9) {
            // Degenerate: the two joints coincide, so any direction preserves the length.
            return from.add(Vec3d(0.0, distance, 0.0))
        }
        return from.add(delta.multiply(distance / length))
    }

    /** The part of [vector] perpendicular to [axis], normalised, with a deterministic fallback. */
    private fun perpendicularTo(vector: Vec3d, axis: Vec3d): Vec3d {
        val flattened = vector.subtract(axis.multiply(vector.dotProduct(axis)))
        if (flattened.lengthSquared() > 1.0e-8) return flattened.normalize()

        val seed = if (Math.abs(axis.y) > 0.9) Vec3d(1.0, 0.0, 0.0) else Vec3d(0.0, 1.0, 0.0)
        return seed.subtract(axis.multiply(seed.dotProduct(axis))).normalize()
    }

    companion object {
        const val DEFAULT_ITERATIONS = 8
        const val DEFAULT_TOLERANCE = 0.002

        /** Bisection steps for the seed bow. */
        private const val BOW_BISECTIONS = 24

        /** A plain half-sine arc: sensible for a limb with no particular anatomy in mind. */
        fun evenProfile(segments: Int): DoubleArray =
            DoubleArray(segments + 1) { Math.sin(it.toDouble() / segments * Math.PI) }
    }
}
