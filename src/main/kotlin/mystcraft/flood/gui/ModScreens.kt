package mystcraft.flood.gui

import mystcraft.flood.MystcraftReforged
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType
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

    val WRITING_DESK_HANDLER: ScreenHandlerType<WritingDeskScreenHandler> = Registry.register(
        Registries.SCREEN_HANDLER,
        Identifier(MystcraftReforged.MOD_ID, "writing_desk"),
        ScreenHandlerType(::WritingDeskScreenHandler, FeatureFlags.VANILLA_FEATURES)
    )

    val EDITING_TABLE_HANDLER: ScreenHandlerType<EditingTableScreenHandler> = Registry.register(
        Registries.SCREEN_HANDLER,
        Identifier(MystcraftReforged.MOD_ID, "editing_table"),
        ScreenHandlerType(::EditingTableScreenHandler, FeatureFlags.VANILLA_FEATURES)
    )

    val PRINTING_TABLE_HANDLER: ScreenHandlerType<PrintingTableScreenHandler> = Registry.register(
        Registries.SCREEN_HANDLER,
        Identifier(MystcraftReforged.MOD_ID, "printing_table"),
        ScreenHandlerType(::PrintingTableScreenHandler, FeatureFlags.VANILLA_FEATURES)
    )

    val NOTEBOOK_HANDLER: ScreenHandlerType<NotebookScreenHandler> = Registry.register(
        Registries.SCREEN_HANDLER,
        Identifier(MystcraftReforged.MOD_ID, "notebook"),
        ExtendedScreenHandlerType(::NotebookScreenHandler)
    )

    fun register() {
        MystcraftReforged.LOGGER.info("Registering Screen Handlers for ${MystcraftReforged.MOD_ID}")
    }
}
