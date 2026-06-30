package mystcraft.flood.entity

import mystcraft.flood.item.AgeBookIntegrity
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.damage.DamageTypes
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.listener.ClientPlayPacketListener
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket
import net.minecraft.sound.SoundEvents
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

class DescriptiveBookEntity(
    entityType: EntityType<out DescriptiveBookEntity>,
    world: World
) : Entity(entityType, world) {

    companion object {
        private val STORED_BOOK: TrackedData<ItemStack> =
            DataTracker.registerData(DescriptiveBookEntity::class.java, TrackedDataHandlerRegistry.ITEM_STACK)
        private val PICKUP_DELAY: TrackedData<Int> =
            DataTracker.registerData(DescriptiveBookEntity::class.java, TrackedDataHandlerRegistry.INTEGER)
        private val HEALTH: TrackedData<Int> =
            DataTracker.registerData(DescriptiveBookEntity::class.java, TrackedDataHandlerRegistry.INTEGER)
        private val HURT_TIMER: TrackedData<Int> =
            DataTracker.registerData(DescriptiveBookEntity::class.java, TrackedDataHandlerRegistry.INTEGER)

        // Total survival hits before the anchor releases its stored stack.
        const val MAX_HEALTH = 16
        // Visual flash duration (ticks) after each hit.
        const val HURT_FLASH_TICKS = 8
        private const val GROUND_SCAN_RANGE = 32
        private const val RELEASED_ITEM_PICKUP_DELAY = 30
    }

    constructor(world: World, pos: Vec3d, stack: ItemStack, pickupDelayTicks: Int = 0) : this(ModEntities.DESCRIPTIVE_BOOK_ANCHOR, world) {
        val cleanStack = stack.copy()
        AgeBookIntegrity.clearAnchorReleaseMarker(cleanStack)
        setStoredBook(cleanStack)
        applyPickupDelay(pickupDelayTicks)
        refreshPositionAndAngles(pos.x, pos.y, pos.z, 0.0f, 0.0f)
        settleOnGround()
    }

    override fun initDataTracker() {
        dataTracker.startTracking(STORED_BOOK, ItemStack.EMPTY)
        dataTracker.startTracking(PICKUP_DELAY, 0)
        dataTracker.startTracking(HEALTH, MAX_HEALTH)
        dataTracker.startTracking(HURT_TIMER, 0)
    }

    override fun tick() {
        super.tick()
        velocity = Vec3d.ZERO
        setNoGravity(true)
        prevX = x
        prevY = y
        prevZ = z
        if (pickupDelay > 0) {
            pickupDelay -= 1
        }
        val flash = dataTracker.get(HURT_TIMER)
        if (flash > 0) {
            dataTracker.set(HURT_TIMER, flash - 1)
        }
        if (!hasVehicle()) {
            settleOnGround()
        }
    }

    override fun isAttackable(): Boolean = true

    override fun canHit(): Boolean = true

    override fun isCollidable(): Boolean = true

    override fun isPushable(): Boolean = false

    override fun isInvulnerableTo(source: DamageSource): Boolean {
        return isWorldHazard(source) ||
            super.isInvulnerableTo(source)
    }

    override fun damage(source: DamageSource, amount: Float): Boolean {
        if (world.isClient || isRemoved || isInvulnerableTo(source)) return false

        val attacker = source.attacker
        if (attacker is PlayerEntity && attacker.isCreative) {
            releaseStoredBook()
            playSound(SoundEvents.ENTITY_ITEM_FRAME_BREAK, 0.9f, 0.9f + random.nextFloat() * 0.2f)
            discard()
            return true
        }
        if (attacker !is PlayerEntity) return false

        val remaining = (dataTracker.get(HEALTH) - 1).coerceAtLeast(0)
        dataTracker.set(HEALTH, remaining)
        dataTracker.set(HURT_TIMER, HURT_FLASH_TICKS)
        playSound(SoundEvents.ENTITY_ITEM_FRAME_ADD_ITEM, 0.6f, 0.7f + random.nextFloat() * 0.3f)

        if (remaining <= 0) {
            releaseStoredBook()
            playSound(SoundEvents.ENTITY_ITEM_FRAME_BREAK, 0.9f, 0.9f + random.nextFloat() * 0.2f)
            discard()
        }
        return true
    }

    /** Returns 0..1 fade representing the recent-hit visual flash. Client-side use. */
    fun getHurtFlash(): Float {
        val t = dataTracker.get(HURT_TIMER)
        if (t <= 0) return 0f
        return (t.toFloat() / HURT_FLASH_TICKS).coerceIn(0f, 1f)
    }

    override fun interact(player: PlayerEntity, hand: Hand): ActionResult {
        if (world.isClient) return ActionResult.SUCCESS
        return if (reclaimBy(player)) ActionResult.CONSUME else ActionResult.PASS
    }

    override fun onPlayerCollision(player: PlayerEntity) {
        if (world.isClient) return
        reclaimBy(player)
    }

    private fun reclaimBy(player: PlayerEntity): Boolean {
        if (player.isSpectator) return false
        if (pickupDelay > 0) return false
        val storedBook = getStoredBook()
        if (storedBook.isEmpty) {
            discard()
            return false
        }
        val retrieved = storedBook.copy()
        setStoredBook(ItemStack.EMPTY)

        if (!player.giveItemStack(retrieved)) {
            val dropped = ItemEntity(world, player.x, player.y + 0.25, player.z, AgeBookIntegrity.markReleasedFromAnchor(retrieved, world))
            dropped.setPickupDelay(0)
            world.spawnEntity(dropped)
        }

        playSound(SoundEvents.ITEM_BOOK_PAGE_TURN, 0.8f, 0.85f + random.nextFloat() * 0.25f)
        discard()
        return true
    }

    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        val storedBook = getStoredBook()
        if (!storedBook.isEmpty) {
            val itemNbt = NbtCompound()
            storedBook.writeNbt(itemNbt)
            nbt.put("StoredBook", itemNbt)
        }
        if (pickupDelay > 0) {
            nbt.putInt("PickupDelay", pickupDelay)
        }
        nbt.putInt("Health", dataTracker.get(HEALTH))
    }

    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        if (nbt.contains("StoredBook")) {
            setStoredBook(ItemStack.fromNbt(nbt.getCompound("StoredBook")))
        }
        if (nbt.contains("PickupDelay")) {
            applyPickupDelay(nbt.getInt("PickupDelay"))
        }
        val saved = if (nbt.contains("Health")) nbt.getInt("Health") else MAX_HEALTH
        dataTracker.set(HEALTH, saved.coerceIn(1, MAX_HEALTH))
    }

    override fun createSpawnPacket(): Packet<ClientPlayPacketListener> = EntitySpawnS2CPacket(this)

    fun getStoredBook(): ItemStack = dataTracker.get(STORED_BOOK)

    fun setStoredBook(stack: ItemStack) {
        dataTracker.set(STORED_BOOK, if (stack.isEmpty) ItemStack.EMPTY else stack.copyWithCount(1))
    }

    private var pickupDelay: Int
        get() = dataTracker.get(PICKUP_DELAY)
        set(value) {
            dataTracker.set(PICKUP_DELAY, value.coerceAtLeast(0))
        }

    fun applyPickupDelay(ticks: Int) {
        pickupDelay = ticks
    }

    private fun releaseStoredBook() {
        val storedBook = getStoredBook()
        if (storedBook.isEmpty || world.isClient) return

        val dropped = ItemEntity(world, x, y + 0.1, z, AgeBookIntegrity.markReleasedFromAnchor(storedBook, world))
        dropped.velocity = Vec3d.ZERO
        dropped.setPickupDelay(RELEASED_ITEM_PICKUP_DELAY)
        world.spawnEntity(dropped)
        setStoredBook(ItemStack.EMPTY)
    }

    private fun settleOnGround() {
        val settledY = findGroundY() ?: return
        setPosition(x, settledY, z)
    }

    private fun findGroundY(): Double? {
        val blockX = MathHelper.floor(x)
        val blockZ = MathHelper.floor(z)
        val startY = MathHelper.floor(y + 0.5)
        val mutable = BlockPos.Mutable(blockX, startY, blockZ)
        for (offset in 0..GROUND_SCAN_RANGE) {
            mutable.set(blockX, startY - offset, blockZ)
            val shape = world.getBlockState(mutable).getCollisionShape(world, mutable)
            if (!shape.isEmpty) {
                return mutable.y + shape.getMax(Direction.Axis.Y) + 0.02
            }
        }
        return null
    }

    private fun isWorldHazard(source: DamageSource): Boolean {
        return source.isOf(DamageTypes.IN_FIRE) ||
            source.isOf(DamageTypes.ON_FIRE) ||
            source.isOf(DamageTypes.LAVA) ||
            source.isOf(DamageTypes.HOT_FLOOR) ||
            source.isOf(DamageTypes.LIGHTNING_BOLT) ||
            source.isOf(DamageTypes.CACTUS) ||
            source.isOf(DamageTypes.SWEET_BERRY_BUSH) ||
            source.isOf(DamageTypes.DROWN) ||
            source.isOf(DamageTypes.FALL) ||
            source.isOf(DamageTypes.FLY_INTO_WALL) ||
            source.isOf(DamageTypes.FREEZE) ||
            source.isOf(DamageTypes.FALLING_BLOCK) ||
            source.isOf(DamageTypes.FALLING_ANVIL) ||
            source.isOf(DamageTypes.FALLING_STALACTITE) ||
            source.isOf(DamageTypes.EXPLOSION) ||
            source.isOf(DamageTypes.PLAYER_EXPLOSION)
    }
}
