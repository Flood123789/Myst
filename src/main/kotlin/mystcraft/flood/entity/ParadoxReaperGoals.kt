package mystcraft.flood.entity

import net.minecraft.entity.ai.goal.Goal
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.util.EnumSet

/**
 * Steering goals for [ParadoxReaperEntity].
 *
 * None of them use vanilla path navigation. A Reaper is allowed to cross any surface, so a
 * ground-only A* solver would reject exactly the routes that make the mob interesting. Instead
 * each goal writes a world-space direction and lets the entity's surface locomotion resolve how
 * that direction maps onto the floor, wall, or ceiling it currently occupies.
 *
 * Which goal runs is decided by [ReaperState], not by whether a target happens to exist. A
 * dormant Reaper has no movement goal at all: it sits anchored and listens.
 */

/**
 * Runs the target down while it is actually in sight. Steers straight at it and lets terrain
 * transitions happen naturally: walking into a wall becomes a climb, cresting a lip becomes a
 * ceiling crawl.
 */
class ParadoxReaperHuntGoal(private val reaper: ParadoxReaperEntity) : Goal() {

    private var lungeCooldown = 0
    private var blockedTicks = 0
    private var escapeTicks = 0
    private var escapeHeading: Vec3d = Vec3d.ZERO
    private var lastPosition: Vec3d? = null

    init {
        controls = EnumSet.of(Control.MOVE, Control.LOOK)
    }

    override fun canStart(): Boolean =
        reaper.state == ReaperState.PURSUIT && reaper.target?.isAlive == true

    override fun shouldContinue(): Boolean = canStart()

    override fun stop() {
        reaper.setDesiredMove(Vec3d.ZERO)
        blockedTicks = 0
        escapeTicks = 0
        lastPosition = null
    }

    override fun shouldRunEveryTick(): Boolean = true

    override fun tick() {
        val target = reaper.target ?: return
        if (lungeCooldown > 0) lungeCooldown--

        // Aim at the target's mid-height rather than its feet, so a Reaper on a ceiling steers
        // toward the body it is about to drop onto instead of the floor beneath it.
        val from = reaper.pos.add(0.0, reaper.height * 0.5, 0.0)
        val to = target.pos.add(0.0, target.height * 0.5, 0.0)
        val toTarget = to.subtract(from)

        // A direct chase is normally best. When the body has genuinely stopped moving, hold a
        // clear lateral tangent long enough to get its 2x2 hitbox around the obstruction, then
        // resume the direct line. This complements surface transitions: walls are climbed, while
        // pillars, narrow gaps, and awkward inside corners are skirted.
        if (escapeTicks > 0) {
            reaper.setDesiredMove(escapeHeading)
            escapeTicks--
        } else {
            reaper.setDesiredMove(toTarget)
        }
        reaper.lookControl.lookAt(target, 90.0f, 90.0f)

        val previous = lastPosition
        val movedSquared = previous?.squaredDistanceTo(reaper.pos) ?: Double.MAX_VALUE
        lastPosition = reaper.pos
        // Adhesion deliberately pushes into a wall or ceiling every tick, which sets vanilla's
        // collision flags even while the Reaper is making full tangential progress. Position is
        // the only reliable indication that the body is actually wedged.
        val stalled = movedSquared < MIN_PROGRESS_SQUARED
        blockedTicks = if (stalled) blockedTicks + 1 else 0

        if (blockedTicks >= BLOCKED_BEFORE_ESCAPE) {
            // A planned hop is ideal for a real gap. If no valid landing exists, sidestep and let
            // ordinary surface walking take the long way around the obstruction.
            if (reaper.tryTraversalJump(toTarget)) {
                blockedTicks = 0
                escapeTicks = 0
                return
            }
            val detour = reaper.findUnstuckHeading(toTarget)
            if (detour != null) {
                escapeHeading = detour
                escapeTicks = ESCAPE_TICKS
                reaper.setDesiredMove(escapeHeading)
            }
            blockedTicks = 0
        }

        val distanceSquared = reaper.squaredDistanceTo(target)
        if (reaper.tryReaperAttack(target)) return

        // Close the last few blocks with a leap. Only from a surface, and only when the target is
        // actually visible, so the Reaper does not fling itself at a wall.
        if (lungeCooldown == 0 && reaper.clingFace != null && distanceSquared <= LUNGE_RANGE_SQUARED) {
            if (reaper.canSee(target)) {
                reaper.lunge(toTarget, LUNGE_POWER)
                lungeCooldown = LUNGE_COOLDOWN_TICKS
            }
        }
    }

    private companion object {
        const val LUNGE_RANGE_SQUARED = 36.0
        const val LUNGE_POWER = 0.62
        const val LUNGE_COOLDOWN_TICKS = 70
        const val BLOCKED_BEFORE_ESCAPE = 8
        const val ESCAPE_TICKS = 24
        const val MIN_PROGRESS_SQUARED = 0.0025
    }
}

/**
 * Owner-following and shelter behavior for the Reaper spawned by Little Anomaly's ability. The
 * floating [LittleAnomalyEntity] itself never enters this goal.
 */
class ParadoxReaperPetGoal(private val reaper: ParadoxReaperEntity) : Goal() {

    private var blockedTicks = 0
    private var returningToOwner = false
    private var lastPosition: Vec3d? = null
    private var surfacePath: List<ReaperSurfaceNode> = emptyList()
    private var pathIndex = 0
    private var repathTicks = 0
    private var pathTarget: Vec3d? = null
    private var wanderHeading = Vec3d.ZERO
    private var wanderTicks = 0

    init {
        controls = EnumSet.of(Control.MOVE, Control.LOOK)
    }

    override fun canStart(): Boolean {
        val owner = reaper.pettyOwner() ?: return false
        return reaper.isPetty && reaper.pettyRestMode != PettyReaperRestMode.SEEKING_SHELTER &&
            owner.isAlive && owner.world === reaper.world && !reaper.hasControllingPassenger()
    }

    override fun shouldContinue(): Boolean = canStart()

    override fun shouldRunEveryTick(): Boolean = true

    override fun stop() {
        reaper.setDesiredMove(Vec3d.ZERO)
        returningToOwner = false
        blockedTicks = 0
        lastPosition = null
        clearSurfacePath()
        wanderHeading = Vec3d.ZERO
        wanderTicks = 0
    }

    override fun tick() {
        val owner = reaper.pettyOwner() ?: return
        if (reaper.rescuePettyToOwner()) return

        if (reaper.pettyRestMode == PettyReaperRestMode.DORMANT) {
            reaper.setDesiredMove(Vec3d.ZERO)
            returningToOwner = false
            blockedTicks = 0
            lastPosition = reaper.pos
            return
        }

        val quarry = reaper.target?.takeIf { reaper.canPettyAttack(it) }
        if (quarry != null) {
            returningToOwner = false
            val from = reaper.pos.add(0.0, reaper.height * 0.5, 0.0)
            val to = quarry.pos.add(0.0, quarry.height * 0.5, 0.0)
            reaper.setDesiredMove(to.subtract(from))
            reaper.lookControl.lookAt(quarry, 90.0f, 90.0f)
            reaper.tryReaperAttack(quarry)
            attemptTraversal(to.subtract(from))
            return
        }

        val toOwner = owner.pos.subtract(reaper.pos)
        if (reaper.isPermanentCompanion && reaper.companionCommand == ReaperCompanionCommand.STAY) {
            clearSurfacePath()
            reaper.setDesiredMove(Vec3d.ZERO)
            returningToOwner = false
            blockedTicks = 0
            lastPosition = reaper.pos
            return
        }

        if (reaper.isPermanentCompanion && reaper.companionCommand == ReaperCompanionCommand.WANDER) {
            tickBoundedWander(owner.pos, toOwner)
            return
        }

        returningToOwner = PettyReaperFollowLeash.shouldFollow(toOwner.lengthSquared(), returningToOwner)
        if (returningToOwner) {
            val direction = ownerRouteDirection(owner.pos, toOwner)
            reaper.setDesiredMove(direction)
            reaper.lookControl.lookAt(owner, 70.0f, 70.0f)
            attemptTraversal(direction)
            return
        }

        clearSurfacePath()
        reaper.setDesiredMove(Vec3d.ZERO)
        blockedTicks = 0
        lastPosition = reaper.pos
    }

    private fun tickBoundedWander(ownerPosition: Vec3d, toOwner: Vec3d) {
        // Returning at the outer leash uses the surface graph; ordinary wandering is just a held
        // tangent changed every few seconds, so several companions add almost no pathing cost.
        if (toOwner.lengthSquared() > WANDER_RADIUS_SQUARED) {
            val direction = ownerRouteDirection(ownerPosition, toOwner)
            reaper.setDesiredMove(direction)
            attemptTraversal(direction)
            return
        }

        if (wanderTicks-- <= 0) {
            val up = reaper.clingNormal()
            val forward = SurfaceCling.projectOntoPlane(
                if (wanderHeading.lengthSquared() > 1.0e-6) wanderHeading else toOwner.multiply(-1.0),
                up,
                Vec3d(0.0, 0.0, 1.0)
            )
            val side = up.crossProduct(forward).normalize()
            val candidates = listOf(
                forward,
                side,
                side.multiply(-1.0),
                forward.add(side).normalize(),
                forward.subtract(side).normalize(),
                forward.multiply(-1.0)
            )
            wanderHeading = candidates[reaper.random.nextInt(candidates.size)]
            wanderTicks = 50 + reaper.random.nextInt(91)
        }
        clearSurfacePath()
        reaper.setDesiredMove(wanderHeading)
        if (attemptTraversal(wanderHeading)) wanderTicks = 0
    }

    /** True when sustained lack of progress should make wandering choose another tangent. */
    private fun attemptTraversal(direction: Vec3d): Boolean {
        val previous = lastPosition
        val movedSquared = previous?.squaredDistanceTo(reaper.pos) ?: Double.MAX_VALUE
        lastPosition = reaper.pos
        val stalled = movedSquared < MIN_PROGRESS_SQUARED
        blockedTicks = if (stalled) blockedTicks + 1 else 0
        if (blockedTicks < BLOCKED_BEFORE_JUMP) return false
        val jumped = reaper.tryTraversalJump(direction)
        blockedTicks = 0
        return !jumped
    }

    /**
     * Uses direct steering on the current face and only invokes the bounded surface graph when the
     * owner is mostly off that plane or collision proves the direct route is stuck. This keeps the
     * common case cheap while still considering floor, wall, and ceiling routes as one graph.
     */
    private fun ownerRouteDirection(target: Vec3d, direct: Vec3d): Vec3d {
        val up = reaper.clingNormal()
        val normalGap = kotlin.math.abs(direct.dotProduct(up))
        val planar = direct.subtract(up.multiply(direct.dotProduct(up)))
        val needsSurfaceRoute = normalGap > 2.0 && normalGap > planar.length() * 0.8 || blockedTicks >= 5
        if (!needsSurfaceRoute && surfacePath.isEmpty()) return direct

        repathTicks--
        val targetMoved = pathTarget?.squaredDistanceTo(target)?.let { it > REPATH_TARGET_MOVEMENT_SQUARED } ?: true
        if (surfacePath.isEmpty() || repathTicks <= 0 || targetMoved) {
            surfacePath = ReaperSurfacePath.find(
                reaper.world,
                reaper.getDimensions(reaper.pose),
                reaper.blockPos,
                reaper.clingFace ?: net.minecraft.util.math.Direction.UP,
                target
            )
            pathIndex = 1.coerceAtMost(surfacePath.size)
            pathTarget = target
            repathTicks = MIN_REPATH_TICKS +
                ((reaper.uuid.leastSignificantBits and Long.MAX_VALUE) % REPATH_SPREAD).toInt()
        }

        while (pathIndex < surfacePath.size) {
            val node = surfacePath[pathIndex]
            val atCell = reaper.blockPos.getManhattanDistance(node.cell) == 0
            if (!atCell || reaper.clingFace != node.normal) break
            pathIndex++
        }
        val next = surfacePath.getOrNull(pathIndex) ?: return direct
        if (next.cell == reaper.blockPos && reaper.clingFace != next.normal) {
            return SurfaceCling.vectorOf(next.normal).multiply(-1.0)
        }
        return Vec3d.ofCenter(next.cell).subtract(reaper.pos)
    }

    private fun clearSurfacePath() {
        surfacePath = emptyList()
        pathIndex = 0
        repathTicks = 0
        pathTarget = null
    }

    private companion object {
        const val BLOCKED_BEFORE_JUMP = 8
        const val MIN_PROGRESS_SQUARED = 0.0016
        const val MIN_REPATH_TICKS = 30
        const val REPATH_SPREAD = 20L
        const val REPATH_TARGET_MOVEMENT_SQUARED = 2.0 * 2.0
        const val WANDER_RADIUS_SQUARED = 9.0 * 9.0
    }
}

/** Keeps a screech-summoned lesser near its Greater without replacing its normal AI. */
class ParadoxReaperReturnToMasterGoal(private val reaper: ParadoxReaperEntity) : Goal() {
    private var blockedTicks = 0
    private var returning = false
    private var lastPosition: Vec3d? = null

    init {
        controls = EnumSet.of(Control.MOVE)
    }

    override fun canStart(): Boolean {
        val master = reaper.summonedMaster() ?: return false
        return reaper.isSummonedBackup &&
            SummonedReaperLeash.shouldReturn(reaper.squaredDistanceTo(master), alreadyReturning = false)
    }

    override fun shouldContinue(): Boolean {
        val master = reaper.summonedMaster() ?: return false
        return reaper.isSummonedBackup &&
            SummonedReaperLeash.shouldReturn(reaper.squaredDistanceTo(master), alreadyReturning = returning)
    }
    override fun shouldRunEveryTick(): Boolean = true

    override fun start() {
        returning = true
        blockedTicks = 0
        lastPosition = null
    }

    override fun stop() {
        reaper.setDesiredMove(Vec3d.ZERO)
        returning = false
        blockedTicks = 0
        lastPosition = null
    }

    override fun tick() {
        val master = reaper.summonedMaster() ?: return
        val direction = master.pos.subtract(reaper.pos)
        reaper.setDesiredMove(direction)
        val previous = lastPosition
        val movedSquared = previous?.squaredDistanceTo(reaper.pos) ?: Double.MAX_VALUE
        lastPosition = reaper.pos
        blockedTicks = if (movedSquared < MIN_PROGRESS_SQUARED) blockedTicks + 1 else 0
        if (blockedTicks >= BLOCKED_BEFORE_JUMP && reaper.tryTraversalJump(direction)) blockedTicks = 0
    }

    private companion object {
        const val BLOCKED_BEFORE_JUMP = 8
        const val MIN_PROGRESS_SQUARED = 0.0016
    }
}

/**
 * The search. Heads for the last place the Reaper had reason to think a player was, then casts
 * around it once it arrives.
 *
 * This is what makes hiding work as a tactic rather than a formality: the Reaper commits to a
 * position, not to the player, so breaking line of sight and moving somewhere else genuinely
 * loses it — while standing still behind the nearest wall does not.
 */
class ParadoxReaperInvestigateGoal(private val reaper: ParadoxReaperEntity) : Goal() {

    private var castHeading: Vec3d = Vec3d.ZERO
    private var ticksUntilTurn = 0
    private val progress = ReaperRoamProgress()

    init {
        controls = EnumSet.of(Control.MOVE)
    }

    override fun canStart(): Boolean = reaper.state == ReaperState.HUNTING

    override fun shouldContinue(): Boolean = canStart()

    override fun start() {
        ticksUntilTurn = 0
        progress.reset()
    }

    override fun stop() {
        reaper.setDesiredMove(Vec3d.ZERO)
        progress.reset()
    }

    override fun shouldRunEveryTick(): Boolean = true

    override fun tick() {
        val focus = reaper.investigationTarget
        if (focus == null) {
            castAround()
            return
        }

        val toFocus = Vec3d.ofCenter(focus).subtract(reaper.pos)
        if (toFocus.lengthSquared() > ReaperSpeed.searchArrivalRadiusSquared(reaper.width)) {
            reaper.setDesiredMove(toFocus)
            ticksUntilTurn = 0
            progress.reset()
            return
        }

        // Arrived at the last known position and found nothing; sweep the area.
        castAround()
    }

    private fun castAround() {
        val genuinelyStalled = progress.observe(reaper.pos, castHeading)
        if (ticksUntilTurn-- <= 0 || genuinelyStalled) {
            val up = reaper.clingNormal()
            var best = Vec3d.ZERO
            var bestScore = Double.NEGATIVE_INFINITY
            repeat(CAST_CANDIDATES) {
                val randomDirection = Vec3d(
                    reaper.random.nextDouble() - 0.5,
                    reaper.random.nextDouble() - 0.5,
                    reaper.random.nextDouble() - 0.5
                )
                val candidate = SurfaceCling.projectOntoPlane(randomDirection, up, Vec3d(1.0, 0.0, 0.0))
                val clearance = reaper.roamingClearance(candidate)
                val score = clearance * CLEARANCE_WEIGHT + reaper.random.nextDouble()
                if (score > bestScore) {
                    bestScore = score
                    best = candidate
                }
            }
            castHeading = best
            ticksUntilTurn = MIN_CAST_TICKS + reaper.random.nextInt(CAST_TICK_SPREAD)
            progress.reset()
        }
        reaper.setDesiredMove(castHeading)
    }

    private companion object {
        const val MIN_CAST_TICKS = 45
        const val CAST_TICK_SPREAD = 50
        const val CAST_CANDIDATES = 10
        const val CLEARANCE_WEIGHT = 2.0
    }
}

/**
 * Winding down. Runs only while settling, as the Reaper drifts off to find somewhere to anchor.
 * A dormant Reaper deliberately has no movement goal, so it stays put and listens.
 */
class ParadoxReaperProwlGoal(private val reaper: ParadoxReaperEntity) : Goal() {

    private var heading: Vec3d = Vec3d.ZERO
    private var ticksUntilTurn = 0
    private var roostTarget: Vec3d? = null
    private val progress = ReaperRoamProgress()

    init {
        controls = EnumSet.of(Control.MOVE)
    }

    override fun canStart(): Boolean = reaper.state == ReaperState.SETTLING

    override fun shouldContinue(): Boolean = canStart()

    override fun start() {
        ticksUntilTurn = 0
        roostTarget = null
        progress.reset()
    }

    override fun stop() {
        reaper.setDesiredMove(Vec3d.ZERO)
        roostTarget = null
        progress.reset()
    }

    override fun shouldRunEveryTick(): Boolean = true

    override fun tick() {
        // Darkness is the whole criterion for stopping, and it is not a ceiling criterion. The
        // old rule anchored on any ceiling the creature happened to touch, which meant a lit one
        // ended the search outright while the dark corner two blocks away never got looked at.
        // Solved limbs will fold this thing into anything it fits inside, so a dark floor corner
        // is every bit as valid a perch as a rafter, and the score already prefers whichever of
        // them is darker.
        if (reaper.clingFace != null &&
            ReaperRoostScoring.isDarkEnough(reaper.world.getLightLevel(reaper.blockPos)) &&
            (!reaper.isPetty || reaper.isPettyRoostReady())
        ) {
            heading = Vec3d.ZERO
            ticksUntilTurn = 0
            roostTarget = null
            reaper.setDesiredMove(Vec3d.ZERO)
            reaper.rememberRoost()
            progress.reset()
            return
        }

        if (roostTarget == null && ticksUntilTurn-- <= 0) {
            roostTarget = chooseRoostTarget()
            ticksUntilTurn = TARGET_RETRY_TICKS
            progress.reset()
        }

        val target = roostTarget
        if (target != null) {
            val toTarget = target.subtract(reaper.pos)
            if (toTarget.lengthSquared() <= ReaperRoostScoring.arrivalRadiusSquared(reaper.width)) {
                heading = Vec3d.ZERO
                reaper.setDesiredMove(Vec3d.ZERO)
                if (reaper.clingFace != null) reaper.rememberRoost()
                progress.reset()
                return
            }
            if (progress.observe(reaper.pos, toTarget)) {
                roostTarget = chooseRoostTarget(excluding = target)
                progress.reset()
            }
            val revised = roostTarget?.subtract(reaper.pos) ?: toTarget
            reaper.setDesiredMove(revised)
            return
        }

        val genuinelyStalled = progress.observe(reaper.pos, heading)
        if (ticksUntilTurn <= 0 || genuinelyStalled) {
            heading = preferredRoostHeading()
            ticksUntilTurn = MIN_TURN_TICKS + reaper.random.nextInt(TURN_TICK_SPREAD)
            progress.reset()
        }
        reaper.setDesiredMove(heading)
    }

    /**
     * Chooses an actual destination volume. Darkness dominates the score, height chooses among
     * similarly dark spaces, and a high lit ledge remains the fallback when no dark space was
     * sampled. The full Reaper hitbox must fit before a location can participate.
     */
    private fun chooseRoostTarget(excluding: Vec3d? = null): Vec3d? {
        val origin = reaper.blockPos
        var best: Vec3d? = null
        var bestScore = Double.NEGATIVE_INFINITY

        repeat(ROOST_TARGET_SAMPLES) {
            val dx = reaper.random.nextInt(ROOST_HORIZONTAL_RADIUS * 2 + 1) - ROOST_HORIZONTAL_RADIUS
            val dy = reaper.random.nextInt(ROOST_VERTICAL_ABOVE + ROOST_VERTICAL_BELOW + 1) - ROOST_VERTICAL_BELOW
            val dz = reaper.random.nextInt(ROOST_HORIZONTAL_RADIUS * 2 + 1) - ROOST_HORIZONTAL_RADIUS
            if (dx * dx + dz * dz < MIN_TARGET_HORIZONTAL_DISTANCE_SQUARED) return@repeat

            val cell = origin.add(dx, dy, dz)
            if (!reaper.canOccupyRoost(cell)) return@repeat
            val destination = Vec3d(cell.x + 0.5, cell.y.toDouble(), cell.z + 0.5)
            if (excluding != null && destination.squaredDistanceTo(excluding) < 1.0) return@repeat

            val score = ReaperRoostScoring.score(
                lightLevel = reaper.world.getLightLevel(cell),
                heightDelta = dy,
                openSky = reaper.world.isSkyVisible(cell),
                enclosingFaces = reaper.enclosingFaces(cell)
            ) - reaper.pos.squaredDistanceTo(destination) * DISTANCE_COST + reaper.random.nextDouble()
            if (score > bestScore) {
                bestScore = score
                best = destination
            }
        }
        return best
    }

    /**
     * Samples several legal surface headings and favours three things, in order: staying out of
     * open sky, lower combined light, and climbing upward when it is already on a wall. On the
     * floor the darkward heading naturally carries through the first wall it meets; once on that
     * wall the upward bias takes it to the underside of the ceiling, where [tick] anchors it.
     */
    private fun preferredRoostHeading(): Vec3d {
        val up = reaper.clingNormal()
        var best = Vec3d.ZERO
        var bestScore = Double.NEGATIVE_INFINITY

        repeat(ROOST_CANDIDATES) {
            val randomDirection = Vec3d(
                reaper.random.nextDouble() - 0.5,
                reaper.random.nextDouble() - 0.5,
                reaper.random.nextDouble() - 0.5
            )
            val candidate = SurfaceCling.projectOntoPlane(randomDirection, up, Vec3d(1.0, 0.0, 0.0))
            val clearance = reaper.roamingClearance(candidate)
            if (clearance == 0) return@repeat
            var score = reaper.random.nextDouble() * 0.35
            score += clearance * CLEARANCE_WEIGHT

            for (distance in ROOST_SAMPLE_DISTANCES) {
                val sample = net.minecraft.util.math.BlockPos.ofFloored(
                    reaper.pos.add(candidate.multiply(distance.toDouble()))
                )
                val light = reaper.world.getLightLevel(sample)
                score += (15 - light) * DARKNESS_WEIGHT
                if (reaper.world.isSkyVisible(sample)) score -= OPEN_SKY_PENALTY
            }

            // On a wall, climbing is one route to a dark place, and it used to be weighted
            // heavily enough to drag the creature up into lit rafters past unlit ground. It is
            // now a nudge that only breaks ties between equally dark directions.
            if (reaper.clingFace?.axis?.isHorizontal == true) score += candidate.y * UPWARD_WEIGHT

            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best
    }

    private companion object {
        const val MIN_TURN_TICKS = 24
        const val TURN_TICK_SPREAD = 36
        const val ROOST_CANDIDATES = 12
        const val DARKNESS_WEIGHT = 1.6
        const val OPEN_SKY_PENALTY = 12.0
        const val UPWARD_WEIGHT = 1.5
        const val CLEARANCE_WEIGHT = 2.0
        const val TARGET_RETRY_TICKS = 50
        const val ROOST_TARGET_SAMPLES = 144
        const val ROOST_HORIZONTAL_RADIUS = 12
        const val ROOST_VERTICAL_ABOVE = 14
        const val ROOST_VERTICAL_BELOW = 6
        const val MIN_TARGET_HORIZONTAL_DISTANCE_SQUARED = 3 * 3
        const val DISTANCE_COST = 0.025
        val ROOST_SAMPLE_DISTANCES = intArrayOf(2, 5, 8)
    }
}

/**
 * Keeps a dormant Reaper on the perch it chose, and walks it back after anything drags it off.
 *
 * Without this a Reaper that stood down simply stopped wherever its last search happened to end,
 * so the creature that had spent a minute climbing toward a dark corner would then spend the rest
 * of its life standing in a corridor. Settling is only half a roosting behaviour; the other half
 * is the returning, and it is what turns a spot a Reaper once chose into somewhere it lives.
 *
 * Running only while dormant keeps it out of the way of everything else. The moment a noise or a
 * sighting raises the creature it drops out entirely, and [ParadoxReaperProwlGoal] takes over
 * again on the way back down — choosing a fresh perch if this one has since been lit or filled.
 */
class ParadoxReaperRoostGoal(private val reaper: ParadoxReaperEntity) : Goal() {

    private val progress = ReaperRoamProgress()
    private var recheckTicks = 0

    init {
        controls = EnumSet.of(Control.MOVE)
    }

    override fun canStart(): Boolean =
        reaper.state == ReaperState.DORMANT && !reaper.isPetty && !reaper.isDrone

    override fun shouldContinue(): Boolean = canStart()

    override fun shouldRunEveryTick(): Boolean = true

    override fun start() {
        progress.reset()
        recheckTicks = 0
    }

    override fun stop() {
        reaper.setDesiredMove(Vec3d.ZERO)
        progress.reset()
    }

    override fun tick() {
        // A perch that has been lit up or built over is no longer a perch. Checked on an interval
        // rather than every tick: it is two block lookups and a box test, and nothing about a
        // roost changes fast enough to need them twenty times a second.
        if (recheckTicks-- <= 0) {
            reaper.forgetRoostIfUnusable()
            recheckTicks = ROOST_RECHECK_TICKS
        }

        val anchor = reaper.roostAnchor
        if (anchor == null) {
            // Nothing remembered. If it is already somewhere dark, that is the roost; otherwise
            // hold still and let the settling pass claim one the next time it is roused.
            reaper.setDesiredMove(Vec3d.ZERO)
            if (reaper.clingFace != null &&
                ReaperRoostScoring.isDarkEnough(reaper.world.getLightLevel(reaper.blockPos))
            ) {
                reaper.rememberRoost()
            }
            return
        }

        if (reaper.isAtRoost()) {
            reaper.setDesiredMove(Vec3d.ZERO)
            progress.reset()
            return
        }

        val toAnchor = Vec3d.ofBottomCenter(anchor).subtract(reaper.pos)
        if (progress.observe(reaper.pos, toAnchor)) {
            // Wedged on the way home. The perch is not worth being stuck over, so drop it and let
            // the next settling pass pick somewhere reachable.
            reaper.roostAnchor = null
            reaper.roostFace = null
            progress.reset()
            reaper.setDesiredMove(Vec3d.ZERO)
            return
        }
        reaper.setDesiredMove(toAnchor)
    }

    private companion object {
        const val ROOST_RECHECK_TICKS = 40
    }
}

/**
 * Walks a debug drone to whatever the lure tool is pointing at.
 *
 * Highest priority of any movement goal, and gated on the drone flag, so it can never interfere
 * with a real Reaper's behaviour.
 */
class ParadoxReaperDroneGoal(private val reaper: ParadoxReaperEntity) : Goal() {

    private var blockedTicks = 0
    private var lastPosition: Vec3d? = null

    init {
        controls = EnumSet.of(Control.MOVE, Control.LOOK)
    }

    override fun canStart(): Boolean =
        reaper.isDrone && ReaperDebugLure.get(reaper.world) != null

    override fun shouldContinue(): Boolean = canStart()

    override fun stop() {
        reaper.setDesiredMove(Vec3d.ZERO)
        blockedTicks = 0
        lastPosition = null
    }

    override fun shouldRunEveryTick(): Boolean = true

    override fun tick() {
        val lure = ReaperDebugLure.get(reaper.world) ?: return
        val toLure = Vec3d.ofCenter(lure).subtract(reaper.pos.add(0.0, reaper.height * 0.5, 0.0))

        // Stop once it is basically there, so it settles on the block instead of jittering.
        if (toLure.lengthSquared() < ARRIVAL_SQUARED) {
            reaper.setDesiredMove(Vec3d.ZERO)
            blockedTicks = 0
            lastPosition = reaper.pos
            return
        }
        reaper.setDesiredMove(toLure)

        val previous = lastPosition
        val movedSquared = previous?.squaredDistanceTo(reaper.pos) ?: Double.MAX_VALUE
        lastPosition = reaper.pos
        blockedTicks = if (movedSquared < MIN_PROGRESS_SQUARED) blockedTicks + 1 else 0
        if (blockedTicks >= BLOCKED_BEFORE_JUMP && reaper.tryTraversalJump(toLure)) blockedTicks = 0
    }

    private companion object {
        const val ARRIVAL_SQUARED = 1.4
        const val BLOCKED_BEFORE_JUMP = 8
        const val MIN_PROGRESS_SQUARED = 0.0016
    }
}
