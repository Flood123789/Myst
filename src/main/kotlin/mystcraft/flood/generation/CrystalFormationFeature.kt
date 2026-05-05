package mystcraft.flood.generation

import com.mojang.serialization.Codec
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.block.ModBlocks
import mystcraft.flood.generation.profile.AgeProfileManager
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.util.FeatureContext
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class CrystalFormationFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {

    override fun generate(context: FeatureContext<DefaultFeatureConfig>): Boolean {
        val world = context.world
        val random = context.random
        val origin = context.origin
        val serverWorld = world.toServerWorld()

        val ageId = serverWorld.registryKey.value
        if (!AgeSubdimensionManager.isPrimaryAgeRealm(ageId)) return false

        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, ageId)
        if (AgeLifecycleManager.isDeadAge(profile)) return false
        if (!profile.modifiers.contains("crystal_formations")) return false

        // Rarity: crystals should feel like rare discoveries even when the page is present.
        if (random.nextInt(1000) != 0) return false

        val ground = FeatureBuildHelper.findGround(world, origin.x, origin.z) ?: return false
        if (ground.y < 19 || ground.y > 149) return false

        val centerPos = ground.up()
        val floorBlock = world.getBlockState(ground)

        // Don't spawn on water or mid-air
        if (floorBlock.isOf(Blocks.WATER) || floorBlock.isAir) return false

        val crystalState = ModBlocks.CRYSTAL_BLOCK.defaultState
        
        // Form a cluster of 3 to 7 crystal spikes
        val spikeCount = 3 + random.nextInt(5)

        for (i in 0 until spikeCount) {
            val angle = random.nextFloat() * Math.PI * 2
            val upPitch = random.nextFloat() * 0.7f + 0.5f // Steeper angle upwards
            val spikeLength = 10 + random.nextInt(25) // 10 to 35 blocks long!

            val dx = cos(angle).toFloat()
            val dz = sin(angle).toFloat()

            // Start the spike slightly underground so it looks embedded
            val currentPos = centerPos.add(
                random.nextInt(4) - 2, 
                -random.nextInt(3), 
                random.nextInt(4) - 2
            ).mutableCopy()

            val baseThickness = 2.0f + random.nextFloat() // 2 to 3 blocks thick at base

            for (step in 0..spikeLength) {
                val xOff = (dx * step).toInt()
                val yOff = (upPitch * step).toInt()
                val zOff = (dz * step).toInt()
                
                val segmentCenter = currentPos.add(xOff, yOff, zOff)
                
                // Taper the crystal so it forms a sharp point at the top!
                val currentThickness = baseThickness * (1.0f - (step.toFloat() / spikeLength))
                val thickInt = Math.ceil(currentThickness.toDouble()).toInt()

                for (bx in -thickInt..thickInt) {
                    for (by in -thickInt..thickInt) {
                        for (bz in -thickInt..thickInt) {
                            // Using Manhattan distance (absolute sum) creates a faceted, diamond-like crystal shape instead of a round cylinder!
                            if (Math.abs(bx) + Math.abs(by) + Math.abs(bz) <= currentThickness * 1.2f) {
                                val target = segmentCenter.add(bx, by, bz)
                                safeSetBlock(world, serverWorld, origin, target, crystalState)
                            }
                        }
                    }
                }
            }
        }
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
        origin: BlockPos, 
        target: BlockPos, 
        state: BlockState
    ) {
        if (isSafeToRead(origin, target)) {
            val existing = world.getBlockState(target)
            if (existing.getHardness(world, target) < 0.0f) return 
            if (existing.isAir || existing.isReplaceable || existing.isOf(Blocks.DIRT) || existing.isOf(Blocks.STONE)) {
                world.setBlockState(target, state, 3)
            }
        } else {
            // We can reuse the tree placer queue to prevent chunk crashes!
            DeferredTreePlacer.add(serverWorld, target, state, true)
        }
    }
}
