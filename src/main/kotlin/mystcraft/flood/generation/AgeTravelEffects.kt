package mystcraft.flood.generation

import mystcraft.flood.registry.ModSounds
import net.minecraft.entity.Entity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

object AgeTravelEffects {
    fun playDeparture(world: ServerWorld, pos: Vec3d) {
        val blockPos = BlockPos.ofFloored(pos)
        world.playSound(null, blockPos, ModSounds.AGE_LEAVE, SoundCategory.PLAYERS, 0.9f, 1.0f)
    }

    fun playArrival(world: ServerWorld, pos: Vec3d) {
        val blockPos = BlockPos.ofFloored(pos)
        world.playSound(null, blockPos, ModSounds.AGE_ENTER, SoundCategory.PLAYERS, 1.0f, 1.0f)
    }

    fun playDeparture(world: ServerWorld, entity: Entity) = playDeparture(world, entity.pos)

    fun playArrival(world: ServerWorld, entity: Entity) = playArrival(world, entity.pos)
}
