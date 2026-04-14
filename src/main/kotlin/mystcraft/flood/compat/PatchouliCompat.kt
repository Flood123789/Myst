package mystcraft.flood.compat

import mystcraft.flood.item.ModItems
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.item.ItemStack
import net.minecraft.util.Identifier

object PatchouliCompat {
    private const val PATCHOULI_MOD_ID = "patchouli"
    val GUIDE_BOOK_ID: Identifier = Identifier("mystcraft-reforged", "mystcraft_guide")

    fun isAvailable(): Boolean = FabricLoader.getInstance().isModLoaded(PATCHOULI_MOD_ID)

    fun openGuideGui(): Boolean {
        if (!isAvailable()) return false

        return try {
            val apiClass = Class.forName("vazkii.patchouli.api.PatchouliAPI")
            val api = apiClass.getMethod("get").invoke(null)
            val idMethod = api.javaClass.methods.firstOrNull { method ->
                method.name == "openBookGUI" &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == Identifier::class.java
            }

            if (idMethod != null) {
                idMethod.invoke(api, GUIDE_BOOK_ID)
                return true
            }

            val stackMethod = api.javaClass.methods.firstOrNull { method ->
                method.name == "openBookGUI" &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == ItemStack::class.java
            } ?: return false

            stackMethod.invoke(api, ItemStack(ModItems.GUIDE_BOOK))
            true
        } catch (_: Exception) {
            false
        }
    }
}
