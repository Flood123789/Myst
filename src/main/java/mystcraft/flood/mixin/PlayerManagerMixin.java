package mystcraft.flood.mixin;

import mystcraft.flood.generation.AgeSubdimensionManager;
import mystcraft.flood.player.PlayerSpawnMemory;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {
    @Inject(method = "respawnPlayer", at = @At("HEAD"))
    private void mystcraft$restoreSpawnBeforeRespawn(ServerPlayerEntity player, boolean alive, CallbackInfoReturnable<ServerPlayerEntity> cir) {
        if (AgeSubdimensionManager.isMystcraftRealm(player.getServerWorld().getRegistryKey())) {
            PlayerSpawnMemory.restoreForCurrentWorld(player, player.getPos());
        }
    }
}
