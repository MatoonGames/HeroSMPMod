package com.matoon.herosmp.client.gui.editor;

import com.matoon.herosmp.client.gui.bedrock.BedrockScreen;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI.Style;
import net.minecraft.item.ItemStack;

import java.util.Collections;
import java.util.List;

/**
 * Blocks players may break during a match (matched by block type; data values are ignored). Capped
 * at 54 entries, the same as the in-world configure chest.
 */
final class BreakableTab extends EditorTab {

    private static final int MAX = 54;
    private static final int PANEL_W = 176;

    private int selected = -1;
    private final BedrockScreen.SmoothScroll scroll = new BedrockScreen.SmoothScroll();
    private float contentH, viewH;

    BreakableTab(GuiMapEditor ed) {
        super(ed);
    }

    @Override
    void draw(int x, int y, int w, int h) {
        final List<ItemStack> blocks = session.config.getBreakableBlocks();
        if (selected >= blocks.size()) selected = -1;
        int pw = Math.min(PANEL_W, w / 2);
        int gw = w - pw - 10;

        ed.font().drawString("Breakable blocks  ·  " + blocks.size() + " / " + MAX, x, y, BedrockUI.TEXT_DIM, false);
        int gy = y + 14, gh = h - 14;
        viewH = gh;
        scroll.clamp(contentH - viewH);
        contentH = itemGrid(ed, blocks, x, gy, gw, gh, scroll.value(), selected,
                i -> selected = selected == i ? -1 : i,
                () -> ed.openOverlay(new ItemPicker(ed, ItemPicker.Filter.BLOCKS, "Add a breakable block", this::add)),
                blocks.size() < MAX,
                (st, i, sx, sy, hov) -> Collections.singletonList(ItemPicker.displayName(st)));

        int px = x + w - pw;
        panel(px, y, pw, h);
        int iy = y + 8, ix = px + 8, iw = pw - 16;
        iy = heading(ed, ix, iy, iw, "Breakable Blocks");
        iy = note(ed, ix, iy, iw, "Players can break these blocks during a match. They can always break "
                + "blocks they placed themselves.", BedrockUI.TEXT_MUTED) + 10;
        if (selected >= 0) {
            ItemStack st = blocks.get(selected);
            BedrockUI.slot(ix, iy, 34, true, false);
            BedrockUI.item(st, ix + 1, iy + 1, 2f);
            ed.font().drawString(BedrockUI.trim(ed.font(), ItemPicker.displayName(st), iw - 40), ix + 40, iy + 13, BedrockUI.TEXT, true);
            iy += 42;
            ed.button(ix, iy, iw, 22, "Remove", Style.DANGER, true, this::removeSelected);
        } else {
            ed.button(ix, iy, iw, 22, "Add Block", Style.PRIMARY, blocks.size() < MAX,
                    () -> ed.openOverlay(new ItemPicker(ed, ItemPicker.Filter.BLOCKS, "Add a breakable block", this::add)));
        }
    }

    private void add(ItemStack st) {
        List<ItemStack> blocks = session.config.getBreakableBlocks();
        if (blocks.size() >= MAX) {
            ed.toast("The list is full (" + MAX + ")");
            return;
        }
        for (ItemStack existing : blocks) {
            if (existing.getItem() == st.getItem()) {
                ed.toast("Already in the list");
                return;
            }
        }
        ItemStack e = st.copy();
        e.setCount(1);
        blocks.add(e);
        session.config.setBreakableBlocks(blocks);
        session.markDirty();
    }

    private void removeSelected() {
        List<ItemStack> blocks = session.config.getBreakableBlocks();
        if (selected < 0 || selected >= blocks.size()) return;
        blocks.remove(selected);
        session.config.setBreakableBlocks(blocks);
        session.markDirty();
        selected = -1;
    }

    @Override
    void onScroll(int direction) {
        scroll.add(-direction * (SLOT + SLOT_GAP) * 2);
    }

    @Override
    void onKey(char c, int key) {
        if (key == org.lwjgl.input.Keyboard.KEY_DELETE) removeSelected();
    }
}
