package mystcraft.flood.generation

import net.minecraft.util.math.Direction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ImmersivePortalsCompatTest {
    @Test
    fun `horizontal rectangular frame uses both in-plane dimensions`() {
        val plane = crystalPortalPlane(
            Direction.Axis.Y,
            minX = 10,
            maxX = 13,
            minY = 64,
            maxY = 64,
            minZ = 20,
            maxZ = 25
        )

        assertEquals(6.0, plane.width)
        assertEquals(4.0, plane.height)
        assertEquals(1.0, plane.axisW.crossProduct(plane.axisH).y, 0.0)
    }

    @Test
    fun `vertical destination yaw maps inward door travel to saved heading`() {
        val southFacingPlane = crystalPortalPlane(
            Direction.Axis.Z,
            minX = 0,
            maxX = 1,
            minY = 0,
            maxY = 2,
            minZ = 0,
            maxZ = 0
        )

        assertEquals(180.0, kotlin.math.abs(immersivePortalYawRotation(southFacingPlane, 0.0f, 0)), 0.0)
        assertEquals(90.0, immersivePortalYawRotation(southFacingPlane, 90.0f, 0), 0.0)
        assertEquals(0.0, immersivePortalYawRotation(southFacingPlane, 0.0f, 2), 0.0)
    }
}
