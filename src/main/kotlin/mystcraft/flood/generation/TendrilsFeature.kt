package mystcraft.flood.generation

import com.mojang.serialization.Codec
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.profile.AgeProfileManager
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.util.FeatureContext
import kotlin.math.sqrt

class TendrilsFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {

    override fun generate(context: FeatureContext<DefaultFeatureConfig>): Boolean {
        val world = context.world
        val random = context.random
        val origin = context.origin
        val serverWorld = world.toServerWorld()

        val ageId = serverWorld.registryKey.value
        if (ageId.namespace != MystcraftReforged.MOD_ID) return false

        // Grab the profile so we can check modifiers AND get the seed
        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, ageId)
        if (AgeLifecycleManager.isDeadAge(profile)) return false
        if (!profile.modifiers.contains("tendrils")) return false

        // Rarity: 1 in 20 chunks
        if (random.nextInt(AgeFeatureTuning.rarityRollDivisor(profile, 20, "tendrils")) != 0) return false

        // Use the Age's seed so the material is consistent across the entire dimension!
        val ageRand = kotlin.random.Random(profile.seed)
        
        val materials = listOf(
            Blocks.STONE.defaultState,
            Blocks.DEEPSLATE.defaultState,
            Blocks.BLACKSTONE.defaultState,
            Blocks.BASALT.defaultState,
            Blocks.BONE_BLOCK.defaultState,
            Blocks.PURPUR_BLOCK.defaultState,
            Blocks.OAK_WOOD.defaultState, 
            Blocks.WARPED_WART_BLOCK.defaultState, 
            Blocks.AMETHYST_BLOCK.defaultState,
            mystcraft.flood.block.ModBlocks.CRYSTAL_BLOCK.defaultState 
        )
        
        // Single, clean material declaration
        val material = materials.random(ageRand)

        // Start somewhere high in the sky
        val startY = 80 + random.nextInt(100)
        val currentPos = BlockPos(origin.x, startY, origin.z).mutableCopy()

        var dx = (random.nextFloat() - 0.5f) * 2.0f
        var dy = (random.nextFloat() - 0.5f) * 2.0f
        var dz = (random.nextFloat() - 0.5f) * 2.0f

        val length = 100 + random.nextInt(150) 
        val baseRadius = 3.0f + random.nextFloat() * 3.0f

        for (step in 0..length) {
            dx += (random.nextFloat() - 0.5f) * 0.4f
            dy += (random.nextFloat() - 0.5f) * 0.4f
            dz += (random.nextFloat() - 0.5f) * 0.4f

            val mag = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
            dx /= mag; dy /= mag; dz /= mag

            currentPos.move((dx * 2).toInt(), (dy * 2).toInt(), (dz * 2).toInt())

            val currentRadius = baseRadius + kotlin.math.sin(step / 10.0) * 1.5f
            val rInt = Math.ceil(currentRadius).toInt()

            for (bx in -rInt..rInt) {
                for (by in -rInt..rInt) {
                    for (bz in -rInt..rInt) {
                        if (sqrt((bx * bx + by * by + bz * bz).toDouble()) <= currentRadius) {
                            val target = currentPos.add(bx, by, bz)
                            safeSetBlock(world, serverWorld, origin, target, material)
                        }
                    }
                }
            }
            if (currentPos.y < -60) break
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
        origin: BlockPos, target: BlockPos, state: BlockState
    ) {
        if (isSafeToRead(origin, target)) {
            val existing = world.getBlockState(target)
            if (existing.getHardness(world, target) < 0.0f) return 
            if (existing.isAir || existing.isReplaceable || existing.isOf(Blocks.WATER)) {
                world.setBlockState(target, state, 3)
            }
        } else {
            DeferredTreePlacer.add(serverWorld, target, state, true)
        }
    }
}
