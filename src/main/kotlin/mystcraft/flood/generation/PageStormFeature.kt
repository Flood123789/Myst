package mystcraft.flood.generation

import com.mojang.serialization.Codec
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.generation.profile.TerrainType
import net.minecraft.block.BlockState
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
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class PageStormFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {
    override fun generate(context: FeatureContext<DefaultFeatureConfig>): Boolean {
        val world = context.world
        val serverWorld = world.toServerWorld()
        val ageId = serverWorld.registryKey.value
        if (!AgeSubdimensionManager.isPrimaryAgeRealm(ageId)) return false

        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, ageId)
        if (AgeLifecycleManager.isDeadAge(profile)) return false
        if (profile.terrainType == TerrainType.BIOSPHERES || profile.terrainType == TerrainType.CITIES) return false

        val explicit = profile.modifiers.contains(AmbientAgeThemes.PAGE_STORMS)
        val chance = when {
            explicit -> 0.98f
            profile.stability.instabilityScore >= 75 -> 0.42f
            profile.stability.instabilityScore >= 45 -> 0.18f
            else -> 0.0f
        } * AgeFeatureTuning.chanceMultiplier(profile, AmbientAgeThemes.PAGE_STORMS)
        if (chance <= 0f) return false
        CustomStructureOverrides.generateIfPresent(context, "page_storms", profile, chance)?.let { return it }

        val chunkPos = ChunkPos(context.origin)
        val regionSize = if (explicit) 11 else 16
        val regionX = Math.floorDiv(chunkPos.x, regionSize)
        val regionZ = Math.floorDiv(chunkPos.z, regionSize)
        val seed = profile.seed + regionX * 12_881_719L + regionZ * 7_880_837L + 0xF0A60L
        val rand = java.util.Random(seed)
        if (rand.nextFloat() > chance) return false

        val ownerChunkX = regionX * regionSize + rand.nextInt(regionSize)
        val ownerChunkZ = regionZ * regionSize + rand.nextInt(regionSize)
        if (chunkPos.x != ownerChunkX || chunkPos.z != ownerChunkZ) return false
        if (!AgeFeatureTuning.canPlaceMajorFeature(profile, ChunkPos(ownerChunkX, ownerChunkZ), AmbientAgeThemes.PAGE_STORMS, 10)) return false

        val centerX = ownerChunkX * 16 + 8
        val centerZ = ownerChunkZ * 16 + 8
        val ground = FeatureBuildHelper.findGround(world, centerX, centerZ) ?: return false
        val center = ground.up()
        val chunk = ChunkPos(center)
        val height = 14 + rand.nextInt(10)

        buildEye(world, chunk, center, height)
        buildStorm(world, chunk, center, height, rand)
        buildBase(world, chunk, center, rand)
        placeLoot(world, chunk, center, rand)
        return true
    }

    private fun buildEye(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, height: Int) {
        FeatureBuildHelper.fillColumn(world, chunkPos, center, height, Blocks.CHAIN.defaultState, true)
        FeatureBuildHelper.setBlock(world, chunkPos, center.up(height), Blocks.END_ROD, true)
        FeatureBuildHelper.setBlock(world, chunkPos, center.down(), Blocks.CHISELED_BOOKSHELF, true)
        FeatureBuildHelper.setBlock(world, chunkPos, center.down(2), Blocks.CRYING_OBSIDIAN, true)
    }

    private fun buildStorm(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, height: Int, rand: java.util.Random) {
        for (y in 2..height) {
            val loops = 4 + (y / 4)
            val radius = 2.0 + y * 0.18
            for (index in 0 until loops) {
                val angle = y * 0.45 + index * (Math.PI * 2.0 / loops.toDouble())
                val x = (cos(angle) * radius).roundToInt()
                val z = (sin(angle) * radius).roundToInt()
                val pos = center.add(x, y, z)
                val state = stormState(rand, y)
                FeatureBuildHelper.setBlockState(world, chunkPos, pos, state, true)
                if (rand.nextFloat() < 0.2f) {
                    FeatureBuildHelper.drawLine(world, chunkPos, pos, center.add(0, y - 1, 0), Blocks.END_ROD.defaultState, true)
                }
            }
        }
    }

    private fun buildBase(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, rand: java.util.Random) {
        for (dx in -4..4) {
            for (dz in -4..4) {
                if (dx * dx + dz * dz > 18) continue
                val block = when {
                    dx * dx + dz * dz > 12 -> Blocks.SMOOTH_STONE
                    rand.nextFloat() < 0.22f -> Blocks.CHISELED_BOOKSHELF
                    else -> Blocks.POLISHED_DIORITE
                }
                FeatureBuildHelper.setBlock(world, chunkPos, center.add(dx, -1, dz), block, true)
            }
        }
        FeatureBuildHelper.setBlock(world, chunkPos, center.east(2), Blocks.LECTERN, true)
        FeatureBuildHelper.setBlock(world, chunkPos, center.west(2), Blocks.LECTERN, true)
    }

    private fun placeLoot(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, rand: java.util.Random) {
        FeatureBuildHelper.placeChest(world, chunkPos, center.south(2), Direction.NORTH) { chest ->
            fillChest(
                chest,
                java.util.Random(rand.nextLong()),
                FeatureBuildHelper.randomPageLoot(net.minecraft.util.math.random.Random.create(rand.nextLong())) +
                    listOf(ItemStack(Items.BOOK, 3), ItemStack(Items.PAPER, 6 + rand.nextInt(6)))
            )
        }
    }

    private fun stormState(rand: java.util.Random, y: Int): BlockState = when (rand.nextInt(6)) {
        0 -> Blocks.WHITE_STAINED_GLASS_PANE.defaultState
        1 -> Blocks.LIGHT_GRAY_STAINED_GLASS_PANE.defaultState
        2 -> if (y % 3 == 0) Blocks.BIRCH_TRAPDOOR.defaultState else Blocks.END_ROD.defaultState
        3 -> Blocks.END_ROD.defaultState
        4 -> Blocks.CHISELED_BOOKSHELF.defaultState
        else -> Blocks.TINTED_GLASS.defaultState
    }

    private fun fillChest(chest: ChestBlockEntity, rand: java.util.Random, stacks: List<ItemStack>) {
        stacks.forEach { chest.setStack(rand.nextInt(chest.size()), it) }
    }
}
