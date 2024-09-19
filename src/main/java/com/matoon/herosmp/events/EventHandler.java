package com.matoon.herosmp.events;

import com.matoon.herosmp.client.CustomDisconnectedScreen;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Field;

public class EventHandler {

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
