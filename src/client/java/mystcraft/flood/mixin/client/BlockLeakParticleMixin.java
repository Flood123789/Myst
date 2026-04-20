package mystcraft.flood.mixin.client;

import mystcraft.flood.client.cache.ClientAgeCache;
import mystcraft.flood.generation.profile.AgeProfile;
import net.minecraft.client.particle.BlockLeakParticle;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
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
    private void mystcraft$tintDrips(CallbackInfo ci) {
        if (this.world == null || !this.world.getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) {
            return;
        }

        AgeProfile profile = ClientAgeCache.INSTANCE.getProperties(this.world.getRegistryKey().getValue());
        if (profile == null) {
            return;
        }

        Fluid fluid = ((BlockLeakParticleAccessor) this).mystcraft$getFluid();
        int color;
        if (fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER) {
            color = profile.getColors().getWater();
        } else if (fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA) {
            color = profile.getColors().getFireLava();
        } else {
            return;
        }

        this.setColor(
            ((color >> 16) & 0xFF) / 255.0f,
            ((color >> 8) & 0xFF) / 255.0f,
            (color & 0xFF) / 255.0f
        );
    }
}
