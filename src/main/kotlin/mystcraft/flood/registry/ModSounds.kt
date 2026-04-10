package mystcraft.flood.registry

import mystcraft.flood.MystcraftReforged
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier

object ModSounds {
    val AGE_ENTER: SoundEvent = register("age_enter")
    val AGE_LEAVE: SoundEvent = register("age_leave")

    private fun register(path: String): SoundEvent {
        val id = Identifier(MystcraftReforged.MOD_ID, path)
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id))
    }

    fun register() = Unit
}
