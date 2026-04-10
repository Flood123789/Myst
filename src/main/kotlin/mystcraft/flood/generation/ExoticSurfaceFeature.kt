package mystcraft.flood.generation

import com.mojang.serialization.Codec
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.generation.profile.TerrainType
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.Heightmap
import net.minecraft.world.StructureWorldAccess
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.util.FeatureContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

class ExoticSurfaceFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {

    override fun generate(context: FeatureContext<DefaultFeatureConfig>): Boolean {
        val world = context.world
        val origin = context.origin
        val serverWorld = world.toServerWorld()
        val ageId = serverWorld.registryKey.value

        if (ageId.namespace != MystcraftReforged.MOD_ID) return false

        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, ageId)
        val theme = ExoticAgeThemes.fromModifiers(profile.modifiers) ?: return false

        if (profile.terrainType == TerrainType.CITIES || profile.terrainType == TerrainType.BIOSPHERES) return false

        val chunkPos = ChunkPos(origin)
        val seededRand = java.util.Random(profile.seed + chunkPos.x.toLong() * 341_873_128_712L + chunkPos.z.toLong() * 132_897_987_541L + theme.hashCode())
        if (!shouldPopulateChunk(theme, chunkPos, profile.seed, seededRand)) return false

        var placed = false

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

    private fun shouldPopulateChunk(theme: String, chunkPos: ChunkPos, seed: Long, rand: java.util.Random): Boolean {
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
            else -> 1
        }

        return rand.nextInt(rarity) == 0
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
            else -> false
        }
    }

    private fun placeMajor(theme: String, world: StructureWorldAccess, chunkPos: ChunkPos, ground: BlockPos, rand: java.util.Random): Boolean {
        return when (theme) {
            ExoticAgeThemes.HEX -> {
                paintHexPatch(world, chunkPos, ground, 5, Blocks.HONEYCOMB_BLOCK, Blocks.WAXED_COPPER_BLOCK)
                for (dx in -2..2 step 2) {
                    placeHexBush(world, chunkPos, ground.add(dx, 1, rand.nextInt(5) - 2), 6 + rand.nextInt(4))
                }
                true
            }
            ExoticAgeThemes.WIRE_CELLS -> {
                paintDisk(world, chunkPos, ground, 5, Blocks.BLACK_CONCRETE, Blocks.SCULK)
                placeWireCube(world, chunkPos, ground.add(0, 3, 0), 3, Blocks.CYAN_STAINED_GLASS, Blocks.SEA_LANTERN)
                placeWireCube(world, chunkPos, ground.add(-4, 5, 0), 2, Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.END_STONE)
                placeWireCube(world, chunkPos, ground.add(4, 4, 1), 2, Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.END_STONE)
                placeWireSpan(world, chunkPos, ground.add(-4, 5, 0), ground.add(0, 3, 0), Blocks.CHAIN)
                placeWireSpan(world, chunkPos, ground.add(4, 4, 1), ground.add(0, 3, 0), Blocks.CHAIN)
                true
            }
            ExoticAgeThemes.SEPARATORS -> {
                placeSeparator(world, chunkPos, ground.add(0, 1, 0), 18 + rand.nextInt(10))
                placeSeparator(world, chunkPos, ground.add(-4, 1, 2), 12 + rand.nextInt(6))
                placeSeparator(world, chunkPos, ground.add(4, 1, -2), 12 + rand.nextInt(6))
                true
            }
            ExoticAgeThemes.CABLES -> {
                paintDisk(world, chunkPos, ground, 5, Blocks.GRAY_CONCRETE, Blocks.BLACK_CONCRETE)
                placeCableBundle(world, chunkPos, ground.add(0, 3, 0), true, 7, 4)
                placeCableBundle(world, chunkPos, ground.add(0, 4, -2), false, 7, 4)
                placeCablePod(world, chunkPos, ground.add(4, 5, 0))
                placeCablePod(world, chunkPos, ground.add(-4, 5, 1))
                true
            }
            ExoticAgeThemes.FRACTAL_CUBES -> {
                paintDisk(world, chunkPos, ground, 5, Blocks.SMOOTH_STONE, Blocks.LIGHT_GRAY_CONCRETE)
                placeFractalCluster(world, chunkPos, ground.add(0, 6, 0), 7, true)
                true
            }
            ExoticAgeThemes.LIGHT_FISSURES -> {
                paintDisk(world, chunkPos, ground, 5, Blocks.CALCITE, Blocks.SMOOTH_BASALT)
                placeLightFissure(world, chunkPos, ground.add(0, 1, 0), 14 + rand.nextInt(8))
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
        val topY = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z)
        if (topY <= world.bottomY + 1 || topY >= world.topY - 4) return null

        val ground = BlockPos(x, topY - 1, z)
        val state = world.getBlockState(ground)
        if (state.isAir || state.isOf(Blocks.WATER) || state.isOf(Blocks.LAVA)) return null
        return ground
    }

    private fun setBlock(world: StructureWorldAccess, chunkPos: ChunkPos, pos: BlockPos, block: Block) {
        if ((pos.x shr 4) != chunkPos.x || (pos.z shr 4) != chunkPos.z) return
        if (pos.y < world.bottomY || pos.y >= world.topY) return

        val existing = world.getBlockState(pos)
        if (existing.getHardness(world, pos) < 0.0f) return

        val canReplace = existing.isAir || existing.isReplaceable || existing.isOf(Blocks.GRASS_BLOCK) ||
            existing.isOf(Blocks.DIRT) || existing.isOf(Blocks.STONE) || existing.isOf(Blocks.SAND) ||
            existing.isOf(Blocks.GRAVEL) || existing.isOf(Blocks.SNOW_BLOCK) || existing.isOf(Blocks.SNOW)

        if (!canReplace && existing.block != block) return

        world.setBlockState(pos, block.defaultState, 2)
    }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
}
