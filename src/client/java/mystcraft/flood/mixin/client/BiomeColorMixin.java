package mystcraft.flood.mixin.client;

import mystcraft.flood.cache.ClientAgeCache;
import mystcraft.flood.generation.profile.AgeProfile; // NEW IMPORT
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Biome.class)
public class BiomeColorMixin {
    
    @Inject(method = "getSkyColor", at = @At("HEAD"), cancellable = true)
    private void mystcraft$overrideSkyColor(CallbackInfoReturnable<Integer> cir) { applyMystColor(cir, "sky"); }

    @Inject(method = "getFogColor", at = @At("HEAD"), cancellable = true)
    private void mystcraft$overrideFogColor(CallbackInfoReturnable<Integer> cir) { applyMystColor(cir, "fog"); }

    @Inject(method = "getWaterColor", at = @At("HEAD"), cancellable = true)
    private void mystcraft$overrideWaterColor(CallbackInfoReturnable<Integer> cir) { applyMystColor(cir, "water"); }

    @Inject(method = "getFoliageColor", at = @At("HEAD"), cancellable = true)
    private void mystcraft$overrideFoliageColor(CallbackInfoReturnable<Integer> cir) { applyMystColor(cir, "foliage"); }

    @Inject(method = "getGrassColorAt", at = @At("HEAD"), cancellable = true)
    private void mystcraft$overrideGrassColor(double x, double z, CallbackInfoReturnable<Integer> cir) { applyMystColor(cir, "grass"); }

    private void applyMystColor(CallbackInfoReturnable<Integer> cir, String type) {
        var client = MinecraftClient.getInstance();
        if (client.world == null) return;

        if (client.world.getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) {
            AgeProfile profile = ClientAgeCache.INSTANCE.getProperties(client.world.getRegistryKey().getValue());
            if (profile != null) {
                int color = switch (type) {
                    case "sky" -> profile.getColors().getSky();
                    case "fog" -> profile.getColors().getFog();
                    case "water" -> profile.getColors().getWater();
                    case "foliage" -> profile.getColors().getFoliage();
                    case "grass" -> profile.getColors().getGrass();
                    default -> 0;
                };
                if (color != 0) cir.setReturnValue(color);
            }
        }
    }
}