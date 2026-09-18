package mystcraft.flood.entity

import net.minecraft.util.math.Vec3d
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaperFootstepMotionTest {

    private val from = Vec3d(0.0, 0.0, 0.0)
    private val to = Vec3d(2.0, 0.0, 0.0)
    private val up = Vec3d(0.0, 1.0, 0.0)

    @Test
    fun `the foot lifts in place before travelling`() {
        val early = ReaperFootstepMotion.position(from, to, up, 0.14, 0.5)
        val lifted = ReaperFootstepMotion.position(from, to, up, 0.28, 0.5)

        assertEquals(0.0, early.x, 1.0e-9)
        assertTrue(early.y > 0.0)
        assertEquals(Vec3d(0.0, 0.5, 0.0), lifted)
    }

    @Test
    fun `the foot crosses while fully clear of the surface`() {
        val middle = ReaperFootstepMotion.position(from, to, up, 0.5, 0.5)

        assertEquals(Vec3d(1.0, 0.5, 0.0), middle)
    }

    @Test
    fun `the foot lowers in place and finishes exactly on its reserved point`() {
        val lowering = ReaperFootstepMotion.position(from, to, up, 0.86, 0.5)
        val landed = ReaperFootstepMotion.position(from, to, up, 1.0, 0.5)

        assertEquals(to.x, lowering.x, 1.0e-9)
        assertTrue(lowering.y > 0.0)
        assertEquals(to, landed)
    }
}
