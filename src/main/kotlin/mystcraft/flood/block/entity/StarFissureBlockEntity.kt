package mystcraft.flood.block

// THIS IS THE LINE TO FIX:
import mystcraft.flood.block.entity.ModBlockEntities 

import net.minecraft.block.BlockState
import net.minecraft.block.entity.EndPortalBlockEntity
import net.minecraft.util.math.BlockPos

class StarFissureBlockEntity(pos: BlockPos, state: BlockState) : 
    EndPortalBlockEntity(ModBlockEntities.STAR_FISSURE, pos, state)