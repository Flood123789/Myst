package mystcraft.flood.item

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LinkingBookTargetTest {
    @Test
    fun `yaw snaps to the nearest cardinal direction`() {
        assertEquals(0.0f, snapCardinalYaw(-1.95f))
        assertEquals(-90.0f, snapCardinalYaw(-85.26f))
        assertEquals(-180.0f, snapCardinalYaw(-146.06f))
        assertEquals(90.0f, snapCardinalYaw(90.15f))
    }
}
