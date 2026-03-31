package mystcraft.flood.generation

import mystcraft.flood.generation.profile.AgeProfile
import net.minecraft.world.level.ServerWorldProperties

class AgeWorldProperties(
    delegate: ServerWorldProperties,
    private val profile: AgeProfile
) : ServerWorldProperties by delegate {

    private var localTime = delegate.time
    private var localTimeOfDay = delegate.timeOfDay
    private var timeAccumulator = 0.0f 

    override fun getTime(): Long = localTime
    override fun setTime(time: Long) { localTime = time }

    override fun getTimeOfDay(): Long {
        return profile.time.fixedTime ?: localTimeOfDay
    }
    
    override fun setTimeOfDay(timeOfDay: Long) {
        localTimeOfDay = timeOfDay
    }

    fun tickAgeTime() {
        localTime++
        
        if (profile.time.fixedTime == null) {
            timeAccumulator += profile.time.timeScale
            if (timeAccumulator >= 1.0f || timeAccumulator <= -1.0f) {
                val change = timeAccumulator.toInt()
                localTimeOfDay += change.toLong() // Ensure it's Long
                timeAccumulator -= change.toFloat()
            }
        }
    }
    
    private var localRaining = false
    private var localThundering = false

    override fun isRaining(): Boolean = if (profile.weather.noWeather) false else if (profile.weather.isEndlessRain || profile.weather.isEndlessStorm) true else localRaining
    override fun setRaining(raining: Boolean) { localRaining = raining }

    override fun isThundering(): Boolean = if (profile.weather.noWeather) false else if (profile.weather.isEndlessStorm) true else localThundering
    override fun setThundering(thundering: Boolean) { localThundering = thundering }
}