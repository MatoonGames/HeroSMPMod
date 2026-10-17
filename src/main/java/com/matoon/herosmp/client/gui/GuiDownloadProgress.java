package com.matoon.herosmp.client.gui;

import com.matoon.herosmp.client.gui.bedrock.BedrockScreen;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI;
import com.matoon.herosmp.client.gui.bedrock.UiSounds;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI.Style;
import com.matoon.herosmp.client.store.InstalledKits;
import com.matoon.herosmp.client.store.InstalledPacks;
import com.matoon.herosmp.client.store.PackInstaller;
import com.matoon.herosmp.client.store.StorePack;
import net.minecraft.client.gui.GuiScreen;

import java.util.Collections;
import java.util.List;

/**
 * Install screen: runs a {@link PackInstaller} and shows a Bedrock progress card with the pack
 * art, a striped progress bar, file / size / speed stats, and the result.
 */
public class GuiDownloadProgress extends BedrockScreen {

    /** When the finished state was first shown (0 = not yet): fanfare once, then a fading flash. */
    private long celebratedAt;
    private boolean failSounded;

    private final StorePack pack;
    /** KIT packs: worlds the kits are available in (empty = all). */
    private final List<String> kitWorlds;
    private String worldsLabel;

    private PackInstaller.Progress progress;

    // Smoothed download speed.
    private long lastBytes;
    private long lastSample;
    private double bytesPerSec;

    /** Mods / Hero Addons the installed kits need but this game lacks (null until checked). */
    private List<com.matoon.herosmp.npc.kit.KitRequirements.Requirement> missing;

    public GuiDownloadProgress(GuiScreen parent, StorePack pack, List<String> kitWorlds) {
        super(parent, "Installing");
        this.pack = pack;
        this.kitWorlds = kitWorlds;
    }

    @Override
    public void initGui() {
        super.initGui();
        if (progress == null) start();
    }

    /** "all worlds" / a world's name / "N worlds", resolved once (it reads level.dat files). */
    private String worldsLabel() {
        if (worldsLabel == null) {
            List<String> w = kitWorlds != null ? kitWorlds : Collections.<String>emptyList();
            worldsLabel = w.isEmpty() ? "all worlds" : InstalledKits.describeWorlds(w, InstalledKits.worlds());
        }
        return worldsLabel;
    }

    private void start() {
        progress = PackInstaller.start(pack, kitWorlds);
        lastBytes = 0;
        lastSample = System.currentTimeMillis();
        bytesPerSec = 0;
    }

    @Override
    public void updateScreen() {
        long now = System.currentTimeMillis();
        if (progress != null && now - lastSample >= 500) {
            long b = progress.bytesDone();
            double inst = (b - lastBytes) * 1000.0 / (now - lastSample);
            bytesPerSec = bytesPerSec == 0 ? inst : bytesPerSec * 0.6 + inst * 0.4;
            lastBytes = b;
            lastSample = now;
        }
    }

    @Override
    protected void goBack() {
        if (progress != null && progress.state() == PackInstaller.State.RUNNING) progress.requestCancel();
        super.goBack();
    }

    @Override
    protected void drawBody(float partialTicks) {
        int w = Math.min(330, width - 30);
        int h = 176;
        int x = (width - w) / 2;
        int y = HEADER_H + Math.max(10, (height - HEADER_H - h) / 2);

        BedrockUI.shadow(x, y, w, h);
        BedrockUI.rect(x, y, w, h, BedrockUI.OUTLINE);
        BedrockUI.rect(x + 1, y + 1, w - 2, h - 2, BedrockUI.PANEL);

        // Pack summary.
        int aw = 96, ah = 54;
        BedrockUI.rect(x + 11, y + 11, aw + 2, ah + 2, BedrockUI.OUTLINE);
        PackArt.draw(pack, false, x + 12, y + 12, aw, ah);
        int tx = x + 12 + aw + 12;
        int tw = x + w - 12 - tx;
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, pack.displayName, tw), tx, y + 15, BedrockUI.TEXT, true);
        fontRenderer.drawString(pack.typeLabel(), tx, y + 28, BedrockUI.TEXT_MUTED, false);
        String dest = pack.type == StorePack.PackType.KIT ? "Into: Kit Library  ·  " + worldsLabel()
                : pack.type == StorePack.PackType.ADDON ? "Into: LucraftCore addon packs" : "Into: Hunger Games maps";
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, dest, tw), tx, y + 41, BedrockUI.TEXT_DIM, false);

        BedrockUI.rect(x + 12, y + 76, w - 24, 1, BedrockUI.PANEL_LIGHT);

        PackInstaller.State state = progress.state();
        int cy = y + 86;
        switch (state) {
            case RUNNING:  drawRunning(x, cy, w, y + h); break;
            case SUCCESS:  drawSuccess(x, cy, w, y + h); break;
            case ERROR:    drawFailure(x, cy, w, y + h, "Install failed", progress.error()); break;
            default:       drawFailure(x, cy, w, y + h, "Install cancelled", "Nothing was changed."); break;
        }
    }

    private void drawRunning(int x, int y, int w, int bottom) {
        boolean scanning = progress.totalFiles() <= 0;
        String status = scanning ? "Preparing download..." : progress.currentFile();
        String pct = scanning ? "" : Math.round(progress.fraction() * 100) + "%";
        int pctW = fontRenderer.getStringWidth(pct);
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, status, w - 32 - pctW), x + 12, y, BedrockUI.TEXT_DIM, false);
        fontRenderer.drawString(pct, x + w - 12 - pctW, y, BedrockUI.TEXT, true);

        BedrockUI.progressBar(x + 12, y + 12, w - 24, 14, scanning ? -1f : progress.fraction());

        if (!scanning) {
            String stats = progress.filesDone() + " of " + progress.totalFiles() + " files  ·  "
                    + InstalledPacks.formatBytes(progress.bytesDone());
            if (bytesPerSec > 1) stats += "  ·  " + InstalledPacks.formatBytes((long) bytesPerSec) + "/s";
            fontRenderer.drawString(BedrockUI.trim(fontRenderer, stats, w - 24), x + 12, y + 32, BedrockUI.TEXT_MUTED, false);
        }

        button(x + (w - 100) / 2, bottom - 34, 100, 24, "Cancel", Style.DARK, true, () -> progress.requestCancel());
    }

    private void drawSuccess(int x, int y, int w, int bottom) {
        if (celebratedAt == 0) {
            celebratedAt = System.currentTimeMillis();
            UiSounds.celebrate();
            confetti(80);
            sparkle(x + 27, y + 13, 16, BedrockUI.GREEN_LIGHT, BedrockUI.GOLD, 0xFFFFFFFF);
        }
        float flash = 1f - Math.min(1f, (System.currentTimeMillis() - celebratedAt) / 600f);
        BedrockUI.rect(x + 12, y, w - 24, 26, BedrockUI.OUTLINE);
        BedrockUI.rect(x + 13, y + 1, w - 26, 24, 0xFF234A1A);
        BedrockUI.rect(x + 13, y + 1, w - 26, 24, BedrockUI.fade(0xFF8CE070, flash * 0.6f));
        BedrockUI.rect(x + 13, y + 1, 3, 24, BedrockUI.GREEN_LIGHT);
        BedrockUI.check(x + 24, y + 9, BedrockUI.GREEN_LIGHT);
        fontRenderer.drawString("Installed successfully", x + 40, y + 9, BedrockUI.TEXT, true);

        String sub = pack.type == StorePack.PackType.MAP
                ? "It's now part of your Hunger Games map rotation."
                : pack.type == StorePack.PackType.ADDON
                ? "Restart Minecraft to load its heroes and powers."
                : "Added to your Kit Library, available in " + worldsLabel() + ".";
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, sub, w - 24), x + 12, y + 34, BedrockUI.TEXT_DIM, false);

        // Kits that need mods / Hero Addons this game doesn't have.
        if (pack.type == StorePack.PackType.KIT && missing == null) {
            missing = new java.util.ArrayList<com.matoon.herosmp.npc.kit.KitRequirements.Requirement>();
            for (net.minecraft.nbt.NBTTagCompound k : progress.kitTags()) {
                for (com.matoon.herosmp.npc.kit.KitRequirements.Requirement r : com.matoon.herosmp.npc.kit.KitRequirements.missing(k)) {
                    boolean dup = false;
                    for (com.matoon.herosmp.npc.kit.KitRequirements.Requirement o : missing) dup |= o.id.equals(r.id);
                    if (!dup) missing.add(r);
                }
            }
            if (!missing.isEmpty()) MissingModsOverlay.show(this, pack.displayName, missing);
        }
        if (missing != null && !missing.isEmpty()) {
            String warn = "Needs: " + com.matoon.herosmp.npc.kit.KitRequirements.names(missing);
            fontRenderer.drawString(BedrockUI.trim(fontRenderer, warn, w - 24), x + 12, y + 46, 0xFFE0A040, false);
        }

        int by = bottom - 34;
        if (pack.type == StorePack.PackType.ADDON) {
            int bw = (w - 24 - 8) / 2;
            button(x + 12, by, bw, 24, "Later", Style.DARK, true, () -> mc.displayGuiScreen(parent));
            button(x + 12 + bw + 8, by, bw, 24, "Quit Game", Style.PRIMARY, true, () -> confirm("Quit Minecraft?",
                    "Start it again from your launcher to load the new Hero Addon.", "Quit", Style.PRIMARY, mc::shutdown));
            if (hover(x + 12 + bw + 8, by, bw, 24)) setTooltip("Close Minecraft", "§7Addon packs load when the game starts");
        } else if (pack.type == StorePack.PackType.KIT && missing != null && !missing.isEmpty()) {
            int bw = (w - 24 - 8) / 2;
            button(x + 12, by, bw, 24, "Done", Style.DARK, true, () -> mc.displayGuiScreen(parent));
            button(x + 12 + bw + 8, by, bw, 24, "Missing Mods", Style.GOLD, true,
                    () -> MissingModsOverlay.show(this, pack.displayName, missing));
        } else if (pack.type == StorePack.PackType.MAP) {
            int bw = (w - 24 - 8) / 2;
            button(x + 12, by, bw, 24, "Done", Style.DARK, true, () -> mc.displayGuiScreen(parent));
            splitButton(x + 12 + bw + 8, by, bw, 24, "Edit Map", Style.PRIMARY,
                    () -> EditMenu.editInWorld(this, pack.displayName),
                    (ar, ab) -> EditMenu.open(this, pack.displayName, ar, ab));
        } else {
            button(x + (w - 110) / 2, by, 110, 24, "Done", Style.PRIMARY, true, () -> mc.displayGuiScreen(parent));
        }
    }

    private void drawFailure(int x, int y, int w, int bottom, String title, String message) {
        if (!failSounded) {
            failSounded = true;
            UiSounds.warn();
        }
        BedrockUI.rect(x + 12, y, w - 24, 26, BedrockUI.OUTLINE);
        BedrockUI.rect(x + 13, y + 1, w - 26, 24, 0xFF4A1A1A);
        BedrockUI.rect(x + 13, y + 1, 3, 24, BedrockUI.RED);
        fontRenderer.drawString(title, x + 24, y + 9, BedrockUI.TEXT, true);

        List<String> lines = fontRenderer.listFormattedStringToWidth(message == null ? "" : message, w - 24);
        int ly = y + 32;
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            fontRenderer.drawString(lines.get(i), x + 12, ly, BedrockUI.TEXT_DIM, false);
            ly += 10;
        }

        int bw = (w - 24 - 8) / 2;
        int by = bottom - 34;
        button(x + 12, by, bw, 24, "Back", Style.DARK, true, () -> mc.displayGuiScreen(parent));
        button(x + 12 + bw + 8, by, bw, 24, "Try Again", Style.PRIMARY, true, () -> {
            celebratedAt = 0;
            failSounded = false;
            start();
        });
    }
}
