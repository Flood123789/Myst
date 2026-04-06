package mystcraft.flood.generation

import com.mojang.serialization.Codec
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.item.ModItems
import net.minecraft.block.Blocks
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.Heightmap
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.util.FeatureContext

class AbandonedArchiveFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {

    override fun generate(context: FeatureContext<DefaultFeatureConfig>): Boolean {
        val world = context.world
        val random = context.random
        val origin = context.origin
        val serverWorld = world.toServerWorld()

        // 1. Only spawn in Mystcraft Ages
        if (serverWorld.registryKey.value.namespace != MystcraftReforged.MOD_ID) return false

        // Now a 1 in 300 chance!
        if (random.nextInt(300) != 0) return false

        // 3. Find the absolute surface of the terrain
        val topY = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, origin.x, origin.z)
        
        // Don't spawn them underwater or in the sky
        if (topY < 50 || topY > 150) return false 
        
        val centerPos = BlockPos(origin.x, topY, origin.z)
        // Don't build libraries on the ocean!
        if (world.getBlockState(centerPos.down()).isOf(Blocks.WATER)) return false

        // 4. The Building Blueprint (Radius 3 = 7x7 building)
        val radius = 3
        val height = 4

        for (x in -radius..radius) {
            for (y in -1..height) {
                for (z in -radius..radius) {
                    val currentPos = centerPos.add(x, y, z)
                    val isEdge = x == -radius || x == radius || z == -radius || z == radius

                    if (y == -1) {
                        // THE FLOOR
                        world.setBlockState(currentPos, Blocks.COBBLESTONE.defaultState, 3)
                    } 
                    else if (isEdge && y < height) {
                        // THE WALLS: Procedurally mixed and ruined
                        val wallState = when (random.nextInt(100)) {
                            in 0..40 -> Blocks.STONE_BRICKS.defaultState
                            in 41..60 -> Blocks.CRACKED_STONE_BRICKS.defaultState
                            in 61..75 -> Blocks.MOSSY_STONE_BRICKS.defaultState
                            in 76..85 -> Blocks.BOOKSHELF.defaultState // Bookshelves embedded in walls!
                            else -> Blocks.AIR.defaultState // 15% chance of a hole in the wall
                        }
                        world.setBlockState(currentPos, wallState, 3)
                    } 
                    else if (y == height) {
                        // THE ROOF: 40% caved in
                        if (random.nextFloat() < 0.6f) {
                            world.setBlockState(currentPos, Blocks.SPRUCE_SLAB.defaultState, 3)
                        }
                    } 
                    else {
                        // THE INTERIOR
                        if (x == 0 && y == 0 && z == 0) {
                            // Center Pedestal / Chest
                            world.setBlockState(currentPos, Blocks.CHEST.defaultState, 3)
                            
                            // Inject the Lost Pages directly into the chest's inventory!
                            val blockEntity = world.getBlockEntity(currentPos)
                            if (blockEntity is ChestBlockEntity) {
                                val pageCount = random.nextInt(4) + 2 // 2 to 5 pages
                                for (i in 0 until pageCount) {
                                    val randomSlot = random.nextInt(blockEntity.size())
                                    blockEntity.setStack(randomSlot, ItemStack(ModItems.LOST_PAGE))
                                }
                            }
                        } else if (random.nextFloat() < 0.08f) {
                            // 8% chance to spawn a cobweb in empty interior air blocks
                            world.setBlockState(currentPos, Blocks.COBWEB.defaultState, 3)
                        } else {
                            world.setBlockState(currentPos, Blocks.AIR.defaultState, 3)
                        }
                    }
                }
            }
        }
        return true
    }
}