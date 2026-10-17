package com.matoon.herosmp.client;

import com.matoon.herosmp.client.gui.GoldSheenButton;
import com.matoon.herosmp.client.gui.GuiPackStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

/**
 * Adds the gold "Content Packs" button to the vanilla main menu and provides an in-game keybind to
 * reopen the store (needed so the Installed → Edit flow can enter configure-map mode, which requires
 * a running world). Registered on the client event bus in {@code ClientProxy.preInit}.
 */
@SideOnly(Side.CLIENT)
public class MainMenuPackButton {

    // High id to avoid colliding with vanilla GuiMainMenu button ids.
    private static final int BUTTON_ID = 92810;

    /** Default-unbound keybind to open the store while in a world. Registered by ClientProxy. */
    public static final KeyBinding OPEN_STORE_KEY =
            new KeyBinding("Open Content Packs", Keyboard.KEY_NONE, "Beyonder");

    /** The main menu and the in-game pause menu both get the button. */
    private static boolean hasButton(net.minecraft.client.gui.GuiScreen gui) {
        return gui instanceof GuiMainMenu || gui instanceof net.minecraft.client.gui.GuiIngameMenu;
    }

    @SubscribeEvent
    public void onMenuInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!hasButton(event.getGui())) return;
        event.getButtonList().add(new GoldSheenButton(BUTTON_ID, 6, 6, 128, 24, "Content Packs"));
    }

    @SubscribeEvent
    public void onMenuAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!hasButton(event.getGui())) return;
        if (event.getButton().id != BUTTON_ID) return;
        Minecraft.getMinecraft().displayGuiScreen(new GuiPackStore(event.getGui()));
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (OPEN_STORE_KEY.isPressed() && mc.currentScreen == null && mc.world != null) {
            mc.displayGuiScreen(new GuiPackStore(null));
        }
    }
}
