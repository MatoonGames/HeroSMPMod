package com.matoon.herosmp.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.opengl.GL11;

import com.matoon.herosmp.HeroSMP;

public class MultiplayerScoreboardHandler {

    public void renderScoreboard(ScaledResolution scaledRes) {
        // Check if the scoreboard is enabled in the config
        if (!HeroSMP.enableScoreboard) {
            System.out.println("Scoreboard is disabled in the config, skipping render.");
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();

        if (mc == null || mc.player == null || mc.getCurrentServerData() == null) {
            System.out.println("Minecraft instance, player, or server data is null, skipping scoreboard render.");
            return;
        }

        System.out.println("Rendering scoreboard...");

        // Set position to right-center of the screen
        int centerX = scaledRes.getScaledWidth() - 10; // 10 pixels from the right edge
        int centerY = scaledRes.getScaledHeight() / 2; // Vertically centered

        // Get the width of the "Hero SMP" text and set box width to wrap the text
        String heroText = TextFormatting.DARK_BLUE + "" + TextFormatting.BOLD + "Hero SMP";
        int textWidth = mc.fontRenderer.getStringWidth(heroText);
        int boxWidth = textWidth + 20; // Add some padding to the box width
        int boxHeight = 70;

        // Draw background with transparency
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Draw the background (transparent black)
        drawRect(centerX - boxWidth, centerY - boxHeight / 2, centerX, centerY + boxHeight / 2, 0x60000000);

        // Restore the state
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();

        // Draw the text elements
        mc.fontRenderer.drawString(heroText, centerX - (textWidth + boxWidth) / 2, centerY - 30, 0xFFFFFF);

        // Display "Current Players / Max Players", centered
        if (mc.getConnection() != null) {
            int currentPlayers = mc.getConnection().getPlayerInfoMap().size();
            String playerCount = "Online: " + currentPlayers + "/250";
            int playerCountWidth = mc.fontRenderer.getStringWidth(playerCount);
            mc.fontRenderer.drawString(playerCount, centerX - (playerCountWidth + boxWidth) / 2, centerY - 15, 0xFFFFFF);
        }

        // Display "Level: ", centered
        String levelText = TextFormatting.BOLD + "Level: " + TextFormatting.RESET + mc.player.experienceLevel;
        mc.fontRenderer.drawString(levelText, centerX - mc.fontRenderer.getStringWidth(levelText) - 5, centerY, 0xFFFFFF);

        // Display the server IP, centered
        String serverIP = "HEROSMP.PRO";
        int serverIPWidth = mc.fontRenderer.getStringWidth(serverIP);
        mc.fontRenderer.drawString(serverIP, centerX - (serverIPWidth + boxWidth) / 2, centerY + 15, 0xFFFFFF);
    }

    // Minecraft method for drawing rectangles in 1.12.2
    private void drawRect(int left, int top, int right, int bottom, int color) {
        int j;

        if (left < right) {
            j = left;
            left = right;
            right = j;
        }

        if (top < bottom) {
            j = top;
            top = bottom;
            bottom = j;
        }

        float alpha = (float) (color >> 24 & 255) / 255.0F;
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(red, green, blue, alpha);

        // Draw the rectangle
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(left, bottom);
        GL11.glVertex2f(right, bottom);
        GL11.glVertex2f(right, top);
        GL11.glVertex2f(left, top);
        GL11.glEnd();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }
}
