package mystcraft.flood.client.render

import mystcraft.flood.client.cache.ClientAgeCache
import mystcraft.flood.generation.ChaosAgeThemes
import net.minecraft.client.MinecraftClient
import net.minecraft.particle.ParticleTypes
import kotlin.random.Random

object AgeAmbientParticlePainter {
    fun tick(client: MinecraftClient) {
        val world = client.world ?: return
        val player = client.player ?: return
        val ageId = world.registryKey.value
        if (ageId.namespace != "mystcraft-reforged") return

        val profile = ClientAgeCache.getProperties(ageId) ?: return
        val particleThemes = profile.modifiers.filter { it in ChaosAgeThemes.PARTICLES }
        if (particleThemes.isEmpty()) return

        val random = Random(profile.seed xor world.time xor player.blockPos.asLong())
        particleThemes.forEach { theme ->
            val count = if (theme == ChaosAgeThemes.PARTICLE_VOID) 1 else 2
            repeat(count) {
                spawnParticle(client, theme, random)
            }
        }
    }

    private fun spawnParticle(client: MinecraftClient, theme: String, random: Random) {
        val world = client.world ?: return
        val player = client.player ?: return
        val x = player.x + random.nextDouble(-12.0, 12.0)
        val y = player.eyeY + random.nextDouble(-2.5, 8.0)
        val z = player.z + random.nextDouble(-12.0, 12.0)
        val driftX = random.nextDouble(-0.012, 0.012)
        val driftY = random.nextDouble(-0.006, 0.018)
        val driftZ = random.nextDouble(-0.012, 0.012)

        when (theme) {
            ChaosAgeThemes.PARTICLE_MOTES -> world.addParticle(ParticleTypes.END_ROD, x, y, z, driftX * 0.4, 0.008 + driftY, driftZ * 0.4)
            ChaosAgeThemes.PARTICLE_ASH -> world.addParticle(ParticleTypes.ASH, x, y + 2.0, z, driftX * 0.6, -0.018, driftZ * 0.6)
            ChaosAgeThemes.PARTICLE_SPORES -> world.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, x, y, z, driftX, -0.004 + driftY * 0.25, driftZ)
            ChaosAgeThemes.PARTICLE_VOID -> world.addParticle(ParticleTypes.PORTAL, x, y, z, driftX * 1.8, driftY * 0.8, driftZ * 1.8)
        }
    }
}
