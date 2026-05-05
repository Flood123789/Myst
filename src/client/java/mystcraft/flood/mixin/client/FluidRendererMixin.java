package mystcraft.flood.mixin.client;

import mystcraft.flood.client.cache.ClientAgeCache;
import mystcraft.flood.generation.profile.AgeProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.render.block.FluidRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FluidRenderer.class)
public class FluidRendererMixin {
    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/color/world/BiomeColors;getWaterColor(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/util/math/BlockPos;)I"
            )
    )
    private int mystcraft$getWaterColor(BlockRenderView world, BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null && "mystcraft-reforged".equals(client.world.getRegistryKey().getValue().getNamespace())) {
            AgeProfile profile = ClientAgeCache.INSTANCE.getProperties(client.world.getRegistryKey().getValue());
            if (profile != null) {
                return profile.getColors().getWater() & 0xFFFFFF;
            }
        }

        return BiomeColors.getWaterColor(world, pos);
    }
}
