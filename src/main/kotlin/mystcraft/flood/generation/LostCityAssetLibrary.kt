package mystcraft.flood.generation

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.registry.Registries
import net.minecraft.util.BlockRotation
import net.minecraft.util.Identifier
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.math.abs

object LostCityAssetLibrary {
    private const val ROOT = "data/lostcities/lostcities"

    private val buildingNames = listOf(
        "building1",
        "building2",
        "building3",
        "building4",
        "building5",
        "building6",
        "building7",
        "building8",
        "center00",
        "center01",
        "center10",
        "center11",
        "library00",
        "library01",
        "library10",
        "library11",
        "shopping00",
        "shopping01",
        "shopping10",
        "shopping11",
        "shopping_open00",
        "shopping_open01",
        "shopping_open10",
        "shopping_open11",
        "town00",
        "town01",
        "town10",
        "town11"
    )

    private val brickPalettes = listOf(
        "bricks_standard",
        "bricks_gray",
        "bricks_cyan",
        "bricks_silver",
        "bricks_desert",
        "bricks_desert_orange",
        "bricks_desert_red",
        "bricks_desert_redsand",
        "bricks_border",
        "quartzbricks_border"
    )

    private val glassPalettes = listOf(
        "glass_full",
        "glass_full_blue",
        "glass_full_gray",
        "glass_full_light_blue",
        "glass_full_white",
        "glass_pane",
        "glass_pane_blue",
        "glass_pane_gray",
        "glass_pane_light_blue",
        "glass_pane_white"
    )

    private val sidePalettes = listOf(
        "glass_side_variant_bricks",
        "glass_side_variant_glass",
        "glass_side_variant_quartz",
        "glass_side_variant_street"
    )

    private const val PLACE_CELLARS = false

    private val parts = mutableMapOf<String, LostCityPart>()
    private val buildings = mutableMapOf<String, LostCityBuilding>()
    private val multiBuildings = mutableMapOf<String, LostCityMultiBuilding>()
    private val paletteFiles = mutableMapOf<String, Map<Char, PaletteEntry>>()
    private val blockStates = mutableMapOf<String, BlockState?>()

    private data class LostCityPart(
        val name: String,
        val xSize: Int,
        val zSize: Int,
        val refPalette: String?,
        val inlinePalette: Map<Char, PaletteEntry>,
        val slices: List<List<String>>
    ) {
        val height: Int = slices.size
    }

    private data class LostCityBuilding(
        val name: String,
        val floorParts: List<String>,
        val groundParts: List<String>,
        val topParts: List<String>,
        val cellarParts: List<String>,
        val minFloors: Int,
        val maxFloors: Int,
        val inlinePalette: Map<Char, PaletteEntry>
    )

    private data class LostCityMultiBuilding(
        val name: String,
        val dimX: Int,
        val dimZ: Int,
        val buildings: List<List<String>>
    )

    private data class PaletteEntry(
        val block: String? = null,
        val variant: String? = null,
        val fromPalette: Char? = null,
        val options: List<WeightedBlock> = emptyList()
    )

    private data class WeightedBlock(val block: String, val weight: Int)

    private fun pickBuilding(seed: Long): LostCityBuilding {
        val name = buildingNames[floorMod(seed, buildingNames.size)]
        return building(name)
    }

    fun placeBuilding(
        baseX: Int,
        baseY: Int,
        baseZ: Int,
        floors: Int,
        seed: Long,
        style: Int,
        setBlock: (Int, Int, Int, BlockState) -> Unit
    ): Int {
        return placeBuilding(pickBuilding(seed).name, baseX, baseY, baseZ, floors, seed, style, setBlock)
    }

    fun placeBuilding(
        buildingName: String,
        baseX: Int,
        baseY: Int,
        baseZ: Int,
        floors: Int,
        seed: Long,
        style: Int,
        setBlock: (Int, Int, Int, BlockState) -> Unit
    ): Int {
        val building = building(buildingName)
        var y = baseY

        val cellar = if (PLACE_CELLARS) building.cellarParts.pick(seed xor 0x5ca1e11L) else null
        if (cellar != null) {
            val part = part(cellar)
            placePart(part.name, baseX, baseY - part.height, baseZ, seed, style, building, setBlock)
        }

        val targetFloors = floors.coerceIn(building.minFloors, building.maxFloors)
        val ground = building.groundParts.pick(seed xor 0x67000dL) ?: building.floorParts.pick(seed)
        if (ground != null) {
            placePart(ground, baseX, y, baseZ, seed, style, building, setBlock)
            y += part(ground).height
        }

        val floorPool = if (building.floorParts.isNotEmpty()) building.floorParts else building.groundParts
        val remainingFloors = (targetFloors - 1).coerceAtLeast(0)
        for (floor in 0 until remainingFloors) {
            val floorPart = floorPool.pick(seed + floor * 7919L) ?: break
            placePart(floorPart, baseX, y, baseZ, seed + floor * 1297L, style, building, setBlock)
            y += part(floorPart).height
        }

        val top = building.topParts.pick(seed xor 0x70fL)
        if (top != null) {
            placePart(top, baseX, y, baseZ, seed xor 0x55aaL, style, building, setBlock)
            y += part(top).height
        }

        return y - baseY
    }

    fun multiBuildingPart(name: String, x: Int, z: Int): String? {
        val multi = multiBuilding(name)
        if (x !in 0 until multi.dimX || z !in 0 until multi.dimZ) return null
        return multi.buildings.getOrNull(x)?.getOrNull(z)
    }

    fun placePart(
        partName: String,
        baseX: Int,
        baseY: Int,
        baseZ: Int,
        seed: Long,
        style: Int,
        setBlock: (Int, Int, Int, BlockState) -> Unit,
        rotation: Int = 0,
        voidAsAir: Boolean = false
    ) {
        placePart(partName, baseX, baseY, baseZ, seed, style, null, setBlock, rotation, voidAsAir)
    }

    fun partHeight(partName: String): Int = part(partName).height

    private fun placePart(
        partName: String,
        baseX: Int,
        baseY: Int,
        baseZ: Int,
        seed: Long,
        style: Int,
        building: LostCityBuilding?,
        setBlock: (Int, Int, Int, BlockState) -> Unit,
        rotation: Int = 0,
        voidAsAir: Boolean = false
    ) {
        val part = part(partName)
        val palette = paletteFor(part.refPalette, part.inlinePalette, building, style)
        for (y in part.slices.indices) {
            val slice = part.slices[y]
            for (z in 0 until part.zSize) {
                val row = slice.getOrNull(z) ?: continue
                for (x in 0 until part.xSize) {
                    val ch = row.getOrNull(x) ?: ' '
                    val state = stateFor(ch, palette, seed, x, y, z, style, voidAsAir) ?: continue
                    val (rx, rz) = rotate(x, z, rotation)
                    val rotatedState = state.rotate(rotationFor(rotation))
                    setBlock(baseX + rx, baseY + y, baseZ + rz, rotatedState)
                }
            }
        }
    }

    private fun part(name: String): LostCityPart = parts.getOrPut(name) {
        val json = readJson("$ROOT/parts/$name.json")
        val slices = json.getAsJsonArray("slices").map { sliceElement ->
            sliceElement.asJsonArray.map { it.asString }
        }
        LostCityPart(
            name = name,
            xSize = json.get("xsize")?.asInt ?: 16,
            zSize = json.get("zsize")?.asInt ?: 16,
            refPalette = json.get("refpalette")?.asString,
            inlinePalette = json.getAsJsonObject("palette")?.let(::parsePaletteObject) ?: emptyMap(),
            slices = slices
        )
    }

    private fun building(name: String): LostCityBuilding = buildings.getOrPut(name) {
        val json = readJson("$ROOT/buildings/$name.json")
        val floorParts = mutableListOf<String>()
        val groundParts = mutableListOf<String>()
        val topParts = mutableListOf<String>()
        val cellarParts = mutableListOf<String>()

        val partsArray = json.getAsJsonArray("parts") ?: JsonArray()
        for (element in partsArray) {
            val partObject = element.asJsonObject
            val partName = partObject.get("part")?.asString ?: continue
            when {
                partObject.get("cellar")?.asBoolean == true -> cellarParts += partName
                partObject.get("top")?.asBoolean == true -> topParts += partName
                partObject.get("ground")?.asBoolean == true || partObject.get("floor")?.asInt == 0 -> groundParts += partName
                else -> floorParts += partName
            }
        }

        val interiorParts = json.getAsJsonArray("parts2")
        if (interiorParts != null) {
            for (element in interiorParts) {
                val partName = element.asJsonObject.get("part")?.asString ?: continue
                floorParts += partName
            }
        }

        val inlinePalette = json.getAsJsonObject("palette")?.let(::parsePaletteObject) ?: emptyMap()
        val defaultMax = when {
            name.startsWith("town") -> 4
            name.startsWith("shopping") -> 6
            name.startsWith("center") || name.startsWith("library") -> 8
            else -> 9
        }

        LostCityBuilding(
            name = name,
            floorParts = floorParts,
            groundParts = groundParts,
            topParts = topParts,
            cellarParts = cellarParts,
            minFloors = json.get("minfloors")?.asInt ?: 2,
            maxFloors = json.get("maxfloors")?.asInt ?: defaultMax,
            inlinePalette = inlinePalette
        )
    }

    private fun multiBuilding(name: String): LostCityMultiBuilding = multiBuildings.getOrPut(name) {
        val json = readJson("$ROOT/multibuildings/$name.json")
        val rows = json.getAsJsonArray("buildings")?.map { row ->
            row.asJsonArray.map { it.asString }
        } ?: emptyList()
        LostCityMultiBuilding(
            name = name,
            dimX = json.get("dimx")?.asInt ?: rows.size,
            dimZ = json.get("dimz")?.asInt ?: rows.firstOrNull()?.size ?: 0,
            buildings = rows
        )
    }

    private fun paletteFor(
        refPalette: String?,
        partPalette: Map<Char, PaletteEntry>,
        building: LostCityBuilding?,
        style: Int
    ): Map<Char, PaletteEntry> {
        val palette = linkedMapOf<Char, PaletteEntry>()
        palette.putAll(paletteFile("default"))
        if (floorMod(style.toLong(), 8) in 4..6) {
            palette.putAll(paletteFile("default_desert"))
        }
        palette.putAll(paletteFile("common"))
        palette.putAll(paletteFile(brickPalettes[floorMod(style.toLong(), brickPalettes.size)]))
        palette.putAll(paletteFile(glassPalettes[floorMod((style + 1).toLong(), glassPalettes.size)]))
        palette.putAll(paletteFile(sidePalettes[floorMod((style + 2).toLong(), sidePalettes.size)]))
        if (refPalette != null) {
            palette.putAll(paletteFile(refPalette))
        }
        palette.putAll(partPalette)
        if (building != null) {
            palette.putAll(building.inlinePalette)
        }
        return palette
    }

    private fun paletteFile(name: String): Map<Char, PaletteEntry> = paletteFiles.getOrPut(name) {
        val json = readJson("$ROOT/palettes/$name.json")
        parsePaletteObject(json)
    }

    private fun parsePaletteObject(json: JsonObject): Map<Char, PaletteEntry> {
        val result = linkedMapOf<Char, PaletteEntry>()
        val palette = json.getAsJsonArray("palette") ?: return result
        for (element in palette) {
            val entry = element.asJsonObject
            val ch = entry.get("char")?.asString?.firstOrNull() ?: continue
            val options = entry.getAsJsonArray("blocks")?.mapNotNull { blockElement ->
                val blockObject = blockElement.asJsonObject
                val block = blockObject.get("block")?.asString ?: return@mapNotNull null
                WeightedBlock(block, blockObject.get("random")?.asInt ?: 1)
            } ?: emptyList()
            result[ch] = PaletteEntry(
                block = entry.get("block")?.asString,
                variant = entry.get("variant")?.asString,
                fromPalette = entry.get("frompalette")?.asString?.firstOrNull(),
                options = options
            )
        }
        return result
    }

    private fun stateFor(
        ch: Char,
        palette: Map<Char, PaletteEntry>,
        seed: Long,
        x: Int,
        y: Int,
        z: Int,
        style: Int,
        voidAsAir: Boolean,
        depth: Int = 0
    ): BlockState? {
        if ((ch == '~' || ch == 'b') && !voidAsAir) return null
        if (ch == '~' || ch == 'b') return Blocks.AIR.defaultState
        val entry = palette[ch] ?: return fallbackState(ch, style)
        if (entry.fromPalette != null && depth < 8) {
            return stateFor(entry.fromPalette, palette, seed, x, y, z, style, voidAsAir, depth + 1)
        }
        if (entry.options.isNotEmpty()) {
            val option = pickWeighted(entry.options, seed + x * 7349L + y * 9151L + z * 5801L)
            return parseBlockState(option.block)
        }
        if (entry.variant != null) {
            return variantState(entry.variant, seed + x * 31L + y * 17L + z * 13L, style)
        }
        if (entry.block != null) {
            return parseBlockState(entry.block)
        }
        return fallbackState(ch, style)
    }

    private fun pickWeighted(options: List<WeightedBlock>, seed: Long): WeightedBlock {
        val total = options.sumOf { it.weight.coerceAtLeast(1) }.coerceAtLeast(1)
        var value = floorMod(mix(seed), total)
        for (option in options) {
            value -= option.weight.coerceAtLeast(1)
            if (value < 0) return option
        }
        return options.last()
    }

    private fun parseBlockState(value: String): BlockState? = blockStates.getOrPut(value) {
        val normalized = normalizeLegacyBlockState(value)
        if (normalized == "minecraft:structure_void") return@getOrPut null
        val blockId = normalized.substringBefore('[')
        val block = Registries.BLOCK.getOrEmpty(Identifier(blockId)).orElse(Blocks.AIR)
        if (block == Blocks.AIR && blockId != "minecraft:air") {
            return@getOrPut fallbackState('#', 0)
        }
        var state = block.defaultState
        val propertyText = normalized.substringAfter('[', "").substringBeforeLast(']', "")
        if (propertyText.isNotBlank()) {
            for (assignment in propertyText.split(',')) {
                val propertyName = assignment.substringBefore('=').trim()
                val propertyValue = assignment.substringAfter('=', "").trim()
                val property = state.properties.firstOrNull { it.name == propertyName } ?: continue
                state = BlockStatePropertyBridge.withParsed(state, property, propertyValue)
            }
        }
        state
    }

    private fun normalizeLegacyBlockState(value: String): String {
        if ('@' !in value) return value
        val block = value.substringBefore('@')
        val meta = value.substringAfter('@').substringBefore('[').toIntOrNull() ?: return block
        val propertyText = value.substringAfter('[', "").substringBeforeLast(']', "")
        val normalizedBlock = when (block) {
            "minecraft:red_sandstone" -> when (meta) {
                1 -> "minecraft:chiseled_red_sandstone"
                2 -> "minecraft:smooth_red_sandstone"
                else -> "minecraft:red_sandstone"
            }
            "minecraft:sandstone" -> when (meta) {
                1 -> "minecraft:chiseled_sandstone"
                2 -> "minecraft:smooth_sandstone"
                else -> "minecraft:sandstone"
            }
            "minecraft:stonebrick", "minecraft:stone_bricks" -> when (meta) {
                1 -> "minecraft:mossy_stone_bricks"
                2 -> "minecraft:cracked_stone_bricks"
                3 -> "minecraft:chiseled_stone_bricks"
                else -> "minecraft:stone_bricks"
            }
            else -> block
        }
        return if (propertyText.isBlank()) normalizedBlock else "$normalizedBlock[$propertyText]"
    }

    private fun variantState(variant: String, seed: Long, style: Int): BlockState {
        return when (variant) {
            "stonebrick" -> Blocks.STONE_BRICKS.defaultState
            "stonebrick_rubble" -> rubble(seed, Blocks.CRACKED_STONE_BRICKS.defaultState, Blocks.MOSSY_STONE_BRICKS.defaultState)
            "bricks" -> Blocks.BRICKS.defaultState
            "bricks_rubble" -> rubble(seed, Blocks.BRICKS.defaultState, Blocks.MOSSY_COBBLESTONE.defaultState)
            "quartz" -> Blocks.QUARTZ_BLOCK.defaultState
            "quartz_rubble" -> rubble(seed, Blocks.CHISELED_QUARTZ_BLOCK.defaultState, Blocks.SMOOTH_QUARTZ.defaultState)
            "deepslate" -> Blocks.DEEPSLATE_BRICKS.defaultState
            "deepslate_rubble" -> rubble(seed, Blocks.CRACKED_DEEPSLATE_BRICKS.defaultState, Blocks.COBBLED_DEEPSLATE.defaultState)
            "blackstone" -> Blocks.POLISHED_BLACKSTONE_BRICKS.defaultState
            "blackstone_rubble" -> rubble(seed, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultState, Blocks.BLACKSTONE.defaultState)
            "stoneandbrick" -> if (style and 1 == 0) Blocks.STONE_BRICKS.defaultState else Blocks.BRICKS.defaultState
            "stoneandbrick_rubble" -> rubble(seed, Blocks.CRACKED_STONE_BRICKS.defaultState, Blocks.MOSSY_COBBLESTONE.defaultState)
            else -> Blocks.STONE_BRICKS.defaultState
        }
    }

    private fun rubble(seed: Long, first: BlockState, second: BlockState): BlockState {
        return if (floorMod(mix(seed), 4) == 0) second else first
    }

    private fun fallbackState(ch: Char, style: Int): BlockState? {
        return when (ch) {
            ' ' -> Blocks.AIR.defaultState
            '#', '@', 'v', 'x', 'y' -> Blocks.STONE_BRICKS.defaultState
            '}', 'X' -> Blocks.CRACKED_STONE_BRICKS.defaultState
            'a', '+', '`', 'Z' -> when (floorMod(style.toLong(), 4)) {
                0 -> Blocks.BLUE_STAINED_GLASS.defaultState
                1 -> Blocks.GRAY_STAINED_GLASS.defaultState
                2 -> Blocks.LIGHT_BLUE_STAINED_GLASS.defaultState
                else -> Blocks.WHITE_STAINED_GLASS.defaultState
            }
            'R' -> Blocks.CYAN_TERRACOTTA.defaultState
            'Q' -> Blocks.QUARTZ_BLOCK.defaultState
            'S', 'u' -> Blocks.SMOOTH_STONE.defaultState
            'w' -> Blocks.COBBLESTONE_WALL.defaultState
            ':' -> Blocks.IRON_BARS.defaultState
            'h' -> Blocks.GLOWSTONE.defaultState
            'g' -> Blocks.REDSTONE_TORCH.defaultState
            'G' -> Blocks.GRASS_BLOCK.defaultState
            'D' -> Blocks.DIRT.defaultState
            else -> Blocks.STONE_BRICKS.defaultState
        }
    }

    private fun readJson(path: String): JsonObject {
        val stream = LostCityAssetLibrary::class.java.classLoader.getResourceAsStream(path)
            ?: error("Missing Lost Cities asset: $path")
        return InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
            JsonParser.parseReader(reader).asJsonObject
        }
    }

    private fun <T> List<T>.pick(seed: Long): T? {
        if (isEmpty()) return null
        return this[floorMod(seed, size)]
    }

    private fun rotate(x: Int, z: Int, rotation: Int): Pair<Int, Int> {
        return when (floorMod(rotation.toLong(), 4)) {
            1 -> 15 - z to x
            2 -> 15 - x to 15 - z
            3 -> z to 15 - x
            else -> x to z
        }
    }

    private fun rotationFor(rotation: Int): BlockRotation {
        return when (floorMod(rotation.toLong(), 4)) {
            1 -> BlockRotation.CLOCKWISE_90
            2 -> BlockRotation.CLOCKWISE_180
            3 -> BlockRotation.COUNTERCLOCKWISE_90
            else -> BlockRotation.NONE
        }
    }

    private fun floorMod(value: Long, mod: Int): Int = Math.floorMod(value, mod.toLong()).toInt()

    private fun mix(seed: Long): Long {
        var value = seed
        value = value xor (value ushr 33)
        value *= -49064778989728563L
        value = value xor (value ushr 33)
        value *= -4265267296055464877L
        value = value xor (value ushr 33)
        return abs(value)
    }
}
