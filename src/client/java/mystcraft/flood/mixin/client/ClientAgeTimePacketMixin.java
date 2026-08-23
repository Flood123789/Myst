package mystcraft.flood.mixin.client;

import mystcraft.flood.client.cache.ClientAgeTimeCache;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A Mystcraft Age has a dimension-specific clock that supports fixed and scaled time. Vanilla's
 * time packet carries the host world's clock, so accepting it after an Age clock has synchronized
 * makes the client alternate between those two clocks. The custom dimension-time packet remains
 * the sole authority for an active Age.
 */
@Mixin(ClientPlayNetworkHandler.class)
public class ClientAgeTimePacketMixin {
    @Inject(method = "onWorldTimeUpdate", at = @At("HEAD"), cancellable = true)
    private void mystcraft$ignoreHostTimeForSynchronizedAge(WorldTimeUpdateS2CPacket packet, CallbackInfo ci) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return;

        var id = world.getRegistryKey().getValue();
        if (!"mystcraft-reforged".equals(id.getNamespace())) return;
        if (ClientAgeTimeCache.INSTANCE.hasAuthoritativeTime(id)) {
            ci.cancel();
        }
    }
}
