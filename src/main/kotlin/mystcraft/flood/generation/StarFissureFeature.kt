package mystcraft.flood.generation

import com.mojang.serialization.Codec
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.generation.profile.TerrainType
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.util.FeatureContext
import mystcraft.flood.block.ModBlocks
import kotlin.math.sqrt

class StarFissureFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {

    override fun generate(context: FeatureContext<DefaultFeatureConfig>): Boolean {
        val world = context.world
        val random = context.random
        val origin = context.origin
        val serverWorld = world.toServerWorld()

        // 1. Only spawn in Mystcraft Ages
        if (!AgeSubdimensionManager.isPrimaryAgeRealm(serverWorld.registryKey.value)) return false
        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, serverWorld.registryKey.value)
        if (AgeLifecycleManager.isDeadAge(profile)) return false
        if (profile.terrainType == TerrainType.BIOSPHERES) return false

        val chunkPos = ChunkPos(origin)
        val regionSize = 24
        val regionX = Math.floorDiv(chunkPos.x, regionSize)
        val regionZ = Math.floorDiv(chunkPos.z, regionSize)
        val seed = profile.seed + regionX * 7_721_533L + regionZ * 3_911_141L + 0x57A7F155L
        val regionRand = java.util.Random(seed)
        val chance = when {
            profile.stability.instabilityScore >= 80 -> 0.34f
            profile.stability.instabilityScore >= 45 -> 0.24f
            else -> 0.12f
        }
        if (regionRand.nextFloat() > chance) return false

        val ownerChunkX = regionX * regionSize + 3 + regionRand.nextInt(regionSize - 6)
        val ownerChunkZ = regionZ * regionSize + 3 + regionRand.nextInt(regionSize - 6)
        if (chunkPos.x != ownerChunkX || chunkPos.z != ownerChunkZ) return false
        if (!AgeFeatureTuning.canPlaceMajorFeature(profile, chunkPos, AgeFeatureTuning.STAR_FISSURE, 10)) return false

        // 3. Find the absolute surface of the terrain
        val centerX = ownerChunkX * 16 + 8 + regionRand.nextInt(7) - 3
        val centerZ = ownerChunkZ * 16 + 8 + regionRand.nextInt(7) - 3
        val ground = FeatureBuildHelper.findGround(world, centerX, centerZ) ?: return false
        if (ground.y < 19) return false

        val centerPos = ground.up()
        val topY = centerPos.y

        // ==========================================
        // PHASE 1: THE FLOATING DEBRIS (Anti-Gravity)
        // ==========================================
        val debrisRadius = 8
        for (x in -debrisRadius..debrisRadius) {
            for (y in 3..15) { 
                for (z in -debrisRadius..debrisRadius) {
                    val distance = sqrt(x * x + y * y / 4.0 + z * z)
                    
                    if (distance <= debrisRadius && random.nextFloat() < 0.02f) {
                        val currentPos = centerPos.add(x, y, z)
                        val block = when (random.nextInt(100)) {
                            in 0..60 -> Blocks.OBSIDIAN.defaultState
                            in 61..85 -> Blocks.CRYING_OBSIDIAN.defaultState
                            else -> Blocks.AMETHYST_BLOCK.defaultState
                        }
                        // Use safeSetBlock to handle potential cross-chunk debris
                        safeSetBlock(world, serverWorld, origin, currentPos, block)
                    }
                }
            }
        }

        // ==========================================
        // PHASE 2: CARVING THE TEAR TO THE VOID
        // ==========================================
        var currentRadius = 5.0f
        var driftX = 0.0f
        var driftZ = 0.0f

        for (y in topY downTo -64) {
            driftX += (random.nextFloat() - 0.5f) * 1.5f
            driftZ += (random.nextFloat() - 0.5f) * 1.5f
            
            if (y % 10 == 0 && currentRadius > 2.0f) {
                currentRadius -= 0.5f
            }

            val layerCenter = BlockPos(
                centerPos.x + driftX.toInt(), 
                y, 
                centerPos.z + driftZ.toInt()
            )

            val carveRadius = currentRadius.toInt() + 2

            for (x in -carveRadius..carveRadius) {
                for (z in -carveRadius..carveRadius) {
                    val currentPos = layerCenter.add(x, 0, z)
                    val distance = sqrt((x * x + z * z).toDouble())

                    // CORE VOID
                    if (distance <= currentRadius) {
                        if (y == -64) {
                            safeSetBlock(world, serverWorld, origin, currentPos, ModBlocks.STAR_FISSURE.defaultState, allowUnbreakable = true)
                        } else {
                            // Use safeSetBlock to erase blocks even in neighboring chunks
                            safeSetBlock(world, serverWorld, origin, currentPos, Blocks.AIR.defaultState, allowUnbreakable = true)
                        }
                    } 
                    // CORRUPTED EDGE
                    else if (distance <= currentRadius + 1.5f) {
                        // We still check isAir for speed, but only if it's safe to read the block
                        if (isSafeToRead(origin, currentPos)) {
                            if (!world.isAir(currentPos)) {
                                val edgeBlock = when (random.nextInt(100)) {
                                    in 0..60 -> Blocks.OBSIDIAN.defaultState
                                    in 61..85 -> Blocks.CRYING_OBSIDIAN.defaultState
                                    in 86..95 -> Blocks.AMETHYST_BLOCK.defaultState
                                    else -> Blocks.AIR.defaultState 
                                }
                                safeSetBlock(world, serverWorld, origin, currentPos, edgeBlock)
                            }
                        } else {
                            // If we can't read it, just queue the update via DeferredTreePlacer
                            val edgeBlock = if (random.nextInt(100) < 80) Blocks.OBSIDIAN.defaultState else Blocks.AIR.defaultState
                            safeSetBlock(world, serverWorld, origin, currentPos, edgeBlock)
                        }
                    }
                }
            }
        }
        return true
    }

    // --- SAFETY HELPERS TO PREVENT CASCADING WORLDGEN ---
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
        origin: BlockPos, target: BlockPos, state: BlockState, allowUnbreakable: Boolean = false
    ) {
        if (isSafeToRead(origin, target)) {
            val existing = world.getBlockState(target)
            // Abort only if it hits bedrock/unbreakable blocks and the current carve is not allowed to pierce them.
            if (!allowUnbreakable && target.y > -64 && existing.getHardness(world, target) < 0.0f) return 
            
            // Flag 2 prevents neighbor updates cascading during worldgen
            world.setBlockState(target, state, 2)
        } else {
            // Queue the block placement for when the chunk actually loads
            DeferredTreePlacer.add(serverWorld, target, state, false)
        }
    }
}
