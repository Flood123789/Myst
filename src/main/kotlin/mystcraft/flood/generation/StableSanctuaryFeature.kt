package mystcraft.flood.generation

import com.mojang.serialization.Codec
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.generation.profile.TerrainType
import net.minecraft.block.Blocks
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.Direction
import net.minecraft.world.StructureWorldAccess
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.util.FeatureContext

class StableSanctuaryFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {
    override fun generate(context: FeatureContext<DefaultFeatureConfig>): Boolean {
        val world = context.world
        val serverWorld = world.toServerWorld()
        val ageId = serverWorld.registryKey.value
        if (!AgeSubdimensionManager.isPrimaryAgeRealm(ageId)) return false

        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, ageId)
        if (AgeLifecycleManager.isDeadAge(profile)) return false
        if (profile.terrainType == TerrainType.CITIES || profile.terrainType == TerrainType.BIOSPHERES) return false

        val explicit = profile.modifiers.contains(AmbientAgeThemes.STABLE_SANCTUARIES)
        val chance = when {
            explicit -> 0.95f
            profile.stability.instabilityScore in 20..65 -> 0.26f
            profile.stability.instabilityScore > 65 -> 0.12f
            else -> 0.10f
        } * AgeFeatureTuning.chanceMultiplier(profile, AmbientAgeThemes.STABLE_SANCTUARIES)
        if (chance <= 0f) return false
        CustomStructureOverrides.generateIfPresent(context, "stable_sanctuaries", profile, chance)?.let { return it }

        val chunkPos = ChunkPos(context.origin)
        val regionSize = if (explicit) 12 else 18
        val regionX = Math.floorDiv(chunkPos.x, regionSize)
        val regionZ = Math.floorDiv(chunkPos.z, regionSize)
        val seed = profile.seed + regionX * 13_301L + regionZ * 90_701L + 0x57AB1EL
        val rand = java.util.Random(seed)
        if (rand.nextFloat() > chance) return false

        val ownerChunkX = regionX * regionSize + rand.nextInt(regionSize)
        val ownerChunkZ = regionZ * regionSize + rand.nextInt(regionSize)
        if (chunkPos.x != ownerChunkX || chunkPos.z != ownerChunkZ) return false
        if (!AgeFeatureTuning.canPlaceMajorFeature(profile, ChunkPos(ownerChunkX, ownerChunkZ), AmbientAgeThemes.STABLE_SANCTUARIES, 9)) return false

        val centerX = ownerChunkX * 16 + 8
        val centerZ = ownerChunkZ * 16 + 8
        val ground = FeatureBuildHelper.findGround(world, centerX, centerZ) ?: return false
        val center = ground.up()
        val chunk = ChunkPos(center)
        val radius = 5 + rand.nextInt(3)

        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                val dist = dx * dx + dz * dz
                if (dist > radius * radius) continue
                val pos = center.add(dx, -1, dz)
                val block = when {
                    dist >= (radius - 1) * (radius - 1) -> Blocks.SMOOTH_QUARTZ
                    dist <= 2 -> Blocks.POLISHED_DIORITE
                    else -> Blocks.CALCITE
                }
                FeatureBuildHelper.setBlock(world, chunk, pos, block, true)
            }
        }

        for (cardinal in Direction.Type.HORIZONTAL) {
            val gate = center.offset(cardinal, radius - 1)
            FeatureBuildHelper.fillColumn(world, chunk, gate, 4, Blocks.QUARTZ_PILLAR.defaultState, true)
            FeatureBuildHelper.setBlock(world, chunk, gate.up(4), Blocks.QUARTZ_SLAB, true)
            FeatureBuildHelper.setBlock(world, chunk, gate.offset(cardinal.rotateYClockwise()), Blocks.SOUL_LANTERN, true)
        }

        for (dx in -2..2) {
            for (dz in -2..2) {
                if (dx * dx + dz * dz > 6) continue
                FeatureBuildHelper.setBlock(world, chunk, center.add(dx, 0, dz), if (dx == 0 && dz == 0) Blocks.WATER else Blocks.SMOOTH_QUARTZ, true)
            }
        }
        FeatureBuildHelper.setBlock(world, chunk, center.up(), Blocks.END_ROD, true)
        FeatureBuildHelper.setBlock(world, chunk, center.up(2), Blocks.AMETHYST_CLUSTER, true)
        FeatureBuildHelper.placeChest(world, chunk, center.south(radius - 2), Direction.NORTH) { chest ->
            fillChest(
                chest,
                java.util.Random(rand.nextLong()),
                FeatureBuildHelper.randomPageLoot(net.minecraft.util.math.random.Random.create(rand.nextLong())) +
                    listOf(ItemStack(Items.GOLDEN_APPLE), ItemStack(Items.CLOCK))
            )
        }
        return true
    }

    private fun fillChest(chest: ChestBlockEntity, rand: java.util.Random, stacks: List<ItemStack>) {
        stacks.forEach { chest.setStack(rand.nextInt(chest.size()), it) }
    }
}
