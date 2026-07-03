package mystcraft.flood.mixin.client;

import mystcraft.flood.client.cache.ClientAgeCache;
import mystcraft.flood.generation.profile.AgeProfile;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.ColorResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.class)
public class ClientWorldColorMixin {
    private static final double CLOUD_BLEND = 0.55;

    @Inject(method = "getCloudsColor", at = @At("RETURN"), cancellable = true)
    private void mystcraft$getCloudsColor(float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        ClientWorld world = (ClientWorld) (Object) this;
        if (!"mystcraft-reforged".equals(world.getRegistryKey().getValue().getNamespace())) {
            return;
        }

        AgeProfile profile = ClientAgeCache.INSTANCE.getProperties(world.getRegistryKey().getValue());
        if (profile == null) {
            return;
        }

        Vec3d target = colorToVec(profile.getColors().getCloud());
        Vec3d base = cir.getReturnValue();
        if (base == null) {
            cir.setReturnValue(target);
            return;
        }

        cir.setReturnValue(new Vec3d(
                lerp(base.x, target.x, CLOUD_BLEND),
                lerp(base.y, target.y, CLOUD_BLEND),
                lerp(base.z, target.z, CLOUD_BLEND)
        ));
    }

    @Inject(method = "getColor", at = @At("HEAD"), cancellable = true)
    private void mystcraft$getAgeColor(BlockPos pos, ColorResolver colorResolver, CallbackInfoReturnable<Integer> cir) {
        if (colorResolver != BiomeColors.WATER_COLOR) {
            return;
        }

        ClientWorld world = (ClientWorld) (Object) this;
        if (!"mystcraft-reforged".equals(world.getRegistryKey().getValue().getNamespace())) {
            return;
        }

        AgeProfile profile = ClientAgeCache.INSTANCE.getProperties(world.getRegistryKey().getValue());
        if (profile != null) {
            cir.setReturnValue(profile.getColors().getWater() & 0xFFFFFF);
        }
    }

    private static Vec3d colorToVec(int color) {
        return new Vec3d(
                ((color >> 16) & 0xFF) / 255.0,
                ((color >> 8) & 0xFF) / 255.0,
                (color & 0xFF) / 255.0
        );
    }

    private static double lerp(double from, double to, double amount) {
        return from * (1.0 - amount) + to * amount;
    }
}
