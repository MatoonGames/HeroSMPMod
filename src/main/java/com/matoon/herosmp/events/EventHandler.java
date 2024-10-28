package com.matoon.herosmp.events;

import com.matoon.herosmp.HeroSMP;
import com.matoon.herosmp.client.CustomDisconnectedScreen;
import com.matoon.herosmp.client.MultiplayerScoreboardHandler;
import com.matoon.herosmp.client.GuiImageCycler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Field;

public class EventHandler {

    private final GuiImageCycler guiImageCycler = new GuiImageCycler();
    private final MultiplayerScoreboardHandler scoreboardHandler = new MultiplayerScoreboardHandler();

    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent event) {
        if (event.getType() == RenderGameOverlayEvent.ElementType.ALL) {
            // Check if the GUI is enabled in the config
            if (HeroSMP.enableGUI) {
                ScaledResolution scaledRes = new ScaledResolution(Minecraft.getMinecraft());

                // Display the custom image and scoreboard if multiplayer
                if (HeroSMP.guiMultiplayerOnly && isMultiplayer()) {
                    guiImageCycler.renderCyclingImages(scaledRes);
                    scoreboardHandler.renderScoreboard(scaledRes);
                } else if (!HeroSMP.guiMultiplayerOnly) {
                    // Display in both singleplayer and multiplayer
                    guiImageCycler.renderCyclingImages(scaledRes);
                    scoreboardHandler.renderScoreboard(scaledRes);
                }
            }
        }
    }

    // Method to check if the player is in a multiplayer server
    private boolean isMultiplayer() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.getCurrentServerData() != null && !mc.isIntegratedServerRunning()) {
            //System.out.println("Player is in Multiplayer");
            return true;
        }
        //System.out.println("Player is NOT in Multiplayer");
        return false;
    }

    @SubscribeEvent
    public void onClientDisconnect(GuiOpenEvent event) {
        GuiScreen openedGui = event.getGui();

        if (openedGui != null) {
            System.out.println("GuiOpenEvent caught for GUI: " + openedGui.getClass().getName());

            // Ensure this only triggers if the player is actually disconnected (GuiDisconnected screen)
            if (openedGui instanceof GuiDisconnected) {
                System.out.println("Detected a disconnection-related GUI: " + openedGui.getClass().getName());

                // Use reflection to get the message field
                try {
                    Field messageField = GuiDisconnected.class.getDeclaredField("message");
                    messageField.setAccessible(true);
                    ITextComponent message = (ITextComponent) messageField.get(openedGui);
                    System.out.println("Successfully retrieved message: " + message.getUnformattedText());

                    // Open the custom disconnect screen after disconnection
                    event.setGui(new CustomDisconnectedScreen(openedGui, message));
                    System.out.println("CustomDisconnectedScreen set after disconnection");

                } catch (NoSuchFieldException | IllegalAccessException e) {
                    System.err.println("Failed to retrieve the message field, using default message");
                    ITextComponent defaultMessage = new TextComponentString("Disconnected from the server.");

                    // Use a default message if reflection fails
                    event.setGui(new CustomDisconnectedScreen(openedGui, defaultMessage));
                    e.printStackTrace();
                }
            }
        }
    }
}
