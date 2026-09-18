package mystcraft.flood.entity

import net.minecraft.util.math.Vec3d
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.random.Random

/** Femur, tibia, tarsus, matching the Reaper's leg. */
private val LEG = doubleArrayOf(0.90, 1.14, 0.30)
private val BOW = doubleArrayOf(0.0, 1.0, 0.22, 0.0)

class FabrikChainTest {

    private fun chain() = FabrikChain(LEG.copyOf(), BOW.copyOf())

    /** The invariant FABRIK exists to maintain: bones never stretch. */
    private fun assertSegmentsIntact(chain: FabrikChain, tolerance: Double = 1.0e-6) {
        for (segment in 0 until chain.segmentCount) {
            val actual = chain.joints[segment].distanceTo(chain.joints[segment + 1])
            assertEquals(chain.lengthOf(segment), actual, tolerance, "segment $segment stretched")
        }
    }

    @Test
    fun `reaches a target inside its range and keeps every bone intact`() {
        val chain = chain()
        val root = Vec3d(0.0, 1.25, 0.0)
        val target = Vec3d(1.05, 0.0, 0.95)

        chain.solve(root, target, Vec3d(0.0, 1.0, 0.0))

        assertEquals(root, chain.joints[0])
        assertTrue(chain.joints[chain.segmentCount].distanceTo(target) < 0.01, "did not reach the foot")
        assertSegmentsIntact(chain)
    }

    @Test
    fun `an unreachable target leaves the limb straight, not stretched`() {
        val chain = chain()
        val root = Vec3d.ZERO
        val target = Vec3d(50.0, 0.0, 0.0)

        chain.solve(root, target, Vec3d(0.0, 1.0, 0.0))

        assertSegmentsIntact(chain)
        // Fully extended along the line to the target.
        assertEquals(chain.totalLength, chain.joints[0].distanceTo(chain.joints[chain.segmentCount]), 1.0e-6)
    }

    @Test
    fun `the knee bends toward the pole, on a floor and upside down alike`() {
        val floor = chain()
        floor.solve(Vec3d(0.0, 1.25, 0.0), Vec3d(1.05, 0.0, 0.0), Vec3d(0.0, 1.0, 0.0))
        val floorKnee = floor.joints[1]

        val ceiling = chain()
        ceiling.solve(Vec3d(0.0, -1.25, 0.0), Vec3d(1.05, 0.0, 0.0), Vec3d(0.0, -1.0, 0.0))
        val ceilingKnee = ceiling.joints[1]

        assertTrue(floorKnee.y > 0.0, "knee should peak upward on a floor, got $floorKnee")
        assertTrue(ceilingKnee.y < 0.0, "knee should peak downward on a ceiling, got $ceilingKnee")
    }

    @Test
    fun `scaling shrinks every segment together`() {
        val chain = chain()
        chain.scale = 0.5
        chain.solve(Vec3d(0.0, 0.62, 0.0), Vec3d(0.5, 0.0, 0.45), Vec3d(0.0, 1.0, 0.0))

        assertSegmentsIntact(chain)
        assertEquals(LEG.sum() * 0.5, chain.totalLength, 1.0e-9)
    }

    /**
     * Temporal coherence is the reason FABRIK was chosen over re-deriving angles each frame: a
     * foot creeping along should not make the limb snap to a mirrored solution part way.
     */
    @Test
    fun `a foot walked slowly across the ground never flips the bend`() {
        val chain = chain()
        val root = Vec3d(0.0, 1.25, 0.0)
        val pole = Vec3d(0.0, 1.0, 0.0)

        chain.solve(root, Vec3d(1.2, 0.0, -0.6), pole)
        var previousKnee = chain.joints[1]

        for (step in 1..80) {
            val target = Vec3d(1.2, 0.0, -0.6 + step * 0.015)
            chain.solve(root, target, pole)

            assertTrue(chain.joints[1].y > 0.0, "bend inverted at step $step")
            assertTrue(
                chain.joints[1].distanceTo(previousKnee) < 0.25,
                "knee jumped ${chain.joints[1].distanceTo(previousKnee)} at step $step"
            )
            previousKnee = chain.joints[1]
            assertSegmentsIntact(chain, 1.0e-4)
        }
    }

    @Test
    fun `handles a target sitting exactly on the root without producing NaN`() {
        val chain = chain()
        val root = Vec3d(2.0, 3.0, 4.0)

        chain.solve(root, root, Vec3d(0.0, 1.0, 0.0))

        chain.joints.forEach { joint ->
            assertTrue(joint.x.isFinite() && joint.y.isFinite() && joint.z.isFinite(), "NaN joint $joint")
        }
        assertSegmentsIntact(chain, 1.0e-4)
    }

    @Test
    fun `random reachable targets are all solved to within tolerance`() {
        val random = Random(11)
        val chain = chain()
        val root = Vec3d.ZERO
        var worst = 0.0

        repeat(4000) {
            val direction = Vec3d(
                random.nextDouble(-1.0, 1.0),
                random.nextDouble(-1.0, 1.0),
                random.nextDouble(-1.0, 1.0)
            )
            if (direction.lengthSquared() < 1.0e-6) return@repeat
            val distance = random.nextDouble(0.4, chain.totalLength * 0.95)
            val target = direction.normalize().multiply(distance)

            chain.solve(root, target, Vec3d(0.0, 1.0, 0.0))
            worst = maxOf(worst, chain.joints[chain.segmentCount].distanceTo(target))

            for (segment in 0 until chain.segmentCount) {
                val actual = chain.joints[segment].distanceTo(chain.joints[segment + 1])
                assertTrue(abs(actual - chain.lengthOf(segment)) < 1.0e-4, "segment $segment stretched")
            }
        }
        assertTrue(worst < 0.02, "worst miss was $worst")
    }
}
