package mystcraft.flood.generation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LostCityLayoutTest {

    @Test
    fun `layout is deterministic and uses the age seed`() {
        val first = LostCityLayout(12345L)
        val same = LostCityLayout(12345L)
        val different = LostCityLayout(98765L)

        for (cellX in -3..3) {
            for (cellZ in -3..3) {
                assertEquals(first.anchor(cellX, cellZ), same.anchor(cellX, cellZ))
            }
        }

        val firstAnchors = (-3..3).flatMap { x -> (-3..3).map { z -> first.anchor(x, z) } }
        val differentAnchors = (-3..3).flatMap { x -> (-3..3).map { z -> different.anchor(x, z) } }
        assertNotEquals(firstAnchors, differentAnchors)
    }

    @Test
    fun `every active city center is a station`() {
        val layout = LostCityLayout(0x5eedL)
        val anchors = activeAnchors(layout)

        assertTrue(anchors.isNotEmpty())
        anchors.forEach { anchor ->
            val center = layout.plan(anchor.centerChunkX, anchor.centerChunkZ)
            assertTrue(center.inCity, "city center must be inside its city")
            assertTrue(center.station, "city center must provide a subway station")
            assertTrue(
                center.subway.eastWest || center.subway.northSouth,
                "station must be attached to the subway network"
            )
        }
    }

    @Test
    fun `subways continuously connect neighboring cities`() {
        val layout = LostCityLayout(0x1cedc17L)
        val source = activeAnchors(layout).firstNotNullOf { anchor ->
            layout.eastNeighbor(anchor)?.let { anchor to it }
        }
        val (start, end) = source

        for (z in orderedRange(start.centerChunkZ, end.centerChunkZ)) {
            assertTrue(
                layout.plan(start.centerChunkX, z).subway.northSouth,
                "missing north/south subway at ${start.centerChunkX},$z"
            )
        }
        for (x in orderedRange(start.centerChunkX, end.centerChunkX)) {
            assertTrue(
                layout.plan(x, end.centerChunkZ).subway.eastWest,
                "missing east/west subway at $x,${end.centerChunkZ}"
            )
        }
    }

    @Test
    fun `highways bridge the overworld gap between cities`() {
        val layout = LostCityLayout(0xc175L)
        val (start, end) = activeAnchors(layout).firstNotNullOf { anchor ->
            layout.eastNeighbor(anchor)?.let { anchor to it }
        }

        val corridor = orderedRange(start.centerChunkX, end.centerChunkX)
            .map { x -> layout.plan(x, start.centerChunkZ) }
            .filterNot { it.inCity }

        assertTrue(corridor.isNotEmpty(), "neighboring cities must have overworld terrain between them")
        assertTrue(corridor.all { it.highway.eastWest }, "the highway must continuously span the non-city gap")
    }

    private fun activeAnchors(layout: LostCityLayout): List<LostCityLayout.Anchor> =
        (-4..4).flatMap { x -> (-4..4).map { z -> layout.anchor(x, z) } }.filter { it.active }

    private fun orderedRange(first: Int, second: Int): IntRange = minOf(first, second)..maxOf(first, second)
}
