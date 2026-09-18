package mystcraft.flood.entity

/**
 * The four things a Paradox Reaper can be doing about a player.
 *
 * Order matters only for the tracked byte written to clients; the transitions are explicit.
 */
enum class ReaperState {
    /** Anchored and listening. Cubes are still inside the body, and vision is poor and rare. */
    DORMANT,

    /** Has the player in sight and is running them down. */
    PURSUIT,

    /** Lost sight, or heard something. Searching around the last place it knew about. */
    HUNTING,

    /** Given up the search, but still on edge while it finds somewhere to go dormant again. */
    SETTLING;

    val isAlerted: Boolean get() = this != DORMANT
}

/**
 * The alertness state machine, kept free of world and entity types so it can be tested directly.
 *
 * The shape of it is deliberately Warden-like: a creature that mostly listens, that a careful
 * player can slip past, and that once roused stays roused long enough to be frightening. Losing
 * a Reaper is a matter of breaking line of sight and then staying hidden and quiet — not of
 * outrunning it, which is impossible by design.
 */
class ReaperAwareness {

    var state: ReaperState = ReaperState.DORMANT
        private set

    /** Ticks since the target was last seen. Drives the exit from [ReaperState.HUNTING]. */
    var ticksSinceSeen: Int = 0
        private set

    /** Ticks left on edge before dropping back to [ReaperState.DORMANT]. */
    var settleTicksRemaining: Int = 0
        private set

    /** True on the tick the creature first becomes alerted, so callers can scream once. */
    var justAlerted: Boolean = false
        private set

    /** Sight beats everything: the creature knows exactly where the player is. */
    fun onSighted() {
        justAlerted = !state.isAlerted
        state = ReaperState.PURSUIT
        ticksSinceSeen = 0
        settleTicksRemaining = 0
    }

    /**
     * Heard something, or was told about it by another Reaper. This raises a search rather than
     * a chase: the creature knows a position, not a target.
     */
    fun onDisturbed() {
        if (state == ReaperState.PURSUIT) {
            // Already chasing; a noise only renews the search timer.
            ticksSinceSeen = 0
            return
        }
        justAlerted = !state.isAlerted
        state = ReaperState.HUNTING
        ticksSinceSeen = 0
        settleTicksRemaining = 0
    }

    /**
     * Advances one tick. [canSeeTarget] is the result of this tick's line-of-sight check.
     *
     * A Reaper only stands down after [SEARCH_TICKS] unbroken ticks without sight, and even then
     * spends [SETTLE_TICKS] wound up, so a player who breaks line of sight cannot immediately
     * start making noise again.
     */
    fun tick(canSeeTarget: Boolean) {
        justAlerted = false

        if (canSeeTarget) {
            onSighted()
            return
        }

        when (state) {
            ReaperState.DORMANT -> Unit

            ReaperState.PURSUIT -> {
                // Lost them this tick; drop into a search rather than standing down outright.
                state = ReaperState.HUNTING
                ticksSinceSeen = 1
            }

            ReaperState.HUNTING -> {
                ticksSinceSeen++
                if (ticksSinceSeen >= SEARCH_TICKS) {
                    state = ReaperState.SETTLING
                    settleTicksRemaining = SETTLE_TICKS
                    ticksSinceSeen = 0
                }
            }

            ReaperState.SETTLING -> {
                settleTicksRemaining--
                if (settleTicksRemaining <= 0) {
                    state = ReaperState.DORMANT
                    settleTicksRemaining = 0
                }
            }
        }
    }

    /** Restores a state loaded from disk without replaying the transitions that produced it. */
    fun restore(state: ReaperState, ticksSinceSeen: Int, settleTicksRemaining: Int) {
        this.state = state
        this.ticksSinceSeen = ticksSinceSeen.coerceIn(0, SEARCH_TICKS)
        this.settleTicksRemaining = settleTicksRemaining.coerceIn(0, SETTLE_TICKS)
    }

    companion object {
        /** 15 seconds out of sight before a Reaper gives up the search. */
        const val SEARCH_TICKS = 15 * 20

        /** 60 seconds of heightened sensitivity after the search ends. */
        const val SETTLE_TICKS = 60 * 20
    }
}
