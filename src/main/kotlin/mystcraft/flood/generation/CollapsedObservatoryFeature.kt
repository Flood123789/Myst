package mystcraft.flood.generation

import com.mojang.serialization.Codec
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.generation.profile.TerrainType
import net.minecraft.block.Blocks
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.Direction
import net.minecraft.world.StructureWorldAccess
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.util.FeatureContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class CollapsedObservatoryFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {
    override fun generate(context: FeatureContext<DefaultFeatureConfig>): Boolean {
        val world = context.world
        val serverWorld = world.toServerWorld()
        val ageId = serverWorld.registryKey.value
        if (!AgeSubdimensionManager.isPrimaryAgeRealm(ageId)) return false

        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, ageId)
        if (AgeLifecycleManager.isDeadAge(profile)) return false
        if (profile.terrainType == TerrainType.BIOSPHERES || profile.terrainType == TerrainType.CITIES) return false

        val explicit = profile.modifiers.contains(HistoricAgeThemes.COLLAPSED_OBSERVATORY)
        val chance = when {
            explicit -> 0.96f
            profile.stability.instabilityScore >= 70 -> 0.38f
            profile.stability.instabilityScore >= 40 -> 0.18f
            else -> 0.0f
        } * AgeFeatureTuning.chanceMultiplier(profile, HistoricAgeThemes.COLLAPSED_OBSERVATORY)
        if (chance <= 0f) return false

        val chunkPos = ChunkPos(context.origin)
        val regionSize = if (explicit) 11 else 15
        val regionX = Math.floorDiv(chunkPos.x, regionSize)
        val regionZ = Math.floorDiv(chunkPos.z, regionSize)
        val seed = profile.seed + regionX * 7_345_123L + regionZ * 9_118_711L + 0x0B51A7EL
        val rand = java.util.Random(seed)
        if (rand.nextFloat() > chance) return false

        val ownerChunkX = regionX * regionSize + rand.nextInt(regionSize)
        val ownerChunkZ = regionZ * regionSize + rand.nextInt(regionSize)
        if (chunkPos.x != ownerChunkX || chunkPos.z != ownerChunkZ) return false
        if (!AgeFeatureTuning.canPlaceMajorFeature(profile, ChunkPos(ownerChunkX, ownerChunkZ), HistoricAgeThemes.COLLAPSED_OBSERVATORY, 11)) return false

        val centerX = ownerChunkX * 16 + 8 + rand.nextInt(7) - 3
        val centerZ = ownerChunkZ * 16 + 8 + rand.nextInt(7) - 3
        val ground = FeatureBuildHelper.findGround(world, centerX, centerZ) ?: return false
        val center = ground.up()
        val radius = 8 + rand.nextInt(4)
        val wallHeight = 5 + rand.nextInt(3)
        val chunk = ChunkPos(center)

        prepareObservatorySite(world, chunk, center, radius, wallHeight)
        buildStoneRing(world, chunk, center, radius)
        buildEntranceApproach(world, chunk, center, radius, rand)
        buildBrokenWall(world, chunk, center, radius, wallHeight)
        buildOuterButtresses(world, chunk, center, radius, wallHeight)
        buildCollapsedDome(world, chunk, center, radius, wallHeight, rand)
        buildTelescope(world, chunk, center, rand)
        placeInteriorDetails(world, chunk, center, radius, rand)
        placeLoot(world, chunk, center, radius, rand)
        scatterDebris(world, chunk, center, radius + 3, rand)
        return true
    }

    private fun prepareObservatorySite(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        radius: Int,
        wallHeight: Int
    ) {
        val clearRadius = radius + 5
        val clearHeight = wallHeight + radius + 8
        val foundationRadius = radius + 2

        for (dx in -clearRadius..clearRadius) {
            for (dz in -clearRadius..clearRadius) {
                val distanceSq = dx * dx + dz * dz
                if (distanceSq > clearRadius * clearRadius) continue

                for (y in 0..clearHeight) {
                    FeatureBuildHelper.setBlockState(world, chunkPos, center.add(dx, y, dz), Blocks.AIR.defaultState, true)
                }

                if (distanceSq <= foundationRadius * foundationRadius) {
                    for (y in -3..-1) {
                        val block = if (y == -1) Blocks.POLISHED_ANDESITE else Blocks.STONE_BRICKS
                        FeatureBuildHelper.setBlock(world, chunkPos, center.add(dx, y, dz), block, true)
                    }
                }
            }
        }
    }

    private fun buildStoneRing(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, radius: Int) {
        for (dx in -radius - 2..radius + 2) {
            for (dz in -radius - 2..radius + 2) {
                val dist = dx * dx + dz * dz
                if (dist > (radius + 2) * (radius + 2)) continue
                val pos = center.add(dx, -1, dz)
                val block = when {
                    dist >= (radius - 1) * (radius - 1) -> Blocks.POLISHED_ANDESITE
                    dist >= (radius - 3) * (radius - 3) -> Blocks.STONE_BRICKS
                    else -> Blocks.SMOOTH_STONE
                }
                FeatureBuildHelper.setBlock(world, chunkPos, pos, block, true)
            }
        }
    }

    private fun buildEntranceApproach(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        radius: Int,
        rand: java.util.Random
    ) {
        for (step in 0..9) {
            val width = if (step < 3) 3 else 2
            for (x in -width..width) {
                val pos = center.add(x, -1, radius + step)
                val block = when {
                    abs(x) == width && step % 2 == 0 -> Blocks.STONE_BRICKS
                    rand.nextFloat() < 0.18f -> Blocks.CRACKED_STONE_BRICKS
                    else -> Blocks.SMOOTH_STONE
                }
                FeatureBuildHelper.setBlock(world, chunkPos, pos, block, true)
                if (step < 4) {
                    FeatureBuildHelper.setBlockState(world, chunkPos, pos.up(), Blocks.AIR.defaultState, true)
                    FeatureBuildHelper.setBlockState(world, chunkPos, pos.up(2), Blocks.AIR.defaultState, true)
                }
            }
        }
    }

    private fun buildBrokenWall(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        radius: Int,
        wallHeight: Int
    ) {
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                val dist = dx * dx + dz * dz
                if (dist < (radius - 1) * (radius - 1) || dist > radius * radius + 3) continue
                val collapseBias = if (dx > 0) 2 else 0
                val localHeight = (wallHeight - abs(dz) / 3 - collapseBias).coerceAtLeast(2)
                for (y in 0..localHeight) {
                    val doorway = dz == radius && abs(dx) <= 1 && y <= 3
                    if (doorway) {
                        FeatureBuildHelper.setBlockState(world, chunkPos, center.add(dx, y, dz), Blocks.AIR.defaultState, true)
                        continue
                    }
                    val block = when {
                        y == localHeight && (dx + dz) % 2 == 0 -> Blocks.STONE_BRICK_SLAB
                        (dx + dz + y) % 7 == 0 -> Blocks.MOSSY_STONE_BRICKS
                        else -> Blocks.STONE_BRICKS
                    }
                    FeatureBuildHelper.setBlock(world, chunkPos, center.add(dx, y, dz), block, true)
                }
            }
        }
    }

    private fun buildOuterButtresses(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        radius: Int,
        wallHeight: Int
    ) {
        for (direction in listOf(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
            val side = if (direction.axis == Direction.Axis.X) Direction.SOUTH else Direction.EAST
            for (offset in -1..1) {
                val base = center.offset(direction, radius + 1).offset(side, offset)
                val height = if (offset == 0) wallHeight + 2 else wallHeight
                FeatureBuildHelper.fillColumn(world, chunkPos, base, height, Blocks.POLISHED_DEEPSLATE.defaultState, true)
                FeatureBuildHelper.setBlock(world, chunkPos, base.up(height), Blocks.DEEPSLATE_BRICK_SLAB, true)
            }
        }
    }

    private fun buildCollapsedDome(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        radius: Int,
        wallHeight: Int,
        rand: java.util.Random
    ) {
        val domeRadius = radius - 1
        for (angleStep in 0 until 8) {
            val angle = Math.PI * 2.0 * angleStep / 8.0
            val collapse = angleStep in 2..4
            var previous: BlockPos? = null
            for (segment in 0..domeRadius + 3) {
                val progress = segment.toDouble() / (domeRadius + 3).toDouble()
                val x = (cos(angle) * domeRadius * (1.0 - progress * 0.12)).roundToInt()
                val z = (sin(angle) * domeRadius * (1.0 - progress * 0.12)).roundToInt()
                val y = wallHeight + (sin(progress * Math.PI) * domeRadius * 0.78).roundToInt()
                if (collapse && progress > 0.55) break
                val pos = center.add(x, y, z)
                previous?.let {
                    val material = if ((segment + angleStep) % 3 == 0) Blocks.TINTED_GLASS else Blocks.DEEPSLATE_BRICK_SLAB
                    FeatureBuildHelper.drawLine(world, chunkPos, it, pos, material.defaultState, true)
                }
                previous = pos
            }
        }

        for (i in 0 until 4) {
            val beamStart = center.add(-radius / 2 + i * 2, wallHeight + 1, -radius / 2)
            val beamEnd = center.add(-radius / 2 + i * 2, wallHeight + 4 + rand.nextInt(2), radius / 2)
            FeatureBuildHelper.drawLine(world, chunkPos, beamStart, beamEnd, Blocks.DARK_OAK_TRAPDOOR.defaultState, true)
        }
    }

    private fun buildTelescope(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, rand: java.util.Random) {
        val base = center.add(-2, 0, -2)
        FeatureBuildHelper.setBlock(world, chunkPos, base, Blocks.POLISHED_BLACKSTONE, true)
        FeatureBuildHelper.setBlock(world, chunkPos, base.east(), Blocks.POLISHED_BLACKSTONE, true)
        FeatureBuildHelper.setBlock(world, chunkPos, base.up(), Blocks.POLISHED_BLACKSTONE_WALL, true)
        FeatureBuildHelper.setBlock(world, chunkPos, base.up().east(), Blocks.POLISHED_BLACKSTONE_WALL, true)
        val lensStart = base.add(0, 2, 0)
        val lensEnd = base.add(5, 5 + rand.nextInt(2), -3)
        FeatureBuildHelper.drawLine(world, chunkPos, lensStart, lensEnd, Blocks.DARK_PRISMARINE.defaultState, true)
        FeatureBuildHelper.setBlock(world, chunkPos, lensEnd, Blocks.END_ROD, true)
    }

    private fun placeInteriorDetails(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, radius: Int, rand: java.util.Random) {
        for (step in -radius / 2..radius / 2 step 2) {
            FeatureBuildHelper.setBlock(world, chunkPos, center.add(step, 0, radius / 2), Blocks.CHISELED_BOOKSHELF, true)
            if ((step + radius) % 4 == 0) {
                FeatureBuildHelper.setBlock(world, chunkPos, center.add(step, 0, radius / 2 - 2), Blocks.LECTERN, true)
            }
        }

        val starChart = center.add(2, 0, 1)
        FeatureBuildHelper.setBlock(world, chunkPos, starChart, Blocks.CARTOGRAPHY_TABLE, true)
        if (rand.nextBoolean()) {
            FeatureBuildHelper.setBlock(world, chunkPos, center.add(-3, 0, 2), Blocks.ENCHANTING_TABLE, true)
        }
    }

    private fun placeLoot(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, radius: Int, rand: java.util.Random) {
        val pageChestPos = center.add(0, 0, -radius / 2)
        FeatureBuildHelper.placeChest(world, chunkPos, pageChestPos, Direction.SOUTH) { chest ->
            fillChest(chest, java.util.Random(rand.nextLong()), FeatureBuildHelper.randomPageLoot(net.minecraft.util.math.random.Random.create(rand.nextLong())) + listOf(ItemStack(Items.SPYGLASS)))
        }

        val supplyPos = center.add(radius / 2 - 1, 0, 0)
        FeatureBuildHelper.placeChest(world, chunkPos, supplyPos, Direction.WEST) { chest ->
            fillChest(
                chest,
                java.util.Random(rand.nextLong()),
                FeatureBuildHelper.randomSupplyLoot(net.minecraft.util.math.random.Random.create(rand.nextLong())) +
                    listOf(ItemStack(Items.GLASS_PANE, 6 + rand.nextInt(6)))
            )
        }
    }

    private fun scatterDebris(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, radius: Int, rand: java.util.Random) {
        repeat(24 + rand.nextInt(12)) {
            val pos = center.add(rand.nextInt(radius * 2 + 1) - radius, rand.nextInt(3), rand.nextInt(radius * 2 + 1) - radius)
            val block = when (rand.nextInt(5)) {
                0 -> Blocks.STONE_BRICK_SLAB
                1 -> Blocks.MOSSY_STONE_BRICKS
                2 -> Blocks.TINTED_GLASS
                3 -> Blocks.POLISHED_BLACKSTONE
                else -> Blocks.DARK_OAK_TRAPDOOR
            }
            FeatureBuildHelper.setBlock(world, chunkPos, pos, block, true)
        }
    }

    private fun fillChest(chest: ChestBlockEntity, rand: java.util.Random, stacks: List<ItemStack>) {
        stacks.forEach { stack ->
            chest.setStack(rand.nextInt(chest.size()), stack)
        }
    }
}
