package mystcraft.flood.mixin.client;

import mystcraft.flood.client.compat.ReaperVrCompat;
import mystcraft.flood.client.config.ReaperMountClientConfig;
import mystcraft.flood.client.render.ReaperCameraRoll;
import mystcraft.flood.client.render.ReaperMountCamera;
import mystcraft.flood.entity.ParadoxReaperEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Aligns a mounted camera to the procedural body pose without changing the player's aim.
 *
 * Three things happen here. The camera is moved onto the shell the renderer actually drew, which
 * the entity's own position cannot supply because the drawn body rides a solved leg stance rather
 * than the hitbox origin. That position is filtered and, where the creature has put its back
 * inside a wall, displaced — both handled by {@link ReaperMountCamera}. And the roll needed to
 * keep the rider square with the body is measured and published for {@link ReaperViewRollMixin},
 * which is the injection that can genuinely rotate the picture; rolling this class's quaternion
 * alone only ever reached billboarded particles and nameplates.
 *
 * The quaternion and basis vectors are still rolled, so those billboards agree with the view.
 */
@Mixin(Camera.class)
public abstract class ReaperMountCameraMixin {
    @Shadow @Final private Quaternionf rotation;
    @Shadow @Final private Vector3f horizontalPlane;
    @Shadow @Final private Vector3f verticalPlane;
    @Shadow @Final private Vector3f diagonalPlane;
    @Shadow protected abstract void setPos(Vec3d pos);

    @Inject(method = "update", at = @At("TAIL"))
    private void mystcraft$trackReaperBody(
        BlockView area,
        Entity focusedEntity,
        boolean thirdPerson,
        boolean inverseView,
        float tickDelta,
        CallbackInfo ci
    ) {
        if (!ReaperMountClientConfig.bodyTrackingCameraEnabled()) return;
        if (!(focusedEntity.getVehicle() instanceof ParadoxReaperEntity reaper)) {
            // Dismounted, or never mounted. Drop the filter's history so remounting does not
            // start by easing in from wherever the last ride happened to end.
            ReaperMountCamera.forget();
            return;
        }
        if (!reaper.hasPassenger(focusedEntity)) return;

        Vec3d bodyUp = reaper.getMountVisualPoseReady()
            ? reaper.getMountVisualUp()
            : reaper.clingNormal();
        Vec3d body = reaper.getMountVisualPoseReady()
            ? reaper.getMountVisualBody()
            : reaper.getPos().add(bodyUp.multiply(0.95));
        Vec3d bodyForward = reaper.getMountVisualForward();

        Vec3d eyeAnchor = body.add(bodyUp.multiply(0.76));

        // The same eye height taken from the creature's raw interpolated position rather than its
        // drawn pose. Only the gap between the two is filtered, so the creature's actual travel
        // reaches the camera untouched and steering stays exactly as responsive as it was.
        Vec3d rawBody = new Vec3d(
            MathHelper.lerp(tickDelta, reaper.prevX, reaper.getX()),
            MathHelper.lerp(tickDelta, reaper.prevY, reaper.getY()),
            MathHelper.lerp(tickDelta, reaper.prevZ, reaper.getZ())
        );
        Vec3d reference = rawBody.add(bodyUp.multiply(0.95 + 0.76));

        double clock = reaper.age + tickDelta;
        Vec3d resolved = ReaperMountCamera.resolve(reaper, eyeAnchor, reference, bodyUp, bodyForward, clock);

        Camera camera = (Camera) (Object) this;
        if (thirdPerson) {
            Vec3d vanillaEye = new Vec3d(
                MathHelper.lerp(tickDelta, focusedEntity.prevX, focusedEntity.getX()),
                MathHelper.lerp(tickDelta, focusedEntity.prevY, focusedEntity.getY())
                    + focusedEntity.getStandingEyeHeight(),
                MathHelper.lerp(tickDelta, focusedEntity.prevZ, focusedEntity.getZ())
            );
            setPos(camera.getPos().add(resolved.subtract(vanillaEye)));
        } else {
            setPos(resolved);
        }

        // Never roll a headset. The seat above still tracks the body, which is correct in VR and
        // wanted; what must not happen is tilting a horizon the player's neck is not tilting.
        if (ReaperVrCompat.isInVr()) return;

        double strength = ReaperMountClientConfig.cameraRollStrength();
        if (strength <= 0.0) return;

        Vector3f forward = new Vector3f(horizontalPlane).normalize();
        Vector3f currentUp = new Vector3f(verticalPlane).normalize();
        Vector3f wantedUp = new Vector3f((float) bodyUp.x, (float) bodyUp.y, (float) bodyUp.z);
        wantedUp.sub(new Vector3f(forward).mul(wantedUp.dot(forward)));
        if (wantedUp.lengthSquared() < 1.0e-6f) return;
        wantedUp.normalize();

        float cosine = MathHelper.clamp(currentUp.dot(wantedUp), -1.0f, 1.0f);
        float sine = forward.dot(new Vector3f(currentUp).cross(wantedUp));
        float rollAngle = (float) (Math.atan2(sine, cosine) * strength);
        if (Math.abs(rollAngle) < 1.0e-5f) return;

        ReaperCameraRoll.publish(rollAngle);

        Quaternionf roll = new Quaternionf().rotationAxis(rollAngle, forward);
        rotation.premul(roll);
        horizontalPlane.rotate(roll);
        verticalPlane.rotate(roll);
        diagonalPlane.rotate(roll);
    }
}
