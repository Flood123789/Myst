package mystcraft.flood.entity

/**
 * The posture of somebody riding a Paradox Reaper.
 *
 * Vanilla's riding pose was authored for a saddle: knees high, thighs together, hands on reins in
 * front of the chest. A Reaper is a metre of glass lattice with no saddle and no reins, and a
 * rider sitting on one that way looks perched on a chair that happens to be moving. This is the
 * pose for what is actually happening — straddling a wide shell, leaning down over it, hands on
 * the lattice.
 *
 * Kept as plain numbers, free of any rendering type, so the shape can be reasoned about and tested
 * without a model in the room. Angles are radians and follow Minecraft's model conventions:
 * negative limb pitch swings a limb forward, positive body pitch leans the torso forward.
 */
object ReaperRiderPose {

    /**
     * @param bodyPitch forward lean of the torso.
     * @param headPivotY how far the head drops to stay on the leaned torso. Head and body are
     *   siblings in the biped model rather than parent and child, so a leaning torso does not
     *   carry the head with it and the join has to be closed by hand. Vanilla does exactly this
     *   for sneaking, and these follow its ratios so the seams sit where players expect.
     */
    data class Pose(
        val bodyPitch: Float,
        val headPivotY: Float,
        val bodyPivotY: Float,
        val armPivotY: Float,
        val armPitch: Float,
        val armYaw: Float,
        val armRoll: Float,
        val legPitch: Float,
        val legYaw: Float,
        val legRoll: Float,
        val legPivotY: Float,
        val legPivotZ: Float
    )

    /**
     * The pose at a given travel speed.
     *
     * The lean is the only part that moves. A rider ambling along sits up; one at a full sprint
     * folds down over the shell, which is both what a person would do and the readable difference
     * between the creature strolling and the creature hunting.
     *
     * [lesser] narrows the straddle for the half-size form, whose body a rider's legs have much
     * less to get around.
     */
    fun forSpeed(speedBlocksPerTick: Double, lesser: Boolean): Pose {
        val urgency = (speedBlocksPerTick / SPRINT_REFERENCE_SPEED).coerceIn(0.0, 1.0)
        val eased = urgency * urgency * (3.0 - 2.0 * urgency)
        val lean = (REST_LEAN + (SPRINT_LEAN - REST_LEAN) * eased).toFloat()

        val straddle = if (lesser) LESSER_STRADDLE else 1.0

        return Pose(
            bodyPitch = lean,
            // Vanilla's own sneak compensation, scaled: it pairs a 0.5 rad lean with these
            // offsets, so holding the ratio keeps the neck and hips closed at any lean.
            headPivotY = lean * HEAD_DROP_PER_RADIAN,
            bodyPivotY = lean * BODY_DROP_PER_RADIAN,
            armPivotY = ARM_PIVOT_Y + lean * BODY_DROP_PER_RADIAN,
            // Reaching forward and down onto the lattice rather than up onto reins that are not
            // there. Deepens with the lean so the hands stay on the shell as the rider folds.
            armPitch = (ARM_PITCH - lean * ARM_PITCH_PER_LEAN).toFloat(),
            armYaw = ARM_YAW.toFloat(),
            armRoll = ARM_ROLL.toFloat(),
            // Knees out and feet back, gripping the sides. Far less raised than the saddle sit,
            // because the thing between them is wide rather than tall.
            legPitch = LEG_PITCH.toFloat(),
            legYaw = (LEG_YAW * straddle).toFloat(),
            legRoll = (LEG_ROLL * straddle).toFloat(),
            legPivotY = LEG_PIVOT_Y,
            legPivotZ = LEG_PIVOT_Z
        )
    }

    /** Blocks per tick treated as a full sprint for the purpose of the lean. */
    private const val SPRINT_REFERENCE_SPEED = 0.245

    private const val REST_LEAN = 0.22
    private const val SPRINT_LEAN = 0.52

    // Vanilla sneak pairs body.pitch 0.5 with body.pivotY 3.2 and head.pivotY 4.2.
    private const val BODY_DROP_PER_RADIAN = 6.4f
    private const val HEAD_DROP_PER_RADIAN = 8.4f

    /** Default biped arm pivot height, which the lean is added to. */
    private const val ARM_PIVOT_Y = 2.0f

    private const val ARM_PITCH = -0.95
    private const val ARM_PITCH_PER_LEAN = 0.55
    private const val ARM_YAW = 0.16
    private const val ARM_ROLL = 0.13

    private const val LEG_PITCH = -0.85
    private const val LEG_YAW = 0.30
    private const val LEG_ROLL = 0.24
    private const val LEG_PIVOT_Y = 11.4f
    private const val LEG_PIVOT_Z = 1.8f

    /** How much of the straddle a rider needs on the half-size form. */
    private const val LESSER_STRADDLE = 0.55
}
