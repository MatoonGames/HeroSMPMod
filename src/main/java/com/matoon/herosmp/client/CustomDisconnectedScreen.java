package com.matoon.herosmp.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

public class CustomDisconnectedScreen extends GuiScreen {

    private final GuiScreen parentScreen;
    private final ITextComponent message;
    private static final ResourceLocation BACKGROUND_IMAGE = new ResourceLocation("herosmp", "textures/gui/custom_screen.png");
    private static final ResourceLocation DISCONNECTED_BACKGROUND = new ResourceLocation("herosmp", "textures/gui/disconnected_background.png");
    private static final int imageWidth = 200;
    private static final int imageHeight = 190;

    public CustomDisconnectedScreen(GuiScreen parentScreen, ITextComponent message) {
        this.parentScreen = parentScreen != null ? parentScreen : new GuiScreen() {};
        this.message = message != null ? message : new TextComponentString("Disconnected from the server");

        // Print the message using toString() instead of getUnformattedText()
        System.out.println("CustomDisconnectedScreen initialized with message: " + message.toString());
    }

    @Override
    public void initGui() {
        System.out.println("CustomDisconnectedScreen initGui called");
        int centerX = (this.width - imageWidth) / 2;
        int centerY = (this.height - imageHeight) / 2;

        this.buttonList.add(new GuiButton(0, centerX + (imageWidth / 2) - 65, centerY + 150, 130, 20, "Play Now"));
        this.buttonList.add(new GuiButton(1, centerX + (imageWidth / 2) - 100, centerY + imageHeight + 20, 200, 20, "Cancel"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            if (Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().browse(new URI("https://discordapp.com/servers/matoon-community-917570600770342923"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else if (button.id == 1) {
            mc.displayGuiScreen(new GuiMultiplayer(this.parentScreen));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        System.out.println("CustomDisconnectedScreen drawScreen called");
        Minecraft.getMinecraft().getTextureManager().bindTexture(DISCONNECTED_BACKGROUND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        drawModalRectWithCustomSizedTexture(0, 0, 0, 0, this.width, this.height, this.width, this.height);

        Minecraft.getMinecraft().getTextureManager().bindTexture(BACKGROUND_IMAGE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        int centerX = (this.width - imageWidth) / 2;
        int centerY = (this.height - imageHeight) / 2;
        drawModalRectWithCustomSizedTexture(centerX, centerY, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        GL11.glDisable(GL11.GL_BLEND);

        drawCenteredString(this.fontRenderer, message.getUnformattedText(), this.width / 2, 20, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;  // Prevent Minecraft from overriding the GUI
    }
}
