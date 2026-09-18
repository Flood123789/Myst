package mystcraft.flood.generation.instability

import mystcraft.flood.block.ModBlocks
import mystcraft.flood.config.MystcraftConfig
import mystcraft.flood.entity.ModEntities
import mystcraft.flood.entity.ParadoxReaperEntity
import mystcraft.flood.entity.ReaperArrivalGrace
import mystcraft.flood.entity.SurfaceCling
import mystcraft.flood.generation.profile.AgeProfile
import net.minecraft.entity.SpawnReason
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d

/**
 * Population control for Paradox Reapers.
 *
 * An unstable Age produces Reapers the way it produces decay or lightning: as a symptom that
 * scales with its instability score. Spawning is therefore driven from the same per-second
 * instability pass rather than from vanilla mob spawning, which would tie Reapers to light
 * level and biome mob caps and let a player avoid them by carrying a torch.
 *
 * Placement looks for any air pocket with a solid neighbour, so Reapers arrive already attached
 * to whatever surface is available, including cave roofs and the undersides of overhangs.
 *
 * Decay is the strongest signal of where they belong. A spot walled by white or black decay is
 * both preferred over a clean one and rolled at a multiplied rate, which turns decay from slow
 * background scenery into the thing that hands the player a hunter: white decay creeping through
 * a base becomes an active threat, and the craters black decay leaves behind become nests.
 */
object ParadoxReaperSpawner {

    /** Called once per second per Age world from [InstabilityManager]. */
    fun tick(world: ServerWorld, profile: AgeProfile) {
        val balance = MystcraftConfig.current.paradoxReaper
        if (!balance.enabled || balance.maxAlivePerPlayer <= 0) return
        if (profile.spawning.noMobs) return
        if (profile.stability.isStable || !profile.stability.effectsEnabled) return

        val score = profile.stability.instabilityScore
        if (score < balance.minimumInstabilityScore) return

        // One sweep of the entity list per second serves both the cull and the per-player cap.
        // Querying by Box instead would mean a multi-hundred-block region scan for every player.
        val living = collectReapers(world)
        val despawnSquared = (balance.despawnDistance * balance.despawnDistance).toDouble()
        val remaining = cullDistantReapers(world, living, despawnSquared)

        val overload = score - balance.minimumInstabilityScore
        val chance = (balance.spawnChancePerSecond + overload * balance.spawnChancePerInstabilityPoint)
            .coerceAtMost(1.0f)

        val decayBoost = balance.decayChanceMultiplier
        val boosted = (chance * decayBoost).coerceAtMost(1.0f)

        for (player in world.players) {
            if (player.isSpectator || (player.isCreative && !balance.targetCreativePlayers)) continue
            if (ReaperArrivalGrace.isProtected(player)) continue
            if (!ReaperSpawnCadence.isReady(player.uuid, world.server.ticks)) continue

            // Rolled once against the optimistic ceiling, then confirmed against the rate that
            // actually applies to the spot that was found. That keeps the decay bonus
            // statistically honest while only paying for the search on rolls that could land.
            val roll = world.random.nextFloat()
            if (roll >= boosted) continue

            val nearby = remaining.count { it.squaredDistanceTo(player) <= despawnSquared }
            if (nearby >= balance.maxAlivePerPlayer) continue

            val found = findSpawnPos(world, player, balance.minSpawnDistance, balance.maxSpawnDistance) ?: continue
            if (roll >= (if (found.nearDecay) boosted else chance)) continue

            if (spawnAt(world, found.pos)) {
                ReaperSpawnCadence.markSpawned(
                    player.uuid, world.server.ticks, balance.spawnCooldownSeconds
                )
            }
        }
    }

    /** A usable spawn point, and whether decay walls it. */
    private data class SpawnCandidate(val pos: BlockPos, val nearDecay: Boolean)

    private fun collectReapers(world: ServerWorld): List<ParadoxReaperEntity> {
        val found = ArrayList<ParadoxReaperEntity>()
        world.iterateEntities().forEach { entity ->
            if (entity is ParadoxReaperEntity && !entity.isPetty) found.add(entity)
        }
        return found
    }

    private fun spawnAt(world: ServerWorld, pos: BlockPos): Boolean {
        val reaper = ModEntities.PARADOX_REAPER.create(world) ?: return false
        // Ages only ever produce the greater form. Lesser Reapers exist solely as backup a
        // greater one screeches for, which keeps the population ceiling meaningful.
        reaper.applyVariant(false)
        reaper.refreshPositionAndAngles(
            pos.x + 0.5,
            pos.y.toDouble(),
            pos.z + 0.5,
            world.random.nextFloat() * 360.0f,
            0.0f
        )
        reaper.initialize(world, world.getLocalDifficulty(pos), SpawnReason.EVENT, null, null)
        return world.spawnEntity(reaper)
    }

    /**
     * Picks an air block that has at least one solid neighbour, inside the configured shell
     * around the player. Candidates are sampled rather than searched exhaustively: this runs
     * every second for every player, so a bounded number of probes matters more than always
     * finding a spot.
     */
    private fun findSpawnPos(
        world: ServerWorld,
        player: ServerPlayerEntity,
        minDistance: Int,
        maxDistance: Int
    ): SpawnCandidate? {
        val random = world.random
        val span = (maxDistance - minDistance).coerceAtLeast(1)
        var fallback: SpawnCandidate? = null

        repeat(SAMPLE_ATTEMPTS) {
            val angle = random.nextDouble() * Math.PI * 2.0
            val distance = minDistance + random.nextDouble() * span
            val x = player.x + MathHelper.cos(angle.toFloat()) * distance
            val z = player.z + MathHelper.sin(angle.toFloat()) * distance
            val column = BlockPos.ofFloored(x, player.y, z)
            if (!world.isChunkLoaded(column.x shr 4, column.z shr 4)) return@repeat

            // A random Y probe only had a 1-in-21 chance to hit the usable air immediately above
            // the floor in flat Ages. Check the bounded vertical column instead. This also finds
            // cave roofs and the walls of black-decay craters without loading distant chunks.
            for (offset in VERTICAL_OFFSETS) {
                val candidate = BlockPos(column.x, MathHelper.floor(player.y) + offset, column.z)
                if (candidate.y < world.bottomY || candidate.y >= world.topY) continue
                if (player.squaredDistanceTo(Vec3d.ofCenter(candidate)) < (minDistance * minDistance).toDouble()) continue

                val evaluated = evaluateSpawn(world, candidate) ?: continue
                // A decay-walled spot wins outright; a clean one is only kept as a fallback, so a
                // rotting Age reliably produces its hunters out of the rot.
                if (evaluated.nearDecay) return evaluated
                if (fallback == null) fallback = evaluated
            }
        }
        return fallback
    }

    /** Read-only snapshot used by /reaper status to explain silent natural-spawn gates. */
    fun describeStatus(world: ServerWorld, profile: AgeProfile, player: ServerPlayerEntity): String {
        val balance = MystcraftConfig.current.paradoxReaper
        val living = collectReapers(world)
        val rangeSquared = (balance.despawnDistance * balance.despawnDistance).toDouble()
        val nearby = living.count { it.squaredDistanceTo(player) <= rangeSquared }
        val eligible = !player.isSpectator && (!player.isCreative || balance.targetCreativePlayers)
        val candidate = if (eligible) {
            findSpawnPos(world, player, balance.minSpawnDistance, balance.maxSpawnDistance)
        } else null
        val surface = when {
            !eligible -> "not checked"
            candidate == null -> "none found"
            candidate.nearDecay -> "decay surface found"
            else -> "clean surface found"
        }
        return "instability=${profile.stability.instabilityScore}, stable=${profile.stability.isStable}, " +
            "effects=${profile.stability.effectsEnabled}, noMobs=${profile.spawning.noMobs}, " +
            "playerMode=${if (player.isCreative) "creative" else if (player.isSpectator) "spectator" else "survival"}, " +
            "creativeIncluded=${balance.targetCreativePlayers}, nearbyReapers=$nearby/${balance.maxAlivePerPlayer}, " +
            "spawnSurface=$surface"
    }

    /**
     * Air a greater Reaper fits inside, touching something it can grip; null when unusable.
     * The same surface probe reports whether what it found was decay.
     */
    private fun evaluateSpawn(world: ServerWorld, pos: BlockPos): SpawnCandidate? {
        val size = ParadoxReaperEntity.GREATER_DIMENSIONS
        val half = size.width.toDouble() / 2.0
        val fitting = Box(
            pos.x + 0.5 - half, pos.y.toDouble(), pos.z + 0.5 - half,
            pos.x + 0.5 + half, pos.y + size.height.toDouble(), pos.z + 0.5 + half
        )
        if (!world.isSpaceEmpty(fitting)) return null

        // Use the same collision probe as the entity itself. The former centre-block offsets
        // missed an ordinary floor (one block below the feet), producing candidates that looked
        // attached to the spawner but immediately fell when the Reaper evaluated them.
        if (SurfaceCling.findSupport(world, fitting, null) == null) return null

        // Scan the thin shell around the whole 2x2 body. This catches decay beneath the feet,
        // beside either half of the body, and around irregular black-decay crater walls.
        val shell = fitting.expand(SurfaceCling.PROBE_DISTANCE + 0.05)
        var nearDecay = false
        for (nearby in BlockPos.iterate(
            MathHelper.floor(shell.minX), MathHelper.floor(shell.minY), MathHelper.floor(shell.minZ),
            MathHelper.floor(shell.maxX), MathHelper.floor(shell.maxY), MathHelper.floor(shell.maxZ)
        )) {
            val state = world.getBlockState(nearby)
            if (state.isOf(ModBlocks.WHITE_DECAY) || state.isOf(ModBlocks.BLACK_DECAY)) {
                nearDecay = true
                break
            }
        }
        return SpawnCandidate(pos, nearDecay)
    }

    /**
     * Discards Reapers no player is near and returns those that survived. Without this, a player
     * who outruns a hunt leaves a permanent trail of them and the Age's entity count only climbs.
     */
    private fun cullDistantReapers(
        world: ServerWorld,
        reapers: List<ParadoxReaperEntity>,
        despawnSquared: Double
    ): List<ParadoxReaperEntity> {
        if (world.players.isEmpty()) return reapers
        return reapers.filter { reaper ->
            if (reaper.isPersistent) return@filter true
            val nearest = world.players.minOf { it.squaredDistanceTo(reaper) }
            if (nearest > despawnSquared) {
                reaper.discard()
                false
            } else {
                true
            }
        }
    }

    private const val SAMPLE_ATTEMPTS = 16
    private const val VERTICAL_SPREAD = 10
    private val VERTICAL_OFFSETS = IntArray(VERTICAL_SPREAD * 2 + 1) { index ->
        if (index == 0) 0 else if (index % 2 == 1) -(index + 1) / 2 else index / 2
    }
}
