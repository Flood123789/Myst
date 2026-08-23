package mystcraft.flood.generation

import com.mojang.serialization.Codec
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.profile.AgeProfileManager
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.Direction
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.util.FeatureContext
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class MemoryBloomFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {
    override fun generate(context: FeatureContext<DefaultFeatureConfig>): Boolean {
        val world = context.world
        val serverWorld = world.toServerWorld()
        val ageId = serverWorld.registryKey.value
        if (!AgeSubdimensionManager.isPrimaryAgeRealm(ageId)) return false

        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, ageId)
        if (AgeLifecycleManager.isDeadAge(profile)) return false

        val explicit = profile.modifiers.contains(AmbientAgeThemes.MEMORY_BLOOMS)
        val chance = when {
            explicit -> 0.98f
            profile.stability.instabilityScore >= 55 -> 0.48f
            profile.stability.instabilityScore >= 25 -> 0.22f
            else -> 0.06f
        } * AgeFeatureTuning.chanceMultiplier(profile, AmbientAgeThemes.MEMORY_BLOOMS)
        if (chance <= 0f) return false
        CustomStructureOverrides.generateIfPresent(context, "memory_blooms", profile, chance)?.let { return it }

        val chunkPos = ChunkPos(context.origin)
        val regionSize = if (explicit) 7 else 10
        val regionX = Math.floorDiv(chunkPos.x, regionSize)
        val regionZ = Math.floorDiv(chunkPos.z, regionSize)
        val seed = profile.seed + regionX * 1_981_331L + regionZ * 2_781_041L + 0x0B1000L
        val rand = java.util.Random(seed)
        if (rand.nextFloat() > chance) return false

        val ownerChunkX = regionX * regionSize + rand.nextInt(regionSize)
        val ownerChunkZ = regionZ * regionSize + rand.nextInt(regionSize)
        if (chunkPos.x != ownerChunkX || chunkPos.z != ownerChunkZ) return false
        if (!AgeFeatureTuning.canPlaceMajorFeature(profile, ChunkPos(ownerChunkX, ownerChunkZ), AmbientAgeThemes.MEMORY_BLOOMS, 8)) return false

        val centerX = ownerChunkX * 16 + 8 + rand.nextInt(5) - 2
        val centerZ = ownerChunkZ * 16 + 8 + rand.nextInt(5) - 2
        val ground = FeatureBuildHelper.findGround(world, centerX, centerZ) ?: return false
        val center = ground.up()
        val chunk = ChunkPos(center)
        val radius = 4 + rand.nextInt(4)

        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                val dist = dx * dx + dz * dz
                if (dist > radius * radius) continue
                val pos = center.add(dx, 0, dz)
                val floor = when {
                    dist <= 2 -> Blocks.MOSS_BLOCK
                    rand.nextFloat() < 0.35f -> Blocks.PEARLESCENT_FROGLIGHT
                    else -> Blocks.MOSSY_COBBLESTONE
                }
                FeatureBuildHelper.setBlock(world, chunk, pos.down(), floor, true)
                if (rand.nextFloat() < 0.32f) {
                    FeatureBuildHelper.setBlockState(world, chunk, pos, blossomState(rand), true)
                }
            }
        }

        for (petal in 0 until 8) {
            val angle = petal * (Math.PI / 4.0)
            val petalPos = center.add((cos(angle) * radius).roundToInt(), 1 + rand.nextInt(2), (sin(angle) * radius).roundToInt())
            FeatureBuildHelper.drawLine(world, chunk, center.up(), petalPos, blossomState(rand), true)
            FeatureBuildHelper.setBlock(world, chunk, petalPos, Blocks.AMETHYST_CLUSTER, true)
        }

        FeatureBuildHelper.setBlock(world, chunk, center, Blocks.SEA_LANTERN, true)
        FeatureBuildHelper.setBlock(world, chunk, center.up(), Blocks.END_ROD, true)
        return true
    }

    private fun blossomState(rand: java.util.Random): BlockState = when (rand.nextInt(6)) {
        0 -> Blocks.PINK_PETALS.defaultState
        1 -> Blocks.SMALL_AMETHYST_BUD.defaultState
        2 -> Blocks.MEDIUM_AMETHYST_BUD.defaultState
        3 -> Blocks.PINK_TULIP.defaultState
        4 -> Blocks.AZALEA_LEAVES.defaultState
        else -> Blocks.TINTED_GLASS.defaultState
    }
}
