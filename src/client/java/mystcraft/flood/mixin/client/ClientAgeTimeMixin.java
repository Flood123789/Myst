package mystcraft.flood.mixin.client;

import mystcraft.flood.client.cache.ClientAgeTimeCache;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Resolves sky and lightmap time from the world being rendered, not from
 * MinecraftClient.world. Immersive Portals temporarily renders several
 * ClientWorld instances in one frame, so a global clock leaks an Age's night
 * into the host dimension.
 */
@Mixin(World.class)
public class ClientAgeTimeMixin {
    @Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true)
    private void mystcraft$getDimensionLocalTime(CallbackInfoReturnable<Long> cir) {
        World world = (World) (Object) this;
        if (!(world instanceof ClientWorld)) {
            return;
        }

        Identifier id = world.getRegistryKey().getValue();
        if (!"mystcraft-reforged".equals(id.getNamespace())) {
            return;
        }

        Long visibleTime = ClientAgeTimeCache.INSTANCE.getVisibleTime(id);
        if (visibleTime != null) {
            cir.setReturnValue(visibleTime);
        }
    }
}
