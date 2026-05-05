package mystcraft.flood.mixin.client;

import mystcraft.flood.client.cache.ClientAgeCache;
import mystcraft.flood.generation.profile.AgeProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BiomeColors.class)
public class BiomeColorsMixin {
    @Inject(method = "getGrassColor", at = @At("HEAD"), cancellable = true)
    private static void mystcraft$getGrassColor(BlockRenderView world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        applyMystcraftColor("grass", cir);
    }

    @Inject(method = "getFoliageColor", at = @At("HEAD"), cancellable = true)
    private static void mystcraft$getFoliageColor(BlockRenderView world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        applyMystcraftColor("foliage", cir);
    }

    @Inject(method = "getWaterColor", at = @At("HEAD"), cancellable = true)
    private static void mystcraft$getWaterColor(BlockRenderView world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        applyMystcraftColor("water", cir);
    }

    private static void applyMystcraftColor(String type, CallbackInfoReturnable<Integer> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        if (!"mystcraft-reforged".equals(client.world.getRegistryKey().getValue().getNamespace())) return;

        AgeProfile profile = ClientAgeCache.INSTANCE.getProperties(client.world.getRegistryKey().getValue());
        if (profile == null) return;

        Integer color = switch (type) {
            case "grass" -> profile.getColors().getGrass();
            case "foliage" -> profile.getColors().getFoliage();
            case "water" -> profile.getColors().getWater();
            default -> null;
        };
        if (color != null) {
            cir.setReturnValue(color);
        }
    }
}
