package com.matoon.herosmp.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class GuiImageCycler {

    // Image to display (keeping the name as image_1)
    private static final ResourceLocation IMAGE_1 = new ResourceLocation("herosmp", "textures/gui/image_1.png");

    private static final int imageWidth = 130;  // Original width of the image
    private static final int imageHeight = 28;  // Original height of the image

    public void renderCyclingImages(ScaledResolution scaledRes) {
        Minecraft mc = Minecraft.getMinecraft();

        // Log for debugging
        System.out.println("Rendering image: image_1");

        // Calculate the position to center the image at the top
        int centerX = (scaledRes.getScaledWidth() - imageWidth) / 2;
        int centerY = 0;  // Anchored near the top, adjust Y-coordinate as necessary

        // Bind the image
        mc.getTextureManager().bindTexture(IMAGE_1);

        // Enable blending for transparency
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Draw the image at its original size
        mc.ingameGUI.drawModalRectWithCustomSizedTexture(centerX, centerY, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);

        // Disable blending after drawing
        GL11.glDisable(GL11.GL_BLEND);
    }
}
