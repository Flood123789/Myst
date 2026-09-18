package mystcraft.flood.entity

import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class SurfaceClingTest {

    @Test
    fun `cling vectors point away from the surface`() {
        assertEquals(Vec3d(0.0, 1.0, 0.0), SurfaceCling.vectorOf(Direction.UP))
        assertEquals(Vec3d(0.0, -1.0, 0.0), SurfaceCling.vectorOf(Direction.DOWN))
        assertEquals(Vec3d(0.0, 0.0, -1.0), SurfaceCling.vectorOf(Direction.NORTH))
    }

    @Test
    fun `projection removes the normal component and keeps unit length`() {
        val up = Vec3d(0.0, 1.0, 0.0)
        val projected = SurfaceCling.projectOntoPlane(Vec3d(3.0, 7.0, 0.0), up, Vec3d(1.0, 0.0, 0.0))

        assertEquals(0.0, projected.dotProduct(up), 1.0e-12)
        assertEquals(1.0, projected.length(), 1.0e-12)
        assertTrue(projected.x > 0.0)
    }

    /** On a wall, a heading straight into the wall has to resolve to something usable. */
    @Test
    fun `projection falls back when the vector is parallel to the normal`() {
        val up = Vec3d(1.0, 0.0, 0.0)
        val projected = SurfaceCling.projectOntoPlane(Vec3d(5.0, 0.0, 0.0), up, Vec3d(0.0, 0.0, 1.0))

        assertEquals(0.0, projected.dotProduct(up), 1.0e-12)
        assertEquals(1.0, projected.length(), 1.0e-12)
    }

    @Test
    fun `projection survives both vector and fallback being parallel to the normal`() {
        val up = Vec3d(0.0, 1.0, 0.0)
        val projected = SurfaceCling.projectOntoPlane(Vec3d(0.0, 2.0, 0.0), up, Vec3d(0.0, -3.0, 0.0))

        assertEquals(0.0, projected.dotProduct(up), 1.0e-12)
        assertEquals(1.0, projected.length(), 1.0e-12)
    }

    @Test
    fun `forward on a floor matches the plain yaw convention`() {
        val up = Vec3d(0.0, 1.0, 0.0)
        val fallback = Vec3d(0.0, 0.0, 1.0)

        // Yaw 0 faces +Z in Minecraft's convention; yaw 90 faces -X.
        val south = SurfaceCling.forwardOnSurface(up, 0.0f, fallback)
        assertEquals(0.0, south.x, 1.0e-6)
        assertEquals(1.0, south.z, 1.0e-6)

        val west = SurfaceCling.forwardOnSurface(up, 90.0f, fallback)
        assertEquals(-1.0, west.x, 1.0e-6)
        assertEquals(0.0, west.z, 1.0e-6)
    }

    @Test
    fun `forward stays in the surface plane on a wall`() {
        val up = Vec3d(1.0, 0.0, 0.0)
        for (yaw in 0 until 360 step 15) {
            val forward = SurfaceCling.forwardOnSurface(up, yaw.toFloat(), Vec3d(0.0, 0.0, 1.0))
            assertEquals(0.0, forward.dotProduct(up), 1.0e-9, "yaw $yaw left the plane")
            assertEquals(1.0, forward.length(), 1.0e-9, "yaw $yaw was not unit length")
        }
    }

    /**
     * Tolerance is set by MathHelper, not by this code. Minecraft's sin/cos are reads from a
     * 65536-entry table, so a yaw cannot survive the trip through a direction vector better than
     * about a hundredth of a degree. Measured worst case here is 0.0038 degrees.
     */
    @Test
    fun `yaw round trips through forward on a floor`() {
        val up = Vec3d(0.0, 1.0, 0.0)
        var worst = 0.0f
        for (yaw in -180 until 180 step 15) {
            val forward = SurfaceCling.forwardOnSurface(up, yaw.toFloat(), Vec3d(0.0, 0.0, 1.0))
            val recovered = SurfaceCling.yawFor(forward, up)
            worst = maxOf(worst, abs(((recovered - yaw + 540f) % 360f) - 180f))
        }
        assertTrue(worst < 0.01f, "worst round trip error was $worst degrees")
    }

    /** Climbing a wall means facing along its normal; yaw must not become NaN there. */
    @Test
    fun `yaw is finite when forward points straight up a wall`() {
        val up = Vec3d(1.0, 0.0, 0.0)
        val recovered = SurfaceCling.yawFor(Vec3d(0.0, 1.0, 0.0), up)
        assertTrue(recovered.isFinite(), "yaw was $recovered")
    }

    /**
     * Regression: the body roll used to be a componentwise lerp. That converges for a wall
     * transition but sticks forever on a floor-to-ceiling flip, because the midpoint of two
     * opposed vectors renormalises back to the start.
     */
    @Test
    fun `roll completes a full inversion instead of stalling`() {
        var normal = Vec3d(0.0, 1.0, 0.0)
        val target = Vec3d(0.0, -1.0, 0.0)

        repeat(40) { normal = SurfaceCling.rotateToward(normal, target, 0.30) }

        assertEquals(-1.0, normal.y, 1.0e-6, "roll stalled at $normal")
    }

    @Test
    fun `roll turns at the requested rate and stays unit length`() {
        var normal = Vec3d(0.0, 1.0, 0.0)
        val target = Vec3d(0.0, 0.0, 1.0)

        normal = SurfaceCling.rotateToward(normal, target, 0.30)
        assertEquals(1.0, normal.length(), 1.0e-9)
        // One step of 0.30 rad off the start: the dot with the origin is cos(0.30).
        assertEquals(kotlin.math.cos(0.30), normal.dotProduct(Vec3d(0.0, 1.0, 0.0)), 1.0e-9)

        repeat(20) { normal = SurfaceCling.rotateToward(normal, target, 0.30) }
        assertEquals(1.0, normal.z, 1.0e-6, "roll did not settle on the target")
    }

    @Test
    fun `roll snaps home once inside the step`() {
        val target = Vec3d(0.0, 0.0, 1.0)
        val nearly = SurfaceCling.rotateToward(target, target, 0.30)
        assertEquals(1.0, nearly.z, 1.0e-12)
    }

    @Test
    fun `forward floor motion transports upward when meeting a wall`() {
        val floorUp = Vec3d(0.0, 1.0, 0.0)
        val northWallOut = Vec3d(0.0, 0.0, -1.0)
        val intoWall = Vec3d(0.0, 0.0, 1.0)

        val transported = SurfaceCling.transportDirection(intoWall, floorUp, northWallOut)

        assertEquals(1.0, transported.y, 1.0e-9)
        assertEquals(0.0, transported.dotProduct(northWallOut), 1.0e-9)
    }

    @Test
    fun `forward floor motion transports downward over an outside ledge`() {
        val floorUp = Vec3d(0.0, 1.0, 0.0)
        val outsideFaceOut = Vec3d(0.0, 0.0, 1.0)
        val overLedge = Vec3d(0.0, 0.0, 1.0)

        val transported = SurfaceCling.transportDirection(overLedge, floorUp, outsideFaceOut)

        assertEquals(-1.0, transported.y, 1.0e-9)
        assertEquals(0.0, transported.dotProduct(outsideFaceOut), 1.0e-9)
    }

    @Test
    fun `dominant face picks the strongest axis`() {
        assertSame(Direction.EAST, SurfaceCling.dominantFace(Vec3d(0.9, 0.2, -0.3)))
        assertSame(Direction.WEST, SurfaceCling.dominantFace(Vec3d(-0.9, 0.2, -0.3)))
        assertSame(Direction.UP, SurfaceCling.dominantFace(Vec3d(0.1, 0.8, -0.3)))
        assertSame(Direction.DOWN, SurfaceCling.dominantFace(Vec3d(0.1, -0.8, -0.3)))
        assertSame(Direction.SOUTH, SurfaceCling.dominantFace(Vec3d(0.1, 0.2, 0.9)))
        assertSame(Direction.NORTH, SurfaceCling.dominantFace(Vec3d(0.1, 0.2, -0.9)))
    }

    /**
     * The climb transition takes the opposite of this face as the new surface normal, so an
     * unstable answer here would make a Reaper flicker between two walls in a corner.
     */
    @Test
    fun `dominant face is deterministic for exact ties`() {
        val tie = Vec3d(0.5, 0.5, 0.5)
        assertSame(SurfaceCling.dominantFace(tie), SurfaceCling.dominantFace(tie))
    }

    @Test
    fun `wall motion can transition up or down instead of retaining the wall`() {
        val wall = Direction.WEST

        assertSame(Direction.DOWN, SurfaceCling.concaveTransitionFace(wall, Vec3d(0.0, 1.0, 0.0)))
        assertSame(Direction.UP, SurfaceCling.concaveTransitionFace(wall, Vec3d(0.0, -1.0, 0.0)))
    }
}
