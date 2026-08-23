package mystcraft.flood.generation

import com.mojang.serialization.Codec
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.generation.profile.TerrainType
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.Direction
import net.minecraft.world.StructureWorldAccess
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.util.FeatureContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Generates seeded archaeological remains as an optional historic Age landmark. */
class AncientRemainsFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {

    override fun generate(context: FeatureContext<DefaultFeatureConfig>): Boolean {
        val world = context.world
        val origin = context.origin
        val serverWorld = world.toServerWorld()
        val ageId = serverWorld.registryKey.value

        if (!AgeSubdimensionManager.isPrimaryAgeRealm(ageId)) return false

        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, ageId)
        if (AgeLifecycleManager.isDeadAge(profile)) return false
        if (profile.terrainType == TerrainType.BIOSPHERES) return false

        val explicit = profile.modifiers.contains(HistoricAgeThemes.ANCIENT_BONES)
        val instability = profile.stability.instabilityScore
        val spawnChance = when {
            explicit -> 0.95f
            instability >= 80 -> 0.72f
            instability >= 50 -> 0.42f
            instability >= 28 -> 0.18f
            else -> 0.0f
        } * AgeFeatureTuning.chanceMultiplier(profile, HistoricAgeThemes.ANCIENT_BONES)
        if (spawnChance <= 0f) return false
        CustomStructureOverrides.generateIfPresent(context, "ancient_remains", profile, spawnChance)?.let { return it }

        val chunkPos = ChunkPos(origin)
        val regionSize = if (explicit) 9 else 12
        val regionX = Math.floorDiv(chunkPos.x, regionSize)
        val regionZ = Math.floorDiv(chunkPos.z, regionSize)
        val regionSeed = profile.seed +
            regionX.toLong() * 5_312_441_927L +
            regionZ.toLong() * 7_182_144_331L +
            0xB0BE5EEDL
        val rand = java.util.Random(regionSeed)
        if (rand.nextFloat() > spawnChance) return false

        val ownerChunkX = regionX * regionSize + 1 + rand.nextInt((regionSize - 2).coerceAtLeast(1))
        val ownerChunkZ = regionZ * regionSize + 1 + rand.nextInt((regionSize - 2).coerceAtLeast(1))
        if (chunkPos.x != ownerChunkX || chunkPos.z != ownerChunkZ) return false
        if (!AgeFeatureTuning.canPlaceMajorFeature(profile, ChunkPos(ownerChunkX, ownerChunkZ), HistoricAgeThemes.ANCIENT_BONES, 10)) return false

        val centerX = ownerChunkX * 16 + 8 + rand.nextInt(7) - 3
        val centerZ = ownerChunkZ * 16 + 8 + rand.nextInt(7) - 3
        val ground = getGround(world, centerX, centerZ) ?: return false
        val center = BlockPos(centerX, ground.y + 1, centerZ)
        val orientation = if (rand.nextBoolean()) Direction.Axis.X else Direction.Axis.Z

        return when (rand.nextInt(100)) {
            in 0..20 -> {
                placeSpineRemains(world, chunkPos, center, orientation, 18 + rand.nextInt(12), 4, 5 + rand.nextInt(3))
                true
            }
            in 21..43 -> {
                placeRibCage(world, chunkPos, center, orientation, 12 + rand.nextInt(6), 8 + rand.nextInt(4), 8 + rand.nextInt(5))
                true
            }
            in 44..57 -> {
                placeSkull(world, chunkPos, center, orientation, 8 + rand.nextInt(4))
                true
            }
            in 58..74 -> {
                placeLeviathan(world, chunkPos, center, orientation, 26 + rand.nextInt(12))
                true
            }
            in 75..89 -> {
                placeColossus(world, chunkPos, center, orientation, rand.nextInt(4) == 0)
                true
            }
            in 90..96 -> {
                placeVertebraeField(world, chunkPos, center, orientation, 6 + rand.nextInt(4))
                true
            }
            else -> {
                placeTitanGraveyard(world, chunkPos, center, orientation, rand.nextBoolean())
                true
            }
        }
    }

    private fun placeSpineRemains(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        axis: Direction.Axis,
        length: Int,
        ribSpacing: Int,
        ribHeight: Int
    ) {
        val half = length / 2
        var previousSpine: BlockPos? = null
        for (step in -half..half) {
            val sineLift = sin((step + half).toDouble() / length.toDouble() * PI).roundToInt()
            val spine = offset(center, axis, step, sineLift, 0)
            previousSpine?.let { drawBoneLine(world, chunkPos, it, spine, bone(axis)) }
            setBlockState(world, chunkPos, spine, bone(axis))
            previousSpine = spine
            if (step % ribSpacing == 0 && abs(step) < half - 2) {
                placeRibPair(world, chunkPos, spine, axis, 3 + abs(step) % 3, ribHeight)
            }
        }
        scatterBoneDebris(world, chunkPos, center, 9, 14)
    }

    private fun placeRibCage(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        axis: Direction.Axis,
        length: Int,
        width: Int,
        height: Int
    ) {
        val half = length / 2
        for (step in -half..half) {
            val spinePos = offset(center, axis, step, 0, 0)
            setBlockState(world, chunkPos, spinePos, bone(axis))
            if (step % 2 == 0) {
                val ribWidth = width - abs(step) / 2
                if (ribWidth >= 3) {
                    placeRibPair(world, chunkPos, spinePos, axis, ribWidth, height - abs(step) / 3)
                }
            }
        }
        scatterBoneDebris(world, chunkPos, center, width + 4, 20)
    }

    private fun placeSkull(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        axis: Direction.Axis,
        size: Int
    ) {
        val width = size
        val depth = (size * 0.8f).roundToInt()
        val height = (size * 0.7f).roundToInt()

        for (dx in -width..width) {
            for (dy in -height..height) {
                for (dz in -depth..depth) {
                    val nx = dx.toDouble() / width.toDouble()
                    val ny = dy.toDouble() / height.toDouble()
                    val nz = dz.toDouble() / depth.toDouble()
                    val dist = nx * nx + ny * ny + nz * nz
                    if (dist > 1.08) continue

                    val pos = if (axis == Direction.Axis.X) center.add(dx, dy, dz) else center.add(dz, dy, dx)
                    if (dist >= 0.72) {
                        setBlockState(world, chunkPos, pos, bone(if (abs(dx) > abs(dz)) Direction.Axis.X else Direction.Axis.Z))
                    } else {
                        setBlockState(world, chunkPos, pos, Blocks.AIR.defaultState)
                    }
                }
            }
        }

        val eyeOffset = size / 3
        val eyeY = center.y + height / 4
        val jawY = center.y - height / 2
        val eyeLeft = if (axis == Direction.Axis.X) BlockPos(center.x - eyeOffset, eyeY, center.z - 2) else BlockPos(center.x - 2, eyeY, center.z - eyeOffset)
        val eyeRight = if (axis == Direction.Axis.X) BlockPos(center.x + eyeOffset, eyeY, center.z - 2) else BlockPos(center.x - 2, eyeY, center.z + eyeOffset)
        carvePocket(world, chunkPos, eyeLeft, 2)
        carvePocket(world, chunkPos, eyeRight, 2)

        for (step in -width / 2..width / 2) {
            val tooth = if (step % 2 == 0) bone(Direction.Axis.Y) else bone(axis)
            val jawPos = if (axis == Direction.Axis.X) BlockPos(center.x + step, jawY, center.z + depth / 2) else BlockPos(center.x + depth / 2, jawY, center.z + step)
            setBlockState(world, chunkPos, jawPos, tooth)
        }
        scatterBoneDebris(world, chunkPos, center, size + 3, 18)
    }

    private fun placeLeviathan(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        axis: Direction.Axis,
        length: Int
    ) {
        placeSpineRemains(world, chunkPos, center, axis, length, 3, 6)
        val head = offset(center, axis, length / 2, 1, 0)
        placeSkull(world, chunkPos, head, axis, 7)
        placeVertebraeField(world, chunkPos, offset(center, axis, -length / 4, -1, 6), axis, 4)
    }

    private fun placeColossus(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        axis: Direction.Axis,
        humongous: Boolean
    ) {
        val ribLength = if (humongous) 20 else 15
        val ribWidth = if (humongous) 13 else 10
        val ribHeight = if (humongous) 15 else 11
        placeRibCage(world, chunkPos, center.add(0, 2, 0), axis, ribLength, ribWidth, ribHeight)

        val limbReach = if (humongous) 18 else 12
        val limbLift = if (humongous) 6 else 4
        val leftFemur = offset(center, perpendicular(axis), -limbReach / 2, 0, 0)
        val rightFemur = offset(center, perpendicular(axis), limbReach / 2, 0, 0)
        placeLongBone(world, chunkPos, leftFemur.add(0, limbLift, 0), offset(leftFemur, axis, -limbReach, 0, 0))
        placeLongBone(world, chunkPos, rightFemur.add(0, limbLift, 0), offset(rightFemur, axis, limbReach, 0, 0))
        placeLongBone(world, chunkPos, center.add(0, ribHeight / 2, 0), center.add(0, ribHeight + 6, 0))
        placeSkull(world, chunkPos, offset(center, axis, ribLength / 2 + 6, 3, 0), axis, if (humongous) 12 else 9)
        scatterBoneDebris(world, chunkPos, center, ribWidth + 8, if (humongous) 30 else 22)
    }

    private fun placeVertebraeField(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, axis: Direction.Axis, count: Int) {
        for (index in 0 until count) {
            val step = index * 4 - (count * 2)
            val vertebraCenter = offset(center, axis, step, index % 3, 0)
            placeVertebra(world, chunkPos, vertebraCenter, axis, 2 + index % 2)
        }
        scatterBoneDebris(world, chunkPos, center, count * 3, count * 3)
    }

    private fun placeTitanGraveyard(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, axis: Direction.Axis, humongous: Boolean) {
        val length = if (humongous) 38 else 30
        placeColossus(world, chunkPos, center, axis, humongous)
        placeLeviathan(world, chunkPos, offset(center, perpendicular(axis), 10, -1, 0), axis, length)
        placeVertebraeField(world, chunkPos, offset(center, perpendicular(axis), -12, 0, 0), axis, if (humongous) 10 else 7)
        placeLongBone(world, chunkPos, offset(center, axis, -8, 6, 7), offset(center, axis, 12, -1, 14))
        placeLongBone(world, chunkPos, offset(center, axis, -5, 5, -8), offset(center, axis, 10, 0, -15))
        scatterBoneDebris(world, chunkPos, center, if (humongous) 28 else 22, if (humongous) 40 else 28)
    }

    private fun placeVertebra(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, axis: Direction.Axis, radius: Int) {
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                for (dz in -radius..radius) {
                    val ring = abs(dx) + abs(dy) + abs(dz)
                    if (ring < radius || ring > radius + 1) continue
                    val pos = if (axis == Direction.Axis.X) center.add(dx, dy, dz) else center.add(dz, dy, dx)
                    if (abs(dx) <= 1 && abs(dz) <= 1 && dy == 0) continue
                    setBlockState(world, chunkPos, pos, bone(if (abs(dx) > abs(dz)) Direction.Axis.X else Direction.Axis.Z))
                }
            }
        }
    }

    private fun placeLongBone(world: StructureWorldAccess, chunkPos: ChunkPos, start: BlockPos, end: BlockPos) {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val dz = end.z - start.z
        val axis = when {
            abs(dx) >= abs(dy) && abs(dx) >= abs(dz) -> Direction.Axis.X
            abs(dz) >= abs(dx) && abs(dz) >= abs(dy) -> Direction.Axis.Z
            else -> Direction.Axis.Y
        }
        drawBoneLine(world, chunkPos, start, end, bone(axis))
        placeKnuckle(world, chunkPos, start)
        placeKnuckle(world, chunkPos, end)
    }

    private fun placeKnuckle(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos) {
        for (dx in -1..1) {
            for (dy in -1..1) {
                for (dz in -1..1) {
                    if (abs(dx) + abs(dy) + abs(dz) > 2) continue
                    setBlockState(world, chunkPos, center.add(dx, dy, dz), bone(Direction.Axis.Y))
                }
            }
        }
    }

    private fun placeRibPair(world: StructureWorldAccess, chunkPos: ChunkPos, spine: BlockPos, axis: Direction.Axis, width: Int, height: Int) {
        for (side in listOf(-1, 1)) {
            var previousPos: BlockPos? = spine
            for (step in 1..width) {
                val lift = sin(step.toDouble() / width.toDouble() * PI).times(height.toDouble()).roundToInt()
                val ribPos = offset(spine, perpendicular(axis), side * step, lift, 0)
                val ribAxis = if (axis == Direction.Axis.X) Direction.Axis.Z else Direction.Axis.X
                previousPos?.let { drawBoneLine(world, chunkPos, it, ribPos, bone(ribAxis)) }
                setBlockState(world, chunkPos, ribPos, bone(ribAxis))
                previousPos = ribPos
                if (step == width) {
                    val ground = getGround(world, ribPos.x, ribPos.z)?.y ?: spine.y
                    for (y in ground..ribPos.y) {
                        setBlockState(world, chunkPos, BlockPos(ribPos.x, y, ribPos.z), bone(Direction.Axis.Y))
                    }
                }
            }
        }
    }

    private fun carvePocket(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, radius: Int) {
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                for (dz in -radius..radius) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) continue
                    setBlockState(world, chunkPos, center.add(dx, dy, dz), Blocks.AIR.defaultState)
                }
            }
        }
    }

    private fun drawBoneLine(world: StructureWorldAccess, chunkPos: ChunkPos, start: BlockPos, end: BlockPos, state: BlockState) {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val dz = end.z - start.z
        val steps = max(max(abs(dx), abs(dy)), abs(dz)).coerceAtLeast(1)
        for (step in 0..steps) {
            val t = step.toDouble() / steps.toDouble()
            val pos = BlockPos(
                lerp(start.x.toDouble(), end.x.toDouble(), t).roundToInt(),
                lerp(start.y.toDouble(), end.y.toDouble(), t).roundToInt(),
                lerp(start.z.toDouble(), end.z.toDouble(), t).roundToInt()
            )
            setBlockState(world, chunkPos, pos, state)
        }
    }

    private fun scatterBoneDebris(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, radius: Int, count: Int) {
        val rand = java.util.Random(center.asLong())
        repeat(count) {
            val pos = BlockPos(
                center.x + rand.nextInt(radius * 2 + 1) - radius,
                center.y,
                center.z + rand.nextInt(radius * 2 + 1) - radius
            )
            val ground = getGround(world, pos.x, pos.z) ?: return@repeat
            val axis = when (rand.nextInt(3)) {
                0 -> Direction.Axis.X
                1 -> Direction.Axis.Y
                else -> Direction.Axis.Z
            }
            setBlockState(world, chunkPos, ground.up(rand.nextInt(2)), bone(axis))
        }
    }

    private fun bone(axis: Direction.Axis): BlockState = Blocks.BONE_BLOCK.defaultState.with(Properties.AXIS, axis)

    private fun offset(base: BlockPos, axis: Direction.Axis, along: Int, up: Int, sideways: Int): BlockPos {
        return when (axis) {
            Direction.Axis.X -> base.add(along, up, sideways)
            Direction.Axis.Z -> base.add(sideways, up, along)
            Direction.Axis.Y -> base.add(sideways, along, up)
        }
    }

    private fun perpendicular(axis: Direction.Axis): Direction.Axis =
        if (axis == Direction.Axis.X) Direction.Axis.Z else Direction.Axis.X

    private fun getGround(world: StructureWorldAccess, x: Int, z: Int): BlockPos? {
        return FeatureBuildHelper.findGround(world, x, z)
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
            existing.isOf(Blocks.GRAVEL) || existing.isOf(Blocks.COARSE_DIRT) || existing.isOf(Blocks.MUD) ||
            existing.isOf(Blocks.SNOW_BLOCK) || existing.isOf(Blocks.SNOW)

        if (!canReplace && existing.block != state.block) return
        world.setBlockState(pos, state, 2)
    }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
}
