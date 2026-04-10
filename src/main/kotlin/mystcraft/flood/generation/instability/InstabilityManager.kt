package mystcraft.flood.generation.instability

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.block.ModBlocks 
import mystcraft.flood.generation.profile.AgeProfileManager
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.block.Blocks
import net.minecraft.entity.EntityType
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World.ExplosionSourceType

object InstabilityManager {

    fun register() {
        ServerTickEvents.END_WORLD_TICK.register { world ->
            if (world.registryKey.value.namespace == MystcraftReforged.MOD_ID) {
                processInstability(world)
            }
        }
    }

    private fun processInstability(world: ServerWorld) {
        if (world.time % 20L != 0L) return

        val profile = AgeProfileManager.getOrGenerateProfile(world.server, world.registryKey.value)
        
        if (profile.stability.isStable || !profile.stability.effectsEnabled || profile.stability.instabilityScore <= 0) return

        val score = profile.stability.instabilityScore

        for (player in world.players) {
            if (player.isCreative || player.isSpectator) continue

            val rand = world.random

            // ==========================================
            // TIER 1: Mild Decay (Score 10+)
            // ==========================================
            if (score >= 10 && rand.nextFloat() < 0.05f) { 
                player.addStatusEffect(StatusEffectInstance(StatusEffects.SLOWNESS, 200, 0, false, false))
                player.addStatusEffect(StatusEffectInstance(StatusEffects.MINING_FATIGUE, 200, 0, false, false))
            }

            // ==========================================
            // TIER 2: Moderate Decay (Score 20+)
            // ==========================================
            if (score >= 20 && rand.nextFloat() < 0.03f) { 
                player.addStatusEffect(StatusEffectInstance(StatusEffects.NAUSEA, 160, 0, false, false))
                player.addStatusEffect(StatusEffectInstance(StatusEffects.POISON, 100, 0, false, false))
            }

            // ==========================================
            // TIER 3: Severe Decay (Score 30+)
            // ==========================================
            if (score >= 30 && rand.nextFloat() < 0.02f) { 
                val offsetX = rand.nextInt(20) - 10
                val offsetZ = rand.nextInt(20) - 10
                val strikePos = player.blockPos.add(offsetX, 0, offsetZ)
                val topPos = world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, strikePos)

                val lightning = EntityType.LIGHTNING_BOLT.create(world)
                if (lightning != null) {
                    lightning.refreshPositionAfterTeleport(Vec3d.ofBottomCenter(topPos))
                    world.spawnEntity(lightning)
                }
            }

            // ==========================================
            // TIER 4: The World Eater (Score 40+)
            // ==========================================
            if (score >= 40) {
                val overload = score - 40
                
                // Frequency Math: Base 5% chance. Adds 1% for every 1 point over 40. Coerced to max 100%.
                val spawnChance = (0.05f + (overload * 0.01f)).coerceAtMost(1.0f)
                
                if (rand.nextFloat() < spawnChance) {
                    // Quantity Math: Base 1 seed. Adds 1 extra seed for every 15 points over 40. Coerced to max 10 seeds.
                    val maxSeeds = (1 + (overload / 15)).coerceAtMost(10)
                    
                    for (i in 0 until maxSeeds) {
                        val offsetX = rand.nextInt(40) - 20 
                        val offsetZ = rand.nextInt(40) - 20
                        val offsetY = rand.nextInt(20) - 10
                        
                        val seedPos = player.blockPos.add(offsetX, offsetY, offsetZ)
                        val targetState = world.getBlockState(seedPos)
                        
                        if (!targetState.isAir && !targetState.isOf(Blocks.BEDROCK)) {
                            world.setBlockState(seedPos, ModBlocks.BLACK_DECAY.defaultState, 3)
                        }
                    }
                }
            }

            // ==========================================
            // TIER 5: Critical Collapse (Score 50+)
            // Symptoms: Spontaneous small explosions!
            // ==========================================
            if (score >= 50 && rand.nextFloat() < 0.01f) { 
                val offsetX = rand.nextInt(10) - 5
                val offsetZ = rand.nextInt(10) - 5
                val boomPos = player.blockPos.add(offsetX, 0, offsetZ)
                
                world.createExplosion(
                    null, 
                    boomPos.x.toDouble(), 
                    boomPos.y.toDouble(), 
                    boomPos.z.toDouble(), 
                    2.0f, 
                    false, 
                    ExplosionSourceType.MOB
                )
            }
        }
    }
}
