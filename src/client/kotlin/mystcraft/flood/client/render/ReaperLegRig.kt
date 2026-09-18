package mystcraft.flood.client.render

import mystcraft.flood.entity.FabrikChain
import mystcraft.flood.entity.ParadoxReaperEntity
import mystcraft.flood.entity.ReaperFootstepPlan
import mystcraft.flood.entity.ReaperFootstepMotion
import mystcraft.flood.entity.ReaperSmoothing
import mystcraft.flood.entity.SurfaceCling
import net.minecraft.util.math.Vec3d
import net.minecraft.util.hit.HitResult
import net.minecraft.world.RaycastContext

/**
 * Per-creature procedural gait state for a Paradox Reaper.
 *
 * The animation principle is the one used by Spore-style creature rigs: feet belong to the
 * world, not to the model. Each foot holds a fixed world position while the body moves past it,
 * and only when the body has pulled a foot too far out of place does that foot take a single
 * quick step to a freshly raycast spot. Knees are then solved from wherever the feet actually
 * are, so the walk fits whatever surface it happens to be crossing.
 *
 * Six legs on an alternating tripod, the real insect gait: three feet are always planted while
 * the other three swing. That is what keeps the body visually supported no matter how fast it is
 * moving, and it is why the creature never looks like it is hopping.
 *
 * One instance exists per entity, cached on the entity itself, because a renderer instance is
 * shared across every Reaper on screen and this state is emphatically not shareable.
 */
class ReaperLegRig {

    /**
     * @param gaitGroup legs alternate between two tripods. A leg may only begin a step while the
     *   opposing tripod is fully planted.
     */
    class Leg(
        val hipForward: Double,
        val hipRight: Double,
        val hipUp: Double,
        val restForward: Double,
        val restRight: Double,
        val gaitGroup: Int
    ) {
        var foot: Vec3d = Vec3d.ZERO
        var stepFrom: Vec3d = Vec3d.ZERO
        var stepTo: Vec3d = Vec3d.ZERO
        var stepUp: Vec3d = Vec3d(0.0, 1.0, 0.0)
        var stepProgress = 1.0
        val isStepping: Boolean get() = stepProgress < 1.0

        /**
         * Ticks this particular swing was given.
         *
         * Held per leg rather than read from the constant, because a step already in flight when
         * the creature changes pace must finish on the timing it started with. Re-deriving the
         * duration mid-swing rescales the progress the foot has already made and jumps it along
         * its own arc, which is a snap in the one place the rig is meant to be smoothest.
         */
        var stepDuration = 1.0

        /** How much of the body's weight this foot is still carrying, 1 planted and 0 lifted. */
        val contactWeight: Double get() = ReaperFootstepMotion.contactWeight(stepProgress)

        /** Stable destination reserved while this leg waits for its tripod's turn. */
        val plan = ReaperFootstepPlan()

        /**
         * Femur, tibia, tarsus. Three segments give the two visible bends a spider has: a knee
         * that peaks above the body and a short ankle that angles the foot back down onto the
         * surface. Solved by FABRIK, which unlike a closed-form two-bone solve generalises past
         * a single joint.
         */
        val chain = FabrikChain(doubleArrayOf(FEMUR, TIBIA, TARSUS), SPIDER_BOW)
    }

    /**
     * Authored at greater-form proportions, in blocks; [scale] narrows them for lesser forms.
     *
     * Feet rest well outside the body. Combined with limbs longer than the hip-to-foot distance,
     * that surplus is what lets each knee peak above the shell instead of standing the creature
     * on straight posts.
     */
    val legs = arrayOf(
        // hipUp is negative: legs hang from the underside of the shell, not its roof. The
        // shell spans +/-0.406 blocks about the body origin, so -0.30 sits just inside its
        // floor. Anything positive attaches the limbs to the top of the chest.
        //        hipF   hipR   hipU   restF  restR  tripod
        Leg( 0.32, -0.28, -0.30,  1.00, -1.10, 0),  // front left
        Leg( 0.32,  0.28, -0.30,  1.00,  1.10, 1),  // front right
        Leg( 0.02, -0.32, -0.30,  0.05, -1.30, 1),  // middle left
        Leg( 0.02,  0.32, -0.30,  0.05,  1.30, 0),  // middle right
        Leg(-0.30, -0.28, -0.30, -0.95, -1.10, 0),  // rear left
        Leg(-0.30,  0.28, -0.30, -0.95,  1.10, 1)   // rear right
    )

    private var initialised = false
    private var lastClock = 0.0
    private var lastOrigin: Vec3d = Vec3d.ZERO
    private var facingClock = Double.NaN
    private var smoothedFacingMotion: Vec3d = Vec3d.ZERO
    private var poseClock = Double.NaN
    private var lastRawOrigin: Vec3d = Vec3d.ZERO
    private var smoothedOrigin: Vec3d = Vec3d.ZERO
    private var smoothedOriginVelocity: Vec3d = Vec3d.ZERO
    private var lastUpClock = Double.NaN

    /**
     * Every orientation the creature is drawn with rides a critically damped spring.
     *
     * These four used to be fixed-rate rotations, which turn at a constant angular velocity and
     * then stop dead. That is what made the body look like it was being clicked into place: the
     * motion had no ease at either end, so a wall transition, a turn, and a change of stance all
     * arrived as abrupt starts and abrupt stops against an otherwise continuous world. Springs
     * ease in and out on their own and cost nothing extra to evaluate.
     */
    private val poseUp = ReaperSmoothing.DampedDirection(Vec3d(0.0, 1.0, 0.0))
    private val travelFacing = ReaperSmoothing.DampedDirection(Vec3d(0.0, 0.0, 1.0))
    private val stanceUp = ReaperSmoothing.DampedDirection(Vec3d(0.0, 1.0, 0.0))
    private val stanceForward = ReaperSmoothing.DampedDirection(Vec3d(0.0, 0.0, 1.0))
    private val rideHeight = ReaperSmoothing.DampedScalar(BODY_HEIGHT)

    /** 0 while hunting, 1 while fully dormant. Drives the folded roosting posture. */
    private var dormancy = 0.0

    /**
     * Whether each foot last found ground under it, and when that was last actually measured.
     *
     * This rig runs once per rendered frame, not once per tick, and confirming six footholds plus
     * six reserved plans is twelve raycasts. At a high frame rate that is thousands of world
     * queries a second for a single creature, which costs frame time and therefore shows up as
     * exactly the stutter the smoothing is meant to remove — and multiplies by however many
     * Reapers are on screen. What it buys is nothing: a block either supports a foot or it does
     * not, and that answer changes only when somebody mines it. Measuring on an interval and
     * reusing the answer in between is visually identical and an order of magnitude cheaper.
     */
    private val supportCache = BooleanArray(6)
    private var supportClock = Double.NaN

    /**
     * Which tripod is allowed to swing right now, and whether it is mid-swing.
     *
     * Turn-taking has to be explicit. Deciding it by "may step if the other tripod is idle",
     * evaluated in array order, starves one side permanently: by the time the first tripod
     * finishes its swing the body has already travelled far enough that its legs re-qualify
     * immediately, so they take every turn and the other three legs are dragged along forever.
     */
    private var swingGroup = 0
    private var groupWasSwinging = false

    /** Body velocity in blocks/tick, used to plant feet where the body is going. */
    private var bodyVelocity: Vec3d = Vec3d.ZERO

    /**
     * The up-vector the body is actually drawn against, leaned to follow the ground under it.
     *
     * The clinging face is one of six axis directions and nothing else, so a body oriented purely
     * by it stays perfectly level over every slope, stair, and hole while its feet sit at wildly
     * different heights. Fitting a plane through the planted feet and leaning toward it is what
     * makes the creature look like it is negotiating terrain rather than hovering over it.
     */
    var bodyUp: Vec3d = Vec3d(0.0, 1.0, 0.0)
        private set

    /** 1.0 for a greater Reaper, smaller for a lesser one. Scales every length below. */
    var scale = 1.0
        private set

    /** Smoothed vertical offset of the body, driven by where the feet actually landed. */
    val bodyLift: Double get() = rideHeight.value

    /**
     * Last usable heading, kept per creature so the degenerate case (yaw pointing straight along
     * the surface normal) falls back to this Reaper's own facing rather than a neighbour's.
     */
    var lastForward: Vec3d = Vec3d(0.0, 0.0, 1.0)

    /** Rendered facing recovered from the labelled front/rear planted-foot stance. */
    var bodyForward: Vec3d = Vec3d(0.0, 0.0, 1.0)
        private set

    /**
     * Where this creature was first rendered. The arrival rift anchors here rather than to the
     * body, so a summon visibly drops away from the portal it came out of.
     */
    var birthOrigin: Vec3d? = null

    /** Segment length for a given index, at the current body scale. */
    fun segmentLength(leg: Leg, segment: Int): Double = leg.chain.lengthOf(segment) * scale

    /**
     * How long the next swing gets, in ticks, from how fast the body is currently travelling.
     *
     * A fixed duration is the reason a sprinting Reaper looked like it was snapping its legs into
     * place. The stride the body covers in one swing grows with speed, so at a run each foot sat
     * still through a long drift and then crossed a large distance in the same six ticks, which
     * reads as a flick however smooth the arc between the endpoints is. Shortening the swing as
     * the creature speeds up keeps the apparent foot speed roughly matched to the body's, which
     * is the thing the eye is actually judging. The floor keeps a full sprint from degenerating
     * into a blur, and the whole range stays inside what the description calls for: quick.
     */
    private val swingDuration: Double
        get() {
            val speed = bodyVelocity.length() / scale.coerceAtLeast(0.25)
            if (speed <= REFERENCE_SPEED) return STEP_DURATION_TICKS
            val shortened = STEP_DURATION_TICKS * (REFERENCE_SPEED / speed)
            return shortened.coerceAtLeast(STEP_DURATION_TICKS * MIN_STEP_DURATION_FRACTION)
        }

    /**
     * Client-only visual origin filter. Vanilla interpolation is linear inside each network tick,
     * so even steady server movement changes velocity abruptly at every tick boundary. Filtering
     * that velocity and leading by the same time constant removes the corner without leaving the
     * rendered body visibly trailing its real hitbox.
     */
    fun smoothVisualOrigin(rawOrigin: Vec3d, clock: Double): Vec3d {
        if (!poseClock.isFinite() || rawOrigin.squaredDistanceTo(lastRawOrigin) > POSE_RESET_DISTANCE_SQUARED) {
            poseClock = clock
            lastRawOrigin = rawOrigin
            smoothedOrigin = rawOrigin
            smoothedOriginVelocity = Vec3d.ZERO
            lastUpClock = Double.NaN
            return smoothedOrigin
        }

        val delta = (clock - poseClock).coerceIn(0.0, 4.0)
        poseClock = clock
        if (delta <= 1.0e-6) return smoothedOrigin

        val measuredVelocity = rawOrigin.subtract(lastRawOrigin).multiply(1.0 / delta)
        lastRawOrigin = rawOrigin
        val velocityBlend = 1.0 - Math.exp(-delta / POSITION_VELOCITY_SMOOTHING_TICKS)
        smoothedOriginVelocity = smoothedOriginVelocity.lerp(measuredVelocity, velocityBlend)

        val predicted = rawOrigin.add(smoothedOriginVelocity.multiply(POSITION_LOOKAHEAD_TICKS))
        val positionBlend = 1.0 - Math.exp(-delta / POSITION_SMOOTHING_TICKS)
        smoothedOrigin = smoothedOrigin.lerp(predicted, positionBlend)

        // Large corrections must remain readable as movement rather than letting the visual body
        // detach from combat and collision. Teleports reset above; this caps ordinary corrections.
        val offset = smoothedOrigin.subtract(rawOrigin)
        if (offset.lengthSquared() > MAX_VISUAL_OFFSET * MAX_VISUAL_OFFSET) {
            smoothedOrigin = rawOrigin.add(offset.normalize().multiply(MAX_VISUAL_OFFSET))
        }
        return smoothedOrigin
    }

    /**
     * Smooths the final client pose normal between 20 Hz cling-state updates.
     *
     * This is the axis the whole creature is drawn against, so it carries a corner transition in
     * its entirety: floor to wall is a ninety degree change of this one vector. Running it on a
     * spring is what turns that into a roll the body eases into and out of, rather than a fixed
     * rate sweep that begins and ends instantaneously.
     */
    fun smoothVisualUp(rawUp: Vec3d, clock: Double): Vec3d {
        val target = if (rawUp.lengthSquared() > 1.0e-6) rawUp.normalize() else poseUp.value
        if (!lastUpClock.isFinite()) {
            poseUp.reset(target)
            lastUpClock = clock
            return poseUp.value
        }
        val delta = (clock - lastUpClock).coerceIn(0.0, 4.0)
        lastUpClock = clock
        return poseUp.advance(target, ReaperSmoothing.frequencyForHalfLife(UP_HALF_LIFE_TICKS), delta)
    }

    /**
     * Stable body heading derived from actual travel without following every navigation twitch.
     *
     * Vanilla path steering can alternate its one-tick displacement around an obstacle, and the
     * old renderer treated each tiny direction as a new facing. Low-pass filtering plus a motion
     * dead band makes a stopped or hesitant Reaper keep looking where it last committed, while a
     * real turn is followed at a bounded angular rate.
     */
    fun resolveForward(rawMotion: Vec3d, up: Vec3d, clock: Double): Vec3d {
        val previous = SurfaceCling.projectOntoPlane(travelFacing.value, up, Vec3d(0.0, 0.0, 1.0))
        val planar = rawMotion.subtract(up.multiply(rawMotion.dotProduct(up)))

        if (!facingClock.isFinite()) {
            facingClock = clock
            smoothedFacingMotion = planar
            if (planar.lengthSquared() > FACING_MOTION_THRESHOLD_SQUARED) {
                travelFacing.reset(planar)
            }
            lastForward = travelFacing.value
            return SurfaceCling.projectOntoPlane(lastForward, up, previous)
        }

        val delta = (clock - facingClock).coerceIn(0.0, 4.0)
        facingClock = clock
        val oldPlanar = smoothedFacingMotion.subtract(up.multiply(smoothedFacingMotion.dotProduct(up)))
        val blend = 1.0 - Math.exp(-delta / FACING_SMOOTHING_TICKS)
        smoothedFacingMotion = oldPlanar.lerp(planar, blend)

        val target = if (smoothedFacingMotion.lengthSquared() > FACING_MOTION_THRESHOLD_SQUARED) {
            smoothedFacingMotion.normalize()
        } else {
            previous
        }

        // Re-seat the spring on the plane of the current surface before advancing it. Rounding a
        // corner moves that plane out from under the stored direction, and springing toward a
        // target from a stale off-plane start turns the heading through an axis the creature is
        // not actually on. The momentum is deliberately kept across the reprojection.
        travelFacing.reproject(previous)
        lastForward = travelFacing.advance(
            target, ReaperSmoothing.frequencyForHalfLife(FACING_HALF_LIFE_TICKS), delta
        )
        return lastForward
    }

    /**
     * Advances the rig. [clock] is a monotonic tick counter including partial ticks, so the gait
     * runs on real elapsed time and stays smooth above and below 20 fps. It is a Double because a
     * Float loses sub-tick resolution once an entity has been alive for a few hundred thousand
     * ticks, which would make the step timing progressively coarser the longer a Reaper survives.
     */
    fun update(
        reaper: ParadoxReaperEntity,
        origin: Vec3d,
        forward: Vec3d,
        right: Vec3d,
        up: Vec3d,
        clock: Double,
        bodyScale: Double
    ) {
        val rescaled = bodyScale != scale
        scale = bodyScale

        val delta = if (initialised) (clock - lastClock).coerceIn(0.0, 4.0) else 0.0
        lastClock = clock

        // Dormancy trails the creature's own alertness glow, so waking up and folding back down
        // are one continuous posture change rather than a switch between two rigs.
        val dormantTarget = 1.0 - reaper.huntGlow.toDouble()
        dormancy += (dormantTarget - dormancy) * (delta * DORMANCY_RATE).coerceIn(0.0, 1.0)

        // A teleport, dimension change, or a variant swap would otherwise drag the feet across
        // the world in one frame, producing legs stretched to the horizon.
        if (!initialised || rescaled || origin.squaredDistanceTo(lastOrigin) > REPLANT_DISTANCE_SQUARED) {
            replant(reaper, origin, forward, right, up)
            initialised = true
            lastOrigin = origin
            return
        }
        val measuredVelocity =
            if (delta > 1.0e-6) origin.subtract(lastOrigin).multiply(1.0 / delta) else Vec3d.ZERO
        // Entity interpolation is piecewise linear, so velocity measured from render frames
        // changes sharply at tick boundaries even when the creature is moving steadily. A short,
        // time-correct low-pass keeps that noise out of look-ahead placement without adding a
        // visible delay when the Reaper turns.
        val velocityBlend = 1.0 - Math.exp(-delta / VELOCITY_SMOOTHING_TICKS)
        bodyVelocity = bodyVelocity.lerp(measuredVelocity, velocityBlend)
        lastOrigin = origin

        val airborne = reaper.clingFace == null
        val stepThreshold = STEP_THRESHOLD * scale

        // Swings are advanced before the handover is judged, not after. Checking first means a
        // tripod that lands this tick still reads as busy, so the other one cannot start until
        // the following frame; that dead tick appears twice per gait cycle and stretches the
        // stride the body covers between steps by about half again.
        if (!airborne) {
            for (leg in legs) {
                if (!leg.isStepping) continue
                leg.stepProgress = (leg.stepProgress + delta / leg.stepDuration).coerceAtMost(1.0)
                // Keep the lift axis from the instant this step began. Changing it halfway
                // through a floor/wall transition kinks the trajectory and makes the foot look
                // attached to the body rather than committed to its chosen patch of terrain.
                leg.foot = ReaperFootstepMotion.position(
                    leg.stepFrom,
                    leg.stepTo,
                    leg.stepUp,
                    leg.stepProgress,
                    STEP_CLEARANCE * scale
                )
            }
        }

        // Decide the handover before planning. A target for the waiting tripod has to predict the
        // remainder of the active swing as well as its own swing, or faithfully retaining that
        // point would make it land one whole step behind the body.
        val swinging = legs.any { it.isStepping }
        if (groupWasSwinging && !swinging) swingGroup = 1 - swingGroup
        groupWasSwinging = swinging

        // A leg only searches once it is actually overdue. The first valid result is then latched
        // until its tripod gets the next turn. This prevents a waiting leg from chasing a newly
        // raycast destination every rendered frame, while a tight support probe still lets it
        // abandon a plan immediately if that block disappears.
        val footSupported = BooleanArray(legs.size)

        // The posture solvers below read this rather than the boolean. A swinging foot fades its
        // influence out as it lifts and back in as it lands, so the set of constraints shaping
        // the body changes continuously instead of all at once on the tick a tripod departs.
        val bearing = DoubleArray(legs.size)

        val remeasure = !supportClock.isFinite() ||
            Math.abs(clock - supportClock) >= SUPPORT_PROBE_INTERVAL_TICKS
        if (remeasure) supportClock = clock

        for ((index, leg) in legs.withIndex()) {
            if (airborne || leg.isStepping) {
                leg.plan.invalidate()
                // A lifted foot still describes the surface it came off and is heading onto; it
                // simply describes it less and less as it rises. Dropping it to zero outright is
                // the discontinuity this weighting exists to remove.
                if (!airborne) bearing[index] = leg.contactWeight
                continue
            }

            if (remeasure) {
                supportCache[index] = isFootholdSupported(reaper, leg.foot, up)
                val reserved = leg.plan.target
                if (reserved != null && !isFootholdSupported(reaper, reserved, up)) {
                    leg.plan.invalidate()
                }
            }
            footSupported[index] = supportCache[index]
            if (footSupported[index]) bearing[index] = 1.0
        }

        fun planGroup(group: Int, landingTicks: Double) {
            for ((index, leg) in legs.withIndex()) {
                if (leg.gaitGroup != group || leg.isStepping) continue
                val predicted = predictedRestPosition(origin, forward, right, up, leg, landingTicks)
                if (leg.plan.isStale(predicted, PLAN_RECONSIDER_DISTANCE * scale)) {
                    leg.plan.invalidate()
                }
                if (leg.plan.target != null) continue
                val overdue = leg.foot.squaredDistanceTo(predicted) > stepThreshold * stepThreshold
                if (overdue || !footSupported[index]) {
                    val candidate = findFoothold(reaper, origin, forward, right, up, leg, landingTicks)
                    leg.plan.consider(leg.foot, candidate, footSupported[index], stepThreshold, predicted)
                }
            }
        }

        if (swinging) {
            val remainingTicks = legs.asSequence()
                .filter { it.isStepping }
                .maxOf { (1.0 - it.stepProgress) * it.stepDuration }
            planGroup(1 - swingGroup, remainingTicks + swingDuration)
        } else {
            planGroup(swingGroup, swingDuration)
            val mine = legs.any { it.gaitGroup == swingGroup && it.plan.target != null }

            // If the current group will swing first, the other group lands one complete gait
            // phase later. When the current group has nothing to do, let the other plan for an
            // immediate handover instead of needlessly aiming a full stride too far ahead.
            val otherGroup = 1 - swingGroup
            val otherLandingTicks = if (mine) swingDuration * 2.0 else swingDuration
            planGroup(otherGroup, otherLandingTicks)
            val theirs = legs.any { it.gaitGroup != swingGroup && it.plan.target != null }
            if (!mine && theirs) swingGroup = 1 - swingGroup
        }

        for ((index, leg) in legs.withIndex()) {
            if (airborne) {
                // Nothing to stand on: pull the feet in toward the body so the silhouette reads
                // as a tucked, falling creature rather than a table sliding through the air.
                val tucked = restPosition(origin, forward, right, up, leg, 0.45)
                    .add(up.multiply(0.35 * scale))
                leg.foot = leg.foot.add(tucked.subtract(leg.foot).multiply(TUCK_RATE))
                leg.stepProgress = 1.0
                leg.plan.invalidate()
                continue
            }

            // Already advanced above.
            if (leg.isStepping) continue

            val desired = leg.plan.target
            if (desired == null) {
                // Nothing solid anywhere near where this leg wants to be. A foot must never be
                // planted in open air, so hold the one it has, and only draw it in once the body
                // has walked far enough that the limb would otherwise stretch past its length.
                if (!footSupported[index]) {
                    retractIfStranded(leg, origin, forward, right, up)
                }
                continue
            }

            if (leg.gaitGroup != swingGroup) continue

            leg.stepFrom = leg.foot
            leg.stepTo = leg.plan.take() ?: continue
            leg.stepUp = up
            leg.stepDuration = swingDuration
            leg.stepProgress = 0.0

            // This destination was raycast onto real geometry when the plan was made, so the foot
            // is known to be supported the moment it arrives. Seeding the cache means a freshly
            // landed leg bears weight immediately instead of reading as unsupported until the
            // next scheduled measurement, which would briefly drop it out of the body's stance.
            supportCache[index] = true
        }

        // Record the state after new swings start as well as after old ones advance. Otherwise a
        // very low render rate can start and finish a whole swing between two observations and
        // the gait never hands the next turn to the opposing tripod.
        groupWasSwinging = legs.any { it.isStepping }

        updateBodyLift(origin, up, airborne, delta, bearing)
        updateBodyTilt(up, delta, bearing)
        updateBodyForward(bodyUp, forward, delta, bearing, airborne)
    }

    /**
     * Lets the planted legs rotate the body.
     *
     * Each labelled foot contributes its world offset weighted by its authored front/rear rest
     * coordinate. Front feet therefore pull this axis forward and rear feet push it the same way.
     * Swinging feet are excluded: only constraints currently bearing weight are allowed to turn
     * the shell. Travel heading still plans future footholds, breaking the otherwise circular
     * dependency where a body could not turn until its feet turned and vice versa.
     */
    private fun updateBodyForward(
        up: Vec3d,
        travelForward: Vec3d,
        delta: Double,
        bearing: DoubleArray,
        airborne: Boolean
    ) {
        val current = SurfaceCling.projectOntoPlane(bodyForward, up, travelForward)
        stanceForward.reproject(current)
        val omega = ReaperSmoothing.frequencyForHalfLife(BODY_FACING_HALF_LIFE_TICKS)

        if (airborne) {
            bodyForward = stanceForward.advance(travelForward, omega, delta)
            return
        }

        val total = bearing.sum()
        if (total < MINIMUM_STANCE_WEIGHT) return

        var centroid = Vec3d.ZERO
        for (index in legs.indices) centroid = centroid.add(legs[index].foot.multiply(bearing[index]))
        centroid = centroid.multiply(1.0 / total)

        var stanceAxis = Vec3d.ZERO
        for (index in legs.indices) {
            val leg = legs[index]
            stanceAxis = stanceAxis.add(
                leg.foot.subtract(centroid).multiply(leg.restForward * bearing[index])
            )
        }
        stanceAxis = stanceAxis.subtract(up.multiply(stanceAxis.dotProduct(up)))

        val target = if (stanceAxis.lengthSquared() > 1.0e-8) stanceAxis.normalize() else travelForward
        bodyForward = stanceForward.advance(target, omega, delta)
    }

    /**
     * Leans [bodyUp] toward the plane through the six planted feet.
     *
     * The normal is accumulated Newell-style, summing the cross products of successive foot
     * offsets taken around the body's perimeter rather than in array order. Array order zig-zags
     * left and right, and those cross products largely cancel, which yields a near-zero and
     * wildly unstable normal; walking the outline gives a well-conditioned one.
     */
    private fun updateBodyTilt(clingUp: Vec3d, delta: Double, bearing: DoubleArray) {
        // A foot mid-swing is lifted into its step arc, so it describes the ground less well the
        // higher it gets. Excluding it outright the instant it leaves is what rocked the fitted
        // plane back and forth in time with the gait; fading it out over the lift removes that
        // beat entirely while still keeping raised feet from flattening the fit.
        val total = bearing.sum()
        if (total < MINIMUM_STANCE_WEIGHT) return

        var centroid = Vec3d.ZERO
        for (index in legs.indices) centroid = centroid.add(legs[index].foot.multiply(bearing[index]))
        centroid = centroid.multiply(1.0 / total)

        // Newell's method around the body's outline. Each edge of the perimeter is weighted by
        // both of its endpoints, so a foot fading out withdraws from the fit smoothly rather
        // than deleting two edges and rewiring the polygon across the gap it left.
        var accumulated = Vec3d.ZERO
        for (position in FOOT_PERIMETER.indices) {
            val hereIndex = FOOT_PERIMETER[position]
            val nextIndex = FOOT_PERIMETER[(position + 1) % FOOT_PERIMETER.size]
            val edgeWeight = bearing[hereIndex] * bearing[nextIndex]
            if (edgeWeight <= 0.0) continue
            val here = legs[hereIndex].foot.subtract(centroid)
            val next = legs[nextIndex].foot.subtract(centroid)
            accumulated = accumulated.add(here.crossProduct(next).multiply(edgeWeight))
        }

        var target = clingUp
        if (accumulated.lengthSquared() > 1.0e-8) {
            var fitted = accumulated.normalize()
            // The perimeter winds the other way on a ceiling, so the fit can come out inverted.
            if (fitted.dotProduct(clingUp) < 0.0) fitted = fitted.multiply(-1.0)

            // How far the feet disagree with the face the body is nominally attached to.
            //
            // On open ground they agree and the clinging normal is a useful stabiliser: it keeps
            // the body square when a foot is momentarily on something odd. In a corner they
            // disagree completely — half the feet are on one wall and half on the next, and the
            // clinging normal is one of six axis directions that by definition cannot describe a
            // body halfway between two of them. Leaving it weighted there is what dragged the
            // creature back to axis-aligned and made the turn a snap between two poses instead of
            // a rotation through them.
            //
            // So its authority is spent in proportion to its disagreement. Flat ground keeps the
            // old blend; the deeper into a corner the creature gets, the more completely the feet
            // decide which way its body is pointing — which is the right answer, because the feet
            // are the parts actually touching the world.
            val disagreement = Math.acos(fitted.dotProduct(clingUp).coerceIn(-1.0, 1.0))
            val handover = (disagreement / CORNER_HANDOVER_RADIANS).coerceIn(0.0, 1.0)
            val eased = handover * handover * (3.0 - 2.0 * handover)
            val weight = TILT_WEIGHT + (1.0 - TILT_WEIGHT) * eased

            val blended = clingUp.multiply(1.0 - weight).add(fitted.multiply(weight))
            if (blended.lengthSquared() > 1.0e-8) target = blended.normalize()
        }

        stanceUp.reproject(bodyUp)
        bodyUp = stanceUp.advance(
            target, ReaperSmoothing.frequencyForHalfLife(TILT_HALF_LIFE_TICKS), delta
        )
    }

    /**
     * Folds a leg in against the body when it has nothing to stand on and is running out of reach.
     *
     * This is what a Reaper on a tree trunk needs. A one-block trunk is narrower than the span the
     * legs rest at, so almost every foot search comes back empty; without folding, those legs keep
     * their old footholds on the ground below, stretch to full extension, and the solver lays them
     * out as dead straight bars sticking into thin air. Drawing them in reads as the creature
     * gripping something too narrow for it, which is what is actually happening.
     */
    private fun retractIfStranded(leg: Leg, origin: Vec3d, forward: Vec3d, right: Vec3d, up: Vec3d) {
        val limit = leg.chain.totalLength * STRANDED_FRACTION
        if (leg.foot.squaredDistanceTo(origin) <= limit * limit) return

        val tucked = restPosition(origin, forward, right, up, leg, 0.4).add(up.multiply(0.2 * scale))
        leg.foot = leg.foot.add(tucked.subtract(leg.foot).multiply(STRANDED_TUCK_RATE))
    }

    /**
     * Body height follows the mean foot offset so the creature settles into dips and rises over
     * bumps instead of gliding at a constant altitude.
     */
    private fun updateBodyLift(
        origin: Vec3d,
        up: Vec3d,
        airborne: Boolean,
        delta: Double,
        bearing: DoubleArray
    ) {
        // A roosting Reaper settles right down onto whatever it has chosen, which is most of what
        // sells a dormant one as a smear of wet glass rather than a creature standing at ease.
        val rest = BODY_HEIGHT * scale * (1.0 - dormancy * DORMANT_CROUCH)
        val target = if (airborne) {
            rest
        } else {
            // Weighted by contact for the same reason the tilt is: averaging a whole tripod in or
            // out the moment it leaves the ground lifts the body twice a cycle, which is bobbing.
            val total = bearing.sum()
            if (total < MINIMUM_STANCE_WEIGHT) return
            val mean = legs.indices.sumOf {
                legs[it].foot.subtract(origin).dotProduct(up) * bearing[it]
            } / total
            (rest + mean).coerceIn(rest * 0.45, rest * 1.8)
        }
        rideHeight.advance(target, ReaperSmoothing.frequencyForHalfLife(LIFT_HALF_LIFE_TICKS), delta)
    }

    private fun replant(reaper: ParadoxReaperEntity, origin: Vec3d, forward: Vec3d, right: Vec3d, up: Vec3d) {
        for (leg in legs) {
            leg.foot = findFoothold(reaper, origin, forward, right, up, leg)
                ?: restPosition(origin, forward, right, up, leg, 1.0)
            leg.stepProgress = 1.0
            leg.stepUp = up
            leg.plan.invalidate()
        }
        supportCache.fill(true)
        supportClock = Double.NaN
        bodyVelocity = Vec3d.ZERO
        rideHeight.reset(BODY_HEIGHT * scale)
        bodyUp = up
        bodyForward = forward
        // A teleport is not a movement, so every spring starts again from rest rather than
        // carrying momentum from wherever the creature used to be into its new pose.
        stanceUp.reset(up)
        stanceForward.reset(forward)
    }

    /**
     * Where a foot wants to sit, before any surface is taken into account.
     *
     * A dormant creature draws its stance in. That is the whole silhouette change between a thing
     * you would walk straight past and a thing coming across the ceiling at you, and because it
     * feeds the ordinary foothold search rather than overriding the feet directly, a Reaper
     * folding down onto its roost still places every foot on real geometry on the way in.
     */
    private fun restPosition(
        origin: Vec3d,
        forward: Vec3d,
        right: Vec3d,
        up: Vec3d,
        leg: Leg,
        reach: Double
    ): Vec3d {
        val gathered = reach * (1.0 - dormancy * DORMANT_STANCE_GATHER)
        return origin
            .add(forward.multiply(leg.restForward * gathered * scale))
            .add(right.multiply(leg.restRight * gathered * scale))
    }

    /** Rest position led to where the filtered body motion predicts this step will finish. */
    private fun predictedRestPosition(
        origin: Vec3d,
        forward: Vec3d,
        right: Vec3d,
        up: Vec3d,
        leg: Leg,
        landingTicks: Double
    ): Vec3d {
        val lead = bodyVelocity.subtract(up.multiply(bodyVelocity.dotProduct(up)))
            .multiply(landingTicks)
        return restPosition(origin, forward, right, up, leg, 1.0).add(lead)
    }

    /**
     * Solid ground for this leg to step onto, or null when there is none worth reaching.
     *
     * A single probe at the ideal spot is not enough. That spot is led ahead of the body by the
     * stride, so over a ledge, a gap, or open water it simply misses, and the old fallback of
     * returning the un-grounded point planted the foot in mid air. The search now walks the
     * stride back toward the body and then sweeps a ring around the rest position, taking the
     * first real surface it finds. When every candidate is empty the leg does not step at all.
     */
    private fun findFoothold(
        reaper: ParadoxReaperEntity,
        origin: Vec3d,
        forward: Vec3d,
        right: Vec3d,
        up: Vec3d,
        leg: Leg,
        landingTicks: Double = swingDuration
    ): Vec3d? {
        val base = restPosition(origin, forward, right, up, leg, 1.0)

        // Lead the placement by the stride so the foot lands where the body is going rather than
        // where it already was, shortening the stride when the full one has nothing under it.
        val lead = bodyVelocity.subtract(up.multiply(bodyVelocity.dotProduct(up))).multiply(landingTicks)
        for (fraction in STRIDE_FALLBACKS) {
            probeDown(reaper, base.add(lead.multiply(fraction)), up)?.let { return it }
        }

        // Over the lip. This is the case the whole search was blind to, and it is the one that
        // made a Reaper cross an outside corner by shoving its body into open air and dragging
        // its legs behind it until they tore free.
        //
        // The foot wants the vertical face below the edge. A downward cast cannot find it: the
        // face is parallel to that cast and offset outside it, so the ray runs down through open
        // air alongside the wall forever. The outward cast cannot find it either, because it
        // travels horizontally straight over the top of it. The one direction that meets that
        // face is *back inward*, from a point already past the edge and already below it — which
        // is exactly the motion a real climber makes reaching a hand down over a ledge.
        //
        // Tried ahead of the ring sweep on purpose. The sweep would otherwise find the floor a
        // little way behind the foot and plant there, which is what cramped every leading leg
        // back onto the roof instead of letting it take the wall.
        val inward = origin.subtract(base)
        val inwardPlanar = inward.subtract(up.multiply(inward.dotProduct(up)))
        if (inwardPlanar.lengthSquared() > 1.0e-8) {
            val back = inwardPlanar.normalize().multiply(LEDGE_REACH_BACK * scale)
            for (drop in LEDGE_DROPS) {
                val from = base.subtract(up.multiply(drop * scale))
                probeOutward(reaper, from, from.add(back))?.let { return it }
            }
        }

        // Broken ground: sweep for anything solid near where the foot belongs.
        val radius = FOOTHOLD_SEARCH_RADIUS * scale
        for (sample in 0 until FOOTHOLD_RING_SAMPLES) {
            val angle = sample * (2.0 * Math.PI / FOOTHOLD_RING_SAMPLES)
            val offset = forward.multiply(Math.cos(angle) * radius)
                .add(right.multiply(Math.sin(angle) * radius))
            probeDown(reaper, base.add(offset), up)?.let { return it }
        }

        // Every probe so far has been cast along the creature's own down axis, which can only
        // ever find the surface it is already standing on. In an inside corner the surface the
        // foot actually wants is not underneath it at all — it is the wall directly in front,
        // perpendicular to the one being walked on, and no amount of casting downward will see
        // it. That is why a Reaper pressed into a corner would not put its legs on the wall it
        // was leaning against: the legs on that side searched, found nothing, and folded away.
        //
        // Reaching outward from the body to where the foot belongs finds whatever is in the way,
        // whichever direction it faces. Placed last, so it only governs the cases the ordinary
        // search could not answer, and the plain floor walk is untouched.
        probeOutward(reaper, origin, base)?.let { return it }

        // Nothing at the rest distance either; try a little further, for a foot that wants a
        // surface just past its natural reach.
        val stretched = base.subtract(origin)
        if (stretched.lengthSquared() > 1.0e-8) {
            val further = origin.add(stretched.multiply(OUTWARD_STRETCH))
            probeOutward(reaper, origin, further)?.let { return it }
        }
        return null
    }

    /**
     * Casts from the body out to where a foot belongs, returning the first surface in the way.
     *
     * The counterpart to [probeDown]: that one asks what is beneath the foot, this one asks what
     * is beside it. Together they cover a creature that treats every face as a floor.
     */
    private fun probeOutward(reaper: ParadoxReaperEntity, from: Vec3d, to: Vec3d): Vec3d? {
        if (from.squaredDistanceTo(to) < 1.0e-8) return null
        val hit = reaper.world.raycast(
            RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, reaper)
        )
        return if (hit.type == HitResult.Type.BLOCK) hit.pos else null
    }

    /** Casts along -up through [at], returning the surface point only if it hits something. */
    private fun probeDown(reaper: ParadoxReaperEntity, at: Vec3d, up: Vec3d): Vec3d? {
        val start = at.add(up.multiply(PROBE_ABOVE * scale))
        val end = at.subtract(up.multiply(PROBE_BELOW * scale))
        val hit = reaper.world.raycast(
            RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, reaper)
        )
        return if (hit.type == HitResult.Type.BLOCK) hit.pos else null
    }

    /**
     * Whether [at] is still touching solid geometry, whichever way that geometry faces.
     *
     * This used to cast along the creature's down axis, which asks a narrower question than it
     * looks: not "is this foot on something" but "is this foot on something *beneath* it". A foot
     * gripping the wall in an inside corner is on a perfectly good surface and failed the test,
     * so the rig treated it as dangling and hauled it back in — undoing the placement the
     * outward search had just made. Testing for any collision geometry within a tight box is
     * orientation-agnostic, answers the question that was actually meant, and costs one world
     * query rather than a raycast.
     */
    private fun isFootholdSupported(reaper: ParadoxReaperEntity, at: Vec3d, up: Vec3d): Boolean {
        val tolerance = FOOTHOLD_VALIDATION_DEPTH * scale
        val around = net.minecraft.util.math.Box(
            at.x - tolerance, at.y - tolerance, at.z - tolerance,
            at.x + tolerance, at.y + tolerance, at.z + tolerance
        )
        return !reaper.world.isSpaceEmpty(around)
    }

    companion object {
        /**
     * Limb lengths are deliberately well over the hip-to-foot distance, leaving the chain around
     * half extended. The knee rises above the hip only by
     *   -drop/2 + sqrt(L^2 - d^2/4) * (span/d)
     * so without that surplus the first term wins, the knee sits level with the hip, and the legs
     * read as scaffolding instead of as a spider. These values put the knee above the shell.
     */
    /** Resting height of the body above its surface, in blocks, at greater proportions. */
        const val BODY_HEIGHT = 0.95

        // Femur out and up, a long tibia back down, then a short tarsus planting the foot.
        const val FEMUR = 0.90
        const val TIBIA = 1.14
        const val TARSUS = 0.30

        /**
         * Where each joint sits relative to the straight hip-to-foot line, as a share of the
         * limb's bow. A spider leg is not a symmetric arc: the knee rides far above that line
         * while the ankle sits just above the ground near the foot. Feeding a plain half-sine
         * here instead lifts both joints equally, which is what produced flat accordion folds
         * rather than legs.
         */
        val SPIDER_BOW = doubleArrayOf(0.0, 1.0, 0.22, 0.0)

        /** How far a foot may drift from its rest spot before it takes a step. */
        private const val STEP_THRESHOLD = 0.32

        /** Six ticks gives the lift/travel/lower phases enough frames to read at ordinary FPS. */
        private const val STEP_DURATION_TICKS = 6.0
        private const val STEP_CLEARANCE = 0.48

        /**
         * Speed, in blocks per tick, at which a swing runs its full length. Set at the Reaper's
         * own cruising pace so ordinary hunting uses the authored timing and only a genuine
         * sprint tightens it.
         */
        private const val REFERENCE_SPEED = 0.12

        /** Floor on the shortening, so a full sprint still shows three distinct swing phases. */
        private const val MIN_STEP_DURATION_FRACTION = 0.55

        /** Combined contact weight below which the stance is too sparse to fit a body pose to. */
        private const val MINIMUM_STANCE_WEIGHT = 2.5

        /**
         * Ticks between foothold support measurements. Half a tick is still ten checks a second,
         * far faster than terrain changes, while capping the cost per creature no matter how high
         * the frame rate goes.
         */
        private const val SUPPORT_PROBE_INTERVAL_TICKS = 0.5

        /** How fast the folded roosting posture is blended in and out, per tick. */
        private const val DORMANCY_RATE = 0.05

        /** Share of the ride height a roosting Reaper gives up as it settles onto its perch. */
        private const val DORMANT_CROUCH = 0.42

        /** Share of the stance width a roosting Reaper draws its feet in by. */
        private const val DORMANT_STANCE_GATHER = 0.30

        /** Time constant for averaging render-frame velocity before using it for foot planning. */
        private const val VELOCITY_SMOOTHING_TICKS = 1.5

        /** Client pose filtering: equal lead and lag keep steady movement centered on the hitbox. */
        private const val POSITION_VELOCITY_SMOOTHING_TICKS = 1.15
        private const val POSITION_SMOOTHING_TICKS = 1.15
        private const val POSITION_LOOKAHEAD_TICKS = 1.15

        /**
         * Spring half-lives, in ticks, for each orientation the body is drawn with.
         *
         * These replace the old fixed angular rates. A half-life is the time the spring takes to
         * close half its remaining error, so a ninety degree corner is most of the way through in
         * roughly three of these and visually settled in four or five: fast enough to keep up
         * with something moving at a sprint, slow enough that the roll through the corner is a
         * motion the eye can follow rather than a cut.
         */
        private const val UP_HALF_LIFE_TICKS = 1.6
        private const val MAX_VISUAL_OFFSET = 0.45
        private const val POSE_RESET_DISTANCE_SQUARED = 5.0 * 5.0

        /** A reservation survives normal prediction noise but not a real turn or reversal. */
        private const val PLAN_RECONSIDER_DISTANCE = 0.55

        /** Heading filter and hysteresis; displacement is measured in blocks per game tick. */
        private const val FACING_SMOOTHING_TICKS = 2.25
        private const val FACING_MOTION_THRESHOLD_SQUARED = 0.02 * 0.02
        private const val FACING_HALF_LIFE_TICKS = 2.6

        /** Body follows the weight-bearing stance, slower than travel can change its mind. */
        private const val BODY_FACING_HALF_LIFE_TICKS = 4.0

        /** Stride fractions tried in order, from a full lead back to slightly behind the body. */
        private val STRIDE_FALLBACKS = doubleArrayOf(1.0, 0.6, 0.25, 0.0, -0.4)

        /** How far past the rest distance the outward reach will stretch for a surface. */
        private const val OUTWARD_STRETCH = 1.35

        /**
         * Depths below the current surface at which a foot reaches back for a face over a lip.
         *
         * Spread rather than a single value so the same search serves a foot just cresting an
         * edge and one whose body has already carried it well past.
         */
        private val LEDGE_DROPS = doubleArrayOf(0.35, 0.75, 1.25, 1.8)

        /** How far back toward the body that reach casts, in blocks at greater proportions. */
        private const val LEDGE_REACH_BACK = 1.1

        /** Ring sweep used when the entire stride line is over nothing. */
        private const val FOOTHOLD_SEARCH_RADIUS = 0.5
        private const val FOOTHOLD_RING_SAMPLES = 8

        /** Share of the limb's length past which a stranded foot is drawn back in. */
        private const val STRANDED_FRACTION = 0.72

        /** Folds faster than the airborne tuck, so a stretched leg does not linger straight. */
        private const val STRANDED_TUCK_RATE = 0.35

        /**
         * Feet in perimeter order: down the left side, back up the right. Array order zig-zags
         * across the body, and the cross products of a zig-zag largely cancel.
         */
        private val FOOT_PERIMETER = intArrayOf(0, 2, 4, 5, 3, 1)

        /** How far the body leans toward the ground plane rather than the clinging face. */
        private const val TILT_WEIGHT = 0.7

        /**
         * Disagreement between the feet and the clinging face at which the feet take over fully.
         *
         * Set below a right angle so a body crossing between two perpendicular surfaces has
         * handed over well before it arrives, rather than being argued with the whole way across.
         */
        private const val CORNER_HANDOVER_RADIANS = Math.PI / 4.0

        /** Lean half-life, in ticks. Slow enough to read as weight shifting. */
        private const val TILT_HALF_LIFE_TICKS = 2.2

        private const val PROBE_ABOVE = 0.9
        private const val PROBE_BELOW = 2.0

        /** Tight probe used to notice a removed block without accepting a lower ledge as support. */
        private const val FOOTHOLD_VALIDATION_HEIGHT = 0.08
        private const val FOOTHOLD_VALIDATION_DEPTH = 0.20

        private const val TUCK_RATE = 0.25
        private const val LIFT_HALF_LIFE_TICKS = 2.4

        private const val REPLANT_DISTANCE_SQUARED = 25.0
    }
}
