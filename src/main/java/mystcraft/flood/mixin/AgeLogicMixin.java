package mystcraft.flood.mixin;

import mystcraft.flood.generation.AgeWorldProperties;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;
import net.minecraft.world.level.ServerWorldProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
public class AgeLogicMixin {

    @Inject(method = "tickTime", at = @At("HEAD"), cancellable = true)
    private void mystcraft$decoupledTick(CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        
        // Safely check if this world is using our independent heart
        if (world.getLevelProperties() instanceof AgeWorldProperties ageProps) {
            ageProps.tickAgeTime();
            ci.cancel(); // Stop vanilla daylight logic entirely
        }
    }

    @Redirect(method = "tickTime", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/GameRules;getBoolean(Lnet/minecraft/world/GameRules$Key;)Z"))
    private boolean mystcraft$forceDaylightCycleOff(GameRules gameRules, GameRules.Key<GameRules.BooleanRule> rule) {
        ServerWorld world = (ServerWorld) (Object) this;
        // Lie to the client renderer to stop the sun from stuttering
        if (world.getLevelProperties() instanceof AgeWorldProperties) {
            return false; 
        }
        return gameRules.getBoolean(rule);
    }
}