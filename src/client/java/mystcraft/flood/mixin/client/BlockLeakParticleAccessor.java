package mystcraft.flood.mixin.client;

import net.minecraft.client.particle.BlockLeakParticle;
import net.minecraft.fluid.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BlockLeakParticle.class)
public interface BlockLeakParticleAccessor {
    @Invoker("getFluid")
    Fluid mystcraft$getFluid();
}
