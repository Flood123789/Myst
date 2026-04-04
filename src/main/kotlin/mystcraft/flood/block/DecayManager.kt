package mystcraft.flood.block

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents

object DecayManager {
    // The maximum number of Decay operations allowed across the ENTIRE server per tick.
    // 100 is a very safe number that won't drop TPS.
    private const val MAX_OPERATIONS_PER_TICK = 100
    
    var operationsThisTick = 0

    fun register() {
        // At the start of every single server tick, reset the counter to 0!
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