package mystcraft.flood.generation

import mystcraft.flood.generation.profile.ColorCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

class AgeCompilerCatalogTest {
    @Test
    fun `new preset colors compile to their advertised rgb values`() {
        val cases = mapOf(
            "color_orange" to 0xFF8800,
            "color_cyan" to 0x00FFFF,
            "color_teal" to 0x008080,
            "color_pink" to 0xFF69B4,
            "color_magenta" to 0xFF00FF,
            "color_lime" to 0x7FFF00,
            "color_brown" to 0x8B4513,
            "color_gray" to 0x808080,
            "color_light_blue" to 0x66CCFF
        )

        for ((page, expected) in cases) {
            assertEquals(expected, AgeCompiler.compile(listOf(page, "color_sky")).skyColor, page)
        }
    }

    @Test
    fun `nether and end terrain pages compile as world types`() {
        assertEquals("NETHER", AgeCompiler.compile(listOf("terrain_nether")).terrainType)
        assertEquals("END", AgeCompiler.compile(listOf("terrain_end")).terrainType)
    }

    @Test
    fun `random pages are deterministic and can select real terrain symbols`() {
        val first = AgeCompiler.compile(listOf("random"), kotlin.random.Random(42))
        val second = AgeCompiler.compile(listOf("random"), kotlin.random.Random(42))
        assertEquals(first, second)

        val generatedTerrains = (0 until 200).mapNotNull { seed ->
            AgeCompiler.compile(listOf("random"), kotlin.random.Random(seed)).terrainType
        }
        assertTrue(generatedTerrains.isNotEmpty())
        assertTrue(generatedTerrains.all { it in setOf("FLOATING_ISLANDS", "AMPLIFIED", "ALPHA", "BETA", "CAVES", "FLAT", "BIOSPHERES", "CITIES", "NETHER", "END", "VOID") })
    }

    @Test
    fun `custom hex color remains exact across random seeds`() {
        val compiled = AgeCompiler.compile(listOf("color_custom:#FF00AA", "color_grass"))
        val spec = compiled.grassColorSpec as CompiledColor.Exact
        assertEquals(0xFF00AA, spec.rgb)
        assertEquals(0xFF00AA, spec.resolve(Random(12345)))
        assertEquals(0xFF00AA, spec.resolve(Random(67890)))
    }

    @Test
    fun `red preset color produces seed variation within acceptable red range`() {
        val compiled = AgeCompiler.compile(listOf("color_red", "color_grass"))
        val spec = compiled.grassColorSpec as CompiledColor.Preset
        assertEquals(ColorCategory.RED, spec.category)

        val color1 = spec.resolve(Random(11111))
        val color2 = spec.resolve(Random(99999))

        // Colors vary across seeds
        assertNotEquals(color1, color2)

        // Verify acceptable Red HSV bounds (Saturation >= 0.5, Brightness/Value >= 0.3, Hue near 0/360)
        for (rgb in listOf(color1, color2)) {
            val r = (rgb shr 16) and 0xFF
            val g = (rgb shr 8) and 0xFF
            val b = rgb and 0xFF
            val hsv = FloatArray(3)
            java.awt.Color.RGBtoHSB(r, g, b, hsv)

            val hueDeg = hsv[0] * 360f
            val sat = hsv[1]
            val valBright = hsv[2]

            // Hue near red (340° to 20°)
            assertTrue(hueDeg >= 340f || hueDeg <= 20f, "Hue should be red, was $hueDeg°")
            // Saturation >= 0.5 (not washed out/gray)
            assertTrue(sat >= 0.5f, "Saturation should be >= 0.5, was $sat")
            // Brightness >= 0.3 (not black)
            assertTrue(valBright >= 0.3f, "Value/Brightness should be >= 0.3, was $valBright")
        }
    }
}
