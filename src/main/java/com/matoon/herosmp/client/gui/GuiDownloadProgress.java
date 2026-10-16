package com.matoon.herosmp.client.gui;

import com.matoon.herosmp.client.gui.bedrock.BedrockScreen;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI.Style;
import com.matoon.herosmp.client.store.InstalledPacks;
import com.matoon.herosmp.client.store.PackInstaller;
import com.matoon.herosmp.client.store.StorePack;
import net.minecraft.client.gui.GuiScreen;

import java.io.File;
import java.util.List;

/**
 * Install screen: runs a {@link PackInstaller} and shows a Bedrock progress card with the pack
 * art, a striped progress bar, file / size / speed stats, and the result.
 */
public class GuiDownloadProgress extends BedrockScreen {

    private final StorePack pack;
    private final File targetWorldDir;

    private PackInstaller.Progress progress;

    // Smoothed download speed.
    private long lastBytes;
    private long lastSample;
    private double bytesPerSec;

    public GuiDownloadProgress(GuiScreen parent, StorePack pack, File targetWorldDir) {
        super(parent, "Installing");
        this.pack = pack;
        this.targetWorldDir = targetWorldDir;
    }

    @Override
    public void initGui() {
        super.initGui();
        if (progress == null) start();
    }

    private void start() {
        progress = PackInstaller.start(pack, targetWorldDir);
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
        String dest = targetWorldDir != null ? "Into world: " + targetWorldDir.getName() : "Into: Hunger Games maps";
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
        BedrockUI.rect(x + 12, y, w - 24, 26, BedrockUI.OUTLINE);
        BedrockUI.rect(x + 13, y + 1, w - 26, 24, 0xFF234A1A);
        BedrockUI.rect(x + 13, y + 1, 3, 24, BedrockUI.GREEN_LIGHT);
        BedrockUI.check(x + 24, y + 9, BedrockUI.GREEN_LIGHT);
        fontRenderer.drawString("Installed successfully", x + 40, y + 9, BedrockUI.TEXT, true);

        String sub = pack.type == StorePack.PackType.MAP
                ? "It's now part of your Hunger Games map rotation."
                : "Its kits were added to " + (targetWorldDir != null ? targetWorldDir.getName() : "the world") + ".";
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, sub, w - 24), x + 12, y + 34, BedrockUI.TEXT_DIM, false);

        int by = bottom - 34;
        if (pack.type == StorePack.PackType.MAP) {
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
        button(x + 12 + bw + 8, by, bw, 24, "Try Again", Style.PRIMARY, true, this::start);
    }
}
