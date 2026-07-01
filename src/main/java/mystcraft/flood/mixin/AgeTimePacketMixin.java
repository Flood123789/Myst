package mystcraft.flood.mixin;

import mystcraft.flood.generation.profile.AgeProfile;
import mystcraft.flood.generation.profile.AgeProfileManager;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerPlayNetworkHandler.class)
public class AgeTimePacketMixin {

    // Catch the packet regardless of which send method vanilla uses
    @ModifyVariable(
        method = {
            "sendPacket(Lnet/minecraft/network/packet/Packet;)V",
            "sendPacket(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;)V"
        },
        at = @At("HEAD"), 
        argsOnly = true
    )
    private Packet<?> mystcraft$interceptTimePacket(Packet<?> packet) {
        if (packet instanceof WorldTimeUpdateS2CPacket timePacket) {
            ServerPlayNetworkHandler handler = (ServerPlayNetworkHandler) (Object) this;
            
            if (handler.player == null) return packet; 
            
            ServerWorld world = handler.player.getServerWorld();
            
            if (world.getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) {
                AgeProfile profile = AgeProfileManager.INSTANCE.getOrGenerateProfile(world.getServer(), world.getRegistryKey().getValue());
                
                boolean isFixed = profile.getTime().getVisibleTimeFrozen();
                long targetTime = profile.getTime().getVisibleTimeOfDay();
                
                // We construct a replacement packet. 
                // Passing '!isFixed' natively tells the client renderer to freeze the sun.
                return new WorldTimeUpdateS2CPacket(timePacket.getTime(), targetTime, !isFixed);
            }
        }
        return packet;
    }
}
