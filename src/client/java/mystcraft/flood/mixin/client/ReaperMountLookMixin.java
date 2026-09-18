package mystcraft.flood.mixin.client;

import mystcraft.flood.client.compat.ReaperVrCompat;
import mystcraft.flood.client.config.ReaperMountClientConfig;
import mystcraft.flood.entity.ParadoxReaperEntity;
import mystcraft.flood.entity.SurfaceCling;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turns the rider's aim in the Reaper's frame rather than the world's.
 *
 * Vanilla adds the mouse delta straight onto world yaw and pitch. That is fine while up is up,
 * but the mounted view is rolled to match the creature, so on a wall the screen's horizontal axis
 * is no longer the world's: dragging the mouse sideways swings the view up the screen instead of
 * across it, and on a ceiling it moves the wrong way entirely. Rotating the look direction about
 * the axes the player can actually see puts the controls back under the picture.
 *
 * It also removes a worse problem. Roll is measured about the view's forward axis, and that angle
 * is undefined when forward lines up with the body's up — which on a wall is simply looking
 * straight at it. Approaching that direction made the roll swing wildly and then snap to nothing
 * as the measurement gave out, which is most of what "the camera moves poorly" was. Clamping the
 * pitch against the creature's own horizon means forward can never reach that axis, so the
 * singularity is unreachable rather than merely handled.
 *
 * Left alone in VR. There the aim is the player's physical head, not a pair of numbers this mod
 * is entitled to redefine.
 */
@Mixin(Entity.class)
public abstract class ReaperMountLookMixin {

    @Shadow public float prevPitch;
    @Shadow public float prevYaw;

    @Shadow public abstract float getPitch();
    @Shadow public abstract float getYaw();
    @Shadow public abstract void setPitch(float pitch);
    @Shadow public abstract void setYaw(float yaw);
    @Shadow public abstract Vec3d getRotationVector();

    /** Sine of the steepest angle the rider may look from the creature's horizon. */
    private static final double MYSTCRAFT$PITCH_LIMIT = 0.9998;

    /** Vanilla's degrees-per-unit-of-mouse-delta. */
    private static final double MYSTCRAFT$SENSITIVITY = 0.15;

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void mystcraft$lookInReaperFrame(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof PlayerEntity)) return;
        if (!self.getWorld().isClient()) return;
        if (!ReaperMountClientConfig.bodyTrackingCameraEnabled()) return;
        if (!(self.getVehicle() instanceof ParadoxReaperEntity reaper)) return;
        if (!reaper.getMountVisualPoseReady()) return;
        if (ReaperVrCompat.isInVr()) return;

        Vec3d frameUp = mystcraft$screenUp(reaper);
        if (frameUp == null) return;

        Vec3d look = getRotationVector();
        double yawRadians = Math.toRadians(cursorDeltaX * MYSTCRAFT$SENSITIVITY);
        double pitchRadians = Math.toRadians(cursorDeltaY * MYSTCRAFT$SENSITIVITY);

        // Yaw first, about what the player sees as vertical. Negated because Minecraft's yaw
        // increases clockwise seen from above, the opposite sense to a right-handed rotation.
        look = SurfaceCling.rotateAround(look, frameUp, -yawRadians);

        // Then pitch, about the screen's horizontal axis recomputed after that turn. Composing
        // the two in this order is what keeps the horizon from creeping as the player circles.
        Vec3d lateral = frameUp.crossProduct(look);
        if (lateral.lengthSquared() < 1.0e-8) return;
        lateral = lateral.normalize();
        look = SurfaceCling.rotateAround(look, lateral, pitchRadians);

        look = mystcraft$clampToHorizon(look, frameUp);
        if (look == null) return;

        // Back to the scalars everything downstream still reads: steering projects this onto the
        // surface, the server receives it, and reach and interaction are cast along it.
        float wantedYaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float wantedPitch = (float) Math.toDegrees(-Math.asin(MathHelper.clamp(look.y, -1.0, 1.0)));

        // Applied as deltas rather than assigned, so yaw stays continuous across the wrap point
        // and does not spin the long way round when it crosses south.
        float yawStep = MathHelper.wrapDegrees(wantedYaw - getYaw());
        float pitchStep = wantedPitch - getPitch();

        setYaw(getYaw() + yawStep);
        setPitch(MathHelper.clamp(getPitch() + pitchStep, -90.0f, 90.0f));

        // Vanilla advances the previous-frame angles by the same step so the render interpolation
        // does not lag a whole tick behind the input, and notifies the vehicle. Both still apply.
        this.prevYaw += yawStep;
        this.prevPitch = MathHelper.clamp(this.prevPitch + pitchStep, -90.0f, 90.0f);

        Entity vehicle = self.getVehicle();
        if (vehicle != null) vehicle.onPassengerLookAround(self);

        ci.cancel();
    }

    /**
     * The direction the player currently sees as up.
     *
     * At full roll strength that is the creature's up. Below it the view is only part way over, so
     * the control frame is carried the same fraction of the way and the two stay in agreement.
     */
    private Vec3d mystcraft$screenUp(ParadoxReaperEntity reaper) {
        Vec3d bodyUp = reaper.getMountVisualUp();
        if (bodyUp.lengthSquared() < 1.0e-8) return null;
        bodyUp = bodyUp.normalize();

        double strength = ReaperMountClientConfig.cameraRollStrength();
        if (strength >= 0.999) return bodyUp;
        if (strength <= 0.001) return null;

        Vec3d worldUp = new Vec3d(0.0, 1.0, 0.0);
        double angle = Math.acos(MathHelper.clamp(worldUp.dotProduct(bodyUp), -1.0, 1.0));
        return SurfaceCling.rotateToward(worldUp, bodyUp, angle * strength);
    }

    /**
     * Holds the aim short of the creature's own vertical.
     *
     * Passing it would tumble the view over the top, and arriving exactly on it leaves the roll
     * measurement with no plane to work in.
     */
    private Vec3d mystcraft$clampToHorizon(Vec3d look, Vec3d frameUp) {
        double along = look.dotProduct(frameUp);
        if (Math.abs(along) <= MYSTCRAFT$PITCH_LIMIT) return look;

        Vec3d flat = look.subtract(frameUp.multiply(along));
        if (flat.lengthSquared() < 1.0e-10) return null;

        double spread = Math.sqrt(1.0 - MYSTCRAFT$PITCH_LIMIT * MYSTCRAFT$PITCH_LIMIT);
        return flat.normalize().multiply(spread)
            .add(frameUp.multiply(Math.signum(along) * MYSTCRAFT$PITCH_LIMIT));
    }
}
