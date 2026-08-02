package mystcraft.flood.generation

import com.mojang.serialization.Codec
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.generation.profile.TerrainType
import net.minecraft.block.DoorBlock
import net.minecraft.block.LadderBlock
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.RailBlock
import net.minecraft.block.StairsBlock
import net.minecraft.block.enums.DoubleBlockHalf
import net.minecraft.block.enums.RailShape
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.Heightmap
import net.minecraft.world.StructureWorldAccess
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.util.FeatureContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Places a city district inside a deterministic, bounded footprint.
 *
 * Layout decisions derive from world seed and grid coordinates rather than generation order.
 * Every write is clipped to the active feature region so neighboring chunks can generate safely
 * in parallel without duplicating or tearing roads and buildings.
 */
class BoundedLostCityFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {

    companion object {
        private const val CELL_CHUNKS = 64
        private const val MIN_RADIUS_CHUNKS = 18
        private const val RADIUS_VARIANCE_CHUNKS = 12
        private const val CENTER_JITTER_CHUNKS = 18
        private const val ROAD_GRID_CHUNKS = 4
        private const val AVENUE_GRID_CHUNKS = 8
        private const val RAIL_GRID_CHUNKS = 20
        private const val EDGE_FEATHER_CHUNKS = 5
        private const val RAIL_DEPTH = 42
        private const val MAX_CLEAR_ABOVE = 112
    }

    private enum class Kind {
        OUTSIDE,
        ROAD,
        AVENUE,
        STATION,
        BUILDING,
        PARK,
        PLAZA
    }

    private data class BuildingChoice(
        val name: String,
        val multi: Boolean,
        val seedChunkX: Int,
        val seedChunkZ: Int
    )

    private data class Anchor(
        val cellX: Int,
        val cellZ: Int,
        val centerChunkX: Int,
        val centerChunkZ: Int,
        val radiusChunks: Int,
        val active: Boolean,
        val style: Int
    )

    private data class Plan(
        val chunkX: Int,
        val chunkZ: Int,
        val anchor: Anchor,
        val localChunkX: Int,
        val localChunkZ: Int,
        val cityFactor: Double,
        val kind: Kind,
        val roadNS: Boolean,
        val roadEW: Boolean,
        val subwayNS: Boolean,
        val subwayEW: Boolean,
        val building: BuildingChoice?
    ) {
        val inCity: Boolean = cityFactor > 0.0
        val isSurfaceRoad: Boolean = kind == Kind.ROAD || kind == Kind.AVENUE || kind == Kind.STATION
    }

    override fun generate(context: FeatureContext<DefaultFeatureConfig>): Boolean {
        val world = context.world
        val serverWorld = world.toServerWorld()
        if (context.generator is LostCityChunkGenerator) return false
        if (!AgeSubdimensionManager.isPrimaryAgeRealm(serverWorld.registryKey.value)) return false

        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, serverWorld.registryKey.value)
        if (profile.terrainType != TerrainType.CITIES) return false
        if (!LostCityAssetLibrary.canGenerateCities()) return false

        val origin = context.origin
        val chunkX = origin.x shr 4
        val chunkZ = origin.z shr 4
        val plan = plan(chunkX, chunkZ)
        if (!plan.inCity) return false

        val groundY = cityGroundY(world, plan)
        if (groundY <= world.bottomY + 8 || groundY >= world.topY - 32) return false

        if (plan.inCity) {
            prepareLot(world, origin, groundY, plan)
            when (plan.kind) {
                Kind.ROAD, Kind.AVENUE, Kind.STATION -> placeRoad(world, origin, groundY, plan)
                Kind.BUILDING -> placeBuilding(world, origin, groundY, plan)
                Kind.PARK -> placePark(world, origin, groundY, plan)
                Kind.PLAZA -> placePlaza(world, origin, groundY, plan)
                Kind.OUTSIDE -> {}
            }
        }

        if (plan.inCity && (plan.subwayNS || plan.subwayEW || plan.kind == Kind.STATION)) {
            placeRail(world, origin, groundY, plan)
        }

        if (plan.inCity) {
            applyDecay(world, origin, groundY, plan)
            placeCityBoundary(world, origin, groundY, plan)
        }

        return true
    }

    private fun prepareLot(world: StructureWorldAccess, origin: BlockPos, groundY: Int, plan: Plan) {
        val floor = when (plan.kind) {
            Kind.PARK -> Blocks.GRASS_BLOCK.defaultState
            Kind.BUILDING -> lotBlock(plan)
            else -> Blocks.SMOOTH_STONE.defaultState
        }
        val clearTop = (groundY + clearance(plan)).coerceAtMost(world.topY - 4)
        for (x in 0..15) {
            for (z in 0..15) {
                val wx = origin.x + x
                val wz = origin.z + z
                val terrainTop = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, wx, wz) - 1
                val oceanFloor = world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, wx, wz) - 1
                for (y in (groundY + 1)..clearTop) {
                    val pos = BlockPos(wx, y, wz)
                    val state = world.getBlockState(pos)
                    if (!state.isAir) {
                        world.setBlockState(pos, Blocks.AIR.defaultState, 2)
                    }
                }
                val foundationBottom = foundationBottom(world, terrainTop, oceanFloor)
                for (y in foundationBottom until groundY) {
                    val pos = BlockPos(wx, y, wz)
                    val state = world.getBlockState(pos)
                    if (state.isAir || state.isOf(Blocks.WATER) || state.isOf(Blocks.LAVA)) {
                        world.setBlockState(pos, foundationBlock(plan, y, groundY), 2)
                    }
                }
                world.setBlockState(BlockPos(wx, groundY, wz), floor, 2)
            }
        }
    }

    private fun placeRoad(world: StructureWorldAccess, origin: BlockPos, groundY: Int, plan: Plan) {
        val (part, rotation) = streetPartFor(plan)
        placePart(world, origin, groundY, part, plan, 97L, rotation)
        if (plan.kind == Kind.AVENUE || plan.kind == Kind.STATION) {
            for ((x, z) in listOf(2 to 2, 13 to 2, 2 to 13, 13 to 13)) {
                lamp(world, origin.x + x, groundY + 1, origin.z + z)
            }
        }
        if (plan.kind == Kind.AVENUE) {
            for (i in 4..11 step 3) {
                planter(world, origin.x + i, groundY + 1, origin.z + 7)
                planter(world, origin.x + 7, groundY + 1, origin.z + i)
            }
        }
        if (plan.kind == Kind.STATION) {
            placeStationSurface(world, origin, groundY)
        }
    }

    private fun waterTop(
        world: StructureWorldAccess,
        x: Int,
        z: Int,
        terrainTop: Int,
        oceanFloor: Int
    ): Int? {
        val minY = max(oceanFloor + 1, world.bottomY)
        for (y in terrainTop downTo minY) {
            val state = world.getBlockState(BlockPos(x, y, z))
            if (state.isOf(Blocks.WATER)) return y
            if (!state.isAir && y < terrainTop) return null
        }
        return null
    }

    private fun foundationBottom(world: StructureWorldAccess, terrainTop: Int, oceanFloor: Int): Int {
        val bottom = if (terrainTop > oceanFloor + 1) oceanFloor else terrainTop
        return bottom.coerceAtLeast(world.bottomY + 1)
    }

    private fun foundationBlock(plan: Plan, y: Int, groundY: Int): BlockState {
        if (y >= groundY - 7) {
            return when (plan.kind) {
                Kind.ROAD, Kind.AVENUE, Kind.STATION, Kind.PLAZA -> Blocks.STONE_BRICKS.defaultState
                Kind.PARK -> Blocks.DIRT.defaultState
                else -> Blocks.STONE.defaultState
            }
        }
        return Blocks.STONE.defaultState
    }

    private fun placeBuilding(world: StructureWorldAccess, origin: BlockPos, groundY: Int, plan: Plan) {
        val seedChunkX = plan.building?.seedChunkX ?: plan.chunkX
        val seedChunkZ = plan.building?.seedChunkZ ?: plan.chunkZ
        val floors = (2 + (plan.cityFactor * 7.0).toInt() + positiveHash(seedChunkX, seedChunkZ, 103L) % 3)
            .coerceIn(2, 11)
        val setter = setter(world)
        val name = plan.building?.name
        val buildingY = groundY + 1
        if (name != null) {
            LostCityAssetLibrary.placeBuilding(name, origin.x, buildingY, origin.z, floors, mixedHash(seedChunkX, seedChunkZ, 101L), plan.anchor.style, setter)
        } else {
            LostCityAssetLibrary.placeBuilding(origin.x, buildingY, origin.z, floors, mixedHash(seedChunkX, seedChunkZ, 101L), plan.anchor.style, setter)
        }
        carveBuildingEntrance(world, origin, groundY, plan)
        floodSunkenBuilding(world, origin, groundY)
    }

    private fun floodSunkenBuilding(world: StructureWorldAccess, origin: BlockPos, groundY: Int) {
        val scan = surroundingWaterScan(world, origin)
        if (scan.waterSamples < scan.totalSamples * 3 / 4) return
        val floodTop = scan.topY
        if (floodTop <= groundY + 5) return
        for (x in 1..14) {
            for (z in 1..14) {
                val wx = origin.x + x
                val wz = origin.z + z
                for (y in groundY + 1..min(floodTop, groundY + 18)) {
                    val pos = BlockPos(wx, y, wz)
                    if (world.getBlockState(pos).isAir) {
                        world.setBlockState(pos, Blocks.WATER.defaultState, 2)
                    }
                }
            }
        }
    }

    private data class WaterScan(val topY: Int, val waterSamples: Int, val totalSamples: Int)

    private fun surroundingWaterScan(world: StructureWorldAccess, origin: BlockPos): WaterScan {
        var best = world.bottomY
        var waterSamples = 0
        var totalSamples = 0
        val samples = mutableListOf<Pair<Int, Int>>()
        for (i in -1..16 step 2) {
            samples += origin.x - 1 to origin.z + i
            samples += origin.x + 16 to origin.z + i
            samples += origin.x + i to origin.z - 1
            samples += origin.x + i to origin.z + 16
        }
        for ((x, z) in samples) {
            totalSamples++
            val terrainTop = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z) - 1
            val oceanFloor = world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, x, z) - 1
            val water = waterTop(world, x, z, terrainTop, oceanFloor) ?: continue
            waterSamples++
            best = max(best, water)
        }
        return WaterScan(best, waterSamples, totalSamples.coerceAtLeast(1))
    }

    private fun placePark(world: StructureWorldAccess, origin: BlockPos, groundY: Int, plan: Plan) {
        val parts = listOf("park_trees", "park_pool", "park_plants", "park_plants_pillars", "park_fountain1", "park_fountain2")
        placePart(world, origin, groundY + 1, parts[positiveHash(plan.chunkX, plan.chunkZ, 127L) % parts.size], plan, 129L, 0)
        for ((x, z) in listOf(3 to 3, 12 to 4, 4 to 12, 11 to 11)) {
            if (positiveHash(plan.chunkX + x, plan.chunkZ + z, 128L) % 3 != 0) {
                smallTree(world, origin.x + x, groundY + 1, origin.z + z)
            }
        }
    }

    private fun placePlaza(world: StructureWorldAccess, origin: BlockPos, groundY: Int, plan: Plan) {
        val accent = if (plan.anchor.style % 2 == 0) Blocks.POLISHED_ANDESITE.defaultState else Blocks.STONE_BRICKS.defaultState
        for (x in 4..11) {
            set(world, origin.x + x, groundY, origin.z + 4, accent)
            set(world, origin.x + x, groundY, origin.z + 11, accent)
        }
        for (z in 4..11) {
            set(world, origin.x + 4, groundY, origin.z + z, accent)
            set(world, origin.x + 11, groundY, origin.z + z, accent)
        }
        if (positiveHash(plan.chunkX, plan.chunkZ, 131L) % 3 == 0) {
            val part = listOf("fountain1", "fountain2", "fountain3")[positiveHash(plan.chunkX, plan.chunkZ, 133L) % 3]
            placePart(world, origin, groundY + 1, part, plan, 135L, 0)
        }
        if (positiveHash(plan.chunkX, plan.chunkZ, 139L) % 4 == 0) {
            smallTree(world, origin.x + 7, groundY + 1, origin.z + 7)
        }
    }

    private fun placeRail(world: StructureWorldAccess, origin: BlockPos, groundY: Int, plan: Plan) {
        val railY = cityRailY(world, groundY)
        val part = when {
            plan.kind == Kind.STATION -> "station_underground"
            plan.subwayNS && plan.subwayEW -> "rails_3split"
            plan.subwayNS -> "rails_vertical"
            plan.subwayEW -> "rails_horizontal"
            else -> return
        }
        placePart(world, origin, railY, part, plan, 173L, 0, voidAsAir = true)
        carveSubwayTunnels(world, origin, railY, plan)
        if (plan.kind == Kind.STATION) {
            placePart(world, origin, railY, "rails_3split", plan, 179L, 0, voidAsAir = true)
            carveSubwayTunnels(world, origin, railY, plan.copy(subwayNS = true, subwayEW = true))
            placeStationAccess(world, origin, groundY, railY)
        }
    }

    private fun carveSubwayTunnels(world: StructureWorldAccess, origin: BlockPos, railY: Int, plan: Plan) {
        if (plan.subwayNS || plan.kind == Kind.STATION) {
            for (z in 0..15) {
                for (x in 5..10) {
                    val edge = x == 5 || x == 10
                    set(world, origin.x + x, railY, origin.z + z, Blocks.STONE_BRICKS.defaultState)
                    for (y in railY + 1..railY + 4) {
                        set(world, origin.x + x, y, origin.z + z, if (edge) Blocks.STONE_BRICKS.defaultState else Blocks.AIR.defaultState)
                    }
                }
                set(world, origin.x + 7, railY + 1, origin.z + z, Blocks.RAIL.defaultState.with(RailBlock.SHAPE, RailShape.NORTH_SOUTH))
                set(world, origin.x + 8, railY + 1, origin.z + z, Blocks.RAIL.defaultState.with(RailBlock.SHAPE, RailShape.NORTH_SOUTH))
            }
        }
        if (plan.subwayEW || plan.kind == Kind.STATION) {
            for (x in 0..15) {
                for (z in 5..10) {
                    val edge = z == 5 || z == 10
                    set(world, origin.x + x, railY, origin.z + z, Blocks.STONE_BRICKS.defaultState)
                    for (y in railY + 1..railY + 4) {
                        set(world, origin.x + x, y, origin.z + z, if (edge) Blocks.STONE_BRICKS.defaultState else Blocks.AIR.defaultState)
                    }
                }
                set(world, origin.x + x, railY + 1, origin.z + 7, Blocks.RAIL.defaultState.with(RailBlock.SHAPE, RailShape.EAST_WEST))
                set(world, origin.x + x, railY + 1, origin.z + 8, Blocks.RAIL.defaultState.with(RailBlock.SHAPE, RailShape.EAST_WEST))
            }
        }
        for ((x, z) in listOf(6 to 6, 9 to 6, 6 to 9, 9 to 9)) {
            set(world, origin.x + x, railY + 3, origin.z + z, Blocks.TORCH.defaultState)
        }
    }

    private fun carveBuildingEntrance(world: StructureWorldAccess, origin: BlockPos, groundY: Int, plan: Plan) {
        val side = entranceSide(plan)
        val doorState = Blocks.IRON_DOOR.defaultState
        when (side) {
            Direction.WEST -> carveEntrance(world, origin.x, origin.z + 7, groundY, Direction.WEST, doorState)
            Direction.EAST -> carveEntrance(world, origin.x + 15, origin.z + 7, groundY, Direction.EAST, doorState)
            Direction.NORTH -> carveEntrance(world, origin.x + 7, origin.z, groundY, Direction.NORTH, doorState)
            Direction.SOUTH -> carveEntrance(world, origin.x + 7, origin.z + 15, groundY, Direction.SOUTH, doorState)
            else -> {}
        }
    }

    private fun entranceSide(plan: Plan): Direction {
        val west = plan(plan.chunkX - 1, plan.chunkZ).isSurfaceRoad
        val east = plan(plan.chunkX + 1, plan.chunkZ).isSurfaceRoad
        val north = plan(plan.chunkX, plan.chunkZ - 1).isSurfaceRoad
        val south = plan(plan.chunkX, plan.chunkZ + 1).isSurfaceRoad
        return when {
            west -> Direction.WEST
            east -> Direction.EAST
            north -> Direction.NORTH
            south -> Direction.SOUTH
            abs(plan.localChunkX) > abs(plan.localChunkZ) && plan.localChunkX > 0 -> Direction.EAST
            abs(plan.localChunkX) > abs(plan.localChunkZ) -> Direction.WEST
            plan.localChunkZ > 0 -> Direction.SOUTH
            else -> Direction.NORTH
        }
    }

    private fun carveEntrance(
        world: StructureWorldAccess,
        centerX: Int,
        centerZ: Int,
        groundY: Int,
        facing: Direction,
        doorState: BlockState
    ) {
        val across = if (facing.axis == Direction.Axis.X) Direction.SOUTH else Direction.EAST
        for (offset in 0..1) {
            val x = centerX + across.offsetX * offset
            val z = centerZ + across.offsetZ * offset
            for (depth in 0..2) {
                val wx = x - facing.offsetX * depth
                val wz = z - facing.offsetZ * depth
                set(world, wx, groundY, wz, Blocks.SMOOTH_STONE.defaultState)
                for (y in groundY + 1..groundY + 3) {
                    set(world, wx, y, wz, Blocks.AIR.defaultState)
                }
            }
            set(world, x, groundY + 1, z, doorState.with(DoorBlock.FACING, facing).with(DoorBlock.HALF, DoubleBlockHalf.LOWER))
            set(world, x, groundY + 2, z, doorState.with(DoorBlock.FACING, facing).with(DoorBlock.HALF, DoubleBlockHalf.UPPER))
        }
        for (offset in -1..2) {
            val x = centerX + across.offsetX * offset + facing.offsetX
            val z = centerZ + across.offsetZ * offset + facing.offsetZ
            set(world, x, groundY, z, Blocks.SMOOTH_STONE.defaultState)
        }
    }

    private fun placeStationSurface(world: StructureWorldAccess, origin: BlockPos, groundY: Int) {
        for (x in 4..11) {
            for (z in 4..11) {
                val edge = x == 4 || x == 11 || z == 4 || z == 11
                val state = if (edge) Blocks.STONE_BRICKS.defaultState else Blocks.SMOOTH_STONE.defaultState
                set(world, origin.x + x, groundY + 1, origin.z + z, state)
            }
        }
        for ((x, z) in listOf(4 to 4, 11 to 4, 4 to 11, 11 to 11)) {
            set(world, origin.x + x, groundY + 2, origin.z + z, Blocks.STONE_BRICK_WALL.defaultState)
            set(world, origin.x + x, groundY + 3, origin.z + z, Blocks.SEA_LANTERN.defaultState)
        }
        for (x in 6..9) {
            for (z in 6..9) {
                set(world, origin.x + x, groundY + 1, origin.z + z, Blocks.AIR.defaultState)
                set(world, origin.x + x, groundY, origin.z + z, Blocks.AIR.defaultState)
            }
        }
    }

    private fun placeStationAccess(world: StructureWorldAccess, origin: BlockPos, groundY: Int, railY: Int) {
        val minY = railY + 1
        val maxY = groundY + 4
        for (y in minY..maxY) {
            for (x in 6..9) {
                for (z in 6..9) {
                    val edge = x == 6 || x == 9 || z == 6 || z == 9
                    val state = if (edge && y < groundY + 1) Blocks.STONE_BRICKS.defaultState else Blocks.AIR.defaultState
                    set(world, origin.x + x, y, origin.z + z, state)
                }
            }
            set(
                world,
                origin.x + 6,
                y,
                origin.z + 7,
                Blocks.LADDER.defaultState.with(LadderBlock.FACING, Direction.EAST)
            )
            set(
                world,
                origin.x + 9,
                y,
                origin.z + 8,
                Blocks.LADDER.defaultState.with(LadderBlock.FACING, Direction.WEST)
            )
        }
        for (x in 3..12) {
            for (z in 3..12) {
                val edge = x == 3 || x == 12 || z == 3 || z == 12
                if (edge) set(world, origin.x + x, railY, origin.z + z, Blocks.STONE_BRICKS.defaultState)
            }
        }
        for (x in 4..11) {
            for (z in 4..11) {
                set(world, origin.x + x, railY + 1, origin.z + z, Blocks.AIR.defaultState)
                set(world, origin.x + x, railY + 2, origin.z + z, Blocks.AIR.defaultState)
            }
        }
    }

    private fun placeCityBoundary(world: StructureWorldAccess, origin: BlockPos, groundY: Int, plan: Plan) {
        for (direction in listOf(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            val neighbor = plan(plan.chunkX + direction.offsetX, plan.chunkZ + direction.offsetZ)
            if (neighbor.inCity) continue
            placeBoundarySide(world, origin, groundY, plan, direction)
        }
    }

    private fun placeBoundarySide(
        world: StructureWorldAccess,
        origin: BlockPos,
        groundY: Int,
        plan: Plan,
        direction: Direction
    ) {
        val stairStart = 6 + positiveHash(plan.chunkX + direction.offsetX, plan.chunkZ + direction.offsetZ, 431L) % 3
        val stairRange = stairStart..(stairStart + 3)
        for (slot in 0..15) {
            if (slot in stairRange) continue
            val wx = when (direction) {
                Direction.WEST -> origin.x
                Direction.EAST -> origin.x + 15
                else -> origin.x + slot
            }
            val wz = when (direction) {
                Direction.NORTH -> origin.z
                Direction.SOUTH -> origin.z + 15
                else -> origin.z + slot
            }
            val outsideX = wx + direction.offsetX
            val outsideZ = wz + direction.offsetZ
            val terrainTop = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, outsideX, outsideZ) - 1
            val oceanFloor = world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, outsideX, outsideZ) - 1
            val bottom = foundationBottom(world, terrainTop, oceanFloor)
            val wallTop = max(groundY, min(terrainTop + 1, groundY + 5))
            for (y in bottom..wallTop) {
                set(world, wx, y, wz, Blocks.STONE_BRICKS.defaultState)
            }
            set(world, wx, groundY + 1, wz, Blocks.STONE_BRICK_WALL.defaultState)
        }
        placeBoundaryStair(world, origin, groundY, direction, stairRange)
    }

    private fun placeBoundaryStair(
        world: StructureWorldAccess,
        origin: BlockPos,
        groundY: Int,
        direction: Direction,
        slots: IntRange
    ) {
        val centerSlot = (slots.first + slots.last) / 2
        val outsideBaseX = when (direction) {
            Direction.WEST -> origin.x - 1
            Direction.EAST -> origin.x + 16
            else -> origin.x + centerSlot
        }
        val outsideBaseZ = when (direction) {
            Direction.NORTH -> origin.z - 1
            Direction.SOUTH -> origin.z + 16
            else -> origin.z + centerSlot
        }
        val outsideY = averageTerrainY(world, outsideBaseX, outsideBaseZ, direction)
        val delta = (outsideY - groundY).coerceIn(-12, 12)
        val steps = max(abs(delta), 3)
        val stairFacing = if (delta < 0) direction else direction.opposite

        for (step in 0..steps) {
            val progress = if (steps == 0) 0.0 else step.toDouble() / steps.toDouble()
            val y = (groundY + delta * progress).toInt()
            val baseX = when (direction) {
                Direction.WEST -> origin.x - step
                Direction.EAST -> origin.x + 15 + step
                else -> origin.x + slots.first
            }
            val baseZ = when (direction) {
                Direction.NORTH -> origin.z - step
                Direction.SOUTH -> origin.z + 15 + step
                else -> origin.z + slots.first
            }
            for (width in 0 until slots.count()) {
                val wx = if (direction.axis == Direction.Axis.X) baseX else baseX + width
                val wz = if (direction.axis == Direction.Axis.Z) baseZ else baseZ + width
                val terrainTop = world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, wx, wz) - 1
                for (fillY in terrainTop until y) {
                    set(world, wx, fillY, wz, Blocks.STONE.defaultState)
                }
                set(world, wx, y, wz, Blocks.STONE_BRICK_STAIRS.defaultState.with(StairsBlock.FACING, stairFacing))
                for (clearY in y + 1..y + 4) {
                    set(world, wx, clearY, wz, Blocks.AIR.defaultState)
                }
            }
        }
        for (slot in slots) {
            val wx = when (direction) {
                Direction.WEST -> origin.x
                Direction.EAST -> origin.x + 15
                else -> origin.x + slot
            }
            val wz = when (direction) {
                Direction.NORTH -> origin.z
                Direction.SOUTH -> origin.z + 15
                else -> origin.z + slot
            }
            set(world, wx, groundY + 1, wz, Blocks.AIR.defaultState)
            set(world, wx, groundY + 2, wz, Blocks.AIR.defaultState)
        }
    }

    private fun averageTerrainY(world: StructureWorldAccess, x: Int, z: Int, direction: Direction): Int {
        var total = 0
        var samples = 0
        for (offset in -1..1) {
            val sx = if (direction.axis == Direction.Axis.X) x else x + offset
            val sz = if (direction.axis == Direction.Axis.Z) z else z + offset
            total += world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, sx, sz) - 1
            samples++
        }
        return if (samples == 0) 72 else total / samples
    }

    private fun applyDecay(world: StructureWorldAccess, origin: BlockPos, groundY: Int, plan: Plan) {
        val decay = decayLevel(plan)
        if (decay <= 0) return

        scarSurface(world, origin, groundY, plan, decay)
        if (plan.kind == Kind.BUILDING) {
            scarBuilding(world, origin, groundY, plan, decay)
        }
        if (plan.kind != Kind.STATION && decay >= 2 && positiveHash(plan.chunkX, plan.chunkZ, 313L) % 100 < craterChance(plan, decay)) {
            placeCrater(world, origin, groundY, plan, decay)
        }
    }

    private fun scarSurface(world: StructureWorldAccess, origin: BlockPos, groundY: Int, plan: Plan, decay: Int) {
        val chance = when (plan.kind) {
            Kind.PARK -> 8 + decay * 3
            Kind.BUILDING -> 4 + decay * 2
            else -> 10 + decay * 4
        }
        for (x in 0..15) {
            for (z in 0..15) {
                if (positiveHash(origin.x + x, origin.z + z, 331L) % 100 >= chance) continue
                val pos = BlockPos(origin.x + x, groundY, origin.z + z)
                val current = world.getBlockState(pos)
                if (current.isAir) continue
                val roll = positiveHash(origin.x + x, origin.z + z, 337L) % 7
                val replacement = when (roll) {
                    0 -> Blocks.CRACKED_STONE_BRICKS.defaultState
                    1 -> Blocks.MOSSY_STONE_BRICKS.defaultState
                    2 -> Blocks.COBBLESTONE.defaultState
                    3 -> Blocks.GRAVEL.defaultState
                    4 -> Blocks.MOSS_BLOCK.defaultState
                    else -> if (plan.kind == Kind.PARK) Blocks.GRASS_BLOCK.defaultState else current
                }
                world.setBlockState(pos, replacement, 2)
                if (roll == 4 && positiveHash(origin.x + x, origin.z + z, 339L) % 4 == 0) {
                    set(world, origin.x + x, groundY + 1, origin.z + z, Blocks.AZALEA.defaultState)
                }
            }
        }
    }

    private fun scarBuilding(world: StructureWorldAccess, origin: BlockPos, groundY: Int, plan: Plan, decay: Int) {
        val sides = 1 + positiveHash(plan.chunkX, plan.chunkZ, 347L) % decay.coerceAtMost(3)
        val height = (10 + decay * 10 + positiveHash(plan.chunkX, plan.chunkZ, 349L) % 18).coerceAtMost(MAX_CLEAR_ABOVE)
        for (sideIndex in 0 until sides) {
            val side = when ((positiveHash(plan.chunkX, plan.chunkZ, 351L) + sideIndex) % 4) {
                0 -> Direction.NORTH
                1 -> Direction.EAST
                2 -> Direction.SOUTH
                else -> Direction.WEST
            }
            val band = 3 + positiveHash(plan.chunkX + sideIndex, plan.chunkZ, 353L) % 7
            for (y in groundY + 3..groundY + height) {
                val wobble = positiveHash(plan.chunkX + y, plan.chunkZ + sideIndex, 359L) % 3 - 1
                for (w in -1..1) {
                    val slot = (band + wobble + w).coerceIn(2, 13)
                    val depth = if (decay >= 3) 3 else 2
                    for (d in 0 until depth) {
                        val (x, z) = when (side) {
                            Direction.NORTH -> origin.x + slot to origin.z + d
                            Direction.SOUTH -> origin.x + slot to origin.z + 15 - d
                            Direction.WEST -> origin.x + d to origin.z + slot
                            else -> origin.x + 15 - d to origin.z + slot
                        }
                        if (positiveHash(x, z + y, 361L) % 100 < 68) {
                            set(world, x, y, z, Blocks.AIR.defaultState)
                        }
                    }
                }
            }
        }

        val rubbleCount = 8 + decay * 8
        for (i in 0 until rubbleCount) {
            val x = positiveHash(plan.chunkX + i, plan.chunkZ, 367L) % 16
            val z = positiveHash(plan.chunkX, plan.chunkZ + i, 369L) % 16
            val state = when (i % 5) {
                0 -> Blocks.COBBLESTONE.defaultState
                1 -> Blocks.CRACKED_STONE_BRICKS.defaultState
                2 -> Blocks.GRAVEL.defaultState
                3 -> Blocks.MOSSY_COBBLESTONE.defaultState
                else -> Blocks.STONE_BRICKS.defaultState
            }
            set(world, origin.x + x, groundY + 1, origin.z + z, state)
        }
    }

    private fun placeCrater(world: StructureWorldAccess, origin: BlockPos, groundY: Int, plan: Plan, decay: Int) {
        val centerX = 4 + positiveHash(plan.chunkX, plan.chunkZ, 379L) % 8
        val centerZ = 4 + positiveHash(plan.chunkX, plan.chunkZ, 383L) % 8
        val radius = 3 + decay + positiveHash(plan.chunkX, plan.chunkZ, 389L) % 3
        val depth = 2 + decay + positiveHash(plan.chunkX, plan.chunkZ, 397L) % 3
        val radiusSq = radius * radius
        for (x in (centerX - radius)..(centerX + radius)) {
            for (z in (centerZ - radius)..(centerZ + radius)) {
                if (x !in 0..15 || z !in 0..15) continue
                val dx = x - centerX
                val dz = z - centerZ
                val distSq = dx * dx + dz * dz
                if (distSq > radiusSq) continue
                val centerDrop = ((radiusSq - distSq) * depth / radiusSq).coerceAtLeast(1)
                val floorY = groundY - centerDrop
                for (y in floorY + 1..groundY + 5) {
                    set(world, origin.x + x, y, origin.z + z, Blocks.AIR.defaultState)
                }
                val floorState = when (positiveHash(origin.x + x, origin.z + z, 401L) % 5) {
                    0 -> Blocks.GRAVEL.defaultState
                    1 -> Blocks.COBBLESTONE.defaultState
                    2 -> Blocks.MOSSY_COBBLESTONE.defaultState
                    else -> Blocks.STONE.defaultState
                }
                set(world, origin.x + x, floorY, origin.z + z, floorState)
            }
        }
    }

    private fun decayLevel(plan: Plan): Int {
        val roll = positiveHash(plan.chunkX, plan.chunkZ, 307L) % 100
        return when {
            plan.kind == Kind.STATION -> if (roll < 18) 1 else 0
            plan.cityFactor < 0.24 -> if (roll < 36) 2 else if (roll < 62) 1 else 0
            roll < 8 -> 3
            roll < 24 -> 2
            roll < 52 -> 1
            else -> 0
        }
    }

    private fun craterChance(plan: Plan, decay: Int): Int = when (plan.kind) {
        Kind.BUILDING -> 4 + decay * 4
        Kind.PARK, Kind.PLAZA -> 8 + decay * 6
        Kind.ROAD, Kind.AVENUE -> 3 + decay * 3
        else -> 0
    }

    private fun planter(world: StructureWorldAccess, x: Int, y: Int, z: Int) {
        set(world, x, y, z, Blocks.GRASS_BLOCK.defaultState)
        set(world, x, y + 1, z, Blocks.AZALEA.defaultState)
    }

    private fun smallTree(world: StructureWorldAccess, x: Int, y: Int, z: Int) {
        set(world, x, y, z, Blocks.OAK_LOG.defaultState)
        set(world, x, y + 1, z, Blocks.OAK_LOG.defaultState)
        for (dx in -1..1) {
            for (dz in -1..1) {
                set(world, x + dx, y + 2, z + dz, Blocks.OAK_LEAVES.defaultState)
            }
        }
        set(world, x, y + 3, z, Blocks.OAK_LEAVES.defaultState)
    }

    private fun placePart(
        world: StructureWorldAccess,
        origin: BlockPos,
        y: Int,
        part: String,
        plan: Plan,
        salt: Long,
        rotation: Int,
        voidAsAir: Boolean = false
    ) {
        LostCityAssetLibrary.placePart(part, origin.x, y, origin.z, mixedHash(plan.chunkX, plan.chunkZ, salt), plan.anchor.style, setter(world), rotation, voidAsAir)
    }

    private fun setter(world: StructureWorldAccess): (Int, Int, Int, BlockState) -> Unit = { x, y, z, state ->
        set(world, x, y, z, state)
    }

    private fun set(world: StructureWorldAccess, x: Int, y: Int, z: Int, state: BlockState) {
        if (y < world.bottomY || y >= world.topY) return
        world.setBlockState(BlockPos(x, y, z), state, 2)
    }

    private fun cityGroundY(world: StructureWorldAccess, plan: Plan): Int {
        val baseChunkX = plan.anchor.centerChunkX + Math.floorDiv(plan.localChunkX, ROAD_GRID_CHUNKS) * ROAD_GRID_CHUNKS
        val baseChunkZ = plan.anchor.centerChunkZ + Math.floorDiv(plan.localChunkZ, ROAD_GRID_CHUNKS) * ROAD_GRID_CHUNKS
        val baseX = baseChunkX shl 4
        val baseZ = baseChunkZ shl 4
        var total = 0
        var samples = 0
        for (x in listOf(8, 24, 40, 56)) {
            for (z in listOf(8, 24, 40, 56)) {
                val top = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, baseX + x, baseZ + z)
                total += top
                samples++
            }
        }
        val average = if (samples == 0) 72 else total / samples
        return (average / 2 * 2).coerceIn(world.bottomY + 16, world.topY - 48)
    }

    private fun cityRailY(world: StructureWorldAccess, groundY: Int): Int {
        return (groundY - RAIL_DEPTH).coerceIn(world.bottomY + 10, world.topY - 64)
    }

    private fun streetPartFor(plan: Plan): Pair<String, Int> {
        if (plan.kind == Kind.AVENUE || plan.kind == Kind.STATION) return "street_all" to 0
        val north = plan(plan.chunkX, plan.chunkZ - 1).isSurfaceRoad
        val south = plan(plan.chunkX, plan.chunkZ + 1).isSurfaceRoad
        val west = plan(plan.chunkX - 1, plan.chunkZ).isSurfaceRoad
        val east = plan(plan.chunkX + 1, plan.chunkZ).isSurfaceRoad
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

    private fun plan(chunkX: Int, chunkZ: Int): Plan {
        val anchor = nearestAnchor(chunkX, chunkZ)
        val localX = chunkX - anchor.centerChunkX
        val localZ = chunkZ - anchor.centerChunkZ
        val distance = hypot(localX.toDouble(), localZ.toDouble())
        val cityFactor = ((anchor.radiusChunks - distance) / EDGE_FEATHER_CHUNKS.toDouble()).coerceIn(0.0, 1.0)
        val inCity = cityFactor > 0.0
        val railX = Math.floorMod(localX + 1, RAIL_GRID_CHUNKS)
        val railZ = Math.floorMod(localZ + 1, RAIL_GRID_CHUNKS)
        val subwayNS = distance <= anchor.radiusChunks + 8 && (railX == 0 || railX == RAIL_GRID_CHUNKS / 2)
        val subwayEW = distance <= anchor.radiusChunks + 8 && (railZ == 0 || railZ == RAIL_GRID_CHUNKS / 2)
        val station = inCity && cityFactor > 0.18 && subwayNS && subwayEW
        val roadSlotX = Math.floorMod(localX, ROAD_GRID_CHUNKS)
        val roadSlotZ = Math.floorMod(localZ, ROAD_GRID_CHUNKS)
        val roadNS = inCity && (roadSlotX == 0 || roadSlotX == ROAD_GRID_CHUNKS - 1 || station)
        val roadEW = inCity && (roadSlotZ == 0 || roadSlotZ == ROAD_GRID_CHUNKS - 1 || station)
        val avenue = roadNS && Math.floorMod(localX, AVENUE_GRID_CHUNKS) == 0 ||
            roadEW && Math.floorMod(localZ, AVENUE_GRID_CHUNKS) == 0
        val building = lostCityBuilding(chunkX, chunkZ, localX, localZ, anchor, cityFactor)
        val kind = when {
            station -> Kind.STATION
            !inCity -> Kind.OUTSIDE
            roadNS || roadEW -> if (avenue) Kind.AVENUE else Kind.ROAD
            building?.multi == true -> Kind.BUILDING
            cityFactor < 0.36 && positiveHash(chunkX, chunkZ, 11L) % 3 == 0 -> Kind.PARK
            positiveHash(chunkX, chunkZ, 17L) % 100 < buildingChance(cityFactor) -> Kind.BUILDING
            positiveHash(chunkX, chunkZ, 23L) % 5 == 0 -> Kind.PARK
            else -> Kind.PLAZA
        }
        return Plan(chunkX, chunkZ, anchor, localX, localZ, cityFactor, kind, roadNS, roadEW, subwayNS, subwayEW, building)
    }

    private fun nearestAnchor(chunkX: Int, chunkZ: Int): Anchor {
        val cellX = Math.floorDiv(chunkX, CELL_CHUNKS)
        val cellZ = Math.floorDiv(chunkZ, CELL_CHUNKS)
        var best = anchor(cellX, cellZ)
        var bestDist = Double.MAX_VALUE
        for (x in (cellX - 2)..(cellX + 2)) {
            for (z in (cellZ - 2)..(cellZ + 2)) {
                val candidate = anchor(x, z)
                if (!candidate.active) continue
                val dist = hypot((chunkX - candidate.centerChunkX).toDouble(), (chunkZ - candidate.centerChunkZ).toDouble())
                if (dist < bestDist) {
                    best = candidate
                    bestDist = dist
                }
            }
        }
        return best
    }

    private fun anchor(cellX: Int, cellZ: Int): Anchor {
        return Anchor(
            cellX = cellX,
            cellZ = cellZ,
            centerChunkX = cellX * CELL_CHUNKS + CELL_CHUNKS / 2 + rangedHash(cellX, cellZ, 31L, -CENTER_JITTER_CHUNKS, CENTER_JITTER_CHUNKS),
            centerChunkZ = cellZ * CELL_CHUNKS + CELL_CHUNKS / 2 + rangedHash(cellX, cellZ, 37L, -CENTER_JITTER_CHUNKS, CENTER_JITTER_CHUNKS),
            radiusChunks = MIN_RADIUS_CHUNKS + rangedHash(cellX, cellZ, 41L, 0, RADIUS_VARIANCE_CHUNKS),
            active = positiveHash(cellX, cellZ, 53L) % 100 < 82,
            style = rangedHash(cellX, cellZ, 43L, 0, 7)
        )
    }

    private fun lostCityBuilding(chunkX: Int, chunkZ: Int, localX: Int, localZ: Int, anchor: Anchor, cityFactor: Double): BuildingChoice? {
        val lotX = Math.floorMod(localX, ROAD_GRID_CHUNKS)
        val lotZ = Math.floorMod(localZ, ROAD_GRID_CHUNKS)
        if (lotX in 1..2 && lotZ in 1..2 && cityFactor > 0.42) {
            val blockX = Math.floorDiv(localX, ROAD_GRID_CHUNKS)
            val blockZ = Math.floorDiv(localZ, ROAD_GRID_CHUNKS)
            val roll = positiveHash(anchor.centerChunkX + blockX, anchor.centerChunkZ + blockZ, 211L)
            if (roll % 100 < if (cityFactor > 0.72) 42 else 24) {
                val multi = when ((roll / 100) % 5) {
                    0 -> "center"
                    1 -> "library"
                    2 -> "shopping"
                    3 -> "shopping_open"
                    else -> "townhall"
                }
                val part = LostCityAssetLibrary.multiBuildingPart(multi, lotX - 1, lotZ - 1)
                if (part != null) {
                    return BuildingChoice(part, true, anchor.centerChunkX + blockX, anchor.centerChunkZ + blockZ)
                }
            }
        }
        return BuildingChoice("building${1 + positiveHash(chunkX, chunkZ, 223L) % 8}", false, chunkX, chunkZ)
    }

    private fun lotBlock(plan: Plan): BlockState = when (plan.anchor.style) {
        1 -> Blocks.PACKED_MUD.defaultState
        2 -> Blocks.SMOOTH_QUARTZ.defaultState
        else -> Blocks.SMOOTH_STONE.defaultState
    }

    private fun clearance(plan: Plan): Int = when (plan.kind) {
        Kind.BUILDING -> MAX_CLEAR_ABOVE
        Kind.STATION -> 32
        Kind.ROAD, Kind.AVENUE -> 18
        Kind.PARK, Kind.PLAZA -> 26
        else -> 12
    }

    private fun buildingChance(cityFactor: Double): Int = when {
        cityFactor > 0.78 -> 92
        cityFactor > 0.52 -> 76
        cityFactor > 0.28 -> 55
        else -> 28
    }

    private fun lamp(world: StructureWorldAccess, x: Int, y: Int, z: Int) {
        set(world, x, y, z, Blocks.STONE_BRICK_WALL.defaultState)
        set(world, x, y + 1, z, Blocks.STONE_BRICK_WALL.defaultState)
        set(world, x, y + 2, z, Blocks.SEA_LANTERN.defaultState)
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
