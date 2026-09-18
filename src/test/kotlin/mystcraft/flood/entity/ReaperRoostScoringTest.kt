package mystcraft.flood.entity

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaperRoostScoringTest {
    @Test
    fun `darkness beats a high but brightly lit destination`() {
        val darkAndLow = ReaperRoostScoring.score(lightLevel = 1, heightDelta = -6, openSky = false)
        val brightAndHigh = ReaperRoostScoring.score(lightLevel = 15, heightDelta = 14, openSky = false)

        assertTrue(darkAndLow > brightAndHigh)
    }

    @Test
    fun `height wins when destinations are equally dark`() {
        val darkAndLow = ReaperRoostScoring.score(lightLevel = 1, heightDelta = -3, openSky = false)
        val darkAndHigh = ReaperRoostScoring.score(lightLevel = 1, heightDelta = 10, openSky = false)

        assertTrue(darkAndHigh > darkAndLow)
    }
}
