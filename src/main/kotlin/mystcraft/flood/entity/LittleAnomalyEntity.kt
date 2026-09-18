package mystcraft.flood.entity

import net.minecraft.entity.EntityType
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.MovementType
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.passive.PassiveEntity
import net.minecraft.entity.passive.TameableEntity
import net.minecraft.entity.ai.goal.AttackWithOwnerGoal
import net.minecraft.entity.ai.goal.LookAroundGoal
import net.minecraft.entity.ai.goal.LookAtEntityGoal
import net.minecraft.entity.ai.goal.SitGoal
import net.minecraft.entity.ai.goal.TrackOwnerAttackerGoal
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import net.minecraft.world.EntityView

/**
 * The Familiar Friends summon is a real tame creature, not a small [HostileEntity].
 *
 * Keeping this as its own non-monster entity type is important: iron golems, faction mods,
 * minimaps, and many other mods classify an entity by its Java base class or spawn group before
 * they ever ask its goals what it intends to attack. A "friendly" flag on a Paradox Reaper could
 * therefore never make the old summon consistently friendly to the rest of the game.
 */
class LittleAnomalyEntity(type: EntityType<out TameableEntity>, world: World) : TameableEntity(type, world) {

    private var supportPulseTicks = 0
    private var attackCooldown = 0
    private var flightDestination: Vec3d? = null

    init {
        stepHeight = 1.0f
        setNoGravity(true)
    }

    override fun initGoals() {
        goalSelector.add(1, SitGoal(this))
        goalSelector.add(7, LookAtEntityGoal(this, LivingEntity::class.java, 7.0f))
        goalSelector.add(8, LookAroundGoal(this))

        targetSelector.add(1, TrackOwnerAttackerGoal(this))
        targetSelector.add(2, AttackWithOwnerGoal(this))
        // There is deliberately no broad hostile target goal. It helps when a fight reaches its
        // owner, but does not continually run away to start fights or pace around while idle.
    }

    override fun tick() {
        // Familiar Friends used this entity id for the ability summon before the cosmetic
        // familiar and its spawned crawler were correctly separated. The real floating familiar
        // is familiar_friends:little_anomaly_companion; migrate any saved mistaken summon into
        // the owner-bound petty Reaper it was always meant to be.
        if (!world.isClient && migrateLegacyAbilitySpawn()) return

        if (!isSitting) {
            owner?.let { flightDestination = FamiliarOrbit.anchor(it.pos, age, uuid.leastSignificantBits) }
        }
        super.tick()
        setNoGravity(true)
        fallDistance = 0.0f
        if (attackCooldown > 0) attackCooldown--

        val owner = owner ?: return
        if (!owner.isAlive) return

        if (isSitting) {
            flightDestination = null
            return
        }

        val quarry = target?.takeIf { it.isAlive }
        flightDestination = if (quarry != null) {
            quarry.pos.add(0.0, quarry.height * 0.55, 0.0)
        } else {
            FamiliarOrbit.anchor(owner.pos, age, uuid.leastSignificantBits)
        }

        if (!world.isClient && quarry != null && attackCooldown == 0 &&
            squaredDistanceTo(quarry) <= ATTACK_REACH_SQUARED
        ) {
            tryAttack(quarry)
            attackCooldown = ATTACK_COOLDOWN_TICKS
        }

        if (world.isClient) return

        if (supportPulseTicks-- <= 0) {
            supportPulseTicks = 20
            // The decoy only intercepts enemies already fighting the owner. The old implementation
            // pulled idle monsters from ten blocks away, which made every faction appear to hate it.
            world.getEntitiesByClass(MobEntity::class.java, boundingBox.expand(10.0)) { mob ->
                mob is HostileEntity && mob.isAlive && mob.target === owner
            }.forEach { it.target = this }
        }

        if (squaredDistanceTo(owner) > RESCUE_DISTANCE_SQUARED) {
            val anchor = FamiliarOrbit.anchor(owner.pos, age, uuid.leastSignificantBits)
            refreshPositionAndAngles(anchor.x, anchor.y, anchor.z, owner.yaw, 0.0f)
            velocity = Vec3d.ZERO
        }
    }

    private fun migrateLegacyAbilitySpawn(): Boolean {
        val serverWorld = world as? ServerWorld ?: return false
        val owner = owner ?: return false
        val replacement = ModEntities.PARADOX_REAPER.create(serverWorld) ?: return false
        replacement.applyPetty(owner.uuid)
        replacement.refreshPositionAndAngles(x, y, z, yaw, pitch)
        replacement.velocity = Vec3d.ZERO
        if (!serverWorld.spawnEntity(replacement)) return false
        discard()
        return true
    }

    /** Little Anomaly flight is independent of Reaper surface locomotion. */
    override fun travel(movementInput: Vec3d) {
        val offset = flightDestination?.subtract(pos)
        val wanted = if (offset != null && offset.lengthSquared() > 1.0e-6) {
            val speed = minOf(MAX_FLIGHT_SPEED, offset.length() * 0.16)
            offset.normalize().multiply(speed)
        } else Vec3d.ZERO
        velocity = if (wanted.lengthSquared() > 1.0e-8) {
            velocity.lerp(wanted, FLIGHT_ACCELERATION)
        } else velocity.multiply(IDLE_DAMPING)
        move(MovementType.SELF, velocity)
    }

    override fun interactMob(player: PlayerEntity, hand: Hand): ActionResult {
        if (isOwner(player) && !world.isClient) {
            isSitting = !isSitting
            jumping = false
            navigation.stop()
            target = null
            return ActionResult.SUCCESS
        }
        return super.interactMob(player, hand)
    }

    override fun damage(source: DamageSource, amount: Float): Boolean {
        val attacker = source.attacker
        if (attacker is PlayerEntity && (isOwner(attacker) || isTeammate(attacker))) return false
        return super.damage(source, amount)
    }

    override fun tryAttack(target: Entity): Boolean {
        val hit = super.tryAttack(target)
        if (hit && !world.isClient && target is LivingEntity) {
            target.addStatusEffect(StatusEffectInstance(StatusEffects.POISON, POISON_TICKS, 0))
        }
        return hit
    }

    override fun createChild(world: ServerWorld, entity: PassiveEntity): PassiveEntity? = null

    // Yarn 1.20.1 leaves this TameableEntity bridge unmapped. Kotlin does not inherit the Java
    // bridge automatically, so provide the EntityView used by tame-pet target predicates.
    override fun method_48926(): EntityView = world

    companion object {
        private const val RESCUE_DISTANCE_SQUARED = 18.0 * 18.0
        private const val POISON_TICKS = 80
        private const val MAX_FLIGHT_SPEED = 0.18
        private const val FLIGHT_ACCELERATION = 0.18
        private const val IDLE_DAMPING = 0.62
        private const val ATTACK_REACH_SQUARED = 1.6 * 1.6
        private const val ATTACK_COOLDOWN_TICKS = 16

        fun createAttributes(): DefaultAttributeContainer.Builder = MobEntity.createMobAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 8.0)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.30)
            .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 2.0)
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 20.0)
    }
}
