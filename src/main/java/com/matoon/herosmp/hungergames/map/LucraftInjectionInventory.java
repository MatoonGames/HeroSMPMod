package com.matoon.herosmp.hungergames.map;

import com.matoon.herosmp.integration.LucraftInjectionEntry;
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
 * Tabbed chest inventory for the HG injection pool editor in configure-map mode.
 *
 * Layout (54 slots, 6 rows):
 *   Row 0, slots 0-8:
 *     [0-3]  Phase tab buttons (Phase 1 / Phase 2 / Phase 3 / All Phases)
 *     [4-8]  Fillers
 *   Rows 1-5, slots 9-53: injection grid + pagination
 *     Slots 9-50  = injection grid (42 slots per page)
 *     Slot 51     = previous page
 *     Slot 52     = page indicator
 *     Slot 53     = next page
 *
 * Admins place real {@code lucraftcore:injection} ItemStacks into the grid slots.
 * Only items accepted by {@link LucraftInjectionEntry#hasInjectionTag} are kept.
 */
public class LucraftInjectionInventory extends InventoryBasic implements IInteractionObject {

    public static final int SIZE      = 54;
    public static final int GRID_START = 9;
    public static final int PAGE_SIZE  = 42;  // slots 9-50
    public static final int SLOT_PREV  = 51;
    public static final int SLOT_PAGE  = 52;
    public static final int SLOT_NEXT  = 53;

    private static final String[] TAB_NAMES = {"Phase 1", "Phase 2", "Phase 3", "All Phases"};

    private final String mapName;

    @SuppressWarnings("unchecked")
    private final List<ItemStack>[] tabContents = new List[4];
    private final int[] tabPage = new int[4];
    private int activeTab = 3; // default to "All Phases"

    /** When true, the phase tab row is hidden (all fillers) and tab switching is disabled. */
    private boolean hideTabs = false;

    public LucraftInjectionInventory(String mapName) {
        super("Injections: " + mapName, false, SIZE);
        this.mapName = mapName;
        for (int i = 0; i < 4; i++) tabContents[i] = new ArrayList<>();
        buildHeader();
        refreshGrid();
    }

    // IInteractionObject
    @Override public Container createContainer(InventoryPlayer playerInv, EntityPlayer player) {
        return new LucraftInjectionContainer(playerInv, this);
    }
    @Override public String getGuiID() { return "minecraft:container"; }
    @Override public ITextComponent getDisplayName() { return new TextComponentString(getName()); }

    public String getMapName()  { return mapName; }
    public int getActiveTab()   { return activeTab; }
    public boolean isHideTabs() { return hideTabs; }

    /** Call before the inventory is opened to hide the phase tab row. */
    public void setHideTabs(boolean hide) {
        this.hideTabs = hide;
        buildHeader();
    }

    // -------------------------------------------------------------------------
    // Loading / read-back
    // -------------------------------------------------------------------------

    /**
     * Populates all four phase tabs from the given lists.
     * Uses {@link LucraftInjectionEntry#hasInjectionTag} so that previously saved
     * stacks always appear even if the LucraftCore registry isn't fully populated yet.
     */
    public void loadTabContents(List<ItemStack> p1, List<ItemStack> p2,
                                List<ItemStack> p3, List<ItemStack> all) {
        tabContents[0] = filterCopy(p1);
        tabContents[1] = filterCopy(p2);
        tabContents[2] = filterCopy(p3);
        tabContents[3] = filterCopy(all);
        buildHeader();
        refreshGrid();
    }

    /**
     * Returns all valid injection ItemStacks currently stored in the given tab.
     * Flushes the visible grid page first so no in-progress edits are lost.
     */
    public List<ItemStack> getTabItems(int tab) {
        if (tab == activeTab) flushGridToTab(activeTab);
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack s : tabContents[tab]) {
            if (!s.isEmpty()) result.add(s.copy());
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Tab switching
    // -------------------------------------------------------------------------

    public void setActiveTab(int tab) {
        if (hideTabs) return;
        if (tab < 0 || tab > 3 || tab == activeTab) return;
        flushGridToTab(activeTab);
        activeTab = tab;
        buildHeader();
        refreshGrid();
    }

    // -------------------------------------------------------------------------
    // Pagination
    // -------------------------------------------------------------------------

    public void prevPage() {
        flushGridToTab(activeTab);
        if (tabPage[activeTab] > 0) tabPage[activeTab]--;
        refreshGrid();
    }

    public void nextPage() {
        flushGridToTab(activeTab);
        int max = maxPage();
        if (tabPage[activeTab] < max) tabPage[activeTab]++;
        refreshGrid();
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
        if (hideTabs) {
            ItemStack filler = makeFiller();
            for (int i = 0; i <= 8; i++) super.setInventorySlotContents(i, filler.copy());
            return;
        }
        for (int i = 0; i < 4; i++) {
            ItemStack tab = new ItemStack(Items.BOOK);
            tab.setStackDisplayName(i == activeTab
                    ? TextFormatting.GOLD + "[" + TAB_NAMES[i] + "]"
                    : TextFormatting.GRAY + TAB_NAMES[i]);
            addLore(tab, TextFormatting.GRAY + (tabContents[i].size() + " injection(s)"));
            super.setInventorySlotContents(i, tab);
        }
        ItemStack filler = makeFiller();
        for (int i = 4; i <= 8; i++) super.setInventorySlotContents(i, filler.copy());
    }

    private void refreshGrid() {
        List<ItemStack> list   = tabContents[activeTab];
        int             offset = pageOffset();
        for (int i = 0; i < PAGE_SIZE; i++) {
            int listIdx = offset + i;
            super.setInventorySlotContents(GRID_START + i,
                    listIdx < list.size() ? list.get(listIdx).copy() : ItemStack.EMPTY);
        }
        buildPaginationRow();
    }

    private void buildPaginationRow() {
        int page    = tabPage[activeTab];
        int maxPage = maxPage();
        int total   = tabContents[activeTab].size();

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
        addLore(indicator, TextFormatting.GRAY + (total + " injection(s) in this tab"));
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

    /**
     * Reads the visible grid slots back into the tab's content list,
     * then strips trailing empty entries.
     */
    private void flushGridToTab(int tab) {
        List<ItemStack> list   = tabContents[tab];
        int             offset = tabPage[tab] * PAGE_SIZE;

        // Extend list if needed.
        while (list.size() < offset) list.add(ItemStack.EMPTY);

        for (int i = 0; i < PAGE_SIZE; i++) {
            ItemStack s      = getStackInSlot(GRID_START + i);
            int       listIdx = offset + i;
            if (listIdx < list.size()) {
                list.set(listIdx, s.copy());
            } else if (!s.isEmpty()) {
                list.add(s.copy());
            }
        }

        // Strip trailing empty slots.
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).isEmpty()) list.remove(i);
            else break;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static List<ItemStack> filterCopy(List<ItemStack> src) {
        List<ItemStack> out = new ArrayList<>();
        if (src == null) return out;
        for (ItemStack s : src) {
            if (LucraftInjectionEntry.hasInjectionTag(s)) out.add(s.copy());
        }
        return out;
    }

    private static ItemStack makeFiller() {
        ItemStack f = new ItemStack(Blocks.STAINED_GLASS_PANE, 1, 2); // magenta
        f.setStackDisplayName(TextFormatting.DARK_GRAY + " ");
        return f;
    }

    private static void addLore(ItemStack stack, String... lines) {
        NBTTagCompound tag     = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        NBTTagCompound display = tag.hasKey("display") ? tag.getCompoundTag("display") : new NBTTagCompound();
        NBTTagList     lore    = new NBTTagList();
        for (String line : lines) lore.appendTag(new NBTTagString(line));
        display.setTag("Lore", lore);
        tag.setTag("display", display);
        stack.setTagCompound(tag);
    }
}
