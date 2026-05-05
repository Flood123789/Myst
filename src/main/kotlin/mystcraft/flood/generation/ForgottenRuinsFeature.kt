package mystcraft.flood.generation

import com.mojang.serialization.Codec
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.generation.profile.TerrainType
import mystcraft.flood.item.ModItems
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.PillarBlock
import net.minecraft.block.ChestBlock
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtString
import net.minecraft.state.property.Properties
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.Direction
import net.minecraft.world.Heightmap
import net.minecraft.world.StructureWorldAccess
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.util.FeatureContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class ForgottenRuinsFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {

    override fun generate(context: FeatureContext<DefaultFeatureConfig>): Boolean {
        val world = context.world
        val origin = context.origin
        val serverWorld = world.toServerWorld()
        val ageId = serverWorld.registryKey.value

        if (!AgeSubdimensionManager.isPrimaryAgeRealm(ageId)) return false

        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, ageId)
        if (AgeLifecycleManager.isDeadAge(profile)) return false
        if (profile.terrainType == TerrainType.BIOSPHERES) return false

        val explicit = profile.modifiers.contains(HistoricAgeThemes.FORGOTTEN_RUINS)
        val instability = profile.stability.instabilityScore
        val spawnChance = when {
            explicit -> 0.72f
            instability >= 75 -> 0.34f
            instability >= 45 -> 0.16f
            instability >= 18 -> 0.055f
            else -> 0.0f
        } * AgeFeatureTuning.chanceMultiplier(profile, HistoricAgeThemes.FORGOTTEN_RUINS)
        if (spawnChance <= 0f) return false

        val chunkPos = ChunkPos(origin)
        val cultureRegionSize = 36
        val cultureRegionX = Math.floorDiv(chunkPos.x, cultureRegionSize)
        val cultureRegionZ = Math.floorDiv(chunkPos.z, cultureRegionSize)
        val cultureSeed = profile.seed +
            cultureRegionX.toLong() * 9_182_376_553L +
            cultureRegionZ.toLong() * 5_194_882_611L +
            0xCA71A4E2L
        val cultureRand = java.util.Random(cultureSeed)
        val activeRegionChance = if (explicit) 0.78f else 0.38f
        if (cultureRand.nextFloat() > activeRegionChance) return false
        val culture = RuinCulture.entries[cultureRand.nextInt(RuinCulture.entries.size)]

        val cellSize = if (explicit) 12 else 15
        val cellX = Math.floorDiv(chunkPos.x, cellSize)
        val cellZ = Math.floorDiv(chunkPos.z, cellSize)
        val localX = Math.floorMod(chunkPos.x, cellSize)
        val localZ = Math.floorMod(chunkPos.z, cellSize)
        val cellSeed = profile.seed +
            cellX.toLong() * 4_413_188_291L +
            cellZ.toLong() * 7_337_144_291L +
            culture.ordinal.toLong() * 41L
        val cellRand = java.util.Random(cellSeed)
        val targetX = cellRand.nextInt(cellSize)
        val targetZ = cellRand.nextInt(cellSize)
        if (localX != targetX || localZ != targetZ) return false
        if (cellRand.nextFloat() > spawnChance) return false

        val centerX = chunkPos.startX + 8 + cellRand.nextInt(7) - 3
        val centerZ = chunkPos.startZ + 8 + cellRand.nextInt(7) - 3
        val ground = getGround(world, centerX, centerZ) ?: return false
        val center = BlockPos(centerX, ground.y + 1, centerZ)
        if (!AgeFeatureTuning.canPlaceMajorFeature(profile, ChunkPos(center), HistoricAgeThemes.FORGOTTEN_RUINS, 9)) return false

        val majorSettlementChance = when {
            explicit -> 0.38f
            instability >= 70 -> 0.20f
            instability >= 40 -> 0.10f
            else -> 0.03f
        }
        if (cellRand.nextFloat() < majorSettlementChance) {
            buildSettlementCluster(world, chunkPos, center, culture, cellRand)
            return true
        }

        val bridgeAxis = findBridgeAxis(world, centerX, centerZ)
        val variantRoll = cellRand.nextInt(100)
        return when {
            bridgeAxis != null && variantRoll < 22 -> {
                buildBrokenBridge(world, chunkPos, center, culture, bridgeAxis, cellRand.nextBoolean())
                true
            }
            variantRoll < 38 -> {
                buildArchSite(world, chunkPos, center, culture, cellRand)
                true
            }
            variantRoll < 56 -> {
                buildHouseRuin(world, chunkPos, center, culture, 6 + cellRand.nextInt(3), 5 + cellRand.nextInt(3), 5 + cellRand.nextInt(2), true)
                true
            }
            variantRoll < 72 -> {
                buildCourtyardRuin(world, chunkPos, center, culture, cellRand)
                true
            }
            variantRoll < 84 -> {
                buildHamlet(world, chunkPos, center, culture, cellRand)
                true
            }
            variantRoll < 94 -> {
                buildShrinePlaza(world, chunkPos, center, culture, cellRand)
                true
            }
            else -> {
                buildWaystationLibrary(world, chunkPos, center, culture, cellRand)
                true
            }
        }
    }

    private fun buildSettlementCluster(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, culture: RuinCulture, rand: java.util.Random) {
        val clusterOffsets = listOf(
            BlockPos(0, 0, 0),
            BlockPos(-12, 0, -6),
            BlockPos(12, 0, -4),
            BlockPos(-10, 0, 8),
            BlockPos(11, 0, 9),
            BlockPos(0, 0, -12),
            BlockPos(0, 0, 13)
        ).shuffled(rand).take(5 + rand.nextInt(3))

        buildCultureLandmark(world, chunkPos, center, culture, rand)

        for (offset in clusterOffsets) {
            val siteCenter = center.add(offset)
            when (rand.nextInt(4)) {
                0 -> buildHouseRuin(world, chunkPos, siteCenter, culture, 6 + rand.nextInt(3), 5 + rand.nextInt(3), 5 + rand.nextInt(2), true)
                1 -> buildCourtyardRuin(world, chunkPos, siteCenter, culture, rand)
                2 -> buildHouseRuin(world, chunkPos, siteCenter, culture, 7 + rand.nextInt(3), 6 + rand.nextInt(2), 6 + rand.nextInt(2), true)
                else -> buildArchSite(world, chunkPos, siteCenter, culture, rand)
            }
            paveRoad(world, chunkPos, center, siteCenter, culture)
        }

        val pageChestSpots = listOf(center.add(1, 0, 1), center.add(-2, 0, 0))
        pageChestSpots.forEachIndexed { index, spot ->
            if (index < 1 + rand.nextInt(2)) {
                placeRuinChest(world, chunkPos, spot, facingToward(spot, center), true, rand)
            }
        }
        val supplySpots = listOf(center.add(0, 0, 3), center.add(3, 0, 0), center.add(-3, 0, 0))
        supplySpots.shuffled(rand).take(1 + rand.nextInt(2)).forEach { spot ->
            placeRuinChest(world, chunkPos, spot, facingToward(spot, center), false, rand)
        }

        scatterRubble(world, chunkPos, center, 18, culture, rand)
        placeOvergrowth(world, chunkPos, center, 18, culture, rand)
        placeDisasterScars(world, chunkPos, center, culture, rand)
    }

    private fun buildArchSite(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, culture: RuinCulture, rand: java.util.Random) {
        val columns = 3 + rand.nextInt(3)
        val spacing = 4
        val start = center.add(-(columns * spacing) / 2, 0, 0)
        val height = 6 + rand.nextInt(4)
        for (index in 0 until columns) {
            val x = start.x + index * spacing
            val fullHeight = height - if (rand.nextBoolean()) rand.nextInt(3) else 0
            buildColumn(world, chunkPos, BlockPos(x, center.y, center.z), culture, fullHeight)
            if (index < columns - 1 && rand.nextFloat() < 0.75f) {
                buildLintel(world, chunkPos, BlockPos(x, center.y + fullHeight, center.z), BlockPos(x + spacing, center.y + fullHeight - rand.nextInt(2), center.z), culture)
            }
        }
        scatterRubble(world, chunkPos, center, 9, culture, rand)
        placeOvergrowth(world, chunkPos, center, 10, culture, rand)
    }

    private fun buildCourtyardRuin(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, culture: RuinCulture, rand: java.util.Random) {
        val halfX = 6 + rand.nextInt(2)
        val halfZ = 5 + rand.nextInt(2)
        val wallHeight = 5 + rand.nextInt(2)

        for (dx in -halfX..halfX) {
            for (dz in -halfZ..halfZ) {
                val ground = getGround(world, center.x + dx, center.z + dz) ?: continue
                setBlock(world, chunkPos, ground, culture.floor)
                val isWall = dx == -halfX || dx == halfX || dz == -halfZ || dz == halfZ
                if (!isWall) continue
                val doorway = (abs(dx) <= 1 && dz == halfZ) || (abs(dz) <= 1 && dx == -halfX)
                for (y in 1..wallHeight) {
                    val pos = ground.up(y)
                    if (doorway && y <= 3) {
                        setBlockState(world, chunkPos, pos, Blocks.AIR.defaultState)
                    } else if (rand.nextFloat() > 0.14f + y * 0.03f) {
                        setBlock(world, chunkPos, pos, culture.wallFor(rand))
                    }
                }
            }
        }

        for (dx in -halfX + 2..halfX - 2 step 4) {
            buildColumn(world, chunkPos, center.add(dx, 0, -halfZ + 2), culture, 5 + rand.nextInt(2))
            buildColumn(world, chunkPos, center.add(dx, 0, halfZ - 2), culture, 5 + rand.nextInt(2))
        }

        scatterRubble(world, chunkPos, center, max(halfX, halfZ) + 3, culture, rand)
        placeOvergrowth(world, chunkPos, center, max(halfX, halfZ) + 2, culture, rand)
        if (rand.nextFloat() < 0.45f) {
            placeRuinChest(world, chunkPos, center.add(0, 0, 0), Direction.NORTH, rand.nextBoolean(), rand)
        }
    }

    private fun buildHouseRuin(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        culture: RuinCulture,
        width: Int,
        depth: Int,
        height: Int,
        damaged: Boolean
    ) {
        val halfW = width / 2
        val halfD = depth / 2
        for (dx in -halfW..halfW) {
            for (dz in -halfD..halfD) {
                val ground = getGround(world, center.x + dx, center.z + dz) ?: continue
                setBlock(world, chunkPos, ground, culture.floor)
                val perimeter = dx == -halfW || dx == halfW || dz == -halfD || dz == halfD
                if (!perimeter) continue
                val doorway = dz == halfD && abs(dx) <= 1
                for (y in 1..height) {
                    val pos = ground.up(y)
                    if (doorway && y <= 3) {
                        setBlockState(world, chunkPos, pos, Blocks.AIR.defaultState)
                    } else if (!damaged || (dx + dz + y).mod(5) != 0) {
                        setBlock(world, chunkPos, pos, culture.wallForDeterministic(dx, dz, y))
                    }
                }
                if (abs(dx) == halfW && abs(dz) == halfD) {
                    buildColumn(world, chunkPos, ground.up(1), culture, height + 1)
                }
            }
        }

        for (dx in -halfW + 1..halfW - 1) {
            val leftRoof = center.add(dx, height + 1 - abs(dx) / 3, -halfD + 1)
            val rightRoof = center.add(dx, height + 1 - abs(dx) / 3, halfD - 1)
            if (!damaged || (dx and 1) == 0) {
                setBlock(world, chunkPos, leftRoof, culture.slab)
                setBlock(world, chunkPos, rightRoof, culture.slab)
            }
        }

        setBlock(world, chunkPos, center.up(1), Blocks.CRAFTING_TABLE)
        scatterRubble(world, chunkPos, center, max(width, depth), culture, java.util.Random(center.asLong()))
        if ((width + depth + height) % 2 == 0) {
            placeRuinChest(world, chunkPos, center.add(0, 0, -halfD + 1), Direction.SOUTH, false, java.util.Random(center.asLong() xor 31L))
        }
    }

    private fun buildHamlet(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, culture: RuinCulture, rand: java.util.Random) {
        val offsets = listOf(
            BlockPos(-8, 0, -4),
            BlockPos(7, 0, -5),
            BlockPos(-6, 0, 6),
            BlockPos(8, 0, 5),
            BlockPos(0, 0, 0)
        ).shuffled(rand).take(3 + rand.nextInt(3))

        for (offset in offsets) {
            val houseCenter = center.add(offset)
            buildHouseRuin(world, chunkPos, houseCenter, culture, 5 + rand.nextInt(3), 5 + rand.nextInt(3), 4 + rand.nextInt(2), true)
        }

        for (dx in -10..10) {
            val ground = getGround(world, center.x + dx, center.z) ?: continue
            if (dx % 2 == 0) setBlock(world, chunkPos, ground, culture.floor)
        }
        scatterRubble(world, chunkPos, center, 13, culture, rand)
        placeOvergrowth(world, chunkPos, center, 14, culture, rand)
        placeDisasterScars(world, chunkPos, center, culture, rand)
    }

    private fun buildShrinePlaza(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, culture: RuinCulture, rand: java.util.Random) {
        val radius = 7
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                val dist = abs(dx) + abs(dz)
                if (dist > 11) continue
                val ground = getGround(world, center.x + dx, center.z + dz) ?: continue
                val block = when {
                    dist > 9 -> culture.cracked
                    dx == 0 || dz == 0 -> culture.floor
                    else -> if ((dx * 13 + dz * 7).mod(5) == 0) culture.slab else culture.floor
                }
                setBlock(world, chunkPos, ground, block)
            }
        }

        buildSteppedDais(world, chunkPos, center, culture, 3)
        for (corner in listOf(BlockPos(-5, 0, -5), BlockPos(5, 0, -5), BlockPos(-5, 0, 5), BlockPos(5, 0, 5))) {
            buildColumn(world, chunkPos, center.add(corner), culture, 5 + rand.nextInt(2))
        }
        buildLintel(world, chunkPos, center.add(-5, 5, -5), center.add(5, 4, -5), culture)
        buildLintel(world, chunkPos, center.add(-5, 5, 5), center.add(5, 4, 5), culture)
        setBlock(world, chunkPos, center.up(4), Blocks.BELL)
        setBlock(world, chunkPos, center.north(3).up(), Blocks.SOUL_LANTERN)
        setBlock(world, chunkPos, center.south(3).up(), Blocks.SOUL_LANTERN)

        if (rand.nextFloat() < 0.65f) {
            placeRuinChest(world, chunkPos, center.add(0, 0, -5), Direction.SOUTH, rand.nextBoolean(), rand)
        }
        scatterRubble(world, chunkPos, center, 10, culture, rand)
        placeOvergrowth(world, chunkPos, center, 11, culture, rand)
    }

    private fun buildWaystationLibrary(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, culture: RuinCulture, rand: java.util.Random) {
        val halfX = 5
        val halfZ = 7
        for (dx in -halfX..halfX) {
            for (dz in -halfZ..halfZ) {
                val ground = getGround(world, center.x + dx, center.z + dz) ?: continue
                setBlock(world, chunkPos, ground, if ((dx + dz).mod(4) == 0) culture.cracked else culture.floor)
                val perimeter = dx == -halfX || dx == halfX || dz == -halfZ || dz == halfZ
                if (!perimeter) continue
                val doorway = dz == halfZ && abs(dx) <= 1
                for (y in 1..5) {
                    if (doorway && y <= 3) {
                        setBlockState(world, chunkPos, ground.up(y), Blocks.AIR.defaultState)
                    } else if (rand.nextFloat() > 0.18f + y * 0.025f) {
                        setBlock(world, chunkPos, ground.up(y), culture.wallForDeterministic(dx, dz, y))
                    }
                }
            }
        }

        for (z in -5..3 step 2) {
            setBlock(world, chunkPos, center.add(-3, 0, z), Blocks.BOOKSHELF)
            setBlock(world, chunkPos, center.add(-3, 1, z), if (rand.nextBoolean()) Blocks.CHISELED_BOOKSHELF else Blocks.BOOKSHELF)
            setBlock(world, chunkPos, center.add(3, 0, z), Blocks.BOOKSHELF)
            setBlock(world, chunkPos, center.add(3, 1, z), if (rand.nextBoolean()) Blocks.CHISELED_BOOKSHELF else Blocks.BOOKSHELF)
        }
        setBlock(world, chunkPos, center.add(0, 0, -4), Blocks.LECTERN)
        setBlock(world, chunkPos, center.add(0, 0, -2), Blocks.CARTOGRAPHY_TABLE)
        buildColumn(world, chunkPos, center.add(-halfX, 0, -halfZ), culture, 6)
        buildColumn(world, chunkPos, center.add(halfX, 0, -halfZ), culture, 6)
        buildLintel(world, chunkPos, center.add(-halfX, 5, -halfZ), center.add(halfX, 4, -halfZ), culture)
        paveRoad(world, chunkPos, center.add(0, 0, halfZ), center.add(0, 0, halfZ + 10), culture)

        placeRuinChest(world, chunkPos, center.add(0, 0, -5), Direction.SOUTH, true, rand)
        if (rand.nextBoolean()) {
            placeRuinChest(world, chunkPos, center.add(4, 0, 4), Direction.WEST, false, rand)
        }
        scatterRubble(world, chunkPos, center, 10, culture, rand)
        placeOvergrowth(world, chunkPos, center, 10, culture, rand)
    }

    private fun buildBrokenBridge(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        culture: RuinCulture,
        axis: Direction.Axis,
        mostlyIntact: Boolean
    ) {
        val span = 11
        val width = 2
        val bridgeY = center.y + 1
        val breakStart = if (mostlyIntact) 2 else -1
        val breakEnd = if (mostlyIntact) 3 else 1
        for (step in -span..span) {
            val inGap = step in breakStart..breakEnd
            val pillarHere = step == -span || step == span || step == -span / 2 || step == span / 2
            for (side in -width..width) {
                val deckPos = offset(BlockPos(center.x, bridgeY, center.z), axis, step, 0, side)
                if (!inGap) {
                    setBlock(world, chunkPos, deckPos, culture.floor)
                    if (abs(side) == width) setBlock(world, chunkPos, deckPos.up(), culture.stair)
                } else if (mostlyIntact && abs(side) <= 1 && abs(step) >= 2) {
                    setBlock(world, chunkPos, deckPos, culture.floor)
                }
            }
            if (pillarHere) {
                val pillarBase = offset(center, axis, step, 0, 0)
                val ground = getGround(world, pillarBase.x, pillarBase.z) ?: continue
                for (y in ground.y..bridgeY) {
                    setBlockState(world, chunkPos, BlockPos(pillarBase.x, y, pillarBase.z), culture.pillarState(Direction.Axis.Y))
                }
            }
        }
        scatterRubble(world, chunkPos, center, 8, culture, java.util.Random(center.asLong()))
        if (mostlyIntact && java.util.Random(center.asLong()).nextFloat() < 0.35f) {
            placeRuinChest(world, chunkPos, center.add(0, 0, 0), Direction.NORTH, false, java.util.Random(center.asLong() xor 91L))
        }
    }

    private fun buildCultureLandmark(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, culture: RuinCulture, rand: java.util.Random) {
        when (culture) {
            RuinCulture.CLASSICAL -> {
                buildArchSite(world, chunkPos, center, culture, rand)
                buildSteppedDais(world, chunkPos, center.add(0, 0, 6), culture, 4)
            }
            RuinCulture.HIGHLAND -> {
                buildWatchtowerRuin(world, chunkPos, center, culture, 11 + rand.nextInt(4))
            }
            RuinCulture.DESERT -> {
                buildSteppedDais(world, chunkPos, center, culture, 5)
                buildArchSite(world, chunkPos, center.add(0, 0, 8), culture, rand)
            }
            RuinCulture.JUNGLE -> {
                buildSteppedDais(world, chunkPos, center, culture, 5)
                buildCourtyardRuin(world, chunkPos, center.add(0, 0, 9), culture, rand)
            }
            RuinCulture.BLACKFORT -> {
                buildGatehouseRuin(world, chunkPos, center, culture, rand)
            }
        }
    }

    private fun buildWatchtowerRuin(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, culture: RuinCulture, height: Int) {
        val half = 3
        for (dx in -half..half) {
            for (dz in -half..half) {
                val ground = getGround(world, center.x + dx, center.z + dz) ?: continue
                if (abs(dx) == half || abs(dz) == half) {
                    for (y in 0..height) {
                        if ((dx + dz + y) % 6 != 0 || y < 3) {
                            setBlock(world, chunkPos, ground.up(y), culture.wallForDeterministic(dx, dz, y))
                        }
                    }
                } else if (dx == 0 && dz == 0) {
                    setBlock(world, chunkPos, ground, culture.floor)
                }
            }
        }
        buildLintel(world, chunkPos, center.add(-3, height - 2, -3), center.add(3, height - 3, -3), culture)
        placeRuinChest(world, chunkPos, center.add(0, 0, 0), Direction.SOUTH, true, java.util.Random(center.asLong() xor 113L))
    }

    private fun buildGatehouseRuin(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, culture: RuinCulture, rand: java.util.Random) {
        val halfX = 5
        val halfZ = 3
        val wallHeight = 7
        for (dx in -halfX..halfX) {
            for (dz in -halfZ..halfZ) {
                val ground = getGround(world, center.x + dx, center.z + dz) ?: continue
                setBlock(world, chunkPos, ground, culture.floor)
                val perimeter = dx == -halfX || dx == halfX || dz == -halfZ || dz == halfZ
                if (!perimeter) continue
                for (y in 1..wallHeight) {
                    val isGate = abs(dx) <= 1 && dz == 0 && y <= 4
                    if (!isGate && rand.nextFloat() > 0.12f + y * 0.02f) {
                        setBlock(world, chunkPos, ground.up(y), culture.wallForDeterministic(dx, dz, y))
                    }
                }
            }
        }
        buildColumn(world, chunkPos, center.add(-halfX, 0, -halfZ), culture, wallHeight + 1)
        buildColumn(world, chunkPos, center.add(halfX, 0, -halfZ), culture, wallHeight + 1)
        buildColumn(world, chunkPos, center.add(-halfX, 0, halfZ), culture, wallHeight + 1)
        buildColumn(world, chunkPos, center.add(halfX, 0, halfZ), culture, wallHeight + 1)
        placeRuinChest(world, chunkPos, center.add(0, 0, -2), Direction.SOUTH, true, rand)
    }

    private fun buildSteppedDais(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, culture: RuinCulture, tiers: Int) {
        for (tier in tiers downTo 1) {
            val radius = tier + 1
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    val ground = getGround(world, center.x + dx, center.z + dz) ?: continue
                    setBlock(world, chunkPos, ground.up(tiers - tier), if (abs(dx) == radius || abs(dz) == radius) culture.stair else culture.floor)
                }
            }
        }
        buildColumn(world, chunkPos, center.up(tiers), culture, 4)
    }

    private fun paveRoad(world: StructureWorldAccess, chunkPos: ChunkPos, start: BlockPos, end: BlockPos, culture: RuinCulture) {
        val dx = end.x - start.x
        val dz = end.z - start.z
        val steps = max(abs(dx), abs(dz)).coerceAtLeast(1)
        for (step in 0..steps) {
            val t = step.toDouble() / steps.toDouble()
            val x = lerp(start.x.toDouble(), end.x.toDouble(), t).roundToInt()
            val z = lerp(start.z.toDouble(), end.z.toDouble(), t).roundToInt()
            val ground = getGround(world, x, z) ?: continue
            setBlock(world, chunkPos, ground, culture.floor)
        }
    }

    private fun buildColumn(world: StructureWorldAccess, chunkPos: ChunkPos, base: BlockPos, culture: RuinCulture, height: Int) {
        for (y in 0 until height) {
            setBlockState(world, chunkPos, base.up(y), culture.pillarState(Direction.Axis.Y))
        }
        setBlock(world, chunkPos, base.up(height), culture.slab)
    }

    private fun buildLintel(world: StructureWorldAccess, chunkPos: ChunkPos, start: BlockPos, end: BlockPos, culture: RuinCulture) {
        val steps = max(abs(end.x - start.x), abs(end.z - start.z)).coerceAtLeast(1)
        for (step in 0..steps) {
            val t = step.toDouble() / steps.toDouble()
            val pos = BlockPos(
                lerp(start.x.toDouble(), end.x.toDouble(), t).roundToInt(),
                lerp(start.y.toDouble(), end.y.toDouble(), t).roundToInt(),
                lerp(start.z.toDouble(), end.z.toDouble(), t).roundToInt()
            )
            setBlock(world, chunkPos, pos, culture.beam)
        }
    }

    private fun scatterRubble(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, radius: Int, culture: RuinCulture, rand: java.util.Random) {
        repeat(18 + rand.nextInt(14)) {
            val x = center.x + rand.nextInt(radius * 2 + 1) - radius
            val z = center.z + rand.nextInt(radius * 2 + 1) - radius
            val ground = getGround(world, x, z) ?: return@repeat
            val state = when (rand.nextInt(5)) {
                0 -> culture.floor.defaultState
                1 -> culture.wallFor(rand).defaultState
                2 -> culture.slab.defaultState
                3 -> culture.stair.defaultState.with(Properties.HORIZONTAL_FACING, Direction.fromHorizontal(rand.nextInt(4)))
                else -> culture.beam.defaultState
            }
            setBlockState(world, chunkPos, ground.up(rand.nextInt(2)), state)
        }
    }

    private fun placeOvergrowth(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, radius: Int, culture: RuinCulture, rand: java.util.Random) {
        repeat(10 + rand.nextInt(8)) {
            val x = center.x + rand.nextInt(radius * 2 + 1) - radius
            val z = center.z + rand.nextInt(radius * 2 + 1) - radius
            val ground = getGround(world, x, z) ?: return@repeat
            val plantPos = ground.up()
            if (isSubmerged(world, plantPos)) return@repeat
            if (!world.getBlockState(plantPos).isAir) return@repeat
            setBlock(world, chunkPos, plantPos, culture.overgrowth)
        }
    }

    private fun placeDisasterScars(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, culture: RuinCulture, rand: java.util.Random) {
        repeat(8 + rand.nextInt(6)) {
            val x = center.x + rand.nextInt(25) - 12
            val z = center.z + rand.nextInt(12 * 2 + 1) - 12
            val ground = getGround(world, x, z) ?: return@repeat
            when (rand.nextInt(4)) {
                0 -> setBlockState(world, chunkPos, ground.up(), Blocks.CAMPFIRE.defaultState.with(Properties.LIT, false))
                1 -> setBlock(world, chunkPos, ground.up(), Blocks.SOUL_LANTERN)
                2 -> setBlock(world, chunkPos, ground.up(), Blocks.SKELETON_SKULL)
                else -> setBlock(world, chunkPos, ground.up(), culture.beam)
            }
        }
    }

    private fun placeRuinChest(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        pos: BlockPos,
        facing: Direction,
        pageLoot: Boolean,
        rand: java.util.Random
    ) {
        if ((pos.x shr 4) != chunkPos.x || (pos.z shr 4) != chunkPos.z) return
        setBlockState(world, chunkPos, pos, Blocks.CHEST.defaultState.with(ChestBlock.FACING, facing))
        val chest = world.getBlockEntity(pos) as? ChestBlockEntity ?: return
        if (pageLoot) {
            repeat(1 + rand.nextInt(2)) {
                chest.setStack(rand.nextInt(chest.size()), ItemStack(ModItems.LOST_PAGE))
            }
        }
        val loot = mutableListOf(
            ItemStack(Items.BOOK, 1 + rand.nextInt(2)),
            ItemStack(Items.PAPER, 2 + rand.nextInt(5)),
            ItemStack(Items.MAP),
            ItemStack(Items.TORCH, 2 + rand.nextInt(4)),
            ItemStack(Items.BREAD, 1 + rand.nextInt(3))
        )
        if (rand.nextFloat() < 0.45f) {
            loot += storyBook(rand)
        }
        loot.shuffled(rand).take(2 + rand.nextInt(2)).forEach {
            chest.setStack(rand.nextInt(chest.size()), it)
        }
    }

    private fun storyBook(rand: java.util.Random): ItemStack {
        val titles = listOf(
            "Last Ledger",
            "Ash Record",
            "The Silent Ward",
            "When The Bells Stopped",
            "House Names"
        )
        val lines = listOf(
            listOf("The lamps burned for three nights after the doors were barred.", "By dawn there was no one left to claim the shelves."),
            listOf("We counted the houses twice.", "The empty ones were still warm."),
            listOf("The bridge held when the people fled.", "It did not hold for what followed."),
            listOf("Bones were found higher than the rooftops.", "No storm leaves ribs in stone."),
            listOf("The archives were sealed.", "The books remained. The voices did not.")
        )
        val choice = rand.nextInt(lines.size)
        return ItemStack(Items.WRITTEN_BOOK).also { stack ->
            val pages = NbtList()
            lines[choice].forEach { pages.add(NbtString.of(Text.Serializer.toJson(Text.literal(it)))) }
            val nbt = stack.orCreateNbt
            nbt.putString("title", titles[choice])
            nbt.putString("author", "Unknown")
            nbt.put("pages", pages)
        }
    }

    private fun facingToward(pos: BlockPos, center: BlockPos): Direction {
        val dx = pos.x - center.x
        val dz = pos.z - center.z
        return if (abs(dx) > abs(dz)) {
            if (dx > 0) Direction.WEST else Direction.EAST
        } else {
            if (dz > 0) Direction.NORTH else Direction.SOUTH
        }
    }

    private fun findBridgeAxis(world: StructureWorldAccess, centerX: Int, centerZ: Int): Direction.Axis? {
        return sequenceOf(Direction.Axis.X, Direction.Axis.Z).firstOrNull { axis ->
            var waterCount = 0
            for (step in -8..8) {
                val x = if (axis == Direction.Axis.X) centerX + step else centerX
                val z = if (axis == Direction.Axis.Z) centerZ + step else centerZ
                val topY = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z)
                val state = world.getBlockState(BlockPos(x, topY - 1, z))
                if (state.isOf(Blocks.WATER)) waterCount++
            }
            waterCount >= 5
        }
    }

    private fun getGround(world: StructureWorldAccess, x: Int, z: Int): BlockPos? {
        return FeatureBuildHelper.findGround(world, x, z, FeatureBuildHelper.WaterMode.SEABED)
    }

    private fun setBlock(world: StructureWorldAccess, chunkPos: ChunkPos, pos: BlockPos, block: Block) {
        setBlockState(world, chunkPos, pos, block.defaultState)
    }

    private fun setBlockState(world: StructureWorldAccess, chunkPos: ChunkPos, pos: BlockPos, state: BlockState) {
        if (pos.y < world.bottomY || pos.y >= world.topY) return
        val targetState = adaptForWater(world, pos, state)

        if ((pos.x shr 4) != chunkPos.x || (pos.z shr 4) != chunkPos.z) {
            DeferredTreePlacer.add(world.toServerWorld(), pos, targetState, !targetState.isAir)
            return
        }

        val existing = world.getBlockState(pos)
        if (existing.getHardness(world, pos) < 0.0f) return
        if (targetState.isAir) {
            world.setBlockState(pos, targetState, 2)
            return
        }

        val canReplace = existing.isAir || existing.isReplaceable || existing.isOf(Blocks.GRASS_BLOCK) ||
            existing.isOf(Blocks.DIRT) || existing.isOf(Blocks.STONE) || existing.isOf(Blocks.SAND) ||
            existing.isOf(Blocks.GRAVEL) || existing.isOf(Blocks.COARSE_DIRT) || existing.isOf(Blocks.MUD) ||
            existing.isOf(Blocks.SNOW) || existing.isOf(Blocks.SNOW_BLOCK) || existing.isOf(Blocks.WATER)

        if (!canReplace && existing.block != targetState.block) return
        world.setBlockState(pos, targetState, 2)
    }

    private fun adaptForWater(world: StructureWorldAccess, pos: BlockPos, requested: BlockState): BlockState {
        val submerged = isSubmerged(world, pos)
        if (requested.isAir) {
            return if (submerged) Blocks.WATER.defaultState else Blocks.AIR.defaultState
        }

        var result = requested
        if (submerged && result.contains(Properties.WATERLOGGED)) {
            result = result.with(Properties.WATERLOGGED, true)
        }
        return result
    }

    private fun isSubmerged(world: StructureWorldAccess, pos: BlockPos): Boolean {
        val topY = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, pos.x, pos.z)
        if (topY <= world.bottomY + 1) return false
        val topState = world.getBlockState(BlockPos(pos.x, topY - 1, pos.z))
        return topState.isOf(Blocks.WATER) && pos.y <= topY - 1
    }

    private fun hasStableMass(world: StructureWorldAccess, pos: BlockPos): Boolean {
        var solidDepth = 0
        for (offset in 0..3) {
            val sampleY = pos.y - offset
            if (sampleY <= world.bottomY) break
            val sampleState = world.getBlockState(BlockPos(pos.x, sampleY, pos.z))
            if (isSolidGround(sampleState)) {
                solidDepth++
            }
        }
        return solidDepth >= 3
    }

    private fun isSolidGround(state: BlockState): Boolean {
        return !state.isAir && !state.isOf(Blocks.WATER) && !state.isOf(Blocks.LAVA)
    }

    private fun offset(base: BlockPos, axis: Direction.Axis, along: Int, up: Int, sideways: Int): BlockPos =
        when (axis) {
            Direction.Axis.X -> base.add(along, up, sideways)
            Direction.Axis.Z -> base.add(sideways, up, along)
            Direction.Axis.Y -> base.add(sideways, along, up)
        }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    private enum class RuinCulture(
        val wall: Block,
        val cracked: Block,
        val floor: Block,
        val beam: Block,
        val stair: Block,
        val slab: Block,
        val pillar: Block,
        val overgrowth: Block
    ) {
        CLASSICAL(
            Blocks.SMOOTH_SANDSTONE,
            Blocks.CUT_SANDSTONE,
            Blocks.CUT_SANDSTONE,
            Blocks.CHISELED_SANDSTONE,
            Blocks.SANDSTONE_STAIRS,
            Blocks.SANDSTONE_SLAB,
            Blocks.QUARTZ_PILLAR,
            Blocks.FLOWERING_AZALEA_LEAVES
        ),
        HIGHLAND(
            Blocks.STONE_BRICKS,
            Blocks.CRACKED_STONE_BRICKS,
            Blocks.COBBLESTONE,
            Blocks.SPRUCE_PLANKS,
            Blocks.STONE_BRICK_STAIRS,
            Blocks.STONE_BRICK_SLAB,
            Blocks.STONE_BRICK_WALL,
            Blocks.FERN
        ),
        DESERT(
            Blocks.MUD_BRICKS,
            Blocks.PACKED_MUD,
            Blocks.SMOOTH_SANDSTONE,
            Blocks.ACACIA_PLANKS,
            Blocks.MUD_BRICK_STAIRS,
            Blocks.MUD_BRICK_SLAB,
            Blocks.CUT_SANDSTONE,
            Blocks.DEAD_BUSH
        ),
        JUNGLE(
            Blocks.MOSSY_COBBLESTONE,
            Blocks.COBBLESTONE,
            Blocks.MOSS_BLOCK,
            Blocks.JUNGLE_PLANKS,
            Blocks.COBBLESTONE_STAIRS,
            Blocks.COBBLESTONE_SLAB,
            Blocks.MOSSY_STONE_BRICKS,
            Blocks.JUNGLE_LEAVES
        ),
        BLACKFORT(
            Blocks.DEEPSLATE_BRICKS,
            Blocks.CRACKED_DEEPSLATE_BRICKS,
            Blocks.POLISHED_BASALT,
            Blocks.DARK_OAK_PLANKS,
            Blocks.DEEPSLATE_BRICK_STAIRS,
            Blocks.DEEPSLATE_BRICK_SLAB,
            Blocks.POLISHED_BLACKSTONE_BRICKS,
            Blocks.WARPED_WART_BLOCK
        );

        fun wallFor(rand: java.util.Random): Block = if (rand.nextInt(4) == 0) cracked else wall

        fun wallForDeterministic(dx: Int, dz: Int, y: Int): Block =
            if ((dx * 31 + dz * 17 + y * 13).mod(5) == 0) cracked else wall

        fun pillarState(axis: Direction.Axis): BlockState =
            if (pillar is PillarBlock) pillar.defaultState.with(Properties.AXIS, axis) else pillar.defaultState
    }
}
