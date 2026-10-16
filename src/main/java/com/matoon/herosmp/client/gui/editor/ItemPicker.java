package com.matoon.herosmp.client.gui.editor;

import com.matoon.herosmp.client.gui.bedrock.BedrockScreen;
import com.matoon.herosmp.client.gui.bedrock.BedrockTextField;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI.Style;
import com.matoon.herosmp.integration.LucraftInjectionEntry;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Searchable item browser shown over the editor. Lists every item variant the creative search tab
 * would (so potions, dyes, enchanted books etc. are included), or only blocks / only LucraftCore
 * powers. Clicking adds the item; the picker stays open so several can be added in a row.
 */
final class ItemPicker implements BedrockScreen.Overlay {

    enum Filter { ALL, BLOCKS, INJECTIONS }

    private static List<ItemStack> allItems, injections;

    private final Filter filter;
    private final String title;
    private final Consumer<ItemStack> onPick;
    private final BedrockTextField search;
    private final BedrockScreen.SmoothScroll scroll = new BedrockScreen.SmoothScroll();

    private List<ItemStack> source;
    private List<String> names;
    private List<ItemStack> shown = new ArrayList<ItemStack>();
    private float contentH, viewH;

    ItemPicker(BedrockScreen screen, Filter filter, String title, Consumer<ItemStack> onPick) {
        this.filter = filter;
        this.title = title;
        this.onPick = onPick;
        this.search = BedrockTextField.text(screen.font(), 40, s -> applySearch(s));
        this.source = sourceFor(filter);
        this.names = new ArrayList<String>(source.size());
        for (ItemStack st : source) names.add(searchText(st));
        applySearch("");
        screen.focus(search);
    }

    private void applySearch(String q) {
        String needle = q.trim().toLowerCase(Locale.ROOT);
        List<ItemStack> out = new ArrayList<ItemStack>();
        for (int i = 0; i < source.size(); i++) {
            if (needle.isEmpty() || names.get(i).contains(needle)) out.add(source.get(i));
        }
        shown = out;
        scroll.set(0);
    }

    @Override
    public void onScroll(int direction) {
        scroll.add(-direction * (EditorTab.SLOT + EditorTab.SLOT_GAP) * 2);
    }

    @Override
    public void draw(BedrockScreen s, float partialTicks) {
        BedrockUI.rect(0, 0, s.width, s.height, 0xA0000000);
        int w = Math.min(380, s.width - 30);
        int h = Math.min(280, s.height - 40);
        int x = (s.width - w) / 2;
        int y = (s.height - h) / 2;
        s.block(0, 0, s.width, s.height);

        BedrockUI.shadow(x, y, w, h);
        BedrockUI.rect(x, y, w, h, BedrockUI.OUTLINE);
        BedrockUI.rect(x + 1, y + 1, w - 2, h - 2, BedrockUI.PANEL);
        BedrockUI.rect(x + 1, y + 1, w - 2, 26, BedrockUI.PANEL_LIGHT);
        BedrockUI.rect(x + 1, y + 27, w - 2, 1, BedrockUI.OUTLINE);
        s.font().drawString(title, x + 10, y + 10, BedrockUI.TEXT, true);
        String count = shown.size() + " results";
        s.font().drawString(count, x + w - 40 - s.font().getStringWidth(count), y + 10, BedrockUI.TEXT_DIM, false);
        s.button(x + w - 28, y + 4, 22, 20, "x", Style.DARK, true, s::closeOverlay);

        search.draw(s, x + 10, y + 34, w - 20, 20, "Search items...");

        int gx = x + 10, gy = y + 60, gw = w - 20, gh = h - 70;
        int cols = Math.max(1, (gw + EditorTab.SLOT_GAP) / (EditorTab.SLOT + EditorTab.SLOT_GAP));
        int rows = (shown.size() + cols - 1) / cols;
        contentH = rows * (EditorTab.SLOT + EditorTab.SLOT_GAP);
        viewH = gh;
        scroll.clamp(contentH - viewH);
        float sc = scroll.value();

        if (shown.isEmpty()) {
            String msg = filter == Filter.INJECTIONS ? "No powers found. Is LucraftCore installed?" : "Nothing matches your search.";
            s.font().drawString(msg, x + (w - s.font().getStringWidth(msg)) / 2, gy + 30, BedrockUI.TEXT_DIM, false);
            return;
        }

        BedrockUI.pushClip(gx, gy, gw, gh);
        int first = Math.max(0, (int) (sc / (EditorTab.SLOT + EditorTab.SLOT_GAP)) * cols);
        for (int i = first; i < shown.size(); i++) {
            int sx = gx + (i % cols) * (EditorTab.SLOT + EditorTab.SLOT_GAP);
            int sy = gy + (i / cols) * (EditorTab.SLOT + EditorTab.SLOT_GAP) - (int) sc;
            if (sy > gy + gh) break;
            final ItemStack st = shown.get(i);
            boolean hov = s.hover(sx, sy, EditorTab.SLOT, EditorTab.SLOT);
            BedrockUI.slot(sx, sy, EditorTab.SLOT, false, hov);
            BedrockUI.item(st, sx + 3, sy + 3, 1f);
            s.hit(sx, sy, EditorTab.SLOT, EditorTab.SLOT, () -> {
                onPick.accept(st.copy());
                s.toast("Added " + displayName(st));
            });
            if (hov) s.setTooltip(displayName(st), "§7Click to add");
        }
        BedrockUI.popClip();

        if (contentH > viewH) {
            int tx = x + w - 6;
            BedrockUI.rect(tx, gy, 3, gh, 0x26FFFFFF);
            int th = Math.max(16, (int) (gh * (viewH / contentH)));
            int ty = gy + (int) ((gh - th) * Math.min(1f, sc / (contentH - viewH)));
            BedrockUI.rect(tx, ty, 3, th, 0xA0FFFFFF);
        }
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
        }
        injections = out;
        return out;
    }

    private static String searchText(ItemStack st) {
        String reg = st.getItem().getRegistryName() != null ? st.getItem().getRegistryName().toString() : "";
        return (displayName(st) + " " + reg).toLowerCase(Locale.ROOT);
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
