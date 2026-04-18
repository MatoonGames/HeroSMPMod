package com.matoon.herosmp.events;

import com.matoon.herosmp.client.CustomDisconnectedScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Field;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

// Add @SideOnly to ensure it's client-side only
@SideOnly(Side.CLIENT)
public class EventHandler {

    @SubscribeEvent
    @SideOnly(Side.CLIENT) // Ensure this only runs client-side
    public void onClientDisconnect(GuiOpenEvent event) {
        GuiScreen openedGui = event.getGui();

        if (openedGui == null || openedGui instanceof CustomDisconnectedScreen) {
            return;
        }

        String guiName = openedGui.getClass().getName();
        boolean disconnectGui = openedGui instanceof GuiDisconnected
                || guiName.toLowerCase().contains("guidisconnected");

        if (!disconnectGui) {
            return;
        }

        System.out.println("Detected disconnect GUI: " + guiName);

        ITextComponent message = extractDisconnectMessage(openedGui);
        if (message == null) {
            message = new TextComponentString("Disconnected from the server.");
        }

        event.setGui(new CustomDisconnectedScreen(openedGui, message));
        System.out.println("CustomDisconnectedScreen set after disconnection");
    }

    private ITextComponent extractDisconnectMessage(GuiScreen gui) {
        String[] preferredFieldNames = new String[]{"message", "field_146304_f"};

        for (String fieldName : preferredFieldNames) {
            try {
                Field field = gui.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(gui);
                if (value instanceof ITextComponent) {
                    return (ITextComponent) value;
                }
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                // Continue with fallback search
            }
        }

        // Fallback: find any ITextComponent field on this screen class
        for (Field field : gui.getClass().getDeclaredFields()) {
            if (ITextComponent.class.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(gui);
                    if (value instanceof ITextComponent) {
                        return (ITextComponent) value;
                    }
                } catch (IllegalAccessException ignored) {
                    // Keep searching
                }
            }
        }

        return null;
    }
}
