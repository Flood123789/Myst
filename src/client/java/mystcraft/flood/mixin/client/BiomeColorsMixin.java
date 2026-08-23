package mystcraft.flood.mixin.client;

import mystcraft.flood.client.render.AgeColorContext;
import mystcraft.flood.generation.profile.AgeProfile;
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
        applyMystcraftColor(world, "grass", cir);
    }

    @Inject(method = "getFoliageColor", at = @At("HEAD"), cancellable = true)
    private static void mystcraft$getFoliageColor(BlockRenderView world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        applyMystcraftColor(world, "foliage", cir);
    }

    @Inject(method = "getWaterColor", at = @At("HEAD"), cancellable = true)
    private static void mystcraft$getWaterColor(BlockRenderView world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        applyMystcraftColor(world, "water", cir);
    }

    private static void applyMystcraftColor(BlockRenderView view, String type, CallbackInfoReturnable<Integer> cir) {
        AgeProfile profile = AgeColorContext.getProfile(view);
        if (profile == null) return;

        Integer color = switch (type) {
            case "grass" -> profile.getColors().getGrass() & 0xFFFFFF;
            case "foliage" -> profile.getColors().getFoliage() & 0xFFFFFF;
            case "water" -> profile.getColors().getWater() & 0xFFFFFF;
            default -> null;
        };
        if (color != null) {
            cir.setReturnValue(color);
        }
    }
}
