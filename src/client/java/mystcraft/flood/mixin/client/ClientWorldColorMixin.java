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

        cir.setReturnValue(colorToVec(profile.getColors().getCloud()));
    }

    @Inject(method = "getSkyColor", at = @At("HEAD"), cancellable = true)
    private void mystcraft$getSkyColor(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        ClientWorld world = (ClientWorld) (Object) this;
        if (!"mystcraft-reforged".equals(world.getRegistryKey().getValue().getNamespace())) {
            return;
        }

        AgeProfile profile = ClientAgeCache.INSTANCE.getProperties(world.getRegistryKey().getValue());
        if (profile != null) {
            cir.setReturnValue(colorToVec(profile.getColors().getSky()));
        }
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
}
