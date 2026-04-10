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
import kotlin.math.max
import kotlin.math.min

class CityGridFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {

    companion object {
        // Flip this off once we know which generation path is misbehaving.
        private const val DEBUG_CITY_GEN = true
        private const val SLOW_CHUNK_WARN_MS = 50L
    }

    private data class DistrictInfo(
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
        val isCore: Boolean,
        val isSuburb: Boolean,
        val isCity: Boolean,
        val isHighway: Boolean,
        val isMainAvenue: Boolean,
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

    override fun generate(context: FeatureContext<DefaultFeatureConfig>): Boolean {
        val world = context.world
        val origin = context.origin
        val serverWorld = world.toServerWorld()
        val startedAt = System.nanoTime()

        if (serverWorld.registryKey.value.namespace != MystcraftReforged.MOD_ID) return false
        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, serverWorld.registryKey.value)
        if (profile.terrainType != TerrainType.CITIES) return false

        val chunkX = origin.x shr 4
        val chunkZ = origin.z shr 4
        val debugNotes = mutableListOf<String>()
        val cityBaseY = 64
        val stationRadius = 28
        val stationFloorY = 29
        val stationRailY = 30
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

        for (x in 0..15) {
            for (z in 0..15) {
                val gX = origin.x + x
                val gZ = origin.z + z
                val info = districtInfo(gX, gZ)
                val natY = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, gX, gZ)
                val isWaterSurface = world.getBlockState(BlockPos(gX, 61, gZ)).isOf(Blocks.WATER) || natY < 62

                val cityInfluence = clamp01((288.0 - info.radius) / 32.0)
                val smoothCityInfluence = cityInfluence * cityInfluence * (3.0 - 2.0 * cityInfluence)
                val blendedY = natY + ((cityBaseY - natY) * smoothCityInfluence).toInt()
                val roadDistance = if (info.isCity) min(info.distX, info.distZ) else info.minHighwayDist
                val roadY = computeRoadY(cityBaseY, blendedY, info.isCity)
                val roadShoulderInfluence = if (info.isHighway || info.isMainAvenue || info.isCity) {
                    clamp01((10.0 - roadDistance) / 6.0)
                } else {
                    0.0
                }
                val terrainInfluence = max(cityInfluence, roadShoulderInfluence)
                val targetTerrainY = if (roadShoulderInfluence > cityInfluence) {
                    natY + ((roadY - natY) * roadShoulderInfluence).toInt()
                } else {
                    blendedY
                }
                val isBridge = info.isHighway && isWaterSurface && cityInfluence == 0.0

                if (terrainInfluence > 0.0 && !isBridge) {
                    if (targetTerrainY < 319) {
                        for (y in (targetTerrainY + 1)..min(319, max(natY, targetTerrainY) + 15)) {
                            world.setBlockState(BlockPos(gX, y, gZ), Blocks.AIR.defaultState, 2)
                        }
                    }
                    for (y in max(min(natY, targetTerrainY) - 5, 50)..targetTerrainY) {
                        val pos = BlockPos(gX, y, gZ)
                        if (!world.getBlockState(pos).isOpaqueFullCube(world, pos)) {
                            world.setBlockState(pos, Blocks.DIRT.defaultState, 2)
                        }
                    }
                    if (smoothCityInfluence < 0.99 && !info.isMainAvenue && !info.isHighway) {
                        world.setBlockState(BlockPos(gX, targetTerrainY, gZ), Blocks.GRASS_BLOCK.defaultState, 2)
                    }
                }

                if ((info.isSubwayX || info.isSubwayZ) && info.radius > stationRadius) {
                    generateTunnelColumn(context, gX, gZ, info, stationRailY, isWaterSurface)
                    if (x == 8 && z == 8 && DEBUG_CITY_GEN) {
                        debugNotes.add("subway-column(y=${if (info.isSubwayZ && !info.isSubwayX) stationRailY - (max(0, 24 - info.distX) / 3) else stationRailY})")
                    }
                }

                if (terrainInfluence == 0.0 && !isBridge && !info.isHighway) continue

                if (info.isHighway || info.isMainAvenue) {
                    generateRoadColumn(context, gX, gZ, roadY, info, isBridge)
                    if (x == 8 && z == 8 && DEBUG_CITY_GEN) {
                        debugNotes.add(if (isBridge) "bridge-road(y=$roadY)" else "road(y=$roadY)")
                    }
                    continue
                }

                val coreStreet = Math.floorMod(gX, 16) < 4 || Math.floorMod(gZ, 16) < 4
                val suburbStreet = Math.floorMod(info.regionX, 32) < 6 || Math.floorMod(info.regionZ, 32) < 6

                if (info.isCore && info.radius > stationRadius) {
                    if (coreStreet) {
                        world.setBlockState(BlockPos(gX, cityBaseY - 1, gZ), Blocks.STONE.defaultState, 2)
                        world.setBlockState(BlockPos(gX, cityBaseY, gZ), Blocks.GRAY_CONCRETE_POWDER.defaultState, 2)
                    } else {
                        world.setBlockState(BlockPos(gX, cityBaseY, gZ), Blocks.SMOOTH_STONE.defaultState, 2)
                    }
                } else if (info.isSuburb) {
                    if (suburbStreet) {
                        world.setBlockState(BlockPos(gX, cityBaseY - 1, gZ), Blocks.GRAVEL.defaultState, 2)
                        world.setBlockState(BlockPos(gX, cityBaseY, gZ), Blocks.COBBLESTONE.defaultState, 2)
                    } else {
                        for (y in (cityBaseY + 1)..319) {
                            world.setBlockState(BlockPos(gX, y, gZ), Blocks.AIR.defaultState, 2)
                        }
                        world.setBlockState(BlockPos(gX, cityBaseY, gZ), Blocks.GRASS_BLOCK.defaultState, 2)
                    }
                }
            }
        }

        generateCentralStation(context, cityBaseY, stationFloorY, stationRailY)
        if (DEBUG_CITY_GEN && intersectsStation(origin)) {
            debugNotes.add("station-pass")
        }

        val skyscraperResult = generateSkyscraperChunk(context, cityBaseY, skyscraperPalettes)
        if (DEBUG_CITY_GEN && skyscraperResult != null) {
            debugNotes.add(skyscraperResult)
        }

        val suburbResult = generateSuburbHouseChunk(context, cityBaseY, housePalettes)
        if (DEBUG_CITY_GEN && suburbResult != null) {
            debugNotes.add(suburbResult)
        }

        if (DEBUG_CITY_GEN) {
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            val centerInfo = districtInfo(origin.x + 8, origin.z + 8)
            if (debugNotes.isNotEmpty() || elapsedMs >= SLOW_CHUNK_WARN_MS) {
                val level = if (elapsedMs >= SLOW_CHUNK_WARN_MS) "warn" else "info"
                val message = buildString {
                    append("[CityGrid] chunk=")
                    append(chunkX)
                    append(",")
                    append(chunkZ)
                    append(" region=")
                    append(centerInfo.regionX)
                    append(",")
                    append(centerInfo.regionZ)
                    append(" radius=")
                    append(centerInfo.radius)
                    append(" city=")
                    append(centerInfo.isCity)
                    append(" highway=")
                    append(centerInfo.isHighway || centerInfo.isMainAvenue)
                    append(" subway=")
                    append(centerInfo.isSubwayX || centerInfo.isSubwayZ)
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

    private fun generateRoadColumn(
        context: FeatureContext<DefaultFeatureConfig>,
        gX: Int,
        gZ: Int,
        roadY: Int,
        info: DistrictInfo,
        isBridge: Boolean
    ) {
        val world = context.world

        if (isBridge) {
            world.setBlockState(BlockPos(gX, roadY - 1, gZ), Blocks.STONE.defaultState, 2)
            world.setBlockState(BlockPos(gX, roadY, gZ), Blocks.CYAN_TERRACOTTA.defaultState, 2)

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
                        world.setBlockState(BlockPos(gX, y, gZ), Blocks.RED_CONCRETE.defaultState, 2)
                    }

                    for (dx in -1..1) {
                        for (dz in -1..1) {
                            if (abs(dx) == 1 && abs(dz) == 1) continue
                            for (y in max(seabedY, roadY + 1)..archY + 5) {
                                world.setBlockState(BlockPos(gX + dx, y, gZ + dz), Blocks.RED_NETHER_BRICKS.defaultState, 2)
                            }
                        }
                    }
                } else {
                    for (y in roadY + 1..archY - 1) {
                        world.setBlockState(BlockPos(gX, y, gZ), Blocks.AIR.defaultState, 2)
                    }

                    world.setBlockState(BlockPos(gX, archY, gZ), Blocks.RED_NETHER_BRICKS.defaultState, 2)
                    if (archY > roadY + 10 && spanPos % 3 == 0) {
                        world.setBlockState(BlockPos(gX, archY - 1, gZ), Blocks.RED_NETHER_BRICKS.defaultState, 2)
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

        world.setBlockState(BlockPos(gX, roadY - 1, gZ), Blocks.STONE.defaultState, 2)
        world.setBlockState(BlockPos(gX, roadY, gZ), Blocks.CYAN_TERRACOTTA.defaultState, 2)

        val actualMinDist = if (info.isCity) min(info.distX, info.distZ) else info.minHighwayDist
        if (actualMinDist == 0) {
            world.setBlockState(BlockPos(gX, roadY, gZ), Blocks.YELLOW_CONCRETE.defaultState, 2)
        }

        if (actualMinDist == 6 && ((gX % 32 == 0) || (gZ % 32 == 0))) {
            world.setBlockState(BlockPos(gX, roadY, gZ), Blocks.STONE_BRICKS.defaultState, 2)
            world.setBlockState(BlockPos(gX, roadY + 1, gZ), Blocks.STONE_BRICK_WALL.defaultState, 2)
            world.setBlockState(BlockPos(gX, roadY + 2, gZ), Blocks.STONE_BRICK_WALL.defaultState, 2)
            world.setBlockState(BlockPos(gX, roadY + 3, gZ), Blocks.SHROOMLIGHT.defaultState, 2)
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

        for (sy in (tunnelY - 1)..(tunnelY + 4)) {
            val pos = BlockPos(gX, sy, gZ)
            val isWall = (info.isSubwayX && info.distZ == 2) || (info.isSubwayZ && info.distX == 2) || sy == tunnelY - 1 || sy == tunnelY + 4
            val isLightColumn = sy == tunnelY + 2 && (
                (info.isSubwayX && info.distZ == 2 && Math.floorMod(gX, 8) == 0) ||
                (info.isSubwayZ && info.distX == 2 && Math.floorMod(gZ, 8) == 0)
            )
            if (isWall) {
                val material = when {
                    isLightColumn -> Blocks.SHROOMLIGHT.defaultState
                    isWaterSurface && sy > tunnelY -> Blocks.GLASS.defaultState
                    else -> Blocks.STONE_BRICKS.defaultState
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

        val chunkAxisDistance = min(abs(chunkInfo.centerX), abs(chunkInfo.centerZ)) - 8
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
        val palette = palettes[chunkHash % palettes.size]
        val height = cityBaseY + 30 + (chunkHash % 80)
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

    private fun generateSuburbHouseChunk(
        context: FeatureContext<DefaultFeatureConfig>,
        cityBaseY: Int,
        palettes: List<HousePalette>
    ): String? {
        val origin = context.origin
        val chunkInfo = districtInfo(origin.x + 8, origin.z + 8)
        if (!chunkInfo.isSuburb) return null

        val apartmentResult = generateApartmentBuildingChunk(context, cityBaseY)
        if (apartmentResult != null) return apartmentResult

        val lotLocalX = Math.floorMod(origin.x, 32)
        val lotLocalZ = Math.floorMod(origin.z, 32)
        if (lotLocalX != 16 || lotLocalZ != 16) return "house-skip(not-lot-owner)"

        val distanceToMainAvenue = min(abs(chunkInfo.centerX), abs(chunkInfo.centerZ)) - 8
        if (distanceToMainAvenue <= 18) return "house-skip(near-avenue)"
        if (chunkInfo.radius <= 140) return "house-skip(inner-ring)"

        val lotHash = positiveHash(origin.x shr 4, origin.z shr 4)
        val styles = listOf(
            HouseStyle("plains", 8, 8, 4),
            HouseStyle("plains", 10, 7, 4),
            HouseStyle("plains", 9, 9, 4, stories = 2),
            HouseStyle("plains", 10, 8, 4, stories = 2, splitLevel = true),
            HouseStyle("taiga", 8, 10, 4),
            HouseStyle("taiga", 9, 9, 4, stories = 2),
            HouseStyle("taiga", 10, 8, 4, splitLevel = true),
            HouseStyle("savanna", 9, 7, 4, flatRoof = true),
            HouseStyle("savanna", 10, 8, 4, stories = 2, flatRoof = true),
            HouseStyle("desert", 10, 8, 4, flatRoof = true),
            HouseStyle("desert", 9, 9, 4, stories = 2, flatRoof = true),
            HouseStyle("snowy", 8, 8, 5),
            HouseStyle("snowy", 9, 8, 4, stories = 2),
            HouseStyle("generic", 9, 8, 4),
            HouseStyle("generic", 10, 9, 4, stories = 2),
            HouseStyle("generic", 8, 10, 4, splitLevel = true)
        )
        val style = styles[lotHash % styles.size]
        val houseOffsetX = 2 + max(0, (12 - style.width) / 2)
        val houseOffsetZ = 2 + max(0, (12 - style.depth) / 2)
        val housePos = BlockPos(origin.x + houseOffsetX, cityBaseY, origin.z + houseOffsetZ)
        val hasPageLoot = lotHash % 30 == 0
        generateVillageStyleHouse(context.world, housePos, style, palettes[lotHash % palettes.size], lotHash, hasPageLoot)
        return "house(${style.family},stories=${style.stories},split=${style.splitLevel},loot=$hasPageLoot,palette=${lotHash % palettes.size})"
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
            repeat(max(1, pages)) {
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
        val distanceToMainAvenue = min(abs(ownerInfo.centerX), abs(ownerInfo.centerZ)) - 8
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
        val regionX = Math.floorMod(gX, 1024)
        val regionZ = Math.floorMod(gZ, 1024)
        val centerX = regionX - 512
        val centerZ = regionZ - 512
        val distX = abs(centerX)
        val distZ = abs(centerZ)
        val radius = max(distX, distZ)

        val isCore = radius <= 128
        val isSuburb = radius in 129..256
        val isCity = radius <= 256

        val fadeX = clamp01((distZ - 256).toDouble() / 128.0)
        val fadeZ = clamp01((distX - 256).toDouble() / 128.0)
        val twistX = (Math.sin(gZ * 0.03) * 20.0 * fadeX).toInt()
        val twistZ = (Math.sin(gX * 0.03) * 20.0 * fadeZ).toInt()
        val highwayDistX = abs(centerX + twistX)
        val highwayDistZ = abs(centerZ + twistZ)
        val minHighwayDist = min(highwayDistX, highwayDistZ)

        return DistrictInfo(
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
            isCore = isCore,
            isSuburb = isSuburb,
            isCity = isCity,
            isHighway = !isCity && minHighwayDist <= 6,
            isMainAvenue = isCity && min(distX, distZ) <= 6,
            isSubwayX = distZ < 3,
            isSubwayZ = distX < 3
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

    private fun shouldDecay(x: Int, y: Int, z: Int): Boolean {
        val hash = positiveHash(x xor y, z xor (y * 31))
        return hash % 100 < 5
    }

    private fun pickSkyscraperFront(info: DistrictInfo, chunkHash: Int): Direction {
        val touchesMainRoad = min(abs(info.centerX), abs(info.centerZ)) <= 24
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
