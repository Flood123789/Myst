package mystcraft.flood.generation.instability

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.block.ModBlocks 
import mystcraft.flood.generation.AgeLifecycleManager
import mystcraft.flood.generation.physics.LowGravityPhysics
import mystcraft.flood.generation.profile.AgeProfile
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.config.MystcraftConfig
import net.minecraft.entity.effect.StatusEffect
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.block.Blocks
import net.minecraft.entity.EntityType
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World.ExplosionSourceType

/**
 * Applies runtime consequences of an Age's instability score.
 *
 * Lightweight physics runs every world tick; random status, lightning, decay, and explosion
 * checks run once per second. Thresholds define escalation order while balance config controls
 * probabilities, keeping save meaning stable when server owners tune effect frequency.
 */
object InstabilityManager {

    fun register() {
        ServerTickEvents.END_WORLD_TICK.register { world ->
            if (world.registryKey.value.namespace == MystcraftReforged.MOD_ID) {
                processInstability(world)
            }
        }
    }

    private fun processInstability(world: ServerWorld) {
        val profile = AgeProfileManager.getOrGenerateProfile(world.server, world.registryKey.value)
        if (AgeLifecycleManager.isDeadAge(profile)) return

        // Gravity affects motion and must remain smooth, while destructive/random effects are
        // deliberately throttled to a one-second cadence.
        applyLowGravity(world, profile)
        if (world.time % 20L != 0L) return

        processAgeEffect(world, profile)
        if (profile.stability.isStable || !profile.stability.effectsEnabled || profile.stability.instabilityScore <= 0) return

        val score = profile.stability.instabilityScore
        val balance = MystcraftConfig.current.instability
        val naturalDecayState = getNaturalDecayState(profile)

        for (player in world.players) {
            if (player.isCreative || player.isSpectator) continue

            val rand = world.random

            // ==========================================
            // TIER 1: Mild Decay (Score 45+)
            // ==========================================
            if (score >= InstabilityThresholds.MILD_DECAY && rand.nextFloat() < balance.mildChancePerSecond) {
                player.addStatusEffect(StatusEffectInstance(StatusEffects.SLOWNESS, balance.mildDurationTicks, 0, false, false))
                player.addStatusEffect(StatusEffectInstance(StatusEffects.MINING_FATIGUE, balance.mildDurationTicks, 0, false, false))
            }

            // ==========================================
            // TIER 2: Moderate Decay (Score 65+)
            // ==========================================
            if (score >= InstabilityThresholds.MODERATE_DECAY && rand.nextFloat() < balance.moderateChancePerSecond) {
                player.addStatusEffect(StatusEffectInstance(StatusEffects.DARKNESS, balance.darknessDurationTicks, 0, false, false))
                player.addStatusEffect(StatusEffectInstance(StatusEffects.POISON, balance.poisonDurationTicks, 0, false, false))
            }

            // ==========================================
            // TIER 3: Severe Decay (Score 80+)
            // ==========================================
            if (score >= InstabilityThresholds.SEVERE_DECAY && rand.nextFloat() < balance.severeChancePerSecond) {
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
            // TIER 4: The World Eater (Score 100+)
            // ==========================================
            if (score >= InstabilityThresholds.WORLD_EATER) {
                val overload = score - InstabilityThresholds.WORLD_EATER
                
                // Frequency Math: Base 5% chance. Adds 1% for every point over the threshold. Coerced to max 100%.
                val spawnChance = (balance.worldEaterBaseChancePerSecond + (overload * balance.worldEaterChancePerPoint)).coerceAtMost(1.0f)
                
                if (rand.nextFloat() < spawnChance) {
                    // Quantity Math: Base 1 seed. Adds 1 extra seed for every 15 overload points. Coerced to max 10 seeds.
                    val maxSeeds = (1 + (overload / 15)).coerceAtMost(10)
                    
                    for (i in 0 until maxSeeds) {
                        val offsetX = rand.nextInt(40) - 20 
                        val offsetZ = rand.nextInt(40) - 20
                        val offsetY = rand.nextInt(20) - 10
                        
                        val seedPos = player.blockPos.add(offsetX, offsetY, offsetZ)
                        val targetState = world.getBlockState(seedPos)
                        
                        if (!targetState.isAir && !targetState.isOf(Blocks.BEDROCK)) {
                            world.setBlockState(seedPos, naturalDecayState, 3)
                        }
                    }
                }
            }

            // ==========================================
            // TIER 5: Critical Collapse (Score 115+)
            // Symptoms: Spontaneous small explosions!
            // ==========================================
            if (score >= InstabilityThresholds.CRITICAL_COLLAPSE && rand.nextFloat() < balance.criticalChancePerSecond) {
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

    private fun processAgeEffect(world: ServerWorld, profile: AgeProfile) {
        val effectId = profile.ageEffect.effectId ?: return
        if (!profile.ageEffect.enabled) return

        val statusEffect = net.minecraft.registry.Registries.STATUS_EFFECT.get(net.minecraft.util.Identifier.tryParse(effectId))
        if (statusEffect == null) return

        for (player in world.players) {
            if (player.isCreative || player.isSpectator) continue
            if (world.random.nextFloat() < 0.06f) {
                applyAgeEffect(player, statusEffect)
            }
        }
    }

    private fun applyAgeEffect(player: net.minecraft.server.network.ServerPlayerEntity, statusEffect: StatusEffect) {
        if (statusEffect.isInstant) {
            statusEffect.applyInstantEffect(null, null, player, 0, 1.0)
            return
        }

        player.addStatusEffect(
            StatusEffectInstance(statusEffect, 180, 0, false, false, false)
        )
    }

    private fun applyLowGravity(world: ServerWorld, profile: AgeProfile) {
        val gravityScale = profile.physics.gravityScale
        if (gravityScale >= 0.999f) return

        for (player in world.players) {
            LowGravityPhysics.apply(player, gravityScale)
        }
    }

    private fun getNaturalDecayState(profile: AgeProfile) =
        if ((profile.seed and 1L) == 0L) ModBlocks.BLACK_DECAY.defaultState else ModBlocks.WHITE_DECAY.defaultState
}
