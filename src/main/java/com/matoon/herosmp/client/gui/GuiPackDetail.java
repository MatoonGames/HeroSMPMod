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
        installed = pack.type == StorePack.PackType.MAP ? InstalledPacks.isMapInstalled(pack.displayName)
                : pack.type == StorePack.PackType.ADDON && com.matoon.herosmp.client.store.HeroAddons.isInstalled(pack);
        size = installed && pack.type == StorePack.PackType.MAP ? InstalledPacks.sizeOf(pack.displayName) : 0;
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
        if (pack.type == StorePack.PackType.ADDON) {
            com.matoon.herosmp.client.store.AddonMeta.Info info = com.matoon.herosmp.client.store.AddonMeta.remote(pack);
            if (info == null && com.matoon.herosmp.client.store.AddonMeta.loading(pack)) return null; // still reading it
            String own = info != null && !info.description.isEmpty() ? info.description + "\n\n" : "";
            return own + "A LucraftCore addon pack: new heroes, superpowers and suits. Install it, restart Minecraft, "
                    + "and its powers show up in the game, in kit superpowers, and in Hunger Games power pools. "
                    + "Kits made with it need it too.";
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
        title = PackArt.title(pack); // a Hero Addon's own name once its info has loaded
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
        PackArt.draw(pack, installed, left, y, mediaW, mediaH, hover(left, y, mediaW, mediaH));

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
                {"Size", installed && size > 0 ? InstalledPacks.formatBytes(size) : "-"},
                {"Installs to", pack.type == StorePack.PackType.MAP ? "herosmp_hg_maps/" + pack.displayName
                        : pack.type == StorePack.PackType.ADDON ? com.matoon.herosmp.client.store.HeroAddons.dir().getName() + "/"
                        + com.matoon.herosmp.client.store.HeroAddons.targetFor(pack).getName() : "Your Kit Library"},
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
        boolean addon = pack.type == StorePack.PackType.ADDON;
        BedrockUI.chip(fontRenderer, x, y, map ? "HUNGER GAMES MAP" : addon ? "HERO ADDON" : "PVP KIT",
                map ? BedrockUI.GREEN : addon ? 0xFF7A3FC0 : BedrockUI.BLUE, BedrockUI.TEXT);
        y += 18;

        List<String> titleLines = fontRenderer.listFormattedStringToWidth(PackArt.title(pack), Math.max(20, w / 2));
        for (int i = 0; i < Math.min(2, titleLines.size()); i++) {
            BedrockUI.text(fontRenderer, titleLines.get(i), x, y, BedrockUI.TEXT, 2f, true);
            y += 19;
        }
        com.matoon.herosmp.client.store.AddonMeta.Info addonInfo = addon ? com.matoon.herosmp.client.store.AddonMeta.remote(pack) : null;
        String byline = addonInfo != null && !addonInfo.author.isEmpty() ? "by " + addonInfo.author : "Beyonder";
        fontRenderer.drawString(byline, x, y + 1, BedrockUI.TEXT_MUTED, false);
        y += 16;

        if (installed && addon) {
            final java.io.File file = com.matoon.herosmp.client.store.HeroAddons.targetFor(pack);
            boolean pending = com.matoon.herosmp.client.store.HeroAddons.needsRestart(file);
            BedrockUI.check(x, y + 1, BedrockUI.GREEN_LIGHT);
            fontRenderer.drawString(pending ? "Installed: restart to load" : "Installed", x + 13, y + 1,
                    pending ? 0xFFE0A040 : BedrockUI.GREEN_LIGHT, false);
            y += 16;
        } else if (installed) {
            BedrockUI.check(x, y + 1, BedrockUI.GREEN_LIGHT);
            fontRenderer.drawString("Installed", x + 13, y + 1, BedrockUI.GREEN_LIGHT, false);
            y += 16;
            // Match-rotation switch.
            final boolean enabled = InstalledPacks.isMapEnabled(pack.displayName);
            toggle(x, y, enabled, true, () -> {
                boolean now = InstalledPacks.setMapEnabled(pack.displayName, !enabled);
                toast(pack.displayName + (now ? " is back in the match rotation" : " won't be picked for matches"));
            });
            fontRenderer.drawString(enabled ? "In match rotation" : "Disabled: not picked for matches",
                    x + TOGGLE_W + 8, y + 5, enabled ? BedrockUI.TEXT_DIM : 0xFFE0A040, false);
            y += TOGGLE_H + 4;
        } else {
            fontRenderer.drawString("Free", x, y + 1, BedrockUI.TEXT, false);
        }
        y += 16;

        // Actions.
        int bh = 24, gap = 6;
        if (addon) {
            final java.io.File file = com.matoon.herosmp.client.store.HeroAddons.targetFor(pack);
            if (!installed) {
                button(x, y, w, bh, "Install", Style.PRIMARY, hasRemote(),
                        () -> mc.displayGuiScreen(new GuiDownloadProgress(this, pack, null)));
                y += bh + 3;
                fontRenderer.drawString(BedrockUI.trim(fontRenderer, "Loads after you restart Minecraft", w), x, y, BedrockUI.TEXT_MUTED, false);
                y += 13;
            } else {
                int half = (w - gap) / 2;
                button(x, y, half, bh, "Open Folder", Style.DARK, true, () -> SaveFileDialog.reveal(file.exists() ? file
                        : com.matoon.herosmp.client.store.HeroAddons.dir()));
                button(x + half + gap, y, w - half - gap, bh, "Remove", Style.DANGER, true,
                        () -> confirm("Remove " + pack.displayName + "?",
                                "Deletes the addon pack. Its heroes and powers disappear after you restart Minecraft, "
                                        + "and kits that use them won't load.", "Remove", Style.DANGER, () -> {
                                    boolean now = com.matoon.herosmp.client.store.HeroAddons.remove(file);
                                    refreshState();
                                    toast(now ? "Removed " + pack.displayName + " (restart to finish)"
                                            : "Removed " + pack.displayName + " (deleted when the game restarts)");
                                }));
                y += bh + gap;
            }
        } else if (!map) {
            // Mods / Hero Addons the kit needs that this game is missing.
            final net.minecraft.nbt.NBTTagCompound reqTag = hasRemote()
                    ? com.matoon.herosmp.client.store.PackMeta.remoteKitTag(pack.driveId) : null;
            if (reqTag != null) {
                final java.util.List<com.matoon.herosmp.npc.kit.KitRequirements.Requirement> missing =
                        com.matoon.herosmp.npc.kit.KitRequirements.missing(reqTag);
                if (!missing.isEmpty()) {
                    int boxH = 30;
                    BedrockUI.rect(x, y, w, boxH, 0xFFE0A040);
                    BedrockUI.rect(x + 1, y + 1, w - 2, boxH - 2, 0xFF3A2A12);
                    fontRenderer.drawString("Needs mods you don't have", x + 7, y + 5, 0xFFFFD27A, true);
                    fontRenderer.drawString(BedrockUI.trim(fontRenderer, com.matoon.herosmp.npc.kit.KitRequirements.names(missing), w - 14),
                            x + 7, y + 17, BedrockUI.TEXT_DIM, false);
                    hit(x, y, w, boxH, () -> MissingModsOverlay.show(this, pack.displayName, missing));
                    if (hover(x, y, w, boxH)) setTooltip("See what to install");
                    y += boxH + gap;
                }
            }
            button(x, y, w, bh, "Install to Kit Library", Style.PRIMARY, hasRemote(),
                    () -> GuiPackStore.installKitPack(this, pack));
            y += bh + gap;
            // Save the raw kit file (e.g. to put on a dedicated server or re-upload elsewhere).
            button(x, y, w, bh, "Download File", Style.DARK, hasRemote(),
                    () -> GuiKitDownload.fromStore(this, pack));
            y += bh + gap;
            // Try before installing: uses the pack's kit once it has been fetched for the preview.
            final net.minecraft.nbt.NBTTagCompound kitTag = hasRemote()
                    ? com.matoon.herosmp.client.store.PackMeta.remoteKitTag(pack.driveId) : null;
            button(x, y, w, bh, "Playtest", Style.DARK, kitTag != null, () -> MissingModsOverlay.guard(this, pack.displayName,
                    kitTag, "Playtest anyway", () -> Playtest.kit(this, kitTag, pack.displayName)));
            if (kitTag == null && hover(x, y, w, bh)) setTooltip("Loading the kit...");
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
            button(x, y, w, bh, "Playtest", Style.DARK, true, () -> Playtest.map(this, pack.displayName));
            y += bh + gap;
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
