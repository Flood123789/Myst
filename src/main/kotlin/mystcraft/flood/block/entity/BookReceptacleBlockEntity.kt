package mystcraft.flood.block.entity

import mystcraft.flood.block.ModBlocks
import mystcraft.flood.block.CrystalPortalBlock
import mystcraft.flood.generation.ImmersivePortalsCompat
import mystcraft.flood.item.LinkingBookTarget
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper

class BookReceptacleBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(ModBlockEntities.BOOK_RECEPTACLE, pos, state) {
    
    val inventory = SimpleInventory(1)
    private val activePortals = mutableListOf<BlockPos>()
    private val activeFrameBlocks = mutableListOf<BlockPos>() 
    private var retainedPortalColor: Int? = null
    private var portalSurfaceVisible: Boolean = true
    var destinationRotationQuarterTurns: Int = 0
        private set

    override fun writeNbt(nbt: NbtCompound) {
        super.writeNbt(nbt)
        val itemStack = inventory.getStack(0)
        if (!itemStack.isEmpty) {
            val itemTag = NbtCompound()
            itemStack.writeNbt(itemTag)
            nbt.put("Book", itemTag)
        }
        
        val portalList = NbtList()
        for (p in activePortals) {
            val tag = NbtCompound()
            tag.putInt("x", p.x); tag.putInt("y", p.y); tag.putInt("z", p.z)
            portalList.add(tag)
        }
        nbt.put("ActivePortals", portalList)
        
        val frameList = NbtList()
        for (p in activeFrameBlocks) {
            val tag = NbtCompound()
            tag.putInt("x", p.x); tag.putInt("y", p.y); tag.putInt("z", p.z)
            frameList.add(tag)
        }
        nbt.put("ActiveFrameBlocks", frameList)
        nbt.putInt("DestinationRotationQuarterTurns", destinationRotationQuarterTurns)
        nbt.putBoolean("PortalSurfaceVisible", portalSurfaceVisible)
    }

    override fun readNbt(nbt: NbtCompound) {
        super.readNbt(nbt)
        if (nbt.contains("Book")) {
            inventory.setStack(0, ItemStack.fromNbt(nbt.getCompound("Book")))
        } else {
            inventory.setStack(0, ItemStack.EMPTY)
        }
        
        activePortals.clear()
        if (nbt.contains("ActivePortals")) {
            val list = nbt.getList("ActivePortals", 10)
            for (i in 0 until list.size) {
                val tag = list.getCompound(i)
                activePortals.add(BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")))
            }
        }
        
        activeFrameBlocks.clear()
        if (nbt.contains("ActiveFrameBlocks")) {
            val list = nbt.getList("ActiveFrameBlocks", 10)
            for (i in 0 until list.size) {
                val tag = list.getCompound(i)
                activeFrameBlocks.add(BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")))
            }
        }
        destinationRotationQuarterTurns = Math.floorMod(nbt.getInt("DestinationRotationQuarterTurns"), 4)
        portalSurfaceVisible = !nbt.contains("PortalSurfaceVisible") || nbt.getBoolean("PortalSurfaceVisible")
    }
    
    override fun toUpdatePacket(): net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket? {
        return net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket.create(this)
    }

    override fun toInitialChunkDataNbt(): net.minecraft.nbt.NbtCompound {
        return createNbt()
    }

    fun hasBook() = !inventory.getStack(0).isEmpty

    fun insertBook(stack: ItemStack) {
        inventory.setStack(0, stack)
        markDirty()
        world?.updateListeners(pos, cachedState, cachedState, 3)
        attemptIgnite()
    }

    fun removeBook(): ItemStack {
        val stack = inventory.getStack(0).copy() 
        inventory.setStack(0, ItemStack.EMPTY)
        markDirty()
        world?.updateListeners(pos, cachedState, cachedState, 3)
        extinguishPortal()
        return stack
    }

    /** Rotates the destination view clockwise and rebuilds only an active portal. */
    fun rotateDestinationClockwise(): Int {
        destinationRotationQuarterTurns = (destinationRotationQuarterTurns + 1) % 4
        val wasActive = activePortals.isNotEmpty()
        retainedPortalColor = activePortals.asSequence()
            .mapNotNull { world?.getBlockEntity(it) as? CrystalPortalBlockEntity }
            .map { it.portalColor }
            .firstOrNull { it >= 0 }
        markDirty()
        world?.updateListeners(pos, cachedState, cachedState, 3)

        if (wasActive) {
            extinguishPortal()
            attemptIgnite()
        }
        return destinationRotationQuarterTurns
    }

    /**
     * Hides or restores only Mystcraft's colored portal-block surface. The
     * blocks, block entities, frame integrity checks, and Immersive Portals
     * entity remain alive, so this does not rebuild or relink the portal.
     *
     * @return the new visibility, or null when no IP-backed portal is active.
     */
    fun togglePortalSurfaceVisibility(): Boolean? {
        val serverWorld = world as? ServerWorld ?: return null
        if (!ImmersivePortalsCompat.isAvailable || activePortals.isEmpty()) return null

        val hasImmersivePortal = activePortals.any { portalPos ->
            val portalEntity = serverWorld.getBlockEntity(portalPos) as? CrystalPortalBlockEntity
            portalEntity?.immersivePortalActive == true || ImmersivePortalsCompat.hasPortalAt(serverWorld, portalPos)
        }
        if (!hasImmersivePortal) return null

        portalSurfaceVisible = !portalSurfaceVisible
        activePortals.forEach { portalPos ->
            val state = serverWorld.getBlockState(portalPos)
            if (state.isOf(ModBlocks.CRYSTAL_PORTAL)) {
                serverWorld.setBlockState(
                    portalPos,
                    state.with(CrystalPortalBlock.SURFACE_VISIBLE, portalSurfaceVisible),
                    3
                )
            }
        }
        markDirty()
        serverWorld.updateListeners(pos, cachedState, cachedState, 3)
        return portalSurfaceVisible
    }

    fun verifyPortalIntegrity(): Boolean {
        if (inventory.getStack(0).isEmpty) return false
        
        for (p in activePortals) {
            if (!world!!.getBlockState(p).isOf(ModBlocks.CRYSTAL_PORTAL)) return false
        }
        
        for (p in activeFrameBlocks) {
            val state = world!!.getBlockState(p)
            if (!state.isOf(ModBlocks.CRYSTAL_BLOCK) && !state.isOf(ModBlocks.BOOK_RECEPTACLE)) return false
        }
        
        return true
    }

    private fun attemptIgnite() {
        val world = this.world as? ServerWorld ?: return
        val bookStack = inventory.getStack(0)
        if (bookStack.isEmpty) return 

        var touchingCrystal = false
        for (dir in Direction.entries) {
            if (world.getBlockState(pos.offset(dir)).isOf(ModBlocks.CRYSTAL_BLOCK)) {
                touchingCrystal = true
                break
            }
        }
        if (!touchingCrystal) return

        var destAge = "" 
        var customX: Double? = null
        var customY: Double? = null
        var customZ: Double? = null
        var customYaw: Float? = null

        if (bookStack.hasNbt() && bookStack.nbt != null) {
            val nbt = bookStack.nbt!!
            if (nbt.contains("Age_ID")) destAge = nbt.getString("Age_ID")
            else if (nbt.contains("Dimension")) {
                destAge = nbt.getString("Dimension")
                customX = nbt.getDouble("PosX")
                customZ = nbt.getDouble("PosZ")
                val destinationId = net.minecraft.util.Identifier.tryParse(destAge)
                val destinationWorld = destinationId?.let {
                    world.server.getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, it))
                }
                customY = destinationWorld?.let { LinkingBookTarget.resolveArrivalY(it, nbt) }
                    ?: nbt.getDouble("PosY")
                if (nbt.contains("Yaw")) customYaw = LinkingBookTarget.resolveCardinalYaw(nbt)
            }
        }

        if (destAge.isEmpty()) return

        val possibleStarts = mutableListOf<BlockPos>()
        for (dx in -2..2) {
            for (dy in -2..2) {
                for (dz in -2..2) {
                    val p = pos.add(dx, dy, dz)
                    if (world.getBlockState(p).isReplaceable) possibleStarts.add(p)
                }
            }
        }

        val planes = listOf(
            Pair(Direction.Axis.Z, listOf(Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST)),   
            Pair(Direction.Axis.X, listOf(Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH)), 
            Pair(Direction.Axis.Y, listOf(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) 
        )

        var successfulShape: Set<BlockPos>? = null
        var successfulFrame: Set<BlockPos>? = null
        var successfulAxis: Direction.Axis? = null 

        search@ for (start in possibleStarts) {
            for ((axis, planeDirections) in planes) {
                val visitedAir = mutableSetOf<BlockPos>()
                val visitedFrame = mutableSetOf<BlockPos>()
                val queue = ArrayDeque<BlockPos>()
                
                queue.add(start)
                visitedAir.add(start)
                var isClosed = true

                while (queue.isNotEmpty()) {
                    val curr = queue.removeFirst()
                    if (visitedAir.size > 400) { isClosed = false; break } 

                    for (dir in planeDirections) {
                        val neighbor = curr.offset(dir)
                        if (visitedAir.contains(neighbor) || visitedFrame.contains(neighbor)) continue

                        val state = world.getBlockState(neighbor)
                        
                        if (state.isOf(ModBlocks.CRYSTAL_BLOCK) || state.isOf(ModBlocks.BOOK_RECEPTACLE)) {
                            visitedFrame.add(neighbor)
                        } else if (state.isReplaceable) {
                            visitedAir.add(neighbor)
                            queue.add(neighbor)
                        } else {
                            isClosed = false
                            break
                        }
                    }
                    if (!isClosed) break
                }

                if (isClosed && visitedAir.isNotEmpty() && visitedFrame.isNotEmpty()) {
                    val belongsToUs = visitedFrame.any { it.getSquaredDistance(pos) <= 9.0 }
                    if (belongsToUs) {
                        successfulShape = visitedAir
                        successfulFrame = visitedFrame
                        successfulAxis = axis 
                        break@search
                    }
                }
            }
        }

        if (successfulShape != null && successfulFrame != null && successfulAxis != null) {
            mystcraft.flood.MystcraftReforged.LOGGER.info("Portal ignited! Linking to: $destAge")

            val portalState = ModBlocks.CRYSTAL_PORTAL.defaultState
                .with(net.minecraft.state.property.Properties.AXIS, successfulAxis)
                .with(CrystalPortalBlock.SURFACE_VISIBLE, portalSurfaceVisible)

            // === BULLETPROOF RANDOM COLOR ===
            // Use Math.random() for guaranteed fresh seeds, and immediately strip 
            // the Alpha channel with 'and 0xFFFFFF' so it becomes a positive RGB integer!
            val randomHue = Math.random().toFloat()
            val generatedColor = retainedPortalColor
                ?: (MathHelper.hsvToRgb(randomHue, 0.8f, 0.9f) and 0xFFFFFF)
            retainedPortalColor = null

            successfulShape.forEach { p ->
                world.setBlockState(p, portalState, 3)
                val be = world.getBlockEntity(p) as? CrystalPortalBlockEntity
                if (be != null) {
                    be.destinationAge = if (!destAge.contains(":")) "mystcraft-reforged:$destAge" else destAge
                    be.receptaclePos = pos 
                    be.targetX = customX
                    be.targetY = customY
                    be.targetZ = customZ
                    be.targetYaw = customYaw
                    be.destinationRotationQuarterTurns = destinationRotationQuarterTurns
                    
                    be.portalColor = generatedColor 
                    be.immersivePortalActive = false
                    
                    be.markDirty()
                    world.updateListeners(p, portalState, portalState, 3)
                }
            }
            activePortals.clear()
            activePortals.addAll(successfulShape)
            activeFrameBlocks.clear()
            activeFrameBlocks.addAll(successfulFrame)
            markDirty()

            val targetVec = if (customX != null && customY != null && customZ != null) {
                net.minecraft.util.math.Vec3d(customX, customY, customZ)
            } else null
            val immersivePortalActive = mystcraft.flood.generation.ImmersivePortalsCompat.trySpawnCrystalPortal(
                world,
                successfulShape,
                successfulAxis,
                destAge,
                targetVec,
                destinationRotationQuarterTurns,
                customYaw
            )
            if (immersivePortalActive) {
                successfulShape.forEach { portalPos ->
                    val portalBlockEntity = world.getBlockEntity(portalPos) as? CrystalPortalBlockEntity
                        ?: return@forEach
                    portalBlockEntity.immersivePortalActive = true
                    portalBlockEntity.markDirty()
                    world.updateListeners(portalPos, portalState, portalState, 3)
                }
            }
        }
    }

    fun extinguishPortal() {
        val world = this.world as? ServerWorld ?: return
        
        val portalsToClear = activePortals.toList()
        activePortals.clear()
        activeFrameBlocks.clear()
        markDirty()
        
        portalsToClear.forEach { p ->
            if (world.getBlockState(p).isOf(ModBlocks.CRYSTAL_PORTAL)) {
                world.setBlockState(p, net.minecraft.block.Blocks.AIR.defaultState, 3)
            }
        }
        mystcraft.flood.generation.ImmersivePortalsCompat.removePortalsAround(world, portalsToClear.toSet())
    }
}
