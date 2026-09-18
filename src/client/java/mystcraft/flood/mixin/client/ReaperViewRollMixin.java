package mystcraft.flood.mixin.client;

import mystcraft.flood.client.render.ReaperCameraRoll;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Rolls the rendered world so a rider stays square with the Reaper carrying them.
 *
 * {@code renderWorld} composes the view as {@code M * Rx(pitch) * Ry(yaw + 180)}, and the matrix
 * stack only ever post-multiplies, so there is no call that can add a rotation to the left of
 * that pair after the fact. Folding the roll into the pitch quaternion is the way in: replacing
 * the pitch argument with {@code Rz(roll) * Rx(pitch)} yields {@code M * Rz * Rx * Ry}, which
 * applies {@code Rz} to coordinates that are already in view space. That is a true camera roll
 * about the view axis rather than a rotation of the world about a world axis.
 *
 * Rolling here rather than in {@code Camera} also means the frustum picks the change up: the
 * stack handed to {@code setupFrustum} is the one modified below, so nothing is culled for being
 * outside an un-rolled view.
 */
@Mixin(GameRenderer.class)
public class ReaperViewRollMixin {

    @ModifyArg(
        method = "renderWorld(FJLnet/minecraft/client/util/math/MatrixStack;)V",
        slice = @Slice(
            from = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/render/Camera;getPitch()F"
            )
        ),
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionf;)V",
            ordinal = 0
        ),
        index = 0
    )
    private Quaternionf mystcraft$rollViewWithReaper(Quaternionf pitchRotation) {
        float roll = ReaperCameraRoll.current();
        if (roll == 0.0f) return pitchRotation;

        // Left-multiplied, so the roll lands in view space. Built into a fresh quaternion rather
        // than mutating the argument, which vanilla may hand out from a shared axis helper.
        return new Quaternionf().rotationZ(roll).mul(pitchRotation);
    }

    /**
     * A rider who dismounts, dies, or changes dimension mid-frame would otherwise leave the last
     * published angle standing and tilt the world permanently. Clearing at the head of every
     * frame means the roll only ever survives if the camera pass republishes it this frame.
     */
    @org.spongepowered.asm.mixin.injection.Inject(
        method = "renderWorld(FJLnet/minecraft/client/util/math/MatrixStack;)V",
        at = @At("HEAD")
    )
    private void mystcraft$clearStaleRoll(
        float tickDelta,
        long limitTime,
        MatrixStack matrices,
        org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci
    ) {
        ReaperCameraRoll.clear();
    }
}
