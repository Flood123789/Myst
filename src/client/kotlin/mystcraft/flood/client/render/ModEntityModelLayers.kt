package mystcraft.flood.client.render

import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry
import net.minecraft.client.render.entity.model.EntityModelLayer
import net.minecraft.util.Identifier

object ModEntityModelLayers {
    val BOOKSTAND = EntityModelLayer(Identifier("mystcraft-reforged", "bookstand"), "main")
    val PARADOX_REAPER = EntityModelLayer(Identifier("mystcraft-reforged", "paradox_reaper"), "main")

    fun register() {
        EntityModelLayerRegistry.registerModelLayer(BOOKSTAND) {
            BookstandModel.getTexturedModelData()
        }
        EntityModelLayerRegistry.registerModelLayer(PARADOX_REAPER) {
            ParadoxReaperModel.getTexturedModelData()
        }
    }
}
