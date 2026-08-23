package mystcraft.flood.block

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents

/**
 * Server-wide work budget shared by every spreading decay block.
 *
 * Decay implementations must acquire permission before changing blocks. A global (rather than
 * per-Age) budget prevents several unstable Ages from multiplying worst-case tick cost.
 */
object DecayManager {
    // One operation is one unit requested by decay logic; expensive shapes can request more.
    private const val MAX_OPERATIONS_PER_TICK = 100
    
    var operationsThisTick = 0

    fun register() {
        // Reset before worlds tick so all dimensions compete within one server-tick budget.
        ServerTickEvents.START_SERVER_TICK.register {
            operationsThisTick = 0
        }
    }

    /**
     * Decay blocks will ask this function for permission to act.
     * If the limit is reached, it returns false, forcing the block to wait.
     */
    fun requestPermission(weight: Int = 1): Boolean {
        if (operationsThisTick + weight <= MAX_OPERATIONS_PER_TICK) {
            operationsThisTick += weight
            return true
        }
        return false
    }
}
