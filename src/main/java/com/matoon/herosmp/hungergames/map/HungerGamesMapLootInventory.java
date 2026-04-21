package com.matoon.herosmp.hungergames.map;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Tabbed chest inventory for the HG loot pool editor.
 *
 * Row 0 layout (slots 0–8):
 *   0 = Phase 1 tab      (Book)
 *   1 = Phase 2 tab      (Book)
 *   2 = Phase 3 tab      (Book)
 *   3 = All Phases tab   (Book)
 *   4–7 = Gray glass pane fillers
 *   8 = Staging slot — drag an item here, close the chest, and the property editor opens.
 *
 * Rows 1–5 (slots 9–53): 45 editable loot item slots for the active tab.
 *
 * Tab switching is triggered by the WorldManager when it detects a tab button click
 * (the manager intercepts `setInventorySlotContents` calls from slot 0–3).
 */
public class HungerGamesMapLootInventory extends InventoryBasic {

    public static final int SIZE = 54;
    public static final int GRID_START = 9;  // first editable slot
    public static final int GRID_SIZE  = 45; // rows 1–5

    private static final String[] TAB_NAMES = { "Phase 1", "Phase 2", "Phase 3", "All Phases" };

    private final String mapName;
    private int activeTab = 0; // 0=Phase1, 1=Phase2, 2=Phase3, 3=AllPhases

    // In-memory contents for each tab (items only, not the tab buttons / fillers).
    @SuppressWarnings("unchecked")
    private final List<ItemStack>[] tabContents = new List[4];

    // Called when a player clicks a tab button (slots 0–3). The consumer receives
    // the new tab index so the WorldManager can refresh the client-side inventory.
    private Consumer<Integer> tabClickCallback = null;

    public HungerGamesMapLootInventory(String mapName) {
        super("HG Loot Pool: " + mapName, false, SIZE);
        this.mapName = mapName;
        for (int i = 0; i < 4; i++) tabContents[i] = new ArrayList<>();
        buildHeader();
        refreshGrid();
    }

    public void setTabClickCallback(Consumer<Integer> callback) {
        this.tabClickCallback = callback;
    }

    /**
     * Intercept slot writes from the container so we can detect when a player
     * clicks a tab button (slots 0–3) or a filler/staging slot (4–8).
     * The container calls setInventorySlotContents when the player picks up or
     * places an item in a slot. When the player clicks a tab button that has no
     * item on their cursor, vanilla swaps the cursor (empty) into the slot, which
     * triggers this method with an empty stack — we use that to switch tabs.
     */
    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        if (index >= 0 && index <= 3) {
            // Tab button clicked — switch tab and restore button (ignore the incoming stack).
            if (tabClickCallback != null) tabClickCallback.accept(index);
            else setActiveTab(index);
            buildHeader(); // Ensure header is always restored correctly.
            return;
        }
        if (index >= 4 && index <= 7) {
            // Filler slot — prevent players from removing the filler panes.
            return;
        }
        // Slots 8–53: normal behaviour (staging slot and loot grid).
        super.setInventorySlotContents(index, stack);
    }

    public String getMapName() { return mapName; }
    public int getActiveTab()  { return activeTab; }

    // -------------------------------------------------------------------------
    // Loading
    // -------------------------------------------------------------------------

    /**
     * Populate all four tab contents from saved config data.
     * Must be called after construction, before opening the GUI.
     */
    public void loadTabContents(List<ItemStack> p1, List<ItemStack> p2,
                                List<ItemStack> p3, List<ItemStack> all) {
        copyList(tabContents[0], p1);
        copyList(tabContents[1], p2);
        copyList(tabContents[2], p3);
        copyList(tabContents[3], all);
        refreshGrid();
    }

    // -------------------------------------------------------------------------
    // Tab switching
    // -------------------------------------------------------------------------

    /**
     * Flush the current grid into the active tab's content list, switch tabs,
     * then repopulate the grid with the new tab's items.
     */
    public void setActiveTab(int tab) {
        if (tab < 0 || tab > 3) return;
        flushGridToTab(activeTab);
        activeTab = tab;
        refreshGrid();
        buildHeader();
    }

    // -------------------------------------------------------------------------
    // Read-back helpers (called by WorldManager on chest close)
    // -------------------------------------------------------------------------

    /** Flush in-progress grid edits, then return items for the specified tab. */
    public List<ItemStack> getTabItems(int tab) {
        if (tab == activeTab) flushGridToTab(activeTab);
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack s : tabContents[tab]) {
            if (!s.isEmpty()) result.add(s.copy());
        }
        return result;
    }

    /**
     * Returns the item in the staging slot (slot 8) if it is not the empty-marker paper.
     * Returns ItemStack.EMPTY if nothing meaningful is staged.
     */
    public ItemStack getStagingItem() {
        ItemStack staged = getStackInSlot(8);
        if (staged.isEmpty() || staged.getItem() == Items.PAPER) return ItemStack.EMPTY;
        return staged.copy();
    }

    /** Reset the staging slot to its empty-marker state. */
    public void clearStaging() {
        super.setInventorySlotContents(8, makeStagingMarker());
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void buildHeader() {
        for (int i = 0; i < 4; i++) {
            ItemStack tab = new ItemStack(Items.BOOK);
            if (i == activeTab) {
                tab.setStackDisplayName(TextFormatting.GOLD + "[" + TAB_NAMES[i] + "]");
            } else {
                tab.setStackDisplayName(TextFormatting.GRAY + TAB_NAMES[i]);
            }
            super.setInventorySlotContents(i, tab);
        }
        // Filler glass panes (slots 4–7)
        ItemStack filler = new ItemStack(Blocks.STAINED_GLASS_PANE, 1, 7); // gray
        filler.setStackDisplayName(TextFormatting.DARK_GRAY + " ");
        for (int i = 4; i <= 7; i++) super.setInventorySlotContents(i, filler.copy());
        // Staging slot (slot 8)
        super.setInventorySlotContents(8, makeStagingMarker());
    }

    private static ItemStack makeStagingMarker() {
        ItemStack marker = new ItemStack(Items.PAPER);
        marker.setStackDisplayName(TextFormatting.YELLOW + "Place item here → close to edit properties");
        return marker;
    }

    private void refreshGrid() {
        List<ItemStack> items = tabContents[activeTab];
        for (int i = 0; i < GRID_SIZE; i++) {
            super.setInventorySlotContents(GRID_START + i,
                i < items.size() ? items.get(i).copy() : ItemStack.EMPTY);
        }
    }

    private void flushGridToTab(int tab) {
        List<ItemStack> list = tabContents[tab];
        list.clear();
        for (int i = 0; i < GRID_SIZE; i++) {
            ItemStack s = getStackInSlot(GRID_START + i);
            if (!s.isEmpty()) list.add(s.copy());
        }
    }

    private static void copyList(List<ItemStack> target, List<ItemStack> source) {
        target.clear();
        for (ItemStack s : source) {
            if (!s.isEmpty()) target.add(s.copy());
        }
    }
}
