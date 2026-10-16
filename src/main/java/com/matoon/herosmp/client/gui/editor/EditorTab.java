package com.matoon.herosmp.client.gui.editor;

import com.matoon.herosmp.client.gui.bedrock.BedrockScreen;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI.Style;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.function.IntConsumer;

/** One page of the map editor, plus small widgets shared between pages. */
abstract class EditorTab {

    static final String[] PHASES = {"Phase 1", "Phase 2", "Phase 3", "All Phases"};
    static final int SLOT = 22, SLOT_GAP = 2;

    final GuiMapEditor ed;
    final MapEditSession session;

    EditorTab(GuiMapEditor ed) {
        this.ed = ed;
        this.session = ed.session;
    }

    /** Draws the page into the content box. */
    abstract void draw(int x, int y, int w, int h);

    void onMouseDown(int x, int y, int button) {}
    void onDrag(int x, int y, int button) {}
    void onRelease(int x, int y, int button) {}
    void onScroll(int direction) {}
    void onKey(char c, int key) {}
    void dispose() {}

    // ---------------------------------------------------------------------
    // Shared widgets
    // ---------------------------------------------------------------------

    /** Row of phase buttons with item counts. Returns its height. */
    static int phaseTabs(BedrockScreen s, int x, int y, int w, int selected, int[] counts, IntConsumer onSelect) {
        int gap = 4;
        int bw = (w - gap * 3) / 4;
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            String label = PHASES[i] + (counts[i] > 0 ? " (" + counts[i] + ")" : "");
            s.button(x + i * (bw + gap), y, bw, 20, label, i == selected ? Style.PRIMARY : Style.DARK, true,
                    () -> onSelect.accept(idx));
        }
        return 20;
    }

    /** Section heading with a thin rule underneath. Returns the y below it. */
    static int heading(BedrockScreen s, int x, int y, int w, String text) {
        s.font().drawString(text, x, y, BedrockUI.TEXT, true);
        BedrockUI.rect(x, y + 11, w, 1, BedrockUI.PANEL_LIGHT);
        return y + 16;
    }

    /** Wrapped dim text. Returns the y below it. */
    static int note(BedrockScreen s, int x, int y, int w, String text, int color) {
        for (String line : s.font().listFormattedStringToWidth(text, w)) {
            s.font().drawString(line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    /** Panel background used for the right-hand inspector. */
    static void panel(int x, int y, int w, int h) {
        BedrockUI.rect(x, y, w, h, BedrockUI.OUTLINE);
        BedrockUI.rect(x + 1, y + 1, w - 2, h - 2, 0xEE262728);
    }

    interface SlotDecorator {
        /** Extra drawing on top of a slot (e.g. a badge); may return tooltip lines. */
        List<String> decorate(ItemStack stack, int index, int x, int y, boolean hovered);
    }

    /**
     * Scrollable grid of item slots with a trailing "+" slot. Returns the total content height so
     * the caller can clamp scrolling.
     */
    static int itemGrid(BedrockScreen s, List<ItemStack> items, int x, int y, int w, int h, float scroll,
                        int selected, IntConsumer onSelect, Runnable onAdd, boolean canAdd, SlotDecorator deco) {
        int cols = Math.max(1, (w + SLOT_GAP) / (SLOT + SLOT_GAP));
        int count = items.size() + (onAdd != null ? 1 : 0);
        int rows = (count + cols - 1) / cols;
        int contentH = rows * (SLOT + SLOT_GAP);

        BedrockUI.pushClip(x, y, w, h);
        for (int i = 0; i < count; i++) {
            int sx = x + (i % cols) * (SLOT + SLOT_GAP);
            int sy = y + (i / cols) * (SLOT + SLOT_GAP) - (int) scroll;
            if (sy + SLOT < y || sy > y + h) continue;
            final int idx = i;
            boolean hov = s.hover(sx, sy, SLOT, SLOT);
            if (i == items.size()) {
                // "+" slot.
                BedrockUI.slot(sx, sy, SLOT, false, hov && canAdd);
                int c = canAdd ? (hov ? 0xFFFFFFFF : BedrockUI.GREEN_LIGHT) : 0xFF55565A;
                BedrockUI.rect(sx + SLOT / 2 - 1, sy + 6, 2, SLOT - 12, c);
                BedrockUI.rect(sx + 6, sy + SLOT / 2 - 1, SLOT - 12, 2, c);
                if (canAdd) s.hit(sx, sy, SLOT, SLOT, onAdd);
                if (hov) s.setTooltip(canAdd ? "Add..." : "This list is full");
                continue;
            }
            ItemStack st = items.get(i);
            BedrockUI.slot(sx, sy, SLOT, i == selected, hov);
            BedrockUI.item(st, sx + 3, sy + 3, 1f);
            List<String> tip = deco != null ? deco.decorate(st, i, sx, sy, hov) : null;
            s.hit(sx, sy, SLOT, SLOT, () -> onSelect.accept(idx));
            if (hov && tip != null) s.setTooltip(tip);
        }
        BedrockUI.popClip();
        return contentH;
    }
}
