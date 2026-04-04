package mystcraft.flood.generation

import com.mojang.serialization.Codec
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.block.ModBlocks
import mystcraft.flood.generation.profile.AgeProfileManager
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.util.FeatureContext

class StarFissureFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {

    override fun generate(context: FeatureContext<DefaultFeatureConfig>): Boolean {
        val world = context.world
        val pos = context.origin
        val random = context.random
        val serverWorld = world.toServerWorld()

        // Only spawn these in our custom dimension!
        if (serverWorld.registryKey.value.namespace != MystcraftReforged.MOD_ID) return false

        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, serverWorld.registryKey.value)
        val score = profile.stability.instabilityScore

        // ==========================================
        // DYNAMIC SPAWN CHANCE
        // ==========================================
        // Stable Age: 0.5% chance per chunk
        // Highly Unstable Age: Scales up to a maximum of 10% chance per chunk
        var chunkChance = 0.005f 
        if (score > 0) {
            chunkChance += (score * 0.001f)
        }
        chunkChance = chunkChance.coerceAtMost(0.10f)

        if (random.nextFloat() > chunkChance) return false

        // ==========================================
        // DRILL THE HOLE
        // ==========================================
        // Pick a random spot in this specific 16x16 chunk
        val x = pos.x + random.nextInt(16)
        val z = pos.z + random.nextInt(16)

        // 1. Overwrite Bedrock at Y = -64
        val fissurePos = BlockPos(x, -64, z)
        world.setBlockState(fissurePos, ModBlocks.STAR_FISSURE.defaultState, 3)

        // 2. Erase every block above it so it's visible from the surface!
        for (y in -63 until world.topY) {
            world.setBlockState(BlockPos(x, y, z), Blocks.AIR.defaultState, 3)
        }

        return true
    }
}