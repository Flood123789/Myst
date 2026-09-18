package mystcraft.flood.mixin.client;

import mystcraft.flood.entity.ParadoxReaperEntity;
import mystcraft.flood.entity.ReaperRiderPose;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Poses a Reaper's rider for the creature they are actually on.
 *
 * Vanilla's riding pose was authored for a saddle: knees high, thighs together, hands forward on
 * reins. A Reaper is a metre of glass lattice with neither, and a rider sat on one that way looks
 * perched on a chair that happens to be moving. This straddles the shell instead — knees out, feet
 * back, torso folded down, hands on the lattice — and deepens the fold as the creature speeds up.
 *
 * <h2>Why the pose is applied here</h2>
 *
 * Everything attached to a player is positioned from these same {@link ModelPart}s, so posing the
 * parts is what makes the rest follow rather than detach:
 *
 * <ul>
 *   <li>The skin's second layer — sleeves, trousers, jacket, hat — is copied from these parts by
 *       {@code PlayerEntityModel.setAngles} immediately after the call this injects into, so the
 *       tail of {@code BipedEntityModel.setAngles} is deliberately upstream of that copy.</li>
 *   <li>Armour models never have {@code setAngles} called on them at all; {@code ArmorFeatureRenderer}
 *       copies the context model's angles across with {@code setAttributes}, so armour inherits
 *       this pose for free, and so does any modded layer following the same convention.</li>
 *   <li>Held items are placed by {@code setArmAngle}, which reads the arm part directly.</li>
 *   <li>Capes and elytra are positioned from the torso.</li>
 * </ul>
 *
 * A mod that replaces the player with a model that is not a biped at all is beyond reach — there
 * are no arms and legs to pose — but it keeps whatever pose it would otherwise have had, and the
 * seat and orientation from {@link ReaperRiderOrientationMixin} still apply to it.
 *
 * <h2>What is deliberately left alone</h2>
 *
 * The head keeps the aim vanilla gave it. Head and body are siblings in the biped model rather
 * than parent and child, so leaning the torso does not drag the head with it; the neck join is
 * closed by moving the pivots instead, on the same ratios vanilla uses for sneaking.
 *
 * The arms are only taken over when they are empty or simply holding something. Drawing a bow,
 * levelling a crossbow, raising a shield or a spyglass all say something the player needs to see,
 * and are worth more than a tidy grip.
 */
@Mixin(BipedEntityModel.class)
public abstract class ReaperRiderPoseMixin {

    @Shadow public ModelPart head;
    @Shadow public ModelPart body;
    @Shadow public ModelPart rightArm;
    @Shadow public ModelPart leftArm;
    @Shadow public ModelPart rightLeg;
    @Shadow public ModelPart leftLeg;
    @Shadow public BipedEntityModel.ArmPose leftArmPose;
    @Shadow public BipedEntityModel.ArmPose rightArmPose;

    @Inject(method = "setAngles", at = @At("TAIL"))
    private void mystcraft$poseReaperRider(
        LivingEntity entity,
        float limbAngle,
        float limbDistance,
        float animationProgress,
        float headYaw,
        float headPitch,
        CallbackInfo ci
    ) {
        if (!(entity.getVehicle() instanceof ParadoxReaperEntity reaper)) return;

        double travelled = Math.sqrt(
            square(reaper.getX() - reaper.prevX)
                + square(reaper.getY() - reaper.prevY)
                + square(reaper.getZ() - reaper.prevZ)
        );
        ReaperRiderPose.Pose pose = ReaperRiderPose.INSTANCE.forSpeed(travelled, reaper.isLesser());

        body.pitch = pose.getBodyPitch();
        body.pivotY = pose.getBodyPivotY();
        head.pivotY = pose.getHeadPivotY();

        // Right and left mirror through yaw and roll; pitch is shared.
        rightLeg.pitch = pose.getLegPitch();
        rightLeg.yaw = pose.getLegYaw();
        rightLeg.roll = pose.getLegRoll();
        rightLeg.pivotY = pose.getLegPivotY();
        rightLeg.pivotZ = pose.getLegPivotZ();

        leftLeg.pitch = pose.getLegPitch();
        leftLeg.yaw = -pose.getLegYaw();
        leftLeg.roll = -pose.getLegRoll();
        leftLeg.pivotY = pose.getLegPivotY();
        leftLeg.pivotZ = pose.getLegPivotZ();

        if (mystcraft$armIsFree(rightArmPose)) {
            rightArm.pitch = pose.getArmPitch();
            rightArm.yaw = pose.getArmYaw();
            rightArm.roll = pose.getArmRoll();
        }
        rightArm.pivotY = pose.getArmPivotY();

        if (mystcraft$armIsFree(leftArmPose)) {
            leftArm.pitch = pose.getArmPitch();
            leftArm.yaw = -pose.getArmYaw();
            leftArm.roll = -pose.getArmRoll();
        }
        leftArm.pivotY = pose.getArmPivotY();
    }

    /** True when an arm is not busy saying something the player needs to be able to read. */
    private boolean mystcraft$armIsFree(BipedEntityModel.ArmPose pose) {
        return pose == BipedEntityModel.ArmPose.EMPTY || pose == BipedEntityModel.ArmPose.ITEM;
    }

    private static double square(double value) {
        return value * value;
    }
}
