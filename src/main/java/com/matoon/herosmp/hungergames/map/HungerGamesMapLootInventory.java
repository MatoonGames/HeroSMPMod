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
import net.minecraft.world.IInteractionObject;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Tabbed, paged chest inventory for the HG loot pool editor.
 *
 * Row 0 (slots 0-8):
 *   0-3  = Phase tab buttons — click to switch tab
 *   4-8  = Gray glass pane fillers
 *
 * Rows 1-5 (slots 9-53):
 *   9-50 = Loot item grid (42 slots per page, 6 rows × 7 cols + partial)
 *          Actually laid out as 42 sequential slots across the 5 rows,
 *          leaving the last 3 slots of the last row for pagination.
 *   51   = Previous page button
 *   52   = Page indicator (read-only)
 *   53   = Next page button
 */
public class HungerGamesMapLootInventory extends InventoryBasic implements IInteractionObject {

    public static final int SIZE        = 54;
    public static final int GRID_START  = 9;
    public static final int PAGE_SIZE   = 42;   // slots 9-50 are the grid
    public static final int SLOT_PREV   = 51;
    public static final int SLOT_PAGE   = 52;
    public static final int SLOT_NEXT   = 53;

    // Keep GRID_SIZE as legacy alias so WorldManager compile doesn't break
    public static final int GRID_SIZE   = PAGE_SIZE;

    private static final String[] TAB_NAMES = {"Phase 1", "Phase 2", "Phase 3", "All Phases"};

    private final String mapName;
    private int activeTab = 0;

    // One page index per tab so switching tabs remembers the position.
    private final int[] tabPage = new int[4];

    @SuppressWarnings("unchecked")
    private final List<ItemStack>[] tabContents = new List[4];

    public HungerGamesMapLootInventory(String mapName) {
        super("HG Loot Pool: " + mapName, false, SIZE);
        this.mapName = mapName;
        for (int i = 0; i < 4; i++) tabContents[i] = new ArrayList<>();
        buildHeader();
        refreshGrid();
    }

    // IInteractionObject
    @Override public Container createContainer(InventoryPlayer playerInv, EntityPlayer player) {
        return new HungerGamesLootContainer(playerInv, this);
    }
    @Override public String getGuiID() { return "minecraft:container"; }
    @Override public ITextComponent getDisplayName() { return new TextComponentString(getName()); }

    public String getMapName() { return mapName; }
    public int getActiveTab()  { return activeTab; }

    // -------------------------------------------------------------------------
    // Loading
    // -------------------------------------------------------------------------

    public void loadTabContents(List<ItemStack> p1, List<ItemStack> p2,
                                List<ItemStack> p3, List<ItemStack> all) {
        copyList(tabContents[0], p1);
        copyList(tabContents[1], p2);
        copyList(tabContents[2], p3);
        copyList(tabContents[3], all);
        refreshGrid();
    }

    // -------------------------------------------------------------------------
    // Tab / page switching
    // -------------------------------------------------------------------------

    public void setActiveTab(int tab) {
        if (tab < 0 || tab > 3) return;
        flushGridToTab(activeTab);
        activeTab = tab;
        refreshGrid();
        buildHeader();
    }

    public void prevPage() {
        flushGridToTab(activeTab);
        if (tabPage[activeTab] > 0) tabPage[activeTab]--;
        refreshGrid();
    }

    public void nextPage() {
        flushGridToTab(activeTab);
        int maxPage = maxPage();
        if (tabPage[activeTab] < maxPage) tabPage[activeTab]++;
        refreshGrid();
    }

    // -------------------------------------------------------------------------
    // Read-back helpers
    // -------------------------------------------------------------------------

    /** Flush active grid then return all items for the requested tab (all pages). */
    public List<ItemStack> getTabItems(int tab) {
        if (tab == activeTab) flushGridToTab(activeTab);
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack s : tabContents[tab]) {
            if (!s.isEmpty()) result.add(s.copy());
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private int maxPage() {
        int size = tabContents[activeTab].size();
        return size == 0 ? 0 : (size - 1) / PAGE_SIZE;
    }

    private int pageOffset() {
        return tabPage[activeTab] * PAGE_SIZE;
    }

    private void buildHeader() {
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

    private void refreshGrid() {
        List<ItemStack> items = tabContents[activeTab];
        int offset = pageOffset();
        for (int i = 0; i < PAGE_SIZE; i++) {
            int listIdx = offset + i;
            super.setInventorySlotContents(GRID_START + i,
                    listIdx < items.size() ? items.get(listIdx).copy() : ItemStack.EMPTY);
        }
        buildPaginationRow();
    }

    private void buildPaginationRow() {
        int page    = tabPage[activeTab];
        int maxPage = maxPage();

        // Previous page
        if (page > 0) {
            ItemStack prev = new ItemStack(Items.ARROW);
            prev.setStackDisplayName(TextFormatting.YELLOW + "◄ Previous Page");
            addLore(prev, TextFormatting.GRAY + "Page " + page + " of " + (maxPage + 1));
            super.setInventorySlotContents(SLOT_PREV, prev);
        } else {
            super.setInventorySlotContents(SLOT_PREV, makeFiller());
        }

        // Page indicator
        ItemStack indicator = new ItemStack(Items.MAP);
        indicator.setStackDisplayName(TextFormatting.WHITE + "Page " + (page + 1) + " / " + (maxPage + 1));
        int total = tabContents[activeTab].size();
        addLore(indicator,
                TextFormatting.GRAY + "Showing items " + (pageOffset() + 1)
                        + "-" + Math.min(pageOffset() + PAGE_SIZE, total)
                        + " of " + total);
        super.setInventorySlotContents(SLOT_PAGE, indicator);

        // Next page
        if (page < maxPage) {
            ItemStack next = new ItemStack(Items.ARROW);
            next.setStackDisplayName(TextFormatting.YELLOW + "Next Page ►");
            addLore(next, TextFormatting.GRAY + "Page " + (page + 2) + " of " + (maxPage + 1));
            super.setInventorySlotContents(SLOT_NEXT, next);
        } else {
            super.setInventorySlotContents(SLOT_NEXT, makeFiller());
        }
    }

    /**
     * Flush the visible page of the grid back into tabContents.
     * Items outside the current page are untouched.
     */
    private void flushGridToTab(int tab) {
        List<ItemStack> list = tabContents[tab];
        int offset = tabPage[tab] * PAGE_SIZE;

        // Extend the list if needed.
        while (list.size() < offset) list.add(ItemStack.EMPTY);

        // Overwrite exactly the page range.
        for (int i = 0; i < PAGE_SIZE; i++) {
            ItemStack s = getStackInSlot(GRID_START + i);
            int listIdx = offset + i;
            if (listIdx < list.size()) {
                list.set(listIdx, s.copy());
            } else if (!s.isEmpty()) {
                list.add(s.copy());
            }
        }

        // Strip trailing empty entries.
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).isEmpty()) list.remove(i);
            else break;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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

    private static void copyList(List<ItemStack> target, List<ItemStack> source) {
        target.clear();
        for (ItemStack s : source) {
            if (!s.isEmpty()) target.add(s.copy());
        }
    }
}
