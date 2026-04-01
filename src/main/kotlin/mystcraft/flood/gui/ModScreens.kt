package mystcraft.flood.gui

import mystcraft.flood.MystcraftReforged
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.resource.featuretoggle.FeatureFlags
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.util.Identifier

object ModScreens {
    val BOOK_BINDER_HANDLER: ScreenHandlerType<BookBinderScreenHandler> = Registry.register(
        Registries.SCREEN_HANDLER,
        Identifier(MystcraftReforged.MOD_ID, "book_binder"),
        ScreenHandlerType(::BookBinderScreenHandler, FeatureFlags.VANILLA_FEATURES)
    )

    fun register() {
        MystcraftReforged.LOGGER.info("Registering Screen Handlers for ${MystcraftReforged.MOD_ID}")
    }
}