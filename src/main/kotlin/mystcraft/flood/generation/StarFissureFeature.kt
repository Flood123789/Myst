package mystcraft.flood.generation

import com.mojang.serialization.Codec
import mystcraft.flood.MystcraftReforged
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraft.world.Heightmap
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
        if (serverWorld.registryKey.value.namespace != MystcraftReforged.MOD_ID) return false

        // 2. Rarity Check: 1 in 200 chunks (Make these rare lifelines!)
        if (random.nextInt(200) != 0) return false

        // 3. Find the absolute surface of the terrain
        val topY = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, origin.x, origin.z)
        if (topY < 20) return false // Don't spawn if the world is already basically at bedrock
        
        val centerPos = BlockPos(origin.x, topY, origin.z)

        // ==========================================
        // PHASE 1: THE FLOATING DEBRIS (Anti-Gravity)
        // ==========================================
        val debrisRadius = 8
        for (x in -debrisRadius..debrisRadius) {
            for (y in 3..15) { // Floating 3 to 15 blocks ABOVE the surface
                for (z in -debrisRadius..debrisRadius) {
                    val distance = sqrt((x * x + y * y / 4.0 + z * z).toDouble()) // Elliptical distance
                    
                    if (distance <= debrisRadius && random.nextFloat() < 0.02f) {
                        val currentPos = centerPos.add(x, y, z)
                        val block = when (random.nextInt(100)) {
                            in 0..60 -> Blocks.OBSIDIAN.defaultState
                            in 61..85 -> Blocks.CRYING_OBSIDIAN.defaultState
                            else -> Blocks.AMETHYST_BLOCK.defaultState
                        }
                        world.setBlockState(currentPos, block, 3)
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

        // Drill from the surface all the way down to the bottom of the world
        for (y in topY downTo -64) {
            
            // The fissure "wobbles" as it goes down, making it jagged
            driftX += (random.nextFloat() - 0.5f) * 1.5f
            driftZ += (random.nextFloat() - 0.5f) * 1.5f
            
            // It gets slightly narrower at the bottom, but never closes
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

                    // CORE VOID: The center is completely erased
                    if (distance <= currentRadius) {
                        // THE CATCHER: Put a floor at the bottom so players don't fall into the actual void!
                        if (y == -64) {
                            // If you have a custom block, change this to ModBlocks.STAR_FISSURE.defaultState!
                            world.setBlockState(currentPos,ModBlocks.STAR_FISSURE.defaultState, 3)
                        } else {
                            world.setBlockState(currentPos, Blocks.AIR.defaultState, 3)
                        }
                    } 
                    // CORRUPTED EDGE: The blocks touching the void are turned to magical stone
                    else if (distance <= currentRadius + 1.5f) {
                        // Don't overwrite air from previous passes so it doesn't look like a solid tube
                        if (!world.isAir(currentPos)) {
                            val edgeBlock = when (random.nextInt(100)) {
                                in 0..60 -> Blocks.OBSIDIAN.defaultState
                                in 61..85 -> Blocks.CRYING_OBSIDIAN.defaultState
                                in 86..95 -> Blocks.AMETHYST_BLOCK.defaultState
                                else -> Blocks.AIR.defaultState // Tiny fractures in the wall
                            }
                            world.setBlockState(currentPos, edgeBlock, 3)
                        }
                    }
                }
            }
        }

        return true
    }
}