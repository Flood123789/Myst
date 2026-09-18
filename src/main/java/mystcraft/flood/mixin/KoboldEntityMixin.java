package mystcraft.flood.mixin;

import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents Mobs of Mythology's Kobold fight-task lambdas from dereferencing a target which died
 * or was cleared between SmartBrainLib's memory check and behaviour start check.
 */
@Pseudo
@Mixin(targets = "net.pixeldreamstudios.mobs_of_mythology.entity.mobs.KoboldEntity", remap = false)
public abstract class KoboldEntityMixin {
    @Inject(
        method = {"lambda$getFightTasks$1", "lambda$getFightTasks$3"},
        at = @At("HEAD"),
        cancellable = true
    )
    private void mystcraft$requireTargetForFightCondition(
        MobEntity ignored,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (((MobEntity) (Object) this).getTarget() == null) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
        method = "lambda$getFightTasks$5",
        at = @At("HEAD"),
        cancellable = true
    )
    private void mystcraft$requireTargetBeforeStealing(MobEntity ignored, CallbackInfo ci) {
        if (((MobEntity) (Object) this).getTarget() == null) {
            ci.cancel();
        }
    }
}
