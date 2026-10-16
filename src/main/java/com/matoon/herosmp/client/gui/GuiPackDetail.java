package com.matoon.herosmp.client.gui;

import com.matoon.herosmp.client.gui.bedrock.BedrockScreen;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI.Style;
import com.matoon.herosmp.client.store.InstalledPacks;
import com.matoon.herosmp.client.store.PackMeta;
import com.matoon.herosmp.client.store.StorePack;

import java.util.List;

/**
 * Product page for one pack, laid out like a Bedrock Marketplace listing: large artwork and a
 * details table on the left; title, status, actions, and description on the right (stacked on
 * narrow screens).
 */
public class GuiPackDetail extends BedrockScreen {

    private final GuiPackStore store;
    private final StorePack pack;

    private boolean installed;
    private long size;
    private String localDescription = "";

    private final SmoothScroll scroll = new SmoothScroll();
    private float contentH;

    public GuiPackDetail(GuiPackStore store, StorePack pack) {
        super(store, pack.displayName);
        this.store = store;
        this.pack = pack;
    }

    GuiPackStore store() {
        return store;
    }

    @Override
    public void initGui() {
        super.initGui();
        refreshState();
    }

    private void refreshState() {
        installed = pack.type == StorePack.PackType.MAP && InstalledPacks.isMapInstalled(pack.displayName);
        size = installed ? InstalledPacks.sizeOf(pack.displayName) : 0;
        localDescription = installed ? PackMeta.localDescription(pack.displayName) : "";
    }

    private boolean hasRemote() {
        return pack.driveId != null && !pack.driveId.isEmpty();
    }

    /** Null while the remote description is still loading. */
    private String description() {
        if (!localDescription.isEmpty()) return localDescription;
        if (hasRemote() && pack.isFolder) {
            String d = PackMeta.remoteDescription(pack.driveId);
            if (d == null) return null;
            if (!d.isEmpty()) return d;
        }
        return pack.type == StorePack.PackType.MAP
                ? "A Hunger Games arena for Hero Games matches. Once installed it joins the map rotation "
                  + "automatically, and you can open it in configure mode to adjust spawns, loot, the border "
                  + "and power injections."
                : "A PVP kit pack. Pick a world when installing and its kits are added to that world's kit "
                  + "selection.";
    }

    @Override
    protected void onScroll(int direction) {
        scroll.add(-direction * 36);
    }

    // ---------------------------------------------------------------------

    @Override
    protected void drawBody(float partialTicks) {
        int cw = Math.min(width - 28, 600);
        int left = (width - cw) / 2;
        int top = HEADER_H + 14;
        float sc = scroll.value();
        int y = top - (int) sc;

        boolean wide = cw >= 380;
        int mediaW = wide ? (int) (cw * 0.56f) : cw;
        int mediaH = mediaW * 9 / 16;

        // Artwork.
        BedrockUI.shadow(left - 1, y - 1, mediaW + 2, mediaH + 2);
        BedrockUI.rect(left - 1, y - 1, mediaW + 2, mediaH + 2, BedrockUI.OUTLINE);
        PackArt.draw(pack, installed, left, y, mediaW, mediaH);

        // Details table.
        int tableY = y + mediaH + 12;
        int tableH = drawTable(left, tableY, mediaW);
        int leftBottom = tableY + tableH;

        // Right column.
        int rx = wide ? left + mediaW + 16 : left;
        int rw = wide ? cw - mediaW - 16 : cw;
        int ry = wide ? y : leftBottom + 14;
        int rightBottom = drawInfoColumn(rx, ry, rw);

        contentH = Math.max(leftBottom, rightBottom) + 12 + sc - top;
        scroll.clamp(contentH - (height - top));
    }

    private int drawTable(int x, int y, int w) {
        String[][] rowsData = {
                {"Type", pack.typeLabel()},
                {"Status", installed ? "Installed" : "Not installed"},
                {"Size", installed ? InstalledPacks.formatBytes(size) : "-"},
                {"Installs to", pack.type == StorePack.PackType.MAP ? "herosmp_hg_maps/" + pack.displayName : "A world you choose"},
                {"Source", "Beyonder Store"},
        };
        int rowH = 15;
        int h = rowsData.length * rowH + 2;
        BedrockUI.rect(x, y, w, h, BedrockUI.OUTLINE);
        for (int i = 0; i < rowsData.length; i++) {
            int ry = y + 1 + i * rowH;
            BedrockUI.rect(x + 1, ry, w - 2, rowH, i % 2 == 0 ? BedrockUI.PANEL : BedrockUI.SURFACE);
            fontRenderer.drawString(rowsData[i][0], x + 7, ry + 4, BedrockUI.TEXT_MUTED, false);
            int valueColor = i == 1 && installed ? BedrockUI.GREEN_LIGHT : BedrockUI.TEXT;
            String v = BedrockUI.trim(fontRenderer, rowsData[i][1], w - 90);
            fontRenderer.drawString(v, x + w - 7 - fontRenderer.getStringWidth(v), ry + 4, valueColor, false);
        }
        return h;
    }

    private int drawInfoColumn(int x, int y, int w) {
        boolean map = pack.type == StorePack.PackType.MAP;
        BedrockUI.chip(fontRenderer, x, y, map ? "HUNGER GAMES MAP" : "PVP KIT",
                map ? BedrockUI.GREEN : BedrockUI.BLUE, BedrockUI.TEXT);
        y += 18;

        List<String> titleLines = fontRenderer.listFormattedStringToWidth(pack.displayName, Math.max(20, w / 2));
        for (int i = 0; i < Math.min(2, titleLines.size()); i++) {
            BedrockUI.text(fontRenderer, titleLines.get(i), x, y, BedrockUI.TEXT, 2f, true);
            y += 19;
        }
        fontRenderer.drawString("Beyonder", x, y + 1, BedrockUI.TEXT_MUTED, false);
        y += 16;

        if (installed) {
            BedrockUI.check(x, y + 1, BedrockUI.GREEN_LIGHT);
            fontRenderer.drawString("Installed", x + 13, y + 1, BedrockUI.GREEN_LIGHT, false);
        } else {
            fontRenderer.drawString("Free", x, y + 1, BedrockUI.TEXT, false);
        }
        y += 16;

        // Actions.
        int bh = 24, gap = 6;
        if (!map) {
            button(x, y, w, bh, "Install to a World", Style.PRIMARY, true,
                    () -> mc.displayGuiScreen(new GuiWorldSelect(this, pack)));
            y += bh + gap;
        } else if (!installed) {
            button(x, y, w, bh, "Download", Style.PRIMARY, hasRemote(),
                    () -> mc.displayGuiScreen(new GuiDownloadProgress(this, pack, null)));
            y += bh + gap;
        } else {
            splitButton(x, y, w, bh, "Edit Map", Style.PRIMARY, () -> EditMenu.editInWorld(this, pack.displayName),
                    (ar, ab) -> EditMenu.open(this, pack.displayName, ar, ab));
            y += bh + 3;
            String note = "Use the dots for the Visual Editor";
            fontRenderer.drawString(BedrockUI.trim(fontRenderer, note, w), x, y, BedrockUI.TEXT_MUTED, false);
            y += 13;
            int half = (w - gap) / 2;
            button(x, y, half, bh, "Reinstall", Style.DARK, hasRemote(),
                    () -> mc.displayGuiScreen(new GuiDownloadProgress(this, pack, null)));
            button(x + half + gap, y, w - half - gap, bh, "Delete", Style.DANGER, true,
                    () -> confirm("Delete " + pack.displayName + "?",
                            "This permanently removes the map's files from herosmp_hg_maps. You can download it again from the store.",
                            "Delete", Style.DANGER, () -> {
                                InstalledPacks.deleteMap(pack.displayName);
                                refreshState();
                                toast("Deleted " + pack.displayName);
                            }));
            y += bh + gap;
        }

        // About.
        y += 8;
        BedrockUI.rect(x, y, w, 1, BedrockUI.PANEL_LIGHT);
        y += 9;
        fontRenderer.drawString("About", x, y, BedrockUI.TEXT, true);
        y += 14;
        String desc = description();
        if (desc == null) {
            BedrockUI.loadingDots(x + 16, y + 4);
            y += 14;
        } else {
            for (String line : fontRenderer.listFormattedStringToWidth(desc, w)) {
                fontRenderer.drawString(line, x, y, BedrockUI.TEXT_DIM, false);
                y += 10;
            }
        }
        return y;
    }
}
