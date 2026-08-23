package com.matoon.herosmp.client.gui;

import com.matoon.herosmp.network.ModNetwork;
import com.matoon.herosmp.network.PacketOpenPvpMenu;
import com.matoon.herosmp.network.PacketPvpMenuAction;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GuiPvpMenu extends GuiScreen {

    private static final int COLS = 9;
    private static final int SLOT_SIZE = 18;

    private final List<PacketOpenPvpMenu.ActiveMatchEntry> matches;
    private final boolean queued;
    private final Map<Integer, SlotAction> actions = new HashMap<Integer, SlotAction>();
    private final Map<Integer, ItemStack> slotItems = new HashMap<Integer, ItemStack>();
    private int rows = 3;
    private int left;
    private int top;

    public GuiPvpMenu(List<PacketOpenPvpMenu.ActiveMatchEntry> matches, boolean queued) {
        this.matches = matches;
        this.queued = queued;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.rows = 3;
        recalcBounds();
        slotItems.clear();
        actions.clear();

        registerSlot(11, createNamedItem(new ItemStack(Items.DIAMOND_SWORD), TextFormatting.GOLD + "Quick Queue", "Play the currently available PvP mode"), new SlotAction() {
            @Override
            public void run() {
                ModNetwork.CHANNEL.sendToServer(PacketPvpMenuAction.quickQueue());
                mc.displayGuiScreen(null);
            }
        });
        registerSlot(13, createNamedItem(new ItemStack(Items.COMPASS), TextFormatting.AQUA + "PvP Modes", "Choose a specific PvP mode"), new SlotAction() {
            @Override
            public void run() {
                mc.displayGuiScreen(new GuiPvpModes(GuiPvpMenu.this));
            }
        });
        registerSlot(15, createNamedItem(new ItemStack(Items.ENDER_EYE), TextFormatting.LIGHT_PURPLE + "Active Games", "Browse matches and spectate"), new SlotAction() {
            @Override
            public void run() {
                mc.displayGuiScreen(new GuiActiveMatches(GuiPvpMenu.this, matches));
            }
        });

        if (queued) {
            registerSlot(17, createNamedItem(new ItemStack(Items.REDSTONE), TextFormatting.RED + "Cancel Queue", "Leave the current PvP queue"), new SlotAction() {
                @Override
                public void run() {
                    ModNetwork.CHANNEL.sendToServer(PacketPvpMenuAction.cancelQueue());
                    mc.displayGuiScreen(null);
                }
            });
        }

        registerSlot(26, createNamedItem(new ItemStack(Item.getItemFromBlock(Blocks.BARRIER)), TextFormatting.DARK_RED + "Close", "Close this menu"), new SlotAction() {
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
        drawPanel("Hero PvP Queue");
        drawSlots(mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void registerSlot(int slot, ItemStack stack, SlotAction action) {
        slotItems.put(slot, stack);
        actions.put(slot, action);
    }

    private void recalcBounds() {
        this.left = (this.width - COLS * SLOT_SIZE) / 2;
        this.top = (this.height - rows * SLOT_SIZE) / 2;
    }

    private void drawPanel(String title) {
        int panelLeft = left - 8;
        int panelTop = top - 22;
        int panelRight = left + COLS * SLOT_SIZE + 8;
        int panelBottom = top + rows * SLOT_SIZE + 8;
        drawRect(panelLeft, panelTop, panelRight, panelBottom, 0xAA111111);
        drawCenteredString(this.fontRenderer, title, this.width / 2, panelTop + 8, 0xFFFFFF);
    }

    private void drawSlots(int mouseX, int mouseY) {
        for (int slot = 0; slot < rows * COLS; slot++) {
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
        if (mouseX < left || mouseY < top || mouseX >= left + COLS * SLOT_SIZE || mouseY >= top + rows * SLOT_SIZE) {
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

    public static class GuiPvpModes extends GuiScreen {
        private final GuiScreen parent;
        private final Map<Integer, SlotAction> actions = new HashMap<Integer, SlotAction>();
        private final Map<Integer, ItemStack> slotItems = new HashMap<Integer, ItemStack>();
        private int left;
        private int top;

        public GuiPvpModes(GuiScreen parent) {
            this.parent = parent;
        }

        @Override
        public void initGui() {
            super.initGui();
            left = (this.width - COLS * SLOT_SIZE) / 2;
            top = (this.height - 3 * SLOT_SIZE) / 2;
            actions.clear();
            slotItems.clear();

            slotItems.put(11, createNamedItem(new ItemStack(Items.NETHER_STAR), TextFormatting.LIGHT_PURPLE + "Crownfall (2-6)",
                    "Claim the Gauntlet, gather six Stones, and Snap to win"));
            actions.put(11, new SlotAction() {
                @Override
                public void run() {
                    ModNetwork.CHANNEL.sendToServer(PacketPvpMenuAction.modeQueue("crownfall"));
                    mc.displayGuiScreen(null);
                }
            });

            slotItems.put(13, createNamedItem(new ItemStack(Items.IRON_SWORD), TextFormatting.RED + "1v1 Duel", "Current available PvP mode"));
            actions.put(13, new SlotAction() {
                @Override
                public void run() {
                    ModNetwork.CHANNEL.sendToServer(PacketPvpMenuAction.modeQueue("duel"));
                    mc.displayGuiScreen(null);
                }
            });

            slotItems.put(15, createNamedItem(new ItemStack(Items.GOLDEN_SWORD), TextFormatting.GOLD + "FFA (2-4)", "Free For All mode"));
            actions.put(15, new SlotAction() {
                @Override
                public void run() {
                    ModNetwork.CHANNEL.sendToServer(PacketPvpMenuAction.modeQueue("ffa"));
                    mc.displayGuiScreen(null);
                }
            });

            slotItems.put(22, createNamedItem(new ItemStack(Items.ARROW), TextFormatting.YELLOW + "Back", "Return to previous menu"));
            actions.put(22, new SlotAction() {
                @Override
                public void run() {
                    mc.displayGuiScreen(parent);
                }
            });
        }

        @Override
        protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            if (mouseButton != 0) {
                return;
            }
            int slot = getSlotAt(mouseX, mouseY, 3, left, top);
            SlotAction action = actions.get(slot);
            if (action != null) {
                action.run();
            }
        }

        @Override
        protected void keyTyped(char typedChar, int keyCode) throws IOException {
            if (keyCode == 1) {
                mc.displayGuiScreen(parent);
                return;
            }
            super.keyTyped(typedChar, keyCode);
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            this.drawDefaultBackground();
            drawInventoryScreen(this, "PvP Modes", mouseX, mouseY, 3, left, top, slotItems);
            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }
    }

    public static class GuiActiveMatches extends GuiScreen {
        private final GuiScreen parent;
        private final List<PacketOpenPvpMenu.ActiveMatchEntry> matches;
        private final Map<Integer, SlotAction> actions = new HashMap<Integer, SlotAction>();
        private final Map<Integer, ItemStack> slotItems = new HashMap<Integer, ItemStack>();
        private int rows = 6;
        private int left;
        private int top;

        public GuiActiveMatches(GuiScreen parent, List<PacketOpenPvpMenu.ActiveMatchEntry> matches) {
            this.parent = parent;
            this.matches = matches;
        }

        @Override
        public void initGui() {
            super.initGui();
            left = (this.width - COLS * SLOT_SIZE) / 2;
            top = (this.height - rows * SLOT_SIZE) / 2;
            actions.clear();
            slotItems.clear();

            slotItems.put(0, createNamedItem(new ItemStack(Items.ARROW), TextFormatting.YELLOW + "Back", "Return to previous menu"));
            actions.put(0, new SlotAction() {
                @Override
                public void run() {
                    mc.displayGuiScreen(parent);
                }
            });

            slotItems.put(8, createNamedItem(new ItemStack(Items.CLOCK), TextFormatting.AQUA + "Refresh", "Refresh active games"));
            actions.put(8, new SlotAction() {
                @Override
                public void run() {
                    ModNetwork.CHANNEL.sendToServer(PacketPvpMenuAction.refresh());
                }
            });

            if (matches.isEmpty()) {
                Item barrier = Item.getItemFromBlock(Blocks.BARRIER);
                slotItems.put(22, createNamedItem(new ItemStack(barrier), TextFormatting.RED + "No Active Matches", "Wait for players to start duels"));
                return;
            }

            List<Integer> dataSlots = new ArrayList<Integer>();
            for (int slot = 9; slot < rows * COLS; slot++) {
                dataSlots.add(slot);
            }

            int pointer = 0;
            for (int i = 0; i < matches.size(); i++) {
                final PacketOpenPvpMenu.ActiveMatchEntry match = matches.get(i);
                final SlotAction spectateAction = new SlotAction() {
                    @Override
                    public void run() {
                        ModNetwork.CHANNEL.sendToServer(PacketPvpMenuAction.spectate(match.matchId));
                        mc.displayGuiScreen(null);
                    }
                };

                if (isDebugSoloEntry(match)) {
                    if (pointer >= dataSlots.size()) {
                        break;
                    }
                    int soloSlot = dataSlots.get(pointer++);
                    ItemStack soloHead = createPlayerHead(match.firstPlayer, TextFormatting.GOLD + match.firstPlayer, "Debug Solo #" + match.matchId + " [" + match.status + "]");
                    slotItems.put(soloSlot, soloHead);
                    actions.put(soloSlot, spectateAction);
                    continue;
                }

                if (pointer + 1 >= dataSlots.size()) {
                    break;
                }
                int firstSlot = dataSlots.get(pointer++);
                int secondSlot = dataSlots.get(pointer++);

                ItemStack firstHead = createPlayerHead(match.firstPlayer, TextFormatting.GOLD + match.firstPlayer, "Match #" + match.matchId + " vs " + match.secondPlayer + " [" + match.status + "]");
                ItemStack secondHead = createPlayerHead(match.secondPlayer, TextFormatting.GOLD + match.secondPlayer, "Match #" + match.matchId + " vs " + match.firstPlayer + " [" + match.status + "]");
                slotItems.put(firstSlot, firstHead);
                slotItems.put(secondSlot, secondHead);

                actions.put(firstSlot, spectateAction);
                actions.put(secondSlot, spectateAction);
            }
        }

        private boolean isDebugSoloEntry(PacketOpenPvpMenu.ActiveMatchEntry entry) {
            return entry.status != null && entry.status.startsWith("Debug Solo");
        }

        @Override
        protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            if (mouseButton != 0) {
                return;
            }
            int slot = getSlotAt(mouseX, mouseY, rows, left, top);
            SlotAction action = actions.get(slot);
            if (action != null) {
                action.run();
            }
        }

        @Override
        protected void keyTyped(char typedChar, int keyCode) throws IOException {
            if (keyCode == 1) {
                mc.displayGuiScreen(parent);
                return;
            }
            super.keyTyped(typedChar, keyCode);
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            this.drawDefaultBackground();
            drawInventoryScreen(this, "Active Games", mouseX, mouseY, rows, left, top, slotItems);
            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }
    }

    private static void drawInventoryScreen(GuiScreen screen, String title, int mouseX, int mouseY, int rows, int left, int top, Map<Integer, ItemStack> slotItems) {
        int panelLeft = left - 8;
        int panelTop = top - 22;
        int panelRight = left + COLS * SLOT_SIZE + 8;
        int panelBottom = top + rows * SLOT_SIZE + 8;
        screen.drawRect(panelLeft, panelTop, panelRight, panelBottom, 0xAA111111);
        screen.drawCenteredString(screen.mc.fontRenderer, title, screen.width / 2, panelTop + 8, 0xFFFFFF);

        for (int slot = 0; slot < rows * COLS; slot++) {
            int x = left + (slot % COLS) * SLOT_SIZE;
            int y = top + (slot / COLS) * SLOT_SIZE;
            screen.drawRect(x, y, x + 16, y + 16, 0xFF2A2A2A);
            screen.drawRect(x + 1, y + 1, x + 15, y + 15, 0xFF151515);
            ItemStack stack = slotItems.get(slot);
            if (stack != null && !stack.isEmpty()) {
                screen.mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
                screen.mc.getRenderItem().renderItemOverlayIntoGUI(screen.mc.fontRenderer, stack, x, y, null);
            }
        }

        int hoveredSlot = getSlotAt(mouseX, mouseY, rows, left, top);
        ItemStack hovered = slotItems.get(hoveredSlot);
        if (hovered != null && !hovered.isEmpty()) {
            ITooltipFlag flag = screen.mc.gameSettings.advancedItemTooltips ? ITooltipFlag.TooltipFlags.ADVANCED : ITooltipFlag.TooltipFlags.NORMAL;
            screen.drawHoveringText(hovered.getTooltip(screen.mc.player, flag), mouseX, mouseY);
        }
    }

    private static int getSlotAt(int mouseX, int mouseY, int rows, int left, int top) {
        if (mouseX < left || mouseY < top || mouseX >= left + COLS * SLOT_SIZE || mouseY >= top + rows * SLOT_SIZE) {
            return -1;
        }
        int col = (mouseX - left) / SLOT_SIZE;
        int row = (mouseY - top) / SLOT_SIZE;
        return row * COLS + col;
    }

    private static ItemStack createPlayerHead(String owner, String name, String lore) {
        ItemStack head = new ItemStack(Items.SKULL, 1, 3);
        NBTTagCompound tag = head.hasTagCompound() ? head.getTagCompound() : new NBTTagCompound();
        tag.setString("SkullOwner", owner);
        NBTTagCompound display = tag.hasKey("display", 10) ? tag.getCompoundTag("display") : new NBTTagCompound();
        display.setString("Name", name);
        NBTTagList loreList = new NBTTagList();
        loreList.appendTag(new net.minecraft.nbt.NBTTagString(TextFormatting.GRAY + lore));
        loreList.appendTag(new net.minecraft.nbt.NBTTagString(TextFormatting.YELLOW + "Click to spectate"));
        display.setTag("Lore", loreList);
        tag.setTag("display", display);
        head.setTagCompound(tag);
        return head;
    }
}
