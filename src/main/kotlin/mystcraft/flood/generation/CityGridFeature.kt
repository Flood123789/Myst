package mystcraft.flood.generation

import com.mojang.serialization.Codec
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.generation.profile.TerrainType
import mystcraft.flood.item.ModItems
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.PoweredRailBlock
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.block.enums.BedPart
import net.minecraft.block.enums.DoubleBlockHalf
import net.minecraft.block.enums.RailShape
import net.minecraft.item.ItemStack
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.Heightmap
import net.minecraft.world.StructureWorldAccess
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.util.FeatureContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Legacy city-grid feature retained for data compatibility and focused city placement.
 * New full city terrain is coordinated by [LostCityChunkGenerator] and [LostCityLayout]; do not
 * make their grid constants diverge or roads will fail to meet across generation paths.
 */
class CityGridFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {

    companion object {
        // Flip this off once we know which generation path is misbehaving.
        private const val DEBUG_CITY_GEN = true
        private const val SLOW_CHUNK_WARN_MS = 50L
        private const val CITY_CELL_SIZE = 4096
        private const val CITY_CENTER_JITTER = 640
        private const val CITY_MIN_RADIUS = 240
        private const val CITY_RADIUS_VARIANCE = 140
        private const val CITY_GROUND_MIN_Y = 68
        private const val CITY_GROUND_MAX_Y = 92
        private const val CITY_GROUND_Y = 76
        private const val CITY_GROUND_STEP = 6
        private const val HIGHWAY_CLEARANCE_FROM_CITY = 192
        private const val SUBWAY_RAIL_Y = 28
        private const val STATION_FLOOR_OFFSET = -35
        private const val STATION_RAIL_OFFSET = -34
        private const val RAIL_GRID_PERIOD_CHUNKS = 20
        private const val RAIL_GRID_HALF_PERIOD_CHUNKS = 10
        private const val CITY_ROAD_GRID_CHUNKS = 4
        private const val CITY_AVENUE_GRID_CHUNKS = 8
        private const val BUILDING_WATER_REJECT_RATIO = 0.28
        private const val ROAD_WATER_REJECT_RATIO = 0.62
        private const val TERRAIN_CUT_REJECT_DELTA = 34
        private val ENABLE_LEGACY_CITY_FEATURE = java.lang.Boolean.getBoolean("mystcraft.legacyCityFeature")
    }

    private enum class CityTone {
        CIVIC,
        INDUSTRIAL,
        BRICKWORK,
        GARDEN,
        MARINA
    }

    private data class CityAnchor(
        val gridX: Int,
        val gridZ: Int,
        val centerX: Int,
        val centerZ: Int,
        val radius: Int,
        val groundY: Int,
        val tone: CityTone,
        val avenuePitchX: Double,
        val avenuePitchZ: Double,
        val avenueWaveX: Double,
        val avenueWaveZ: Double,
        val streetPhaseX: Int,
        val streetPhaseZ: Int
    )

    private data class CorridorInfo(
        val influence: Double,
        val axisDistance: Int,
        val alongDistance: Int,
        val eastWest: Boolean,
        val deckY: Int
    )

    private data class RailGridInfo(
        val mx: Int,
        val mz: Int,
        val subwayEW: Boolean,
        val subwayNS: Boolean,
        val station: Boolean,
        val stationApproach: Boolean
    )

    private data class TerrainFit(
        val waterRatio: Double,
        val avgSurfaceY: Int,
        val maxSurfaceY: Int,
        val waterHeavy: Boolean,
        val steepCut: Boolean
    )

    private data class DistrictInfo(
        val gridX: Int,
        val gridZ: Int,
        val regionX: Int,
        val regionZ: Int,
        val centerX: Int,
        val centerZ: Int,
        val distX: Int,
        val distZ: Int,
        val radius: Int,
        val highwayDistX: Int,
        val highwayDistZ: Int,
        val minHighwayDist: Int,
        val highwayDeckY: Int,
        val cityInfluence: Double,
        val connectorInfluence: Double,
        val style: CityTone,
        val avenueAxisDistance: Int,
        val isCore: Boolean,
        val isSuburb: Boolean,
        val isCity: Boolean,
        val isHighway: Boolean,
        val isMainAvenue: Boolean,
        val isPark: Boolean,
        val isSubwayX: Boolean,
        val isSubwayZ: Boolean
    )

    private data class SkyscraperPalette(
        val foundation: BlockState,
        val floor: BlockState,
        val pillar: BlockState,
        val wall: BlockState,
        val glass: BlockState
    )

    private data class HousePalette(
        val plank: BlockState,
        val solid: BlockState,
        val wood: BlockState
    )

    private data class HouseStyle(
        val family: String,
        val width: Int,
        val depth: Int,
        val wallHeight: Int,
        val stories: Int = 1,
        val splitLevel: Boolean = false,
        val flatRoof: Boolean = false
    )

    private data class StreetPalette(
        val avenue: BlockState,
        val avenueStripe: BlockState,
        val support: BlockState,
        val lamp: BlockState,
        val corePaving: BlockState,
        val suburbStreet: BlockState,
        val suburbLot: BlockState,
        val parkGround: BlockState,
        val tunnelWall: BlockState
    )

    private enum class ChunkKind {
        OUTSIDE,
        HIGHWAY,
        AVENUE,
        STREET,
        STATION,
        PARK,
        PLAZA,
        TOWER,
        MIDRISE,
        SUBURB
    }

    private data class CityChunkPlan(
        val chunkX: Int,
        val chunkZ: Int,
        val anchor: CityAnchor,
        val style: CityTone,
        val cityFactor: Double,
        val corridorFactor: Double,
        val localChunkX: Int,
        val localChunkZ: Int,
        val isCity: Boolean,
        val isCore: Boolean,
        val isSuburb: Boolean,
        val stationZone: Boolean,
        val avenueNS: Boolean,
        val avenueEW: Boolean,
        val streetNS: Boolean,
        val streetEW: Boolean,
        val highwayNS: Boolean,
        val highwayEW: Boolean,
        val subwayNS: Boolean,
        val subwayEW: Boolean,
        val kind: ChunkKind,
        val groundY: Int = CITY_GROUND_Y
    ) {
        val isActive: Boolean
            get() = kind != ChunkKind.OUTSIDE || subwayNS || subwayEW

        val isRoadChunk: Boolean
            get() = kind == ChunkKind.HIGHWAY || kind == ChunkKind.AVENUE || kind == ChunkKind.STREET || kind == ChunkKind.STATION
    }

    override fun generate(context: FeatureContext<DefaultFeatureConfig>): Boolean {
        val world = context.world
        val origin = context.origin
        val serverWorld = world.toServerWorld()
        val startedAt = System.nanoTime()

        if (!AgeSubdimensionManager.isPrimaryAgeRealm(serverWorld.registryKey.value)) return false
        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, serverWorld.registryKey.value)
        if (profile.terrainType != TerrainType.CITIES) return false
        if (!ENABLE_LEGACY_CITY_FEATURE) return false

        val chunkX = origin.x shr 4
        val chunkZ = origin.z shr 4
        val debugNotes = mutableListOf<String>()
        val chunkPlan = chunkPlan(chunkX, chunkZ)
        if (!chunkPlan.isActive) return false

        val cityBaseY = chunkPlan.groundY
        val stationRailY = SUBWAY_RAIL_Y
        val stationFloorY = stationRailY - 1
        val skyscraperPalettes = listOf(
            SkyscraperPalette(
                Blocks.SMOOTH_STONE.defaultState,
                Blocks.STONE_BRICKS.defaultState,
                Blocks.CRACKED_STONE_BRICKS.defaultState,
                Blocks.STONE_BRICKS.defaultState,
                Blocks.GRAY_STAINED_GLASS.defaultState
            ),
            SkyscraperPalette(
                Blocks.POLISHED_DIORITE.defaultState,
                Blocks.POLISHED_DIORITE.defaultState,
                Blocks.LIGHT_GRAY_CONCRETE.defaultState,
                Blocks.GRAY_CONCRETE.defaultState,
                Blocks.LIGHT_GRAY_STAINED_GLASS.defaultState
            ),
            SkyscraperPalette(
                Blocks.QUARTZ_BLOCK.defaultState,
                Blocks.SMOOTH_QUARTZ.defaultState,
                Blocks.WHITE_CONCRETE.defaultState,
                Blocks.WHITE_TERRACOTTA.defaultState,
                Blocks.WHITE_STAINED_GLASS.defaultState
            ),
            SkyscraperPalette(
                Blocks.BRICKS.defaultState,
                Blocks.BRICKS.defaultState,
                Blocks.MUD_BRICKS.defaultState,
                Blocks.TERRACOTTA.defaultState,
                Blocks.BROWN_STAINED_GLASS.defaultState
            ),
            SkyscraperPalette(
                Blocks.SMOOTH_STONE.defaultState,
                Blocks.CYAN_TERRACOTTA.defaultState,
                Blocks.DEEPSLATE_BRICKS.defaultState,
                Blocks.LIGHT_BLUE_CONCRETE.defaultState,
                Blocks.LIGHT_BLUE_STAINED_GLASS.defaultState
            )
        )
        val housePalettes = listOf(
            HousePalette(Blocks.OAK_PLANKS.defaultState, Blocks.COBBLESTONE.defaultState, Blocks.OAK_WOOD.defaultState),
            HousePalette(Blocks.DARK_OAK_PLANKS.defaultState, Blocks.DEEPSLATE_BRICKS.defaultState, Blocks.DARK_OAK_WOOD.defaultState),
            HousePalette(Blocks.SPRUCE_PLANKS.defaultState, Blocks.MUD_BRICKS.defaultState, Blocks.SPRUCE_WOOD.defaultState),
            HousePalette(Blocks.BIRCH_PLANKS.defaultState, Blocks.SMOOTH_SANDSTONE.defaultState, Blocks.BIRCH_WOOD.defaultState),
            HousePalette(Blocks.CRIMSON_PLANKS.defaultState, Blocks.CYAN_TERRACOTTA.defaultState, Blocks.STRIPPED_CRIMSON_STEM.defaultState)
        )
        val westPlan = chunkPlan(chunkX - 1, chunkZ)
        val eastPlan = chunkPlan(chunkX + 1, chunkZ)
        val northPlan = chunkPlan(chunkX, chunkZ - 1)
        val southPlan = chunkPlan(chunkX, chunkZ + 1)
        val streetPalette = streetPaletteFor(chunkPlan.style)

        if (chunkPlan.kind != ChunkKind.OUTSIDE) {
            shapeChunkTerrain(world, origin, chunkPlan, westPlan, eastPlan, northPlan, southPlan, streetPalette)
        }

        if (chunkPlan.subwayEW || chunkPlan.subwayNS) {
            generateSubwayChunk(world, origin, chunkPlan, streetPalette, stationRailY)
            debugNotes.add("subway")
        }

        when (chunkPlan.kind) {
            ChunkKind.HIGHWAY -> {
                generateGrandHighwayChunk(world, origin, chunkPlan, streetPalette)
                debugNotes.add("highway(y=${chunkPlan.groundY})")
            }
            ChunkKind.AVENUE, ChunkKind.STREET, ChunkKind.STATION -> {
                generateRoadSurfaceChunk(world, origin, chunkPlan, streetPalette)
                if (chunkPlan.kind == ChunkKind.STATION) {
                    generateSubwayStationChunk(world, origin, chunkPlan, streetPalette, stationFloorY, stationRailY)
                    debugNotes.add("station")
                }
                debugNotes.add("roads(${chunkPlan.kind})")
            }
            ChunkKind.PARK -> {
                generateParkLot(world, origin, cityBaseY, chunkPlan, streetPalette)
                debugNotes.add("park")
            }
            ChunkKind.PLAZA -> {
                generatePlazaChunk(world, origin, cityBaseY, chunkPlan, streetPalette)
                debugNotes.add("plaza")
            }
            ChunkKind.TOWER -> {
                generateTowerChunk(context, cityBaseY, chunkPlan, skyscraperPalettes, tall = true)?.let(debugNotes::add)
            }
            ChunkKind.MIDRISE -> {
                generateTowerChunk(context, cityBaseY, chunkPlan, skyscraperPalettes, tall = false)?.let(debugNotes::add)
            }
            ChunkKind.SUBURB -> {
                generateSuburbLot(context.world, origin, cityBaseY, chunkPlan, housePalettes)?.let(debugNotes::add)
            }
            ChunkKind.OUTSIDE -> {
                if (!chunkPlan.subwayEW && !chunkPlan.subwayNS) return false
            }
        }

        if (DEBUG_CITY_GEN) {
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            if (debugNotes.isNotEmpty() || elapsedMs >= SLOW_CHUNK_WARN_MS) {
                val level = if (elapsedMs >= SLOW_CHUNK_WARN_MS) "warn" else "info"
                val message = buildString {
                    append("[CityGrid] chunk=")
                    append(chunkX)
                    append(",")
                    append(chunkZ)
                    append(" anchor=")
                    append(chunkPlan.anchor.gridX)
                    append(",")
                    append(chunkPlan.anchor.gridZ)
                    append(" kind=")
                    append(chunkPlan.kind)
                    append(" cityFactor=")
                    append("%.2f".format(chunkPlan.cityFactor))
                    append(" corridor=")
                    append("%.2f".format(chunkPlan.corridorFactor))
                    append(" tone=")
                    append(chunkPlan.style)
                    append(" roads=")
                    append(chunkPlan.avenueNS || chunkPlan.avenueEW || chunkPlan.streetNS || chunkPlan.streetEW || chunkPlan.highwayNS || chunkPlan.highwayEW)
                    append(" subway=")
                    append(chunkPlan.subwayNS || chunkPlan.subwayEW)
                    append(" tookMs=")
                    append(elapsedMs)
                    if (debugNotes.isNotEmpty()) {
                        append(" notes=")
                        append(debugNotes.joinToString(";"))
                    }
                }
                if (level == "warn") {
                    MystcraftReforged.LOGGER.warn(message)
                } else {
                    MystcraftReforged.LOGGER.info(message)
                }
            }
        }
        return true
    }

    private fun chunkPlan(chunkX: Int, chunkZ: Int): CityChunkPlan {
        val sampleX = (chunkX shl 4) + 8
        val sampleZ = (chunkZ shl 4) + 8
        val info = districtInfo(sampleX, sampleZ)
        val anchor = cityAnchor(info.gridX, info.gridZ)
        val localChunkX = Math.floorDiv(sampleX - anchor.centerX, 16)
        val localChunkZ = Math.floorDiv(sampleZ - anchor.centerZ, 16)

        val rail = railGridInfo(localChunkX, localChunkZ, info.cityInfluence)
        val roadColumn = info.cityInfluence > 0.30 && Math.floorMod(localChunkX, CITY_ROAD_GRID_CHUNKS) == 0
        val roadRow = info.cityInfluence > 0.30 && Math.floorMod(localChunkZ, CITY_ROAD_GRID_CHUNKS) == 0
        val avenueNS = info.cityInfluence > 0.34 && (
            rail.subwayNS || (roadColumn && Math.floorMod(localChunkX, CITY_AVENUE_GRID_CHUNKS) == 0)
        )
        val avenueEW = info.cityInfluence > 0.34 && (
            rail.subwayEW || (roadRow && Math.floorMod(localChunkZ, CITY_AVENUE_GRID_CHUNKS) == 0)
        )
        val streetNS = roadColumn && !avenueNS
        val streetEW = roadRow && !avenueEW

        val corridorEastWest = info.highwayDistZ <= info.highwayDistX
        val corridorProtected = info.connectorInfluence > 0.10
        val highwayEW = info.connectorInfluence > 0.56 && corridorEastWest && info.cityInfluence < 0.12
        val highwayNS = info.connectorInfluence > 0.56 && !corridorEastWest && info.cityInfluence < 0.12

        val stationZone = rail.station
        val stationApproach = rail.stationApproach
        val corridorRailEW = corridorEastWest && info.connectorInfluence > 0.56 && info.highwayDistZ <= 2
        val corridorRailNS = !corridorEastWest && info.connectorInfluence > 0.56 && info.highwayDistX <= 2
        val subwayEW = rail.subwayEW || corridorRailEW
        val subwayNS = rail.subwayNS || corridorRailNS
        val parkDivisor = if (info.style == CityTone.GARDEN) 8 else 12
        val park = !stationZone && !stationApproach && info.isSuburb && !avenueNS && !avenueEW && !streetNS && !streetEW &&
            rangedHash(chunkX, chunkZ, 401L, 0, parkDivisor - 1) == 0
        val buildingChance = when {
            info.cityInfluence > 0.82 -> 0.94
            info.cityInfluence > 0.66 -> 0.82
            info.cityInfluence > 0.50 -> 0.62
            else -> 0.32
        }
        val buildRoll = rangedHash(chunkX, chunkZ, 557L, 0, 999) / 1000.0
        val canHostBuilding = info.isCity &&
            !stationZone &&
            !stationApproach &&
            !highwayNS &&
            !highwayEW &&
            !avenueNS &&
            !avenueEW &&
            !streetNS &&
            !streetEW &&
            !corridorRailEW &&
            !corridorRailNS &&
            !park
        val chooseTower = info.cityInfluence > 0.72 || rangedHash(chunkX, chunkZ, 571L, 0, 4) == 0

        val kind = when {
            stationZone -> ChunkKind.STATION
            corridorProtected && !highwayEW && !highwayNS && !corridorRailEW && !corridorRailNS -> ChunkKind.OUTSIDE
            !info.isCity && info.connectorInfluence < 0.62 -> ChunkKind.OUTSIDE
            highwayNS || highwayEW -> ChunkKind.HIGHWAY
            avenueNS || avenueEW -> ChunkKind.AVENUE
            streetNS || streetEW -> ChunkKind.STREET
            park -> ChunkKind.PARK
            canHostBuilding && buildRoll < buildingChance -> if (chooseTower) ChunkKind.TOWER else ChunkKind.MIDRISE
            info.isSuburb && !corridorProtected -> ChunkKind.SUBURB
            info.isCity -> ChunkKind.PLAZA
            else -> ChunkKind.OUTSIDE
        }

        return CityChunkPlan(
            chunkX = chunkX,
            chunkZ = chunkZ,
            anchor = anchor,
            style = info.style,
            cityFactor = info.cityInfluence,
            corridorFactor = info.connectorInfluence,
            localChunkX = localChunkX,
            localChunkZ = localChunkZ,
            isCity = info.isCity,
            isCore = info.isCore,
            isSuburb = info.isSuburb,
            stationZone = stationZone,
            avenueNS = avenueNS,
            avenueEW = avenueEW,
            streetNS = streetNS,
            streetEW = streetEW,
            highwayNS = highwayNS,
            highwayEW = highwayEW,
            subwayNS = subwayNS,
            subwayEW = subwayEW,
            kind = kind,
            groundY = when (kind) {
                ChunkKind.HIGHWAY -> info.highwayDeckY
                else -> anchor.groundY
            }
        )
    }

    private fun railGridInfo(localChunkX: Int, localChunkZ: Int, cityInfluence: Double): RailGridInfo {
        if (cityInfluence <= 0.12) {
            return RailGridInfo(0, 0, subwayEW = false, subwayNS = false, station = false, stationApproach = false)
        }

        val mx = Math.floorMod(localChunkX + 1, RAIL_GRID_PERIOD_CHUNKS)
        val mz = Math.floorMod(localChunkZ + 1, RAIL_GRID_PERIOD_CHUNKS)
        val onRailColumn = mx == 0 || mx == RAIL_GRID_HALF_PERIOD_CHUNKS
        val onRailRow = mz == 0 || mz == RAIL_GRID_HALF_PERIOD_CHUNKS
        val nearRailColumn = modDistance(mx, RAIL_GRID_HALF_PERIOD_CHUNKS) <= 1
        val nearRailRow = modDistance(mz, RAIL_GRID_HALF_PERIOD_CHUNKS) <= 1

        return RailGridInfo(
            mx = mx,
            mz = mz,
            subwayEW = onRailRow,
            subwayNS = onRailColumn,
            station = cityInfluence > 0.30 && onRailColumn && onRailRow,
            stationApproach = cityInfluence > 0.28 && nearRailColumn && nearRailRow
        )
    }

    private fun applyTerrainFit(
        world: StructureWorldAccess,
        origin: BlockPos,
        plan: CityChunkPlan
    ): CityChunkPlan {
        if (!plan.isActive || plan.kind == ChunkKind.HIGHWAY) return plan

        val fit = terrainFit(world, origin, plan.groundY)
        val rejectForWater = when (plan.kind) {
            ChunkKind.TOWER, ChunkKind.MIDRISE, ChunkKind.SUBURB, ChunkKind.PARK -> fit.waterRatio > BUILDING_WATER_REJECT_RATIO
            ChunkKind.STATION, ChunkKind.AVENUE, ChunkKind.STREET -> fit.waterRatio > ROAD_WATER_REJECT_RATIO
            else -> false
        }
        val rejectForCut = fit.steepCut && plan.kind != ChunkKind.AVENUE && plan.kind != ChunkKind.STREET

        if ((rejectForWater && plan.style != CityTone.MARINA) || rejectForCut) {
            return plan.copy(
                kind = ChunkKind.OUTSIDE,
                subwayNS = false,
                subwayEW = false,
                stationZone = false,
                avenueNS = false,
                avenueEW = false,
                streetNS = false,
                streetEW = false
            )
        }

        val terrainDelta = fit.avgSurfaceY - plan.groundY
        val adjustedY = when {
            fit.waterHeavy -> max(plan.groundY, 70)
            abs(terrainDelta) <= 10 -> plan.groundY
            else -> quantizeCityY((plan.groundY + terrainDelta / 2).coerceIn(CITY_GROUND_MIN_Y, CITY_GROUND_MAX_Y))
        }

        return plan.copy(groundY = adjustedY)
    }

    private fun terrainFit(world: StructureWorldAccess, origin: BlockPos, targetY: Int): TerrainFit {
        var waterColumns = 0
        var totalSurface = 0
        var maxSurface = world.bottomY
        var samples = 0

        for (x in 2..13 step 3) {
            for (z in 2..13 step 3) {
                val worldX = origin.x + x
                val worldZ = origin.z + z
                val surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, worldX, worldZ)
                val oceanY = world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, worldX, worldZ)
                val surfaceState = world.getBlockState(BlockPos(worldX, max(world.bottomY + 1, surfaceY - 1), worldZ))
                if (surfaceState.block == Blocks.WATER || surfaceY - oceanY > 5) {
                    waterColumns++
                }
                totalSurface += surfaceY
                maxSurface = max(maxSurface, surfaceY)
                samples++
            }
        }

        val avgSurface = if (samples == 0) targetY else totalSurface / samples
        val waterRatio = if (samples == 0) 0.0 else waterColumns.toDouble() / samples.toDouble()
        return TerrainFit(
            waterRatio = waterRatio,
            avgSurfaceY = avgSurface,
            maxSurfaceY = maxSurface,
            waterHeavy = waterRatio > BUILDING_WATER_REJECT_RATIO,
            steepCut = maxSurface - targetY > TERRAIN_CUT_REJECT_DELTA
        )
    }

    private fun quantizeCityY(y: Int): Int {
        val steps = ((y - CITY_GROUND_MIN_Y).toDouble() / CITY_GROUND_STEP.toDouble()).roundToInt()
        return (CITY_GROUND_MIN_Y + steps * CITY_GROUND_STEP).coerceIn(CITY_GROUND_MIN_Y, CITY_GROUND_MAX_Y)
    }

    private fun shapeChunkTerrain(
        world: StructureWorldAccess,
        origin: BlockPos,
        plan: CityChunkPlan,
        westPlan: CityChunkPlan,
        eastPlan: CityChunkPlan,
        northPlan: CityChunkPlan,
        southPlan: CityChunkPlan,
        streetPalette: StreetPalette
    ) {
        if (plan.kind == ChunkKind.HIGHWAY) {
            return
        }

        val surfaceBlock = when (plan.kind) {
            ChunkKind.PARK -> streetPalette.parkGround
            ChunkKind.SUBURB -> streetPalette.suburbLot
            ChunkKind.TOWER, ChunkKind.MIDRISE -> streetPalette.corePaving
            else -> streetPalette.corePaving
        }
        val clearTop = when (plan.kind) {
            ChunkKind.TOWER -> 220
            ChunkKind.MIDRISE -> 170
            ChunkKind.STATION -> plan.groundY + 18
            else -> plan.groundY + 10
        }

        for (x in 0..15) {
            for (z in 0..15) {
                val worldX = origin.x + x
                val worldZ = origin.z + z
                val targetY = plan.groundY
                val topY = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, worldX, worldZ)
                val clearUntil = min(255, max(topY + 3, clearTop))

                for (y in (targetY + 1)..clearUntil) {
                    val pos = BlockPos(worldX, y, worldZ)
                    if (!world.getBlockState(pos).isAir) {
                        world.setBlockState(pos, Blocks.AIR.defaultState, 2)
                    }
                }

                var fillY = targetY - 1
                while (fillY > world.bottomY) {
                    val pos = BlockPos(worldX, fillY, worldZ)
                    val state = world.getBlockState(pos)
                    if (state.isOpaqueFullCube(world, pos)) break
                    world.setBlockState(pos, streetPalette.support, 2)
                    fillY--
                }

                world.setBlockState(BlockPos(worldX, targetY, worldZ), surfaceBlock, 2)
            }
        }

    }

    private fun buildChunkEdgeWall(
        world: StructureWorldAccess,
        origin: BlockPos,
        direction: Direction,
        groundY: Int,
        streetPalette: StreetPalette
    ) {
        val range = 0..15
        for (i in range) {
            val worldX = when (direction) {
                Direction.WEST -> origin.x
                Direction.EAST -> origin.x + 15
                else -> origin.x + i
            }
            val worldZ = when (direction) {
                Direction.NORTH -> origin.z
                Direction.SOUTH -> origin.z + 15
                else -> origin.z + i
            }

            var supportY = groundY
            while (supportY > world.bottomY) {
                val pos = BlockPos(worldX, supportY, worldZ)
                val state = world.getBlockState(pos)
                if (state.isOpaqueFullCube(world, pos) && supportY < groundY - 1) break
                if (!state.isOpaqueFullCube(world, pos) || supportY >= groundY - 1) {
                    world.setBlockState(pos, streetPalette.support, 2)
                }
                supportY--
            }
        }
    }

    private fun generateRoadSurfaceChunk(
        world: StructureWorldAccess,
        origin: BlockPos,
        plan: CityChunkPlan,
        streetPalette: StreetPalette
    ) {
        val roadNS = plan.highwayNS || plan.avenueNS || plan.streetNS || plan.stationZone
        val roadEW = plan.highwayEW || plan.avenueEW || plan.streetEW || plan.stationZone
        val roadHalfWidth = when (plan.kind) {
            ChunkKind.HIGHWAY -> 4
            ChunkKind.AVENUE, ChunkKind.STATION -> 3
            else -> 2
        }
        val stripeColor = if (plan.kind == ChunkKind.HIGHWAY) streetPalette.corePaving else streetPalette.avenueStripe

        for (x in 0..15) {
            for (z in 0..15) {
                val dx = abs(x - 7)
                val dz = abs(z - 7)
                val nsRoad = roadNS && dx <= roadHalfWidth
                val ewRoad = roadEW && dz <= roadHalfWidth
                val sidewalk = (roadNS && dx == roadHalfWidth + 1) || (roadEW && dz == roadHalfWidth + 1)
                val pos = BlockPos(origin.x + x, plan.groundY, origin.z + z)

                when {
                    nsRoad || ewRoad -> world.setBlockState(pos, streetPalette.avenue, 2)
                    sidewalk || plan.kind == ChunkKind.STATION -> world.setBlockState(pos, streetPalette.corePaving, 2)
                }

                if (nsRoad && !ewRoad && x == 7 && z % 4 != 0) {
                    world.setBlockState(pos, stripeColor, 2)
                }
                if (ewRoad && !nsRoad && z == 7 && x % 4 != 0) {
                    world.setBlockState(pos, stripeColor, 2)
                }
            }
        }

        val shouldLamp = when (plan.kind) {
            ChunkKind.STATION -> true
            ChunkKind.AVENUE -> positiveHash(plan.chunkX, plan.chunkZ) % 3 == 0
            else -> false
        }
        if (shouldLamp) {
            for ((lampX, lampZ) in listOf(2 to 2, 13 to 2, 2 to 13, 13 to 13)) {
                val base = BlockPos(origin.x + lampX, plan.groundY + 1, origin.z + lampZ)
                world.setBlockState(base, Blocks.STONE_BRICK_WALL.defaultState, 2)
                world.setBlockState(base.up(), Blocks.STONE_BRICK_WALL.defaultState, 2)
                world.setBlockState(base.up(2), streetPalette.lamp, 2)
            }
        }
    }

    private fun generatePlazaChunk(
        world: StructureWorldAccess,
        origin: BlockPos,
        cityBaseY: Int,
        plan: CityChunkPlan,
        streetPalette: StreetPalette
    ) {
        val hash = positiveHash(plan.chunkX, plan.chunkZ)
        val accent = when (plan.style) {
            CityTone.GARDEN -> Blocks.MOSSY_STONE_BRICKS.defaultState
            CityTone.MARINA -> Blocks.PRISMARINE_BRICKS.defaultState
            CityTone.INDUSTRIAL -> Blocks.POLISHED_DEEPSLATE.defaultState
            CityTone.BRICKWORK -> Blocks.MUD_BRICKS.defaultState
            CityTone.CIVIC -> Blocks.POLISHED_ANDESITE.defaultState
        }

        if (hash % 4 == 0) {
            for (x in 4..11) {
                world.setBlockState(BlockPos(origin.x + x, cityBaseY, origin.z + 4), accent, 2)
                world.setBlockState(BlockPos(origin.x + x, cityBaseY, origin.z + 11), accent, 2)
            }
            for (z in 4..11) {
                world.setBlockState(BlockPos(origin.x + 4, cityBaseY, origin.z + z), accent, 2)
                world.setBlockState(BlockPos(origin.x + 11, cityBaseY, origin.z + z), accent, 2)
            }
        }

        if (hash % 5 == 0) {
            val base = BlockPos(origin.x + 8, cityBaseY + 1, origin.z + 8)
            world.setBlockState(base, Blocks.STONE_BRICK_WALL.defaultState, 2)
            world.setBlockState(base.up(), Blocks.STONE_BRICK_WALL.defaultState, 2)
            world.setBlockState(base.up(2), streetPalette.lamp, 2)
        }
    }

    private fun generateSubwayStationChunk(
        world: StructureWorldAccess,
        origin: BlockPos,
        plan: CityChunkPlan,
        streetPalette: StreetPalette,
        stationFloorY: Int,
        stationRailY: Int
    ) {
        val hallMin = 2
        val hallMax = 13
        for (x in hallMin..hallMax) {
            for (z in hallMin..hallMax) {
                val edge = x == hallMin || x == hallMax || z == hallMin || z == hallMax
                for (y in stationFloorY..(stationRailY + 5)) {
                    val pos = BlockPos(origin.x + x, y, origin.z + z)
                    when {
                        y == stationFloorY -> world.setBlockState(pos, Blocks.POLISHED_ANDESITE.defaultState, 2)
                        y == stationRailY + 5 -> world.setBlockState(pos, Blocks.STONE_BRICKS.defaultState, 2)
                        edge -> world.setBlockState(pos, streetPalette.tunnelWall, 2)
                        else -> world.setBlockState(pos, Blocks.AIR.defaultState, 2)
                    }
                }
            }
        }

        for (x in 5..10) {
            for (z in 5..10) {
                val edge = x == 5 || x == 10 || z == 5 || z == 10
                for (y in (stationRailY + 6)..(plan.groundY + 7)) {
                    val pos = BlockPos(origin.x + x, y, origin.z + z)
                    when {
                        y == plan.groundY -> world.setBlockState(pos, Blocks.SMOOTH_STONE.defaultState, 2)
                        edge -> world.setBlockState(pos, Blocks.STONE_BRICKS.defaultState, 2)
                        else -> world.setBlockState(pos, Blocks.AIR.defaultState, 2)
                    }
                }
            }
        }

        for (y in (stationRailY + 1)..(plan.groundY + 3)) {
            val ladderPos = BlockPos(origin.x + 5, y, origin.z + 8)
            world.setBlockState(ladderPos, Blocks.LADDER.defaultState.with(Properties.HORIZONTAL_FACING, Direction.EAST), 2)
            world.setBlockState(BlockPos(origin.x + 6, y, origin.z + 8), Blocks.AIR.defaultState, 2)
            world.setBlockState(BlockPos(origin.x + 7, y, origin.z + 8), Blocks.AIR.defaultState, 2)
            world.setBlockState(BlockPos(origin.x + 8, y, origin.z + 8), Blocks.AIR.defaultState, 2)
        }

        for (x in 4..11) {
            for (z in 4..11) {
                val pos = BlockPos(origin.x + x, plan.groundY + 1, origin.z + z)
                val edge = x == 4 || x == 11 || z == 4 || z == 11
                val doorway = z == 4 && x in 7..8
                when {
                    doorway -> world.setBlockState(pos, Blocks.AIR.defaultState, 2)
                    edge -> world.setBlockState(pos, Blocks.STONE_BRICKS.defaultState, 2)
                    x in 6..9 && z in 6..9 -> world.setBlockState(pos, Blocks.AIR.defaultState, 2)
                    else -> world.setBlockState(pos, Blocks.AIR.defaultState, 2)
                }
                if (edge && !doorway) {
                    world.setBlockState(pos.up(), Blocks.STONE_BRICKS.defaultState, 2)
                    world.setBlockState(pos.up(2), Blocks.STONE_BRICKS.defaultState, 2)
                }
                world.setBlockState(BlockPos(origin.x + x, plan.groundY + 4, origin.z + z), Blocks.SMOOTH_STONE.defaultState, 2)
            }
        }

        for ((lampX, lampZ) in listOf(3 to 3, 12 to 3, 3 to 12, 12 to 12, 7 to 7, 8 to 8)) {
            world.setBlockState(BlockPos(origin.x + lampX, stationRailY + 4, origin.z + lampZ), streetPalette.lamp, 2)
        }

        if (plan.subwayEW) {
            for (x in 1..14) {
                val railPos = BlockPos(origin.x + x, stationRailY, origin.z + 8)
                if (x % 8 == 0) {
                    world.setBlockState(
                        railPos,
                        Blocks.POWERED_RAIL.defaultState
                            .with(PoweredRailBlock.POWERED, true)
                            .with(Properties.STRAIGHT_RAIL_SHAPE, RailShape.EAST_WEST),
                        2
                    )
                    world.setBlockState(railPos.down(), Blocks.REDSTONE_BLOCK.defaultState, 2)
                } else {
                    world.setBlockState(railPos, Blocks.RAIL.defaultState.with(Properties.RAIL_SHAPE, RailShape.EAST_WEST), 2)
                }
            }
        }

        if (plan.subwayNS) {
            for (z in 1..14) {
                val railPos = BlockPos(origin.x + 8, stationRailY, origin.z + z)
                if (z % 8 == 0) {
                    world.setBlockState(
                        railPos,
                        Blocks.POWERED_RAIL.defaultState
                            .with(PoweredRailBlock.POWERED, true)
                            .with(Properties.STRAIGHT_RAIL_SHAPE, RailShape.NORTH_SOUTH),
                        2
                    )
                    world.setBlockState(railPos.down(), Blocks.REDSTONE_BLOCK.defaultState, 2)
                } else {
                    world.setBlockState(railPos, Blocks.RAIL.defaultState.with(Properties.RAIL_SHAPE, RailShape.NORTH_SOUTH), 2)
                }
            }
        }
    }

    private fun generateGrandHighwayChunk(
        world: StructureWorldAccess,
        origin: BlockPos,
        plan: CityChunkPlan,
        streetPalette: StreetPalette
    ) {
        val deckY = plan.groundY
        val eastWest = plan.highwayEW || !plan.highwayNS
        val halfWidth = 5

        for (x in 0..15) {
            for (z in 0..15) {
                val axisDistance = if (eastWest) abs(z - 7) else abs(x - 7)
                val worldX = origin.x + x
                val worldZ = origin.z + z
                val deckPos = BlockPos(worldX, deckY, worldZ)

                for (y in (deckY + 1)..(deckY + 6)) {
                    world.setBlockState(BlockPos(worldX, y, worldZ), Blocks.AIR.defaultState, 2)
                }

                val block = when {
                    axisDistance <= halfWidth -> streetPalette.avenue
                    else -> Blocks.SMOOTH_STONE.defaultState
                }
                world.setBlockState(deckPos, block, 2)
                world.setBlockState(deckPos.down(), streetPalette.support, 2)

                if (axisDistance == 0 && ((if (eastWest) x else z) % 4 != 0)) {
                    world.setBlockState(deckPos, streetPalette.avenueStripe, 2)
                }

                val along = if (eastWest) x else z
                val shouldSupport = along % 5 == 0 && axisDistance in 0..halfWidth
                if (shouldSupport) {
                    var y = deckY - 1
                    while (y > world.bottomY) {
                        val pos = BlockPos(worldX, y, worldZ)
                        val state = world.getBlockState(pos)
                        if (state.isOpaqueFullCube(world, pos)) {
                            world.setBlockState(pos, streetPalette.support, 2)
                            break
                        }
                        world.setBlockState(pos, streetPalette.support, 2)
                        y--
                    }
                }

                val edge = if (eastWest) z == 1 || z == 14 else x == 1 || x == 14
                if (edge) {
                    world.setBlockState(BlockPos(worldX, deckY + 1, worldZ), Blocks.STONE_BRICK_WALL.defaultState, 2)
                }

                if ((axisDistance == halfWidth + 1 || edge) && along % 8 == 0) {
                    val base = BlockPos(worldX, deckY + 1, worldZ)
                    world.setBlockState(base, Blocks.STONE_BRICK_WALL.defaultState, 2)
                    world.setBlockState(base.up(), Blocks.STONE_BRICK_WALL.defaultState, 2)
                    world.setBlockState(base.up(2), streetPalette.lamp, 2)
                }
            }
        }
    }

    private fun generateSubwayChunk(
        world: StructureWorldAccess,
        origin: BlockPos,
        plan: CityChunkPlan,
        streetPalette: StreetPalette,
        stationRailY: Int
    ) {
        fun carveEw() {
            for (x in 0..15) {
                for (z in 6..9) {
                    for (y in (stationRailY - 1)..(stationRailY + 4)) {
                        val isWall = z == 6 || z == 9 || y == stationRailY - 1 || y == stationRailY + 4
                        val pos = BlockPos(origin.x + x, y, origin.z + z)
                        world.setBlockState(pos, if (isWall) streetPalette.tunnelWall else Blocks.AIR.defaultState, 2)
                    }
                }
                if (x % 8 == 0) {
                    world.setBlockState(BlockPos(origin.x + x, stationRailY + 2, origin.z + 6), streetPalette.lamp, 2)
                    world.setBlockState(BlockPos(origin.x + x, stationRailY + 2, origin.z + 9), streetPalette.lamp, 2)
                }
                val railPos = BlockPos(origin.x + x, stationRailY, origin.z + 8)
                if (x % 8 == 0) {
                    world.setBlockState(
                        railPos,
                        Blocks.POWERED_RAIL.defaultState
                            .with(PoweredRailBlock.POWERED, true)
                            .with(Properties.STRAIGHT_RAIL_SHAPE, RailShape.EAST_WEST),
                        2
                    )
                    world.setBlockState(railPos.down(), Blocks.REDSTONE_BLOCK.defaultState, 2)
                } else {
                    world.setBlockState(railPos, Blocks.RAIL.defaultState.with(Properties.RAIL_SHAPE, RailShape.EAST_WEST), 2)
                }
            }
        }

        fun carveNs() {
            for (x in 6..9) {
                for (z in 0..15) {
                    for (y in (stationRailY - 1)..(stationRailY + 4)) {
                        val isWall = x == 6 || x == 9 || y == stationRailY - 1 || y == stationRailY + 4
                        val pos = BlockPos(origin.x + x, y, origin.z + z)
                        world.setBlockState(pos, if (isWall) streetPalette.tunnelWall else Blocks.AIR.defaultState, 2)
                    }
                }
            }
            for (z in 0..15) {
                if (z % 8 == 0) {
                    world.setBlockState(BlockPos(origin.x + 6, stationRailY + 2, origin.z + z), streetPalette.lamp, 2)
                    world.setBlockState(BlockPos(origin.x + 9, stationRailY + 2, origin.z + z), streetPalette.lamp, 2)
                }
                val railPos = BlockPos(origin.x + 8, stationRailY, origin.z + z)
                if (z % 8 == 0) {
                    world.setBlockState(
                        railPos,
                        Blocks.POWERED_RAIL.defaultState
                            .with(PoweredRailBlock.POWERED, true)
                            .with(Properties.STRAIGHT_RAIL_SHAPE, RailShape.NORTH_SOUTH),
                        2
                    )
                    world.setBlockState(railPos.down(), Blocks.REDSTONE_BLOCK.defaultState, 2)
                } else {
                    world.setBlockState(railPos, Blocks.RAIL.defaultState.with(Properties.RAIL_SHAPE, RailShape.NORTH_SOUTH), 2)
                }
            }
        }

        if (plan.subwayEW) carveEw()
        if (plan.subwayNS) carveNs()
    }

    private fun generateParkLot(
        world: StructureWorldAccess,
        origin: BlockPos,
        cityBaseY: Int,
        plan: CityChunkPlan,
        streetPalette: StreetPalette
    ) {
        val parkHash = positiveHash(origin.x shr 4, origin.z shr 4)
        val hasPond = parkHash % 3 == 0
        val hasGazebo = parkHash % 3 == 1
        val hasFountain = parkHash % 5 == 0

        for (x in 2..13) {
            for (z in 2..13) {
                val gX = origin.x + x
                val gZ = origin.z + z
                val dx = x - 8
                val dz = z - 8
                val radial = dx * dx + dz * dz
                val isCrossPath = abs(dx) <= 1 || abs(dz) <= 1
                val isRingPath = radial in 20..34
                val floor = when {
                    hasPond && radial <= 9 -> Blocks.WATER.defaultState
                    hasPond && radial in 10..16 -> Blocks.STONE_BRICKS.defaultState
                    hasFountain && radial <= 4 -> Blocks.SMOOTH_STONE.defaultState
                    isCrossPath || isRingPath -> streetPalette.suburbStreet
                    else -> streetPalette.parkGround
                }
                world.setBlockState(BlockPos(gX, cityBaseY, gZ), floor, 2)
                for (y in (cityBaseY + 1)..(cityBaseY + 6)) {
                    world.setBlockState(BlockPos(gX, y, gZ), Blocks.AIR.defaultState, 2)
                }
            }
        }

        if (hasGazebo) {
            for (x in 6..10) {
                for (z in 6..10) {
                    val edge = x == 6 || x == 10 || z == 6 || z == 10
                    world.setBlockState(
                        BlockPos(origin.x + x, cityBaseY + 1, origin.z + z),
                        if (edge) Blocks.OAK_FENCE.defaultState else Blocks.AIR.defaultState,
                        2
                    )
                    world.setBlockState(BlockPos(origin.x + x, cityBaseY + 4, origin.z + z), Blocks.SPRUCE_SLAB.defaultState, 2)
                }
            }
        } else if (hasFountain) {
            world.setBlockState(BlockPos(origin.x + 8, cityBaseY + 1, origin.z + 8), Blocks.STONE_BRICK_WALL.defaultState, 2)
            world.setBlockState(BlockPos(origin.x + 8, cityBaseY + 2, origin.z + 8), Blocks.WATER.defaultState, 2)
        } else {
            for ((treeX, treeZ) in listOf(5 to 5, 11 to 5, 5 to 11, 11 to 11)) {
                world.setBlockState(BlockPos(origin.x + treeX, cityBaseY + 1, origin.z + treeZ), Blocks.OAK_LOG.defaultState, 2)
                world.setBlockState(BlockPos(origin.x + treeX, cityBaseY + 2, origin.z + treeZ), Blocks.OAK_LOG.defaultState, 2)
                for (leafX in -1..1) {
                    for (leafZ in -1..1) {
                        world.setBlockState(BlockPos(origin.x + treeX + leafX, cityBaseY + 3, origin.z + treeZ + leafZ), Blocks.OAK_LEAVES.defaultState, 2)
                    }
                }
            }
        }

        val lamps = if (plan.style == CityTone.GARDEN) {
            listOf(4 to 4, 12 to 4, 4 to 12, 12 to 12)
        } else {
            listOf(3 to 3, 13 to 3, 3 to 13, 13 to 13)
        }
        for ((lampX, lampZ) in lamps) {
            val base = BlockPos(origin.x + lampX, cityBaseY + 1, origin.z + lampZ)
            world.setBlockState(base, Blocks.STONE_BRICK_WALL.defaultState, 2)
            world.setBlockState(base.up(), Blocks.STONE_BRICK_WALL.defaultState, 2)
            world.setBlockState(base.up(2), streetPalette.lamp, 2)
        }
    }

    private fun generateTowerChunk(
        context: FeatureContext<DefaultFeatureConfig>,
        cityBaseY: Int,
        plan: CityChunkPlan,
        palettes: List<SkyscraperPalette>,
        tall: Boolean
    ): String? {
        val world = context.world
        val origin = context.origin
        val chunkHash = positiveHash(origin.x shr 4, origin.z shr 4)
        val palette = pickSkyscraperPalette(plan.style, palettes, chunkHash)
        val frontFace = pickSkyscraperFront(districtInfo(origin.x + 8, origin.z + 8), chunkHash)
        val height = cityBaseY + if (tall) 34 + (chunkHash % 42) else 18 + (chunkHash % 18)
        val inset = if (tall) 1 + (chunkHash % 2) else 2 + (chunkHash % 2)
        val min = inset
        val max = 15 - inset
        val hasPageLoot = tall && chunkHash % 18 == 0

        for (x in min..max) {
            for (z in min..max) {
                val gX = origin.x + x
                val gZ = origin.z + z
                world.setBlockState(BlockPos(gX, cityBaseY, gZ), palette.foundation, 2)

                for (y in (cityBaseY + 1)..height) {
                    val pos = BlockPos(gX, y, gZ)
                    val isFloor = (y - cityBaseY) % 4 == 0
                    val isOuterWall = x == min || x == max || z == min || z == max
                    val wallFace = when {
                        x == min && z in (min + 1) until max -> Direction.WEST
                        x == max && z in (min + 1) until max -> Direction.EAST
                        z == min && x in (min + 1) until max -> Direction.NORTH
                        z == max && x in (min + 1) until max -> Direction.SOUTH
                        else -> null
                    }

                    when {
                        isFloor -> world.setBlockState(pos, palette.floor, 2)
                        isOuterWall -> {
                            val isPillar = x % 4 == 0 || z % 4 == 0 || wallFace == null
                            val isSolidBand = (y - cityBaseY) % 8 == 1
                            val facadeBlock = when {
                                isPillar -> palette.pillar
                                isSolidBand -> palette.wall
                                wallFace == frontFace -> palette.glass
                                (y - cityBaseY) % 6 == 2 -> palette.glass
                                else -> palette.wall
                            }
                            world.setBlockState(pos, facadeBlock, 2)
                        }
                        else -> world.setBlockState(pos, Blocks.AIR.defaultState, 2)
                    }
                }
            }
        }

        decorateSkyscraperInterior(world, origin, cityBaseY, height, frontFace, palette, chunkHash, hasPageLoot)
        return if (tall) "tower(height=$height)" else "midrise(height=$height)"
    }

    private fun generateSuburbLot(
        world: StructureWorldAccess,
        origin: BlockPos,
        cityBaseY: Int,
        plan: CityChunkPlan,
        palettes: List<HousePalette>
    ): String? {
        val lotHash = positiveHash(origin.x shr 4, origin.z shr 4)
        val styles = houseStylesFor(plan.style)
        val style = styles[lotHash % styles.size]
        val houseOffsetX = 1 + ((lotHash shr 2) and 1) + max(0, (12 - style.width) / 2)
        val houseOffsetZ = 1 + ((lotHash shr 3) and 1) + max(0, (12 - style.depth) / 2)
        val housePos = BlockPos(origin.x + houseOffsetX, cityBaseY, origin.z + houseOffsetZ)
        val hasPageLoot = lotHash % 30 == 0
        val palette = pickHousePalette(plan.style, palettes, lotHash)
        generateVillageStyleHouse(world, housePos, style, palette, lotHash, hasPageLoot)
        return "suburb(${style.family},loot=$hasPageLoot)"
    }

    private fun modDistance(value: Int, spacing: Int): Int {
        val mod = Math.floorMod(value, spacing)
        return min(mod, spacing - mod)
    }

    private fun generateRoadColumn(
        context: FeatureContext<DefaultFeatureConfig>,
        gX: Int,
        gZ: Int,
        roadY: Int,
        info: DistrictInfo,
        isBridge: Boolean
    ) {
        val world = context.world
        val streetPalette = streetPaletteFor(info.style)

        if (isBridge) {
            world.setBlockState(BlockPos(gX, roadY - 1, gZ), streetPalette.support, 2)
            world.setBlockState(BlockPos(gX, roadY, gZ), streetPalette.avenue, 2)

            val actualMinDist = info.minHighwayDist
            if (actualMinDist == 6) {
                val along = if (info.highwayDistX < info.highwayDistZ) info.centerZ else info.centerX
                val spanLength = 96
                val spanPos = Math.floorMod(along, spanLength)
                val distToPeak = abs(spanPos - (spanLength / 2))
                val peakFactor = 1.0 - (distToPeak.toDouble() / (spanLength / 2).toDouble())
                val archY = roadY + 8 + max(0, (peakFactor * peakFactor * 26.0).toInt())
                val isPeak = distToPeak <= 1
                val seabedY = max(20, world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, gX, gZ) - 1)

                if (isPeak) {
                    for (y in seabedY..min(archY + 8, 120)) {
                        world.setBlockState(BlockPos(gX, y, gZ), streetPalette.support, 2)
                    }

                    for (dx in -1..1) {
                        for (dz in -1..1) {
                            if (abs(dx) == 1 && abs(dz) == 1) continue
                            for (y in max(seabedY, roadY + 1)..archY + 5) {
                                world.setBlockState(BlockPos(gX + dx, y, gZ + dz), streetPalette.avenueStripe, 2)
                            }
                        }
                    }
                } else {
                    for (y in roadY + 1..archY - 1) {
                        world.setBlockState(BlockPos(gX, y, gZ), Blocks.AIR.defaultState, 2)
                    }

                    world.setBlockState(BlockPos(gX, archY, gZ), streetPalette.avenueStripe, 2)
                    if (archY > roadY + 10 && spanPos % 3 == 0) {
                        world.setBlockState(BlockPos(gX, archY - 1, gZ), streetPalette.avenueStripe, 2)
                    }

                    if (spanPos % 6 == 0) {
                        for (y in roadY + 1 until archY) {
                            world.setBlockState(BlockPos(gX, y, gZ), Blocks.CHAIN.defaultState, 2)
                        }
                    }
                }
            }
            return
        }

        for (y in (roadY + 1)..(roadY + 4)) {
            world.setBlockState(BlockPos(gX, y, gZ), Blocks.AIR.defaultState, 2)
        }
        for (y in max(roadY - 3, 50)..<roadY) {
            val pos = BlockPos(gX, y, gZ)
            if (!world.getBlockState(pos).isOpaqueFullCube(world, pos)) {
                world.setBlockState(pos, Blocks.DIRT.defaultState, 2)
            }
        }

        world.setBlockState(BlockPos(gX, roadY - 1, gZ), streetPalette.support, 2)
        world.setBlockState(BlockPos(gX, roadY, gZ), streetPalette.avenue, 2)

        val actualMinDist = if (info.isCity) min(info.distX, info.distZ) else info.minHighwayDist
        if (actualMinDist == 0) {
            world.setBlockState(BlockPos(gX, roadY, gZ), streetPalette.avenueStripe, 2)
        }

        if (actualMinDist == 6 && ((gX % 32 == 0) || (gZ % 32 == 0))) {
            world.setBlockState(BlockPos(gX, roadY, gZ), streetPalette.corePaving, 2)
            world.setBlockState(BlockPos(gX, roadY + 1, gZ), Blocks.STONE_BRICK_WALL.defaultState, 2)
            world.setBlockState(BlockPos(gX, roadY + 2, gZ), Blocks.STONE_BRICK_WALL.defaultState, 2)
            world.setBlockState(BlockPos(gX, roadY + 3, gZ), streetPalette.lamp, 2)
        }
    }

    private fun generateTunnelColumn(
        context: FeatureContext<DefaultFeatureConfig>,
        gX: Int,
        gZ: Int,
        info: DistrictInfo,
        stationRailY: Int,
        isWaterSurface: Boolean
    ) {
        val world = context.world
        val tunnelY = stationRailY
        val streetPalette = streetPaletteFor(info.style)

        for (sy in (tunnelY - 1)..(tunnelY + 4)) {
            val pos = BlockPos(gX, sy, gZ)
            val isWall = (info.isSubwayX && info.distZ == 2) || (info.isSubwayZ && info.distX == 2) || sy == tunnelY - 1 || sy == tunnelY + 4
            val isLightColumn = sy == tunnelY + 2 && (
                (info.isSubwayX && info.distZ == 2 && Math.floorMod(gX, 8) == 0) ||
                (info.isSubwayZ && info.distX == 2 && Math.floorMod(gZ, 8) == 0)
            )
            if (isWall) {
                val material = when {
                    isLightColumn -> streetPalette.lamp
                    isWaterSurface && sy > tunnelY -> Blocks.GLASS.defaultState
                    else -> streetPalette.tunnelWall
                }
                world.setBlockState(pos, material, 2)
            } else {
                world.setBlockState(pos, Blocks.AIR.defaultState, 2)
            }
        }

        val isRailLine = (info.isSubwayX && info.distZ <= 1) || (info.isSubwayZ && info.distX <= 1)
        if (!isRailLine) return

        val railPos = BlockPos(gX, tunnelY, gZ)
        val shape = if (info.isSubwayX) RailShape.EAST_WEST else RailShape.NORTH_SOUTH
        if ((gX % 16 == 0) || (gZ % 16 == 0)) {
            world.setBlockState(
                railPos,
                Blocks.POWERED_RAIL.defaultState
                    .with(PoweredRailBlock.POWERED, true)
                    .with(Properties.STRAIGHT_RAIL_SHAPE, shape),
                2
            )
            world.setBlockState(railPos.down(), Blocks.REDSTONE_BLOCK.defaultState, 2)
        } else {
            world.setBlockState(railPos, Blocks.RAIL.defaultState.with(Properties.RAIL_SHAPE, shape), 2)
        }
    }

    private fun generateCentralStation(
        context: FeatureContext<DefaultFeatureConfig>,
        cityBaseY: Int,
        stationFloorY: Int,
        stationRailY: Int
    ) {
        val world = context.world
        val origin = context.origin
        val hallRadius = 18
        val shellRadius = 20

        for (x in 0..15) {
            for (z in 0..15) {
                val gX = origin.x + x
                val gZ = origin.z + z
                val info = districtInfo(gX, gZ)
                val isEntryStairwell = info.centerX in -46..-12 && info.centerZ in -25..-17
                val isEntryConnector = info.centerX in -16..-8 && info.centerZ in -17..-9
                val isEntryCanopy = info.centerX in -48..-40 && info.centerZ in -25..-17
                if (info.radius > 30 && !isEntryStairwell && !isEntryConnector && !isEntryCanopy) continue

                val posBase = BlockPos(gX, cityBaseY, gZ)
                val isInHall = abs(info.centerX) <= hallRadius && abs(info.centerZ) <= hallRadius
                val isInShell = abs(info.centerX) <= shellRadius && abs(info.centerZ) <= shellRadius
                val isTrackApproachX = info.centerZ in -1..1 && abs(info.centerX) in 10..30
                val isTrackApproachZ = info.centerX in -1..1 && abs(info.centerZ) in 10..30

                if (isInShell && !isInHall) {
                    for (y in stationFloorY..(stationRailY + 5)) {
                        val pos = BlockPos(gX, y, gZ)
                        val isOuterRing = abs(info.centerX) == shellRadius || abs(info.centerZ) == shellRadius
                        when {
                            y == stationFloorY -> world.setBlockState(pos, Blocks.STONE_BRICKS.defaultState, 2)
                            y == stationRailY + 5 -> world.setBlockState(pos, Blocks.STONE_BRICKS.defaultState, 2)
                            isOuterRing -> world.setBlockState(pos, Blocks.STONE_BRICKS.defaultState, 2)
                            else -> world.setBlockState(pos, Blocks.AIR.defaultState, 2)
                        }
                    }
                }

                if (isInHall) {
                    for (y in stationFloorY..(stationRailY + 5)) {
                        val pos = BlockPos(gX, y, gZ)
                        val isShell = abs(info.centerX) == hallRadius || abs(info.centerZ) == hallRadius || y == stationRailY + 5
                        when {
                            y == stationFloorY -> world.setBlockState(pos, Blocks.POLISHED_ANDESITE.defaultState, 2)
                            isShell -> world.setBlockState(pos, Blocks.STONE_BRICKS.defaultState, 2)
                            else -> world.setBlockState(pos, Blocks.AIR.defaultState, 2)
                        }
                    }

                    if ((abs(info.centerX) == 12 || abs(info.centerZ) == 12) && stationRailY + 4 <= stationRailY + 5) {
                        world.setBlockState(BlockPos(gX, stationRailY + 4, gZ), Blocks.SHROOMLIGHT.defaultState, 2)
                    }

                    if ((info.centerX == 9 && info.centerZ in -1..1) ||
                        (info.centerX == -9 && info.centerZ in -1..1) ||
                        (info.centerZ == 9 && info.centerX in -1..1) ||
                        (info.centerZ == -9 && info.centerX in -1..1)
                    ) {
                        world.setBlockState(BlockPos(gX, stationRailY, gZ), Blocks.CHISELED_STONE_BRICKS.defaultState, 2)
                        world.setBlockState(BlockPos(gX, stationRailY + 1, gZ), Blocks.STONE_BRICK_WALL.defaultState, 2)
                    }
                }

                if (isTrackApproachX || isTrackApproachZ) {
                    val isEastWest = isTrackApproachX
                    val shape = if (isEastWest) RailShape.EAST_WEST else RailShape.NORTH_SOUTH
                    val crossAxis = if (isEastWest) abs(info.centerZ) else abs(info.centerX)
                    val isTrackWall = crossAxis == 2
                    for (y in (stationRailY - 1)..(stationRailY + 4)) {
                        val pos = BlockPos(gX, y, gZ)
                        when {
                            y == stationRailY - 1 -> world.setBlockState(pos, Blocks.STONE_BRICKS.defaultState, 2)
                            y == stationRailY + 4 -> world.setBlockState(pos, Blocks.STONE_BRICKS.defaultState, 2)
                            isTrackWall -> world.setBlockState(pos, Blocks.STONE_BRICKS.defaultState, 2)
                            y <= stationRailY + 3 -> world.setBlockState(pos, Blocks.AIR.defaultState, 2)
                        }
                    }

                    if (crossAxis <= 1) {
                        val railPos = BlockPos(gX, stationRailY, gZ)
                        val powered = (if (isEastWest) gX else gZ) % 8 == 0
                        if (powered) {
                            world.setBlockState(
                                railPos,
                                Blocks.POWERED_RAIL.defaultState
                                    .with(PoweredRailBlock.POWERED, true)
                                    .with(Properties.STRAIGHT_RAIL_SHAPE, shape),
                                2
                            )
                            world.setBlockState(railPos.down(), Blocks.REDSTONE_BLOCK.defaultState, 2)
                        } else {
                            world.setBlockState(railPos, Blocks.RAIL.defaultState.with(Properties.RAIL_SHAPE, shape), 2)
                        }
                    }
                }

                if (isEntryStairwell) {
                    val stepIndex = info.centerX + 46
                    val stairY = cityBaseY - stepIndex
                    val isEdge = info.centerZ == -25 || info.centerZ == -17
                    val isTopLanding = stepIndex <= 4
                    val isBottomLanding = stepIndex >= 31
                    val stairState = Blocks.STONE_BRICK_STAIRS.defaultState.with(Properties.HORIZONTAL_FACING, Direction.WEST)

                    for (y in stationFloorY..(cityBaseY + 5)) {
                        val pos = BlockPos(gX, y, gZ)
                        when {
                            y < stairY -> world.setBlockState(pos, Blocks.STONE_BRICKS.defaultState, 2)
                            y == stairY && !isEdge -> world.setBlockState(pos, stairState, 2)
                            y <= stairY + 3 && !isEdge -> world.setBlockState(pos, Blocks.AIR.defaultState, 2)
                            y <= stairY + 2 && isEdge -> world.setBlockState(pos, Blocks.STONE_BRICKS.defaultState, 2)
                            y == stairY + 3 && isEdge -> world.setBlockState(pos, Blocks.STONE_BRICK_WALL.defaultState, 2)
                            (isTopLanding || isBottomLanding) && !isEdge -> world.setBlockState(pos, Blocks.AIR.defaultState, 2)
                            else -> world.setBlockState(pos, Blocks.STONE_BRICKS.defaultState, 2)
                        }
                    }
                }

                if (isEntryConnector) {
                    val floorY = stationFloorY + 1
                    val doorway = info.centerX in -11..-8 && info.centerZ in -11..-9
                    val isEdge = info.centerX == -16 || info.centerX == -8 || info.centerZ == -16 || info.centerZ == -8
                    for (y in stationFloorY..(floorY + 4)) {
                        val pos = BlockPos(gX, y, gZ)
                        when {
                            y == stationFloorY -> world.setBlockState(pos, Blocks.STONE_BRICKS.defaultState, 2)
                            y == floorY -> world.setBlockState(pos, Blocks.SMOOTH_STONE.defaultState, 2)
                            doorway && y <= floorY + 3 -> world.setBlockState(pos, Blocks.AIR.defaultState, 2)
                            isEdge -> world.setBlockState(pos, Blocks.STONE_BRICKS.defaultState, 2)
                            info.centerX in -15..-12 && info.centerZ in -15..-12 && y <= floorY + 3 ->
                                world.setBlockState(pos, Blocks.AIR.defaultState, 2)
                            else -> world.setBlockState(pos, Blocks.STONE_BRICKS.defaultState, 2)
                        }
                    }
                }

                if (isEntryCanopy) {
                    val isOuter = info.centerX == -48 || info.centerX == -40 || info.centerZ == -25 || info.centerZ == -17
                    val opening = info.centerX <= -44 && info.centerZ in -23..-19
                    if (!opening) {
                        world.setBlockState(BlockPos(gX, cityBaseY, gZ), Blocks.SMOOTH_STONE.defaultState, 2)
                    }
                    for (y in (cityBaseY + 1)..(cityBaseY + 4)) {
                        val pos = BlockPos(gX, y, gZ)
                        when {
                            opening -> world.setBlockState(pos, Blocks.AIR.defaultState, 2)
                            isOuter && y <= cityBaseY + 3 -> world.setBlockState(pos, Blocks.STONE_BRICKS.defaultState, 2)
                            y <= cityBaseY + 3 -> world.setBlockState(pos, Blocks.AIR.defaultState, 2)
                            else -> world.setBlockState(pos, Blocks.STONE_BRICKS.defaultState, 2)
                        }
                    }
                }
            }
        }
    }

    private fun generateSkyscraperChunk(
        context: FeatureContext<DefaultFeatureConfig>,
        cityBaseY: Int,
        palettes: List<SkyscraperPalette>
    ): String? {
        val world = context.world
        val origin = context.origin
        val chunkInfo = districtInfo(origin.x + 8, origin.z + 8)
        if (!chunkInfo.isCore || chunkInfo.radius <= 28) return null

        val chunkAxisDistance = chunkInfo.avenueAxisDistance - 8
        if (chunkAxisDistance <= 14) return "skyscraper-skip(near-avenue)"
        for (x in 4..14) {
            for (z in 4..14) {
                val info = districtInfo(origin.x + x, origin.z + z)
                if (isEntrySurfaceProtected(info.centerX, info.centerZ)) {
                    return "skyscraper-skip(entry-access)"
                }
            }
        }

        val chunkHash = positiveHash(origin.x shr 4, origin.z shr 4)
        val palette = pickSkyscraperPalette(chunkInfo.style, palettes, chunkHash)
        val heightBias = when (chunkInfo.style) {
            CityTone.CIVIC -> 26
            CityTone.INDUSTRIAL -> 14
            CityTone.BRICKWORK -> 18
            CityTone.GARDEN -> 8
            CityTone.MARINA -> 22
        }
        val height = cityBaseY + 28 + heightBias + (chunkHash % 54)
        val frontFace = pickSkyscraperFront(chunkInfo, chunkHash)
        val hasPageLoot = chunkHash % 15 == 0

        for (x in 4..14) {
            for (z in 4..14) {
                val gX = origin.x + x
                val gZ = origin.z + z
                world.setBlockState(BlockPos(gX, cityBaseY, gZ), palette.foundation, 2)

                for (y in (cityBaseY + 1)..height) {
                    if (shouldDecay(gX, y, gZ)) continue

                    val pos = BlockPos(gX, y, gZ)
                    val isFloor = (y - cityBaseY) % 4 == 0
                    val isOuterWall = x == 4 || x == 14 || z == 4 || z == 14
                    val wallFace = when {
                        x == 4 && z in 5..13 -> Direction.WEST
                        x == 14 && z in 5..13 -> Direction.EAST
                        z == 4 && x in 5..13 -> Direction.NORTH
                        z == 14 && x in 5..13 -> Direction.SOUTH
                        else -> null
                    }

                    when {
                        isFloor -> world.setBlockState(pos, palette.floor, 2)
                        isOuterWall -> {
                            val isPillar = x % 4 == 0 || z % 4 == 0 || wallFace == null
                            val isSolidBand = (y - cityBaseY) % 8 == 1
                            val facadeBlock = when {
                                isPillar -> palette.pillar
                                isSolidBand -> palette.wall
                                wallFace == frontFace -> palette.glass
                                (y - cityBaseY) % 6 == 2 -> palette.glass
                                else -> palette.wall
                            }
                            world.setBlockState(pos, facadeBlock, 2)
                        }
                        else -> world.setBlockState(pos, Blocks.AIR.defaultState, 2)
                    }
                }
            }
        }
        decorateSkyscraperInterior(context.world, origin, cityBaseY, height, frontFace, palette, chunkHash, hasPageLoot)
        return "skyscraper(height=$height,palette=${chunkHash % palettes.size},loot=$hasPageLoot)"
    }

    private fun generateParkChunk(
        context: FeatureContext<DefaultFeatureConfig>,
        cityBaseY: Int,
        chunkInfo: DistrictInfo
    ): String? {
        if (!chunkInfo.isPark) return null

        val origin = context.origin
        val world = context.world
        val streetPalette = streetPaletteFor(chunkInfo.style)
        val parkHash = positiveHash(origin.x shr 4, origin.z shr 4)
        val hasPond = parkHash % 3 == 0
        val hasGazebo = parkHash % 3 == 1
        val hasFountain = parkHash % 5 == 0

        for (x in 2..13) {
            for (z in 2..13) {
                val gX = origin.x + x
                val gZ = origin.z + z
                val dx = x - 8
                val dz = z - 8
                val radial = dx * dx + dz * dz
                val isCrossPath = abs(dx) <= 1 || abs(dz) <= 1
                val isRingPath = radial in 20..34
                val pos = BlockPos(gX, cityBaseY, gZ)
                val floor = when {
                    hasPond && radial <= 9 -> Blocks.WATER.defaultState
                    hasPond && radial in 10..16 -> Blocks.STONE_BRICKS.defaultState
                    hasFountain && radial <= 4 -> Blocks.SMOOTH_STONE.defaultState
                    isCrossPath || isRingPath -> streetPalette.suburbStreet
                    else -> streetPalette.parkGround
                }
                world.setBlockState(pos, floor, 2)

                for (y in (cityBaseY + 1)..(cityBaseY + 6)) {
                    world.setBlockState(BlockPos(gX, y, gZ), Blocks.AIR.defaultState, 2)
                }

                if (!hasPond && !hasGazebo && !hasFountain && radial in 12..18 && parkHash % 2 == 0 && (x + z) % 5 == 0) {
                    world.setBlockState(BlockPos(gX, cityBaseY + 1, gZ), Blocks.FLOWERING_AZALEA_LEAVES.defaultState, 2)
                }
            }
        }

        val lampPositions = listOf(
            BlockPos(origin.x + 4, cityBaseY + 1, origin.z + 4),
            BlockPos(origin.x + 12, cityBaseY + 1, origin.z + 4),
            BlockPos(origin.x + 4, cityBaseY + 1, origin.z + 12),
            BlockPos(origin.x + 12, cityBaseY + 1, origin.z + 12)
        )
        for (lampBase in lampPositions) {
            world.setBlockState(lampBase, Blocks.STONE_BRICK_WALL.defaultState, 2)
            world.setBlockState(lampBase.up(), Blocks.STONE_BRICK_WALL.defaultState, 2)
            world.setBlockState(lampBase.up(2), streetPalette.lamp, 2)
        }

        if (hasGazebo) {
            for (x in 6..10) {
                for (z in 6..10) {
                    val edge = x == 6 || x == 10 || z == 6 || z == 10
                    world.setBlockState(
                        BlockPos(origin.x + x, cityBaseY + 1, origin.z + z),
                        if (edge) Blocks.OAK_FENCE.defaultState else Blocks.AIR.defaultState,
                        2
                    )
                    world.setBlockState(BlockPos(origin.x + x, cityBaseY + 4, origin.z + z), Blocks.SPRUCE_SLAB.defaultState, 2)
                }
            }
        } else if (hasFountain) {
            world.setBlockState(BlockPos(origin.x + 8, cityBaseY + 1, origin.z + 8), Blocks.STONE_BRICK_WALL.defaultState, 2)
            world.setBlockState(BlockPos(origin.x + 8, cityBaseY + 2, origin.z + 8), Blocks.WATER.defaultState, 2)
        } else if (!hasPond) {
            for ((treeX, treeZ) in listOf(5 to 5, 11 to 5, 5 to 11, 11 to 11)) {
                world.setBlockState(BlockPos(origin.x + treeX, cityBaseY + 1, origin.z + treeZ), Blocks.OAK_LOG.defaultState, 2)
                world.setBlockState(BlockPos(origin.x + treeX, cityBaseY + 2, origin.z + treeZ), Blocks.OAK_LOG.defaultState, 2)
                for (leafX in -1..1) {
                    for (leafZ in -1..1) {
                        world.setBlockState(BlockPos(origin.x + treeX + leafX, cityBaseY + 3, origin.z + treeZ + leafZ), Blocks.OAK_LEAVES.defaultState, 2)
                    }
                }
            }
        }

        return buildString {
            append("park(")
            append(
                when {
                    hasPond -> "pond"
                    hasGazebo -> "gazebo"
                    hasFountain -> "fountain"
                    else -> "garden"
                }
            )
            append(",tone=")
            append(chunkInfo.style)
            append(")")
        }
    }

    private fun generateSuburbHouseChunk(
        context: FeatureContext<DefaultFeatureConfig>,
        cityBaseY: Int,
        palettes: List<HousePalette>
    ): String? {
        val origin = context.origin
        val chunkInfo = districtInfo(origin.x + 8, origin.z + 8)
        if (!chunkInfo.isSuburb) return null

        val parkResult = generateParkChunk(context, cityBaseY, chunkInfo)
        if (parkResult != null) return parkResult

        val apartmentResult = generateApartmentBuildingChunk(context, cityBaseY)
        if (apartmentResult != null) return apartmentResult

        val distanceToMainAvenue = chunkInfo.avenueAxisDistance - 8
        if (distanceToMainAvenue <= 18) return "house-skip(near-avenue)"
        if (chunkInfo.radius <= 140) return "house-skip(inner-ring)"

        val lotHash = positiveHash(origin.x shr 4, origin.z shr 4)
        val styles = houseStylesFor(chunkInfo.style)
        val style = styles[lotHash % styles.size]
        val houseOffsetX = 1 + ((lotHash shr 2) and 1) + max(0, (12 - style.width) / 2)
        val houseOffsetZ = 1 + ((lotHash shr 3) and 1) + max(0, (12 - style.depth) / 2)
        val housePos = BlockPos(origin.x + houseOffsetX, cityBaseY, origin.z + houseOffsetZ)
        val hasPageLoot = lotHash % 30 == 0
        val palette = pickHousePalette(chunkInfo.style, palettes, lotHash)
        generateVillageStyleHouse(context.world, housePos, style, palette, lotHash, hasPageLoot)
        return "house(${style.family},stories=${style.stories},split=${style.splitLevel},loot=$hasPageLoot,tone=${chunkInfo.style})"
    }

    private fun generateVillageStyleHouse(
        world: StructureWorldAccess,
        housePos: BlockPos,
        style: HouseStyle,
        palette: HousePalette,
        lotHash: Int,
        hasPageLoot: Boolean
    ) {
        val width = style.width
        val depth = style.depth
        val storyStep = style.wallHeight + 2
        val wallHeight = style.wallHeight + (style.stories - 1) * storyStep + if (style.splitLevel) 2 else 0
        val family = style.family
        val foundation = when (family) {
            "desert" -> Blocks.SANDSTONE.defaultState
            "savanna" -> Blocks.TERRACOTTA.defaultState
            "snowy" -> Blocks.COBBLESTONE.defaultState
            "taiga" -> Blocks.COBBLESTONE.defaultState
            "generic" -> Blocks.STONE_BRICKS.defaultState
            else -> Blocks.COBBLESTONE.defaultState
        }
        val floorBlock = when (family) {
            "desert" -> Blocks.SMOOTH_SANDSTONE.defaultState
            "savanna" -> Blocks.ACACIA_PLANKS.defaultState
            "snowy" -> Blocks.SPRUCE_PLANKS.defaultState
            "taiga" -> Blocks.SPRUCE_PLANKS.defaultState
            "generic" -> Blocks.POLISHED_ANDESITE.defaultState
            else -> Blocks.OAK_PLANKS.defaultState
        }
        val wallBlock = when (family) {
            "desert" -> Blocks.CUT_SANDSTONE.defaultState
            "savanna" -> Blocks.ORANGE_TERRACOTTA.defaultState
            "snowy" -> Blocks.SPRUCE_PLANKS.defaultState
            "taiga" -> Blocks.SPRUCE_PLANKS.defaultState
            "generic" -> Blocks.LIGHT_GRAY_TERRACOTTA.defaultState
            else -> Blocks.OAK_PLANKS.defaultState
        }
        val cornerBlock = when (family) {
            "desert" -> Blocks.SMOOTH_SANDSTONE.defaultState
            "savanna" -> Blocks.STRIPPED_ACACIA_LOG.defaultState
            "snowy" -> Blocks.STRIPPED_SPRUCE_LOG.defaultState
            "taiga" -> Blocks.STRIPPED_SPRUCE_LOG.defaultState
            "generic" -> Blocks.STONE_BRICKS.defaultState
            else -> Blocks.STRIPPED_OAK_LOG.defaultState
        }
        val roofFill = when (family) {
            "desert" -> Blocks.SMOOTH_SANDSTONE.defaultState
            "savanna" -> Blocks.ACACIA_SLAB.defaultState
            "snowy" -> Blocks.SPRUCE_PLANKS.defaultState
            "taiga" -> Blocks.SPRUCE_PLANKS.defaultState
            "generic" -> Blocks.DEEPSLATE_TILES.defaultState
            else -> Blocks.OAK_PLANKS.defaultState
        }
        val roofStairs = when (family) {
            "desert" -> Blocks.SANDSTONE_STAIRS.defaultState
            "savanna" -> Blocks.ACACIA_STAIRS.defaultState
            "snowy" -> Blocks.SPRUCE_STAIRS.defaultState
            "taiga" -> Blocks.SPRUCE_STAIRS.defaultState
            "generic" -> Blocks.DEEPSLATE_TILE_STAIRS.defaultState
            else -> Blocks.OAK_STAIRS.defaultState
        }
        val windowBlock = when (family) {
            "snowy" -> Blocks.WHITE_STAINED_GLASS.defaultState
            "desert" -> Blocks.YELLOW_STAINED_GLASS.defaultState
            "generic" -> Blocks.LIGHT_GRAY_STAINED_GLASS.defaultState
            else -> Blocks.GLASS.defaultState
        }
        val fenceBlock = when (family) {
            "desert" -> Blocks.SANDSTONE_WALL.defaultState
            "savanna" -> Blocks.ACACIA_FENCE.defaultState
            "snowy" -> Blocks.SPRUCE_FENCE.defaultState
            "taiga" -> Blocks.SPRUCE_FENCE.defaultState
            "generic" -> Blocks.STONE_BRICK_WALL.defaultState
            else -> Blocks.OAK_FENCE.defaultState
        }
        val doorBlock = when (family) {
            "desert" -> Blocks.SPRUCE_DOOR
            "savanna" -> Blocks.ACACIA_DOOR
            "snowy" -> Blocks.SPRUCE_DOOR
            "taiga" -> Blocks.SPRUCE_DOOR
            "generic" -> Blocks.IRON_DOOR
            else -> Blocks.OAK_DOOR
        }

        val frontZ = housePos.z
        val doorLocalX = max(2, min(width - 3, width / 2 + if (lotHash % 3 == 0) -1 else 0))
        val doorX = housePos.x + doorLocalX
        val porchDepth = if (family == "savanna") 2 else 1
        val clearTopY = housePos.y + wallHeight + 8

        for (x in -1..width) {
            for (z in -2..depth + 1) {
                for (y in 1..(clearTopY - housePos.y)) {
                    world.setBlockState(BlockPos(housePos.x + x, housePos.y + y, housePos.z + z), Blocks.AIR.defaultState, 2)
                }
            }
        }

        for (x in 0 until width) {
            for (z in 0 until depth) {
                val worldX = housePos.x + x
                val worldZ = housePos.z + z
                world.setBlockState(BlockPos(worldX, housePos.y, worldZ), foundation, 2)
                world.setBlockState(BlockPos(worldX, housePos.y + 1, worldZ), floorBlock, 2)

                for (y in 2..(wallHeight + 1)) {
                    val pos = BlockPos(worldX, housePos.y + y, worldZ)
                    val localY = y - 1
                    val isOuterWall = x == 0 || z == 0 || x == width - 1 || z == depth - 1
                    val isCorner = (x == 0 || x == width - 1) && (z == 0 || z == depth - 1)
                    val isDoor = x == doorLocalX && z == 0 && localY in 1..2
                    val isFrontWindow = z == 0 && x in 1 until width - 1 && x !in (doorLocalX - 1)..(doorLocalX + 1) && localY == 2
                    val isBackWindow = z == depth - 1 && x in 2 until width - 2 && localY == 2
                    val isSideWindow = (x == 0 || x == width - 1) && z in 2 until depth - 2 && localY == 2
                    val isLowAccent = localY == 1 && family in setOf("plains", "taiga", "snowy")

                    when {
                        !isOuterWall -> world.setBlockState(pos, Blocks.AIR.defaultState, 2)
                        isDoor -> world.setBlockState(pos, Blocks.AIR.defaultState, 2)
                        isCorner -> world.setBlockState(pos, cornerBlock, 2)
                        isFrontWindow || isBackWindow || isSideWindow -> world.setBlockState(pos, windowBlock, 2)
                        isLowAccent -> world.setBlockState(pos, foundation, 2)
                        else -> world.setBlockState(pos, wallBlock, 2)
                    }
                }
            }
        }

        val interiorSplit = if (depth >= 8) housePos.z + depth / 2 else -1
        if (interiorSplit != -1) {
            for (x in 1 until width - 1) {
                for (y in 2..wallHeight) {
                    val pos = BlockPos(housePos.x + x, housePos.y + y, interiorSplit)
                    if (x == width / 2 || x == width / 2 - 1) {
                        world.setBlockState(pos, Blocks.AIR.defaultState, 2)
                    } else {
                        world.setBlockState(pos, wallBlock, 2)
                    }
                }
            }
        }

        for (story in 1 until style.stories) {
            val storyFloorY = housePos.y + 1 + story * storyStep
            for (x in 1 until width - 1) {
                for (z in 1 until depth - 1) {
                    val isStairHole = x in 1..4 && z == depth - 3
                    if (!isStairHole) {
                        world.setBlockState(BlockPos(housePos.x + x, storyFloorY, housePos.z + z), floorBlock, 2)
                    }
                }
            }
        }

        if (style.splitLevel) {
            val splitY = housePos.y + 1 + storyStep / 2
            for (x in 1 until width - 2) {
                for (z in 1 until max(2, depth / 2)) {
                    world.setBlockState(BlockPos(housePos.x + x, splitY, housePos.z + z), floorBlock, 2)
                }
            }
        }

        if (style.stories > 1) {
            for (step in 0..3) {
                world.setBlockState(
                    BlockPos(housePos.x + 1 + step, housePos.y + 2 + step, housePos.z + depth - 3),
                    Blocks.OAK_STAIRS.defaultState.with(Properties.HORIZONTAL_FACING, Direction.EAST),
                    2
                )
                world.setBlockState(BlockPos(housePos.x + 1 + step, housePos.y + 3 + step, housePos.z + depth - 3), Blocks.AIR.defaultState, 2)
            }
        } else if (style.splitLevel) {
            val ladderX = housePos.x + width - 2
            val ladderZ = housePos.z + depth / 2
            for (y in (housePos.y + 2)..(housePos.y + storyStep + 1)) {
                world.setBlockState(
                    BlockPos(ladderX, y, ladderZ),
                    Blocks.LADDER.defaultState.with(Properties.HORIZONTAL_FACING, Direction.WEST),
                    2
                )
            }
        }

        val workY = housePos.y + 2
        world.setBlockState(BlockPos(housePos.x + 2, workY, housePos.z + 2), Blocks.CRAFTING_TABLE.defaultState, 2)
        world.setBlockState(
            BlockPos(housePos.x + 3, workY, housePos.z + 2),
            Blocks.FURNACE.defaultState.with(Properties.HORIZONTAL_FACING, Direction.NORTH),
            2
        )
        world.setBlockState(
            BlockPos(housePos.x + 4, workY, housePos.z + 2),
            Blocks.SMOKER.defaultState.with(Properties.HORIZONTAL_FACING, Direction.NORTH),
            2
        )
        world.setBlockState(BlockPos(housePos.x + 2, workY, housePos.z + 3), Blocks.BARREL.defaultState, 2)

        val bedroomY = if (style.stories > 1) housePos.y + 2 + storyStep else housePos.y + 2
        val bedFoot = if (style.stories > 1) {
            BlockPos(housePos.x + width - 3, bedroomY, housePos.z + 2)
        } else {
            BlockPos(housePos.x + width - 3, bedroomY, housePos.z + depth - 3)
        }
        world.setBlockState(
            bedFoot,
            Blocks.RED_BED.defaultState.with(Properties.HORIZONTAL_FACING, Direction.WEST).with(Properties.BED_PART, BedPart.FOOT),
            2
        )
        world.setBlockState(
            bedFoot.west(),
            Blocks.RED_BED.defaultState.with(Properties.HORIZONTAL_FACING, Direction.WEST).with(Properties.BED_PART, BedPart.HEAD),
            2
        )
        world.setBlockState(BlockPos(housePos.x + width - 3, bedroomY, housePos.z + depth - 3), Blocks.BARREL.defaultState, 2)
        if (style.stories > 1) {
            world.setBlockState(BlockPos(housePos.x + width / 2, bedroomY + 1, housePos.z + depth / 2), Blocks.LANTERN.defaultState, 2)
        }
        if (hasPageLoot) {
            placePageChest(world, BlockPos(housePos.x + width - 2, bedroomY, housePos.z + 2), Direction.WEST, 1 + (lotHash % 2))
        }

        for (offset in -1..1) {
            world.setBlockState(BlockPos(doorX + offset, housePos.y + 1, frontZ - 1), floorBlock, 2)
        }
        if (porchDepth == 2) {
            for (offset in -1..1) {
                world.setBlockState(BlockPos(doorX + offset, housePos.y + 1, frontZ - 2), floorBlock, 2)
            }
        }

        world.setBlockState(BlockPos(doorX - 1, housePos.y + 2, frontZ - porchDepth), fenceBlock, 2)
        world.setBlockState(BlockPos(doorX + 1, housePos.y + 2, frontZ - porchDepth), fenceBlock, 2)
        world.setBlockState(BlockPos(doorX - 1, housePos.y + 3, frontZ - porchDepth), Blocks.LANTERN.defaultState, 2)
        world.setBlockState(BlockPos(doorX + 1, housePos.y + 3, frontZ - porchDepth), Blocks.LANTERN.defaultState, 2)
        placeDoor(world, BlockPos(doorX, housePos.y + 2, frontZ), Direction.SOUTH, doorBlock)

        val roofBaseY = housePos.y + wallHeight + 2
        when (family) {
            "savanna", "desert" -> {
                for (x in -1..width) {
                    for (z in -1..depth) {
                        val isParapet = x == -1 || z == -1 || x == width || z == depth
                        val block = if (isParapet) roofFill else floorBlock
                        world.setBlockState(BlockPos(housePos.x + x, roofBaseY, housePos.z + z), block, 2)
                    }
                }
                if (family == "savanna") {
                    for (x in 1 until width - 1) {
                        for (z in 1 until depth - 1) {
                            if ((x + z) % 5 == 0) {
                                world.setBlockState(BlockPos(housePos.x + x, roofBaseY + 1, housePos.z + z), fenceBlock, 2)
                            }
                        }
                    }
                }
            }

            else -> {
                val ridgeAlongX = width >= depth
                val roofLayers = if (ridgeAlongX) (depth / 2) + 1 else (width / 2) + 1
                for (step in 0 until roofLayers) {
                    val roofY = roofBaseY + step
                    if (ridgeAlongX) {
                        val northZ = housePos.z - 1 + step
                        val southZ = housePos.z + depth - step
                        for (x in -1..width) {
                            world.setBlockState(
                                BlockPos(housePos.x + x, roofY, northZ),
                                roofStairs.with(Properties.HORIZONTAL_FACING, Direction.SOUTH),
                                2
                            )
                            world.setBlockState(
                                BlockPos(housePos.x + x, roofY, southZ),
                                roofStairs.with(Properties.HORIZONTAL_FACING, Direction.NORTH),
                                2
                            )
                            for (fillZ in (northZ + 1) until southZ) {
                                world.setBlockState(BlockPos(housePos.x + x, roofY, fillZ), roofFill, 2)
                            }
                        }
                    } else {
                        val westX = housePos.x - 1 + step
                        val eastX = housePos.x + width - step
                        for (z in -1..depth) {
                            world.setBlockState(
                                BlockPos(westX, roofY, housePos.z + z),
                                roofStairs.with(Properties.HORIZONTAL_FACING, Direction.EAST),
                                2
                            )
                            world.setBlockState(
                                BlockPos(eastX, roofY, housePos.z + z),
                                roofStairs.with(Properties.HORIZONTAL_FACING, Direction.WEST),
                                2
                            )
                            for (fillX in (westX + 1) until eastX) {
                                world.setBlockState(BlockPos(fillX, roofY, housePos.z + z), roofFill, 2)
                            }
                        }
                    }
                }
            }
        }

        if (family != "savanna" && family != "desert") {
            val chimneyX = housePos.x + width - 2
            val chimneyZ = housePos.z + depth - 2
            for (y in 2..(wallHeight + 4)) {
                world.setBlockState(BlockPos(chimneyX, housePos.y + y, chimneyZ), foundation, 2)
            }
            world.setBlockState(BlockPos(chimneyX, housePos.y + wallHeight + 5, chimneyZ), Blocks.CAMPFIRE.defaultState, 2)
        }
    }

    private fun placeDoor(world: StructureWorldAccess, lowerPos: BlockPos, facing: Direction, block: net.minecraft.block.Block) {
        world.setBlockState(lowerPos, block.defaultState.with(Properties.HORIZONTAL_FACING, facing), 2)
        world.setBlockState(
            lowerPos.up(),
            block.defaultState.with(Properties.HORIZONTAL_FACING, facing).with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER),
            2
        )
    }

    private fun placePageChest(world: StructureWorldAccess, pos: BlockPos, facing: Direction, pages: Int) {
        world.setBlockState(pos, Blocks.CHEST.defaultState.with(Properties.HORIZONTAL_FACING, facing), 2)
        val chest = world.getBlockEntity(pos)
        if (chest is ChestBlockEntity) {
            repeat(FeatureBuildHelper.configuredLostPageCount(world.random, max(1, pages), max(1, pages))) {
                chest.setStack((it * 7) % chest.size(), ItemStack(ModItems.LOST_PAGE))
            }
        }
    }

    private fun generateApartmentBuildingChunk(context: FeatureContext<DefaultFeatureConfig>, cityBaseY: Int): String? {
        val origin = context.origin
        val world = context.world
        val modX = Math.floorMod(origin.x, 32)
        val modZ = Math.floorMod(origin.z, 32)
        if (modZ != 16 || (modX != 16 && modX != 0)) return null

        val ownerOriginX = if (modX == 16) origin.x else origin.x - 16
        val ownerInfo = districtInfo(ownerOriginX + 8, origin.z + 8)
        val distanceToMainAvenue = ownerInfo.avenueAxisDistance - 8
        if (!ownerInfo.isSuburb || distanceToMainAvenue <= 18 || ownerInfo.radius <= 140) return null

        val ownerHash = positiveHash(ownerOriginX shr 4, origin.z shr 4)
        if (ownerHash % 6 != 0) return null

        val baseX = ownerOriginX + 2
        val baseZ = origin.z + 2
        val floors = 4 + (ownerHash % 2)
        val topY = cityBaseY + floors * 4 + 5
        for (gX in baseX..(baseX + 27)) {
            if (gX !in origin.x..(origin.x + 15)) continue
            for (gZ in baseZ..(baseZ + 11)) {
                world.setBlockState(BlockPos(gX, cityBaseY, gZ), Blocks.STONE_BRICKS.defaultState, 2)
                for (y in cityBaseY + 1..topY) {
                    val localX = gX - baseX
                    val localZ = gZ - baseZ
                    val isOuter = localX == 0 || localX == 27 || localZ == 0 || localZ == 11
                    val isFloor = (y - cityBaseY) % 4 == 0
                    val block = when {
                        isFloor -> Blocks.POLISHED_ANDESITE.defaultState
                        isOuter && (y - cityBaseY) % 4 == 2 && localX !in 0..1 && localX !in 26..27 -> Blocks.LIGHT_GRAY_STAINED_GLASS.defaultState
                        isOuter -> Blocks.STONE_BRICKS.defaultState
                        else -> Blocks.AIR.defaultState
                    }
                    world.setBlockState(BlockPos(gX, y, gZ), block, 2)
                }
            }
        }

        for (floor in 0 until floors) {
            val floorY = cityBaseY + 1 + floor * 4
            for (gX in (baseX + 1)..(baseX + 26)) {
                if (gX !in origin.x..(origin.x + 15)) continue
                for (gZ in (baseZ + 5)..(baseZ + 6)) {
                    world.setBlockState(BlockPos(gX, floorY, gZ), Blocks.SMOOTH_STONE.defaultState, 2)
                    if ((gX - baseX) % 6 == 0) {
                        world.setBlockState(BlockPos(gX, floorY + 1, gZ), Blocks.SHROOMLIGHT.defaultState, 2)
                    }
                }
            }
            decorateApartmentUnit(world, origin, baseX + 2, baseZ + 1, floorY, ownerHash % 25 == 0 && floor == floors - 1)
            decorateApartmentUnit(world, origin, baseX + 15, baseZ + 1, floorY, false)
            decorateApartmentUnit(world, origin, baseX + 2, baseZ + 7, floorY, false)
            decorateApartmentUnit(world, origin, baseX + 15, baseZ + 7, floorY, false)
        }

        for (floor in 0 until floors - 1) {
            val floorY = cityBaseY + 2 + floor * 4
            for (step in 0..3) {
                val stairX = baseX + 1 + step
                if (stairX !in origin.x..(origin.x + 15)) continue
                world.setBlockState(
                    BlockPos(stairX, floorY + step, baseZ + 10),
                    Blocks.STONE_BRICK_STAIRS.defaultState.with(Properties.HORIZONTAL_FACING, Direction.EAST),
                    2
                )
                world.setBlockState(BlockPos(stairX, floorY + step + 1, baseZ + 10), Blocks.AIR.defaultState, 2)
            }
        }

        if (origin.x == ownerOriginX) {
            for (x in (baseX + 4)..(baseX + 8)) {
                if (x in origin.x..(origin.x + 15)) {
                    world.setBlockState(BlockPos(x, cityBaseY + 1, baseZ - 1), Blocks.SMOOTH_STONE.defaultState, 2)
                    world.setBlockState(BlockPos(x, cityBaseY, baseZ - 2), Blocks.STONE_BRICK_STAIRS.defaultState.with(Properties.HORIZONTAL_FACING, Direction.SOUTH), 2)
                }
            }
            for (x in (baseX + 18)..(baseX + 22)) {
                if (x in origin.x..(origin.x + 15)) {
                    world.setBlockState(BlockPos(x, cityBaseY + 1, baseZ - 1), Blocks.SMOOTH_STONE.defaultState, 2)
                    world.setBlockState(BlockPos(x, cityBaseY, baseZ - 2), Blocks.STONE_BRICK_STAIRS.defaultState.with(Properties.HORIZONTAL_FACING, Direction.SOUTH), 2)
                }
            }
            placeDoor(world, BlockPos(baseX + 6, cityBaseY + 2, baseZ), Direction.SOUTH, Blocks.SPRUCE_DOOR)
            placeDoor(world, BlockPos(baseX + 20, cityBaseY + 2, baseZ), Direction.SOUTH, Blocks.SPRUCE_DOOR)
        }
        return if (origin.x == ownerOriginX) "apartment(owner,floors=$floors)" else "apartment(wing,floors=$floors)"
    }

    private fun decorateApartmentUnit(world: StructureWorldAccess, origin: BlockPos, startX: Int, startZ: Int, floorY: Int, withLoot: Boolean) {
        if (startX !in origin.x..(origin.x + 15) && startX + 9 !in origin.x..(origin.x + 15)) return
        for (gX in startX..(startX + 9)) {
            if (gX !in origin.x..(origin.x + 15)) continue
            for (gZ in startZ..(startZ + 3)) {
                world.setBlockState(BlockPos(gX, floorY, gZ), Blocks.BIRCH_PLANKS.defaultState, 2)
            }
        }
        for (gX in (startX + 4)..(startX + 4)) {
            if (gX !in origin.x..(origin.x + 15)) continue
            for (gZ in startZ..(startZ + 3)) {
                if (gZ == startZ + 1) continue
                world.setBlockState(BlockPos(gX, floorY + 1, gZ), Blocks.SPRUCE_PLANKS.defaultState, 2)
                world.setBlockState(BlockPos(gX, floorY + 2, gZ), Blocks.SPRUCE_PLANKS.defaultState, 2)
            }
        }
        if (startX + 1 in origin.x..(origin.x + 15)) {
            world.setBlockState(BlockPos(startX + 1, floorY + 1, startZ + 2), Blocks.CRAFTING_TABLE.defaultState, 2)
            world.setBlockState(BlockPos(startX + 2, floorY + 1, startZ + 2), Blocks.FURNACE.defaultState.with(Properties.HORIZONTAL_FACING, Direction.NORTH), 2)
            world.setBlockState(BlockPos(startX + 2, floorY + 1, startZ + 1), Blocks.BARREL.defaultState, 2)
        }
        if (startX + 7 in origin.x..(origin.x + 15)) {
            val bedFoot = BlockPos(startX + 7, floorY + 1, startZ + 2)
            world.setBlockState(bedFoot, Blocks.BLUE_BED.defaultState.with(Properties.HORIZONTAL_FACING, Direction.WEST).with(Properties.BED_PART, BedPart.FOOT), 2)
            world.setBlockState(bedFoot.west(), Blocks.BLUE_BED.defaultState.with(Properties.HORIZONTAL_FACING, Direction.WEST).with(Properties.BED_PART, BedPart.HEAD), 2)
            world.setBlockState(BlockPos(startX + 6, floorY + 1, startZ + 1), Blocks.BOOKSHELF.defaultState, 2)
            world.setBlockState(BlockPos(startX + 8, floorY + 1, startZ + 1), Blocks.BARREL.defaultState, 2)
            if (withLoot) {
                placePageChest(world, BlockPos(startX + 8, floorY + 1, startZ + 2), Direction.WEST, 2)
            }
        }
        if (startX + 4 in origin.x..(origin.x + 15)) {
            placeDoor(world, BlockPos(startX + 4, floorY + 1, startZ + 1), Direction.EAST, Blocks.SPRUCE_DOOR)
        }
    }

    private fun decorateSkyscraperInterior(
        world: StructureWorldAccess,
        origin: BlockPos,
        cityBaseY: Int,
        height: Int,
        frontFace: Direction,
        palette: SkyscraperPalette,
        chunkHash: Int,
        hasPageLoot: Boolean
    ) {
        val upX = origin.x + 5
        val upZ = origin.z + 12
        val downX = origin.x + 11
        val downZ = origin.z + 12
        val topY = height - 1

        for (y in cityBaseY + 1..topY) {
            for (shaftX in listOf(upX, downX)) {
                val shaftZ = upZ
                world.setBlockState(BlockPos(shaftX - 1, y, shaftZ), palette.wall, 2)
                world.setBlockState(BlockPos(shaftX + 1, y, shaftZ), palette.wall, 2)
                world.setBlockState(BlockPos(shaftX, y, shaftZ + 1), palette.wall, 2)
                world.setBlockState(BlockPos(shaftX, y, shaftZ - 1), palette.wall, 2)
                world.setBlockState(BlockPos(shaftX, y, shaftZ - 2), Blocks.AIR.defaultState, 2)
            }
        }

        for (y in cityBaseY + 1..topY) {
            world.setBlockState(BlockPos(upX, y, upZ), Blocks.AIR.defaultState, 2)
            world.setBlockState(BlockPos(downX, y, downZ), Blocks.AIR.defaultState, 2)
            world.setBlockState(BlockPos(upX, y, upZ), Blocks.WATER.defaultState, 2)
            world.setBlockState(BlockPos(downX, y, downZ), Blocks.WATER.defaultState, 2)
        }
        world.setBlockState(BlockPos(upX, cityBaseY + 1, upZ), Blocks.SOUL_SAND.defaultState, 2)
        world.setBlockState(BlockPos(downX, cityBaseY + 1, downZ), Blocks.MAGMA_BLOCK.defaultState, 2)

        val doorPos = when (frontFace) {
            Direction.NORTH -> BlockPos(origin.x + 9, cityBaseY + 1, origin.z + 4)
            Direction.SOUTH -> BlockPos(origin.x + 9, cityBaseY + 1, origin.z + 14)
            Direction.EAST -> BlockPos(origin.x + 14, cityBaseY + 1, origin.z + 9)
            Direction.WEST -> BlockPos(origin.x + 4, cityBaseY + 1, origin.z + 9)
            else -> BlockPos(origin.x + 9, cityBaseY + 1, origin.z + 4)
        }
        placeDoor(world, doorPos, frontFace, Blocks.IRON_DOOR)

        val floorCount = max(1, (height - cityBaseY) / 4)
        for (floor in 0 until floorCount) {
            val floorY = cityBaseY + 1 + floor * 4
            placeDoor(world, BlockPos(upX, floorY + 1, upZ - 1), Direction.NORTH, Blocks.SPRUCE_DOOR)
            placeDoor(world, BlockPos(downX, floorY + 1, downZ - 1), Direction.NORTH, Blocks.SPRUCE_DOOR)
            world.setBlockState(BlockPos(upX, floorY + 1, upZ - 2), Blocks.SMOOTH_STONE.defaultState, 2)
            world.setBlockState(BlockPos(downX, floorY + 1, downZ - 2), Blocks.SMOOTH_STONE.defaultState, 2)
            world.setBlockState(BlockPos(upX - 1, floorY + 1, upZ - 2), Blocks.STONE_BRICK_WALL.defaultState, 2)
            world.setBlockState(BlockPos(upX + 1, floorY + 1, upZ - 2), Blocks.STONE_BRICK_WALL.defaultState, 2)
            world.setBlockState(BlockPos(downX - 1, floorY + 1, downZ - 2), Blocks.STONE_BRICK_WALL.defaultState, 2)
            world.setBlockState(BlockPos(downX + 1, floorY + 1, downZ - 2), Blocks.STONE_BRICK_WALL.defaultState, 2)

            if (floor == 0) {
                val hospitality = chunkHash % 3
                for (x in 6..12) {
                    world.setBlockState(BlockPos(origin.x + x, floorY, origin.z + 6), Blocks.SMOOTH_STONE.defaultState, 2)
                }
                if (hospitality == 0) {
                    world.setBlockState(BlockPos(origin.x + 6, floorY + 1, origin.z + 6), Blocks.BARREL.defaultState, 2)
                    world.setBlockState(BlockPos(origin.x + 7, floorY + 1, origin.z + 6), Blocks.SMOKER.defaultState.with(Properties.HORIZONTAL_FACING, Direction.SOUTH), 2)
                    world.setBlockState(BlockPos(origin.x + 10, floorY + 1, origin.z + 11), Blocks.SPRUCE_TRAPDOOR.defaultState, 2)
                } else if (hospitality == 1) {
                    world.setBlockState(BlockPos(origin.x + 6, floorY + 1, origin.z + 6), Blocks.BARREL.defaultState, 2)
                    world.setBlockState(BlockPos(origin.x + 7, floorY + 1, origin.z + 6), Blocks.BARREL.defaultState, 2)
                    world.setBlockState(BlockPos(origin.x + 8, floorY + 1, origin.z + 6), Blocks.BARREL.defaultState, 2)
                } else {
                    world.setBlockState(BlockPos(origin.x + 6, floorY + 1, origin.z + 6), Blocks.CRAFTING_TABLE.defaultState, 2)
                    world.setBlockState(BlockPos(origin.x + 7, floorY + 1, origin.z + 6), Blocks.FURNACE.defaultState.with(Properties.HORIZONTAL_FACING, Direction.SOUTH), 2)
                }
            } else {
                for (x in 6..12 step 2) {
                    world.setBlockState(BlockPos(origin.x + x, floorY + 1, origin.z + 6), Blocks.SMOOTH_STONE_SLAB.defaultState, 2)
                    world.setBlockState(BlockPos(origin.x + x, floorY + 1, origin.z + 11), Blocks.SMOOTH_STONE_SLAB.defaultState, 2)
                    if (origin.x + x !in (upX - 1)..(upX + 1) && origin.x + x !in (downX - 1)..(downX + 1)) {
                        world.setBlockState(BlockPos(origin.x + x, floorY + 1, origin.z + 7), Blocks.CARTOGRAPHY_TABLE.defaultState, 2)
                    }
                    world.setBlockState(BlockPos(origin.x + x, floorY + 1, origin.z + 10), Blocks.BARREL.defaultState, 2)
                }
                if (hasPageLoot && floor % 3 == 0) {
                    placePageChest(world, BlockPos(origin.x + 12, floorY + 1, origin.z + 12), Direction.WEST, 1 + (chunkHash % 2))
                }
            }
        }
    }

    private fun districtInfo(gX: Int, gZ: Int): DistrictInfo {
        val chunkX = Math.floorDiv(gX, 16)
        val chunkZ = Math.floorDiv(gZ, 16)
        val sampleX = (chunkX shl 4) + 8
        val sampleZ = (chunkZ shl 4) + 8
        val cellX = Math.floorDiv(sampleX, CITY_CELL_SIZE)
        val cellZ = Math.floorDiv(sampleZ, CITY_CELL_SIZE)

        var dominantAnchor: CityAnchor? = null
        var dominantScore = Double.NEGATIVE_INFINITY
        var secondScore = Double.NEGATIVE_INFINITY

        for (gridX in (cellX - 1)..(cellX + 1)) {
            for (gridZ in (cellZ - 1)..(cellZ + 1)) {
                val anchor = cityAnchor(gridX, gridZ)
                val distance = hypot((sampleX - anchor.centerX).toDouble(), (sampleZ - anchor.centerZ).toDouble())
                val score = 1.0 - (distance / anchor.radius.toDouble())
                if (score > dominantScore) {
                    secondScore = dominantScore
                    dominantScore = score
                    dominantAnchor = anchor
                } else if (score > secondScore) {
                    secondScore = score
                }
            }
        }

        val anchor = dominantAnchor ?: cityAnchor(cellX, cellZ)
        val centerX = gX - anchor.centerX
        val centerZ = gZ - anchor.centerZ
        val distX = abs(centerX)
        val distZ = abs(centerZ)
        val pointDistance = hypot((gX - anchor.centerX).toDouble(), (gZ - anchor.centerZ).toDouble())
        val radius = pointDistance.roundToInt()

        val avenueWarpX = (sin((gZ + anchor.streetPhaseZ).toDouble() / anchor.avenuePitchX) * anchor.avenueWaveX).roundToInt()
        val avenueWarpZ = (cos((gX - anchor.streetPhaseX).toDouble() / anchor.avenuePitchZ) * anchor.avenueWaveZ).roundToInt()
        val avenueAxisDistance = min(abs(centerX + avenueWarpX), abs(centerZ + avenueWarpZ))
        val corridorInfo = corridorInfo(gX, gZ, anchor)
        val cityInfluence = clamp01(((anchor.radius * 0.88) - pointDistance) / (anchor.radius * 0.20))
        val connectorInfluence = corridorInfo.influence
        val overlapBoost = clamp01((secondScore + 0.12) / 0.42)

        val isCore = pointDistance <= anchor.radius * 0.28 || (cityInfluence > 0.78 && overlapBoost > 0.20)
        val isSuburb = !isCore && pointDistance <= anchor.radius * 0.56 && connectorInfluence < 0.05
        val isCity = cityInfluence > 0.28
        val isHighway = connectorInfluence > 0.72 && cityInfluence < 0.08
        val isMainAvenue = isCity && !isHighway && (avenueAxisDistance <= 6 || connectorInfluence > 0.46)
        val subwayAxisX = abs(centerZ + (avenueWarpZ / 2))
        val subwayAxisZ = abs(centerX + (avenueWarpX / 2))
        val isSubwayX = isCity && anchor.radius > 300 && subwayAxisX < 3
        val isSubwayZ = isCity && anchor.radius > 300 && subwayAxisZ < 3

        val regionX = Math.floorMod(centerX + anchor.streetPhaseX, 128)
        val regionZ = Math.floorMod(centerZ + anchor.streetPhaseZ, 128)
        val parkHash = positiveHash((gX shr 5) + anchor.gridX * 19, (gZ shr 5) + anchor.gridZ * 23)
        val isPark = isSuburb && !isMainAvenue && connectorInfluence < 0.05 && avenueAxisDistance > 10 &&
            parkHash % (if (anchor.tone == CityTone.GARDEN) 4 else 7) == 0
        val highwayDistX = if (corridorInfo.eastWest) corridorInfo.alongDistance else corridorInfo.axisDistance
        val highwayDistZ = if (corridorInfo.eastWest) corridorInfo.axisDistance else corridorInfo.alongDistance
        val minHighwayDist = if (corridorInfo.influence > 0.12) corridorInfo.axisDistance else avenueAxisDistance

        return DistrictInfo(
            gridX = anchor.gridX,
            gridZ = anchor.gridZ,
            regionX = regionX,
            regionZ = regionZ,
            centerX = centerX,
            centerZ = centerZ,
            distX = distX,
            distZ = distZ,
            radius = radius,
            highwayDistX = highwayDistX,
            highwayDistZ = highwayDistZ,
            minHighwayDist = minHighwayDist,
            highwayDeckY = corridorInfo.deckY,
            cityInfluence = cityInfluence,
            connectorInfluence = connectorInfluence,
            style = anchor.tone,
            avenueAxisDistance = avenueAxisDistance,
            isCore = isCore,
            isSuburb = isSuburb,
            isCity = isCity,
            isHighway = isHighway,
            isMainAvenue = isMainAvenue,
            isPark = isPark,
            isSubwayX = isSubwayX,
            isSubwayZ = isSubwayZ
        )
    }

    private fun cityAnchor(gridX: Int, gridZ: Int): CityAnchor {
        val tones = CityTone.values()
        return CityAnchor(
            gridX = gridX,
            gridZ = gridZ,
            centerX = gridX * CITY_CELL_SIZE + (CITY_CELL_SIZE / 2) + rangedHash(gridX, gridZ, 17L, -CITY_CENTER_JITTER, CITY_CENTER_JITTER),
            centerZ = gridZ * CITY_CELL_SIZE + (CITY_CELL_SIZE / 2) + rangedHash(gridX, gridZ, 29L, -CITY_CENTER_JITTER, CITY_CENTER_JITTER),
            radius = CITY_MIN_RADIUS + rangedHash(gridX, gridZ, 43L, 0, CITY_RADIUS_VARIANCE),
            groundY = CITY_GROUND_MIN_Y + CITY_GROUND_STEP * rangedHash(gridX, gridZ, 47L, 0, (CITY_GROUND_MAX_Y - CITY_GROUND_MIN_Y) / CITY_GROUND_STEP),
            tone = tones[rangedHash(gridX, gridZ, 61L, 0, tones.size - 1)],
            avenuePitchX = 68.0 + rangedHash(gridX, gridZ, 73L, 0, 28),
            avenuePitchZ = 68.0 + rangedHash(gridX, gridZ, 89L, 0, 28),
            avenueWaveX = 2.0 + rangedHash(gridX, gridZ, 101L, 0, 5),
            avenueWaveZ = 2.0 + rangedHash(gridX, gridZ, 113L, 0, 5),
            streetPhaseX = rangedHash(gridX, gridZ, 127L, 0, 127),
            streetPhaseZ = rangedHash(gridX, gridZ, 149L, 0, 127)
        )
    }

    private fun corridorInfo(gX: Int, gZ: Int, anchor: CityAnchor): CorridorInfo {
        var bestInfluence = 0.0
        var bestAxisDistance = Int.MAX_VALUE
        var bestAlongDistance = Int.MAX_VALUE
        var bestEastWest = true
        var bestDeckY = anchor.groundY + 10

        val neighbors = listOf(
            anchor.gridX - 1 to anchor.gridZ,
            anchor.gridX + 1 to anchor.gridZ,
            anchor.gridX to anchor.gridZ - 1,
            anchor.gridX to anchor.gridZ + 1
        )

        for ((neighborX, neighborZ) in neighbors) {
            val neighbor = cityAnchor(neighborX, neighborZ)
            val dx = neighbor.centerX - anchor.centerX
            val dz = neighbor.centerZ - anchor.centerZ
            val lengthSquared = dx.toDouble() * dx.toDouble() + dz.toDouble() * dz.toDouble()
            if (lengthSquared <= 0.0) continue

            val localX = gX - anchor.centerX
            val localZ = gZ - anchor.centerZ
            val t = clamp01((localX * dx + localZ * dz) / lengthSquared)
            val corridorLength = kotlin.math.sqrt(lengthSquared)
            val alongDistance = t * corridorLength
            val sourceClearance = anchor.radius + HIGHWAY_CLEARANCE_FROM_CITY
            val targetClearance = neighbor.radius + HIGHWAY_CLEARANCE_FROM_CITY
            if (alongDistance <= sourceClearance || alongDistance >= corridorLength - targetClearance) {
                continue
            }
            val closestX = anchor.centerX + dx * t
            val closestZ = anchor.centerZ + dz * t
            val lateralDistance = hypot(gX - closestX, gZ - closestZ)
            val eastWest = abs(dx) >= abs(dz)
            val width = if (eastWest) 12.0 else 10.0
            val influence = clamp01((width - lateralDistance) / width)
            val deckY = ((anchor.groundY + 10) + ((neighbor.groundY + 10) - (anchor.groundY + 10)) * t).roundToInt()

            if (influence > bestInfluence) {
                bestInfluence = influence
                bestAxisDistance = lateralDistance.roundToInt()
                bestAlongDistance = alongDistance.roundToInt()
                bestEastWest = eastWest
                bestDeckY = deckY
            }
        }

        return CorridorInfo(
            influence = bestInfluence,
            axisDistance = if (bestAxisDistance == Int.MAX_VALUE) 999 else bestAxisDistance,
            alongDistance = if (bestAlongDistance == Int.MAX_VALUE) 999 else bestAlongDistance,
            eastWest = bestEastWest,
            deckY = bestDeckY
        )
    }

    private fun streetPaletteFor(style: CityTone): StreetPalette = when (style) {
        CityTone.CIVIC -> StreetPalette(
            avenue = Blocks.GRAY_CONCRETE_POWDER.defaultState,
            avenueStripe = Blocks.YELLOW_CONCRETE.defaultState,
            support = Blocks.STONE.defaultState,
            lamp = Blocks.SHROOMLIGHT.defaultState,
            corePaving = Blocks.SMOOTH_STONE.defaultState,
            suburbStreet = Blocks.POLISHED_ANDESITE.defaultState,
            suburbLot = Blocks.GRASS_BLOCK.defaultState,
            parkGround = Blocks.MOSS_BLOCK.defaultState,
            tunnelWall = Blocks.STONE_BRICKS.defaultState
        )
        CityTone.INDUSTRIAL -> StreetPalette(
            avenue = Blocks.DEEPSLATE_TILES.defaultState,
            avenueStripe = Blocks.YELLOW_TERRACOTTA.defaultState,
            support = Blocks.COBBLED_DEEPSLATE.defaultState,
            lamp = Blocks.SEA_LANTERN.defaultState,
            corePaving = Blocks.POLISHED_DEEPSLATE.defaultState,
            suburbStreet = Blocks.DEEPSLATE_BRICKS.defaultState,
            suburbLot = Blocks.COARSE_DIRT.defaultState,
            parkGround = Blocks.MOSS_BLOCK.defaultState,
            tunnelWall = Blocks.DEEPSLATE_BRICKS.defaultState
        )
        CityTone.BRICKWORK -> StreetPalette(
            avenue = Blocks.TERRACOTTA.defaultState,
            avenueStripe = Blocks.SMOOTH_STONE.defaultState,
            support = Blocks.BRICKS.defaultState,
            lamp = Blocks.SHROOMLIGHT.defaultState,
            corePaving = Blocks.MUD_BRICKS.defaultState,
            suburbStreet = Blocks.PACKED_MUD.defaultState,
            suburbLot = Blocks.GRASS_BLOCK.defaultState,
            parkGround = Blocks.ROOTED_DIRT.defaultState,
            tunnelWall = Blocks.BRICKS.defaultState
        )
        CityTone.GARDEN -> StreetPalette(
            avenue = Blocks.MOSSY_STONE_BRICKS.defaultState,
            avenueStripe = Blocks.SMOOTH_STONE.defaultState,
            support = Blocks.STONE.defaultState,
            lamp = Blocks.PEARLESCENT_FROGLIGHT.defaultState,
            corePaving = Blocks.CALCITE.defaultState,
            suburbStreet = Blocks.COBBLESTONE.defaultState,
            suburbLot = Blocks.GRASS_BLOCK.defaultState,
            parkGround = Blocks.MOSS_BLOCK.defaultState,
            tunnelWall = Blocks.MOSSY_STONE_BRICKS.defaultState
        )
        CityTone.MARINA -> StreetPalette(
            avenue = Blocks.CYAN_TERRACOTTA.defaultState,
            avenueStripe = Blocks.WHITE_CONCRETE.defaultState,
            support = Blocks.SMOOTH_STONE.defaultState,
            lamp = Blocks.SEA_LANTERN.defaultState,
            corePaving = Blocks.SMOOTH_QUARTZ.defaultState,
            suburbStreet = Blocks.PRISMARINE_BRICKS.defaultState,
            suburbLot = Blocks.GRASS_BLOCK.defaultState,
            parkGround = Blocks.MOSS_BLOCK.defaultState,
            tunnelWall = Blocks.PRISMARINE_BRICKS.defaultState
        )
    }

    private fun pickSkyscraperPalette(style: CityTone, palettes: List<SkyscraperPalette>, chunkHash: Int): SkyscraperPalette {
        val candidates = when (style) {
            CityTone.CIVIC -> listOf(1, 2, 4)
            CityTone.INDUSTRIAL -> listOf(0, 3, 4)
            CityTone.BRICKWORK -> listOf(3, 0, 1)
            CityTone.GARDEN -> listOf(4, 1, 2)
            CityTone.MARINA -> listOf(2, 4, 0)
        }
        return palettes[candidates[chunkHash % candidates.size] % palettes.size]
    }

    private fun pickHousePalette(style: CityTone, palettes: List<HousePalette>, lotHash: Int): HousePalette {
        val candidates = when (style) {
            CityTone.CIVIC -> listOf(0, 3, 4)
            CityTone.INDUSTRIAL -> listOf(1, 2, 4)
            CityTone.BRICKWORK -> listOf(2, 0, 1)
            CityTone.GARDEN -> listOf(0, 2, 3)
            CityTone.MARINA -> listOf(3, 4, 0)
        }
        return palettes[candidates[lotHash % candidates.size] % palettes.size]
    }

    private fun houseStylesFor(style: CityTone): List<HouseStyle> = when (style) {
        CityTone.CIVIC -> listOf(
            HouseStyle("plains", 8, 8, 4),
            HouseStyle("plains", 10, 7, 4),
            HouseStyle("generic", 9, 8, 4),
            HouseStyle("generic", 10, 9, 4, stories = 2)
        )
        CityTone.INDUSTRIAL -> listOf(
            HouseStyle("taiga", 8, 10, 4),
            HouseStyle("generic", 10, 9, 4, stories = 2),
            HouseStyle("generic", 8, 10, 4, splitLevel = true),
            HouseStyle("taiga", 10, 8, 4, splitLevel = true)
        )
        CityTone.BRICKWORK -> listOf(
            HouseStyle("plains", 9, 9, 4, stories = 2),
            HouseStyle("taiga", 9, 9, 4, stories = 2),
            HouseStyle("generic", 9, 8, 4),
            HouseStyle("generic", 10, 9, 4, stories = 2)
        )
        CityTone.GARDEN -> listOf(
            HouseStyle("plains", 8, 8, 4),
            HouseStyle("plains", 10, 8, 4, stories = 2, splitLevel = true),
            HouseStyle("snowy", 8, 8, 5),
            HouseStyle("snowy", 9, 8, 4, stories = 2)
        )
        CityTone.MARINA -> listOf(
            HouseStyle("savanna", 9, 7, 4, flatRoof = true),
            HouseStyle("savanna", 10, 8, 4, stories = 2, flatRoof = true),
            HouseStyle("desert", 10, 8, 4, flatRoof = true),
            HouseStyle("desert", 9, 9, 4, stories = 2, flatRoof = true)
        )
    }

    private fun clamp01(value: Double): Double = when {
        value < 0.0 -> 0.0
        value > 1.0 -> 1.0
        else -> value
    }

    private fun computeRoadY(cityBaseY: Int, blendedY: Int, isCity: Boolean): Int {
        if (isCity) return cityBaseY
        val delta = blendedY - cityBaseY
        val compressedDelta = if (delta >= 0) {
            delta / 2
        } else {
            -((-delta) / 2)
        }
        return cityBaseY + compressedDelta
    }

    private fun positiveHash(x: Int, z: Int): Int {
        return (x * 73428767 xor z * 912931).let { if (it == Int.MIN_VALUE) 0 else abs(it) }
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

    private fun shouldDecay(x: Int, y: Int, z: Int): Boolean {
        return false
    }

    private fun pickSkyscraperFront(info: DistrictInfo, chunkHash: Int): Direction {
        val touchesMainRoad = info.avenueAxisDistance <= 24
        if (touchesMainRoad) {
            return if (abs(info.centerX) <= abs(info.centerZ)) {
                if (info.centerX >= 0) Direction.WEST else Direction.EAST
            } else {
                if (info.centerZ >= 0) Direction.NORTH else Direction.SOUTH
            }
        }

        return when (chunkHash % 4) {
            0 -> Direction.NORTH
            1 -> Direction.SOUTH
            2 -> Direction.EAST
            else -> Direction.WEST
        }
    }

    private fun intersectsStation(origin: BlockPos): Boolean {
        val centerX = origin.x + 8
        val centerZ = origin.z + 8
        val info = districtInfo(centerX, centerZ)
        return info.radius <= 40
    }

    private fun isEntrySurfaceProtected(centerX: Int, centerZ: Int): Boolean {
        return centerX in -52..-6 && centerZ in -30..-8
    }
}
