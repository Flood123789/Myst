package mystcraft.flood.entity

import net.minecraft.util.math.Vec3d
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FamiliarOrbitTest {

    @Test
    fun `active anchor circles at a camera-safe uneven radius`() {
        val owner = Vec3d(12.0, 70.0, -4.0)
        val positions = (0..240 step 20).map { FamiliarOrbit.anchor(owner, it, 42L) }
        positions.forEach { anchor ->
            val radius = anchor.subtract(owner).horizontalLength()
            assertTrue(radius in 2.94..4.26, "orbit radius was $radius")
            assertTrue(anchor.y - owner.y in 0.86..1.64)
        }
        assertNotEquals(positions.first(), positions.last())
    }
}
