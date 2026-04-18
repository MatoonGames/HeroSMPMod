package com.matoon.herosmp.client.gui;

import com.matoon.herosmp.network.ModNetwork;
import com.matoon.herosmp.network.PacketSaveNpcEditor;
import com.matoon.herosmp.npc.NpcMode;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public class GuiNpcEditor extends GuiScreen {

    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 316;
    private static final int FIELD_WIDTH = 240;
    private static final int FIELD_HEIGHT = 20;
    private static final int LABEL_GAP = 10;
    private static final int ROW_GAP = 38;

    private final int entityId;
    private final String initialNpcKey;
    private final String initialName;
    private final String initialSkinOwner;
    private final String initialMode;
    private final String initialCommand;
    private final String initialDisplayItemId;

    private GuiTextField keyField;
    private GuiTextField nameField;
    private GuiTextField skinField;
    private GuiTextField commandField;
    private GuiTextField displayItemField;
    private GuiButton modeButton;
    private NpcMode mode;

    public GuiNpcEditor(int entityId, String npcKey, String displayName, String skinOwner, String mode, String command, String displayItemId) {
        this.entityId = entityId;
        this.initialNpcKey = npcKey == null ? "" : npcKey;
        this.initialName = displayName == null ? "" : displayName;
        this.initialSkinOwner = skinOwner == null ? "" : skinOwner;
        this.initialMode = mode == null ? NpcMode.COMMAND.name() : mode;
        this.initialCommand = command == null ? "" : command;
        this.initialDisplayItemId = displayItemId == null ? "" : displayItemId;
        NpcMode parsed = NpcMode.fromString(this.initialMode);
        this.mode = parsed == null ? NpcMode.COMMAND : parsed;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        int panelLeft = (this.width - PANEL_WIDTH) / 2;
        int panelTop = (this.height - PANEL_HEIGHT) / 2;
        int left = panelLeft + 20;
        int top = panelTop + 28;

        this.keyField = new GuiTextField(0, this.fontRenderer, left, top + LABEL_GAP, FIELD_WIDTH, FIELD_HEIGHT);
        this.keyField.setMaxStringLength(64);
        this.keyField.setText(initialNpcKey);

        this.nameField = new GuiTextField(1, this.fontRenderer, left, top + ROW_GAP + LABEL_GAP, FIELD_WIDTH, FIELD_HEIGHT);
        this.nameField.setMaxStringLength(64);
        this.nameField.setText(initialName);

        this.skinField = new GuiTextField(2, this.fontRenderer, left, top + ROW_GAP * 2 + LABEL_GAP, FIELD_WIDTH, FIELD_HEIGHT);
        this.skinField.setMaxStringLength(64);
        this.skinField.setText(initialSkinOwner);

        this.commandField = new GuiTextField(3, this.fontRenderer, left, top + ROW_GAP * 4 + LABEL_GAP, FIELD_WIDTH, FIELD_HEIGHT);
        this.commandField.setMaxStringLength(256);
        this.commandField.setText(initialCommand);

        this.displayItemField = new GuiTextField(4, this.fontRenderer, left, top + ROW_GAP * 5 + LABEL_GAP, FIELD_WIDTH, FIELD_HEIGHT);
        this.displayItemField.setMaxStringLength(128);
        this.displayItemField.setText(initialDisplayItemId);

        this.modeButton = new GuiButton(10, left, top + ROW_GAP * 3 + LABEL_GAP, FIELD_WIDTH, FIELD_HEIGHT, "");
        updateModeButton();
        this.buttonList.add(modeButton);
        this.buttonList.add(new GuiButton(11, left, panelTop + PANEL_HEIGHT - 32, 74, 20, "Save"));
        this.buttonList.add(new GuiButton(12, left + 82, panelTop + PANEL_HEIGHT - 32, 74, 20, "Cancel"));
        this.buttonList.add(new GuiButton(13, left + FIELD_WIDTH - 74, panelTop + PANEL_HEIGHT - 32, 74, 20, "Delete"));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 10) {
            mode = mode == NpcMode.COMMAND ? NpcMode.PVP_QUEUE : NpcMode.COMMAND;
            updateModeButton();
            return;
        }
        if (button.id == 11) {
            ModNetwork.CHANNEL.sendToServer(new PacketSaveNpcEditor(
                    entityId,
                    keyField.getText(),
                    nameField.getText(),
                    skinField.getText(),
                    mode.name(),
                    commandField.getText(),
                    displayItemField.getText(),
                    false
            ));
            this.mc.displayGuiScreen(null);
            return;
        }
        if (button.id == 12) {
            this.mc.displayGuiScreen(null);
            return;
        }
        if (button.id == 13) {
            ModNetwork.CHANNEL.sendToServer(new PacketSaveNpcEditor(
                    entityId,
                    keyField.getText(),
                    nameField.getText(),
                    skinField.getText(),
                    mode.name(),
                    commandField.getText(),
                    displayItemField.getText(),
                    true
            ));
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            this.mc.displayGuiScreen(null);
            return;
        }
        if (keyField.textboxKeyTyped(typedChar, keyCode)
                || nameField.textboxKeyTyped(typedChar, keyCode)
                || skinField.textboxKeyTyped(typedChar, keyCode)
                || commandField.textboxKeyTyped(typedChar, keyCode)
                || displayItemField.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        keyField.mouseClicked(mouseX, mouseY, mouseButton);
        nameField.mouseClicked(mouseX, mouseY, mouseButton);
        skinField.mouseClicked(mouseX, mouseY, mouseButton);
        commandField.mouseClicked(mouseX, mouseY, mouseButton);
        displayItemField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        keyField.updateCursorCounter();
        nameField.updateCursorCounter();
        skinField.updateCursorCounter();
        commandField.updateCursorCounter();
        displayItemField.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int panelLeft = (this.width - PANEL_WIDTH) / 2;
        int panelTop = (this.height - PANEL_HEIGHT) / 2;
        int panelRight = panelLeft + PANEL_WIDTH;
        int panelBottom = panelTop + PANEL_HEIGHT;
        int left = panelLeft + 20;
        int top = panelTop + 28;
        drawRect(panelLeft, panelTop, panelRight, panelBottom, 0xCC111111);
        drawRect(panelLeft + 1, panelTop + 1, panelRight - 1, panelBottom - 1, 0xAA1A1A1A);

        drawCenteredString(this.fontRenderer, "Hero NPC Editor", this.width / 2, panelTop + 10, 0xFFFFFF);
        this.fontRenderer.drawString("NPC Key", left, top, 0xCFCFCF);
        this.fontRenderer.drawString("Display Name", left, top + ROW_GAP, 0xCFCFCF);
        this.fontRenderer.drawString("Skin Owner", left, top + ROW_GAP * 2, 0xCFCFCF);
        this.fontRenderer.drawString("Mode", left, top + ROW_GAP * 3, 0xCFCFCF);
        this.fontRenderer.drawString("Command", left, top + ROW_GAP * 4, 0xCFCFCF);
        this.fontRenderer.drawString("Display Item", left, top + ROW_GAP * 5, 0xCFCFCF);

        keyField.drawTextBox();
        nameField.drawTextBox();
        skinField.drawTextBox();
        commandField.drawTextBox();
        displayItemField.drawTextBox();

        this.fontRenderer.drawString("Use item ids like minecraft:apple for the name-tag icon.", left, top + ROW_GAP * 5 + 30, 0x8F8F8F);
        if (mode == NpcMode.PVP_QUEUE) {
            this.fontRenderer.drawString("Command is ignored while this NPC is set to PvP Queue.", left, panelTop + PANEL_HEIGHT - 52, 0xCCCC66);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void updateModeButton() {
        if (modeButton != null) {
            modeButton.displayString = "Mode: " + (mode == NpcMode.PVP_QUEUE ? "PvP Queue" : "Command");
        }
    }
}
