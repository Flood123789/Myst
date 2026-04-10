package mystcraft.flood.generation

import net.minecraft.registry.Registry
import net.minecraft.world.biome.Biome
import net.minecraft.util.Identifier
import kotlin.math.max

object BiosphereLayout {
    const val MAIN_CENTER_Y = 104
    private const val CELL_SPACING = 176
    private const val SURFACE_MIN_RADIUS = 26
    private const val SURFACE_RADIUS_VARIATION = 26
    private const val CAVE_MIN_RADIUS = 22
    private const val CAVE_RADIUS_VARIATION = 16

    data class SpherePlacement(
        val key: String,
        val cellX: Int,
        val cellZ: Int,
        val centerX: Int,
        val centerY: Int,
        val centerZ: Int,
        val radius: Int,
        val cave: Boolean,
        val biomeId: Identifier,
        val styleSeed: Int
    )

    data class BridgePlacement(
        val fromKey: String,
        val toKey: String
    )

    data class Layout(
        val spheres: List<SpherePlacement>,
        val bridges: List<BridgePlacement>
    )

    fun collectBiomeIds(biomeRegistry: Registry<Biome>, caveOnly: Boolean): List<Identifier> {
        val ids = biomeRegistry.ids
            .filterNot { it.path == "the_void" }
            .filter { id ->
                val caveLike = isCaveBiome(id)
                if (caveOnly) caveLike else !caveLike
            }
            .sortedBy { it.toString() }

        if (ids.isNotEmpty()) return ids

        val fallback = biomeRegistry.ids.filterNot { it.path == "the_void" }.sortedBy { it.toString() }
        return if (fallback.isNotEmpty()) fallback else listOf(Identifier("minecraft", "plains"))
    }

    fun generate(seed: Long, surfaceBiomeIds: List<Identifier>, caveBiomeIds: List<Identifier>, minX: Int, maxX: Int, minZ: Int, maxZ: Int): Layout {
        val cellMinX = floorDiv(minX, CELL_SPACING) - 1
        val cellMaxX = floorDiv(maxX, CELL_SPACING) + 1
        val cellMinZ = floorDiv(minZ, CELL_SPACING) - 1
        val cellMaxZ = floorDiv(maxZ, CELL_SPACING) + 1

        val sphereMap = linkedMapOf<String, SpherePlacement>()
        val bridges = mutableListOf<BridgePlacement>()

        for (cellX in cellMinX..cellMaxX) {
            for (cellZ in cellMinZ..cellMaxZ) {
                val surface = surfaceSphere(seed, cellX, cellZ, surfaceBiomeIds)
                if (intersects(surface, minX, maxX, minZ, maxZ, CELL_SPACING)) {
                    sphereMap[surface.key] = surface
                }

                val cave = caveSphere(seed, surface, caveBiomeIds)
                if (cave != null && intersects(cave, minX, maxX, minZ, maxZ, CELL_SPACING / 2)) {
                    sphereMap[cave.key] = cave
                    bridges += BridgePlacement(surface.key, cave.key)
                }

                val east = surfaceSphere(seed, cellX + 1, cellZ, surfaceBiomeIds)
                if (bridgeTouchesRegion(surface, east, minX, maxX, minZ, maxZ)) {
                    sphereMap[surface.key] = surface
                    sphereMap[east.key] = east
                    bridges += BridgePlacement(surface.key, east.key)
                }

                val south = surfaceSphere(seed, cellX, cellZ + 1, surfaceBiomeIds)
                if (bridgeTouchesRegion(surface, south, minX, maxX, minZ, maxZ)) {
                    sphereMap[surface.key] = surface
                    sphereMap[south.key] = south
                    bridges += BridgePlacement(surface.key, south.key)
                }
            }
        }

        return Layout(sphereMap.values.toList(), bridges.distinct())
    }

    fun nearestSphere(seed: Long, surfaceBiomeIds: List<Identifier>, caveBiomeIds: List<Identifier>, x: Int, z: Int): List<SpherePlacement> {
        return generate(seed, surfaceBiomeIds, caveBiomeIds, x - CELL_SPACING, x + CELL_SPACING, z - CELL_SPACING, z + CELL_SPACING).spheres
    }

    private fun surfaceSphere(seed: Long, cellX: Int, cellZ: Int, biomeIds: List<Identifier>): SpherePlacement {
        if (cellX == 0 && cellZ == 0) {
            return SpherePlacement("s:0,0", 0, 0, 0, MAIN_CENTER_Y, 0, SURFACE_MIN_RADIUS, false, pickBiome(seed, 0, 0, biomeIds), layoutHash(seed, 0, 0))
        }

        val hash = layoutHash(seed, cellX, cellZ)
        val jitterX = (hash % 51) - 25
        val jitterZ = ((hash / 7) % 51) - 25
        val radius = SURFACE_MIN_RADIUS + ((hash / 17) % (SURFACE_RADIUS_VARIATION + 1))
        val centerY = MAIN_CENTER_Y + (((hash / 31) % 29) - 14)
        return SpherePlacement(
            "s:$cellX,$cellZ",
            cellX,
            cellZ,
            cellX * CELL_SPACING + jitterX,
            centerY,
            cellZ * CELL_SPACING + jitterZ,
            radius,
            false,
            pickBiome(seed, cellX, cellZ, biomeIds),
            hash
        )
    }

    private fun caveSphere(seed: Long, surface: SpherePlacement, biomeIds: List<Identifier>): SpherePlacement? {
        val caveHash = layoutHash(seed xor 0x5F3759DFL, surface.cellX, surface.cellZ)
        if (caveHash % 3 != 0) return null

        val offsetX = (caveHash % 25) - 12
        val offsetZ = ((caveHash / 11) % 25) - 12
        val radius = CAVE_MIN_RADIUS + ((caveHash / 17) % (CAVE_RADIUS_VARIATION + 1))
        val centerY = surface.centerY - (44 + ((caveHash / 23) % 28))

        return SpherePlacement(
            "c:${surface.cellX},${surface.cellZ}",
            surface.cellX,
            surface.cellZ,
            surface.centerX + offsetX,
            centerY,
            surface.centerZ + offsetZ,
            radius,
            true,
            pickBiome(seed xor 0x5F3759DFL, surface.cellX, surface.cellZ, biomeIds),
            caveHash
        )
    }

    private fun pickBiome(seed: Long, cellX: Int, cellZ: Int, ids: List<Identifier>): Identifier {
        if (ids.isEmpty()) return Identifier("minecraft", "plains")
        val hash = layoutHash(seed, cellX, cellZ)
        return ids[hash % max(ids.size, 1)]
    }

    private fun bridgeTouchesRegion(from: SpherePlacement, to: SpherePlacement, minX: Int, maxX: Int, minZ: Int, maxZ: Int): Boolean {
        val bridgeMinX = minOf(from.centerX, to.centerX) - 6
        val bridgeMaxX = maxOf(from.centerX, to.centerX) + 6
        val bridgeMinZ = minOf(from.centerZ, to.centerZ) - 6
        val bridgeMaxZ = maxOf(from.centerZ, to.centerZ) + 6
        return bridgeMaxX >= minX && bridgeMinX <= maxX && bridgeMaxZ >= minZ && bridgeMinZ <= maxZ
    }

    private fun intersects(sphere: SpherePlacement, minX: Int, maxX: Int, minZ: Int, maxZ: Int, margin: Int): Boolean {
        return sphere.centerX + sphere.radius + margin >= minX &&
            sphere.centerX - sphere.radius - margin <= maxX &&
            sphere.centerZ + sphere.radius + margin >= minZ &&
            sphere.centerZ - sphere.radius - margin <= maxZ
    }

    private fun isCaveBiome(id: Identifier): Boolean {
        val path = id.path
        return "cave" in path || "lush" in path || "dripstone" in path || "deep_dark" in path || "skulk" in path
    }

    private fun layoutHash(seed: Long, a: Int, b: Int): Int {
        var hash = seed.toInt() * 73428767
        hash = hash xor (a * 912931)
        hash = hash xor (b * 438289)
        return hash and Int.MAX_VALUE
    }

    private fun floorDiv(value: Int, divisor: Int): Int {
        return Math.floorDiv(value, divisor)
    }
}
