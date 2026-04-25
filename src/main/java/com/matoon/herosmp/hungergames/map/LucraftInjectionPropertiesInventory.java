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
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IInteractionObject;

import java.util.ArrayList;
import java.util.List;

/**
 * All-in-one, tabbed, paged injection properties editor. 54 slots (6-row chest).
 *
 * Layout:
 *   Row 0 (0-8):
 *     [0-3]  Phase tab buttons (Phase 1 / Phase 2 / Phase 3 / All Phases)
 *     [4-8]  Fillers
 *
 *   Row 1 (9-17) — control strip for the selected injection:
 *     [9]    Selected injection display (read-only)
 *     [10]   Weight button  (+/- left/right click, shift x10)
 *     [11]   Max-on-map button
 *     [12]   Global Min Injections button  (per-phase total)
 *     [13]   Global Max Injections button  (per-phase total)
 *     [14-17] Fillers
 *
 *   Rows 2-5 (18-53):
 *     [18-50] Injection list grid (33 slots per page)
 *     [51]    Previous page button
 *     [52]    Page indicator
 *     [53]    Next page button
 *
 * The global-min and global-max buttons modify per-phase integer values stored in
 * this inventory.  They are read back by the world manager when the container closes.
 */
public class LucraftInjectionPropertiesInventory extends InventoryBasic implements IInteractionObject {

    public static final int SIZE          = 54;
    public static final int SLOT_SELECTED = 9;
    public static final int SLOT_WEIGHT   = 10;
    public static final int SLOT_MAX_MAP  = 11;
    public static final int SLOT_GLOB_MIN = 12;
    public static final int SLOT_GLOB_MAX = 13;
    public static final int GRID_START    = 18;
    public static final int PAGE_SIZE     = 33;   // slots 18-50
    public static final int SLOT_PREV     = 51;
    public static final int SLOT_PAGE     = 52;
    public static final int SLOT_NEXT     = 53;

    private static final String[] TAB_NAMES = {"Phase 1", "Phase 2", "Phase 3", "All Phases"};

    @SuppressWarnings("unchecked")
    private final List<ItemStack>[] phases = new List[4];

    // Per-tab global min/max injection counts on the map.
    // Indices: 0=Phase1, 1=Phase2, 2=Phase3, 3=AllPhases
    private final int[] tabGlobalMin = {0, 0, 0, 3};
    private final int[] tabGlobalMax = {0, 0, 0, 8};

    private int activeTab     = 0;
    private int selectedIndex = -1;  // absolute index into phases[activeTab], -1 = none
    private final int[] tabPage = new int[4];

    /** When true, the phase tab row is hidden (all fillers) and tab switching is disabled. */
    private boolean hideTabs = false;

    public LucraftInjectionPropertiesInventory(
            List<ItemStack> p1, List<ItemStack> p2, List<ItemStack> p3, List<ItemStack> all,
            int minP1, int maxP1, int minP2, int maxP2,
            int minP3, int maxP3, int minAll, int maxAll) {
        super("Injection Properties", false, SIZE);
        phases[0] = copyList(p1);
        phases[1] = copyList(p2);
        phases[2] = copyList(p3);
        phases[3] = copyList(all);
        tabGlobalMin[0] = minP1;  tabGlobalMax[0] = maxP1;
        tabGlobalMin[1] = minP2;  tabGlobalMax[1] = maxP2;
        tabGlobalMin[2] = minP3;  tabGlobalMax[2] = maxP3;
        tabGlobalMin[3] = minAll; tabGlobalMax[3] = maxAll;
        rebuildAll();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public int getActiveTab()     { return activeTab; }
    public int getSelectedIndex() { return selectedIndex; }
    public List<ItemStack> getPhase(int tab) { return phases[tab]; }
    public int getGlobalMin(int tab) { return tabGlobalMin[tab]; }
    public int getGlobalMax(int tab) { return tabGlobalMax[tab]; }
    public boolean isHideTabs()   { return hideTabs; }

    /** Call before the inventory is opened to hide the phase tab row. */
    public void setHideTabs(boolean hide) {
        this.hideTabs = hide;
        buildTabRow();
    }

    public void setActiveTab(int tab) {
        if (hideTabs) return;
        if (tab < 0 || tab > 3 || tab == activeTab) return;
        activeTab     = tab;
        selectedIndex = -1;
        rebuildAll();
    }

    public void prevPage() {
        if (tabPage[activeTab] > 0) {
            tabPage[activeTab]--;
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

    /** Select the injection at the given grid inventory slot (18-50). */
    public void selectItem(int gridSlot) {
        int pageIdx = gridSlot - GRID_START;
        int absIdx  = tabPage[activeTab] * PAGE_SIZE + pageIdx;
        if (absIdx < 0 || absIdx >= phases[activeTab].size()) return;
        selectedIndex = absIdx;
        rebuildControlRow();
        rebuildGrid();
    }

    /**
     * Adjust a per-injection or per-phase property.
     *
     * @param propSlot One of SLOT_WEIGHT, SLOT_MAX_MAP, SLOT_GLOB_MIN, SLOT_GLOB_MAX
     * @param delta    +1 or -1 (or ±10 for shift-click)
     */
    public void adjustProperty(int propSlot, int delta) {
        // Global min/max are per-phase, not per-injection — no selection needed.
        if (propSlot == SLOT_GLOB_MIN) {
            tabGlobalMin[activeTab] = clamp(tabGlobalMin[activeTab] + delta, 0, 999);
            // Keep min <= max when non-zero
            if (tabGlobalMax[activeTab] > 0 && tabGlobalMin[activeTab] > tabGlobalMax[activeTab]) {
                tabGlobalMax[activeTab] = tabGlobalMin[activeTab];
            }
            rebuildControlRow();
            return;
        }
        if (propSlot == SLOT_GLOB_MAX) {
            tabGlobalMax[activeTab] = clamp(tabGlobalMax[activeTab] + delta, 0, 999);
            if (tabGlobalMax[activeTab] > 0 && tabGlobalMin[activeTab] > tabGlobalMax[activeTab]) {
                tabGlobalMin[activeTab] = tabGlobalMax[activeTab];
            }
            rebuildControlRow();
            return;
        }

        // Per-injection properties require a selection.
        if (selectedIndex < 0 || selectedIndex >= phases[activeTab].size()) return;
        ItemStack item = phases[activeTab].get(selectedIndex);
        if (item.isEmpty()) return;

        int weight   = LucraftInjectionEntry.getWeight(item);
        int maxOnMap = LucraftInjectionEntry.getMaxOnMap(item);

        switch (propSlot) {
            case SLOT_WEIGHT:  weight   = clamp(weight   + delta, 1, 1000); break;
            case SLOT_MAX_MAP: maxOnMap = clamp(maxOnMap + delta, 0,  999); break;
            default: return;
        }

        LucraftInjectionEntry.embed(item, weight, maxOnMap);
        LucraftInjectionEntry.refreshLore(item);
        rebuildControlRow();
        rebuildGrid();
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
            super.setInventorySlotContents(i, tab);
        }
        ItemStack filler = makeFiller();
        for (int i = 4; i <= 8; i++) super.setInventorySlotContents(i, filler.copy());
    }

    private void rebuildControlRow() {
        int gMin = tabGlobalMin[activeTab];
        int gMax = tabGlobalMax[activeTab];

        if (selectedIndex >= 0 && selectedIndex < phases[activeTab].size()) {
            ItemStack live = phases[activeTab].get(selectedIndex);
            ItemStack sel  = live.copy();
            // Refresh lore on the display copy
            LucraftInjectionEntry.refreshLore(sel);
            super.setInventorySlotContents(SLOT_SELECTED, sel);

            super.setInventorySlotContents(SLOT_WEIGHT, makeButton(
                    TextFormatting.YELLOW + "Pickup Weight",
                    LucraftInjectionEntry.getWeight(live),
                    "Spawn chance relative to other injections.",
                    "Range: 1-1000   Default: 10"));
            super.setInventorySlotContents(SLOT_MAX_MAP, makeButton(
                    TextFormatting.AQUA + "Max on Map",
                    LucraftInjectionEntry.getMaxOnMap(live),
                    "Max simultaneous copies of this injection on the map.",
                    "0 = unlimited.   Range: 0-999"));
        } else {
            ItemStack placeholder = new ItemStack(Blocks.BARRIER);
            placeholder.setStackDisplayName(TextFormatting.RED + "No injection selected");
            addLore(placeholder, TextFormatting.GRAY + "Click an injection in the list below.");
            super.setInventorySlotContents(SLOT_SELECTED, placeholder);
            super.setInventorySlotContents(SLOT_WEIGHT,  makeFiller());
            super.setInventorySlotContents(SLOT_MAX_MAP, makeFiller());
        }

        // Global min/max are always shown regardless of selection.
        super.setInventorySlotContents(SLOT_GLOB_MIN, makeButton(
                TextFormatting.GREEN + "Min Injections",
                gMin,
                "Minimum total injection entities on the map for this phase.",
                "0 = use AllPhases fallback.   Range: 0-999"));
        super.setInventorySlotContents(SLOT_GLOB_MAX, makeButton(
                TextFormatting.RED + "Max Injections",
                gMax,
                "Maximum total injection entities on the map for this phase.",
                "0 = use AllPhases fallback.   Range: 0-999"));

        ItemStack filler = makeFiller();
        for (int s = 14; s <= 17; s++) super.setInventorySlotContents(s, filler.copy());
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
                    copy.setStackDisplayName(TextFormatting.GOLD + "► "
                            + TextFormatting.LIGHT_PURPLE + "Injection: "
                            + LucraftInjectionEntry.getDisplayName(copy));
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
                TextFormatting.GRAY + "Injections " + (total == 0 ? 0 : offset + 1)
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
        ItemStack btn = new ItemStack(Blocks.STAINED_GLASS_PANE, Math.max(1, Math.min(64, value == 0 ? 1 : value)), meta);
        btn.setStackDisplayName(label + TextFormatting.WHITE + ": " + value);
        String[] fullLore = new String[loreLines.length + 2];
        System.arraycopy(loreLines, 0, fullLore, 0, loreLines.length);
        fullLore[loreLines.length]     = TextFormatting.DARK_GRAY + "Left-click: +1   Right-click: -1";
        fullLore[loreLines.length + 1] = TextFormatting.DARK_GRAY + "Shift+click: ×10";
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

    @Override
    public Container createContainer(InventoryPlayer playerInv, EntityPlayer player) {
        return new LucraftInjectionPropertiesContainer(playerInv, this);
    }

    @Override public String getGuiID() { return "minecraft:container"; }
    @Override public ITextComponent getDisplayName() { return new TextComponentString(getName()); }
}
