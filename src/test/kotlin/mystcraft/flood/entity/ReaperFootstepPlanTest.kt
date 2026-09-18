package mystcraft.flood.entity

import net.minecraft.util.math.Vec3d
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReaperFootstepPlanTest {

    @Test
    fun `a waiting foot keeps the first point it planned`() {
        val plan = ReaperFootstepPlan()
        val foot = Vec3d.ZERO
        val reserved = Vec3d(0.6, 0.0, 0.0)

        plan.consider(foot, reserved, footSupported = true, threshold = 0.3)
        plan.consider(foot, Vec3d(1.2, 0.0, 0.0), footSupported = true, threshold = 0.3)

        assertEquals(reserved, plan.target)
    }

    @Test
    fun `small drift does not create a step plan`() {
        val plan = ReaperFootstepPlan()

        plan.consider(Vec3d.ZERO, Vec3d(0.2, 0.0, 0.0), footSupported = true, threshold = 0.3)

        assertNull(plan.target)
    }

    @Test
    fun `a missing support forces a replacement even before normal stride distance`() {
        val plan = ReaperFootstepPlan()
        val replacement = Vec3d(0.1, 0.0, 0.0)

        plan.consider(Vec3d.ZERO, replacement, footSupported = false, threshold = 0.3)

        assertEquals(replacement, plan.target)
    }

    @Test
    fun `invalidating and taking a plan both release the reserved point`() {
        val plan = ReaperFootstepPlan()
        val first = Vec3d(0.6, 0.0, 0.0)
        val second = Vec3d(0.8, 0.0, 0.0)

        plan.consider(Vec3d.ZERO, first, footSupported = true, threshold = 0.3)
        plan.invalidate()
        plan.consider(Vec3d.ZERO, second, footSupported = true, threshold = 0.3)

        assertEquals(second, plan.take())
        assertNull(plan.target)
    }

    @Test
    fun `minor prediction noise keeps a plan but a genuine course change invalidates it`() {
        val plan = ReaperFootstepPlan()
        val expected = Vec3d(1.0, 0.0, 0.0)

        plan.consider(
            foot = Vec3d.ZERO,
            candidate = Vec3d(0.7, -0.2, 0.0),
            footSupported = true,
            threshold = 0.3,
            expected = expected
        )

        assertEquals(false, plan.isStale(Vec3d(1.1, 0.0, 0.0), tolerance = 0.5))
        assertEquals(true, plan.isStale(Vec3d(0.0, 0.0, 1.0), tolerance = 0.5))
    }
}
