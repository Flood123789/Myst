package mystcraft.flood.generation

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.LadderBlock
import net.minecraft.block.PoweredRailBlock
import net.minecraft.block.enums.RailShape
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.ChunkRegion
import net.minecraft.world.HeightLimitView
import net.minecraft.world.Heightmap
import net.minecraft.world.biome.source.BiomeAccess
import net.minecraft.world.biome.source.BiomeSource
import net.minecraft.world.chunk.Chunk
import net.minecraft.world.gen.GenerationStep
import net.minecraft.world.gen.StructureAccessor
import net.minecraft.world.gen.chunk.Blender
import net.minecraft.world.gen.chunk.ChunkGenerator
import net.minecraft.world.gen.chunk.VerticalBlockSample
import net.minecraft.world.gen.noise.NoiseConfig
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class LostCityChunkGenerator(
    private val delegate: ChunkGenerator,
    private val cityBiomeSource: BiomeSource
) : ChunkGenerator(cityBiomeSource) {

    companion object {
        private const val CITY_CELL_CHUNKS = 48
        private const val CITY_RADIUS_CHUNKS = 21
        private const val CITY_CENTER_JITTER_CHUNKS = 4
        private const val CITY_GROUND_Y = 72
        private const val ROAD_GRID_CHUNKS = 4
        private const val AVENUE_GRID_CHUNKS = 8
        private const val RAIL_GRID_CHUNKS = 20
        private const val RAIL_Y = 28
        private const val HIGHWAY_Y = 86
        private const val CLEAR_TOP_Y = 190

        val CODEC: Codec<LostCityChunkGenerator> = RecordCodecBuilder.create { instance ->
            instance.group(
                ChunkGenerator.CODEC.fieldOf("delegate").forGetter(LostCityChunkGenerator::delegate),
                BiomeSource.CODEC.fieldOf("biome_source").forGetter(LostCityChunkGenerator::cityBiomeSource)
            ).apply(instance, ::LostCityChunkGenerator)
        }
    }

    private enum class CityChunkKind {
        OUTSIDE,
        HIGHWAY_EW,
        HIGHWAY_NS,
        ROAD,
        AVENUE,
        STATION,
        BUILDING,
        PLAZA,
        PARK
    }

    private data class CityAnchor(
        val cellX: Int,
        val cellZ: Int,
        val centerChunkX: Int,
        val centerChunkZ: Int,
        val radiusChunks: Int,
        val style: Int
    )

    private data class CityPlan(
        val chunkX: Int,
        val chunkZ: Int,
        val anchor: CityAnchor,
        val localChunkX: Int,
        val localChunkZ: Int,
        val cityFactor: Double,
        val kind: CityChunkKind,
        val roadNS: Boolean,
        val roadEW: Boolean,
        val subwayNS: Boolean,
        val subwayEW: Boolean,
        val highwayNS: Boolean,
        val highwayEW: Boolean
    ) {
        val inCity: Boolean = cityFactor > 0.0
        val groundY: Int = CITY_GROUND_Y
    }

    override fun getCodec(): Codec<out ChunkGenerator> = CODEC

    override fun carve(
        region: ChunkRegion,
        seed: Long,
        noiseConfig: NoiseConfig,
        biomeAccess: BiomeAccess,
        structureAccessor: StructureAccessor,
        chunk: Chunk,
        carverStep: GenerationStep.Carver
    ) {
        delegate.carve(region, seed, noiseConfig, biomeAccess, structureAccessor, chunk, carverStep)
    }

    override fun buildSurface(
        region: ChunkRegion,
        structures: StructureAccessor,
        noiseConfig: NoiseConfig,
        chunk: Chunk
    ) {
        delegate.buildSurface(region, structures, noiseConfig, chunk)
        generateCityChunk(chunk)
    }

    override fun populateEntities(region: ChunkRegion) {
        delegate.populateEntities(region)
    }

    override fun populateNoise(
        executor: Executor,
        blender: Blender,
        noiseConfig: NoiseConfig,
        structureAccessor: StructureAccessor,
        chunk: Chunk
    ): CompletableFuture<Chunk> {
        return delegate.populateNoise(executor, blender, noiseConfig, structureAccessor, chunk)
    }

    override fun getWorldHeight(): Int = delegate.worldHeight

    override fun getSeaLevel(): Int = delegate.seaLevel

    override fun getMinimumY(): Int = delegate.minimumY

    override fun getHeight(
        x: Int,
        z: Int,
        heightmap: Heightmap.Type,
        world: HeightLimitView,
        noiseConfig: NoiseConfig
    ): Int {
        val delegateHeight = delegate.getHeight(x, z, heightmap, world, noiseConfig)
        val plan = cityPlan(Math.floorDiv(x, 16), Math.floorDiv(z, 16))
        if (!plan.inCity && !plan.highwayEW && !plan.highwayNS) {
            return delegateHeight
        }
        val cityHeight = if (plan.inCity) plan.groundY + 1 else delegateHeight
        val highwayHeight = if (plan.highwayEW || plan.highwayNS) {
            HIGHWAY_Y + LostCityAssetLibrary.partHeight("highway_open") + 1
        } else {
            delegateHeight
        }
        return max(delegateHeight, max(cityHeight, highwayHeight))
    }

    override fun getColumnSample(
        x: Int,
        z: Int,
        world: HeightLimitView,
        noiseConfig: NoiseConfig
    ): VerticalBlockSample {
        return delegate.getColumnSample(x, z, world, noiseConfig)
    }

    override fun getDebugHudText(text: MutableList<String>, noiseConfig: NoiseConfig, pos: BlockPos) {
        delegate.getDebugHudText(text, noiseConfig, pos)
        text.add("Mystcraft Lost City Generator")
    }

    override fun getSpawnHeight(world: HeightLimitView): Int = CITY_GROUND_Y + 2

    private fun generateCityChunk(chunk: Chunk) {
        val chunkX = chunk.pos.x
        val chunkZ = chunk.pos.z
        val plan = cityPlan(chunkX, chunkZ)
        if (!plan.inCity && !plan.highwayEW && !plan.highwayNS && !plan.subwayEW && !plan.subwayNS) {
            return
        }

        if (plan.inCity) {
            flattenCityChunk(chunk, plan)
            when (plan.kind) {
                CityChunkKind.ROAD, CityChunkKind.AVENUE, CityChunkKind.STATION -> generateRoad(chunk, plan)
                CityChunkKind.BUILDING -> generateBuilding(chunk, plan)
                CityChunkKind.PARK -> generatePark(chunk, plan)
                CityChunkKind.PLAZA -> generatePlaza(chunk, plan)
                else -> {}
            }
        }

        if (plan.subwayEW || plan.subwayNS) {
            generateSubway(chunk, plan)
        }
        if (plan.kind == CityChunkKind.STATION) {
            generateStation(chunk, plan)
        }
        if (plan.highwayEW || plan.highwayNS) {
            generateHighway(chunk, plan)
        }
    }

    private fun cityPlan(chunkX: Int, chunkZ: Int): CityPlan {
        val anchor = nearestAnchor(chunkX, chunkZ)
        val localChunkX = chunkX - anchor.centerChunkX
        val localChunkZ = chunkZ - anchor.centerChunkZ
        val distance = hypot(localChunkX.toDouble(), localChunkZ.toDouble())
        val cityFactor = ((anchor.radiusChunks - distance) / 5.0).coerceIn(0.0, 1.0)
        val inCity = cityFactor > 0.0

        val railX = Math.floorMod(chunkX + 1, RAIL_GRID_CHUNKS)
        val railZ = Math.floorMod(chunkZ + 1, RAIL_GRID_CHUNKS)
        val railArea = distance <= anchor.radiusChunks + 12
        val subwayNS = railArea && (railX == 5 || railX == 15)
        val subwayEW = railArea && (railZ == 0 || railZ == RAIL_GRID_CHUNKS / 2)
        val station = inCity && cityFactor > 0.15 && (
            railX == 0 && railZ == RAIL_GRID_CHUNKS / 2 ||
                railX == RAIL_GRID_CHUNKS / 2 && railZ == 0 ||
                railX == RAIL_GRID_CHUNKS / 2 && railZ == RAIL_GRID_CHUNKS / 2
            )

        val highwayEW = distance <= anchor.radiusChunks + 10 && abs(localChunkZ) <= 1
        val highwayNS = distance <= anchor.radiusChunks + 10 && abs(localChunkX) <= 1

        val roadNS = inCity && (Math.floorMod(localChunkX, ROAD_GRID_CHUNKS) == 0 || station || highwayNS)
        val roadEW = inCity && (Math.floorMod(localChunkZ, ROAD_GRID_CHUNKS) == 0 || station || highwayEW)
        val avenue = roadNS && Math.floorMod(localChunkX, AVENUE_GRID_CHUNKS) == 0 ||
            roadEW && Math.floorMod(localChunkZ, AVENUE_GRID_CHUNKS) == 0

        val kind = when {
            station -> CityChunkKind.STATION
            !inCity -> CityChunkKind.OUTSIDE
            highwayEW || highwayNS -> CityChunkKind.AVENUE
            roadNS || roadEW -> if (avenue) CityChunkKind.AVENUE else CityChunkKind.ROAD
            cityFactor < 0.28 && positiveHash(chunkX, chunkZ, 11L) % 5 == 0 -> CityChunkKind.PARK
            positiveHash(chunkX, chunkZ, 17L) % 100 < buildingChance(cityFactor) -> CityChunkKind.BUILDING
            positiveHash(chunkX, chunkZ, 23L) % 7 == 0 -> CityChunkKind.PARK
            else -> CityChunkKind.PLAZA
        }

        return CityPlan(
            chunkX = chunkX,
            chunkZ = chunkZ,
            anchor = anchor,
            localChunkX = localChunkX,
            localChunkZ = localChunkZ,
            cityFactor = cityFactor,
            kind = kind,
            roadNS = roadNS,
            roadEW = roadEW,
            subwayNS = subwayNS,
            subwayEW = subwayEW,
            highwayNS = highwayNS,
            highwayEW = highwayEW
        )
    }

    private fun nearestAnchor(chunkX: Int, chunkZ: Int): CityAnchor {
        val cellX = Math.floorDiv(chunkX, CITY_CELL_CHUNKS)
        val cellZ = Math.floorDiv(chunkZ, CITY_CELL_CHUNKS)
        var best = cityAnchor(cellX, cellZ)
        var bestDist = Double.MAX_VALUE
        for (x in (cellX - 1)..(cellX + 1)) {
            for (z in (cellZ - 1)..(cellZ + 1)) {
                val anchor = cityAnchor(x, z)
                val dist = hypot((chunkX - anchor.centerChunkX).toDouble(), (chunkZ - anchor.centerChunkZ).toDouble())
                if (dist < bestDist) {
                    best = anchor
                    bestDist = dist
                }
            }
        }
        return best
    }

    private fun cityAnchor(cellX: Int, cellZ: Int): CityAnchor {
        val jitterX = rangedHash(cellX, cellZ, 31L, -CITY_CENTER_JITTER_CHUNKS, CITY_CENTER_JITTER_CHUNKS)
        val jitterZ = rangedHash(cellX, cellZ, 37L, -CITY_CENTER_JITTER_CHUNKS, CITY_CENTER_JITTER_CHUNKS)
        return CityAnchor(
            cellX = cellX,
            cellZ = cellZ,
            centerChunkX = cellX * CITY_CELL_CHUNKS + CITY_CELL_CHUNKS / 2 + jitterX,
            centerChunkZ = cellZ * CITY_CELL_CHUNKS + CITY_CELL_CHUNKS / 2 + jitterZ,
            radiusChunks = CITY_RADIUS_CHUNKS + rangedHash(cellX, cellZ, 41L, -3, 5),
            style = rangedHash(cellX, cellZ, 43L, 0, 3)
        )
    }

    private fun highwayKind(chunkX: Int, chunkZ: Int, anchor: CityAnchor, distanceToAnchor: Double): CityChunkKind? {
        if (distanceToAnchor <= anchor.radiusChunks + 3) return null
        val east = cityAnchor(anchor.cellX + 1, anchor.cellZ)
        val south = cityAnchor(anchor.cellX, anchor.cellZ + 1)
        if (nearSegment(chunkX, chunkZ, anchor.centerChunkX, anchor.centerChunkZ, east.centerChunkX, east.centerChunkZ, anchor.radiusChunks, east.radiusChunks)) {
            return CityChunkKind.HIGHWAY_EW
        }
        if (nearSegment(chunkX, chunkZ, anchor.centerChunkX, anchor.centerChunkZ, south.centerChunkX, south.centerChunkZ, anchor.radiusChunks, south.radiusChunks)) {
            return CityChunkKind.HIGHWAY_NS
        }
        return null
    }

    private fun nearSegment(
        chunkX: Int,
        chunkZ: Int,
        x1: Int,
        z1: Int,
        x2: Int,
        z2: Int,
        radius1: Int,
        radius2: Int
    ): Boolean {
        val dx = x2 - x1
        val dz = z2 - z1
        val len2 = dx * dx + dz * dz
        if (len2 <= 0) return false
        val t = (((chunkX - x1) * dx + (chunkZ - z1) * dz).toDouble() / len2.toDouble()).coerceIn(0.0, 1.0)
        val along = t * kotlin.math.sqrt(len2.toDouble())
        if (along < radius1 + 4 || along > kotlin.math.sqrt(len2.toDouble()) - radius2 - 4) return false
        val cx = x1 + dx * t
        val cz = z1 + dz * t
        return hypot(chunkX - cx, chunkZ - cz) <= 1.25
    }

    private fun flattenCityChunk(chunk: Chunk, plan: CityPlan) {
        val baseX = chunk.pos.startX
        val baseZ = chunk.pos.startZ
        val floor = when (plan.kind) {
            CityChunkKind.PARK -> Blocks.GRASS_BLOCK.defaultState
            CityChunkKind.BUILDING -> lotBlock(plan)
            else -> Blocks.SMOOTH_STONE.defaultState
        }

        for (x in 0..15) {
            for (z in 0..15) {
                val wx = baseX + x
                val wz = baseZ + z
                for (y in (plan.groundY + 1)..CLEAR_TOP_Y) {
                    set(chunk, wx, y, wz, Blocks.AIR.defaultState)
                }
                for (y in (plan.groundY - 8) until plan.groundY) {
                    val existing = chunk.getBlockState(BlockPos(wx, y, wz))
                    if (existing.isAir || existing.block == Blocks.WATER || existing.block == Blocks.LAVA) {
                        set(chunk, wx, y, wz, Blocks.STONE.defaultState)
                    }
                }
                set(chunk, wx, plan.groundY, wz, floor)
            }
        }
    }

    private fun generateRoad(chunk: Chunk, plan: CityPlan) {
        val baseX = chunk.pos.startX
        val baseZ = chunk.pos.startZ
        val road = if (plan.kind == CityChunkKind.AVENUE) Blocks.BLACK_CONCRETE.defaultState else Blocks.GRAY_CONCRETE.defaultState
        val stripe = Blocks.WHITE_CONCRETE.defaultState
        val median = Blocks.YELLOW_CONCRETE.defaultState

        for (x in 0..15) {
            for (z in 0..15) {
                val wx = baseX + x
                val wz = baseZ + z
                set(chunk, wx, plan.groundY, wz, road)
                if (plan.roadNS && (x == 7 || x == 8) && z % 4 != 0) {
                    set(chunk, wx, plan.groundY, wz, if (plan.kind == CityChunkKind.AVENUE) median else stripe)
                }
                if (plan.roadEW && (z == 7 || z == 8) && x % 4 != 0) {
                    set(chunk, wx, plan.groundY, wz, if (plan.kind == CityChunkKind.AVENUE) median else stripe)
                }
                val sidewalk = (x <= 1 || x >= 14 || z <= 1 || z >= 14) && plan.kind != CityChunkKind.STATION
                if (sidewalk) {
                    set(chunk, wx, plan.groundY, wz, Blocks.SMOOTH_STONE.defaultState)
                }
            }
        }

        if (plan.kind == CityChunkKind.AVENUE || plan.kind == CityChunkKind.STATION) {
            for ((lx, lz) in listOf(2 to 2, 13 to 2, 2 to 13, 13 to 13)) {
                lamp(chunk, baseX + lx, plan.groundY + 1, baseZ + lz)
            }
        }
    }

    private fun generateBuilding(chunk: Chunk, plan: CityPlan) {
        val baseX = chunk.pos.startX
        val baseZ = chunk.pos.startZ
        val hash = mixedHash(plan.chunkX, plan.chunkZ, 101L)
        val floors = (2 + (plan.cityFactor * 7.0).toInt() + positiveHash(plan.chunkX, plan.chunkZ, 103L) % 3)
            .coerceIn(2, 11)
        LostCityAssetLibrary.placeBuilding(
            baseX = baseX,
            baseY = plan.groundY,
            baseZ = baseZ,
            floors = floors,
            seed = hash,
            style = plan.anchor.style
        ) { x, y, z, state ->
            set(chunk, x, y, z, state)
        }
    }

    private fun generatePark(chunk: Chunk, plan: CityPlan) {
        val baseX = chunk.pos.startX
        val baseZ = chunk.pos.startZ
        for (x in 2..13) {
            for (z in 2..13) {
                val dx = x - 8
                val dz = z - 8
                val path = abs(dx) <= 1 || abs(dz) <= 1
                set(chunk, baseX + x, plan.groundY, baseZ + z, if (path) Blocks.SMOOTH_STONE.defaultState else Blocks.GRASS_BLOCK.defaultState)
            }
        }
        for ((tx, tz) in listOf(4 to 4, 12 to 4, 4 to 12, 12 to 12)) {
            set(chunk, baseX + tx, plan.groundY + 1, baseZ + tz, Blocks.OAK_LOG.defaultState)
            set(chunk, baseX + tx, plan.groundY + 2, baseZ + tz, Blocks.OAK_LOG.defaultState)
            for (dx in -1..1) {
                for (dz in -1..1) {
                    set(chunk, baseX + tx + dx, plan.groundY + 3, baseZ + tz + dz, Blocks.OAK_LEAVES.defaultState)
                }
            }
        }
    }

    private fun generatePlaza(chunk: Chunk, plan: CityPlan) {
        val baseX = chunk.pos.startX
        val baseZ = chunk.pos.startZ
        val accent = if (plan.anchor.style % 2 == 0) Blocks.POLISHED_ANDESITE.defaultState else Blocks.STONE_BRICKS.defaultState
        for (x in 4..11) {
            set(chunk, baseX + x, plan.groundY, baseZ + 4, accent)
            set(chunk, baseX + x, plan.groundY, baseZ + 11, accent)
        }
        for (z in 4..11) {
            set(chunk, baseX + 4, plan.groundY, baseZ + z, accent)
            set(chunk, baseX + 11, plan.groundY, baseZ + z, accent)
        }
        if (positiveHash(plan.chunkX, plan.chunkZ, 131L) % 3 == 0) {
            lamp(chunk, baseX + 8, plan.groundY + 1, baseZ + 8)
        }
    }

    private fun generateHighway(chunk: Chunk, plan: CityPlan) {
        val baseX = chunk.pos.startX
        val baseZ = chunk.pos.startZ
        val eastWest = plan.highwayEW || !plan.highwayNS
        val rotation = if (eastWest) 0 else 1
        val part = if (plan.highwayEW && plan.highwayNS) "highway_open_bi" else "highway_open"
        val height = LostCityAssetLibrary.partHeight(part)
        for (x in 0..15) {
            for (z in 0..15) {
                val wx = baseX + x
                val wz = baseZ + z
                for (y in (HIGHWAY_Y + 1)..(HIGHWAY_Y + height + 1)) {
                    set(chunk, wx, y, wz, Blocks.AIR.defaultState)
                }
            }
        }
        LostCityAssetLibrary.placePart(part, baseX, HIGHWAY_Y, baseZ, mixedHash(plan.chunkX, plan.chunkZ, 151L), plan.anchor.style, { x, y, z, state ->
            set(chunk, x, y, z, state)
        }, rotation = rotation, voidAsAir = true)

        val supports = if (eastWest) {
            listOf(2 to 1, 13 to 1, 2 to 14, 13 to 14)
        } else {
            listOf(1 to 2, 1 to 13, 14 to 2, 14 to 13)
        }
        for ((x, z) in supports) {
            supportColumn(chunk, baseX + x, baseZ + z, HIGHWAY_Y - 1)
        }
    }

    private fun generateSubway(chunk: Chunk, plan: CityPlan) {
        val baseX = chunk.pos.startX
        val baseZ = chunk.pos.startZ
        val railPart = railPart(plan) ?: return
        LostCityAssetLibrary.placePart(
            partName = railPart,
            baseX = baseX,
            baseY = RAIL_Y - 1,
            baseZ = baseZ,
            seed = mixedHash(plan.chunkX, plan.chunkZ, 173L),
            style = plan.anchor.style,
            setBlock = { x, y, z, state -> set(chunk, x, y, z, state) },
            voidAsAir = true
        )
    }

    private fun generateStation(chunk: Chunk, plan: CityPlan) {
        val baseX = chunk.pos.startX
        val baseZ = chunk.pos.startZ
        LostCityAssetLibrary.placePart(
            partName = "station_underground_stairs",
            baseX = baseX,
            baseY = RAIL_Y - 1,
            baseZ = baseZ,
            seed = mixedHash(plan.chunkX, plan.chunkZ, 179L),
            style = plan.anchor.style,
            setBlock = { x, y, z, state -> set(chunk, x, y, z, state) },
            voidAsAir = true
        )
        LostCityAssetLibrary.placePart(
            partName = "station_openroof",
            baseX = baseX,
            baseY = plan.groundY,
            baseZ = baseZ,
            seed = mixedHash(plan.chunkX, plan.chunkZ, 181L),
            style = plan.anchor.style,
            setBlock = { x, y, z, state -> set(chunk, x, y, z, state) },
            voidAsAir = true
        )
        for (x in 2..13) {
            for (z in 2..13) {
                val edge = x == 2 || x == 13 || z == 2 || z == 13
                for (y in (RAIL_Y - 1)..(RAIL_Y + 6)) {
                    val state = when {
                        y == RAIL_Y - 1 -> Blocks.POLISHED_ANDESITE.defaultState
                        y == RAIL_Y + 6 -> Blocks.STONE_BRICKS.defaultState
                        edge -> Blocks.STONE_BRICKS.defaultState
                        else -> Blocks.AIR.defaultState
                    }
                    set(chunk, baseX + x, y, baseZ + z, state)
                }
            }
        }
        for (x in 5..10) {
            for (z in 5..10) {
                val edge = x == 5 || x == 10 || z == 5 || z == 10
                for (y in (RAIL_Y + 7)..(plan.groundY + 5)) {
                    set(chunk, baseX + x, y, baseZ + z, if (edge) Blocks.STONE_BRICKS.defaultState else Blocks.AIR.defaultState)
                }
            }
        }
        for (y in (RAIL_Y + 1)..(plan.groundY + 3)) {
            set(chunk, baseX + 5, y, baseZ + 8, Blocks.LADDER.defaultState.with(LadderBlock.FACING, Direction.EAST))
            set(chunk, baseX + 6, y, baseZ + 8, Blocks.AIR.defaultState)
        }
        for (x in 4..11) {
            for (z in 4..11) {
                val edge = x == 4 || x == 11 || z == 4 || z == 11
                val doorway = z == 4 && x in 7..8
                set(chunk, baseX + x, plan.groundY + 1, baseZ + z, if (edge && !doorway) Blocks.STONE_BRICKS.defaultState else Blocks.AIR.defaultState)
                set(chunk, baseX + x, plan.groundY + 4, baseZ + z, Blocks.SMOOTH_STONE.defaultState)
            }
        }
        lamp(chunk, baseX + 4, RAIL_Y + 4, baseZ + 4)
        lamp(chunk, baseX + 11, RAIL_Y + 4, baseZ + 11)
    }

    private fun railPart(plan: CityPlan): String? {
        if (plan.kind == CityChunkKind.STATION) {
            return "station_underground"
        }
        val mx = Math.floorMod(plan.chunkX + 1, RAIL_GRID_CHUNKS)
        val mz = Math.floorMod(plan.chunkZ + 1, RAIL_GRID_CHUNKS)
        return when {
            mz == RAIL_GRID_CHUNKS / 2 && (mx == 5 || mx == 15) -> "rails_3split"
            mz == 0 && (mx == 5 || mx == 15) -> "rails_bend"
            plan.subwayEW -> "rails_horizontal"
            plan.subwayNS -> "rails_vertical"
            else -> null
        }
    }

    private fun tunnelCell(chunk: Chunk, x: Int, z: Int, wall: Boolean) {
        for (y in (RAIL_Y - 1)..(RAIL_Y + 4)) {
            val state = when {
                y == RAIL_Y - 1 || y == RAIL_Y + 4 || wall -> Blocks.STONE_BRICKS.defaultState
                else -> Blocks.AIR.defaultState
            }
            set(chunk, x, y, z, state)
        }
    }

    private fun rail(chunk: Chunk, x: Int, y: Int, z: Int, shape: RailShape, powered: Boolean) {
        if (powered) {
            set(
                chunk,
                x,
                y,
                z,
                Blocks.POWERED_RAIL.defaultState
                    .with(PoweredRailBlock.POWERED, true)
                    .with(Properties.STRAIGHT_RAIL_SHAPE, shape)
            )
            set(chunk, x, y - 1, z, Blocks.REDSTONE_BLOCK.defaultState)
        } else {
            set(chunk, x, y, z, Blocks.RAIL.defaultState.with(Properties.RAIL_SHAPE, shape))
        }
    }

    private fun supportColumn(chunk: Chunk, x: Int, z: Int, topY: Int) {
        var y = topY
        while (y >= minimumY) {
            val pos = BlockPos(x, y, z)
            val state = chunk.getBlockState(pos)
            set(chunk, x, y, z, Blocks.STONE_BRICKS.defaultState)
            if (!state.isAir && state.block != Blocks.WATER && state.block != Blocks.LAVA && y < topY - 3) break
            y--
        }
    }

    private fun lamp(chunk: Chunk, x: Int, y: Int, z: Int) {
        set(chunk, x, y, z, Blocks.STONE_BRICK_WALL.defaultState)
        set(chunk, x, y + 1, z, Blocks.STONE_BRICK_WALL.defaultState)
        set(chunk, x, y + 2, z, Blocks.SEA_LANTERN.defaultState)
    }

    private data class BuildingPalette(
        val foundation: BlockState,
        val floor: BlockState,
        val frame: BlockState,
        val window: BlockState
    )

    private fun buildingPalette(plan: CityPlan): BuildingPalette = when (plan.anchor.style) {
        0 -> BuildingPalette(Blocks.STONE_BRICKS.defaultState, Blocks.SMOOTH_STONE.defaultState, Blocks.DEEPSLATE_BRICKS.defaultState, Blocks.BLUE_STAINED_GLASS.defaultState)
        1 -> BuildingPalette(Blocks.BRICKS.defaultState, Blocks.BRICKS.defaultState, Blocks.TERRACOTTA.defaultState, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultState)
        2 -> BuildingPalette(Blocks.POLISHED_DIORITE.defaultState, Blocks.SMOOTH_QUARTZ.defaultState, Blocks.WHITE_CONCRETE.defaultState, Blocks.GRAY_STAINED_GLASS.defaultState)
        else -> BuildingPalette(Blocks.DEEPSLATE_TILES.defaultState, Blocks.POLISHED_DEEPSLATE.defaultState, Blocks.GRAY_CONCRETE.defaultState, Blocks.CYAN_STAINED_GLASS.defaultState)
    }

    private fun lotBlock(plan: CityPlan): BlockState = when (plan.anchor.style) {
        1 -> Blocks.PACKED_MUD.defaultState
        2 -> Blocks.SMOOTH_QUARTZ.defaultState
        else -> Blocks.SMOOTH_STONE.defaultState
    }

    private fun buildingChance(cityFactor: Double): Int = when {
        cityFactor > 0.78 -> 92
        cityFactor > 0.52 -> 76
        cityFactor > 0.28 -> 55
        else -> 28
    }

    private fun set(chunk: Chunk, x: Int, y: Int, z: Int, state: BlockState) {
        if (y < minimumY || y >= minimumY + worldHeight) return
        chunk.setBlockState(BlockPos(x, y, z), state, false)
    }

    private fun positiveHash(x: Int, z: Int, salt: Long): Int {
        val value = mixedHash(x, z, salt).toInt()
        return if (value == Int.MIN_VALUE) 0 else abs(value)
    }

    private fun rangedHash(x: Int, z: Int, salt: Long, minValue: Int, maxValue: Int): Int {
        if (maxValue <= minValue) return minValue
        val span = maxValue - minValue + 1
        return minValue + Math.floorMod(mixedHash(x, z, salt), span.toLong()).toInt()
    }

    private fun mixedHash(x: Int, z: Int, salt: Long): Long {
        var hash = x.toLong() * 341873128712L + z.toLong() * 132897987541L + salt * 31L
        hash = hash xor (hash ushr 29)
        hash *= 0x9E3779B1L
        hash = hash xor (hash ushr 26)
        return hash
    }
}
