package mystcraft.flood.generation

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import mystcraft.flood.MystcraftReforged
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.LadderBlock
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
import kotlin.math.max
import kotlin.math.min

class LostCityChunkGenerator(
    private val delegate: ChunkGenerator,
    private val cityBiomeSource: BiomeSource,
    private val seed: Long = 0L
) : ChunkGenerator(cityBiomeSource) {

    companion object {
        private const val CITY_GROUND_Y = LostCityLayout.CITY_GROUND_Y
        private const val ROAD_GRID_CHUNKS = LostCityLayout.ROAD_GRID_CHUNKS
        private const val AVENUE_GRID_CHUNKS = LostCityLayout.AVENUE_GRID_CHUNKS
        private const val RAIL_Y = 28
        private const val HIGHWAY_Y = 86

        val CODEC: Codec<LostCityChunkGenerator> = RecordCodecBuilder.create { instance ->
            instance.group(
                ChunkGenerator.CODEC.fieldOf("delegate").forGetter(LostCityChunkGenerator::delegate),
                BiomeSource.CODEC.fieldOf("biome_source").forGetter(LostCityChunkGenerator::cityBiomeSource),
                Codec.LONG.optionalFieldOf("seed", 0L).forGetter(LostCityChunkGenerator::seed)
            ).apply(instance, ::LostCityChunkGenerator)
        }
    }

    private val layout = LostCityLayout(seed)

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

    private data class CityPlan(
        val chunkX: Int,
        val chunkZ: Int,
        val anchor: LostCityLayout.Anchor,
        val localChunkX: Int,
        val localChunkZ: Int,
        val cityFactor: Double,
        val kind: CityChunkKind,
        val roadNS: Boolean,
        val roadEW: Boolean,
        val subwayNS: Boolean,
        val subwayEW: Boolean,
        val highwayNS: Boolean,
        val highwayEW: Boolean,
        val buildingName: String?
    ) {
        val inCity: Boolean = cityFactor > 0.0
        val groundY: Int = anchor.groundY
        val isSurfaceRoad: Boolean = kind == CityChunkKind.ROAD || kind == CityChunkKind.AVENUE || kind == CityChunkKind.STATION
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
        if (carverStep == GenerationStep.Carver.AIR) {
            try {
                generateCityChunk(chunk)
            } catch (error: Exception) {
                MystcraftReforged.LOGGER.error(
                    "Skipping failed Lost City chunk {},{} so world generation can continue",
                    chunk.pos.x,
                    chunk.pos.z,
                    error
                )
            }
        }
    }

    override fun buildSurface(
        region: ChunkRegion,
        structures: StructureAccessor,
        noiseConfig: NoiseConfig,
        chunk: Chunk
    ) {
        delegate.buildSurface(region, structures, noiseConfig, chunk)
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
        if (!LostCityAssetLibrary.canGenerateCities()) {
            return delegateHeight
        }
        val plan = cityPlan(Math.floorDiv(x, 16), Math.floorDiv(z, 16))
        if (!plan.inCity && !plan.highwayEW && !plan.highwayNS) {
            return delegateHeight
        }
        val cityHeight = if (plan.inCity) estimatedChunkTop(plan) else delegateHeight
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

    private fun estimatedChunkTop(plan: CityPlan): Int {
        return when (plan.kind) {
            CityChunkKind.BUILDING -> {
                val floors = (2 + (plan.cityFactor * 7.0).toInt() + positiveHash(plan.chunkX, plan.chunkZ, 103L) % 3)
                    .coerceIn(2, 11)
                plan.groundY + floors * 6 + 12
            }
            CityChunkKind.STATION -> plan.groundY + 12
            CityChunkKind.AVENUE, CityChunkKind.ROAD, CityChunkKind.PLAZA, CityChunkKind.PARK -> plan.groundY + 3
            else -> plan.groundY + 1
        }
    }

    private fun generateCityChunk(chunk: Chunk) {
        if (!LostCityAssetLibrary.canGenerateCities()) return

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
        val chunkLayout = layout.plan(chunkX, chunkZ)
        val anchor = chunkLayout.anchor
        val localChunkX = chunkLayout.localChunkX
        val localChunkZ = chunkLayout.localChunkZ
        val cityFactor = chunkLayout.cityFactor
        val inCity = chunkLayout.inCity
        val subwayNS = chunkLayout.subway.northSouth
        val subwayEW = chunkLayout.subway.eastWest
        val station = chunkLayout.station
        val highwayEW = chunkLayout.highway.eastWest
        val highwayNS = chunkLayout.highway.northSouth

        val roadNS = inCity && (Math.floorMod(localChunkX, ROAD_GRID_CHUNKS) == 0 || station)
        val roadEW = inCity && (Math.floorMod(localChunkZ, ROAD_GRID_CHUNKS) == 0 || station)
        val avenue = roadNS && Math.floorMod(localChunkX, AVENUE_GRID_CHUNKS) == 0 ||
            roadEW && Math.floorMod(localChunkZ, AVENUE_GRID_CHUNKS) == 0

        val buildingName = lostCityBuildingName(chunkX, chunkZ, localChunkX, localChunkZ, anchor, cityFactor)
        val canHostBuilding = inCity && !station && !roadNS && !roadEW && cityFactor > 0.12
        val kind = when {
            station -> CityChunkKind.STATION
            highwayEW -> CityChunkKind.HIGHWAY_EW
            highwayNS -> CityChunkKind.HIGHWAY_NS
            !inCity -> CityChunkKind.OUTSIDE
            roadNS || roadEW -> if (avenue) CityChunkKind.AVENUE else CityChunkKind.ROAD
            cityFactor < 0.28 && positiveHash(chunkX, chunkZ, 11L) % 5 == 0 -> CityChunkKind.PARK
            canHostBuilding && positiveHash(chunkX, chunkZ, 17L) % 100 < buildingChance(cityFactor) -> CityChunkKind.BUILDING
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
            highwayEW = highwayEW,
            buildingName = buildingName
        )
    }

    private fun flattenCityChunk(chunk: Chunk, plan: CityPlan) {
        val baseX = chunk.pos.startX
        val baseZ = chunk.pos.startZ
        val floor = when (plan.kind) {
            CityChunkKind.PARK -> Blocks.GRASS_BLOCK.defaultState
            CityChunkKind.BUILDING -> lotBlock(plan)
            else -> Blocks.SMOOTH_STONE.defaultState
        }

        val topY = minimumY + worldHeight - 1
        val mutablePos = BlockPos.Mutable()
        val airState = Blocks.AIR.defaultState
        val stoneState = Blocks.STONE.defaultState

        for (x in 0..15) {
            for (z in 0..15) {
                val wx = baseX + x
                val wz = baseZ + z
                val surfaceY = min(topY, max(plan.groundY + 2, chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG, x, z) + 4))
                for (y in (plan.groundY + 1)..surfaceY) {
                    mutablePos.set(wx, y, wz)
                    if (!chunk.getBlockState(mutablePos).isAir) {
                        chunk.setBlockState(mutablePos, airState, false)
                    }
                }
                for (y in (RAIL_Y - 1) until plan.groundY) {
                    mutablePos.set(wx, y, wz)
                    val existing = chunk.getBlockState(mutablePos)
                    if (existing.isAir || existing.block == Blocks.WATER || existing.block == Blocks.LAVA) {
                        chunk.setBlockState(mutablePos, stoneState, false)
                    }
                }
                set(chunk, wx, plan.groundY, wz, floor)
            }
        }

        generateBorderRetainingWalls(chunk, plan)
    }

    private fun generateBorderRetainingWalls(chunk: Chunk, plan: CityPlan) {
        val baseX = chunk.pos.startX
        val baseZ = chunk.pos.startZ
        val wallState = Blocks.STONE_BRICKS.defaultState

        val northBorder = !cityPlan(plan.chunkX, plan.chunkZ - 1).inCity
        val southBorder = !cityPlan(plan.chunkX, plan.chunkZ + 1).inCity
        val westBorder = !cityPlan(plan.chunkX - 1, plan.chunkZ).inCity
        val eastBorder = !cityPlan(plan.chunkX + 1, plan.chunkZ).inCity

        if (!northBorder && !southBorder && !westBorder && !eastBorder) return

        for (x in 0..15) {
            if (northBorder) {
                set(chunk, baseX + x, plan.groundY + 1, baseZ, wallState)
            }
            if (southBorder) {
                set(chunk, baseX + x, plan.groundY + 1, baseZ + 15, wallState)
            }
        }
        for (z in 0..15) {
            if (westBorder) {
                set(chunk, baseX, plan.groundY + 1, baseZ + z, wallState)
            }
            if (eastBorder) {
                set(chunk, baseX + 15, plan.groundY + 1, baseZ + z, wallState)
            }
        }
    }

    private fun generateRoad(chunk: Chunk, plan: CityPlan) {
        val baseX = chunk.pos.startX
        val baseZ = chunk.pos.startZ
        val (part, rotation) = streetPartFor(plan)
        LostCityAssetLibrary.placePart(
            partName = part,
            baseX = baseX,
            baseY = plan.groundY,
            baseZ = baseZ,
            seed = mixedHash(plan.chunkX, plan.chunkZ, 97L),
            style = plan.anchor.style,
            setBlock = { x, y, z, state -> set(chunk, x, y, z, state) },
            rotation = rotation,
            voidAsAir = false
        )

        if (plan.kind == CityChunkKind.AVENUE || plan.kind == CityChunkKind.STATION) {
            for ((lx, lz) in listOf(2 to 2, 13 to 2, 2 to 13, 13 to 13)) {
                lamp(chunk, baseX + lx, plan.groundY + 1, baseZ + lz)
            }
        }
    }

    private fun streetPartFor(plan: CityPlan): Pair<String, Int> {
        if (plan.kind == CityChunkKind.AVENUE || plan.kind == CityChunkKind.STATION) {
            return "street_all" to 0
        }

        val north = isRoadConnection(plan.chunkX, plan.chunkZ - 1)
        val south = isRoadConnection(plan.chunkX, plan.chunkZ + 1)
        val west = isRoadConnection(plan.chunkX - 1, plan.chunkZ)
        val east = isRoadConnection(plan.chunkX + 1, plan.chunkZ)
        val count = listOf(north, south, west, east).count { it }

        return when (count) {
            4 -> "street_all" to 0
            3 -> "street_t" to when {
                !south -> 0
                !west -> 1
                !north -> 2
                else -> 3
            }
            2 -> when {
                east && west -> "street_straight" to 0
                north && south -> "street_straight" to 1
                north && west -> "street_bend" to 0
                north && east -> "street_bend" to 1
                south && east -> "street_bend" to 2
                else -> "street_bend" to 3
            }
            1 -> "street_end" to when {
                west -> 0
                north -> 1
                east -> 2
                else -> 3
            }
            else -> "street_none" to 0
        }
    }

    private fun isRoadConnection(chunkX: Int, chunkZ: Int): Boolean {
        val neighbor = cityPlan(chunkX, chunkZ)
        return neighbor.isSurfaceRoad
    }

    private fun generateBuilding(chunk: Chunk, plan: CityPlan) {
        val baseX = chunk.pos.startX
        val baseZ = chunk.pos.startZ
        val hash = mixedHash(plan.chunkX, plan.chunkZ, 101L)
        val floors = (2 + (plan.cityFactor * 7.0).toInt() + positiveHash(plan.chunkX, plan.chunkZ, 103L) % 3)
            .coerceIn(2, 11)
        val placedName = plan.buildingName
        if (placedName != null) {
            LostCityAssetLibrary.placeBuilding(
                buildingName = placedName,
                baseX = baseX,
                baseY = plan.groundY,
                baseZ = baseZ,
                floors = floors,
                seed = hash,
                style = plan.anchor.style
            ) { x, y, z, state ->
                set(chunk, x, y, z, state)
            }
        } else {
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
    }

    private fun generatePark(chunk: Chunk, plan: CityPlan) {
        val baseX = chunk.pos.startX
        val baseZ = chunk.pos.startZ
        val parkParts = listOf("park_trees", "park_pool", "park_plants", "park_plants_pillars", "park_fountain1", "park_fountain2")
        val part = parkParts[positiveHash(plan.chunkX, plan.chunkZ, 127L) % parkParts.size]
        LostCityAssetLibrary.placePart(
            partName = part,
            baseX = baseX,
            baseY = plan.groundY + 1,
            baseZ = baseZ,
            seed = mixedHash(plan.chunkX, plan.chunkZ, 129L),
            style = plan.anchor.style,
            setBlock = { x, y, z, state -> set(chunk, x, y, z, state) },
            voidAsAir = false
        )
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
            val part = listOf("fountain1", "fountain2", "fountain3")[positiveHash(plan.chunkX, plan.chunkZ, 133L) % 3]
            LostCityAssetLibrary.placePart(
                partName = part,
                baseX = baseX,
                baseY = plan.groundY + 1,
                baseZ = baseZ,
                seed = mixedHash(plan.chunkX, plan.chunkZ, 135L),
                style = plan.anchor.style,
                setBlock = { x, y, z, state -> set(chunk, x, y, z, state) },
                voidAsAir = false
            )
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
        val (railPart, rotation) = railPart(plan) ?: return
        LostCityAssetLibrary.placePart(
            partName = railPart,
            baseX = baseX,
            baseY = RAIL_Y - 1,
            baseZ = baseZ,
            seed = mixedHash(plan.chunkX, plan.chunkZ, 173L),
            style = plan.anchor.style,
            setBlock = { x, y, z, state -> set(chunk, x, y, z, state) },
            rotation = rotation,
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

    private fun railPart(plan: CityPlan): Pair<String, Int>? {
        if (plan.kind == CityChunkKind.STATION) {
            return "station_underground" to 0
        }
        return when {
            plan.subwayEW && plan.subwayNS -> "rails_3split" to 0
            plan.subwayEW -> "rails_horizontal" to 0
            plan.subwayNS -> "rails_vertical" to 0
            else -> null
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

    private fun lotBlock(plan: CityPlan): BlockState = when (plan.anchor.style) {
        1 -> Blocks.PACKED_MUD.defaultState
        2 -> Blocks.SMOOTH_QUARTZ.defaultState
        else -> Blocks.SMOOTH_STONE.defaultState
    }

    private fun lostCityBuildingName(
        chunkX: Int,
        chunkZ: Int,
        localChunkX: Int,
        localChunkZ: Int,
        anchor: LostCityLayout.Anchor,
        cityFactor: Double
    ): String? {
        val lotX = Math.floorMod(localChunkX, ROAD_GRID_CHUNKS)
        val lotZ = Math.floorMod(localChunkZ, ROAD_GRID_CHUNKS)
        val canFitTwoByTwo = lotX in 1..2 && lotZ in 1..2
        if (canFitTwoByTwo && cityFactor > 0.42) {
            val blockX = Math.floorDiv(localChunkX, ROAD_GRID_CHUNKS)
            val blockZ = Math.floorDiv(localChunkZ, ROAD_GRID_CHUNKS)
            val roll = positiveHash(anchor.centerChunkX + blockX, anchor.centerChunkZ + blockZ, 211L)
            if (roll % 100 < if (cityFactor > 0.72) 42 else 24) {
                val multi = when ((roll / 100) % 5) {
                    0 -> "center"
                    1 -> "library"
                    2 -> "shopping"
                    3 -> "shopping_open"
                    else -> "townhall"
                }
                return LostCityAssetLibrary.multiBuildingPart(multi, lotX - 1, lotZ - 1)
            }
        }

        return when (positiveHash(chunkX, chunkZ, 223L) % 8) {
            0 -> "building1"
            1 -> "building2"
            2 -> "building3"
            3 -> "building4"
            4 -> "building5"
            5 -> "building6"
            6 -> "building7"
            else -> "building8"
        }
    }

    private fun buildingChance(cityFactor: Double): Int = when {
        cityFactor > 0.78 -> 92
        cityFactor > 0.52 -> 76
        cityFactor > 0.28 -> 55
        else -> 28
    }

    private fun set(chunk: Chunk, x: Int, y: Int, z: Int, state: BlockState) {
        if (y < minimumY || y >= minimumY + worldHeight) return
        val startX = chunk.pos.startX
        val startZ = chunk.pos.startZ
        if (x < startX || x > startX + 15 || z < startZ || z > startZ + 15) return
        chunk.setBlockState(BlockPos(x, y, z), state, false)
    }

    private fun positiveHash(x: Int, z: Int, salt: Long): Int {
        return layout.positiveHash(x, z, salt)
    }

    private fun mixedHash(x: Int, z: Int, salt: Long): Long {
        return layout.mixedHash(x, z, salt)
    }
}
