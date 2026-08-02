package mystcraft.flood.generation

import com.mojang.serialization.Codec
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.generation.profile.TerrainType
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.ChestBlock
import net.minecraft.block.entity.ChestBlockEntity
import mystcraft.flood.item.ModItems
import net.minecraft.state.property.Properties
import net.minecraft.util.math.Direction
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.StructureWorldAccess
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.util.FeatureContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Applies profile-selected exotic surface treatments without replacing the base chunk generator.
 * The feature samples the existing terrain first, then performs bounded decoration so authored
 * themes remain compatible with vanilla and modded biome/noise sources.
 */
class ExoticSurfaceFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {

    override fun generate(context: FeatureContext<DefaultFeatureConfig>): Boolean {
        val world = context.world
        val origin = context.origin
        val serverWorld = world.toServerWorld()
        val ageId = serverWorld.registryKey.value

        if (!AgeSubdimensionManager.isPrimaryAgeRealm(ageId)) return false

        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, ageId)
        if (AgeLifecycleManager.isDeadAge(profile)) return false
        val theme = ExoticAgeThemes.fromModifiers(profile.modifiers) ?: return false

        if (profile.terrainType == TerrainType.CITIES || profile.terrainType == TerrainType.BIOSPHERES) return false

        val chunkPos = ChunkPos(origin)
        var placed = false
        val seededRand = java.util.Random(profile.seed + chunkPos.x.toLong() * 341_873_128_712L + chunkPos.z.toLong() * 132_897_987_541L + theme.hashCode())

        if (theme == ExoticAgeThemes.VIRUS) {
            placed = placeMegaVirusStructure(world, chunkPos, profile.seed) || placed
        }

        if (!shouldPopulateChunk(profile, theme, chunkPos, profile.seed, seededRand)) return placed

        repeat(1) {
            val centerX = origin.x + 3 + seededRand.nextInt(10)
            val centerZ = origin.z + 3 + seededRand.nextInt(10)
            val ground = getGround(world, centerX, centerZ) ?: return@repeat
            placed = placeAmbient(theme, world, chunkPos, ground, seededRand) || placed
        }

        if (seededRand.nextInt(5) == 0) {
            val centerX = origin.x + 5 + seededRand.nextInt(6)
            val centerZ = origin.z + 5 + seededRand.nextInt(6)
            val ground = getGround(world, centerX, centerZ) ?: return placed
            placed = placeMajor(theme, world, chunkPos, ground, seededRand) || placed
        }

        return placed
    }

    private fun shouldPopulateChunk(profile: mystcraft.flood.generation.profile.AgeProfile, theme: String, chunkPos: ChunkPos, seed: Long, rand: java.util.Random): Boolean {
        val cellSize = when (theme) {
            ExoticAgeThemes.FRACTAL_CUBES -> 5
            ExoticAgeThemes.CABLES -> 4
            else -> 4
        }

        val cellX = Math.floorDiv(chunkPos.x, cellSize)
        val cellZ = Math.floorDiv(chunkPos.z, cellSize)
        val localX = Math.floorMod(chunkPos.x, cellSize)
        val localZ = Math.floorMod(chunkPos.z, cellSize)

        val cellSeed = seed +
            cellX.toLong() * 18_734_234_551L +
            cellZ.toLong() * 9_127_367_819L +
            theme.hashCode().toLong() * 31L
        val cellRand = java.util.Random(cellSeed)
        val targetX = cellRand.nextInt(cellSize)
        val targetZ = cellRand.nextInt(cellSize)
        if (localX != targetX || localZ != targetZ) return false

        val rarity = when (theme) {
            ExoticAgeThemes.FRACTAL_CUBES -> 3
            ExoticAgeThemes.SEPARATORS -> 2
            ExoticAgeThemes.WIRE_CELLS -> 2
            ExoticAgeThemes.LIGHT_FISSURES -> 2
            ExoticAgeThemes.VIRUS -> 3
            else -> 1
        }

        return rand.nextInt(AgeFeatureTuning.rarityRollDivisor(profile, rarity, theme)) == 0
    }

    private fun placeAmbient(theme: String, world: StructureWorldAccess, chunkPos: ChunkPos, ground: BlockPos, rand: java.util.Random): Boolean {
        return when (theme) {
            ExoticAgeThemes.HEX -> {
                paintHexPatch(world, chunkPos, ground, 3, Blocks.HONEYCOMB_BLOCK, Blocks.CUT_COPPER)
                placeHexBush(world, chunkPos, ground.add(rand.nextInt(3) - 1, 1, rand.nextInt(3) - 1), 4 + rand.nextInt(3))
                true
            }
            ExoticAgeThemes.WIRE_CELLS -> {
                paintDisk(world, chunkPos, ground, 3, Blocks.BLACKSTONE, Blocks.SCULK)
                placeWireCube(world, chunkPos, ground.add(0, 2, 0), 2, Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.SEA_LANTERN)
                true
            }
            ExoticAgeThemes.SEPARATORS -> {
                placeSeparator(world, chunkPos, ground.add(0, 1, 0), 10 + rand.nextInt(8))
                true
            }
            ExoticAgeThemes.CABLES -> {
                paintDisk(world, chunkPos, ground, 3, Blocks.GRAY_CONCRETE, Blocks.BLACK_CONCRETE)
                placeCableBundle(world, chunkPos, ground.add(0, 2, 0), rand.nextBoolean(), 5, 3)
                true
            }
            ExoticAgeThemes.FRACTAL_CUBES -> {
                paintDisk(world, chunkPos, ground, 3, Blocks.SMOOTH_STONE, Blocks.LIGHT_GRAY_CONCRETE)
                placeFractalCluster(world, chunkPos, ground.add(0, 4, 0), 5, false)
                true
            }
            ExoticAgeThemes.LIGHT_FISSURES -> {
                paintDisk(world, chunkPos, ground, 3, Blocks.CALCITE, Blocks.SMOOTH_BASALT)
                placeLightFissure(world, chunkPos, ground.add(0, 1, 0), 8 + rand.nextInt(6))
                true
            }
            ExoticAgeThemes.VIRUS -> {
                paintDisk(world, chunkPos, ground, 3, Blocks.PURPLE_CONCRETE, Blocks.BLACKSTONE)
                if (rand.nextBoolean()) placeVirusClump(world, chunkPos, ground.add(0, 4, 0), 3, false)
                else placeVirusTower(world, chunkPos, ground.add(0, 1, 0), 8, 4, false)
                true
            }
            else -> false
        }
    }

    private fun placeMajor(theme: String, world: StructureWorldAccess, chunkPos: ChunkPos, ground: BlockPos, rand: java.util.Random): Boolean {
        val colossal = rand.nextInt(6) == 0
        return when (theme) {
            ExoticAgeThemes.HEX -> {
                paintHexPatch(world, chunkPos, ground, if (colossal) 6 else 5, Blocks.HONEYCOMB_BLOCK, Blocks.WAXED_COPPER_BLOCK)
                for (dx in -2..2 step 2) {
                    placeHexBush(world, chunkPos, ground.add(dx, 1, rand.nextInt(5) - 2), (if (colossal) 8 else 6) + rand.nextInt(4))
                }
                true
            }
            ExoticAgeThemes.WIRE_CELLS -> {
                paintDisk(world, chunkPos, ground, if (colossal) 6 else 5, Blocks.BLACK_CONCRETE, Blocks.SCULK)
                placeWireCube(world, chunkPos, ground.add(0, if (colossal) 5 else 3, 0), if (colossal) 4 else 3, Blocks.CYAN_STAINED_GLASS, Blocks.SEA_LANTERN)
                placeWireCube(world, chunkPos, ground.add(-4, if (colossal) 7 else 5, 0), 2, Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.END_STONE)
                placeWireCube(world, chunkPos, ground.add(4, if (colossal) 6 else 4, 1), 2, Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.END_STONE)
                placeWireSpan(world, chunkPos, ground.add(-4, if (colossal) 7 else 5, 0), ground.add(0, if (colossal) 5 else 3, 0), Blocks.CHAIN)
                placeWireSpan(world, chunkPos, ground.add(4, if (colossal) 6 else 4, 1), ground.add(0, if (colossal) 5 else 3, 0), Blocks.CHAIN)
                true
            }
            ExoticAgeThemes.SEPARATORS -> {
                placeSeparator(world, chunkPos, ground.add(0, 1, 0), (if (colossal) 24 else 18) + rand.nextInt(10))
                placeSeparator(world, chunkPos, ground.add(-4, 1, 2), (if (colossal) 16 else 12) + rand.nextInt(6))
                placeSeparator(world, chunkPos, ground.add(4, 1, -2), (if (colossal) 16 else 12) + rand.nextInt(6))
                true
            }
            ExoticAgeThemes.CABLES -> {
                paintDisk(world, chunkPos, ground, if (colossal) 6 else 5, Blocks.GRAY_CONCRETE, Blocks.BLACK_CONCRETE)
                placeCableBundle(world, chunkPos, ground.add(0, if (colossal) 4 else 3, 0), true, if (colossal) 8 else 7, if (colossal) 5 else 4)
                placeCableBundle(world, chunkPos, ground.add(0, if (colossal) 5 else 4, -2), false, if (colossal) 8 else 7, if (colossal) 5 else 4)
                placeCablePod(world, chunkPos, ground.add(4, if (colossal) 6 else 5, 0))
                placeCablePod(world, chunkPos, ground.add(-4, if (colossal) 6 else 5, 1))
                true
            }
            ExoticAgeThemes.FRACTAL_CUBES -> {
                paintDisk(world, chunkPos, ground, if (colossal) 6 else 5, Blocks.SMOOTH_STONE, Blocks.LIGHT_GRAY_CONCRETE)
                placeFractalCluster(world, chunkPos, ground.add(0, if (colossal) 8 else 6, 0), if (colossal) 8 else 7, true)
                true
            }
            ExoticAgeThemes.LIGHT_FISSURES -> {
                paintDisk(world, chunkPos, ground, if (colossal) 6 else 5, Blocks.CALCITE, Blocks.SMOOTH_BASALT)
                placeLightFissure(world, chunkPos, ground.add(0, 1, 0), (if (colossal) 18 else 14) + rand.nextInt(8))
                true
            }
            ExoticAgeThemes.VIRUS -> {
                paintDisk(world, chunkPos, ground, if (colossal) 6 else 5, Blocks.PURPLE_CONCRETE, Blocks.BLACKSTONE)
                if (rand.nextBoolean()) {
                    placeVirusTower(world, chunkPos, ground.add(0, 1, 0), if (colossal) 10 else 8, if (colossal) 5 else 4, true)
                } else {
                    placeVirusClump(world, chunkPos, ground.add(0, if (colossal) 5 else 4, 0), if (colossal) 4 else 3, true)
                }
                true
            }
            else -> false
        }
    }

    private fun placeHexBush(world: StructureWorldAccess, chunkPos: ChunkPos, base: BlockPos, height: Int) {
        for (y in 0 until height) {
            setBlock(world, chunkPos, base.add(0, y, 0), Blocks.LIGHT_GRAY_STAINED_GLASS)
            if (y % 2 == 1) {
                setBlock(world, chunkPos, base.add(1, y, 0), Blocks.IRON_BARS)
                setBlock(world, chunkPos, base.add(-1, y, 0), Blocks.IRON_BARS)
            }
        }
        setBlock(world, chunkPos, base.add(0, height, 0), Blocks.HONEYCOMB_BLOCK)
        setBlock(world, chunkPos, base.add(1, height, 0), Blocks.HONEYCOMB_BLOCK)
        setBlock(world, chunkPos, base.add(-1, height, 0), Blocks.HONEYCOMB_BLOCK)
    }

    private fun placeWireCube(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, radius: Int, frame: Block, core: Block) {
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                for (dz in -radius..radius) {
                    val edgeCount = listOf(abs(dx) == radius, abs(dy) == radius, abs(dz) == radius).count { it }
                    if (edgeCount >= 2) {
                        setBlock(world, chunkPos, center.add(dx, dy, dz), frame)
                    }
                }
            }
        }
        setBlock(world, chunkPos, center, core)
    }

    private fun placeWireSpan(world: StructureWorldAccess, chunkPos: ChunkPos, start: BlockPos, end: BlockPos, block: Block) {
        val steps = max(max(abs(end.x - start.x), abs(end.y - start.y)), abs(end.z - start.z)).coerceAtLeast(1)
        for (step in 0..steps) {
            val t = step.toDouble() / steps.toDouble()
            val x = lerp(start.x.toDouble(), end.x.toDouble(), t).roundToInt()
            val y = lerp(start.y.toDouble(), end.y.toDouble(), t).roundToInt()
            val z = lerp(start.z.toDouble(), end.z.toDouble(), t).roundToInt()
            setBlock(world, chunkPos, BlockPos(x, y, z), block)
        }
    }

    private fun placeSeparator(world: StructureWorldAccess, chunkPos: ChunkPos, base: BlockPos, height: Int) {
        for (y in 0 until height) {
            val pos = base.add(0, y, 0)
            setBlock(world, chunkPos, pos, Blocks.POLISHED_BLACKSTONE)
            setBlock(world, chunkPos, pos.add(1, 0, 0), Blocks.SHROOMLIGHT)
            if (y % 3 == 0) {
                setBlock(world, chunkPos, pos.add(0, 0, 1), Blocks.POLISHED_BLACKSTONE_BRICKS)
                setBlock(world, chunkPos, pos.add(0, 0, -1), Blocks.POLISHED_BLACKSTONE_BRICKS)
            }
        }
        setBlock(world, chunkPos, base.add(0, height, 0), Blocks.CRYING_OBSIDIAN)
    }

    private fun placeCableBundle(world: StructureWorldAccess, chunkPos: ChunkPos, base: BlockPos, alongX: Boolean, length: Int, arcHeight: Int) {
        for (step in -length..length) {
            val t = (step + length).toDouble() / (length * 2).toDouble()
            val rise = sin(t * Math.PI).roundToInt() * arcHeight
            val center = if (alongX) base.add(step, rise, 0) else base.add(0, rise, step)
            for (dx in -1..1) {
                for (dz in -1..1) {
                    if (abs(dx) + abs(dz) <= 1) {
                        val target = if (alongX) center.add(0, 0, dz) else center.add(dx, 0, 0)
                        setBlock(world, chunkPos, target, Blocks.BLACK_CONCRETE)
                    }
                }
            }
        }
    }

    private fun placeCablePod(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos) {
        for (dx in -1..1) {
            for (dy in -1..1) {
                for (dz in -1..1) {
                    val shell = abs(dx) + abs(dy) + abs(dz) >= 2
                    val block = if (shell) Blocks.TINTED_GLASS else Blocks.SEA_LANTERN
                    setBlock(world, chunkPos, center.add(dx, dy, dz), block)
                }
            }
        }
    }

    private fun placeFractalCluster(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, size: Int, major: Boolean) {
        placeFloatingCube(world, chunkPos, center, size, Blocks.LIGHT_GRAY_CONCRETE, Blocks.AMETHYST_BLOCK)
        placeFloatingCube(world, chunkPos, center.add(size - 1, size - 2, 0), size / 2, Blocks.CALCITE, Blocks.SEA_LANTERN)
        placeFloatingCube(world, chunkPos, center.add(-(size - 1), size - 1, 1), size / 2, Blocks.CALCITE, Blocks.SEA_LANTERN)
        if (major) {
            placeFloatingCube(world, chunkPos, center.add(0, size, size - 1), size / 2, Blocks.LIGHT_GRAY_CONCRETE, Blocks.SEA_LANTERN)
            placeWireSpan(world, chunkPos, center, center.add(size - 1, size - 2, 0), Blocks.CHAIN)
            placeWireSpan(world, chunkPos, center, center.add(-(size - 1), size - 1, 1), Blocks.CHAIN)
        }
    }

    private fun placeFloatingCube(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, radius: Int, frame: Block, core: Block) {
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                for (dz in -radius..radius) {
                    val edgeCount = listOf(abs(dx) == radius, abs(dy) == radius, abs(dz) == radius).count { it }
                    if (edgeCount >= 2) {
                        setBlock(world, chunkPos, center.add(dx, dy, dz), frame)
                    }
                }
            }
        }
        setBlock(world, chunkPos, center, core)
    }

    private fun placeLightFissure(world: StructureWorldAccess, chunkPos: ChunkPos, base: BlockPos, height: Int) {
        for (arm in 0 until 6) {
            val angle = arm * (Math.PI / 3.0)
            for (step in 0..3) {
                val x = (cos(angle) * step).roundToInt()
                val z = (sin(angle) * step).roundToInt()
                setBlock(world, chunkPos, base.add(x, 0, z), Blocks.CALCITE)
                if (step > 0) {
                    setBlock(world, chunkPos, base.add(x, 1, z), Blocks.SMOOTH_BASALT)
                }
            }
        }

        for (y in 0 until height) {
            val beamBlock = if (y % 3 == 0) Blocks.OCHRE_FROGLIGHT else Blocks.END_ROD
            setBlock(world, chunkPos, base.add(0, y, 0), beamBlock)
            if (y > 2 && y % 4 == 0) {
                setBlock(world, chunkPos, base.add(1, y, 0), Blocks.CALCITE)
                setBlock(world, chunkPos, base.add(-1, y, 0), Blocks.CALCITE)
            }
        }
    }

    private fun placeVirusTower(world: StructureWorldAccess, chunkPos: ChunkPos, base: BlockPos, stemHeight: Int, headRadius: Int, withLoot: Boolean) {
        val stemTop = base.add(0, stemHeight, 0)

        for (y in 0 until stemHeight) {
            val ring = if (y % 2 == 0) Blocks.QUARTZ_PILLAR else Blocks.SMOOTH_QUARTZ
            setBlock(world, chunkPos, base.add(0, y, 0), ring)
        }

        val legTips = listOf(
            BlockPos(-4, 0, -2), BlockPos(4, 0, -2),
            BlockPos(-5, 0, 2), BlockPos(5, 0, 2),
            BlockPos(-2, 0, 5), BlockPos(2, 0, 5)
        )
        for (tip in legTips) {
            placeWireSpan(world, chunkPos, base.add(0, 2, 0), base.add(tip.x, 0, tip.z), Blocks.PURPLE_CONCRETE)
        }

        for (dx in -headRadius..headRadius) {
            for (dy in -headRadius..headRadius) {
                for (dz in -headRadius..headRadius) {
                    val dist = abs(dx) + abs(dy) + abs(dz)
                    val pos = stemTop.add(dx, dy, dz)
                    if (dist > headRadius + 1) continue
                    if (dist >= headRadius) {
                        val faceCount = listOf(abs(dx) == headRadius, abs(dy) == headRadius, abs(dz) == headRadius).count { it }
                        val shell = if (faceCount >= 2) Blocks.PURPLE_CONCRETE else Blocks.LIGHT_BLUE_STAINED_GLASS
                        setBlock(world, chunkPos, pos, shell)
                    } else if (withLoot && dx == 0 && dy == 0 && dz == 0) {
                        placeLootChest(world, chunkPos, pos, 1 + world.random.nextInt(2))
                    } else if (abs(dx) + abs(dz) <= 1 && dy <= 0) {
                        setBlock(world, chunkPos, pos, Blocks.AIR)
                    }
                }
            }
        }
    }

    private fun placeVirusClump(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, radius: Int, withLoot: Boolean) {
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                for (dz in -radius..radius) {
                    val distSq = dx * dx + dy * dy + dz * dz
                    val pos = center.add(dx, dy, dz)
                    if (distSq > radius * radius) continue
                    if (distSq >= (radius - 1) * (radius - 1)) {
                        setBlock(world, chunkPos, pos, Blocks.MAGENTA_CONCRETE)
                    } else if (withLoot && dx == 0 && dy == 0 && dz == 0) {
                        placeLootChest(world, chunkPos, pos, 1 + world.random.nextInt(2))
                    } else if (distSq >= (radius - 2) * (radius - 2)) {
                        setBlock(world, chunkPos, pos, Blocks.LIGHT_BLUE_STAINED_GLASS)
                    } else {
                        setBlock(world, chunkPos, pos, Blocks.AIR)
                    }
                }
            }
        }

        val spikeDirs = listOf(
            BlockPos(radius + 1, 0, 0), BlockPos(-(radius + 1), 0, 0),
            BlockPos(0, 0, radius + 1), BlockPos(0, 0, -(radius + 1)),
            BlockPos(radius, radius / 2, 0), BlockPos(-radius, radius / 2, 0),
            BlockPos(0, radius / 2, radius), BlockPos(0, radius / 2, -radius)
        )
        for (tip in spikeDirs) {
            placeWireSpan(world, chunkPos, center.add(0, 0, 0), center.add(tip.x, tip.y, tip.z), Blocks.PURPLE_CONCRETE)
        }
    }

    private fun placeMegaVirusStructure(world: StructureWorldAccess, chunkPos: ChunkPos, seed: Long): Boolean {
        val regionSize = 14
        val regionX = Math.floorDiv(chunkPos.x, regionSize)
        val regionZ = Math.floorDiv(chunkPos.z, regionSize)
        val regionSeed = seed +
            regionX.toLong() * 4_139_517_821L +
            regionZ.toLong() * 7_912_145_113L +
            0x51F15A2EL
        val rand = java.util.Random(regionSeed)
        if (rand.nextInt(3) != 0) return false

        val centerChunkX = regionX * regionSize + 3 + rand.nextInt(regionSize - 6)
        val centerChunkZ = regionZ * regionSize + 3 + rand.nextInt(regionSize - 6)
        val megaTower = rand.nextBoolean()
        val humongous = rand.nextInt(4) == 0
        if (chunkPos.x != centerChunkX || chunkPos.z != centerChunkZ) return false

        val centerX = centerChunkX * 16 + 8
        val centerZ = centerChunkZ * 16 + 8
        val ground = getGround(world, centerX, centerZ) ?: return false
        val center = BlockPos(centerX, ground.y + 3, centerZ)
        return if (megaTower) {
            placeMegaVirusTower(world, chunkPos, center, humongous, rand)
        } else {
            placeMegaVirusColony(world, chunkPos, center, humongous, rand)
        }
    }

    private fun placeMegaVirusTower(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        humongous: Boolean,
        rand: java.util.Random
    ): Boolean {
        val shaftRadius = if (humongous) 7 else 5
        val shaftHeight = if (humongous) 56 else 38
        val headRadius = if (humongous) 18 else 13
        val headHeight = if (humongous) 24 else 17
        val collarY = center.y
        val headBaseY = collarY + shaftHeight

        for (y in 0..shaftHeight) {
            val tubeCenter = center.add(0, y, 0)
            for (dx in -(shaftRadius + 1)..(shaftRadius + 1)) {
                for (dz in -(shaftRadius + 1)..(shaftRadius + 1)) {
                    val dist = sqrt((dx * dx + dz * dz).toDouble())
                    val pos = tubeCenter.add(dx, 0, dz)
                    when {
                        dist <= shaftRadius - 2.0 -> {
                            if ((dx + dz + y) % 7 == 0) setBlock(world, chunkPos, pos, Blocks.MAGENTA_CONCRETE)
                            else setBlock(world, chunkPos, pos, Blocks.AIR)
                        }
                        dist <= shaftRadius - 0.45 -> setBlock(world, chunkPos, pos, Blocks.LIGHT_BLUE_STAINED_GLASS)
                        dist <= shaftRadius + 0.9 -> setBlock(world, chunkPos, pos, if ((dx + dz + y) % 4 == 0) Blocks.BLACK_CONCRETE else Blocks.POLISHED_BLACKSTONE)
                    }
                }
            }
        }

        for (ringY in -2..2) {
            val radius = shaftRadius + 3
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    val dist = sqrt((dx * dx + dz * dz).toDouble())
                    if (dist in (radius - 1.0)..(radius + 0.2)) {
                        setBlock(world, chunkPos, center.add(dx, ringY, dz), Blocks.BLACK_CONCRETE)
                    }
                }
            }
        }

        val legLength = if (humongous) 26 else 18
        val legAnchors = listOf(
            BlockPos(legLength, -2, 0),
            BlockPos(-legLength, -2, 0),
            BlockPos(0, -2, legLength),
            BlockPos(0, -2, -legLength),
            BlockPos(legLength - 5, -4, legLength - 5),
            BlockPos(-(legLength - 5), -4, -(legLength - 5)),
            BlockPos(legLength - 5, -4, -(legLength - 5)),
            BlockPos(-(legLength - 5), -4, legLength - 5)
        )
        for (tip in legAnchors) {
            buildMegaLeg(world, chunkPos, center.add(0, 4, 0), center.add(tip.x / 2, tip.y + 8, tip.z / 2), center.add(tip.x, tip.y, tip.z), humongous)
        }

        val drillHeight = if (humongous) 26 else 18
        for (step in 0..drillHeight) {
            val radius = (((drillHeight - step).toFloat() / drillHeight.toFloat()) * (if (humongous) 5.0f else 3.5f)).roundToInt().coerceAtLeast(1)
            val y = center.y - step - 2
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (abs(dx) + abs(dz) > radius + 1) continue
                    val block = if ((dx == 0 && dz == 0) || (step + dx + dz) % 4 == 0) Blocks.CRYING_OBSIDIAN else if (step % 3 == 0) Blocks.POLISHED_BLACKSTONE_BRICKS else Blocks.BLACKSTONE
                    setBlock(world, chunkPos, BlockPos(center.x + dx, y, center.z + dz), block)
                }
            }
        }

        val surfaceY = getGround(world, center.x, center.z)?.y ?: (center.y - 1)
        for (y in (surfaceY + 1)..(center.y - 1)) {
            for (dx in -shaftRadius..shaftRadius) {
                for (dz in -shaftRadius..shaftRadius) {
                    if (sqrt((dx * dx + dz * dz).toDouble()) <= shaftRadius - 0.2) {
                        setBlock(world, chunkPos, BlockPos(center.x + dx, y, center.z + dz), if ((y + dx + dz) % 3 == 0) Blocks.BLACK_CONCRETE else Blocks.POLISHED_BLACKSTONE)
                    }
                }
            }
        }

        for (dy in 0..headHeight) {
            val t = dy.toFloat() / headHeight.toFloat()
            val width = when {
                t < 0.15f -> shaftRadius + 3 + (headRadius - shaftRadius - 3) * (t / 0.15f)
                t < 0.8f -> headRadius.toFloat()
                else -> headRadius.toFloat() * (1.0f - ((t - 0.8f) / 0.2f) * 0.6f)
            }
            val yPos = headBaseY + dy
            for (dx in -(headRadius + 1)..(headRadius + 1)) {
                for (dz in -(headRadius + 1)..(headRadius + 1)) {
                    val shape = max(abs(dx).toFloat(), abs(dz).toFloat()) + (abs(dx + dz).toFloat() * 0.18f)
                    if (shape > width + 0.8f) continue
                    val pos = BlockPos(center.x + dx, yPos, center.z + dz)
                    when {
                        shape >= width - 1.0f -> {
                            val shell = if (shape >= width - 0.25f) Blocks.MAGENTA_CONCRETE else Blocks.PINK_STAINED_GLASS
                            setBlock(world, chunkPos, pos, shell)
                        }
                        dy > 2 && dy < headHeight - 2 -> setBlock(world, chunkPos, pos, Blocks.AIR)
                    }
                }
            }
        }

        val chamberCenter = BlockPos(center.x, headBaseY + headHeight / 2, center.z)
        setBlock(world, chunkPos, chamberCenter.add(0, 0, 0), Blocks.AIR)
        setBlock(world, chunkPos, chamberCenter.add(1, 0, 0), Blocks.AIR)
        setBlock(world, chunkPos, chamberCenter.add(-1, 0, 0), Blocks.AIR)
        if (rand.nextBoolean()) placeLootChest(world, chunkPos, chamberCenter, 1 + rand.nextInt(2))
        if (humongous) placeLootChest(world, chunkPos, chamberCenter.add(0, -2, 0), 1 + rand.nextInt(2))

        for (tip in 0..3) {
            val pointY = headBaseY + headHeight + tip
            val radius = (3 - tip).coerceAtLeast(0)
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (abs(dx) + abs(dz) > radius + 1) continue
                    setBlock(world, chunkPos, BlockPos(center.x + dx, pointY, center.z + dz), if (tip == 3) Blocks.REDSTONE_BLOCK else Blocks.RED_STAINED_GLASS)
                }
            }
        }

        return true
    }

    private fun placeMegaVirusColony(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        humongous: Boolean,
        rand: java.util.Random
    ): Boolean {
        val radius = if (humongous) 18 else 13
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                for (dz in -radius..radius) {
                    val dist = sqrt((dx * dx + dy * dy + dz * dz).toDouble())
                    if (dist > radius + 0.5) continue
                    val pos = center.add(dx, dy, dz)
                    when {
                        dist >= radius - 1.2 -> setBlock(world, chunkPos, pos, Blocks.MAGENTA_CONCRETE)
                        dist >= radius - 3.0 -> setBlock(world, chunkPos, pos, Blocks.PINK_STAINED_GLASS)
                        else -> setBlock(world, chunkPos, pos, Blocks.AIR)
                    }
                }
            }
        }

        val armCount = if (humongous) 20 else 14
        repeat(armCount) {
            val angle = rand.nextDouble() * Math.PI * 2.0
            val rise = rand.nextInt(-2, 5)
            val length = if (humongous) 11 + rand.nextInt(6) else 7 + rand.nextInt(4)
            val tip = center.add((cos(angle) * length).roundToInt(), rise, (sin(angle) * length).roundToInt())
            placeWireSpan(world, chunkPos, center, tip, Blocks.PURPLE_CONCRETE)
            setBlock(world, chunkPos, tip, Blocks.MAGENTA_CONCRETE)
        }

        val chamber = center.add(0, 0, 0)
        placeLootChest(world, chunkPos, chamber, 1 + rand.nextInt(2))
        if (humongous) placeLootChest(world, chunkPos, chamber.add(0, -2, 0), 1 + rand.nextInt(2))
        return true
    }

    private fun placeLegToGround(world: StructureWorldAccess, chunkPos: ChunkPos, anchor: BlockPos) {
        if ((anchor.x shr 4) != chunkPos.x || (anchor.z shr 4) != chunkPos.z) return
        val ground = getGround(world, anchor.x, anchor.z) ?: return
        val top = anchor.y.coerceAtMost(world.topY - 1)
        for (y in ground.y + 1..top) {
            val block = if ((y - ground.y) % 4 == 0) Blocks.POLISHED_BLACKSTONE else Blocks.BLACK_CONCRETE
            setBlock(world, chunkPos, BlockPos(anchor.x, y, anchor.z), block)
        }
    }

    private fun buildMegaLeg(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        start: BlockPos,
        joint: BlockPos,
        tip: BlockPos,
        humongous: Boolean
    ) {
        placeThickStrut(world, chunkPos, start, joint, if (humongous) 2 else 1)
        placeThickStrut(world, chunkPos, joint, tip, if (humongous) 2 else 1)
        placeClawPad(world, chunkPos, tip, humongous)
    }

    private fun placeThickStrut(world: StructureWorldAccess, chunkPos: ChunkPos, start: BlockPos, end: BlockPos, radius: Int) {
        val steps = max(max(abs(end.x - start.x), abs(end.y - start.y)), abs(end.z - start.z)).coerceAtLeast(1)
        for (step in 0..steps) {
            val t = step.toDouble() / steps.toDouble()
            val x = lerp(start.x.toDouble(), end.x.toDouble(), t).roundToInt()
            val y = lerp(start.y.toDouble(), end.y.toDouble(), t).roundToInt()
            val z = lerp(start.z.toDouble(), end.z.toDouble(), t).roundToInt()
            for (dx in -radius..radius) {
                for (dy in -radius..radius) {
                    for (dz in -radius..radius) {
                        if (abs(dx) + abs(dy) + abs(dz) > radius + 1) continue
                        val shell = if (abs(dx) + abs(dy) + abs(dz) >= radius) Blocks.BLACK_CONCRETE else Blocks.POLISHED_BLACKSTONE
                        setBlock(world, chunkPos, BlockPos(x + dx, y + dy, z + dz), shell)
                    }
                }
            }
        }
    }

    private fun placeClawPad(world: StructureWorldAccess, chunkPos: ChunkPos, tip: BlockPos, humongous: Boolean) {
        val span = if (humongous) 3 else 2
        for (dx in -span..span) {
            for (dz in -span..span) {
                if (abs(dx) + abs(dz) > span + 1) continue
                setBlock(world, chunkPos, BlockPos(tip.x + dx, tip.y, tip.z + dz), Blocks.BLACKSTONE)
                setBlock(world, chunkPos, BlockPos(tip.x + dx, tip.y - 1, tip.z + dz), Blocks.POLISHED_BLACKSTONE)
            }
        }
        setBlock(world, chunkPos, tip.add(span + 1, -1, 0), Blocks.BLACKSTONE)
        setBlock(world, chunkPos, tip.add(-(span + 1), -1, 0), Blocks.BLACKSTONE)
        setBlock(world, chunkPos, tip.add(0, -1, span + 1), Blocks.BLACKSTONE)
    }

    private fun paintHexPatch(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, radius: Int, fill: Block, border: Block) {
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                val hexDist = max(max(abs(dx), abs(dz)), abs(dx + dz))
                if (hexDist > radius) continue
                val ground = getGround(world, center.x + dx, center.z + dz) ?: continue
                val block = if (hexDist == radius) border else fill
                setBlock(world, chunkPos, ground, block)
            }
        }
    }

    private fun paintDisk(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, radius: Int, border: Block, fill: Block) {
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                val dist = dx * dx + dz * dz
                if (dist > radius * radius) continue
                val ground = getGround(world, center.x + dx, center.z + dz) ?: continue
                val block = if (dist >= (radius - 1) * (radius - 1)) border else fill
                setBlock(world, chunkPos, ground, block)
            }
        }
    }

    private fun getGround(world: StructureWorldAccess, x: Int, z: Int): BlockPos? {
        return FeatureBuildHelper.findGround(world, x, z)
    }

    private fun setBlock(world: StructureWorldAccess, chunkPos: ChunkPos, pos: BlockPos, block: Block) {
        setBlockState(world, chunkPos, pos, block.defaultState)
    }

    private fun setBlockState(world: StructureWorldAccess, chunkPos: ChunkPos, pos: BlockPos, state: BlockState) {
        if (pos.y < world.bottomY || pos.y >= world.topY) return
        if ((pos.x shr 4) != chunkPos.x || (pos.z shr 4) != chunkPos.z) {
            DeferredTreePlacer.add(world.toServerWorld(), pos, state, !state.isAir)
            return
        }

        val existing = world.getBlockState(pos)
        if (existing.getHardness(world, pos) < 0.0f) return
        if (state.isAir) {
            world.setBlockState(pos, state, 2)
            return
        }

        val canReplace = existing.isAir || existing.isReplaceable || existing.isOf(Blocks.GRASS_BLOCK) ||
            existing.isOf(Blocks.DIRT) || existing.isOf(Blocks.STONE) || existing.isOf(Blocks.SAND) ||
            existing.isOf(Blocks.GRAVEL) || existing.isOf(Blocks.SNOW_BLOCK) || existing.isOf(Blocks.SNOW)

        if (!canReplace && existing.block != state.block) return

        world.setBlockState(pos, state, 2)
    }

    private fun placeLootChest(world: StructureWorldAccess, chunkPos: ChunkPos, pos: BlockPos, pageCount: Int) {
        if ((pos.x shr 4) != chunkPos.x || (pos.z shr 4) != chunkPos.z) {
            DeferredTreePlacer.add(world.toServerWorld(), pos, Blocks.CHEST.defaultState.with(ChestBlock.FACING, Direction.NORTH), false)
            return
        }
        setBlockState(world, chunkPos, pos, Blocks.CHEST.defaultState.with(ChestBlock.FACING, Direction.NORTH))
        val chest = world.getBlockEntity(pos) as? ChestBlockEntity ?: return
        repeat(FeatureBuildHelper.configuredLostPageCount(world.random, pageCount, pageCount)) {
            chest.setStack(world.random.nextInt(chest.size()), net.minecraft.item.ItemStack(ModItems.LOST_PAGE))
        }
    }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
}
