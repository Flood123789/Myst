package mystcraft.flood.block.entity

import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.util.math.BlockPos

class CrystalPortalBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(ModBlockEntities.CRYSTAL_PORTAL, pos, state) {
    
    var destinationAge: String = ""
    var receptaclePos: BlockPos? = null 
    
    var targetX: Double? = null
    var targetY: Double? = null
    var targetZ: Double? = null
    
    // === THE MISSING VARIABLE: Stores the random color! ===
    var portalColor: Int = -1 

    override fun toUpdatePacket(): net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket? {
        return net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket.create(this)
    }

    override fun toInitialChunkDataNbt(): net.minecraft.nbt.NbtCompound {
        return createNbt()
    }

    override fun writeNbt(nbt: NbtCompound) {
        super.writeNbt(nbt)
        nbt.putString("DestinationAge", destinationAge)
        receptaclePos?.let { 
            nbt.putInt("RecX", it.x)
            nbt.putInt("RecY", it.y)
            nbt.putInt("RecZ", it.z)
        }
        targetX?.let { nbt.putDouble("TargetX", it) }
        targetY?.let { nbt.putDouble("TargetY", it) }
        targetZ?.let { nbt.putDouble("TargetZ", it) }
        
        // Save the color
        nbt.putInt("PortalColor", portalColor)
    }

    override fun readNbt(nbt: NbtCompound) {
        super.readNbt(nbt)
        destinationAge = nbt.getString("DestinationAge")
        if (nbt.contains("RecX")) {
            receptaclePos = BlockPos(nbt.getInt("RecX"), nbt.getInt("RecY"), nbt.getInt("RecZ"))
        }
        if (nbt.contains("TargetX")) targetX = nbt.getDouble("TargetX")
        if (nbt.contains("TargetY")) targetY = nbt.getDouble("TargetY")
        if (nbt.contains("TargetZ")) targetZ = nbt.getDouble("TargetZ")
        
        // Load the color
        if (nbt.contains("PortalColor")) {
            portalColor = nbt.getInt("PortalColor")
        }
    }
}