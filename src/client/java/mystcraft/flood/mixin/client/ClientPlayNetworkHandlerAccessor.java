package mystcraft.flood.mixin.client;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(ClientPlayNetworkHandler.class)
public interface ClientPlayNetworkHandlerAccessor {
    @Accessor("worldKeys")
    Set<RegistryKey<World>> mystcraft$getWorldKeys();

    @Accessor("worldKeys")
    void mystcraft$setWorldKeys(Set<RegistryKey<World>> keys);
}