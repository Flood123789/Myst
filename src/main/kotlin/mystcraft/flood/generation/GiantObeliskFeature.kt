package mystcraft.flood.generation

import com.mojang.serialization.Codec
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.block.ModBlocks
import mystcraft.flood.item.ModItems
import mystcraft.flood.generation.profile.AgeProfileManager
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.PistonBlock
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.item.ItemStack
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.Heightmap
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.util.FeatureContext

class GiantObeliskFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {

    override fun generate(context: FeatureContext<DefaultFeatureConfig>): Boolean {
        val world = context.world
        val random = context.random
        val origin = context.origin
        val serverWorld = world.toServerWorld()

        // 1. Only spawn in Mystcraft Ages
        val ageId = serverWorld.registryKey.value
        if (!AgeSubdimensionManager.isPrimaryAgeRealm(ageId)) return false

        // 2. Modifier Check: Must have the "giant_obelisks" page in the book
        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, ageId)
        if (AgeLifecycleManager.isDeadAge(profile)) return false
        if (!profile.modifiers.contains("giant_obelisks")) return false

        // 3. Rarity Check: 1 in 1000 chunks.
        // Increase this number to make them rarer, decrease it to see them more often.
        if (random.nextInt(1000) != 0) return false

        // 4. Height Check: Use OCEAN_FLOOR so they can emerge from deep water too
        val topY = world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, origin.x, origin.z)
        if (topY < 10 || topY > 250) return false 
        
        val centerPos = BlockPos(origin.x, topY, origin.z)
        
        // Log the success so you can find it in your console
        MystcraftReforged.LOGGER.info("OBELISK GENERATED at $centerPos")

        val ageRand = kotlin.random.Random(profile.seed)
        val palettes = listOf(
            Pair(Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILES),
            Pair(Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS),
            Pair(Blocks.SMOOTH_BASALT, Blocks.POLISHED_BASALT),
            Pair(Blocks.QUARTZ_PILLAR, Blocks.QUARTZ_BLOCK),
            Pair(Blocks.DARK_PRISMARINE, Blocks.PRISMARINE_BRICKS),
            Pair(ModBlocks.CRYSTAL_BLOCK, Blocks.AMETHYST_BLOCK)
        )

        val selectedPalette = palettes.random(ageRand)
        val shellBlock = selectedPalette.first.defaultState
        val trimBlock = selectedPalette.second.defaultState
        val coreBlock = Blocks.CRYING_OBSIDIAN.defaultState 

        val height = 40 + random.nextInt(20)
        val maxRadius = 5.0f

        // --- PHASE 1: BUILD MAIN STRUCTURE ---
        for (y in -5..height) {
            val currentRadius = if (y < 0) maxRadius else maxRadius * (1.0f - (y.toFloat() / height))
            val rInt = Math.ceil(currentRadius.toDouble()).toInt()

            for (x in -rInt..rInt) {
                for (z in -rInt..rInt) {
                    if (Math.abs(x) + Math.abs(z) <= currentRadius) {
                        val current = centerPos.add(x, y, z)
                        val isEdge = Math.abs(x) + Math.abs(z) >= currentRadius - 1.0f
                        
                        // Clear a small entryway/lobby at the base
                        if (x in -2..2 && z in -2..2 && y in 0..5) {
                            safeSetBlock(world, serverWorld, origin, current, Blocks.AIR.defaultState)
                        } else if (x == 0 && z == 0 && y > 5) {
                            safeSetBlock(world, serverWorld, origin, current, coreBlock)
                        } else {
                            val blockToPlace = if (isEdge) trimBlock else shellBlock
                            safeSetBlock(world, serverWorld, origin, current, blockToPlace)
                        }
                    }
                }
            }
        }
        
        // --- PHASE 2: REDSTONE & INTERIORS ---
        // Entryway Air
        world.setBlockState(centerPos.add(0, 1, 3), Blocks.AIR.defaultState, 2)
        world.setBlockState(centerPos.add(0, 2, 3), Blocks.AIR.defaultState, 2)

        // Ladder Shaft
        for (y in -1 downTo -4) {
            world.setBlockState(centerPos.add(2, y, 2), Blocks.AIR.defaultState, 2)
            world.setBlockState(centerPos.add(2, y, 2), Blocks.LADDER.defaultState.with(Properties.HORIZONTAL_FACING, Direction.NORTH), 2)
        }

        // Basement Room
        for (x in 1..3) {
            for (y in -4..-2) {
                for (z in 1..3) {
                    world.setBlockState(centerPos.add(x, y, z), Blocks.AIR.defaultState, 2)
                }
            }
        }

        // Loot Chest
        val chestPos = centerPos.add(2, -4, 3)
        world.setBlockState(chestPos, Blocks.CHEST.defaultState.with(Properties.HORIZONTAL_FACING, Direction.NORTH), 2)
        val chestEntity = world.getBlockEntity(chestPos)
        if (chestEntity is ChestBlockEntity) {
            val pageCount = FeatureBuildHelper.configuredLostPageCount(random, 1, 3)
            for (i in 0 until pageCount) {
                chestEntity.setStack(random.nextInt(chestEntity.size()), ItemStack(ModItems.LOST_PAGE))
            }
        }

        // Secret Door Mechanism
        val secretWallPos = centerPos.add(2, 1, 2)
        world.setBlockState(secretWallPos, shellBlock, 2)

        val pistonPos = centerPos.add(3, 1, 2)
        world.setBlockState(pistonPos, Blocks.STICKY_PISTON.defaultState.with(Properties.FACING, Direction.WEST), 2)

        val leverPos = centerPos.add(3, 1, 1)
        world.setBlockState(
            leverPos, 
            Blocks.LEVER.defaultState
                .with(net.minecraft.block.LeverBlock.FACE, net.minecraft.block.enums.WallMountLocation.WALL)
                .with(net.minecraft.block.LeverBlock.FACING, Direction.NORTH)
                .with(Properties.POWERED, false), // Start unpowered for stability
            2
        )

        return true
    }

    private fun isSafeToRead(origin: BlockPos, target: BlockPos): Boolean {
        val chunkX = target.x shr 4
        val chunkZ = target.z shr 4
        val originChunkX = origin.x shr 4
        val originChunkZ = origin.z shr 4
        return Math.abs(chunkX - originChunkX) <= 1 && Math.abs(chunkZ - originChunkZ) <= 1
    }

    private fun safeSetBlock(
        world: net.minecraft.world.StructureWorldAccess, 
        serverWorld: net.minecraft.server.world.ServerWorld,
        origin: BlockPos, target: BlockPos, state: BlockState
    ) {
        if (isSafeToRead(origin, target)) {
            val existing = world.getBlockState(target)
            // Abort only on Bedrock/End Portal frames
            if (existing.getHardness(world, target) < 0.0f) return 
            // Use Flag 2 to prevent neighbor updates during generation
            world.setBlockState(target, state, 2)
        } else {
            // Queue the block for when the chunk is ready
            DeferredTreePlacer.add(serverWorld, target, state, false)
        }
    }
}
