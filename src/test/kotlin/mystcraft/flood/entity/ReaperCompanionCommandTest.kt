package mystcraft.flood.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReaperCompanionCommandTest {
    @Test
    fun `owner command cycle is stable`() {
        assertEquals(ReaperCompanionCommand.STAY, ReaperCompanionCommand.FOLLOW.next())
        assertEquals(ReaperCompanionCommand.WANDER, ReaperCompanionCommand.STAY.next())
        assertEquals(ReaperCompanionCommand.FOLLOW, ReaperCompanionCommand.WANDER.next())
    }
}
