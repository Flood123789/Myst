package mystcraft.flood.config

import net.minecraft.util.Identifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MystcraftBalanceConfigTest {
    @Test
    fun `defaults match the balanced age bands`() {
        val config = MystcraftBalanceConfig()
        assertEquals(25, config.instability.overworldFunctionsMaxScore)
        assertEquals(listOf(45, 65, 80, 100, 115), config.instability.run {
            listOf(mildThreshold, moderateThreshold, severeThreshold, worldEaterThreshold, criticalThreshold)
        })
        assertEquals(24, config.worldGeneration.caveGlowLichenAttemptsPerChunk)
    }

    @Test
    fun `normalization clamps rates and keeps thresholds strictly ordered`() {
        val normalized = InstabilityBalance(
            overworldFunctionsMaxScore = 999,
            mildThreshold = 10,
            moderateThreshold = 5,
            severeThreshold = 5,
            worldEaterThreshold = 5,
            criticalThreshold = 5,
            mildChancePerSecond = -1f,
            moderateChancePerSecond = 2f
        ).normalized()

        assertEquals(listOf(10, 11, 12, 13, 14), normalized.run {
            listOf(mildThreshold, moderateThreshold, severeThreshold, worldEaterThreshold, criticalThreshold)
        })
        assertEquals(9, normalized.overworldFunctionsMaxScore)
        assertEquals(0f, normalized.mildChancePerSecond)
        assertEquals(1f, normalized.moderateChancePerSecond)
    }

    @Test
    fun `page pool blacklist accepts namespaces full ids and paths`() {
        val pools = PagePoolBalance(
            blacklistedBiomeNamespaces = mutableListOf(" BETTERNETHER "),
            blacklistedBiomePages = mutableListOf("minecraft:deep_dark"),
            blacklistedTerrainPages = mutableListOf("terrain_void"),
            blacklistedSymbolPages = mutableListOf("mystcraft-reforged:dense_ores")
        ).normalized()

        assertFalse(pools.allowsBiome(Identifier("betternether", "nether_jungle")))
        assertFalse(pools.allowsBiome(Identifier("minecraft", "deep_dark")))
        assertTrue(pools.allowsBiome(Identifier("minecraft", "plains")))
        assertFalse(pools.allowsTerrainSymbol(Identifier("mystcraft-reforged", "terrain_void")))
        assertFalse(pools.allowsSymbol(Identifier("mystcraft-reforged", "dense_ores"), false))
    }
}
