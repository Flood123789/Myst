package mystcraft.flood.entity

import net.minecraft.util.math.Vec3d
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaperMountSteeringTest {
    @Test
    fun `bound lesser and greater companions share the owner mount gate`() {
        assertTrue(ReaperMounting.canOwnerMount(true, true, false))
        assertEquals(0.702, ReaperMounting.seatOffset(0.9f), 1.0e-6)
        assertEquals(1.482, ReaperMounting.seatOffset(1.9f), 1.0e-6)
    }

    @Test
    fun `forward input follows view tangent on a floor`() {
        val motion = ReaperMountSteering.resolve(
            Vec3d(0.0, -0.3, 1.0), Vec3d(0.0, 1.0, 0.0), Vec3d(1.0, 0.0, 0.0), 1.0f, 0.0f
        )
        assertEquals(0.0, motion.direction.y, 1.0e-8)
        assertTrue(motion.direction.z > 0.99)
        assertEquals(1.0, motion.throttle, 1.0e-8)
    }

    @Test
    fun `view is projected onto a wall instead of pushing away from it`() {
        val motion = ReaperMountSteering.resolve(
            Vec3d(1.0, -0.8, 0.2), Vec3d(1.0, 0.0, 0.0), Vec3d(0.0, 1.0, 0.0), 1.0f, 0.0f
        )
        assertEquals(0.0, motion.direction.x, 1.0e-8)
        assertTrue(motion.direction.lengthSquared() > 0.99)
    }

    @Test
    fun `no rider input holds still and reverse is slower`() {
        val still = ReaperMountSteering.resolve(
            Vec3d(0.0, 0.0, 1.0), Vec3d(0.0, 1.0, 0.0), Vec3d(0.0, 0.0, 1.0), 0.0f, 0.0f
        )
        val reverse = ReaperMountSteering.resolve(
            Vec3d(0.0, 0.0, 1.0), Vec3d(0.0, 1.0, 0.0), Vec3d(0.0, 0.0, 1.0), -1.0f, 0.0f
        )
        assertEquals(Vec3d.ZERO, still.direction)
        assertEquals(0.0, still.throttle, 1.0e-8)
        assertEquals(0.58, reverse.throttle, 1.0e-8)
    }
}
