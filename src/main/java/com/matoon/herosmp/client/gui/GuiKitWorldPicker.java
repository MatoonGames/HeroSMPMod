package com.matoon.herosmp.client.gui;

import com.matoon.herosmp.client.gui.bedrock.BedrockScreen;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI.Style;
import com.matoon.herosmp.client.store.InstalledKits;
import com.matoon.herosmp.client.store.PackThumbnails;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Chooses which worlds a library kit is available in: "All worlds", or any number of specific
 * singleplayer worlds. Reports the choice as a list of world folders (empty = all worlds).
 */
public class GuiKitWorldPicker extends BedrockScreen {

    private static final int FOOTER_H = 38;
    private static final ItemStack WORLD_ICON = new ItemStack(Blocks.GRASS);

    private final String heading, subtitle, confirmLabel;
    private final Consumer<List<String>> onConfirm;
    private List<InstalledKits.WorldInfo> worlds = Collections.emptyList();
    private boolean scanned;
    private boolean allWorlds;
    private final Set<String> selected = new LinkedHashSet<String>();

    private final SmoothScroll scroll = new SmoothScroll();
    private float contentH;

    /**
     * @param initial current world folders (empty = all worlds)
     * @param onConfirm receives the chosen folders (empty = all worlds); it decides which screen to show next
     */
    public GuiKitWorldPicker(GuiScreen parent, String heading, String subtitle, List<String> initial,
                             String confirmLabel, Consumer<List<String>> onConfirm) {
        super(parent, "Choose Worlds");
        this.heading = heading;
        this.subtitle = subtitle;
        this.confirmLabel = confirmLabel;
        this.onConfirm = onConfirm;
        this.allWorlds = initial == null || initial.isEmpty();
        if (initial != null) selected.addAll(initial);
    }

    @Override
    public void initGui() {
        super.initGui();
        if (!scanned) {
            scanned = true;
            worlds = InstalledKits.worlds();
        }
    }

    @Override
    protected void onScroll(int direction) {
        scroll.add(-direction * 36);
    }

    private boolean isSelected(InstalledKits.WorldInfo w) {
        for (String s : selected) if (s.equalsIgnoreCase(w.folder())) return true;
        return false;
    }

    private void toggleWorld(InstalledKits.WorldInfo w) {
        if (allWorlds) {
            // Picking a world while "All worlds" is on narrows the kit down to that world.
            allWorlds = false;
            selected.clear();
        }
        if (isSelected(w)) selected.removeIf(s -> s.equalsIgnoreCase(w.folder()));
        else selected.add(w.folder());
    }

    private boolean canConfirm() {
        return allWorlds || !selected.isEmpty();
    }

    private void confirm() {
        if (!canConfirm()) return;
        onConfirm.accept(allWorlds ? Collections.<String>emptyList() : new ArrayList<String>(selected));
    }

    // ---------------------------------------------------------------------

    @Override
    protected void drawBody(float partialTicks) {
        int cw = Math.min(width - 28, 520);
        int left = (width - cw) / 2;
        int top = HEADER_H + 12;
        float sc = scroll.value();
        int y = top - (int) sc;

        fontRenderer.drawString(BedrockUI.trim(fontRenderer, heading, cw), left, y, BedrockUI.TEXT, true);
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, subtitle, cw), left, y + 12, BedrockUI.TEXT_MUTED, false);
        y += 30;

        // "All worlds" row.
        int h = 40;
        boolean hov = hover(left, y, cw, h);
        BedrockUI.rect(left, y, cw, h, allWorlds ? BedrockUI.GREEN_LIGHT : BedrockUI.OUTLINE);
        BedrockUI.rect(left + 1, y + 1, cw - 2, h - 2, allWorlds ? 0xFF2C3B27 : (hov ? BedrockUI.PANEL_HOVER : BedrockUI.PANEL));
        BedrockUI.item(new ItemStack(Blocks.GRASS), left + 10, y + 12, 1f);
        BedrockUI.item(new ItemStack(Blocks.SAND), left + 16, y + 16, 1f);
        fontRenderer.drawString("All worlds", left + 42, y + 9, BedrockUI.TEXT, true);
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, "Every world, including ones you create later", cw - 100),
                left + 42, y + 22, BedrockUI.TEXT_DIM, false);
        toggle(left + cw - 10 - TOGGLE_W, y + (h - TOGGLE_H) / 2, allWorlds, true, () -> allWorlds = !allWorlds);
        y += h + 12;

        fontRenderer.drawString(allWorlds ? "Or pick specific worlds:" : "Available in these worlds:", left, y, BedrockUI.TEXT_DIM, false);
        y += 14;

        if (worlds.isEmpty()) {
            fontRenderer.drawString("No singleplayer worlds yet.", left, y + 4, BedrockUI.TEXT_MUTED, false);
            y += 20;
        }
        int rowH = 40;
        SimpleDateFormat fmt = new SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH);
        for (InstalledKits.WorldInfo w : worlds) {
            drawRow(w, left, y, cw, rowH, fmt);
            y += rowH + 4;
        }

        contentH = y + sc - top + FOOTER_H;
        scroll.clamp(contentH - (height - top));
    }

    private void drawRow(final InstalledKits.WorldInfo world, int x, int y, int w, int h, SimpleDateFormat fmt) {
        boolean sel = !allWorlds && isSelected(world);
        boolean hov = hover(x, y, w, h);
        hit(x, y, w, h, () -> toggleWorld(world));

        BedrockUI.rect(x, y, w, h, sel ? BedrockUI.GREEN_LIGHT : BedrockUI.OUTLINE);
        BedrockUI.rect(x + 1, y + 1, w - 2, h - 2, sel ? 0xFF2C3B27 : (hov ? BedrockUI.PANEL_HOVER : BedrockUI.PANEL));

        int s = h - 10;
        BedrockUI.rect(x + 5, y + 5, s, s, BedrockUI.OUTLINE);
        PackThumbnails.Thumb icon = PackThumbnails.world(world.dir);
        float alpha = allWorlds ? 0.5f : 1f;
        if (icon != null) {
            BedrockUI.imageCover(icon, x + 6, y + 6, s - 2, s - 2, alpha, false);
        } else {
            BedrockUI.vGradient(x + 6, y + 6, s - 2, s - 2, 0xFF3E7A32, 0xFF16301A);
            BedrockUI.item(WORLD_ICON, x + 6 + (s - 18) / 2f, y + 6 + (s - 18) / 2f, 1f);
        }

        int tx = x + 5 + s + 8;
        int avail = x + w - 34 - tx;
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, world.name, avail), tx, y + 8,
                allWorlds ? BedrockUI.TEXT_DIM : BedrockUI.TEXT, !allWorlds);
        String sub = world.folder() + "  ·  " + (world.open ? "Open right now" : "Last played " + fmt.format(new Date(world.lastPlayed)));
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, sub, avail), tx, y + 21, BedrockUI.TEXT_MUTED, false);

        // Checkbox.
        int cx = x + w - 24, cy = y + (h - 14) / 2;
        BedrockUI.rect(cx, cy, 14, 14, BedrockUI.OUTLINE);
        BedrockUI.rect(cx + 1, cy + 1, 12, 12, sel ? BedrockUI.GREEN : 0xFF161718);
        if (sel) BedrockUI.check(cx + 3, cy + 3, 0xFFFFFFFF);
        else if (allWorlds) BedrockUI.check(cx + 3, cy + 3, 0xFF5A5B5D);
    }

    @Override
    protected void drawFooter() {
        int y = height - FOOTER_H;
        BedrockUI.vGradient(0, y - 6, width, 6, 0x00000000, 0x66000000);
        BedrockUI.rect(0, y, width, FOOTER_H, BedrockUI.PANEL);
        BedrockUI.rect(0, y, width, 1, BedrockUI.OUTLINE);
        block(0, y, width, FOOTER_H);

        int bw = Math.max(110, fontRenderer.getStringWidth(confirmLabel) + 24);
        int bx = width - 10 - bw;
        button(bx, y + 7, bw, 24, confirmLabel, Style.PRIMARY, canConfirm(), this::confirm);
        button(bx - 8 - 90, y + 7, 90, 24, "Cancel", Style.DARK, true, this::goBack);

        String hint = allWorlds ? "Available in all worlds"
                : selected.isEmpty() ? "Select at least one world"
                : "Available in " + selected.size() + (selected.size() == 1 ? " world" : " worlds");
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, hint, bx - 8 - 90 - 20), 10, y + 15,
                canConfirm() ? BedrockUI.TEXT_DIM : 0xFFE0A040, false);
    }
}
