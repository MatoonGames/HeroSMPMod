package com.matoon.herosmp.client.gui;

import com.matoon.herosmp.client.gui.bedrock.BedrockScreen;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI.Style;
import com.matoon.herosmp.client.gui.editor.GuiMapEditor;
import com.matoon.herosmp.client.store.InstalledPacks;
import com.matoon.herosmp.client.store.MapEditorLauncher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;

/** The Edit button's actions and its "more" (three-dot) dropdown, shared by every store screen. */
final class EditMenu {

    private EditMenu() {}

    /** Opens the dropdown anchored at the given bottom-right corner. */
    static void open(final BedrockScreen screen, final String mapName, int anchorRight, int anchorBottom) {
        screen.openMenu(anchorRight, anchorBottom,
                new String[]{"Visual Editor", "Edit in World", "Open Map Folder"},
                new Runnable[]{
                        () -> openVisualEditor(screen, mapName),
                        () -> editInWorld(screen, mapName),
                        () -> OpenGlHelper.openFile(InstalledPacks.mapDir(mapName))
                });
    }

    static void openVisualEditor(BedrockScreen screen, String mapName) {
        if (MapEditorLauncher.isEditingMap(mapName)) {
            screen.toastWarn("Finish editing " + mapName + " in the world first (/return)");
            return;
        }
        Minecraft.getMinecraft().displayGuiScreen(new GuiMapEditor(screen, mapName));
    }

    /** Confirms, then opens the map in in-world configure mode (via the editor world from the menu). */
    static void editInWorld(BedrockScreen screen, final String name) {
        boolean inWorld = Minecraft.getMinecraft().world != null;
        String message = inWorld
                ? "You'll be moved into configure mode for " + name + ". Your inventory is stored and "
                  + "given back when you type /return, which also saves the map."
                : "This loads the Beyonder Map Editor world and opens " + name + " in configure mode. "
                  + "Type /return when you're done: your changes are saved and you come straight back here.";
        screen.confirm("Edit " + name + " in World", message, "Open Editor", Style.PRIMARY,
                () -> MapEditorLauncher.editMap(name));
    }
}
