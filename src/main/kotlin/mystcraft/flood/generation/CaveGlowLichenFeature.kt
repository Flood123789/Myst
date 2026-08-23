package mystcraft.flood.generation

import com.mojang.serialization.Codec
import mystcraft.flood.config.MystcraftConfig
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.generation.profile.TerrainType
import net.minecraft.block.Blocks
import net.minecraft.state.property.Properties
import net.minecraft.util.math.Direction
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.util.FeatureContext

class CaveGlowLichenFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {
    override fun generate(context: FeatureContext<DefaultFeatureConfig>): Boolean {
        val world = context.world
        val serverWorld = world.toServerWorld()
        if (!AgeSubdimensionManager.isPrimaryAgeRealm(serverWorld.registryKey.value)) return false
        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, serverWorld.registryKey.value)
        if (profile.terrainType != TerrainType.CAVES) return false

        val attempts = MystcraftConfig.current.worldGeneration.caveGlowLichenAttemptsPerChunk
        if (attempts <= 0) return false
        val random = context.random
        var placed = false
        repeat(attempts) {
            val x = context.origin.x + random.nextInt(16)
            val z = context.origin.z + random.nextInt(16)
            val y = world.bottomY + 2 + random.nextInt((world.height - 4).coerceAtLeast(1))
            val pos = net.minecraft.util.math.BlockPos(x, y, z)
            if (!world.getBlockState(pos).isAir) return@repeat

            val directions = Direction.values().toMutableList().also { list ->
                java.util.Collections.shuffle(list, java.util.Random(random.nextLong()))
            }
            for (direction in directions) {
                val support = pos.offset(direction)
                if (!world.getBlockState(support).isOpaqueFullCube(world, support)) continue
                val property = when (direction) {
                    Direction.DOWN -> Properties.DOWN
                    Direction.UP -> Properties.UP
                    Direction.NORTH -> Properties.NORTH
                    Direction.SOUTH -> Properties.SOUTH
                    Direction.WEST -> Properties.WEST
                    Direction.EAST -> Properties.EAST
                }
                val state = Blocks.GLOW_LICHEN.defaultState.with(property, true)
                if (!state.canPlaceAt(world, pos)) continue
                world.setBlockState(pos, state, 2)
                placed = true
                break
            }
        }
        return placed
    }
}
