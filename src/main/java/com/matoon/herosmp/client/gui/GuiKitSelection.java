package com.matoon.herosmp.client.gui;

import com.matoon.herosmp.network.ModNetwork;
import com.matoon.herosmp.network.PacketOpenKitSelection;
import com.matoon.herosmp.network.PacketSelectKit;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;

import java.io.IOException;
import java.util.List;

public class GuiKitSelection extends GuiScreen {

    private final List<PacketOpenKitSelection.KitEntry> kits;

    public GuiKitSelection(List<PacketOpenKitSelection.KitEntry> kits) {
        this.kits = kits;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();

        int widthPer = 150;
        int heightPer = 20;
        int cols = 2;
        int rows = (int) Math.ceil(kits.size() / 2.0D);
        int startX = (this.width - (cols * widthPer + 10)) / 2;
        int startY = Math.max(40, (this.height - (rows * (heightPer + 6))) / 2);

        for (int i = 0; i < kits.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int x = startX + col * (widthPer + 10);
            int y = startY + row * (heightPer + 6);
            this.buttonList.add(new GuiButton(i, x, y, widthPer, heightPer, kits.get(i).displayName));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id < 0 || button.id >= kits.size()) {
            return;
        }

        PacketOpenKitSelection.KitEntry entry = kits.get(button.id);
        ModNetwork.CHANNEL.sendToServer(new PacketSelectKit(entry.key));
        this.mc.displayGuiScreen(null);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        drawCenteredString(this.fontRenderer, "Choose a PvP Kit", this.width / 2, 16, 0xFFFFFF);

        super.drawScreen(mouseX, mouseY, partialTicks);

        for (int i = 0; i < kits.size() && i < this.buttonList.size(); i++) {
            GuiButton button = this.buttonList.get(i);
            PacketOpenKitSelection.KitEntry entry = kits.get(i);
            if (!entry.icon.isEmpty()) {
                GlStateManager.pushMatrix();
                this.itemRender.renderItemAndEffectIntoGUI(entry.icon, button.x + 2, button.y + 2);
                GlStateManager.popMatrix();
            }
        }

        if (kits.isEmpty()) {
            drawCenteredString(this.fontRenderer, "No kits configured. Ask staff to create kits.", this.width / 2, this.height / 2, 0xFF5555);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
