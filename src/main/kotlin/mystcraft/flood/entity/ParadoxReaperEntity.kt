package mystcraft.flood.entity

import mystcraft.flood.config.MystcraftConfig
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.entity.EntityData
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.EntityPose
import net.minecraft.entity.EntityType
import net.minecraft.entity.Entity
import net.minecraft.entity.JumpingMount
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.Ownable
import net.minecraft.entity.MovementType
import net.minecraft.entity.SpawnReason
import net.minecraft.entity.ai.goal.LookAroundGoal
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.passive.TameableEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.ProjectileEntity
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtHelper
import net.minecraft.nbt.NbtList
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.ActionResult
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.registry.tag.GameEventTags
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.world.LocalDifficulty
import net.minecraft.world.ServerWorldAccess
import net.minecraft.world.World
import net.minecraft.world.event.EntityPositionSource
import net.minecraft.world.event.GameEvent
import net.minecraft.world.event.PositionSource
import net.minecraft.world.event.Vibrations
import net.minecraft.world.event.listener.EntityGameEventHandler
import java.util.UUID
import java.util.function.BiConsumer

/**
 * A Paradox Reaper: the correction an unstable Age sends after matter it cannot reconcile.
 *
 * Locomotion deliberately bypasses vanilla walking physics. Instead of gravity plus a ground
 * check, the Reaper carries a surface normal (see [SurfaceCling]) and is pulled toward whatever
 * face it currently occupies, so floors, walls, and ceilings are all one code path. That is also
 * why it steers directly instead of pathfinding: vanilla navigation is a 2D ground solver and
 * cannot express "up the wall and across the roof", which is the whole point of this mob.
 *
 * Speed is expressed in blocks per tick rather than as a movement attribute, because the custom
 * [travel] never feeds that attribute through vanilla's acceleration curve. That makes the
 * "matches a sprinting player" requirement exact rather than approximate.
 *
 * Two variants share this class. The greater form is what an Age spawns; it can screech for
 * lesser forms as backup. Lesser forms are summoned only, and can never summon in turn, which is
 * what stops a single engagement from cascading into an unbounded swarm.
 *
 * Sensing uses the Warden's vibration machinery and event tag, so it inherits the parts that are
 * tedious to reproduce: crouching players are ignored for the events tagged
 * [GameEventTags.IGNORE_VIBRATIONS_SNEAKING], wool occludes, and distance delays arrival. What
 * differs is source filtering: player pets and allies count, unrelated mobs do not keep the
 * listener awake from across a cave. The Warden's brain is likewise replaced with a small
 * explicit state machine ([ReaperAwareness]) that fits the custom surface locomotion.
 */
class ParadoxReaperEntity(
    entityType: EntityType<out ParadoxReaperEntity>,
    world: World
) : HostileEntity(entityType, world), Vibrations, JumpingMount {

    companion object {
        /** 0-5 map to Direction ordinals; [AIRBORNE] means the Reaper is in open air. */
        private val CLING_FACE: TrackedData<Byte> =
            DataTracker.registerData(ParadoxReaperEntity::class.java, TrackedDataHandlerRegistry.BYTE)
        /** [ReaperState] ordinal. Clients need it for the cube emergence and the glow ramp. */
        private val STATE: TrackedData<Byte> =
            DataTracker.registerData(ParadoxReaperEntity::class.java, TrackedDataHandlerRegistry.BYTE)
        private val LESSER: TrackedData<Boolean> =
            DataTracker.registerData(ParadoxReaperEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)
        private val PETTY: TrackedData<Boolean> =
            DataTracker.registerData(ParadoxReaperEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)
        private val BOUND: TrackedData<Boolean> =
            DataTracker.registerData(ParadoxReaperEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)
        private val COMPANION_COMMAND: TrackedData<Byte> =
            DataTracker.registerData(ParadoxReaperEntity::class.java, TrackedDataHandlerRegistry.BYTE)

        const val AIRBORNE: Byte = 6

        /**
         * A sprinting player covers 5.612 blocks/second, which is 0.2806 blocks/tick.
         * The Reaper matches that exactly, so it can never simply be outrun on open ground.
         */
        /** See [ReaperSpeed]; the arithmetic lives there so it can be unit tested. */
        const val SPRINT_BLOCKS_PER_TICK = ReaperSpeed.SPRINT_BLOCKS_PER_TICK

        /** Fits the clear centre of open double doors, but remains too wide for one door. */
        val GREATER_DIMENSIONS: EntityDimensions = EntityDimensions.fixed(1.55f, 1.9f)

        /** Player-width collision lets the 1x1 form pass a normally opened single door. */
        val LESSER_DIMENSIONS: EntityDimensions = EntityDimensions.fixed(0.6f, 0.9f)

        /** Familiar form is visually and physically tiny but retains the crawler locomotion. */
        val PETTY_DIMENSIONS: EntityDimensions = LESSER_DIMENSIONS

        /** Repeatable Age hunter: tougher than an iron golem, but not a renewable mini-Warden. */
        private const val GREATER_MAX_HEALTH = 120.0
        private const val LESSER_MAX_HEALTH = 10.0
        private const val PETTY_MAX_HEALTH = 8.0
        private const val PERMANENT_PETTY_MAX_HEALTH = 24.0
        private const val HEALTH_MODEL_VERSION = 3

        /** One owner-bound Reaper ability spawn survives at a time, even across loaded dimensions. */
        private val ACTIVE_PETTIES = mutableMapOf<UUID, UUID>()

        /** Pull into the surface, blocks/tick. Enough to hold a ceiling, small enough to slide. */
        private const val ADHESION_PULL = 0.08

        /** Ticks a Reaper keeps its last face while the probe finds nothing. */
        private const val CLING_GRACE_TICKS = 8

        /** Extra adhesion while coasting on that grace. */
        private const val REGRIP_MULTIPLIER = 3.0

        /** How far ahead to check that the current surface actually continues. */
        private const val LIP_LOOKAHEAD = 0.45

        /** Probe offsets measured from the contact plane the creature is standing on. */
        private const val PROBE_AT_CONTACT = 0.22

        /** Depths below the lip searched for the face that continues downward, and how far out. */
        private val LIP_DESCENT_DROPS = doubleArrayOf(0.35, 0.7, 1.1)
        private const val LIP_DESCENT_LOOKAHEAD = 0.15

        /** Ticks a lip wrap is given to find its new face before the creature gives up and falls. */
        private const val WRAP_TICKS = 12

        /** Ticks a freshly left surface is refused, so inside corners cannot buzz between two. */
        private const val FACE_HOLD_TICKS = 10

        /** Forward speed retained mid-wrap, and the extra downward pull applied over the edge. */
        private const val WRAP_FORWARD_FACTOR = 0.3
        private const val WRAP_DESCENT_PULL = 0.16

        /** Half-extent of the small box used to test for floor past the leading edge. */
        private const val LIP_PROBE_HALF = 0.22

        /** Traversal jump tuning. Power scales with the gap so short hops are not launches. */
        /** Height vanilla's collision pass may step the body up, and only ever on a floor. */
        private const val FLOOR_STEP_HEIGHT = 1.0f

        /** Ticks a deliberate jump refuses to grab anything; see [jumpDetachTicks]. */
        private const val JUMP_DETACH_TICKS = 6

        /** Share of a lunge's power added along the surface normal when none is asked for. */
        private const val DEFAULT_LUNGE_UP_BIAS = 0.45

        /** Rider jump power at no charge and at full charge, in blocks per tick. */
        private const val MOUNT_JUMP_MIN_POWER = 0.42
        private const val MOUNT_JUMP_MAX_POWER = 0.95

        /** How far a rider's jump leans into their heading rather than straight off the surface. */
        private const val MOUNT_JUMP_LEAN = 0.55

        private const val JUMP_MINIMUM_BLOCKS = 2
        private const val JUMP_BASE_POWER = 0.34
        private const val JUMP_POWER_PER_BLOCK = 0.085
        private const val JUMP_MAX_POWER = 1.05

        /** Landing heights tried, measured along the creature's own up rather than world up. */
        private val JUMP_LIFTS = intArrayOf(0, -1, 1, -2, 2, -3)

        /** How far an airborne Reaper will reach for something to grab, in blocks. */
        private const val SURFACE_SEEK_RANGE = 3.0
        private const val SURFACE_SEEK_PULL = 0.07

        private const val AIR_GRAVITY = 0.055
        private const val AIR_DRAG = 0.94
        private const val MAX_FALL_SPEED = 1.6
        private const val MOUNT_SPEED_BLOCKS_PER_TICK = 0.245

        /** How far lateral escape steering probes before committing to a detour. */
        private const val UNSTICK_PROBE_STEP = 0.45
        private const val UNSTICK_PROBE_STEPS = 3

        private const val ATTACK_COOLDOWN_TICKS = 16
        private const val FAILED_ATTACK_RETRY_TICKS = 3
        private const val ATTACK_REACH_PADDING = 0.65

        /** Fast enough to corner hard, slow enough that the body still reads as banking. */
        private const val MAX_TURN_DEGREES_PER_TICK = 25.0f

        /** Body roll rate through a surface change: 90 degrees in about a quarter of a second. */
        private const val ROLL_HALF_LIFE_TICKS = 2.2

        /** Travel direction spring. Short: this steers the creature, it does not dress it. */
        private const val TRAVEL_HALF_LIFE_TICKS = 1.6

        /** A dormant Reaper is looking at very little; an alerted one is looking everywhere. */
        private const val DORMANT_FIELD_OF_VIEW = 70.0
        private const val ALERT_FIELD_OF_VIEW = 260.0

        /** Glass can reveal quarry without being thin enough for the Reaper to pass through. */
        val TRANSPARENT_TO_SIGHT: TagKey<Block> = TagKey.of(
            RegistryKeys.BLOCK,
            Identifier("mystcraft-reforged", "reaper_transparent")
        )

        /** Health below which the greater form takes its second summon roll. */
        private const val WOUNDED_FRACTION = 0.4f

        /** How far above the floor a rift opens, so the summon visibly drops out of it. */
        private const val SUMMON_DROP_MINIMUM = 2
        private const val SUMMON_DROP_SPREAD = 2
        private const val SUMMON_FLOOR_SCAN = 6
        private const val PETTY_ROOST_MIN_DISTANCE_SQUARED = 3.5 * 3.5
        private const val PETTY_DARK_ROOST_LIGHT = 7
        private const val PETTY_MIN_SEARCH_TICKS = 20
        /** Ticks the arrival rift stays visible; the renderer reads this off the summon's age. */
        const val PORTAL_TICKS = 16

        fun createReaperAttributes(): DefaultAttributeContainer.Builder =
            createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, GREATER_MAX_HEALTH)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.6)
                .add(EntityAttributes.GENERIC_ARMOR, 2.0)
    }

    /** World-space direction the active goal wants to travel. */
    private var desiredMove: Vec3d = Vec3d.ZERO
    private var attackCooldown = 0
    private var jumpCooldown = 0
    private var companionRegenDelay = 0

    /** Heading carried through the last floor/wall/ceiling transition. */
    private var surfaceHeading: Vec3d = Vec3d.ZERO

    /** Last result of a real sight scan; non-scan ticks must not count as losing the target. */
    private var sawTargetLastScan = false

    /** Lesser forms this Reaper called up. Pruned as they die; blocks further screeching. */
    private val summonedIds = mutableListOf<UUID>()
    private var rolledOnEngage = false
    private var rolledOnWounded = false

    /** Lesser Reapers created by a Greater's screech stay near that master. */
    private var summonedByUuid: UUID? = null
    val isSummonedBackup: Boolean
        get() = summonedByUuid != null && isLesser && !isPetty

    /** Optional Familiar Friends summon state. Kept entirely in this mod to avoid a hard link. */
    private var pettyOwnerUuid: UUID? = null
    private var pettyClaimed = false
    private var pettyDecisionTicks = 0
    private var pettySearchTicks = 0
    private val pettyRestPlan = PettyReaperRestPlan()
    val pettyRestMode: PettyReaperRestMode
        get() = pettyRestPlan.mode
    /** Alertness. Server-authoritative; only the resulting [ReaperState] reaches clients. */
    private val awareness = ReaperAwareness()

    /**
     * Debug drone: ignores players entirely and walks to whatever [ReaperDebugLure] points at.
     *
     * Exists so surface traversal can be exercised deliberately instead of by waiting for a hunt
     * to happen across the terrain you wanted to watch. A drone is marked persistent so the
     * spawner's despawn sweep leaves it alone mid-test.
     */
    var isDrone: Boolean = false
        private set

    /** Where the Reaper last had reason to believe a player was. Drives the search. */
    var investigationTarget: BlockPos? = null
        private set

    /** Last face that actually held, and how long the probe has been finding nothing. */
    private var lastSupport: Direction? = null
    private var ungroundedTicks = 0

    /**
     * The face being wrapped onto while the creature rolls over a convex lip, such as a cliff top.
     *
     * Held across ticks because a wrap spans the moment the body is over nothing at all, when a
     * fresh look at the terrain would find no reason to continue and would drop it instead.
     */
    private var wrapFace: Direction? = null
    private var wrapTicks = 0

    /** The face most recently left, and how long it stays off the menu. See [climbCandidate]. */
    private var previousFace: Direction? = null
    private var faceHoldTicks = 0

    /** Client-side spring behind [renderNormal]; see [advanceRenderNormal]. */
    private val surfaceRoll = ReaperSmoothing.DampedDirection(Vec3d(0.0, 1.0, 0.0))

    /** Server-side spring on the direction the body actually travels; see [travel]. */
    private val travelHeading = ReaperSmoothing.DampedDirection(Vec3d(0.0, 0.0, 1.0))

    /**
     * Ticks during which the creature refuses to grab anything, set when it jumps on purpose.
     *
     * Without it a deliberate jump cannot leave a surface at all. The airborne reach pulls toward
     * any face within three blocks at 0.07 a tick against a gravity of 0.055, so a Reaper that
     * drops off a ceiling is hauled straight back up to it and hangs there. Jumping needs a moment
     * of genuinely letting go; ordinary falling still reaches for a hold as before.
     */
    private var jumpDetachTicks = 0

    /** Rider's charge on the jump bar, 0 to 100. */
    private var mountJumpStrength = 0

    /** Set when a rider releases the jump key; spent by [travel] on the simulating side. */
    private var jumpRequested = false

    /**
     * Where this Reaper has decided to go dormant, and which face it hangs from there.
     *
     * A dormant creature with no memory of a perch simply stops wherever its last search ran out,
     * which is why they used to stand around in corridors. Keeping the anchor lets one return to
     * the same spot after dealing with whatever disturbed it, so a nest reads as a nest.
     */
    var roostAnchor: BlockPos? = null
    var roostFace: Direction? = null

    private val vibrationCallback = ReaperVibrationCallback()
    private var vibrationListenerData = Vibrations.ListenerData()
    private val gameEventHandler = EntityGameEventHandler(Vibrations.VibrationListener(this))

    /** Client only: smoothed normal used for rendering, so corners roll instead of snapping. */
    var renderNormal: Vec3d = Vec3d(0.0, 1.0, 0.0)
        private set

    /** Client only: 0 while dormant, 1 while hunting. Drives the glow and the orbit spread. */
    var huntGlow = 0.0f
        private set

    /** Last procedural body pose drawn on the client, reused by the mounted camera next frame. */
    var mountVisualBody: Vec3d = Vec3d.ZERO
        private set
    var mountVisualUp: Vec3d = Vec3d(0.0, 1.0, 0.0)
        private set

    /** Which way the drawn body faces, so a camera pushed out of a wall knows where its head is. */
    var mountVisualForward: Vec3d = Vec3d(0.0, 0.0, 1.0)
        private set
    var mountVisualPoseReady: Boolean = false
        private set

    fun updateMountVisualPose(body: Vec3d, up: Vec3d, forward: Vec3d) {
        mountVisualBody = body
        mountVisualUp = up
        mountVisualForward = forward
        mountVisualPoseReady = true
    }

    /** Lets a friendly visual proxy reuse the exact Reaper renderer without hostile AI. */
    fun setRenderEngagement(value: Float) {
        huntGlow = value.coerceIn(0.0f, 1.0f)
    }

    /** Supplies the floor orientation for a client-only friendly render proxy. */
    fun setFriendlyRenderSurface(face: Direction, normal: Vec3d) {
        clingFace = face
        renderNormal = normal
    }

    /** Supplies an airborne pose for the real floating Little Anomaly render proxy. */
    fun setFriendlyRenderAirborne() {
        clingFace = null
        renderNormal = Vec3d(0.0, 1.0, 0.0)
    }

    /** Client only: opaque slot holding the renderer's procedural leg state for this instance. */
    var legRigCache: Any? = null

    init {
        this.stepHeight = 1.0f
    }

    override fun initDataTracker() {
        super.initDataTracker()
        dataTracker.startTracking(CLING_FACE, Direction.UP.ordinal.toByte())
        dataTracker.startTracking(STATE, ReaperState.DORMANT.ordinal.toByte())
        dataTracker.startTracking(LESSER, false)
        dataTracker.startTracking(PETTY, false)
        dataTracker.startTracking(BOUND, false)
        dataTracker.startTracking(COMPANION_COMMAND, ReaperCompanionCommand.FOLLOW.ordinal.toByte())
    }

    override fun initGoals() {
        goalSelector.add(0, ParadoxReaperDroneGoal(this))
        goalSelector.add(1, ParadoxReaperPetGoal(this))
        goalSelector.add(1, ParadoxReaperReturnToMasterGoal(this))
        goalSelector.add(1, ParadoxReaperBreakGlassGoal(this))
        goalSelector.add(2, ParadoxReaperHuntGoal(this))
        goalSelector.add(3, ParadoxReaperInvestigateGoal(this))
        goalSelector.add(4, ParadoxReaperProwlGoal(this))
        goalSelector.add(5, ParadoxReaperRoostGoal(this))
        goalSelector.add(8, LookAroundGoal(this))

        // No ActiveTargetGoal: acquisition is the state machine's job, so a dormant Reaper does
        // not silently target a player it has not actually seen or heard.
        // Targeting is intentionally handled by the awareness system. A RevengeGoal can replace
        // a visible player with a skeleton or other incidental attacker after acquisition,
        // violating the Reaper's player-first role and bypassing its stealth rules.
    }

    override fun initialize(
        world: ServerWorldAccess,
        difficulty: LocalDifficulty,
        spawnReason: SpawnReason,
        entityData: EntityData?,
        entityNbt: NbtCompound?
    ): EntityData? {
        // Anything an Age spawns on its own is a greater form; lesser forms only ever arrive
        // through a screech, which sets the variant explicitly before the entity is added.
        if (spawnReason != SpawnReason.MOB_SUMMONED) applyVariant(false)
        return super.initialize(world, difficulty, spawnReason, entityData, entityNbt)
    }

    // ---------------------------------------------------------------- variant

    val isLesser: Boolean
        get() = dataTracker.get(LESSER)

    val isPetty: Boolean
        get() = dataTracker.get(PETTY)

    val isPermanentCompanion: Boolean
        get() = dataTracker.get(BOUND)

    val companionCommand: ReaperCompanionCommand
        get() = ReaperCompanionCommand.entries[
            dataTracker.get(COMPANION_COMMAND).toInt().coerceIn(0, ReaperCompanionCommand.entries.lastIndex)
        ]

    /**
     * Applies the variant along with the stats and hitbox that go with it.
     *
     * [resetHealth] must be false when restoring from NBT: the max-health attribute changes with
     * the variant, and topping the bar up afterwards would fully heal a wounded Reaper every time
     * its chunk reloaded.
     */
    fun applyVariant(lesser: Boolean, resetHealth: Boolean = true) {
        dataTracker.set(LESSER, lesser)
        calculateDimensions()

        if (!world.isClient) {
            getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH)?.baseValue =
                if (lesser) LESSER_MAX_HEALTH else GREATER_MAX_HEALTH
            getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE)?.baseValue = if (lesser) 3.0 else 6.0
            getAttributeInstance(EntityAttributes.GENERIC_ARMOR)?.baseValue = if (lesser) 0.0 else 2.0
            if (resetHealth) health = maxHealth
        }
    }

    /** Converts a lesser Reaper into the fragile, owner-bound Familiar Friends support summon. */
    fun applyPetty(ownerUuid: UUID, resetHealth: Boolean = true) {
        pettyOwnerUuid = ownerUuid
        dataTracker.set(PETTY, true)
        applyVariant(true, resetHealth = false)
        if (!world.isClient) {
            getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH)?.baseValue = PETTY_MAX_HEALTH
            getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE)?.baseValue = 2.0
            getAttributeInstance(EntityAttributes.GENERIC_ARMOR)?.baseValue = 0.0
            if (resetHealth) health = maxHealth
            setPersistent()
        }
        pettyClaimed = false
        pettyRestPlan.reset()
        pettySearchTicks = 0
    }

    /** Binds a weakened lesser permanently while retaining the Little Anomaly pet behavior. */
    private fun applyPermanentCompanion(ownerUuid: UUID, resetHealth: Boolean = true) {
        val retainLesserForm = isLesser
        dataTracker.set(BOUND, true)
        summonedByUuid = null
        target = null
        investigationTarget = null
        applyPetty(ownerUuid, resetHealth = false)
        dataTracker.set(COMPANION_COMMAND, ReaperCompanionCommand.FOLLOW.ordinal.toByte())
        if (!retainLesserForm) applyVariant(lesser = false, resetHealth = false)
        applyPermanentCompanionStats(resetHealth)
    }

    /** Command/test entry point; normal gameplay still reaches this only through Echo Shards. */
    fun bindToOwnerForCommand(ownerUuid: UUID) {
        applyPermanentCompanion(ownerUuid)
    }

    private fun applyPermanentCompanionStats(resetHealth: Boolean) {
        if (!world.isClient) {
            getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH)?.baseValue =
                if (isLesser) PERMANENT_PETTY_MAX_HEALTH else GREATER_MAX_HEALTH
            getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE)?.baseValue = if (isLesser) 3.0 else 6.0
            getAttributeInstance(EntityAttributes.GENERIC_ARMOR)?.baseValue = 2.0
            if (resetHealth) health = maxHealth
            setPersistent()
        }
        pettyClaimed = true
    }

    override fun getDimensions(pose: EntityPose): EntityDimensions =
        when {
            !isLesser -> GREATER_DIMENSIONS
            isPetty -> PETTY_DIMENSIONS
            else -> LESSER_DIMENSIONS
        }

    override fun onTrackedDataSet(data: TrackedData<*>) {
        // The client learns the variant from the tracker, so the hitbox has to be recomputed
        // there too or a greater form renders and collides at lesser proportions.
        if (LESSER == data || PETTY == data || BOUND == data) calculateDimensions()
        super.onTrackedDataSet(data)
    }

    /** Side of the opening this Reaper needs, in whole blocks. Drives how much glass it breaks. */
    fun requiredOpening(): Int = if (isLesser) 1 else 2

    // ---------------------------------------------------------------- cling state

    /** Face the Reaper stands on, or null while falling. [Direction.UP] is ordinary ground. */
    var clingFace: Direction?
        get() {
            val raw = dataTracker.get(CLING_FACE)
            return if (raw == AIRBORNE) null else Direction.values()[raw.toInt()]
        }
        private set(value) {
            dataTracker.set(CLING_FACE, value?.ordinal?.toByte() ?: AIRBORNE)
        }

    /** Mirrored to clients so the renderer knows whether the cubes are out. */
    val state: ReaperState
        get() = ReaperState.entries[dataTracker.get(STATE).toInt().coerceIn(0, ReaperState.entries.lastIndex)]

    val isAlerted: Boolean get() = state.isAlerted

    /** Surface normal as a vector; falls back to world up while airborne. */
    fun clingNormal(): Vec3d = SurfaceCling.vectorOf(clingFace ?: Direction.UP)

    fun setDesiredMove(direction: Vec3d) {
        desiredMove = direction
    }

    /**
     * How fast this Reaper travels, in blocks per tick.
     *
     * In a pack where origins and levels move the movement-speed attribute around, a fixed
     * vanilla-sprint figure means a fast build simply walks away from the one mob whose whole
     * identity is that it cannot be outrun. Matching the quarry exactly is the wrong correction
     * though: it reads as rubber-banding, and it makes investing in speed worthless.
     *
     * So the Reaper tracks the target at a fraction just under 1. A fast player still pulls away,
     * but only on sustained open ground, and a Reaper takes walls and ceilings while they take
     * corners. The floor keeps a slow player no better off than before, which is intended: the
     * escape route for everyone is the stealth system, not a footrace.
     *
     * Crucially the figure it tracks is a snapshot taken when the hunt began, not a live reading.
     * See [quarrySprintSnapshot].
     */
    fun speedBlocksPerTick(): Double {
        val balance = MystcraftConfig.current.paradoxReaper
        val pursuitBase = SPRINT_BLOCKS_PER_TICK * balance.speedMultiplier
        val runningToLastKnown = state == ReaperState.HUNTING && investigationTarget?.let {
            squaredDistanceTo(Vec3d.ofCenter(it)) > ReaperSpeed.searchArrivalRadiusSquared(width)
        } == true
        if (isPetty && pettyRestMode == PettyReaperRestMode.FOLLOWING) return pursuitBase
        val base = ReaperSpeed.baselineSpeed(pursuitBase, state, runningToLastKnown)
        if (state != ReaperState.PURSUIT || !balance.matchTargetSpeed) return base

        val quarry = target as? PlayerEntity ?: return base
        val reference =
            if (balance.lockSpeedAtAcquisition && quarrySprintSnapshot > 0.0) quarrySprintSnapshot
            else sprintSpeedOf(quarry)

        return ReaperSpeed.pursuitSpeed(
            base = base,
            targetSprint = reference,
            fraction = balance.targetSpeedFraction,
            maxMultiplier = balance.maxTargetSpeedMultiplier
        )
    }

    /**
     * The quarry's sprint speed as it was when this Reaper first locked on, in blocks per tick.
     *
     * Locking the reference at acquisition is what keeps a speed potion a usable escape tool in a
     * pack full of permanent movement buffs. Track the live value instead and a player who
     * already runs at Speed II is simply chased at Speed II, so drinking Speed II changes nothing
     * and the whole category of consumable becomes dead weight for exactly the builds carrying a
     * permanent buff. Against a snapshot, permanent speed is priced in once and any buff applied
     * *after* the hunt starts opens a real gap.
     *
     * Zero means no snapshot has been taken yet.
     */
    private var quarrySprintSnapshot: Double = 0.0
    private var snapshotOwner: UUID? = null

    /** Takes a fresh reading when the Reaper locks onto a different player. */
    private fun refreshSpeedSnapshot(player: PlayerEntity) {
        if (snapshotOwner == player.uuid) return
        snapshotOwner = player.uuid
        quarrySprintSnapshot = sprintSpeedOf(player)
    }

    private fun clearSpeedSnapshot() {
        snapshotOwner = null
        quarrySprintSnapshot = 0.0
    }

    /**
     * The player's own sprint speed, derived from their movement-speed attribute so that Origins
     * powers, level perks, and potion effects are all picked up automatically.
     *
     * Note this reads the attribute only. Speed granted outside the attribute system — Pehkui's
     * size scaling is the notable one in this pack — is invisible here, and such a player will
     * outpace a Reaper by whatever that hidden multiplier is.
     */
    private fun sprintSpeedOf(player: PlayerEntity): Double {
        return ReaperSpeed.sprintSpeedFor(player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED))
    }

    // ---------------------------------------------------------------- tick and movement

    override fun tick() {
        // Goals are intentionally not allowed to compete with a rider. Feed the current input to
        // the cling transition code before movement so walking into a wall can become climbing it
        // in the same way it does while hunting.
        controllingPassenger?.let {
            val steering = mountedSteering(clingNormal())
            desiredMove = steering.direction
        }
        super.tick()
        if (attackCooldown > 0) attackCooldown--
        if (jumpCooldown > 0) jumpCooldown--
        if (jumpDetachTicks > 0) jumpDetachTicks--

        if (world.isClient) {
            advanceRenderNormal(clingNormal())
            // Cubes burst out fast and retract over roughly two seconds. Do not leave the visual
            // alert hanging around after the server has declared the Reaper dormant: the cubes
            // and light are the player's readable proof that it is still engaged.
            val wanted = if (isAlerted) 1.0f else 0.0f
            val rate = if (wanted > huntGlow) 0.22f else 0.12f
            huntGlow += (wanted - huntGlow) * rate
            if (!isAlerted && huntGlow < 0.005f) huntGlow = 0.0f
        } else {
            updateCling()
            if (isPetty) {
                tickPettyBehavior()
            } else {
                Vibrations.Ticker.tick(world, vibrationListenerData, vibrationCallback)
                tickAwareness()
                tickSummoning()
            }
            tickCompanionHealing()
        }
    }

    private fun tickCompanionHealing() {
        if (!isPermanentCompanion || !isAlive || health >= maxHealth) return
        if (companionRegenDelay > 0) {
            companionRegenDelay--
            return
        }
        if (age % ReaperCompanionHealing.PASSIVE_INTERVAL_TICKS == 0) {
            heal(ReaperCompanionHealing.PASSIVE_AMOUNT)
        }
    }

    /** Marks a lesser as this greater Reaper's actual summoned backup. */
    fun markSummonedBackup(summonerUuid: UUID) {
        summonedByUuid = summonerUuid
    }

    /** The Greater that called this lesser in, if it is still loaded and alive. */
    fun summonedMaster(): ParadoxReaperEntity? {
        val serverWorld = world as? ServerWorld ?: return null
        val master = summonedByUuid?.let(serverWorld::getEntity) as? ParadoxReaperEntity
        return master?.takeIf { it.isAlive && !it.isPetty }
    }

    // ---------------------------------------------------------------- senses

    /**
     * Runs one tick of sight and alertness.
     *
     * A dormant Reaper barely looks: it scans on a slow interval, through a narrow cone, at short
     * range. That is what makes sneaking past one viable, and it is the reason acquisition is not
     * left to a vanilla target goal, which would notice a player through a wall of ticks the
     * moment they entered follow range.
     */
    private fun tickAwareness() {
        if (isDrone) {
            // Drones never acquire players. Held visibly alert so the lit silhouette, the cubes,
            // and the leg rig are all on screen while movement is being watched.
            target = null
            dataTracker.set(STATE, ReaperState.PURSUIT.ordinal.toByte())
            return
        }

        val existing = target
        if (existing == null || !existing.isAlive || ReaperTargetFilter.isIgnoredEntity(existing) ||
            existing is PlayerEntity && !canHunt(existing)
        ) {
            target = null
            sawTargetLastScan = false
        }

        val scanDue = isSightScanDue()
        val seen = if (scanDue) scanForTarget() else null
        if (scanDue) sawTargetLastScan = seen != null
        val previous = awareness.state
        awareness.tick(sawTargetLastScan)

        if (seen != null) target = seen
        if (awareness.state == ReaperState.PURSUIT) investigationTarget = seen?.blockPos ?: investigationTarget

        // A sound supplies a location, not supernatural knowledge of the source. Once sight is
        // broken, navigation and pane breaking use the last known block rather than tracking the
        // hidden player's live entity position through walls.
        if (awareness.state != ReaperState.PURSUIT) target = null

        // Price the quarry's speed in once per encounter; see quarrySprintSnapshot.
        (target as? PlayerEntity)?.let { refreshSpeedSnapshot(it) }

        if (awareness.justAlerted) onFirstAlert()

        if (awareness.state == ReaperState.DORMANT && previous != ReaperState.DORMANT) {
            // Stood down: forget the player entirely so the next encounter starts clean, and
            // take a fresh speed reading next time rather than reusing a stale buffed one.
            target = null
            investigationTarget = null
            clearSpeedSnapshot()
        }

        dataTracker.set(STATE, awareness.state.ordinal.toByte())
    }

    /** The player this Reaper can actually see right now, or null. */
    private fun isSightScanDue(): Boolean {
        val balance = MystcraftConfig.current.paradoxReaper
        val interval = when (awareness.state) {
            ReaperState.DORMANT -> balance.dormantScanIntervalTicks
            ReaperState.SETTLING -> balance.dormantScanIntervalTicks / 3
            else -> 2
        }.coerceAtLeast(1)
        return age % interval == 0
    }

    private fun scanForTarget(): LivingEntity? {
        val balance = MystcraftConfig.current.paradoxReaper
        val baseRange = when (awareness.state) {
            ReaperState.DORMANT -> balance.dormantSightRange
            ReaperState.SETTLING -> balance.dormantSightRange * 1.6
            else -> balance.alertSightRange
        }
        val halfAngleCos = Math.cos(Math.toRadians(
            (if (awareness.state.isAlerted) ALERT_FIELD_OF_VIEW else DORMANT_FIELD_OF_VIEW) / 2.0
        ))

        var best: LivingEntity? = null
        var bestDistance = Double.MAX_VALUE

        // Players always win, even when a pet or nearby settling target is closer.
        for (player in world.players) {
            if (!canHunt(player)) continue

            // Crouching shortens how far a Reaper can pick a player out, on top of silencing the
            // footstep vibrations it would otherwise hear.
            val range = if (player.isSneaking) baseRange * balance.sneakSightMultiplier else baseRange
            val distanceSquared = squaredDistanceTo(player)
            if (distanceSquared > range * range || distanceSquared >= bestDistance) continue

            if (!canSeeQuarry(player, halfAngleCos)) continue

            best = player
            bestDistance = distanceSquared
        }
        if (best != null) return best

        val nearby = world.getEntitiesByClass(
            LivingEntity::class.java,
            boundingBox.expand(baseRange)
        ) { candidate ->
            candidate !== this && candidate.isAlive && candidate !is ParadoxReaperEntity &&
                candidate !is PlayerEntity && !ReaperTargetFilter.isIgnoredEntity(candidate)
        }

        // Player-owned pets and scoreboard allies are the second priority and remain valid at
        // the same visual range as their player.
        for (candidate in nearby) {
            if (!isPlayerAlly(candidate)) continue
            val distanceSquared = squaredDistanceTo(candidate)
            if (distanceSquared > baseRange * baseRange || distanceSquared >= bestDistance) continue
            if (!canSeeQuarry(candidate, halfAngleCos)) continue
            best = candidate
            bestDistance = distanceSquared
        }
        if (best != null) return best

        // Once an ordinary mob has directly disturbed a Reaper, keep sight of that exact mob
        // for the rest of the chase. The settling-only fallback below is deliberately local,
        // but an already chosen quarry must not disappear from awareness on the next scan just
        // because it crossed the ten-block acquisition boundary.
        val disturbedQuarry = target
        if (disturbedQuarry != null && disturbedQuarry.isAlive &&
            disturbedQuarry !is PlayerEntity && !isPlayerAlly(disturbedQuarry)
        ) {
            val distanceSquared = squaredDistanceTo(disturbedQuarry)
            if (distanceSquared <= baseRange * baseRange && canSeeQuarry(disturbedQuarry, halfAngleCos)) {
                return disturbedQuarry
            }
        }

        return best
    }

    private fun canHunt(player: PlayerEntity): Boolean =
        player.isAlive && !player.isSpectator &&
            !ReaperTargetFilter.isTemporarilyRagdolled(player) &&
            (player !is ServerPlayerEntity || !ReaperArrivalGrace.isProtected(player)) &&
            (!player.isCreative || MystcraftConfig.current.paradoxReaper.targetCreativePlayers)

    /** A tame creature owned by an eligible player, or a living member of their scoreboard team. */
    private fun isPlayerAlly(entity: LivingEntity): Boolean {
        if (entity is ParadoxReaperEntity && entity.isPetty) {
            val owner = entity.pettyOwner()
            if (owner != null && canHunt(owner)) return true
        }
        val owner = (entity as? TameableEntity)?.owner as? PlayerEntity
        if (owner != null && canHunt(owner)) return true
        return world.players.any { player -> canHunt(player) && player.isTeammate(entity) }
    }

    private fun isPriorityQuarry(entity: LivingEntity): Boolean =
        when (entity) {
            is PlayerEntity -> canHunt(entity)
            else -> isPlayerAlly(entity) || isAudibleNeighbour(entity)
        }

    /**
     * A living thing near enough that a roosting Reaper will get up and deal with it.
     *
     * Deliberately narrower than the creature's full hearing range. A Reaper's business is the
     * player, and one that abandoned its perch for every distant sheep would never be found where
     * it settled. Inside this radius, though, nothing gets a pass for not being the player: the
     * Age is correcting stable matter, and a villager is stable matter.
     */
    private fun isAudibleNeighbour(entity: LivingEntity): Boolean {
        if (entity is ParadoxReaperEntity || entity is LittleAnomalyEntity) return false
        if (!entity.isAlive || ReaperTargetFilter.isIgnoredEntity(entity)) return false
        val radius = MystcraftConfig.current.paradoxReaper.neighbourHearingRadius
        return squaredDistanceTo(entity) <= radius * radius
    }

    /** Resolves arrows and other projectiles back to the living faction member that caused them. */
    private fun livingSource(entity: Entity?): LivingEntity? =
        when (entity) {
            is LivingEntity -> entity
            is ProjectileEntity -> entity.owner as? LivingEntity
            else -> null
        }

    /** Commits to the exact living source of a relevant disturbance. */
    private fun pursueDisturber(entity: LivingEntity, around: BlockPos) {
        if (ReaperTargetFilter.isIgnoredEntity(entity)) return
        target = entity
        investigationTarget = around
        sawTargetLastScan = true
        awareness.onSighted()
        dataTracker.set(STATE, awareness.state.ordinal.toByte())
    }

    /** Field-of-view and a collision-shape ray that deliberately ignores tagged glass. */
    private fun canSeeQuarry(entity: LivingEntity, halfAngleCos: Double): Boolean {
        val start = Vec3d(x, eyeY, z)
        val end = Vec3d(entity.x, entity.eyeY, entity.z)
        val ray = end.subtract(start)
        if (ray.lengthSquared() < 1.0e-6) return true
        if (ray.normalize().dotProduct(facing()) < halfAngleCos) return false

        val steps = Math.ceil(ray.length() * 4.0).toInt().coerceAtLeast(1)
        var previous: BlockPos? = null
        for (step in 1 until steps) {
            val point = start.add(ray.multiply(step.toDouble() / steps))
            val block = BlockPos.ofFloored(point)
            if (block == previous) continue
            previous = block
            val state = world.getBlockState(block)
            if (state.isIn(TRANSPARENT_TO_SIGHT)) continue
            val shape = state.getCollisionShape(world, block)
            if (!shape.isEmpty && shape.raycast(start, end, block) != null) return false
        }
        return true
    }

    /** Live quarry while visible, otherwise the last place it was seen for pane breaking. */
    fun glassBreakFocus(): Vec3d? = when (awareness.state) {
        ReaperState.PURSUIT -> target?.takeIf { it.isAlive }?.pos ?: investigationTarget?.let(Vec3d::ofCenter)
        ReaperState.HUNTING -> investigationTarget?.let(Vec3d::ofCenter)
        else -> null
    }

    /** Where the body is pointing, in the plane of whatever surface it is on. */
    private fun facing(): Vec3d =
        SurfaceCling.forwardOnSurface(clingNormal(), bodyYaw, Vec3d(0.0, 0.0, 1.0))

    /** Waking up: shriek, and pull every Reaper in earshot into the search. */
    private fun onFirstAlert() {
        world.playSound(
            null, x, y, z, SoundEvents.ENTITY_WARDEN_LISTENING_ANGRY, SoundCategory.HOSTILE, 1.2f, 1.5f
        )
        val here = investigationTarget ?: blockPos
        alertNeighbours(here)
    }

    /**
     * Tells nearby Reapers where to look. They enter the search rather than the chase, so a pack
     * converges on the disturbance instead of every one of them beelining at a player none of
     * them has actually seen.
     */
    private fun alertNeighbours(around: BlockPos) {
        val radius = MystcraftConfig.current.paradoxReaper.alertShareRadius.toDouble()
        val box = Box.of(Vec3d.ofCenter(around), radius * 2, radius * 2, radius * 2)
        world.getEntitiesByClass(ParadoxReaperEntity::class.java, box) { it !== this && it.isAlive }
            .forEach { it.disturb(around) }
    }

    /** Turns this Reaper into a lure-following test subject. */
    fun makeDrone() {
        isDrone = true
        setPersistent()
        target = null
        dataTracker.set(STATE, ReaperState.PURSUIT.ordinal.toByte())
    }

    /** Raises this Reaper into a search centred on [around]. Safe to call from anywhere. */
    fun disturb(around: BlockPos) {
        if (world.isClient) return
        if (isDrone) return
        investigationTarget = around
        val wasDormant = !awareness.state.isAlerted
        awareness.onDisturbed()
        dataTracker.set(STATE, awareness.state.ordinal.toByte())
        if (wasDormant) {
            world.playSound(
                null, x, y, z, SoundEvents.ENTITY_WARDEN_AGITATED, SoundCategory.HOSTILE, 0.9f, 1.6f
            )
        }
    }

    // ---------------------------------------------------------------- vibrations

    override fun getVibrationListenerData(): Vibrations.ListenerData = vibrationListenerData

    override fun getVibrationCallback(): Vibrations.Callback = vibrationCallback

    override fun updateEventHandler(callback: BiConsumer<EntityGameEventHandler<*>, ServerWorld>) {
        val serverWorld = world
        if (serverWorld is ServerWorld) callback.accept(gameEventHandler, serverWorld)
    }

    /** Warden vibration transport with Reaper-specific quarry filtering. */
    private inner class ReaperVibrationCallback : Vibrations.Callback {
        private val positionSource: PositionSource =
            EntityPositionSource(this@ParadoxReaperEntity, this@ParadoxReaperEntity.standingEyeHeight)

        override fun getRange(): Int = MystcraftConfig.current.paradoxReaper.hearingRange

        override fun getPositionSource(): PositionSource = positionSource

        override fun getTag(): TagKey<GameEvent> = GameEventTags.WARDEN_CAN_LISTEN

        override fun accepts(
            world: ServerWorld,
            pos: BlockPos,
            event: GameEvent,
            emitter: GameEvent.Emitter
        ): Boolean {
            if (isRemoved || isAiDisabled || isDead) return false
            // Reapers do not hunt each other, and a pack of them would otherwise deafen itself.
            val rawSource = emitter.sourceEntity()
            if (rawSource != null && ReaperTargetFilter.isIgnoredEntity(rawSource)) return false
            val source = livingSource(rawSource)
            if (source is ParadoxReaperEntity) return false
            if (source is PlayerEntity) return canHunt(source)
            // Anything alive and close enough to be worth crossing the room for. Restricting this
            // to players and their allies is what made a roost safe to wander through: a villager
            // straying into the cave was inaudible by rule, however much noise it made. The
            // correction is not fussy about what the error is made of.
            if (source != null) return isPlayerAlly(source) || isAudibleNeighbour(source)
            return true
        }

        override fun accept(
            world: ServerWorld,
            pos: BlockPos,
            event: GameEvent,
            sourceEntity: net.minecraft.entity.Entity?,
            entity: net.minecraft.entity.Entity?,
            distance: Float
        ) {
            if (isDead) return
            if (sourceEntity != null && ReaperTargetFilter.isIgnoredEntity(sourceEntity)) return
            if (entity != null && ReaperTargetFilter.isIgnoredEntity(entity)) return
            val disturber = livingSource(sourceEntity) ?: livingSource(entity)
            if (disturber != null && isPriorityQuarry(disturber)) {
                pursueDisturber(disturber, pos)
            } else {
                disturb(pos)
            }
            alertNeighbours(pos)
        }
    }

    /**
     * Re-derives the clinging face after this tick's movement has already been applied.
     *
     * A face the Reaper is pressed against gets offered to the search first. That one rule turns
     * "walked into a wall" into "started climbing the wall" with no transition state machine.
     */
    private fun updateCling() {
        val current = clingFace

        // Just jumped: stay off every surface until the window closes, or the creature re-grips
        // the one it was trying to leave before it has cleared it.
        if (jumpDetachTicks > 0) {
            clingFace = null
            wrapFace = null
            setNoGravity(false)
            return
        }

        // A wrap already under way outranks a fresh look at the terrain. Halfway around a lip the
        // body is over nothing, so re-deriving the intent from scratch each tick would drop it
        // the moment it committed.
        if (wrapFace != null) {
            wrapTicks--
            if (wrapTicks <= 0 || current == wrapFace) wrapFace = null
        }
        if (wrapFace == null) {
            val descent = descendCandidate(current)
            if (descent != null) {
                wrapFace = descent
                wrapTicks = WRAP_TICKS
            }
        }

        if (faceHoldTicks > 0) faceHoldTicks--
        val preferred = wrapFace ?: climbCandidate(current) ?: current ?: lastSupport
        val support = SurfaceCling.findSupport(world, boundingBox, preferred)

        if (support != null) {
            if (support == wrapFace) wrapFace = null
            // Vanilla's step-up is hardcoded to the world's Y axis. On a floor that is the stair
            // climbing this creature wants; on a wall or a ceiling it is a shove along the
            // surface, or straight into it, applied by the collision pass behind the cling
            // system's back. It shows up as the body popping as it crosses uneven geometry.
            stepHeight = if (support == Direction.UP) FLOOR_STEP_HEIGHT else 0.0f
            if (current != null && support != current) {
                val oldUp = SurfaceCling.vectorOf(current)
                val oldPlanar = desiredMove.subtract(oldUp.multiply(desiredMove.dotProduct(oldUp)))
                val oldHeading = when {
                    oldPlanar.lengthSquared() > 1.0e-6 -> oldPlanar.normalize()
                    surfaceHeading.lengthSquared() > 1.0e-6 -> surfaceHeading
                    else -> Vec3d.ZERO
                }
                if (oldHeading.lengthSquared() > 1.0e-6) {
                    surfaceHeading = SurfaceCling.transportDirection(
                        oldHeading, oldUp, SurfaceCling.vectorOf(support)
                    )
                }
            }
            if (current != null && support != current) {
                // Remember what was just left, and refuse to go straight back to it. Inside a
                // corner both walls are within gripping range at once, so the face the creature
                // wants is decided by a heading that itself flips as soon as the face does: it
                // transitions to the second wall, immediately reads the first as the way onward,
                // transitions back, and buzzes between the two several times a second. Holding
                // the new face briefly lets it commit, walk, and settle.
                previousFace = current
                faceHoldTicks = FACE_HOLD_TICKS
            }
            clingFace = support
            lastSupport = support
            ungroundedTicks = 0
            setNoGravity(true)
            return
        }

        // Rounding an edge can leave the hitbox briefly out of probe range of both the surface it
        // is leaving and the one it is joining. Detaching on the first such tick is what made a
        // Reaper drop off a ceiling it was merely turning a corner on, and a falling Reaper is
        // moving away from the surface so it never recovers. Hold the last good face for a few
        // ticks and pull harder toward it instead.
        ungroundedTicks++
        val recent = lastSupport

        // Coming around a lip is precisely the case this grace was written for, and a wrap takes
        // longer than an ordinary corner. Without the extension the creature detached partway
        // through, gravity came back, and `travelUnattached` took over — which does not apply the
        // wrap's reduced throttle, so it sailed away from the face and fell after all. That is
        // the original bug wearing a different hat.
        val graceTicks = if (wrapFace != null) maxOf(CLING_GRACE_TICKS, WRAP_TICKS) else CLING_GRACE_TICKS
        if (recent != null && ungroundedTicks <= graceTicks) {
            clingFace = recent
            setNoGravity(true)
        } else {
            clingFace = null
            setNoGravity(false)
        }
    }

    /** True while the Reaper is coasting on grace rather than on a confirmed surface. */
    private fun isRecoveringGrip(): Boolean = ungroundedTicks > 0

    /** How far into the grace window the creature is, 0 firmly held and 1 about to let go. */
    private fun regripFraction(): Double =
        (ungroundedTicks.toDouble() / CLING_GRACE_TICKS).coerceIn(0.0, 1.0)

    /** The face the Reaper is walking into, when it is solid and worth transferring onto. */
    private fun climbCandidate(current: Direction?): Direction? {
        if (current == null) return null
        if (desiredMove.lengthSquared() < 1.0e-6) return null

        val up = SurfaceCling.vectorOf(current)
        val planar = desiredMove.subtract(up.multiply(desiredMove.dotProduct(up)))
        if (planar.lengthSquared() < 1.0e-6) return null

        val heading = SurfaceCling.dominantFace(planar)
        val candidate = SurfaceCling.concaveTransitionFace(current, planar) ?: return null

        // Not straight back to the face just left; see [faceHoldTicks].
        if (candidate == previousFace && faceHoldTicks > 0) return null

        // Already touching it. This is the inside corner where two walls meet, and it is the case
        // the lookahead probe was worst at: that probe sits a fixed distance ahead of the body
        // centre, so against a thin wall, an irregular face, or a corner the creature is already
        // flush with, it can sample straight past the very block being leant on and report
        // nothing. The transition then never fired, the hunt goal counted the creature as stalled
        // after eight ticks and steered it sideways, which cancelled the climb — and the whole
        // cycle repeated. That oscillation is what reads as a Reaper rubbing its face on a wall
        // and juddering instead of walking up it. Asking the real hitbox whether it is in contact
        // is both cheaper and exactly the question worth asking.
        if (SurfaceCling.hasSurface(world, boundingBox, candidate)) return candidate

        val probe = leadingEdgeProbe(heading)
        return if (SurfaceCling.hasSurface(world, probe, candidate)) candidate else null
    }

    /**
     * The face to wrap onto when walking over a convex lip, such as the top of a cliff.
     *
     * [climbCandidate] only fires when something is in the way, which covers walking into a wall
     * but never covers walking off the edge of one. Nothing blocks a Reaper at a cliff top: the
     * floor simply stops, so no transition was ever considered and it would stroll off the lip
     * and fall instead of rolling over onto the face and descending.
     *
     * The rule is the mirror image of climbing. Meeting a wall makes the new up the opposite of
     * the heading; running out of floor makes it the heading itself, because the face continuing
     * down past the lip points the way the creature was already walking.
     */
    private fun descendCandidate(current: Direction?): Direction? {
        if (current == null) return null
        if (desiredMove.lengthSquared() < 1.0e-6) return null

        val up = SurfaceCling.vectorOf(current)
        val planar = desiredMove.subtract(up.multiply(desiredMove.dotProduct(up)))
        if (planar.lengthSquared() < 1.0e-6) return null

        val heading = SurfaceCling.dominantFace(planar)
        if (heading == current || heading == current.opposite) return null

        // Both probes are small boxes placed out past the leading edge rather than the whole
        // hitbox shifted forward. A body this wide nudged ahead still overlaps the floor it is
        // standing on, so testing the shifted hitbox reports solid ground right up until the
        // creature has already walked off, which is far too late to roll over the lip.
        //
        // Height is what the old version got wrong. Both tests were run against one box sitting
        // at the body's centre, and asking whether there is floor beneath a box floating a metre
        // in the air answers nothing about the ground, while the wall to grab is below the lip
        // and so was never in range of that box either. The consequence was that this never
        // fired at all: a Reaper reaching a cliff simply strolled off it and fell, which is the
        // "jumps down instead of climbing down" behaviour. Each question now gets a box where
        // its answer actually lives.
        val floorAhead = edgeProbe(current, heading, PROBE_AT_CONTACT, LIP_LOOKAHEAD)
        if (SurfaceCling.hasSurface(world, floorAhead, current)) return null

        // The floor has run out ahead, so look for the face carrying on down past the lip.
        //
        // Reach and depth both matter more than they look. The old single probe sat a full
        // LIP_LOOKAHEAD past the body's leading edge, which put it clear of the wall it was
        // hunting for and left only a sliver of the box overlapping once the back-offset was
        // applied — so the descent was detected in a narrow band as the body approached the edge
        // and missed entirely whenever it arrived at speed. Sitting the probe just past the edge
        // and trying several depths makes the same geometry land solidly inside the face.
        for (drop in LIP_DESCENT_DROPS) {
            val faceBelow = edgeProbe(current, heading, -drop, LIP_DESCENT_LOOKAHEAD)
            if (SurfaceCling.hasSurface(world, faceBelow, heading)) return heading
        }
        return null
    }

    /**
     * A small probe box placed [lookahead] blocks past the body's leading edge along [heading],
     * and [alongUp] blocks from the surface the creature is currently standing on.
     *
     * Offsets are measured from the contact plane rather than from the body's centre, so the same
     * call means the same thing whether the creature is on a floor, a wall, or a ceiling.
     */
    private fun edgeProbe(current: Direction, heading: Direction, alongUp: Double, lookahead: Double): Box {
        val up = SurfaceCling.vectorOf(current)
        val contact = boundingBox.center.subtract(up.multiply(extentAlong(current)))
        val at = contact
            .add(SurfaceCling.vectorOf(heading).multiply(extentAlong(heading) + lookahead))
            .add(up.multiply(alongUp))
        return Box(
            at.x - LIP_PROBE_HALF, at.y - LIP_PROBE_HALF, at.z - LIP_PROBE_HALF,
            at.x + LIP_PROBE_HALF, at.y + LIP_PROBE_HALF, at.z + LIP_PROBE_HALF
        )
    }

    /** Half the body's size along one axis: its height on the vertical, its width otherwise. */
    private fun extentAlong(face: Direction): Double =
        if (face.axis == Direction.Axis.Y) height / 2.0 else width / 2.0

    private fun leadingEdgeProbe(heading: Direction): Box {
        val centre = boundingBox.center
        val reach = width / 2.0 + LIP_LOOKAHEAD
        val at = centre.add(SurfaceCling.vectorOf(heading).multiply(reach))
        return Box(
            at.x - LIP_PROBE_HALF, at.y - LIP_PROBE_HALF, at.z - LIP_PROBE_HALF,
            at.x + LIP_PROBE_HALF, at.y + LIP_PROBE_HALF, at.z + LIP_PROBE_HALF
        )
    }

    override fun travel(movementInput: Vec3d) {
        // A charged rider jump is spent here rather than where the key was released, because this
        // is the one place guaranteed to run on whichever side is actually simulating the body.
        if (jumpRequested) spendMountJump()

        val face = clingFace
        // The detach window is checked alongside the tracked face, not instead of it. That face
        // is synchronised, so on the side that did not originate the jump it can still read as
        // attached for a tick or two, and the clinging branch below would overwrite the launch
        // velocity with surface locomotion before the creature had left the ground.
        if (face == null || jumpDetachTicks > 0) {
            travelUnattached()
            return
        }

        val up = SurfaceCling.vectorOf(face)
        val mounted = mountedSteering(up)
        val requestedMove = if (controllingPassenger != null) mounted.direction else desiredMove
        val flattened = requestedMove.subtract(up.multiply(requestedMove.dotProduct(up)))
        val pointsThroughSurface = requestedMove.lengthSquared() > 1.0e-6 &&
            requestedMove.dotProduct(up) < -requestedMove.length() * 0.35
        val carried = SurfaceCling.projectOntoPlane(surfaceHeading, up, Vec3d.ZERO)
        val heading = when {
            pointsThroughSurface && surfaceHeading.lengthSquared() > 1.0e-6 -> carried
            flattened.lengthSquared() > 1.0e-6 -> flattened.normalize()
            else -> Vec3d.ZERO
        }
        if (!pointsThroughSurface && heading.lengthSquared() > 1.0e-6) surfaceHeading = heading

        // Everything the eye sees is filtered except the one thing actually moving the creature.
        // The goals recompute a desired direction from scratch every tick, and around a pillar or
        // a doorway that direction can flip between two options; the body then changes course
        // instantly, and no amount of smoothing applied to the legs downstream can hide a hitbox
        // that is jinking. Turning the travel direction on a spring gives the whole creature the
        // same continuity its pose already has. The half-life is short on purpose — this is
        // steering, not decoration, and a sluggish one would fail to round corners at all.
        val steered = if (heading.lengthSquared() > 1.0e-6) {
            travelHeading.reproject(SurfaceCling.projectOntoPlane(travelHeading.value, up, heading))
            travelHeading.advance(
                heading, ReaperSmoothing.frequencyForHalfLife(TRAVEL_HALF_LIFE_TICKS), 1.0
            )
        } else {
            heading
        }

        // Adhesion is applied every tick, not only when unsupported: continuously holding the
        // Reaper against the face is what keeps a ceiling crawl stable through collision passes.
        // A harder pull while coasting on grace, so a Reaper crossing an edge is dragged back
        // onto the surface rather than merely failing to fall for a few ticks.
        // Ramped rather than switched. This used to triple the instant a support probe missed and
        // drop back the instant one landed, and on broken ground or around a corner that probe
        // flickers between hit and miss from tick to tick — so the body was being yanked into the
        // surface at three times strength every other tick. That is a vibration with nothing to do
        // with the gait, and no amount of filtering downstream can hide it.
        val grip = ADHESION_PULL * (1.0 + (REGRIP_MULTIPLIER - 1.0) * regripFraction())
        val baseSpeed = if (controllingPassenger != null) {
            MOUNT_SPEED_BLOCKS_PER_TICK * mounted.throttle
        } else {
            speedBlocksPerTick()
        }

        // Rolling over a lip is where a wall-crawler either descends or falls off, and the
        // difference is entirely how far it drifts forward while it drops. Carrying full speed
        // over the edge throws the body clear of the face it is trying to catch, and once it is
        // further out than the grip probe reaches, nothing brings it back and it free-falls the
        // rest of the way down. Easing off the throttle and pulling down hard for the few ticks
        // of the wrap keeps the body against the face as it comes around, so the same surface
        // walking that climbs a wall carries it down one.
        val wrapping = wrapFace != null
        val travelSpeed = if (wrapping) baseSpeed * WRAP_FORWARD_FACTOR else baseSpeed
        val descent = if (wrapping) WRAP_DESCENT_PULL else 0.0
        velocity = steered.multiply(travelSpeed).subtract(up.multiply(grip + descent))
        move(MovementType.SELF, velocity)

        if (steered.lengthSquared() > 1.0e-6) {
            yaw = approachAngle(yaw, SurfaceCling.yawFor(steered, up), MAX_TURN_DEGREES_PER_TICK)
            bodyYaw = yaw
        }
    }

    private fun mountedSteering(up: Vec3d): ReaperMountMotion {
        val rider = controllingPassenger as? PlayerEntity ?: return ReaperMountMotion(Vec3d.ZERO, 0.0)
        val fallback = when {
            surfaceHeading.lengthSquared() > 1.0e-6 -> surfaceHeading
            else -> facing()
        }
        return ReaperMountSteering.resolve(
            rider.rotationVector,
            up,
            fallback,
            rider.forwardSpeed,
            rider.sidewaysSpeed
        )
    }

    /**
     * Picks a clear tangent when straight-line steering has made no progress. The hunt goal holds
     * this direction briefly, giving a wide body time to sidestep a pillar, doorway, or concave
     * corner before trying the quarry again.
     */
    fun findUnstuckHeading(towards: Vec3d): Vec3d? {
        val up = clingNormal()
        val direct = towards.subtract(up.multiply(towards.dotProduct(up)))
        val forward = when {
            direct.lengthSquared() > 1.0e-6 -> direct.normalize()
            surfaceHeading.lengthSquared() > 1.0e-6 -> SurfaceCling.projectOntoPlane(surfaceHeading, up, Vec3d(1.0, 0.0, 0.0))
            else -> SurfaceCling.projectOntoPlane(facing(), up, Vec3d(1.0, 0.0, 0.0))
        }
        val side = up.crossProduct(forward).normalize()
        val candidates = listOf(
            side.add(forward.multiply(0.25)).normalize(),
            side.multiply(-1.0).add(forward.multiply(0.25)).normalize(),
            side,
            side.multiply(-1.0),
            forward.multiply(-1.0)
        )
        return candidates
            .map { it to clearanceScore(it, forward) }
            .filter { it.second > 0.0 }
            .maxByOrNull { it.second + random.nextDouble() * 0.05 }
            ?.first
    }

    private fun clearanceScore(direction: Vec3d, preferred: Vec3d): Double {
        var clearSteps = 0
        for (step in 1..UNSTICK_PROBE_STEPS) {
            if (!world.isSpaceEmpty(boundingBox.offset(direction.multiply(UNSTICK_PROBE_STEP * step)))) break
            clearSteps++
        }
        return clearSteps + direction.dotProduct(preferred) * 0.2
    }

    /** Number of full-hitbox steps available for a roaming heading. */
    fun roamingClearance(direction: Vec3d): Int {
        if (direction.lengthSquared() < 1.0e-6) return 0
        val normalized = direction.normalize()
        var clearSteps = 0
        for (step in 1..UNSTICK_PROBE_STEPS) {
            if (!world.isSpaceEmpty(boundingBox.offset(normalized.multiply(UNSTICK_PROBE_STEP * step)))) break
            clearSteps++
        }
        return clearSteps
    }

    /**
     * How many of the six cells around [cell] are solid.
     *
     * Stands in for how tucked-away a spot is. Inverse kinematics means this creature does not
     * need a flat surface to sit on, only somewhere it fits, so the places that suit it are the
     * ones with geometry close on several sides rather than the open middle of a room.
     */
    fun enclosingFaces(cell: BlockPos): Int =
        Direction.values().count { face ->
            val neighbour = cell.offset(face)
            world.getBlockState(neighbour).isSolidBlock(world, neighbour)
        }

    /** Records the current position as this Reaper's perch, to be returned to after a disturbance. */
    fun rememberRoost() {
        val face = clingFace ?: return
        roostAnchor = blockPos
        roostFace = face
    }

    /** True when the creature is settled at the perch it remembered. */
    fun isAtRoost(): Boolean {
        val anchor = roostAnchor ?: return false
        return clingFace != null &&
            pos.squaredDistanceTo(Vec3d.ofBottomCenter(anchor)) <=
            ReaperRoostScoring.arrivalRadiusSquared(width)
    }

    /** Drops a perch that has been lit up, filled in, or otherwise stopped being usable. */
    fun forgetRoostIfUnusable() {
        val anchor = roostAnchor ?: return
        val stillDark = ReaperRoostScoring.isDarkEnough(world.getLightLevel(anchor))
        if (!stillDark || !canOccupyRoost(anchor)) {
            roostAnchor = null
            roostFace = null
        }
    }

    /** True when the full body fits at [cell] and at least one adjacent face can support it. */
    fun canOccupyRoost(cell: BlockPos): Boolean {
        val destination = Vec3d(cell.x + 0.5, cell.y.toDouble(), cell.z + 0.5)
        val fitting = boundingBox.offset(destination.subtract(pos))
        return world.isSpaceEmpty(fitting) && SurfaceCling.findSupport(world, fitting, null) != null
    }

    /** Rotates [current] toward [target] by at most [maxStep] degrees, the short way around. */
    private fun approachAngle(current: Float, target: Float, maxStep: Float): Float {
        val delta = MathHelper.wrapDegrees(target - current)
        return current + delta.coerceIn(-maxStep, maxStep)
    }

    /**
     * Turns the rendered surface normal toward the real one, so the whole body visibly rolls
     * through a surface change instead of snapping to the new face.
     *
     * This is the first of two stages; the renderer springs again on top of it between ticks. It
     * used to turn at a fixed angular rate, which starts and stops instantaneously, and that
     * corner survived the second stage and was most of why a floor-to-wall transition still read
     * as abrupt however much the client-side filter was softened. Both stages are now springs.
     */
    private fun advanceRenderNormal(target: Vec3d) {
        surfaceRoll.reproject(renderNormal)
        renderNormal = surfaceRoll.advance(
            target, ReaperSmoothing.frequencyForHalfLife(ROLL_HALF_LIFE_TICKS), 1.0
        )
    }

    /** Free fall, kept floaty on purpose so the Reaper can steer into a wall mid-drop. */
    private fun travelUnattached() {
        val steer = if (desiredMove.lengthSquared() > 1.0e-6) desiredMove.normalize().multiply(0.02) else Vec3d.ZERO
        val pulled = velocity.add(steer).add(reachForSurface()).add(0.0, -AIR_GRAVITY, 0.0)
        velocity = Vec3d(pulled.x * AIR_DRAG, pulled.y.coerceAtLeast(-MAX_FALL_SPEED), pulled.z * AIR_DRAG)
        move(MovementType.SELF, velocity)
    }

    /**
     * Acceleration toward the nearest surface while airborne.
     *
     * A wall-crawler that merely falls when it has nothing to hold is wrong on its own terms, and
     * it also made the creature unusable near ceilings: anything placed in open air below one was
     * never inside gripping range to begin with, so it just dropped. Reaching gets it to a face,
     * at which point the ordinary short-range probe takes over and it grips.
     */
    private fun reachForSurface(): Vec3d {
        if (jumpDetachTicks > 0) return Vec3d.ZERO
        val face = SurfaceCling.findNearbySupport(world, boundingBox, SURFACE_SEEK_RANGE) ?: return Vec3d.ZERO
        // The face is the outward normal of the surface, so travelling toward it means -face.
        return SurfaceCling.vectorOf(face).multiply(-SURFACE_SEEK_PULL)
    }

    /** Launches the Reaper off its surface to cross a gap or pounce. */
    fun lunge(direction: Vec3d, power: Double, upBias: Double = DEFAULT_LUNGE_UP_BIAS) {
        val up = clingNormal()
        velocity = direction.normalize().multiply(power).add(up.multiply(power * upBias))
        jumpDetachTicks = JUMP_DETACH_TICKS
        // Leaving the surface on purpose, so grace must not drag it straight back down.
        lastSupport = null
        ungroundedTicks = CLING_GRACE_TICKS + 1
        clingFace = null
        wrapFace = null
        setNoGravity(false)
        velocityModified = true
    }

    /**
     * Plans and performs a jump across a gap toward [towards]; true if it launched.
     *
     * Both halves of the plan are expressed against the clinging normal rather than against world
     * up, which is what makes one routine serve every orientation. Landing spots are searched
     * along the creature's own surface plane and offset along its own up, so a Reaper on a wall
     * looks for ledges across the wall rather than across the ground far below. The launch angle
     * is then measured off that same plane, so a jump from a floor arcs upward, a jump from a
     * wall throws the creature out away from it, and a jump from a ceiling drops away downward,
     * with no special case for any of them.
     */
    fun tryTraversalJump(towards: Vec3d): Boolean {
        val balance = MystcraftConfig.current.paradoxReaper
        if (!balance.canJumpGaps || jumpCooldown > 0) return false
        if (clingFace == null || world.isClient) return false

        val up = clingNormal()
        val planar = towards.subtract(up.multiply(towards.dotProduct(up)))
        if (planar.lengthSquared() < 1.0e-6) return false
        val heading = planar.normalize()

        val landing = findLanding(heading, up, balance.maxJumpBlocks) ?: return false

        val radians = Math.toRadians(balance.jumpAngleDegrees)
        val launch = heading.multiply(Math.cos(radians)).add(up.multiply(Math.sin(radians))).normalize()

        val distance = landing.subtract(pos).length()
        val power = (JUMP_BASE_POWER + distance * JUMP_POWER_PER_BLOCK).coerceAtMost(JUMP_MAX_POWER)

        lunge(launch, power)
        jumpCooldown = balance.jumpCooldownTicks
        world.playSound(null, x, y, z, SoundEvents.ENTITY_SPIDER_STEP, SoundCategory.HOSTILE, 0.8f, 0.5f)
        return true
    }

    /**
     * The furthest spot within jump range that this Reaper could actually stand on.
     *
     * Candidates are offset along the surface normal as well as along it, so a gap that resumes a
     * block higher or lower is still a valid target. Furthest wins, because the point of the jump
     * is to clear the gap rather than to land in it.
     */
    private fun findLanding(heading: Vec3d, up: Vec3d, maxBlocks: Int): Vec3d? {
        var best: Vec3d? = null
        for (step in JUMP_MINIMUM_BLOCKS..maxBlocks) {
            for (lift in JUMP_LIFTS) {
                val candidate = pos
                    .add(heading.multiply(step.toDouble()))
                    .add(up.multiply(lift.toDouble()))
                val box = boundingBox.offset(candidate.subtract(pos))

                if (!world.isSpaceEmpty(box)) continue
                if (SurfaceCling.findSupport(world, box, clingFace) == null) continue
                best = candidate
            }
        }
        return best
    }

    fun tryReaperAttack(target: LivingEntity): Boolean {
        if (attackCooldown > 0 || !target.isAlive || !target.isAttackable) return false

        // The old hunt goal used a centre-distance estimate based on twice the Reaper's width.
        // A greater form therefore spent its cooldown swinging several blocks before its body
        // actually touched the quarry. Test the real hitboxes instead; this works for animals,
        // pets, other factions, and players regardless of their dimensions.
        if (!boundingBox.expand(ATTACK_REACH_PADDING).intersects(target.boundingBox)) return false

        swingHand(Hand.MAIN_HAND)
        val healthBefore = target.health
        val hit = tryAttack(target)
        if (hit && isPermanentCompanion) {
            val damageDealt = (healthBefore - target.health).coerceAtLeast(0.0f)
            heal(ReaperCompanionHealing.lifeSteal(damageDealt))
        }
        attackCooldown = if (hit) ATTACK_COOLDOWN_TICKS else FAILED_ATTACK_RETRY_TICKS
        return hit
    }

    // ---------------------------------------------------------------- Little Anomaly support summon

    fun pettyOwner(): PlayerEntity? {
        val ownerUuid = pettyOwnerUuid ?: return null
        val serverWorld = world as? ServerWorld ?: return null
        return serverWorld.server.playerManager.getPlayer(ownerUuid)
    }

    /** Friendly-fire gate used by both target selection and the movement goal. */
    fun canPettyAttack(candidate: LivingEntity): Boolean {
        val owner = pettyOwner() ?: return false
        if (!candidate.isAlive || candidate === this || candidate === owner ||
            ReaperTargetFilter.isIgnoredEntity(candidate)
        ) return false
        if (candidate is PlayerEntity && ReaperTargetFilter.isTemporarilyRagdolled(candidate)) return false
        if (candidate is PlayerEntity && (candidate.isCreative || candidate.isSpectator || owner.isTeammate(candidate))) {
            return false
        }
        if (candidate is TameableEntity && candidate.ownerUuid == owner.uuid) return false
        if (candidate is Ownable && candidate.owner?.uuid == owner.uuid) return false
        if (candidate is ParadoxReaperEntity && candidate.isPetty && candidate.pettyOwnerUuid == owner.uuid) return false
        return true
    }

    /** Dog-like rescue teleport; cross-dimension travel copies the entity through its NBT. */
    fun rescuePettyToOwner(): Boolean {
        val owner = pettyOwner() ?: return false
        val ownerWorld = owner.world as? ServerWorld ?: return false
        if (ownerWorld !== world) {
            val moved = moveToWorld(ownerWorld) as? ParadoxReaperEntity
            moved?.refreshPositionAndAngles(owner.x + 1.0, owner.y, owner.z + 1.0, owner.yaw, 0.0f)
            return true
        }
        if (squaredDistanceTo(owner) <= 48.0 * 48.0) return false
        refreshPositionAndAngles(owner.x + 1.0, owner.y, owner.z + 1.0, owner.yaw, 0.0f)
        velocity = Vec3d.ZERO
        return true
    }

    private fun tickPettyBehavior() {
        val owner = pettyOwner()
        if (owner == null || !owner.isAlive) {
            if (isPermanentCompanion) {
                target = null
                desiredMove = Vec3d.ZERO
                velocity = Vec3d.ZERO
                dataTracker.set(STATE, ReaperState.DORMANT.ordinal.toByte())
            } else {
                discard()
            }
            return
        }

        // This latch belongs to the Reaper spawned by Little Anomaly's ability, not to the
        // floating Little Anomaly and not to lesser Reapers called by a Greater's screech.
        if (owner.world !== world) {
            pettyRestPlan.reset()
            pettySearchTicks = 0
        } else if (pettyRestPlan.wakeIfOwnerFar(owner.pos, pos)) {
            pettySearchTicks = 0
        }
        val holdsPosition = isPermanentCompanion && companionCommand == ReaperCompanionCommand.STAY
        if (!holdsPosition && rescuePettyToOwner() && isRemoved) return
        if (!isPermanentCompanion && !pettyClaimed) claimPettySlot(owner.uuid)

        if (hasControllingPassenger() || isPermanentCompanion && companionCommand != ReaperCompanionCommand.FOLLOW) {
            // Six seconds of sitting still normally asks an owned Reaper to find a dark roost.
            // Riding must never reinterpret the rider pausing as permission to wander away.
            pettyRestPlan.reset()
            pettySearchTicks = 0
            if (holdsPosition) {
                target = null
                desiredMove = Vec3d.ZERO
                velocity = Vec3d.ZERO
            }
        } else if (pettyRestMode == PettyReaperRestMode.FOLLOWING) {
            pettyRestPlan.observeOwner(owner.pos)
            if (pettyRestMode == PettyReaperRestMode.SEEKING_SHELTER) {
                pettySearchTicks = 0
            }
        }

        if (pettyRestMode == PettyReaperRestMode.SEEKING_SHELTER) {
            target = null
            dataTracker.set(STATE, ReaperState.SETTLING.ordinal.toByte())
            pettySearchTicks++
            if (isPettyRoostReady(owner)) {
                pettyRestPlan.reachedShelter()
                desiredMove = Vec3d.ZERO
                velocity = Vec3d.ZERO
                dataTracker.set(STATE, ReaperState.DORMANT.ordinal.toByte())
            }
            return
        }

        if (pettyRestMode == PettyReaperRestMode.DORMANT) {
            target = null
            desiredMove = Vec3d.ZERO
            velocity = Vec3d.ZERO
            dataTracker.set(STATE, ReaperState.DORMANT.ordinal.toByte())
            return
        }

        if (pettyDecisionTicks-- <= 0) {
            pettyDecisionTicks = 20
            val current = target
            if (current != null && (!canPettyAttack(current) || squaredDistanceTo(current) > 24.0 * 24.0)) {
                target = null
            }

            // Defend the owner or itself first. A peaceful player is never selected merely for
            // standing nearby; a player only becomes valid after attacking one of them.
            val urgent = owner.attacker?.takeIf(::canPettyAttack)
                ?: attacker?.takeIf(::canPettyAttack)
                ?: owner.attacking?.takeIf(::canPettyAttack)
            if (urgent != null) {
                target = urgent
            } else if (target == null && random.nextFloat() < 0.45f) {
                target = world.getEntitiesByClass(
                    HostileEntity::class.java,
                    boundingBox.expand(10.0)
                ) { it !== this && canPettyAttack(it) }
                    .minByOrNull { squaredDistanceTo(it) }
            }

            // It is a deliberately fragile decoy. Hostiles already interested in the owner
            // always switch; idle hostiles usually notice the anomaly first as it scuttles by.
            world.getEntitiesByClass(MobEntity::class.java, boundingBox.expand(10.0)) { mob ->
                mob is HostileEntity && mob !== this && mob.isAlive
            }.forEach { mob ->
                if (mob.target === owner || mob.target == null && random.nextFloat() < 0.70f) {
                    mob.target = this
                }
            }
        }

        val quarry = target?.takeIf { canPettyAttack(it) }
        dataTracker.set(
            STATE,
            (if (quarry != null) ReaperState.PURSUIT else ReaperState.SETTLING).ordinal.toByte()
        )
    }

    /** True only after the crawler has actually wandered away to darkness or a ceiling. */
    fun isPettyRoostReady(owner: PlayerEntity? = pettyOwner()): Boolean {
        if (owner == null || !isPetty || pettyRestMode != PettyReaperRestMode.SEEKING_SHELTER ||
            pettySearchTicks < PETTY_MIN_SEARCH_TICKS ||
            squaredDistanceTo(owner) < PETTY_ROOST_MIN_DISTANCE_SQUARED
        ) return false
        if (clingFace == Direction.DOWN) return true
        return !world.isSkyVisible(blockPos) && world.getLightLevel(blockPos) <= PETTY_DARK_ROOST_LIGHT
    }

    private fun claimPettySlot(ownerUuid: UUID) {
        pettyClaimed = true
        val oldUuid = ACTIVE_PETTIES.put(ownerUuid, uuid) ?: return
        if (oldUuid == uuid) return
        val serverWorld = world as? ServerWorld ?: return
        serverWorld.server.worlds.asSequence()
            .mapNotNull { it.getEntity(oldUuid) as? ParadoxReaperEntity }
            .firstOrNull()
            ?.takeIf { it.isPetty }
            ?.discard()
    }

    // ---------------------------------------------------------------- screeching

    /**
     * Two chances to call for backup: the moment a hunt begins, and the moment the Reaper is
     * badly hurt. Each is a single roll. A roll blocked by living backup is spent rather than
     * retried, which keeps the mechanic to at most two screeches per Reaper per engagement
     * instead of one attempt per tick for as long as the condition holds.
     */
    private fun tickSummoning() {
        if (isLesser) return
        val balance = MystcraftConfig.current.paradoxReaper
        if (!balance.canSummonBackup) return

        if (!rolledOnEngage && getTarget()?.let(::isPriorityQuarry) == true) {
            rolledOnEngage = true
            attemptScreech(balance.summonChance)
        }

        if (!rolledOnWounded && health <= maxHealth * WOUNDED_FRACTION) {
            rolledOnWounded = true
            attemptScreech(balance.summonChance)
        }
    }

    private fun attemptScreech(chance: Float) {
        if (livingSummonCount() > 0) return
        if (random.nextFloat() >= chance) return
        screechForBackup()
    }

    /** Lesser forms already called up that are still alive. */
    private fun livingSummonCount(): Int {
        val serverWorld = world as? ServerWorld ?: return 0
        summonedIds.removeAll { id ->
            val entity = serverWorld.getEntity(id) as? ParadoxReaperEntity
            entity == null || !entity.isAlive || !entity.isSummonedBackup || entity.summonedByUuid != uuid
        }
        return summonedIds.size
    }

    private fun screechForBackup() {
        val serverWorld = world as? ServerWorld ?: return
        val balance = MystcraftConfig.current.paradoxReaper

        world.playSound(
            null, x, y, z, SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 1.6f, 1.45f
        )

        val count = balance.summonMinimum +
            random.nextInt((balance.summonMaximum - balance.summonMinimum + 1).coerceAtLeast(1))

        repeat(count) {
            val spot = findSummonSpot() ?: return@repeat
            val lesser = ModEntities.PARADOX_REAPER.create(world) ?: return@repeat
            lesser.applyVariant(true)
            lesser.markSummonedBackup(uuid)
            lesser.refreshPositionAndAngles(spot.x + 0.5, spot.y.toDouble(), spot.z + 0.5, random.nextFloat() * 360f, 0f)
            lesser.initialize(serverWorld, world.getLocalDifficulty(spot), SpawnReason.MOB_SUMMONED, null, null)
            lesser.inheritAlert(this.target, investigationTarget ?: blockPos)
            serverWorld.spawnEntity(lesser)
            summonedIds.add(lesser.uuid)
            spawnPortalBurst(serverWorld, spot)
        }
    }

    /**
     * The rift the lesser form drops out of. The renderer draws the portal itself from the
     * emissive nebula texture for the first few ticks of the summon's life; these particles are
     * the server-side half, so the arrival is visible to everyone and from any angle.
     */
    private fun spawnPortalBurst(serverWorld: ServerWorld, spot: BlockPos) {
        val cx = spot.x + 0.5
        val cy = spot.y + 0.45
        val cz = spot.z + 0.5
        serverWorld.spawnParticles(ParticleTypes.REVERSE_PORTAL, cx, cy, cz, 60, 0.35, 0.35, 0.35, 0.35)
        serverWorld.spawnParticles(ParticleTypes.END_ROD, cx, cy, cz, 12, 0.25, 0.25, 0.25, 0.04)
        serverWorld.playSound(
            null, cx, cy, cz, SoundEvents.BLOCK_PORTAL_TRIGGER, SoundCategory.HOSTILE, 0.7f, 1.7f
        )
    }

    /**
     * A rift opens in open air a short drop above solid ground, so the lesser form plops out and
     * falls rather than materialising already standing. Requires headroom for the body plus the
     * drop itself, which is what stops summons appearing inside a one-block crawlspace.
     */
    private fun findSummonSpot(): BlockPos? {
        repeat(24) {
            val probe = blockPos.add(
                random.nextInt(9) - 4,
                random.nextInt(5) - 1,
                random.nextInt(9) - 4
            )
            val floor = findFloorBelow(probe) ?: return@repeat

            val drop = SUMMON_DROP_MINIMUM + random.nextInt(SUMMON_DROP_SPREAD)
            val spot = floor.up(drop)

            // Every block from the floor to the rift must be clear, or the summon would spawn
            // inside geometry and be shoved out sideways by collision resolution.
            for (step in 1..drop) {
                if (!hasRoomForLesser(floor.up(step))) return@repeat
            }
            return spot
        }
        return null
    }

    /** First solid surface at or below [from], within a short scan; null if it is a long drop. */
    private fun findFloorBelow(from: BlockPos): BlockPos? {
        var cursor = from
        repeat(SUMMON_FLOOR_SCAN) {
            val below = cursor.down()
            if (world.getBlockState(below).isSolidBlock(world, below)) return cursor
            cursor = below
        }
        return null
    }

    private fun hasRoomForLesser(pos: BlockPos): Boolean {
        return hasRoom(pos, LESSER_DIMENSIONS)
    }

    private fun hasRoom(pos: BlockPos, dimensions: EntityDimensions): Boolean {
        val half = dimensions.width.toDouble() / 2.0
        val fitting = Box(
            pos.x + 0.5 - half, pos.y.toDouble(), pos.z + 0.5 - half,
            pos.x + 0.5 + half, pos.y + dimensions.height.toDouble(), pos.z + 0.5 + half
        )
        return world.isSpaceEmpty(fitting)
    }

    /** Starts summoned backup in the same encounter instead of leaving it dormant under its rift. */
    private fun inheritAlert(quarry: LivingEntity?, around: BlockPos) {
        investigationTarget = quarry?.blockPos ?: around
        if (quarry != null && isPriorityQuarry(quarry)) {
            target = quarry
            sawTargetLastScan = true
            awareness.onSighted()
        } else {
            awareness.onDisturbed()
        }
        dataTracker.set(STATE, awareness.state.ordinal.toByte())
    }

    // ---------------------------------------------------------------- traits

    /** Mount a bound companion, or bind a badly weakened wild Reaper with Echo Shards. */
    override fun interactMob(player: PlayerEntity, hand: Hand): ActionResult {
        val stack = player.getStackInHand(hand)

        if (isPermanentCompanion && pettyOwnerUuid == player.uuid && stack.isOf(Items.AMETHYST_SHARD)) {
            if (health >= maxHealth) return ActionResult.PASS
            if (world.isClient) return ActionResult.SUCCESS
            if (!player.abilities.creativeMode) stack.decrement(1)
            val before = health
            heal(ReaperCompanionHealing.amethystHeal(maxHealth))
            val restored = health - before
            world.playSound(
                null, blockPos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                SoundCategory.PLAYERS, 0.8f, 1.25f
            )
            player.sendMessage(
                Text.translatable("message.mystcraft-reforged.reaper_healed", restored.toInt()), true
            )
            return ActionResult.CONSUME
        }

        if (isPermanentCompanion && pettyOwnerUuid == player.uuid && player.isSneaking && stack.isEmpty) {
            if (world.isClient) return ActionResult.SUCCESS
            val next = companionCommand.next()
            dataTracker.set(COMPANION_COMMAND, next.ordinal.toByte())
            pettyRestPlan.reset()
            pettySearchTicks = 0
            target = null
            desiredMove = Vec3d.ZERO
            player.sendMessage(
                Text.translatable("message.mystcraft-reforged.reaper_command.${next.name.lowercase()}"), true
            )
            return ActionResult.CONSUME
        }

        if (isPermanentCompanion && !player.isSneaking) {
            if (world.isClient) return ActionResult.SUCCESS
            if (!ReaperMounting.canOwnerMount(
                    permanentCompanion = isPermanentCompanion,
                    ownerMatches = pettyOwnerUuid == player.uuid,
                    occupied = hasPassengers()
                )
            ) return ActionResult.PASS
            return if (player.startRiding(this)) ActionResult.CONSUME else ActionResult.PASS
        }

        if (!stack.isOf(Items.ECHO_SHARD) || !ReaperTaming.canBind(isLesser, isPetty, health, maxHealth)) {
            return super.interactMob(player, hand)
        }

        val requiredShards = ReaperTaming.requiredEchoShards(isLesser)
        if (!player.abilities.creativeMode && stack.count < requiredShards) {
            if (!world.isClient) {
                player.sendMessage(
                    Text.translatable("message.mystcraft-reforged.reaper_binding_cost", requiredShards), true
                )
            }
            return ActionResult.FAIL
        }

        if (world.isClient) return ActionResult.SUCCESS

        if (!player.abilities.creativeMode) stack.decrement(requiredShards)
        applyPermanentCompanion(player.uuid)
        (world as? ServerWorld)?.spawnParticles(
            ParticleTypes.REVERSE_PORTAL, x, y + height * 0.5, z, 48, 0.45, 0.35, 0.45, 0.08
        )
        world.playSound(
            null, blockPos, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE,
            SoundCategory.PLAYERS, 0.9f, 1.65f
        )
        player.sendMessage(Text.translatable("message.mystcraft-reforged.reaper_bound"), true)
        return ActionResult.CONSUME
    }

    // ---------------------------------------------------------------- rider jumping

    /**
     * A bound Reaper is a jumping mount, so the rider gets the vanilla charge bar and the same
     * hold-and-release the horse uses.
     *
     * Riders previously had no jump at all, which removed the one manoeuvre this creature's whole
     * design is built around: leaving one surface for another. On foot it crosses gaps, takes
     * walls, and drops off ceilings under its own AI, and a rider could do none of it — including
     * getting themselves out of the inside corner that used to trap them.
     */
    override fun canJump(): Boolean = isPermanentCompanion && controllingPassenger != null

    /**
     * Called on the client as the rider releases the key, before the packet goes out.
     *
     * That ordering is the whole reason this works. A mount with a player aboard is simulated by
     * that player's own client — {@code isLogicalSideForUpdatingMovement} defers to
     * {@code isMainPlayer}, which no server ever satisfies — so a launch applied server-side is
     * simply overwritten by the position the client reports back. Recording the charge here and
     * spending it inside [travel] puts the impulse on whichever side is really moving the body.
     */
    override fun setJumpStrength(strength: Int) {
        mountJumpStrength = strength.coerceIn(0, 100)
        if (mountJumpStrength > 0) jumpRequested = true
    }

    override fun stopJumping() {
        mountJumpStrength = 0
        jumpRequested = false
    }

    /**
     * Launches off whatever the creature is standing on, leaning into the rider's heading.
     *
     * The arc is built around the surface normal rather than world up, so the same key throws the
     * creature off a floor, out from a wall, or down from a ceiling, and the airborne reach then
     * catches whatever it arrives at. [height] is the charge vanilla accumulated while the key was
     * held; it is authoritative here because the client already scaled it by the rider's input.
     */
    /**
     * The server's half of the same release, arriving by packet.
     *
     * It records the identical charge so both sides launch the same way, and it is where the
     * sound is played, since only the server can broadcast one to everybody nearby.
     */
    override fun startJumping(height: Int) {
        if (!canJump()) return
        mountJumpStrength = height.coerceIn(0, 100)
        jumpRequested = true
        if (!world.isClient) {
            world.playSound(
                null, x, y, z, SoundEvents.ENTITY_SPIDER_STEP, SoundCategory.PLAYERS, 0.9f, 0.5f
            )
        }
    }

    /**
     * Turns a stored charge into an actual launch, from inside the movement pipeline.
     *
     * The arc is built on the surface normal rather than world up, so one key throws the creature
     * off a floor, out from a wall, or down from a ceiling without any of them being special
     * cases. With no steering input it is a straight push clear of the surface; with input it
     * tips toward where the rider is pointing, which is what reaches a facing wall or a far ledge.
     */
    private fun spendMountJump() {
        jumpRequested = false
        val charge = mountJumpStrength.coerceIn(0, 100) / 100.0
        mountJumpStrength = 0
        if (clingFace == null) return

        val power = MOUNT_JUMP_MIN_POWER + charge * (MOUNT_JUMP_MAX_POWER - MOUNT_JUMP_MIN_POWER)
        val up = clingNormal()
        val steer = mountedSteering(up).direction
        val lean = if (steer.lengthSquared() > 1.0e-6) {
            SurfaceCling.projectOntoPlane(steer, up, Vec3d.ZERO)
        } else {
            Vec3d.ZERO
        }

        val launch = if (lean.lengthSquared() > 1.0e-6) {
            up.add(lean.multiply(MOUNT_JUMP_LEAN)).normalize()
        } else {
            up
        }

        // The direction already carries its own normal component, so no extra bias is added.
        lunge(launch, power, upBias = 0.0)
    }

    override fun canAddPassenger(passenger: Entity): Boolean =
        passenger is PlayerEntity && ReaperMounting.canOwnerMount(
            permanentCompanion = isPermanentCompanion,
            ownerMatches = world.isClient || pettyOwnerUuid == passenger.uuid,
            occupied = hasPassengers()
        )

    override fun getControllingPassenger(): LivingEntity? =
        if (isPermanentCompanion) firstPassenger as? LivingEntity else null

    /**
     * Seats the rider on the body that was drawn, not on the hitbox origin.
     *
     * The rendered shell rides a ride height solved from where the legs actually landed, and that
     * height moves as the creature crosses uneven ground. A seat derived from the hitbox is a
     * fixed distance from a point the body is not at, so the rider hangs above the shell and
     * bobs independently of it. The drawn pose is only known on the client, which is exactly
     * where it matters, since this is a purely visual placement; the server keeps the analytic
     * approximation so ride logic and dismounting stay deterministic.
     */
    override fun updatePassengerPosition(passenger: Entity, positionUpdater: Entity.PositionUpdater) {
        if (!hasPassenger(passenger)) return
        val seat = visualSeat() ?: pos.add(clingNormal().multiply(ReaperMounting.seatOffset(height)))
        positionUpdater.accept(passenger, seat.x, seat.y, seat.z)
    }

    /** The drawn seat, or null when no pose has been rendered yet or the rider opted out. */
    private fun visualSeat(): Vec3d? {
        if (!world.isClient || !mountVisualPoseReady) return null
        if (!ReaperMountSeating.enabled()) return null
        return ReaperMounting.seatOnBody(mountVisualBody, mountVisualUp, if (isLesser) 0.55 else 1.0)
    }

    override fun updatePassengerForDismount(passenger: LivingEntity): Vec3d {
        val up = clingNormal()
        val outward = width * 0.5 + passenger.width * 0.5 + 0.9
        val forward = SurfaceCling.projectOntoPlane(surfaceHeading, up, Vec3d(0.0, 0.0, 1.0))
        return pos.add(up.multiply(outward)).add(forward.multiply(1.25))
    }

    // Reapers travel by dropping off ceilings; fall damage would kill them on their own commute.
    override fun handleFallDamage(fallDistance: Float, damageMultiplier: Float, damageSource: DamageSource?): Boolean = false

    override fun getSafeFallDistance(): Int = 512

    override fun isClimbing(): Boolean = clingFace != null && clingFace != Direction.UP

    override fun getName(): Text =
        when {
            isPermanentCompanion -> Text.translatable("entity.mystcraft-reforged.bound_reaper")
            isPetty -> Text.translatable("entity.mystcraft-reforged.little_anomaly")
            else -> super.getName()
        }

    override fun getAmbientSound(): SoundEvent? =
        if (state == ReaperState.DORMANT) null else SoundEvents.ENTITY_SPIDER_AMBIENT

    override fun getHurtSound(source: DamageSource): SoundEvent = SoundEvents.ENTITY_SPIDER_HURT

    override fun getDeathSound(): SoundEvent = SoundEvents.ENTITY_SPIDER_DEATH

    // Pitched under the spider baseline, and higher for the smaller body.
    override fun getSoundPitch(): Float =
        (when {
            isPetty -> 0.82f
            isLesser -> 0.70f
            else -> 0.38f
        }) + random.nextFloat() * 0.08f

    override fun playStepSound(pos: BlockPos, state: BlockState) {
        if (this.state == ReaperState.DORMANT) return
        playSound(SoundEvents.ENTITY_SPIDER_STEP, 0.12f, if (isLesser) 0.75f else 0.45f)
    }

    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        super.writeCustomDataToNbt(nbt)
        nbt.putByte("ClingFace", dataTracker.get(CLING_FACE))
        nbt.putBoolean("Lesser", isLesser)
        nbt.putInt("HealthModelVersion", HEALTH_MODEL_VERSION)
        nbt.putBoolean("Petty", isPetty)
        pettyOwnerUuid?.let { nbt.putUuid("PettyOwner", it) }
        nbt.putBoolean("PermanentCompanion", isPermanentCompanion)
        nbt.putByte("CompanionCommand", companionCommand.ordinal.toByte())
        nbt.putBoolean("Drone", isDrone)
        nbt.putByte("AwareState", awareness.state.ordinal.toByte())
        nbt.putInt("TicksSinceSeen", awareness.ticksSinceSeen)
        nbt.putInt("SettleTicks", awareness.settleTicksRemaining)
        investigationTarget?.let { nbt.put("Investigating", NbtHelper.fromBlockPos(it)) }
        nbt.putBoolean("RolledOnEngage", rolledOnEngage)
        nbt.putBoolean("RolledOnWounded", rolledOnWounded)
        summonedByUuid?.let { nbt.putUuid("SummonedBy", it) }
        if (isPetty) {
            nbt.putString("PettyRestMode", pettyRestPlan.mode.name)
        }

        val list = NbtList()
        summonedIds.forEach { list.add(NbtHelper.fromUuid(it)) }
        nbt.put("Summoned", list)
    }

    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        super.readCustomDataFromNbt(nbt)
        if (nbt.contains("ClingFace")) dataTracker.set(CLING_FACE, nbt.getByte("ClingFace"))
        // Variant first would clobber the health super just restored, so keep and reapply it.
        var restoredHealth = health
        val petty = nbt.getBoolean("Petty") && nbt.containsUuid("PettyOwner")
        val permanentCompanion = petty && nbt.getBoolean("PermanentCompanion")
        dataTracker.set(BOUND, permanentCompanion)
        val savedCommand = ReaperCompanionCommand.entries.getOrNull(nbt.getByte("CompanionCommand").toInt())
            ?: ReaperCompanionCommand.FOLLOW
        dataTracker.set(COMPANION_COMMAND, savedCommand.ordinal.toByte())
        if (petty) {
            applyPetty(nbt.getUuid("PettyOwner"), resetHealth = false)
            if (permanentCompanion) {
                if (!nbt.getBoolean("Lesser")) applyVariant(lesser = false, resetHealth = false)
                applyPermanentCompanionStats(resetHealth = false)
            }
        } else {
            applyVariant(nbt.getBoolean("Lesser"), resetHealth = false)
        }
        // Preserve the health percentage across both historical Greater health models.
        if (!petty && !isLesser) {
            restoredHealth = when (nbt.getInt("HealthModelVersion")) {
                in Int.MIN_VALUE..1 -> restoredHealth / 30.0f * GREATER_MAX_HEALTH.toFloat()
                2 -> restoredHealth / 375.0f * GREATER_MAX_HEALTH.toFloat()
                else -> restoredHealth
            }
        }
        health = restoredHealth.coerceAtMost(maxHealth)
        isDrone = nbt.getBoolean("Drone")
        rolledOnEngage = nbt.getBoolean("RolledOnEngage")
        rolledOnWounded = nbt.getBoolean("RolledOnWounded")
        summonedByUuid = if (nbt.containsUuid("SummonedBy")) nbt.getUuid("SummonedBy") else null
        if (petty) {
            val restored = runCatching {
                PettyReaperRestMode.valueOf(nbt.getString("PettyRestMode"))
            }.getOrDefault(PettyReaperRestMode.FOLLOWING)
            pettyRestPlan.restore(restored)
            pettySearchTicks = if (restored == PettyReaperRestMode.SEEKING_SHELTER) PETTY_MIN_SEARCH_TICKS else 0
        }

        summonedIds.clear()
        nbt.getList("Summoned", NbtElement.INT_ARRAY_TYPE.toInt()).forEach { element ->
            summonedIds.add(NbtHelper.toUuid(element))
        }

        val stored = ReaperState.entries.getOrNull(nbt.getByte("AwareState").toInt()) ?: ReaperState.DORMANT
        awareness.restore(stored, nbt.getInt("TicksSinceSeen"), nbt.getInt("SettleTicks"))
        dataTracker.set(STATE, awareness.state.ordinal.toByte())
        investigationTarget =
            if (nbt.contains("Investigating")) NbtHelper.toBlockPos(nbt.getCompound("Investigating")) else null
    }

    /** Being hit is unambiguous: the Reaper knows exactly where that came from. */
    override fun damage(source: DamageSource, amount: Float): Boolean {
        if (!world.isClient && !isDead) {
            val attacker = source.attacker
            if (attacker is ServerPlayerEntity) ReaperArrivalGrace.revoke(attacker)
            if (isPetty) {
                (attacker as? LivingEntity)?.takeIf(::canPettyAttack)?.let { target = it }
            } else if (attacker !is PlayerEntity || canHunt(attacker)) {
                (attacker as? LivingEntity)?.takeIf { it !== this && it !is ParadoxReaperEntity }
                    ?.let { pursueDisturber(it, it.blockPos) }
                disturb((attacker ?: this).blockPos)
                alertNeighbours(blockPos)
            }
        }
        val damaged = super.damage(source, amount)
        if (damaged && isPermanentCompanion) {
            companionRegenDelay = ReaperCompanionHealing.PASSIVE_DELAY_AFTER_DAMAGE_TICKS
        }
        return damaged
    }

    override fun remove(reason: RemovalReason) {
        val ownerUuid = pettyOwnerUuid
        super.remove(reason)
        if (!isPermanentCompanion && ownerUuid != null && ACTIVE_PETTIES[ownerUuid] == uuid) {
            ACTIVE_PETTIES.remove(ownerUuid)
        }
    }
}
