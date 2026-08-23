package mystcraft.flood.generation

import com.mojang.serialization.Codec
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.generation.profile.TerrainType
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.StructureWorldAccess
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.util.FeatureContext
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MeteorShowerFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {
    override fun generate(context: FeatureContext<DefaultFeatureConfig>): Boolean {
        val world = context.world
        val serverWorld = world.toServerWorld()
        val ageId = serverWorld.registryKey.value
        if (!AgeSubdimensionManager.isPrimaryAgeRealm(ageId)) return false

        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, ageId)
        if (AgeLifecycleManager.isDeadAge(profile)) return false
        if (!profile.modifiers.contains(ChaosAgeThemes.METEOR_SHOWERS)) return false
        if (profile.terrainType == TerrainType.BIOSPHERES || profile.terrainType == TerrainType.CITIES) return false
        CustomStructureOverrides.generateIfPresent(context, "meteor_showers", profile, 1f / 34f)?.let { return it }

        val origin = context.origin
        val chunkPos = ChunkPos(origin)
        val rand = java.util.Random(
            profile.seed +
                chunkPos.x.toLong() * 3_418_731_287L +
                chunkPos.z.toLong() * 1_328_979_875L +
                0x4D37_E041L
        )
        val rarity = if (profile.weather.isEndlessStorm) 26 else 42
        if (rand.nextInt(rarity) != 0) return false

        val centerX = origin.x + 4 + rand.nextInt(8)
        val centerZ = origin.z + 4 + rand.nextInt(8)
        val ground = FeatureBuildHelper.findGround(world, centerX, centerZ) ?: return false
        if (ground.y < world.bottomY + 8 || ground.y > world.topY - 32) return false

        val radius = 5 + rand.nextInt(7)
        val depth = 2 + rand.nextInt(4)
        carveCrater(world, chunkPos, ground, radius, depth, rand)
        placeMeteorCore(world, chunkPos, ground.down(depth - 1), rand)
        scatterFragments(world, chunkPos, ground, radius + 4, rand)
        return true
    }

    private fun carveCrater(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        radius: Int,
        maxDepth: Int,
        rand: java.util.Random
    ) {
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                val dist = sqrt((dx * dx + dz * dz).toDouble())
                if (dist > radius + 0.4) continue
                val ground = FeatureBuildHelper.findGround(world, center.x + dx, center.z + dz) ?: continue
                val t = (1.0 - dist / radius.toDouble()).coerceIn(0.0, 1.0)
                val localDepth = (t * t * maxDepth).roundToInt().coerceAtLeast(if (dist < radius - 1) 1 else 0)
                val floor = ground.down(localDepth)

                for (y in floor.y + 1..ground.y + 2) {
                    FeatureBuildHelper.setBlockState(world, chunkPos, BlockPos(ground.x, y, ground.z), Blocks.AIR.defaultState, true)
                }

                val floorBlock = when {
                    dist < radius * 0.25 && rand.nextFloat() < 0.35f -> Blocks.MAGMA_BLOCK
                    dist < radius * 0.55 -> Blocks.BLACKSTONE
                    rand.nextFloat() < 0.45f -> Blocks.DEEPSLATE
                    else -> Blocks.TUFF
                }
                FeatureBuildHelper.setBlock(world, chunkPos, floor, floorBlock, true)

                if (dist >= radius - 1.2) {
                    val rim = if (rand.nextBoolean()) Blocks.COBBLED_DEEPSLATE else Blocks.BLACKSTONE
                    FeatureBuildHelper.setBlock(world, chunkPos, ground.up(), rim, true)
                }
            }
        }
    }

    private fun placeMeteorCore(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, rand: java.util.Random) {
        val coreBlocks = listOf(Blocks.CRYING_OBSIDIAN, Blocks.OBSIDIAN, Blocks.MAGMA_BLOCK, Blocks.AMETHYST_BLOCK)
        for (dx in -1..1) {
            for (dy in -1..1) {
                for (dz in -1..1) {
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) + kotlin.math.abs(dz) > 2) continue
                    FeatureBuildHelper.setBlock(world, chunkPos, center.add(dx, dy, dz), coreBlocks[rand.nextInt(coreBlocks.size)], true)
                }
            }
        }
    }

    private fun scatterFragments(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, radius: Int, rand: java.util.Random) {
        repeat(10 + rand.nextInt(10)) {
            val dx = rand.nextInt(radius * 2 + 1) - radius
            val dz = rand.nextInt(radius * 2 + 1) - radius
            val ground = FeatureBuildHelper.findGround(world, center.x + dx, center.z + dz) ?: return@repeat
            val block = when (rand.nextInt(5)) {
                0 -> Blocks.BLACKSTONE
                1 -> Blocks.COBBLED_DEEPSLATE
                2 -> Blocks.MAGMA_BLOCK
                3 -> Blocks.OBSIDIAN
                else -> Blocks.TUFF
            }
            placeFragment(world, chunkPos, ground.up(), block, rand)
        }
    }

    private fun placeFragment(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, block: Block, rand: java.util.Random) {
        val radius = if (rand.nextFloat() < 0.25f) 2 else 1
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                for (dz in -radius..radius) {
                    if (sqrt((dx * dx + dy * dy + dz * dz).toDouble()) > radius + 0.1) continue
                    FeatureBuildHelper.setBlock(world, chunkPos, center.add(dx, dy, dz), block, true)
                }
            }
        }
    }
}
