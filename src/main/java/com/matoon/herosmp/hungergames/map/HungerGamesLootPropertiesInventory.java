package com.matoon.herosmp.hungergames.map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IInteractionObject;

import java.util.ArrayList;
import java.util.List;

/**
 * All-in-one, tabbed, paged loot properties editor. 54 slots (6-row chest).
 *
 * Layout:
 *   Row 0 (0-8):   Phase tab buttons (0-3) + fillers (4-8)
 *   Row 1 (9-17):  [9]  Selected item display (read-only)
 *                  [10] Weight button
 *                  [11] Global Max button
 *                  [12] Per-Chest Max button
 *                  [13] Min Count button
 *                  [14] Max Count button
 *                  [15-17] fillers
 *   Rows 2-5 (18-53):
 *                  [18-50] Item list grid (33 slots per page)
 *                  [51]    Previous page button
 *                  [52]    Page indicator
 *                  [53]    Next page button
 */
public class HungerGamesLootPropertiesInventory extends InventoryBasic implements IInteractionObject {

    public static final int SIZE       = 54;
    public static final int SLOT_SELECTED = 9;
    public static final int SLOT_WEIGHT   = 10;
    public static final int SLOT_GMAX     = 11;
    public static final int SLOT_CMAX     = 12;
    public static final int SLOT_MIN      = 13;
    public static final int SLOT_MAX      = 14;
    public static final int GRID_START    = 18;
    public static final int PAGE_SIZE     = 33;  // slots 18-50
    public static final int SLOT_PREV     = 51;
    public static final int SLOT_PAGE     = 52;
    public static final int SLOT_NEXT     = 53;

    private static final String[] TAB_NAMES = {"Phase 1", "Phase 2", "Phase 3", "All Phases"};

    @SuppressWarnings("unchecked")
    private final List<ItemStack>[] phases = new List[4];

    private int activeTab     = 0;
    private int selectedIndex = -1; // absolute index into phases[activeTab], -1 = none

    // One page per tab.
    private final int[] tabPage = new int[4];

    public HungerGamesLootPropertiesInventory(List<ItemStack> p1, List<ItemStack> p2,
                                               List<ItemStack> p3, List<ItemStack> all) {
        super("Loot Properties", false, SIZE);
        phases[0] = copyList(p1);
        phases[1] = copyList(p2);
        phases[2] = copyList(p3);
        phases[3] = copyList(all);
        rebuildAll();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public int getActiveTab()     { return activeTab; }
    public int getSelectedIndex() { return selectedIndex; }
    public List<ItemStack> getPhase(int tab) { return phases[tab]; }

    public void setActiveTab(int tab) {
        if (tab < 0 || tab > 3 || tab == activeTab) return;
        activeTab     = tab;
        selectedIndex = -1;
        rebuildAll();
    }

    public void prevPage() {
        if (tabPage[activeTab] > 0) {
            tabPage[activeTab]--;
            // Deselect if selected item scrolled off screen.
            if (selectedIndex >= 0 && !isOnCurrentPage(selectedIndex)) selectedIndex = -1;
            rebuildAll();
        }
    }

    public void nextPage() {
        int maxPage = maxPage();
        if (tabPage[activeTab] < maxPage) {
            tabPage[activeTab]++;
            if (selectedIndex >= 0 && !isOnCurrentPage(selectedIndex)) selectedIndex = -1;
            rebuildAll();
        }
    }

    /** Select the item at the given grid inventory slot (18-50). */
    public void selectItem(int gridSlot) {
        int pageIdx = gridSlot - GRID_START;
        int absIdx  = tabPage[activeTab] * PAGE_SIZE + pageIdx;
        if (absIdx < 0 || absIdx >= phases[activeTab].size()) return;
        selectedIndex = absIdx;
        rebuildControlRow();
        rebuildGrid();
    }

    /** Adjust a property on the selected item. */
    public void adjustProperty(int propSlot, int delta) {
        if (selectedIndex < 0 || selectedIndex >= phases[activeTab].size()) return;
        ItemStack item = phases[activeTab].get(selectedIndex);
        if (item.isEmpty()) return;

        int w    = HungerGamesLootEntry.getWeight(item);
        int gmax = HungerGamesLootEntry.getGlobalMax(item);
        int cmax = HungerGamesLootEntry.getPerChestMax(item);
        int min  = HungerGamesLootEntry.getMinCount(item);
        int max  = HungerGamesLootEntry.getMaxCount(item);

        switch (propSlot) {
            case SLOT_WEIGHT: w    = clamp(w    + delta, 1,  1000); break;
            case SLOT_GMAX:   gmax = clamp(gmax + delta, 0,   999); break;
            case SLOT_CMAX:   cmax = clamp(cmax + delta, 1,   999); break;
            case SLOT_MIN:    min  = clamp(min  + delta, 1,    64); break;
            case SLOT_MAX:    max  = clamp(max  + delta, 1,    64); break;
            default: return;
        }
        if (min > max) { if (propSlot == SLOT_MIN) max = min; else min = max; }

        HungerGamesLootEntry.embed(item, w, gmax, cmax, min, max);
        rebuildControlRow();
    }

    // -------------------------------------------------------------------------
    // Internal rebuild
    // -------------------------------------------------------------------------

    private void rebuildAll() {
        buildTabRow();
        rebuildControlRow();
        rebuildGrid();
    }

    private void buildTabRow() {
        for (int i = 0; i < 4; i++) {
            ItemStack tab = new ItemStack(Items.BOOK);
            tab.setStackDisplayName(i == activeTab
                    ? TextFormatting.GOLD + "[" + TAB_NAMES[i] + "]"
                    : TextFormatting.GRAY + TAB_NAMES[i]);
            super.setInventorySlotContents(i, tab);
        }
        ItemStack filler = makeFiller();
        for (int i = 4; i <= 8; i++) super.setInventorySlotContents(i, filler.copy());
    }

    private void rebuildControlRow() {
        if (selectedIndex >= 0 && selectedIndex < phases[activeTab].size()) {
            ItemStack live = phases[activeTab].get(selectedIndex);
            ItemStack sel  = live.copy();
            addLore(sel,
                TextFormatting.GRAY + "Weight: "        + HungerGamesLootEntry.getWeight(live),
                TextFormatting.GRAY + "Global Max: "    + HungerGamesLootEntry.getGlobalMax(live),
                TextFormatting.GRAY + "Per-Chest Max: " + HungerGamesLootEntry.getPerChestMax(live),
                TextFormatting.GRAY + "Min Count: "     + HungerGamesLootEntry.getMinCount(live),
                TextFormatting.GRAY + "Max Count: "     + HungerGamesLootEntry.getMaxCount(live));
            super.setInventorySlotContents(SLOT_SELECTED, sel);

            super.setInventorySlotContents(SLOT_WEIGHT, makeButton(TextFormatting.YELLOW + "Weight",
                    HungerGamesLootEntry.getWeight(live),
                    "Spawn chance relative to other items.",
                    "Range: 1-1000   Default: 10"));
            super.setInventorySlotContents(SLOT_GMAX, makeButton(TextFormatting.AQUA + "Global Max",
                    HungerGamesLootEntry.getGlobalMax(live),
                    "Max spawned across ALL chests per fill. 0 = unlimited.",
                    "Range: 0-999   Default: 0"));
            super.setInventorySlotContents(SLOT_CMAX, makeButton(TextFormatting.GREEN + "Per-Chest Max",
                    HungerGamesLootEntry.getPerChestMax(live),
                    "Max copies placed in a single chest.",
                    "Range: 1-999   Default: 1"));
            super.setInventorySlotContents(SLOT_MIN, makeButton(TextFormatting.GOLD + "Min Stack Size",
                    HungerGamesLootEntry.getMinCount(live),
                    "Minimum items in each placed stack.",
                    "Range: 1-64   Default: 1"));
            super.setInventorySlotContents(SLOT_MAX, makeButton(TextFormatting.GOLD + "Max Stack Size",
                    HungerGamesLootEntry.getMaxCount(live),
                    "Maximum items in each placed stack.",
                    "Range: 1-64   Default: 1"));
        } else {
            ItemStack placeholder = new ItemStack(Blocks.BARRIER);
            placeholder.setStackDisplayName(TextFormatting.RED + "No item selected");
            addLore(placeholder, TextFormatting.GRAY + "Click an item in the list below.");
            super.setInventorySlotContents(SLOT_SELECTED, placeholder);
            ItemStack filler = makeFiller();
            for (int s = SLOT_WEIGHT; s <= SLOT_MAX; s++) super.setInventorySlotContents(s, filler.copy());
        }
        ItemStack filler = makeFiller();
        for (int s = 15; s <= 17; s++) super.setInventorySlotContents(s, filler.copy());
    }

    private void rebuildGrid() {
        List<ItemStack> list   = phases[activeTab];
        int             offset = tabPage[activeTab] * PAGE_SIZE;

        for (int i = 0; i < PAGE_SIZE; i++) {
            int slot   = GRID_START + i;
            int absIdx = offset + i;
            if (absIdx < list.size()) {
                ItemStack copy = list.get(absIdx).copy();
                if (absIdx == selectedIndex) {
                    copy.setStackDisplayName(TextFormatting.GOLD + "► " + list.get(absIdx).getDisplayName());
                }
                super.setInventorySlotContents(slot, copy);
            } else {
                super.setInventorySlotContents(slot, ItemStack.EMPTY);
            }
        }
        buildPaginationRow();
    }

    private void buildPaginationRow() {
        int page    = tabPage[activeTab];
        int maxPage = maxPage();
        int total   = phases[activeTab].size();
        int offset  = page * PAGE_SIZE;

        if (page > 0) {
            ItemStack prev = new ItemStack(Items.ARROW);
            prev.setStackDisplayName(TextFormatting.YELLOW + "◄ Previous Page");
            addLore(prev, TextFormatting.GRAY + "Page " + page + " of " + (maxPage + 1));
            super.setInventorySlotContents(SLOT_PREV, prev);
        } else {
            super.setInventorySlotContents(SLOT_PREV, makeFiller());
        }

        ItemStack indicator = new ItemStack(Items.MAP);
        indicator.setStackDisplayName(TextFormatting.WHITE + "Page " + (page + 1) + " / " + (maxPage + 1));
        addLore(indicator,
                TextFormatting.GRAY + "Items " + (total == 0 ? 0 : offset + 1)
                        + "-" + Math.min(offset + PAGE_SIZE, total) + " of " + total);
        super.setInventorySlotContents(SLOT_PAGE, indicator);

        if (page < maxPage) {
            ItemStack next = new ItemStack(Items.ARROW);
            next.setStackDisplayName(TextFormatting.YELLOW + "Next Page ►");
            addLore(next, TextFormatting.GRAY + "Page " + (page + 2) + " of " + (maxPage + 1));
            super.setInventorySlotContents(SLOT_NEXT, next);
        } else {
            super.setInventorySlotContents(SLOT_NEXT, makeFiller());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int maxPage() {
        int size = phases[activeTab].size();
        return size == 0 ? 0 : (size - 1) / PAGE_SIZE;
    }

    private boolean isOnCurrentPage(int absIdx) {
        int offset = tabPage[activeTab] * PAGE_SIZE;
        return absIdx >= offset && absIdx < offset + PAGE_SIZE;
    }

    private static ItemStack makeButton(String label, int value, String... loreLines) {
        int meta = (value == 0) ? 14 : 5;
        ItemStack btn = new ItemStack(Blocks.STAINED_GLASS_PANE, Math.max(1, Math.min(64, value)), meta);
        btn.setStackDisplayName(label + TextFormatting.WHITE + ": " + value);
        String[] fullLore = new String[loreLines.length + 2];
        System.arraycopy(loreLines, 0, fullLore, 0, loreLines.length);
        fullLore[loreLines.length]     = TextFormatting.DARK_GRAY + "Left-click: +1   Right-click: -1";
        fullLore[loreLines.length + 1] = TextFormatting.DARK_GRAY + "Shift+click: x10";
        addLore(btn, fullLore);
        return btn;
    }

    private static ItemStack makeFiller() {
        ItemStack f = new ItemStack(Blocks.STAINED_GLASS_PANE, 1, 7);
        f.setStackDisplayName(TextFormatting.DARK_GRAY + " ");
        return f;
    }

    private static void addLore(ItemStack stack, String... lines) {
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        NBTTagCompound display = tag.hasKey("display") ? tag.getCompoundTag("display") : new NBTTagCompound();
        NBTTagList lore = new NBTTagList();
        for (String line : lines) lore.appendTag(new NBTTagString(line));
        display.setTag("Lore", lore);
        tag.setTag("display", display);
        stack.setTagCompound(tag);
    }

    private static List<ItemStack> copyList(List<ItemStack> src) {
        List<ItemStack> out = new ArrayList<>();
        if (src != null) for (ItemStack s : src) if (!s.isEmpty()) out.add(s.copy());
        return out;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override public Container createContainer(InventoryPlayer playerInv, EntityPlayer player) {
        return new HungerGamesLootPropertiesContainer(playerInv, this);
    }
    @Override public String getGuiID() { return "minecraft:container"; }
    @Override public ITextComponent getDisplayName() { return new TextComponentString(getName()); }
}
