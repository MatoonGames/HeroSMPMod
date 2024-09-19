package com.matoon.herosmp.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class GuiCustomScreen extends GuiScreen {

    private static final ResourceLocation BACKGROUND_IMAGE = new ResourceLocation("herosmp", "textures/gui/custom_screen.png");
    private int imageWidth = 240;  // Updated size
    private int imageHeight = 228;  // Updated size

    @Override
    public void initGui() {
        int centerX = (this.width - imageWidth) / 2;
        int centerY = (this.height - imageHeight) / 2;

        // Add a button above the image
        this.buttonList.add(new GuiButton(0, centerX + (imageWidth / 2) - 100, centerY - 30, 200, 20, "Visit Website"));
        this.buttonList.add(new GuiButton(1, centerX + (imageWidth / 2) - 100, centerY + imageHeight + 10, 200, 20, "Close"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {  // Button to open a website
            if (Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().browse(new URI("https://yourwebsite.com"));
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
            }
        } else if (button.id == 1) {  // Button to close the screen
            mc.displayGuiScreen(null);  // Close the GUI
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Calculate the center of the screen
        int centerX = (this.width - imageWidth) / 2;
        int centerY = (this.height - imageHeight) / 2;

        // Bind and draw the background image
        this.mc.getTextureManager().bindTexture(BACKGROUND_IMAGE);
        drawModalRectWithCustomSizedTexture(centerX, centerY, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);

        // Draw the buttons after the image
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;  // Pauses the game when the GUI is open
    }
}
