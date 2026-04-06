package mystcraft.flood.generation

import com.mojang.serialization.Codec
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.profile.AgeProfileManager
import net.minecraft.block.Blocks
import net.minecraft.registry.Registries
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.world.Heightmap
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.util.FeatureContext
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class GiantTreeFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {

    override fun generate(context: FeatureContext<DefaultFeatureConfig>): Boolean {
        val world = context.world
        val random = context.random
        val origin = context.origin
        val serverWorld = world.toServerWorld()

        val ageId = serverWorld.registryKey.value
        if (ageId.namespace != MystcraftReforged.MOD_ID) return false

        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, ageId)
        if (!profile.modifiers.contains("giant_trees")) return false

        // 1 in 15 chunks
        if (random.nextInt(15) != 0) return false

        val topY = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, origin.x, origin.z)
        if (topY < 40 || topY > 150) return false 
        
        val centerPos = BlockPos(origin.x, topY, origin.z)

        // Jesus check: No trees on water
        if (world.getBlockState(centerPos.down()).isOf(Blocks.WATER)) return false

        // ==========================================
        // 1. FILTERED REGISTRY SCRAPER
        // ==========================================
        val allBlocks = Registries.BLOCK.toList()
        val validLogs = allBlocks.filter { 
            val name = Registries.BLOCK.getId(it).path
            (name.endsWith("_log") || name.endsWith("_wood") || name.endsWith("_stem")) &&
            !name.contains("dripleaf") && !name.contains("melon") && 
            !name.contains("pumpkin") && !name.contains("bamboo")
        }
        val validLeaves = allBlocks.filter { 
            val name = Registries.BLOCK.getId(it).path
            name.endsWith("_leaves") || name.endsWith("_wart_block") || name == "leaves"
        }

        val selectedLog = if (validLogs.isNotEmpty()) validLogs[random.nextInt(validLogs.size)] else Blocks.JUNGLE_LOG
        val selectedLeaf = if (validLeaves.isNotEmpty()) validLeaves[random.nextInt(validLeaves.size)] else Blocks.JUNGLE_LEAVES

        val logState = selectedLog.defaultState
        var leafState = selectedLeaf.defaultState
        if (leafState.contains(Properties.PERSISTENT)) {
            leafState = leafState.with(Properties.PERSISTENT, true)
        }

        // ==========================================
        // 2. PHASE A: GRAVITY-AFFECTED ROOTS
        // ==========================================
        val rootCount = 10 + random.nextInt(6) // Increased for more epic look
        val trunkRadius = 5.5f

        for (i in 0 until rootCount) {
            val angle = random.nextFloat() * Math.PI * 2
            val rootLength = 25 + random.nextInt(15)

            val dx = cos(angle).toFloat()
            val dz = sin(angle).toFloat()

            // START: Slightly up the trunk. FIXED: .mutableCopy() instead of .toMutablePos()
            val currentPos = centerPos.add(0, 2 + random.nextInt(3), 0).mutableCopy()
            
            var velX = dx * 1.2f
            var velY = -0.2f 
            var velZ = dz * 1.2f

            for (step in 0..rootLength) {
                val rootThickness = if (step < rootLength / 3) 2 else 1

                // Gravity logic: If in air, fall. If in ground, spread.
                if (isSafeToRead(origin, currentPos)) {
                    if (world.isAir(currentPos)) {
                        velY -= 0.18f // Physics!
                        velX *= 0.85f 
                        velZ *= 0.85f
                    } else {
                        velY = -0.25f // Reset to crawling pitch
                        velX = dx * 1.1f
                        velZ = dz * 1.1f
                    }
                }

                val nextX = currentPos.x + velX
                val nextY = currentPos.y + velY
                val nextZ = currentPos.z + velZ
                currentPos.set(nextX.toInt(), nextY.toInt(), nextZ.toInt())

                for (bx in -rootThickness..rootThickness) {
                    for (by in -rootThickness..rootThickness) {
                        for (bz in -rootThickness..rootThickness) {
                            if (sqrt((bx*bx + by*by + bz*bz).toDouble()) <= rootThickness) {
                                safeSetBlock(world, serverWorld, origin, BlockPos(currentPos.x + bx, currentPos.y + by, currentPos.z + bz), logState, false)
                            }
                        }
                    }
                }
                if (currentPos.y < -60) break
            }
        }

        // ==========================================
        // 3. PHASE B: MASSIVE TRUNK
        // ==========================================
        val treeHeight = 70 + random.nextInt(40) // Up to 110 blocks tall

        for (y in 0..treeHeight) {
            val currentRadius = if (y > treeHeight - 15) trunkRadius - 2.5f else trunkRadius
            for (x in -6..6) {
                for (z in -6..6) {
                    if (sqrt((x * x + z * z).toDouble()) <= currentRadius) {
                        safeSetBlock(world, serverWorld, origin, centerPos.add(x, y, z), logState, false)
                    }
                }
            }
        }

        // ==========================================
        // 4. PHASE C: WIDE SWEEPING BRANCHES
        // ==========================================
        val branchCount = 40 + random.nextInt(20) // Restored massive branch count
        for (i in 0 until branchCount) {
            val startY = (treeHeight / 4) + random.nextInt(treeHeight - (treeHeight / 4))
            val angle = random.nextFloat() * Math.PI * 2
            val upPitch = random.nextFloat() * 0.4f + 0.15f 
            val branchLength = 18 + random.nextInt(15)

            val bDx = cos(angle).toFloat()
            val bDz = sin(angle).toFloat()
            var bPos = centerPos.add(0, startY, 0)

            for (step in 0..branchLength) {
                val xOff = (bDx * step * 1.6f).toInt()
                val yOff = (upPitch * step).toInt()
                val zOff = (bDz * step * 1.6f).toInt()
                val currentBPos = bPos.add(xOff, yOff, zOff)
                
                val bThickness = if (step < branchLength / 2) 2 else 1
                for (bx in -bThickness..bThickness) {
                    // FIXED: Corrected the loop range end from 'by' to 'bThickness'
                    for (by in -bThickness..bThickness) { 
                        for (bz in -bThickness..bThickness) {
                            if (sqrt((bx*bx + by*by + bz*bz).toDouble()) <= bThickness) {
                                val target = currentBPos.add(bx, by, bz)
                                // Only draw wood where safe or replacing its own leaves
                                if (isSafeToRead(origin, target)) {
                                    val state = world.getBlockState(target)
                                    if (state.isAir || state.block == selectedLeaf) {
                                        safeSetBlock(world, serverWorld, origin, target, logState, true)
                                    }
                                } else {
                                    // If we can't read, we assume it's air and queue it
                                    safeSetBlock(world, serverWorld, origin, target, logState, true)
                                }
                            }
                        }
                    }
                }

                if (step > branchLength / 2 && random.nextFloat() < 0.3f) {
                    generateLeafCluster(world, serverWorld, origin, currentBPos, 4, leafState, random)
                }
            }
            // Tip Cluster
            generateLeafCluster(world, serverWorld, origin, bPos.add((bDx * branchLength * 1.6f).toInt(), (upPitch * branchLength).toInt(), (bDz * branchLength * 1.6f).toInt()), 6, leafState, random)
        }

        // Top Crown
        generateLeafCluster(world, serverWorld, origin, centerPos.add(0, treeHeight + 2, 0), 8, leafState, random)

        return true
    }

    private fun generateLeafCluster(world: net.minecraft.world.StructureWorldAccess, serverWorld: net.minecraft.server.world.ServerWorld, origin: BlockPos, clusterCenter: BlockPos, radius: Int, leafState: net.minecraft.block.BlockState, random: net.minecraft.util.math.random.Random) {
        for (lx in -radius..radius) {
            for (ly in -radius..radius) {
                for (lz in -radius..radius) {
                    if (sqrt((lx * lx + ly * ly + lz * lz).toDouble()) <= radius && random.nextFloat() < 0.75f) {
                        safeSetBlock(world, serverWorld, origin, clusterCenter.add(lx, ly, lz), leafState, true)
                    }
                }
            }
        }
    }

    private fun isSafeToRead(origin: BlockPos, target: BlockPos): Boolean {
        val chunkX = target.x shr 4
        val chunkZ = target.z shr 4
        val originChunkX = origin.x shr 4
        val originChunkZ = origin.z shr 4
        return Math.abs(chunkX - originChunkX) <= 1 && Math.abs(chunkZ - originChunkZ) <= 1
    }

    private fun safeSetBlock(world: net.minecraft.world.StructureWorldAccess, serverWorld: net.minecraft.server.world.ServerWorld, origin: BlockPos, target: BlockPos, state: net.minecraft.block.BlockState, requireSoft: Boolean) {
        if (isSafeToRead(origin, target)) {
            val existing = world.getBlockState(target)
            if (existing.getHardness(world, target) < 0.0f) return 
            if (!requireSoft || existing.isAir || existing.isReplaceable) {
                world.setBlockState(target, state, 3)
            }
        } else {
            DeferredTreePlacer.add(serverWorld, target, state, requireSoft)
        }
    }
}