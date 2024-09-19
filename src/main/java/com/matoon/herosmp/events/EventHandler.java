package com.matoon.herosmp.events;

import com.matoon.herosmp.client.CustomDisconnectedScreen;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Field;

public class EventHandler {

    @SubscribeEvent
    public void onClientDisconnect(GuiOpenEvent event) {
        if (event.getGui() instanceof GuiDisconnected) {
            GuiDisconnected disconnected = (GuiDisconnected) event.getGui();

            try {
                // Use reflection to access the private "parentScreen" field
                Field parentScreenField = GuiDisconnected.class.getDeclaredField("parentScreen");
                parentScreenField.setAccessible(true);
                GuiScreen parentScreen = (GuiScreen) parentScreenField.get(disconnected);

                // Use reflection to access the private "message" field
                Field messageField = GuiDisconnected.class.getDeclaredField("message");
                messageField.setAccessible(true);
                ITextComponent message = (ITextComponent) messageField.get(disconnected);  // Cast to ITextComponent

                // Set the custom disconnected screen
                event.setGui(new CustomDisconnectedScreen(parentScreen, message));

            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }
}
