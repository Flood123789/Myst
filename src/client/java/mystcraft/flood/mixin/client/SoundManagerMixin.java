package mystcraft.flood.mixin.client;

import mystcraft.flood.client.AgeTravelSoundSuppressor;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundManager.class)
public class SoundManagerMixin {

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void mystcraft$suppressVanillaTravelSounds(SoundInstance sound, CallbackInfo ci) {
        if (AgeTravelSoundSuppressor.INSTANCE.shouldSuppress(sound.getId())) {
            ci.cancel();
        }
    }
}
