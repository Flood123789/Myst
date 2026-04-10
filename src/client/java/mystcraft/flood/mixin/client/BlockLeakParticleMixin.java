package mystcraft.flood.mixin.client;

import mystcraft.flood.client.cache.ClientAgeCache;
import mystcraft.flood.generation.profile.AgeProfile;
import net.minecraft.client.particle.BlockLeakParticle;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockLeakParticle.class)
public abstract class BlockLeakParticleMixin extends SpriteBillboardParticle {

    protected BlockLeakParticleMixin(ClientWorld world, double x, double y, double z) {
        super(world, x, y, z);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void mystcraft$tintWaterDrips(CallbackInfo ci) {
        if (this.world == null || !this.world.getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) {
            return;
        }

        if (this.blue < this.red || this.blue < this.green) {
            return;
        }

        AgeProfile profile = ClientAgeCache.INSTANCE.getProperties(this.world.getRegistryKey().getValue());
        if (profile == null) {
            return;
        }

        int color = profile.getColors().getWater();
        this.setColor(
            ((color >> 16) & 0xFF) / 255.0f,
            ((color >> 8) & 0xFF) / 255.0f,
            (color & 0xFF) / 255.0f
        );
    }
}
