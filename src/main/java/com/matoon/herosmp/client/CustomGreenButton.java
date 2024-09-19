package com.matoon.herosmp.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.opengl.GL11;

public class CustomGreenButton extends GuiButton {

    public CustomGreenButton(int buttonId, int x, int y, int widthIn, int heightIn, String buttonText) {
        super(buttonId, x, y, widthIn, heightIn, buttonText);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (this.visible) {
            mc.getTextureManager().bindTexture(BUTTON_TEXTURES);

            // Check if the button is hovered and set color accordingly
            if (this.hovered) {
                // Brighter green color when hovered (slightly increase green and blue values)
                GL11.glColor4f(0.3F, 1.0F, 0.3F, 1.0F);  // A bit lighter green on hover
            } else {
                // Default green color
                GL11.glColor4f(0.0F, 1.0F, 0.0F, 1.0F);  // Regular green
            }

            // Draw button background
            int hoverState = this.getHoverState(this.hovered);
            drawTexturedModalRect(this.x, this.y, 0, 46 + hoverState * 20, this.width / 2, this.height);
            drawTexturedModalRect(this.x + this.width / 2, this.y, 200 - this.width / 2, 46 + hoverState * 20, this.width / 2, this.height);

            // Reset color to white for the text and other elements
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            // Draw button text
            this.drawCenteredString(mc.fontRenderer, this.displayString, this.x + this.width / 2, this.y + (this.height - 8) / 2, 0xFFFFFF);
        }
    }
}
