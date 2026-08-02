package mystcraft.flood.generation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
}
