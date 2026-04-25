package com.matoon.herosmp.client.gui;

import com.matoon.herosmp.network.ModNetwork;
import com.matoon.herosmp.network.PacketHungerGamesMenuAction;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.TextFormatting;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class GuiHungerGamesMenu extends GuiScreen {

    private static final int COLS = 9;
    private static final int ROWS = 3;
    private static final int SLOT_SIZE = 18;

    private final boolean queued;
    private final int queueSize;
    private final int activeMatches;

    private final Map<Integer, SlotAction> actions = new HashMap<Integer, SlotAction>();
    private final Map<Integer, ItemStack> slotItems = new HashMap<Integer, ItemStack>();
    private int left;
    private int top;

    public GuiHungerGamesMenu(boolean queued, int queueSize, int activeMatches) {
        this.queued = queued;
        this.queueSize = queueSize;
        this.activeMatches = activeMatches;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.left = (this.width - COLS * SLOT_SIZE) / 2;
        this.top = (this.height - ROWS * SLOT_SIZE) / 2;
        slotItems.clear();
        actions.clear();

        if (!queued) {
            registerSlot(13, createNamedItem(
                    new ItemStack(Items.BOW),
                    TextFormatting.GREEN + "Join Queue",
                    "Queue for the next Hunger Games match  (" + queueSize + " in queue)"
            ), new SlotAction() {
                @Override
                public void run() {
                    ModNetwork.CHANNEL.sendToServer(PacketHungerGamesMenuAction.queue());
                    mc.displayGuiScreen(null);
                }
            });
        } else {
            registerSlot(13, createNamedItem(
                    new ItemStack(Items.BOW),
                    TextFormatting.GREEN + "Join Queue",
                    "Queue for the next Hunger Games match  (" + queueSize + " in queue)"
            ), null);

            registerSlot(11, createNamedItem(
                    new ItemStack(Items.REDSTONE),
                    TextFormatting.RED + "Leave Queue",
                    "Leave the Hunger Games queue"
            ), new SlotAction() {
                @Override
                public void run() {
                    ModNetwork.CHANNEL.sendToServer(PacketHungerGamesMenuAction.cancelQueue());
                    mc.displayGuiScreen(null);
                }
            });
        }

        registerSlot(15, createNamedItem(
                new ItemStack(Items.CLOCK),
                TextFormatting.AQUA + "Refresh",
                "Refresh queue info  (" + activeMatches + " active match" + (activeMatches == 1 ? "" : "es") + ")"
        ), new SlotAction() {
            @Override
            public void run() {
                ModNetwork.CHANNEL.sendToServer(PacketHungerGamesMenuAction.refresh());
            }
        });

        registerSlot(26, createNamedItem(
                new ItemStack(Item.getItemFromBlock(Blocks.BARRIER)),
                TextFormatting.DARK_RED + "Close",
                "Close this menu"
        ), new SlotAction() {
            @Override
            public void run() {
                mc.displayGuiScreen(null);
            }
        });
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0) {
            return;
        }
        int slot = getSlotAt(mouseX, mouseY);
        SlotAction action = actions.get(slot);
        if (action != null) {
            action.run();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        drawPanel();
        drawSlots(mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void registerSlot(int slot, ItemStack stack, SlotAction action) {
        slotItems.put(slot, stack);
        if (action != null) {
            actions.put(slot, action);
        }
    }

    private void drawPanel() {
        int panelLeft = left - 8;
        int panelTop = top - 22;
        int panelRight = left + COLS * SLOT_SIZE + 8;
        int panelBottom = top + ROWS * SLOT_SIZE + 8;
        drawRect(panelLeft, panelTop, panelRight, panelBottom, 0xAA111111);
        String status = queued ? TextFormatting.YELLOW + " (In Queue)" : "";
        drawCenteredString(this.fontRenderer, TextFormatting.GOLD + "Hunger Games" + status, this.width / 2, panelTop + 8, 0xFFFFFF);
    }

    private void drawSlots(int mouseX, int mouseY) {
        for (int slot = 0; slot < ROWS * COLS; slot++) {
            int x = left + (slot % COLS) * SLOT_SIZE;
            int y = top + (slot / COLS) * SLOT_SIZE;
            drawRect(x, y, x + 16, y + 16, 0xFF2A2A2A);
            drawRect(x + 1, y + 1, x + 15, y + 15, 0xFF151515);
            ItemStack stack = slotItems.get(slot);
            if (stack != null && !stack.isEmpty()) {
                this.itemRender.renderItemAndEffectIntoGUI(stack, x, y);
                this.itemRender.renderItemOverlayIntoGUI(this.fontRenderer, stack, x, y, null);
            }
        }

        int hoveredSlot = getSlotAt(mouseX, mouseY);
        ItemStack hovered = slotItems.get(hoveredSlot);
        if (hovered != null && !hovered.isEmpty()) {
            ITooltipFlag flag = this.mc.gameSettings.advancedItemTooltips ? ITooltipFlag.TooltipFlags.ADVANCED : ITooltipFlag.TooltipFlags.NORMAL;
            drawHoveringText(hovered.getTooltip(this.mc.player, flag), mouseX, mouseY);
        }
    }

    private int getSlotAt(int mouseX, int mouseY) {
        if (mouseX < left || mouseY < top || mouseX >= left + COLS * SLOT_SIZE || mouseY >= top + ROWS * SLOT_SIZE) {
            return -1;
        }
        int col = (mouseX - left) / SLOT_SIZE;
        int row = (mouseY - top) / SLOT_SIZE;
        return row * COLS + col;
    }

    private static ItemStack createNamedItem(ItemStack stack, String name, String lore) {
        ItemStack copy = stack.copy();
        NBTTagCompound tag = copy.hasTagCompound() ? copy.getTagCompound() : new NBTTagCompound();
        NBTTagCompound display = tag.hasKey("display", 10) ? tag.getCompoundTag("display") : new NBTTagCompound();
        display.setString("Name", name);
        NBTTagList loreList = new NBTTagList();
        loreList.appendTag(new net.minecraft.nbt.NBTTagString(TextFormatting.GRAY + lore));
        display.setTag("Lore", loreList);
        tag.setTag("display", display);
        tag.setInteger("HideFlags", 2);
        copy.setTagCompound(tag);
        return copy;
    }

    private interface SlotAction {
        void run();
    }
}
