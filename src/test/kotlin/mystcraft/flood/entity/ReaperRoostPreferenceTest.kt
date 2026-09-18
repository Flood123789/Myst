package mystcraft.flood.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The stated preference is that darkness beats everything, and height only sorts places that are
 * already dark. These pin that ordering down so it cannot be reintroduced as a points system by
 * a later tweak to any one weight.
 */
class ReaperRoostPreferenceTest {

    private fun score(light: Int, height: Int, sky: Boolean = false, enclosure: Int = 1) =
        ReaperRoostScoring.score(light, height, sky, enclosure)

    @Test
    fun `a dark corner behind a desk beats a lit ceiling`() {
        val darkCornerOnTheFloor = score(light = 0, height = 0, enclosure = 4)
        val ceilingBesideGlowstone = score(light = 15, height = 14, enclosure = 1)
        assertTrue(
            darkCornerOnTheFloor > ceilingBesideGlowstone,
            "dark corner $darkCornerOnTheFloor should beat lit ceiling $ceilingBesideGlowstone"
        )
    }

    @Test
    fun `no amount of height promotes a brighter spot over a darker one`() {
        val darkAndLow = score(light = 3, height = -16, enclosure = 0)
        val brighterAndAsHighAsPossible = score(light = 4, height = 16, enclosure = 6)
        assertTrue(
            darkAndLow > brighterAndAsHighAsPossible,
            "one light level must outrank the entire height and enclosure range"
        )
    }

    @Test
    fun `height breaks ties between equally dark places`() {
        assertTrue(score(light = 0, height = 8) > score(light = 0, height = 0))
    }

    @Test
    fun `enclosure breaks ties before height does`() {
        val tuckedAway = score(light = 0, height = 0, enclosure = 5)
        val outInTheOpenButHigher = score(light = 0, height = 4, enclosure = 0)
        assertTrue(
            tuckedAway > outInTheOpenButHigher,
            "a corner should win over open ground a few blocks up"
        )
    }

    @Test
    fun `open sky is a penalty at equal darkness`() {
        assertTrue(score(light = 0, height = 0, sky = false) > score(light = 0, height = 0, sky = true))
    }

    @Test
    fun `only genuinely dark cells count as somewhere to settle`() {
        assertTrue(ReaperRoostScoring.isDarkEnough(0))
        assertTrue(ReaperRoostScoring.isDarkEnough(ReaperRoostScoring.ROOST_LIGHT_CEILING))
        assertFalse(ReaperRoostScoring.isDarkEnough(ReaperRoostScoring.ROOST_LIGHT_CEILING + 1))
        assertFalse(ReaperRoostScoring.isDarkEnough(15))
    }

    @Test
    fun `light level is clamped rather than allowed to invert the ranking`() {
        assertTrue(score(light = -5, height = 0) >= score(light = 0, height = 0))
        assertTrue(score(light = 40, height = 0) <= score(light = 15, height = 0))
    }
}
