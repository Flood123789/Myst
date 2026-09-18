package mystcraft.flood.entity

import net.minecraft.util.math.Vec3d
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaperRoamProgressTest {
    @Test
    fun `surface contact cannot cause an immediate turn while the Reaper is moving`() {
        val progress = ReaperRoamProgress()
        val heading = Vec3d(1.0, 0.0, 0.0)

        repeat(20) { tick ->
            assertFalse(progress.observe(Vec3d(tick * 0.08, 0.0, 0.0), heading))
        }
    }

    @Test
    fun `a wedged Reaper chooses another heading after sustained lack of progress`() {
        val progress = ReaperRoamProgress(stalledTicksBeforeTurn = 8)
        val position = Vec3d(2.0, 3.0, 4.0)
        val heading = Vec3d(0.0, 0.0, 1.0)

        assertFalse(progress.observe(position, heading))
        repeat(7) { assertFalse(progress.observe(position, heading)) }
        assertTrue(progress.observe(position, heading))
    }
}
