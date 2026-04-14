package mystcraft.flood.generation

import com.mojang.serialization.Codec
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.profile.AgeProfileManager
import net.minecraft.block.Blocks
import net.minecraft.registry.tag.BlockTags
import net.minecraft.util.math.BlockPos
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.util.FeatureContext

class DenseOresFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {

    override fun generate(context: FeatureContext<DefaultFeatureConfig>): Boolean {
        val world = context.world
        val pos = context.origin
        val random = context.random
        val serverWorld = world.toServerWorld()

        // 1. Only run in Mystcraft Ages
        if (serverWorld.registryKey.value.namespace != MystcraftReforged.MOD_ID) return false

        // 2. Check the profile to see if Dense Ores is active!
        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, serverWorld.registryKey.value)
        if (AgeLifecycleManager.isDeadAge(profile)) return false
        
        // NOTE: Adjust this check to match however you are storing Page data in your AgeProfile!
        // For example, if you use a boolean flag or a list of active page names:
        val hasDenseOres = profile.modifiers.contains("dense_ores") 
        if (!hasDenseOres) return false

        // 3. Define the ores we want to multiply (Standard State, Deepslate State, Max Y Level)
        val ores = listOf(
            Triple(Blocks.DIAMOND_ORE.defaultState, Blocks.DEEPSLATE_DIAMOND_ORE.defaultState, 16),
            Triple(Blocks.GOLD_ORE.defaultState, Blocks.DEEPSLATE_GOLD_ORE.defaultState, 32),
            Triple(Blocks.IRON_ORE.defaultState, Blocks.DEEPSLATE_IRON_ORE.defaultState, 64),
            Triple(Blocks.COAL_ORE.defaultState, Blocks.DEEPSLATE_COAL_ORE.defaultState, 128)
        )

        // 4. THE MULTIPLIER: How many extra veins to jam into this single chunk
        // 40 extra veins per chunk is absolutely insane and will look amazing.
        val extraVeinsPerChunk = 40

        for (i in 0 until extraVeinsPerChunk) {
            val targetOre = ores.random()
            val standardState = targetOre.first
            val deepslateState = targetOre.second
            val maxY = targetOre.third

            // Pick a random Y level based on the ore's max height (down to bedrock at -64)
            val y = random.nextInt(maxY + 64) - 64 
            val x = pos.x + random.nextInt(16)
            val z = pos.z + random.nextInt(16)
            val startPos = BlockPos(x, y, z)

            // Create a blob size between 4 and 8 blocks
            val veinSize = 4 + random.nextInt(5)

            // Inject the blob into the world
            for (j in 0 until veinSize) {
                // Slightly cluster them together
                val placePos = startPos.add(
                    random.nextInt(3) - 1,
                    random.nextInt(3) - 1,
                    random.nextInt(3) - 1
                )

                val targetState = world.getBlockState(placePos)
                
                // Only replace standard stone/deepslate so we don't accidentally overwrite chests, spawners, or the Star Fissure!
                if (targetState.isIn(BlockTags.STONE_ORE_REPLACEABLES)) {
                    world.setBlockState(placePos, standardState, 3)
                } else if (targetState.isIn(BlockTags.DEEPSLATE_ORE_REPLACEABLES)) {
                    world.setBlockState(placePos, deepslateState, 3)
                }
            }
        }

        return true
    }
}
