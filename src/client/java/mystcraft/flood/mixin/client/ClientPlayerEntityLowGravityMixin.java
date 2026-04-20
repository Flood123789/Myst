package mystcraft.flood.mixin.client;

import mystcraft.flood.client.cache.ClientAgeCache;
import mystcraft.flood.generation.physics.LowGravityPhysics;
import mystcraft.flood.generation.profile.AgeProfile;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityLowGravityMixin {
    @Unique
    private Vec3d mystcraft$preLowGravityVelocity = Vec3d.ZERO;

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void mystcraft$capturePreTickVelocity(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        this.mystcraft$preLowGravityVelocity = player.getVelocity();
    }

    @Inject(method = "tickMovement", at = @At("TAIL"))
    private void mystcraft$applyLowGravityInsideMovementTick(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        if (!player.getWorld().getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) {
            return;
        }

        AgeProfile profile = ClientAgeCache.INSTANCE.getProperties(player.getWorld().getRegistryKey().getValue());
        if (profile == null) {
            return;
        }

        LowGravityPhysics.INSTANCE.applyAfterVanilla(player, profile.getPhysics().getGravityScale(), this.mystcraft$preLowGravityVelocity);
    }
}
