package mystcraft.flood.generation

import mystcraft.flood.generation.profile.AgeProfile
import net.minecraft.server.world.ServerWorld
import kotlin.random.Random

object AgeWeatherController {
    fun tick(world: ServerWorld, profile: AgeProfile): Boolean {
        val weather = profile.weather
        val beforeRaining = weather.currentRaining
        val beforeThundering = weather.currentThundering

        if (weather.temporaryClearTicks > 0) {
            weather.temporaryClearTicks--
            weather.currentRaining = false
            weather.currentThundering = false
            weather.rainTicks = 0
            weather.thunderTicks = 0
            return beforeRaining || beforeThundering || weather.temporaryClearTicks == 0
        }

        when {
            weather.noWeather -> {
                weather.currentRaining = false
                weather.currentThundering = false
                weather.clearTicks = 12000
                weather.rainTicks = 0
                weather.thunderTicks = 0
            }

            weather.isEndlessStorm -> {
                weather.currentRaining = true
                weather.currentThundering = true
                weather.clearTicks = 0
                weather.rainTicks = Int.MAX_VALUE
                weather.thunderTicks = Int.MAX_VALUE
            }

            weather.isEndlessRain -> {
                weather.currentRaining = true
                weather.currentThundering = false
                weather.clearTicks = 0
                weather.rainTicks = Int.MAX_VALUE
                weather.thunderTicks = 0
            }

            else -> tickNormalWeather(world, profile)
        }

        return beforeRaining != weather.currentRaining || beforeThundering != weather.currentThundering
    }

    private fun tickNormalWeather(world: ServerWorld, profile: AgeProfile) {
        val weather = profile.weather
        val random = Random(profile.seed xor world.time)

        if (weather.clearTicks <= 0 && weather.rainTicks <= 0 && weather.thunderTicks <= 0) {
            weather.clearTicks = random.nextInt(6000, 18000)
            weather.currentRaining = false
            weather.currentThundering = false
        }

        if (weather.clearTicks > 0) {
            weather.clearTicks--
            weather.currentRaining = false
            weather.currentThundering = false
            if (weather.clearTicks == 0) {
                weather.currentRaining = true
                weather.rainTicks = random.nextInt(6000, 18000)
                weather.thunderTicks = random.nextInt(3000, 9000)
            }
            return
        }

        if (!weather.currentRaining) {
            weather.currentRaining = true
            weather.rainTicks = random.nextInt(6000, 18000)
            weather.thunderTicks = random.nextInt(3000, 9000)
        }

        if (weather.rainTicks > 0) {
            weather.rainTicks--
        }

        if (weather.thunderTicks > 0) {
            weather.thunderTicks--
        }

        if (weather.rainTicks <= 0) {
            weather.currentRaining = false
            weather.currentThundering = false
            weather.clearTicks = random.nextInt(9000, 24000)
            weather.thunderTicks = 0
            return
        }

        if (weather.currentThundering) {
            if (weather.thunderTicks <= 0) {
                weather.currentThundering = false
                weather.thunderTicks = random.nextInt(4000, 12000)
            }
        } else if (weather.thunderTicks <= 0 && random.nextFloat() < 0.35f) {
            weather.currentThundering = true
            weather.thunderTicks = random.nextInt(2000, 6000)
        } else if (weather.thunderTicks <= 0) {
            weather.thunderTicks = random.nextInt(4000, 12000)
        }
    }
}
