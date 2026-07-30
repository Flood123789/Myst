package mystcraft.flood.generation

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Seeded, world-independent city and transit planning.
 *
 * Lost Cities plans infrastructure per chunk before placing blocks. Keeping that decision layer
 * free of world access makes it deterministic and safe to call from parallel generation workers.
 */
internal class LostCityLayout(private val seed: Long) {

    companion object {
        const val CITY_CELL_CHUNKS = 64
        const val CITY_MIN_RADIUS_CHUNKS = 18
        const val CITY_RADIUS_VARIANCE_CHUNKS = 12
        const val CITY_CENTER_JITTER_CHUNKS = 18
        const val CITY_GROUND_Y = 72
        const val CITY_EDGE_FEATHER_CHUNKS = 5
        const val ROAD_GRID_CHUNKS = 4
        const val AVENUE_GRID_CHUNKS = 8

        private const val MAX_LINK_DISTANCE_CELLS = 3
        private const val ANCHOR_SEARCH_RADIUS_CELLS = 2
        private const val PLAN_CACHE_SIZE = 2048
    }

    private val planCache = ThreadLocal.withInitial {
        object : LinkedHashMap<Long, ChunkLayout>(PLAN_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ChunkLayout>?): Boolean =
                size > PLAN_CACHE_SIZE
        }
    }

    internal data class Anchor(
        val cellX: Int,
        val cellZ: Int,
        val centerChunkX: Int,
        val centerChunkZ: Int,
        val radiusChunks: Int,
        val groundY: Int,
        val active: Boolean,
        val style: Int
    )

    internal data class Axes(val eastWest: Boolean = false, val northSouth: Boolean = false) {
        operator fun plus(other: Axes): Axes = Axes(
            eastWest = eastWest || other.eastWest,
            northSouth = northSouth || other.northSouth
        )
    }

    internal data class ChunkLayout(
        val anchor: Anchor,
        val localChunkX: Int,
        val localChunkZ: Int,
        val cityFactor: Double,
        val station: Boolean,
        val subway: Axes,
        val highway: Axes
    ) {
        val inCity: Boolean = cityFactor > 0.0
    }

    fun plan(chunkX: Int, chunkZ: Int): ChunkLayout {
        val key = (chunkX.toLong() shl 32) xor (chunkZ.toLong() and 0xffffffffL)
        planCache.get()[key]?.let { return it }

        val anchor = nearestAnchor(chunkX, chunkZ)
        val localChunkX = chunkX - anchor.centerChunkX
        val localChunkZ = chunkZ - anchor.centerChunkZ
        val distance = hypot(localChunkX.toDouble(), localChunkZ.toDouble())
        val cityFactor = if (anchor.active) {
            ((anchor.radiusChunks - distance) / CITY_EDGE_FEATHER_CHUNKS.toDouble()).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        val inCity = cityFactor > 0.0
        val station = inCity && chunkX == anchor.centerChunkX && chunkZ == anchor.centerChunkZ
        val subway = transitAxes(chunkX, chunkZ, underground = true)
        val highway = if (inCity) Axes() else transitAxes(chunkX, chunkZ, underground = false)

        return ChunkLayout(
            anchor = anchor,
            localChunkX = localChunkX,
            localChunkZ = localChunkZ,
            cityFactor = cityFactor,
            station = station,
            subway = subway,
            highway = highway
        ).also { planCache.get()[key] = it }
    }

    internal fun anchor(cellX: Int, cellZ: Int): Anchor {
        val jitterX = rangedHash(cellX, cellZ, 31L, -CITY_CENTER_JITTER_CHUNKS, CITY_CENTER_JITTER_CHUNKS)
        val jitterZ = rangedHash(cellX, cellZ, 37L, -CITY_CENTER_JITTER_CHUNKS, CITY_CENTER_JITTER_CHUNKS)
        val radius = CITY_MIN_RADIUS_CHUNKS + rangedHash(cellX, cellZ, 41L, 0, CITY_RADIUS_VARIANCE_CHUNKS)
        return Anchor(
            cellX = cellX,
            cellZ = cellZ,
            centerChunkX = cellX * CITY_CELL_CHUNKS + CITY_CELL_CHUNKS / 2 + jitterX,
            centerChunkZ = cellZ * CITY_CELL_CHUNKS + CITY_CELL_CHUNKS / 2 + jitterZ,
            radiusChunks = radius,
            groundY = CITY_GROUND_Y + rangedHash(cellX, cellZ, 47L, -1, 2) * 4,
            active = positiveHash(cellX, cellZ, 53L) % 100 < 82,
            style = rangedHash(cellX, cellZ, 43L, 0, 7)
        )
    }

    internal fun eastNeighbor(anchor: Anchor): Anchor? = firstActiveNeighbor(anchor, 1, 0)

    internal fun southNeighbor(anchor: Anchor): Anchor? = firstActiveNeighbor(anchor, 0, 1)

    internal fun positiveHash(x: Int, z: Int, salt: Long): Int {
        val value = mixedHash(x, z, salt).toInt()
        return if (value == Int.MIN_VALUE) 0 else abs(value)
    }

    internal fun mixedHash(x: Int, z: Int, salt: Long): Long {
        var hash = seed xor (x.toLong() * 341873128712L) xor (z.toLong() * 132897987541L) xor (salt * 31L)
        hash = hash xor (hash ushr 29)
        hash *= -7046029254386353131L
        hash = hash xor (hash ushr 26)
        hash *= -4658895280553007687L
        return hash xor (hash ushr 32)
    }

    private fun nearestAnchor(chunkX: Int, chunkZ: Int): Anchor {
        val cellX = Math.floorDiv(chunkX, CITY_CELL_CHUNKS)
        val cellZ = Math.floorDiv(chunkZ, CITY_CELL_CHUNKS)
        var best = anchor(cellX, cellZ)
        var bestDistance = Double.MAX_VALUE
        for (x in (cellX - ANCHOR_SEARCH_RADIUS_CELLS)..(cellX + ANCHOR_SEARCH_RADIUS_CELLS)) {
            for (z in (cellZ - ANCHOR_SEARCH_RADIUS_CELLS)..(cellZ + ANCHOR_SEARCH_RADIUS_CELLS)) {
                val candidate = anchor(x, z)
                if (!candidate.active) continue
                val distance = hypot(
                    (chunkX - candidate.centerChunkX).toDouble(),
                    (chunkZ - candidate.centerChunkZ).toDouble()
                )
                if (distance < bestDistance) {
                    best = candidate
                    bestDistance = distance
                }
            }
        }
        return best
    }

    /**
     * Every active city links to the next active city to its east and south. The two networks use
     * opposite L-shaped routes so highways and subways do not simply occupy the same chunks.
     */
    private fun transitAxes(chunkX: Int, chunkZ: Int, underground: Boolean): Axes {
        val cellX = Math.floorDiv(chunkX, CITY_CELL_CHUNKS)
        val cellZ = Math.floorDiv(chunkZ, CITY_CELL_CHUNKS)
        var result = Axes()

        val search = MAX_LINK_DISTANCE_CELLS + 1
        for (sourceCellX in (cellX - search)..(cellX + 1)) {
            for (sourceCellZ in (cellZ - search)..(cellZ + 1)) {
                val source = anchor(sourceCellX, sourceCellZ)
                if (!source.active) continue

                eastNeighbor(source)?.let { destination ->
                    result += routeAxes(chunkX, chunkZ, source, destination, eastLink = true, underground)
                }
                southNeighbor(source)?.let { destination ->
                    result += routeAxes(chunkX, chunkZ, source, destination, eastLink = false, underground)
                }
                if (result.eastWest && result.northSouth) return result
            }
        }
        return result
    }

    private fun firstActiveNeighbor(source: Anchor, stepX: Int, stepZ: Int): Anchor? {
        for (distance in 1..MAX_LINK_DISTANCE_CELLS) {
            val candidate = anchor(source.cellX + stepX * distance, source.cellZ + stepZ * distance)
            if (candidate.active) return candidate
        }
        return null
    }

    private fun routeAxes(
        chunkX: Int,
        chunkZ: Int,
        source: Anchor,
        destination: Anchor,
        eastLink: Boolean,
        underground: Boolean
    ): Axes {
        val horizontalZ: Int
        val verticalX: Int

        if (eastLink) {
            // Subway bends at the source side; the highway bends at the destination side.
            horizontalZ = if (underground) destination.centerChunkZ else source.centerChunkZ
            verticalX = if (underground) source.centerChunkX else destination.centerChunkX
        } else {
            // For north/south links, use the other corner of the bounding rectangle.
            horizontalZ = if (underground) source.centerChunkZ else destination.centerChunkZ
            verticalX = if (underground) destination.centerChunkX else source.centerChunkX
        }

        val onHorizontal = chunkZ == horizontalZ && chunkX.between(source.centerChunkX, destination.centerChunkX)
        val onVertical = chunkX == verticalX && chunkZ.between(source.centerChunkZ, destination.centerChunkZ)
        return Axes(eastWest = onHorizontal, northSouth = onVertical)
    }

    private fun rangedHash(x: Int, z: Int, salt: Long, minValue: Int, maxValue: Int): Int {
        if (maxValue <= minValue) return minValue
        val span = maxValue - minValue + 1
        return minValue + Math.floorMod(mixedHash(x, z, salt), span.toLong()).toInt()
    }

    private fun Int.between(first: Int, second: Int): Boolean = this in minOf(first, second)..maxOf(first, second)
}
