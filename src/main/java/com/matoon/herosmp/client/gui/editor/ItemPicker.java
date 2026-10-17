package com.matoon.herosmp.client.gui.editor;

import com.matoon.herosmp.client.gui.bedrock.BedrockScreen;
import com.matoon.herosmp.client.gui.bedrock.BedrockTextField;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI.Style;
import com.matoon.herosmp.integration.LucraftInjectionEntry;
import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Item browser shown over the editors, organised like the creative inventory: a sidebar of
 * categories (Recent, All, every creative tab including modded ones, and Other for items that
 * belong to no tab), a search box that looks through everything ({@code @modid} narrows to one
 * mod), and a roomy grid with an info strip naming the item under the pointer. Superpowers are
 * listed by name and grouped by the mod that adds them, since every syringe looks the same.
 */
final class ItemPicker implements BedrockScreen.Overlay {

    enum Filter { ALL, BLOCKS, INJECTIONS }

    /** One sidebar entry. */
    private static final class Category {
        final String name;
        final ItemStack icon;
        final List<ItemStack> items;

        Category(String name, ItemStack icon, List<ItemStack> items) {
            this.name = name;
            this.icon = icon;
            this.items = items;
        }
    }

    private static final Map<Filter, List<Category>> CATEGORIES = new HashMap<Filter, List<Category>>();
    private static List<ItemStack> allItems, injections;
    /** Most recently picked, newest first (kept for the session). */
    private static final LinkedList<ItemStack> RECENT = new LinkedList<ItemStack>();
    private static final int RECENT_MAX = 27;

    private static final int CELL = 24, CELL_GAP = 3, SIDEBAR_W = 132, ROW_H = 20, INFO_H = 22;

    private final BedrockScreen screen;
    private final Filter filter;
    private final String title;
    private final Consumer<ItemStack> onPick;
    /** Stays open after a pick (adding several to a list) instead of closing. */
    private final boolean multi;
    private final BedrockTextField search;
    private final BedrockScreen.SmoothScroll scroll = new BedrockScreen.SmoothScroll();
    private final BedrockScreen.SmoothScroll sideScroll = new BedrockScreen.SmoothScroll();

    private final List<Category> categories;
    private int category;
    private String query = "";
    private List<ItemStack> shown = new ArrayList<ItemStack>();
    private float contentH, viewH, sideContentH, sideViewH;
    private int sideX, sideY, sideW, sideH;
    private ItemStack infoStack = ItemStack.EMPTY;

    /** Picker that stays open so several items can be added in a row. */
    ItemPicker(BedrockScreen screen, Filter filter, String title, Consumer<ItemStack> onPick) {
        this(screen, filter, title, onPick, true);
    }

    ItemPicker(BedrockScreen screen, Filter filter, String title, Consumer<ItemStack> onPick, boolean multi) {
        this.screen = screen;
        this.filter = filter;
        this.title = title;
        this.onPick = onPick;
        this.multi = multi;
        this.search = BedrockTextField.text(screen.font(), 40, s -> { query = s.trim(); refresh(); });
        this.categories = withRecent(categoriesFor(filter));
        // Open on "All" (just after Recent, when there is one).
        this.category = categories.size() > 1 && "Recent".equals(categories.get(0).name) ? 1 : 0;
        refresh();
        screen.focus(search);
    }

    /** Picker that picks one item and closes. */
    static ItemPicker single(BedrockScreen screen, Filter filter, String title, Consumer<ItemStack> onPick) {
        return new ItemPicker(screen, filter, title, onPick, false);
    }

    private List<Category> withRecent(List<Category> base) {
        List<ItemStack> recent = new ArrayList<ItemStack>();
        for (ItemStack st : RECENT) if (accepts(st)) recent.add(st);
        if (recent.isEmpty()) return base;
        List<Category> out = new ArrayList<Category>();
        out.add(new Category("Recent", new ItemStack(Items.CLOCK), recent));
        out.addAll(base);
        return out;
    }

    private boolean accepts(ItemStack st) {
        switch (filter) {
            case BLOCKS: return st.getItem() instanceof ItemBlock;
            case INJECTIONS: return LucraftInjectionEntry.isValidInjection(st);
            default: return true;
        }
    }

    private void refresh() {
        List<ItemStack> out = new ArrayList<ItemStack>();
        if (query.isEmpty()) {
            if (!categories.isEmpty()) out.addAll(categories.get(category).items);
        } else {
            String q = query.toLowerCase(Locale.ROOT);
            String mod = null;
            if (q.startsWith("@")) {
                int sp = q.indexOf(' ');
                mod = sp < 0 ? q.substring(1) : q.substring(1, sp);
                q = sp < 0 ? "" : q.substring(sp + 1).trim();
            }
            for (ItemStack st : sourceFor(filter)) {
                if (mod != null && !modId(st).startsWith(mod)) continue;
                if (!q.isEmpty() && !searchText(st).contains(q)) continue;
                out.add(st);
            }
        }
        shown = out;
        scroll.set(0);
    }

    @Override
    public void onScroll(int direction) {
        int mx = screen.mouseX(), my = screen.mouseY();
        if (mx >= sideX && mx < sideX + sideW && my >= sideY && my < sideY + sideH) {
            sideScroll.add(-direction * ROW_H * 2);
        } else {
            scroll.add(-direction * (filter == Filter.INJECTIONS ? ROW_H + 2 : CELL + CELL_GAP) * 2);
        }
    }

    // ---------------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------------

    @Override
    public void draw(final BedrockScreen s, float partialTicks) {
        BedrockUI.rect(0, 0, s.width, s.height, 0xB0000000);
        int w = Math.min(600, s.width - 24);
        int h = Math.min(360, s.height - 24);
        int x = (s.width - w) / 2;
        int y = (s.height - h) / 2;
        s.block(0, 0, s.width, s.height);

        BedrockUI.shadow(x, y, w, h);
        BedrockUI.rect(x, y, w, h, BedrockUI.OUTLINE);
        BedrockUI.rect(x + 1, y + 1, w - 2, h - 2, BedrockUI.PANEL);

        // Header: title, search, close.
        BedrockUI.rect(x + 1, y + 1, w - 2, 30, BedrockUI.PANEL_LIGHT);
        BedrockUI.rect(x + 1, y + 31, w - 2, 1, BedrockUI.OUTLINE);
        int titleW = Math.min(s.font().getStringWidth(title), w / 3);
        s.font().drawString(BedrockUI.trim(s.font(), title, w / 3), x + 10, y + 12, BedrockUI.TEXT, true);
        int sx = x + 20 + titleW, sw = x + w - 36 - sx;
        search.draw(s, sx, y + 6, sw, 20, filter == Filter.INJECTIONS ? "Search powers..." : "Search all items  (@mod to filter by mod)");
        s.button(x + w - 30, y + 6, 22, 20, "x", Style.DARK, true, s::closeOverlay);

        int bodyY = y + 32, bodyH = h - 33;
        boolean sidebar = categories.size() > 1 || filter != Filter.INJECTIONS;
        int gridX = x + 1;
        if (sidebar) {
            drawSidebar(s, x + 1, bodyY, SIDEBAR_W, bodyH);
            gridX = x + 1 + SIDEBAR_W + 1;
            BedrockUI.rect(gridX - 1, bodyY, 1, bodyH, BedrockUI.OUTLINE);
        }
        int gridW = x + w - 1 - gridX;

        // Section heading + count.
        String heading = !query.isEmpty() ? "Search results" : categories.isEmpty() ? "" : categories.get(category).name;
        s.font().drawString(heading, gridX + 10, bodyY + 8, BedrockUI.TEXT, true);
        String count = shown.size() + (shown.size() == 1 ? " item" : " items");
        s.font().drawString(count, gridX + gridW - 10 - s.font().getStringWidth(count), bodyY + 8, BedrockUI.TEXT_MUTED, false);

        int gy = bodyY + 22, gh = bodyH - 22 - INFO_H;
        infoStack = ItemStack.EMPTY;
        if (shown.isEmpty()) {
            String msg = filter == Filter.INJECTIONS && sourceFor(filter).isEmpty()
                    ? "No powers found. Is LucraftCore installed?" : "Nothing matches \"" + query + "\".";
            s.font().drawString(msg, gridX + (gridW - s.font().getStringWidth(msg)) / 2, gy + 40, BedrockUI.TEXT_DIM, false);
        } else if (filter == Filter.INJECTIONS) {
            drawPowerList(s, gridX + 8, gy, gridW - 16, gh);
        } else {
            drawGrid(s, gridX + 8, gy, gridW - 16, gh);
        }
        drawInfo(s, gridX, y + h - 1 - INFO_H, gridW, INFO_H);
    }

    private void drawSidebar(final BedrockScreen s, int x, int y, int w, int h) {
        sideX = x; sideY = y; sideW = w; sideH = h;
        BedrockUI.rect(x, y, w, h, BedrockUI.SURFACE);
        sideContentH = categories.size() * ROW_H + 8;
        sideViewH = h;
        sideScroll.clamp(sideContentH - sideViewH);
        float sc = sideScroll.value();
        BedrockUI.pushClip(x, y, w, h);
        for (int i = 0; i < categories.size(); i++) {
            final int idx = i;
            Category c = categories.get(i);
            int ry = y + 4 + i * ROW_H - (int) sc;
            if (ry + ROW_H < y || ry > y + h) continue;
            boolean active = query.isEmpty() && i == category;
            boolean hov = s.hover(x, ry, w, ROW_H);
            float a = s.anim("cat:" + i, active || hov, 10f);
            if (active) BedrockUI.rect(x, ry, w, ROW_H, 0xFF3A4A36);
            else if (a > 0.01f) BedrockUI.rect(x, ry, w, ROW_H, BedrockUI.fade(0xFF3B3C3E, a));
            if (active) BedrockUI.rect(x, ry + 2, 3, ROW_H - 4, BedrockUI.GREEN_LIGHT);
            BedrockUI.item(c.icon, x + 6 + Math.round(a * 2), ry + 2, 1f);
            String n = BedrockUI.trim(s.font(), c.name, w - 54);
            s.font().drawString(n, x + 27 + Math.round(a * 2), ry + 6, active ? 0xFFFFFFFF : BedrockUI.TEXT_DIM, active);
            String cnt = String.valueOf(c.items.size());
            s.font().drawString(cnt, x + w - 6 - s.font().getStringWidth(cnt), ry + 6, BedrockUI.TEXT_MUTED, false);
            s.cue(com.matoon.herosmp.client.gui.bedrock.UiSounds.Cue.TAB);
            s.hit(x, ry, w, ROW_H, () -> {
                category = idx;
                if (!query.isEmpty()) { search.setText(""); query = ""; }
                refresh();
            });
        }
        BedrockUI.popClip();
        scrollbar(x + w - 3, y, h, sc, sideContentH, sideViewH);
    }

    private void drawGrid(final BedrockScreen s, int gx, int gy, int gw, int gh) {
        int step = CELL + CELL_GAP;
        int cols = Math.max(1, (gw + CELL_GAP) / step);
        int left = gx + (gw - (cols * step - CELL_GAP)) / 2;
        int rows = (shown.size() + cols - 1) / cols;
        contentH = rows * step;
        viewH = gh;
        scroll.clamp(contentH - viewH);
        float sc = scroll.value();

        BedrockUI.pushClip(gx - 4, gy, gw + 8, gh);
        int first = Math.max(0, (int) (sc / step) * cols);
        for (int i = first; i < shown.size(); i++) {
            int cx = left + (i % cols) * step;
            int cy = gy + (i / cols) * step - (int) sc;
            if (cy > gy + gh) break;
            final ItemStack st = shown.get(i);
            boolean hov = s.hover(cx, cy, CELL, CELL);
            float lift = s.anim("cell:" + i + ":" + st.getItem().hashCode(), hov, 14f);
            int ly = cy - Math.round(lift * 2);
            if (lift > 0.02f) BedrockUI.glow(cx, ly, CELL, CELL, 0xFFE27A, lift);
            BedrockUI.rect(cx, ly, CELL, CELL, hov ? BedrockUI.GOLD : BedrockUI.OUTLINE);
            BedrockUI.rect(cx + 1, ly + 1, CELL - 2, CELL - 2, hov ? 0xFF45464A : 0xFF1F2023);
            BedrockUI.rect(cx + 1, ly + 1, CELL - 2, 1, hov ? 0xFF5A5B5F : 0xFF141415);
            BedrockUI.item(st, cx + (CELL - 16) / 2f, ly + (CELL - 16) / 2f, 1f);
            s.cue(com.matoon.herosmp.client.gui.bedrock.UiSounds.Cue.NONE);
            s.hit(cx, cy, CELL, CELL, () -> pick(s, st));
            if (hov) {
                infoStack = st;
                s.setTooltip(displayName(st), "§7" + modName(st), multi ? "§8Click to add" : "§8Click to choose");
            }
        }
        BedrockUI.popClip();
        scrollbar(gx + gw + 2, gy, gh, sc, contentH, viewH);
    }

    /** Powers: two columns of named rows (every injection uses the same syringe icon). */
    private void drawPowerList(final BedrockScreen s, int gx, int gy, int gw, int gh) {
        int cols = gw >= 300 ? 2 : 1;
        int colW = (gw - (cols - 1) * 6) / cols;
        int step = ROW_H + 4;
        int rows = (shown.size() + cols - 1) / cols;
        contentH = rows * step;
        viewH = gh;
        scroll.clamp(contentH - viewH);
        float sc = scroll.value();
        BedrockUI.pushClip(gx - 2, gy, gw + 4, gh);
        for (int i = 0; i < shown.size(); i++) {
            int rx = gx + (i % cols) * (colW + 6);
            int ry = gy + (i / cols) * step - (int) sc;
            if (ry + step < gy) continue;
            if (ry > gy + gh) break;
            final ItemStack st = shown.get(i);
            boolean hov = s.hover(rx, ry, colW, ROW_H);
            BedrockUI.rect(rx, ry, colW, ROW_H, hov ? 0xFFB57EDC : BedrockUI.OUTLINE);
            BedrockUI.rect(rx + 1, ry + 1, colW - 2, ROW_H - 2, hov ? 0xFF4A3A60 : 0xFF26272A);
            BedrockUI.item(st, rx + 3, ry + 2, 1f);
            String n = BedrockUI.trim(s.font(), displayName(st), colW - 30);
            s.font().drawString(n, rx + 23, ry + 6, hov ? 0xFFFFFFFF : BedrockUI.TEXT, false);
            s.cue(com.matoon.herosmp.client.gui.bedrock.UiSounds.Cue.NONE);
            s.hit(rx, ry, colW, ROW_H, () -> pick(s, st));
            if (hov) infoStack = st;
        }
        BedrockUI.popClip();
        scrollbar(gx + gw + 2, gy, gh, sc, contentH, viewH);
    }

    /** Bottom strip: what's under the pointer. */
    private void drawInfo(BedrockScreen s, int x, int y, int w, int h) {
        BedrockUI.rect(x, y, w, 1, BedrockUI.OUTLINE);
        BedrockUI.rect(x, y + 1, w, h - 1, BedrockUI.SURFACE);
        if (infoStack.isEmpty()) {
            String hint = multi ? "Click items to add them. The picker stays open." : "Click an item to choose it.";
            s.font().drawString(hint, x + 10, y + 8, BedrockUI.TEXT_MUTED, false);
            return;
        }
        BedrockUI.item(infoStack, x + 6, y + 3, 1f);
        String name = displayName(infoStack);
        s.font().drawString(BedrockUI.trim(s.font(), name, w / 2), x + 27, y + 8, BedrockUI.TEXT, true);
        String id = modName(infoStack) + "  ·  " + idOf(infoStack);
        int nameW = Math.min(s.font().getStringWidth(name), w / 2);
        s.font().drawString(BedrockUI.trim(s.font(), id, w - 40 - nameW), x + 35 + nameW, y + 8, BedrockUI.TEXT_MUTED, false);
    }

    private static void scrollbar(int x, int y, int h, float sc, float content, float view) {
        if (content <= view) return;
        BedrockUI.rect(x, y, 3, h, 0x26FFFFFF);
        int th = Math.max(16, (int) (h * (view / content)));
        int ty = y + (int) ((h - th) * Math.min(1f, sc / (content - view)));
        BedrockUI.rect(x, ty, 3, th, 0xA0FFFFFF);
    }

    private void pick(BedrockScreen s, ItemStack st) {
        remember(st);
        onPick.accept(st.copy());
        if (multi) s.toast("Added " + displayName(st));
    }

    private static void remember(ItemStack st) {
        for (java.util.Iterator<ItemStack> it = RECENT.iterator(); it.hasNext(); ) {
            if (ItemStack.areItemStacksEqual(it.next(), st)) it.remove();
        }
        ItemStack one = st.copy();
        one.setCount(1);
        RECENT.addFirst(one);
        while (RECENT.size() > RECENT_MAX) RECENT.removeLast();
    }

    // ---------------------------------------------------------------------
    // Sources
    // ---------------------------------------------------------------------

    private static List<ItemStack> sourceFor(Filter f) {
        if (f == Filter.INJECTIONS) return injectionStacks();
        List<ItemStack> all = allStacks();
        if (f == Filter.ALL) return all;
        List<ItemStack> blocks = new ArrayList<ItemStack>();
        for (ItemStack st : all) if (st.getItem() instanceof ItemBlock) blocks.add(st);
        return blocks;
    }

    /** "All", one entry per creative tab, then "Other" for items no tab lists. Cached per filter. */
    private static List<Category> categoriesFor(Filter f) {
        List<Category> cached = CATEGORIES.get(f);
        if (cached != null) return cached;
        List<Category> out = new ArrayList<Category>();
        if (f == Filter.INJECTIONS) {
            // Group powers by the mod that adds them.
            Map<String, List<ItemStack>> byMod = new LinkedHashMap<String, List<ItemStack>>();
            for (ItemStack st : injectionStacks()) {
                String id = LucraftInjectionEntry.getLucraftId(st);
                String mod = id.contains(":") ? id.substring(0, id.indexOf(':')) : "minecraft";
                List<ItemStack> l = byMod.get(mod);
                if (l == null) byMod.put(mod, l = new ArrayList<ItemStack>());
                l.add(st);
            }
            out.add(new Category("All powers", injectionStacks().isEmpty() ? new ItemStack(Items.GLASS_BOTTLE)
                    : injectionStacks().get(0), injectionStacks()));
            if (byMod.size() > 1) {
                for (Map.Entry<String, List<ItemStack>> e : byMod.entrySet()) {
                    out.add(new Category(modDisplayName(e.getKey()), e.getValue().get(0), e.getValue()));
                }
            }
        } else {
            List<ItemStack> all = sourceFor(f);
            out.add(new Category(f == Filter.BLOCKS ? "All blocks" : "All items", new ItemStack(Items.COMPASS), all));
            Set<Item> listed = new HashSet<Item>();
            for (CreativeTabs tab : CreativeTabs.CREATIVE_TAB_ARRAY) {
                if (tab == null || tab == CreativeTabs.SEARCH || tab == CreativeTabs.INVENTORY || tab == CreativeTabs.HOTBAR) continue;
                NonNullList<ItemStack> items = NonNullList.create();
                try {
                    tab.displayAllRelevantItems(items);
                } catch (Exception ignored) {
                    // A modded tab that can't list outside a world: skip it.
                    continue;
                }
                List<ItemStack> keep = new ArrayList<ItemStack>();
                for (ItemStack st : items) {
                    if (st.isEmpty()) continue;
                    listed.add(st.getItem());
                    if (f != Filter.BLOCKS || st.getItem() instanceof ItemBlock) keep.add(st);
                }
                if (keep.isEmpty()) continue;
                ItemStack icon;
                try {
                    icon = tab.getIcon();
                } catch (Exception e) {
                    icon = keep.get(0);
                }
                out.add(new Category(I18n.format(tab.getTranslationKey()), icon.isEmpty() ? keep.get(0) : icon, keep));
            }
            List<ItemStack> other = new ArrayList<ItemStack>();
            for (ItemStack st : all) if (!listed.contains(st.getItem())) other.add(st);
            if (!other.isEmpty()) out.add(new Category("Other", new ItemStack(Items.NAME_TAG), other));
        }
        CATEGORIES.put(f, out);
        return out;
    }

    private static List<ItemStack> allStacks() {
        if (allItems != null) return allItems;
        List<ItemStack> out = new ArrayList<ItemStack>();
        for (Item item : ForgeRegistries.ITEMS) {
            if (item == null || item == Items.AIR) continue;
            NonNullList<ItemStack> subs = NonNullList.create();
            try {
                item.getSubItems(CreativeTabs.SEARCH, subs);
            } catch (Exception ignored) {
                // Some modded items throw outside a world; fall back to the plain item.
            }
            if (subs.isEmpty()) subs.add(new ItemStack(item));
            for (ItemStack st : subs) if (!st.isEmpty()) out.add(st);
        }
        allItems = out;
        return out;
    }

    static List<ItemStack> injectionStacks() {
        if (injections != null) return injections;
        List<ItemStack> out = new ArrayList<ItemStack>();
        Item inj = ForgeRegistries.ITEMS.getValue(new ResourceLocation("lucraftcore", "injection"));
        if (inj != null) {
            NonNullList<ItemStack> subs = NonNullList.create();
            try {
                inj.getSubItems(CreativeTabs.SEARCH, subs);
            } catch (Exception ignored) {
                // Leave the list empty.
            }
            for (ItemStack st : subs) if (LucraftInjectionEntry.isValidInjection(st)) out.add(st);
            out.sort((a, b) -> displayName(a).compareToIgnoreCase(displayName(b)));
        }
        injections = out;
        return out;
    }

    private static String searchText(ItemStack st) {
        return (displayName(st) + " " + idOf(st)).toLowerCase(Locale.ROOT);
    }

    private static String idOf(ItemStack st) {
        if (LucraftInjectionEntry.hasInjectionTag(st)) return LucraftInjectionEntry.getLucraftId(st);
        ResourceLocation rl = st.getItem().getRegistryName();
        return rl == null ? "?" : rl.toString() + (st.getHasSubtypes() && st.getMetadata() != 0 ? "/" + st.getMetadata() : "");
    }

    private static String modId(ItemStack st) {
        String id = idOf(st);
        return id.contains(":") ? id.substring(0, id.indexOf(':')).toLowerCase(Locale.ROOT) : "minecraft";
    }

    private static String modName(ItemStack st) {
        return modDisplayName(modId(st));
    }

    private static String modDisplayName(String modId) {
        if ("minecraft".equals(modId)) return "Minecraft";
        ModContainer mc = Loader.instance().getIndexedModList().get(modId);
        return mc != null ? mc.getName() : modId;
    }

    static String displayName(ItemStack st) {
        if (LucraftInjectionEntry.hasInjectionTag(st)) return LucraftInjectionEntry.getDisplayName(st);
        try {
            return st.getDisplayName();
        } catch (Exception e) {
            return String.valueOf(st.getItem().getRegistryName());
        }
    }
}
