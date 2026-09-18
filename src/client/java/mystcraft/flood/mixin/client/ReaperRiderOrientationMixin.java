package mystcraft.flood.mixin.client;

import mystcraft.flood.entity.ParadoxReaperEntity;
import mystcraft.flood.entity.ReaperMounting;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts a Reaper's rider on the creature's back and turns them to match it.
 *
 * Two corrections, both applied before anything else touches the matrix.
 *
 * <p><b>Seat.</b> {@code updatePassengerPosition} already places the rider on the drawn shell, but
 * it runs once a tick and the result is then interpolated, so the model reaches the right place a
 * tick late and smoothed — which against a body that rises over terrain and crouches as it settles
 * reads as floating rather than sitting. Correcting here instead lands the model on the pose the
 * renderer is drawing *this frame*, so the rider goes up when the creature rises and down when it
 * crouches, exactly in step. The tick-rate seat is still the authority for collision, interaction,
 * and dismounting; this only removes the visual lag on top of it.
 *
 * <p><b>Orientation.</b> A mounted entity is otherwise drawn from world axes, so a rider crossing
 * a ceiling stayed bolted upright alongside the creature rather than on it.
 *
 * <p>{@code setupTransforms} is the right seam for both. It runs with the matrix at the entity's
 * own origin and world-aligned, before vanilla applies body yaw, so a world-space translation
 * lands directly and the rotation put in front of that yaw makes the yaw turn about the
 * creature's up rather than the world's. Rotating any later would leave the two disagreeing.
 *
 * <p>Targets {@link LivingEntityRenderer} rather than the player renderer, so anything a Reaper
 * can carry is drawn the same way.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class ReaperRiderOrientationMixin {

    private static final Vector3f MYSTCRAFT$WORLD_UP = new Vector3f(0.0f, 1.0f, 0.0f);

    @Inject(method = "setupTransforms", at = @At("HEAD"))
    private void mystcraft$seatAndAlignRider(
        LivingEntity entity,
        MatrixStack matrices,
        float animationProgress,
        float bodyYaw,
        float tickDelta,
        CallbackInfo ci
    ) {
        if (!(entity.getVehicle() instanceof ParadoxReaperEntity reaper)) return;
        if (!reaper.getMountVisualPoseReady()) return;

        Vec3d up = reaper.getMountVisualUp();
        if (up.lengthSquared() < 1.0e-8) return;

        mystcraft$snapToBack(entity, reaper, up, matrices, tickDelta);
        mystcraft$alignToBody(up, matrices);
    }

    /** Moves the model from where interpolation put it onto the seat being drawn this frame. */
    private void mystcraft$snapToBack(
        LivingEntity entity,
        ParadoxReaperEntity reaper,
        Vec3d up,
        MatrixStack matrices,
        float tickDelta
    ) {
        Vec3d seat = ReaperMounting.INSTANCE.seatOnBody(
            reaper.getMountVisualBody(), up, reaper.isLesser() ? 0.5 : 1.0
        );
        Vec3d drawnAt = new Vec3d(
            MathHelper.lerp(tickDelta, entity.prevX, entity.getX()),
            MathHelper.lerp(tickDelta, entity.prevY, entity.getY()),
            MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ())
        );

        Vec3d correction = seat.subtract(drawnAt);
        // A correction this large means the two are describing different creatures — a stale pose
        // from a Reaper that has left the view, most likely. Skip rather than fling the model.
        if (correction.lengthSquared() > MYSTCRAFT$MAX_CORRECTION * MYSTCRAFT$MAX_CORRECTION) return;

        matrices.translate(correction.x, correction.y, correction.z);
    }

    /** Turns the model so its up axis is the creature's, about the seat translated to above. */
    private void mystcraft$alignToBody(Vec3d up, MatrixStack matrices) {
        Vector3f wanted = new Vector3f((float) up.x, (float) up.y, (float) up.z).normalize();
        float alignment = MYSTCRAFT$WORLD_UP.dot(wanted);
        if (alignment > 0.99999f) return;

        Vector3f axis = new Vector3f(MYSTCRAFT$WORLD_UP).cross(wanted);
        if (axis.lengthSquared() < 1.0e-8f) {
            // Exactly inverted, so every axis is a valid turn and the cross product cannot pick
            // one. Hanging from a ceiling reaches this on the nose, so choose deterministically
            // rather than letting a degenerate axis produce a NaN pose.
            axis.set(1.0f, 0.0f, 0.0f);
        }
        axis.normalize();

        float angle = (float) Math.acos(MathHelper.clamp(alignment, -1.0f, 1.0f));
        matrices.multiply(new Quaternionf().rotationAxis(angle, axis));
    }

    /** Blocks of seat correction past which the drawn pose is assumed to belong to something else. */
    private static final double MYSTCRAFT$MAX_CORRECTION = 4.0;
}
