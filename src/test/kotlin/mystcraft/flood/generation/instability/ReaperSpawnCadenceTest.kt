package mystcraft.flood.generation.instability

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ReaperSpawnCadenceTest {
    @AfterEach
    fun clearCadence() = ReaperSpawnCadence.clear()

    @Test
    fun `successful spawn creates a hard per-player cooldown`() {
        val player = UUID.randomUUID()
        assertTrue(ReaperSpawnCadence.isReady(player, 1_000))
        ReaperSpawnCadence.markSpawned(player, 1_000, 90)
        assertFalse(ReaperSpawnCadence.isReady(player, 2_799))
        assertTrue(ReaperSpawnCadence.isReady(player, 2_800))
    }
}
