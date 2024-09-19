package com.matoon.herosmp.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public class CustomDisconnectedScreen extends GuiScreen {

    private final GuiScreen parentScreen;
    private final ITextComponent message;
    private static final ResourceLocation BACKGROUND_IMAGE = new ResourceLocation("herosmp", "textures/gui/custom_screen.png");
    private static final ResourceLocation DISCONNECTED_BACKGROUND = new ResourceLocation("herosmp", "textures/gui/disconnected_background.png");
    private static final int imageWidth = 200;
    private static final int imageHeight = 190;

    // Store Discord widget data
    private String discordServerName = "Loading...";
    private int discordOnlineMembers = 0;
    private boolean discordDataLoaded = false;

    public CustomDisconnectedScreen(GuiScreen parentScreen, ITextComponent message) {
        this.parentScreen = parentScreen;
        this.message = message;
        // Fetch Discord widget data on initialization
        fetchDiscordWidgetData();
    }

    @Override
    public void initGui() {
        int centerX = (this.width - imageWidth) / 2;
        int centerY = (this.height - imageHeight) / 2;

        // Adjust Play Now button
        this.buttonList.add(new CustomGreenButton(0, centerX + (imageWidth / 2) - 65, centerY + 150, 130, 20, "Play Now")); // Reduced width to 130
        this.buttonList.add(new GuiButton(1, centerX + (imageWidth / 2) - 100, centerY + imageHeight + 20, 200, 20, "Cancel"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {  // "Play Now" button
            if (Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().browse(new URI("https://discordapp.com/servers/matoon-community-917570600770342923"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else if (button.id == 1) {  // "Cancel" button
            mc.displayGuiScreen(new GuiMultiplayer(this.parentScreen));  // Return to the Multiplayer screen
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Draw the background image
        Minecraft.getMinecraft().getTextureManager().bindTexture(DISCONNECTED_BACKGROUND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        drawModalRectWithCustomSizedTexture(0, 0, 0, 0, this.width, this.height, this.width, this.height);

        // Draw the custom foreground image
        Minecraft.getMinecraft().getTextureManager().bindTexture(BACKGROUND_IMAGE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        int centerX = (this.width - imageWidth) / 2;
        int centerY = (this.height - imageHeight) / 2;
        drawModalRectWithCustomSizedTexture(centerX, centerY, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        GL11.glDisable(GL11.GL_BLEND);

        // Draw the disconnect message in the red-outlined area
        drawCenteredString(this.fontRenderer, this.message.getFormattedText(), this.width / 2, 20, 0xFFFFFF);

        // Draw buttons
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Draw Discord widget info on the right side
        drawRightAlignedString(this.fontRenderer, "Server: " + discordServerName, this.width - 10, 100, 0xFFFFFF);
        drawRightAlignedString(this.fontRenderer, "Online Members: " + discordOnlineMembers, this.width - 10, 120, 0xFFFFFF);
    }

    private void fetchDiscordWidgetData() {
        new Thread(() -> {
            try {
                System.out.println("Starting Discord API request...");
                URL url = new URL("https://discordapp.com/api/guilds/917570600770342923/widget.json");
                HttpURLConnection request = (HttpURLConnection) url.openConnection();
                request.connect();

                int responseCode = request.getResponseCode();
                System.out.println("Discord API response code: " + responseCode);

                if (responseCode == 200) {
                    JsonParser jp = new JsonParser();
                    JsonObject root = jp.parse(new InputStreamReader((request.getInputStream()))).getAsJsonObject();
                    discordServerName = root.get("name").getAsString();
                    discordOnlineMembers = root.get("presence_count").getAsInt();
                    discordDataLoaded = true;  // Data is now loaded
                    System.out.println("Discord data loaded successfully.");
                } else {
                    discordServerName = "Failed to load";
                    discordDataLoaded = true;
                    System.err.println("Failed to load Discord data, response code: " + responseCode);
                }
            } catch (Exception e) {
                e.printStackTrace();
                discordServerName = "Failed to load";  // Error case
                discordDataLoaded = true;
                System.err.println("Error loading Discord data: " + e.getMessage());
            }
        }).start();
    }

    // Helper method to align text on the right
    private void drawRightAlignedString(net.minecraft.client.gui.FontRenderer fontRenderer, String text, int x, int y, int color) {
        fontRenderer.drawString(text, x - fontRenderer.getStringWidth(text), y, color);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
