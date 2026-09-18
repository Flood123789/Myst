package mystcraft.flood.entity

import mystcraft.flood.config.MystcraftConfig
import net.minecraft.block.Block
import net.minecraft.entity.ai.goal.Goal
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.world.GameRules
import java.util.EnumSet

/**
 * Lets a hunting Reaper shatter thin glass that stands between it and its target.
 *
 * Membership is a block tag rather than a class check so packs can extend it, and so the
 * distinction the tag encodes stays explicit: panes are thin enough to break through, full glass
 * blocks are not, and iron bars are not glass at all.
 *
 * A Reaper only breaks in where its whole body fits. A lesser form needs a single pane; a greater
 * form needs a genuine 2x2 window and will not chew a bigger hole out of a smaller one. That is
 * the load-bearing rule for base defence: a one-pane window keeps the greater form out entirely,
 * but does nothing about the lesser forms it screeches for.
 *
 * The goal owns movement while a breach is underway. Once the final pane breaks it retains a
 * point on the far side and crosses the opening before ordinary hunting can choose another move.
 */
class ParadoxReaperBreakGlassGoal(private val reaper: ParadoxReaperEntity) : Goal() {

    /** Every pane in the opening being torn out, so a 2x2 is finished once started. */
    private var opening: List<BlockPos> = emptyList()
    private var current: BlockPos? = null
    private var entryTarget: Vec3d? = null
    private var entryDirection: Vec3d? = null
    private var entering = false
    private var entryTicks = 0
    private var progressTicks = 0

    init {
        controls = EnumSet.of(Control.MOVE)
    }

    override fun canStart(): Boolean {
        if (!MystcraftConfig.current.paradoxReaper.canBreakGlassPanes) return false
        val focus = reaper.glassBreakFocus() ?: return false
        if (!reaper.world.gameRules.getBoolean(GameRules.DO_MOB_GRIEFING)) return false

        val found = findOpening(focus) ?: return false
        opening = found.blocks
        entryTarget = found.entryTarget
        entryDirection = found.entryDirection
        current = opening.firstOrNull { isBreakable(it) }
        return current != null
    }

    override fun shouldContinue(): Boolean {
        if (entering) {
            val destination = entryTarget ?: return false
            val direction = entryDirection ?: return false
            return entryTicks < ENTRY_TIMEOUT_TICKS &&
                destination.subtract(reaper.pos).dotProduct(direction) > ENTRY_REMAINING_DISTANCE
        }
        if (reaper.glassBreakFocus() == null) return false
        val pane = current ?: return false
        return isBreakable(pane) && reaper.squaredDistanceTo(
            pane.x + 0.5, pane.y + 0.5, pane.z + 0.5
        ) <= REACH_SQUARED
    }

    override fun start() {
        progressTicks = 0
        entryTicks = 0
        entering = false
    }

    override fun stop() {
        // Clearing the crack overlay matters: the client keeps drawing the last stage forever if
        // the goal is interrupted, leaving damaged-looking glass that is actually intact.
        current?.let { reaper.world.setBlockBreakingInfo(reaper.id, it, -1) }
        reaper.setDesiredMove(Vec3d.ZERO)
        opening = emptyList()
        current = null
        entryTarget = null
        entryDirection = null
        entering = false
        entryTicks = 0
        progressTicks = 0
    }

    override fun shouldRunEveryTick(): Boolean = true

    override fun tick() {
        if (entering) {
            entryTicks++
            val destination = entryTarget ?: return
            reaper.setDesiredMove(destination.subtract(reaper.pos))
            return
        }

        val pane = current ?: return
        entryTarget?.let { reaper.setDesiredMove(it.subtract(reaper.pos)) }
        val duration = MystcraftConfig.current.paradoxReaper.paneBreakTicks
        progressTicks++

        if (progressTicks % 4 == 0) {
            reaper.world.playSound(
                null, pane, SoundEvents.BLOCK_GLASS_HIT, SoundCategory.HOSTILE, 0.7f, 0.6f
            )
        }

        // Vanilla's crack overlay runs 0..9; anything outside that range clears it.
        val stage = (progressTicks * 10 / duration.coerceAtLeast(1)).coerceIn(0, 9)
        reaper.world.setBlockBreakingInfo(reaper.id, pane, stage)

        if (progressTicks < duration) return

        reaper.world.setBlockBreakingInfo(reaper.id, pane, -1)
        reaper.world.breakBlock(pane, false, reaper)
        reaper.world.playSound(
            null, pane, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 1.0f, 0.8f
        )

        // Move straight on to the next pane of the same opening rather than re-searching, so a
        // greater form always finishes the hole it committed to.
        progressTicks = 0
        current = opening.firstOrNull { isBreakable(it) }
        if (current == null) entering = true
    }

    /**
     * Finds a square of breakable panes, as wide and tall as this Reaper needs, in the direction
     * of its target. Returns null when no opening of the required size exists, which is what
     * stops a greater form from forcing a window that is too small for it.
     */
    private fun findOpening(focus: Vec3d): BreachPlan? {
        val heading = focus.subtract(reaper.pos)
        if (heading.lengthSquared() < 1.0e-6) return null

        val step = SurfaceCling.dominantFace(heading)
        val size = reaper.requiredOpening()
        val vertical = step.axis.isVertical

        // Plane spanned by the window. Approaching horizontally, the opening rises from the
        // Reaper's own level; approaching vertically, it can shift on both horizontal axes.
        val axisA = if (vertical) Direction.EAST else rotateHorizontally(step)
        val axisB = if (vertical) Direction.NORTH else Direction.UP

        val shiftA = -(size - 1)..0
        val shiftB = if (vertical) -(size - 1)..0 else 0..0
        val origin = reaper.blockPos

        for (distance in 1..PROBE_BLOCKS) {
            val ahead = origin.offset(step, distance)
            for (a0 in shiftA) {
                for (b0 in shiftB) {
                    val group = collectSquare(ahead, axisA, axisB, a0, b0, size) ?: continue
                    val center = group.fold(Vec3d.ZERO) { sum, pos ->
                        sum.add(Vec3d.ofCenter(pos))
                    }.multiply(1.0 / group.size)
                    val entryDirection = Vec3d.of(step.vector)
                    return BreachPlan(
                        group,
                        center.add(entryDirection.multiply(ENTRY_DEPTH)),
                        entryDirection
                    )
                }
            }
        }
        return null
    }

    /** The size x size square anchored at the given offsets, or null if any cell is not glass. */
    private fun collectSquare(
        ahead: BlockPos,
        axisA: Direction,
        axisB: Direction,
        a0: Int,
        b0: Int,
        size: Int
    ): List<BlockPos>? {
        val group = ArrayList<BlockPos>(size * size)
        for (da in 0 until size) {
            for (db in 0 until size) {
                val pos = ahead.offset(axisA, a0 + da).offset(axisB, b0 + db)
                if (!isBreakable(pos)) return null
                group.add(pos)
            }
        }
        return group
    }

    private fun rotateHorizontally(face: Direction): Direction =
        if (face.axis == Direction.Axis.Z) Direction.EAST else Direction.NORTH

    private fun isBreakable(pos: BlockPos): Boolean =
        reaper.world.getBlockState(pos).isIn(BREAKABLE)

    private data class BreachPlan(
        val blocks: List<BlockPos>,
        val entryTarget: Vec3d,
        val entryDirection: Vec3d
    )

    companion object {
        /** Ships as the vanilla glass panes; extendable by datapack. */
        val BREAKABLE: TagKey<Block> = TagKey.of(
            RegistryKeys.BLOCK,
            Identifier("mystcraft-reforged", "reaper_breakable")
        )

        private const val PROBE_BLOCKS = 2
        private const val REACH_SQUARED = 16.0
        private const val ENTRY_DEPTH = 1.35
        private const val ENTRY_REMAINING_DISTANCE = 0.25
        private const val ENTRY_TIMEOUT_TICKS = 60
    }
}
