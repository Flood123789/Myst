package mystcraft.flood.mixin.client;

import mystcraft.flood.client.cache.ClientAgeCache;
import mystcraft.flood.generation.profile.AgeProfile;
import net.minecraft.block.MapColor;
import net.minecraft.item.map.MapState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.render.MapRenderer$MapTexture")
public abstract class MapRendererMixin {
    @Shadow private MapState state;

    @Redirect(
        method = "updateTexture",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/MapColor;getRenderColor(Lnet/minecraft/block/MapColor$Brightness;)I"
        ),
        require = 0
    )
    private int mystcraft$overrideMapColor(MapColor instance, MapColor.Brightness brightness) {
        if (state != null && state.dimension != null && "mystcraft-reforged".equals(state.dimension.getValue().getNamespace())) {
            AgeProfile profile = ClientAgeCache.INSTANCE.getProperties(state.dimension.getValue());
            if (profile != null) {
                Integer customRgb = null;
                if (instance == MapColor.PALE_GREEN || instance.id == 1) {
                    customRgb = profile.getColors().getGrass();
                } else if (instance == MapColor.DARK_GREEN || instance.id == 3) {
                    customRgb = profile.getColors().getFoliage();
                } else if (instance == MapColor.WATER_BLUE || instance.id == 12) {
                    customRgb = profile.getColors().getWater();
                }

                if (customRgb != null) {
                    int mult = brightness.brightness;
                    int r = (((customRgb >> 16) & 0xFF) * mult) / 255;
                    int g = (((customRgb >> 8) & 0xFF) * mult) / 255;
                    int b = ((customRgb & 0xFF) * mult) / 255;
                    return -16777216 | (b << 16) | (g << 8) | r;
                }
            }
        }
        return instance.getRenderColor(brightness);
    }
}
