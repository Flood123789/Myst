package mystcraft.flood.entity

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PettyReaperFollowLeashTest {
    @Test
    fun `idle pet stays satisfied throughout arrival band`() {
        assertFalse(PettyReaperFollowLeash.shouldFollow(5.0 * 5.0, alreadyFollowing = false))
        assertFalse(PettyReaperFollowLeash.shouldFollow(4.0 * 4.0, alreadyFollowing = false))
    }

    @Test
    fun `pet starts only outside six blocks and stops inside four`() {
        assertFalse(PettyReaperFollowLeash.shouldFollow(6.0 * 6.0, alreadyFollowing = false))
        assertTrue(PettyReaperFollowLeash.shouldFollow(6.01 * 6.01, alreadyFollowing = false))
        assertTrue(PettyReaperFollowLeash.shouldFollow(4.01 * 4.01, alreadyFollowing = true))
        assertFalse(PettyReaperFollowLeash.shouldFollow(4.0 * 4.0, alreadyFollowing = true))
    }
}
