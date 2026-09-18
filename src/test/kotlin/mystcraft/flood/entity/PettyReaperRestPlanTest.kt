package mystcraft.flood.entity

import net.minecraft.util.math.Vec3d
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PettyReaperRestPlanTest {
    @Test
    fun `six still seconds sends owner-bound Reaper to shelter`() {
        val plan = PettyReaperRestPlan()
        repeat(120) { plan.observeOwner(Vec3d.ZERO) }
        assertEquals(PettyReaperRestMode.SEEKING_SHELTER, plan.mode)
    }

    @Test
    fun `owner motion resets shelter countdown`() {
        val plan = PettyReaperRestPlan()
        repeat(100) { plan.observeOwner(Vec3d.ZERO) }
        plan.observeOwner(Vec3d(0.2, 0.0, 0.0))
        assertEquals(0, plan.stillTicks)
        assertEquals(PettyReaperRestMode.FOLLOWING, plan.mode)
    }

    @Test
    fun `sheltered petty stays dormant through ten blocks and wakes beyond it`() {
        val plan = PettyReaperRestPlan()
        repeat(120) { plan.observeOwner(Vec3d.ZERO) }
        plan.reachedShelter()
        assertFalse(plan.wakeIfOwnerFar(Vec3d(10.0, 0.0, 0.0), Vec3d.ZERO))
        assertEquals(PettyReaperRestMode.DORMANT, plan.mode)
        assertTrue(plan.wakeIfOwnerFar(Vec3d(10.01, 0.0, 0.0), Vec3d.ZERO))
        assertEquals(PettyReaperRestMode.FOLLOWING, plan.mode)
    }
}
