package com.matoon.herosmp.client.gui;

import com.matoon.herosmp.client.gui.bedrock.BedrockScreen;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI;
import com.matoon.herosmp.client.gui.bedrock.UiSounds;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI.Style;
import com.matoon.herosmp.client.store.DriveClient;
import com.matoon.herosmp.client.store.InstalledPacks;
import com.matoon.herosmp.client.store.StorePack;
import com.matoon.herosmp.network.KitChunks;
import com.matoon.herosmp.network.PacketKitTransfer;
import com.matoon.herosmp.npc.kit.KitCard;
import com.matoon.herosmp.npc.kit.KitDefinition;
import com.matoon.herosmp.npc.kit.KitLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Kit download popup. Shows the kit, a progress bar and byte count while the file arrives — from
 * the server ({@code /heropvp kit download}), from the store's Drive folder, or straight from a
 * local kit — then saves it to {@code <gameDir>/beyonder_exports/PVP Kits/<Kit Name>.dat} and offers
 * to open that folder. The file is the template for kits published in the store's "PVP Kits" folder.
 */
public class GuiKitDownload extends BedrockScreen {

    /** When the finished state was first shown (0 = not yet): fanfare once, then a fading flash. */
    private long celebratedAt;
    private boolean failSounded;

    public static final String EXPORT_FOLDER = "PVP Kits";

    private enum State { CHOOSING, RUNNING, DONE, CANCELLED, ERROR }

    private final String name;
    private volatile State state = State.CHOOSING;
    /** Where the user chose to save (null until chosen). */
    private File target;
    /** Kit bytes that arrived before a location was chosen (client thread). */
    private byte[] ready;
    private boolean dialogShowing;
    private volatile long totalBytes; // <= 0 while unknown
    private volatile long doneBytes;
    private volatile String error;
    private volatile File savedFile;
    private volatile ItemStack icon = new ItemStack(Items.DIAMOND_SWORD);
    private volatile KitAvatar.Loadout loadout;
    private float shown; // eased progress, so instant transfers still read as a download

    // Server transfer in progress (client thread only).
    private static GuiKitDownload receiving;
    private int transferId;
    private KitChunks.Assembler assembler;

    private GuiKitDownload(GuiScreen parent, String name) {
        super(parent, "Download Kit");
        this.name = name == null || name.isEmpty() ? "Kit" : name;
    }

    public static File exportDir() {
        return new File(new File(Minecraft.getMinecraft().gameDir, "beyonder_exports"), EXPORT_FOLDER);
    }

    // ---------------------------------------------------------------------
    // Sources
    // ---------------------------------------------------------------------

    /** A chunk of {@code /heropvp kit download} arrived (client thread). */
    public static void receive(PacketKitTransfer p) {
        Minecraft mc = Minecraft.getMinecraft();
        GuiKitDownload screen = receiving;
        if (screen == null || screen.transferId != p.transferId) {
            GuiScreen parent = mc.currentScreen instanceof GuiKitDownload ? ((GuiKitDownload) mc.currentScreen).parent : mc.currentScreen;
            screen = new GuiKitDownload(parent, p.name);
            screen.transferId = p.transferId;
            screen.totalBytes = p.totalBytes;
            try {
                screen.assembler = new KitChunks.Assembler(p.totalChunks);
            } catch (IllegalArgumentException e) {
                screen.fail(e.getMessage());
            }
            receiving = screen;
            mc.displayGuiScreen(screen);
            screen.chooseLocation();
        }
        if ((screen.state != State.RUNNING && screen.state != State.CHOOSING) || screen.assembler == null) return;
        try {
            boolean complete = screen.assembler.accept(p.index, p.totalChunks, p.data);
            screen.doneBytes = screen.assembler.bytes();
            if (complete) {
                receiving = null;
                screen.finish(screen.assembler.result());
            }
        } catch (IllegalArgumentException e) {
            receiving = null;
            screen.fail(e.getMessage());
        }
    }

    /** Downloads a store kit pack's file from Drive. */
    public static void fromStore(GuiScreen parent, final StorePack pack) {
        final GuiKitDownload screen = new GuiKitDownload(parent, pack.displayName);
        Minecraft.getMinecraft().displayGuiScreen(screen);
        screen.chooseLocation(); // the file downloads while the user picks where to save it
        Thread t = new Thread(() -> {
            File tmp = null;
            try {
                tmp = File.createTempFile("beyonder_kit_", ".dat");
                DriveClient.downloadFile(pack.driveId, tmp, new DriveClient.DownloadListener() {
                    @Override public void bytesTransferred(long delta) { screen.doneBytes += delta; }
                    @Override public boolean isCancelled() { return screen.isClosed(); }
                });
                byte[] bytes = KitLibrary.readBytes(tmp);
                screen.totalBytes = bytes.length;
                screen.finish(bytes);
            } catch (Exception e) {
                screen.fail(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            } finally {
                if (tmp != null) tmp.delete();
            }
        }, "Beyonder kit download");
        t.setDaemon(true);
        t.start();
    }

    /** Saves a kit file that's already in memory (library / world / editor kits). */
    public static void local(GuiScreen parent, String name, byte[] file) {
        GuiKitDownload screen = new GuiKitDownload(parent, name);
        screen.totalBytes = file.length;
        screen.doneBytes = file.length;
        Minecraft.getMinecraft().displayGuiScreen(screen);
        screen.chooseLocation();
        screen.finish(file);
    }

    /** Opens the system "Save As" window; the kit is saved wherever the user picks. */
    private void chooseLocation() {
        if (!SaveFileDialog.available()) {
            // No desktop to show a window on: save to the game's export folder instead.
            onLocationChosen(new File(exportDir(), KitLibrary.safeFileName(name) + ".png"));
            return;
        }
        if (dialogShowing) return;
        dialogShowing = true;
        state = state == State.CANCELLED ? State.CHOOSING : state;
        SaveFileDialog.ask("Save Kit", KitLibrary.safeFileName(name), ".png", this::onLocationChosen);
    }

    /** Client thread. {@code file == null}: the user cancelled the Save window. */
    private void onLocationChosen(File file) {
        dialogShowing = false;
        if (closed || state == State.ERROR || state == State.DONE) return; // popup was closed meanwhile
        if (file == null) {
            state = State.CANCELLED;
            return;
        }
        target = file;
        state = State.RUNNING;
        if (ready != null) write(ready);
    }

    private boolean closed;

    private boolean isClosed() {
        return closed;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        closed = true;
    }

    @Override
    public void initGui() {
        super.initGui();
        closed = false;
    }

    // ---------------------------------------------------------------------

    /**
     * Saves the kit as a kit card: a low-resolution picture of the kit's figure (so the file's icon in
     * File Explorer shows the kit) with the kit file embedded. Falls back to a plain {@code .dat} if
     * the picture can't be rendered. Rendering needs the client thread, so other threads hand off.
     */
    private void finish(final byte[] bytes) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (!mc.isCallingFromMinecraftThread()) {
            mc.addScheduledTask(() -> finish(bytes));
            return;
        }
        try {
            KitLibrary.Parsed parsed = KitLibrary.parse(bytes);
            NBTTagCompound first = parsed.kits.get(0);
            ItemStack i = new ItemStack(first.getCompoundTag("Icon"));
            if (!i.isEmpty()) icon = i;
            loadout = KitAvatar.Loadout.of(KitDefinition.deserialize(first));
        } catch (IOException | RuntimeException e) {
            fail(e.getMessage());
            return;
        }
        ready = bytes;
        if (target != null) write(bytes); // otherwise wait for the user to pick a location
    }

    /** Writes the kit card to the chosen location (client thread: it renders the picture). */
    private void write(byte[] bytes) {
        try {
            byte[] kitFile = KitCard.isPng(bytes) ? KitCard.extract(bytes) : bytes;
            byte[] picture = KitAvatar.snapshotPng(loadout);
            File out = target;
            if (picture != null) {
                KitLibrary.writeAtomic(out, KitCard.embed(picture, kitFile));
            } else {
                // Couldn't render the picture: save the plain kit file instead.
                String n = out.getName();
                out = new File(out.getParentFile(), (n.toLowerCase().endsWith(".png") ? n.substring(0, n.length() - 4) : n) + ".dat");
                KitLibrary.writeAtomic(out, kitFile);
            }
            savedFile = out;
            state = State.DONE;
        } catch (IOException | RuntimeException e) {
            fail("Couldn't save to " + target + ": " + e.getMessage());
        }
    }

    private void fail(String message) {
        error = message == null ? "Unknown error" : message;
        state = State.ERROR;
    }

    private float fraction() {
        if (state == State.DONE) return 1f;
        long t = totalBytes;
        return t <= 0 ? -1f : Math.min(1f, doneBytes / (float) t);
    }

    /** Opens File Explorer with the saved kit selected. */
    private void openFolder() {
        if (savedFile != null) SaveFileDialog.reveal(savedFile);
    }

    // ---------------------------------------------------------------------

    @Override
    protected void drawBody(float partialTicks) {
        int w = Math.min(340, width - 30);
        int h = 196;
        int x = (width - w) / 2;
        int y = HEADER_H + Math.max(10, (height - HEADER_H - h) / 2);

        BedrockUI.shadow(x, y, w, h);
        BedrockUI.rect(x, y, w, h, BedrockUI.OUTLINE);
        BedrockUI.rect(x + 1, y + 1, w - 2, h - 2, BedrockUI.PANEL);

        // Kit summary: the kit's figure once it's known, its item icon before that.
        KitAvatar.Loadout l = loadout;
        BedrockUI.rect(x + 11, y + 7, 46, 52, BedrockUI.OUTLINE);
        BedrockUI.rect(x + 12, y + 8, 44, 50, 0xFF1A1B1D);
        if (l != null) KitAvatar.draw(l, "download:" + name, x + 12, y + 8, 44, 50, hover(x + 12, y + 8, 44, 50));
        else BedrockUI.item(icon, x + 18, y + 17, 2f);
        int tx = x + 64;
        int tw = x + w - 12 - tx;
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, name, tw), tx, y + 15, BedrockUI.TEXT, true);
        fontRenderer.drawString("Kit card (picture + kit file)", tx, y + 28, BedrockUI.TEXT_MUTED, false);
        File to = savedFile != null ? savedFile : target;
        String dest = to != null ? "To: " + to.getAbsolutePath() : "Choose where to save it...";
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, dest, tw), tx, y + 40, BedrockUI.TEXT_DIM, false);
        BedrockUI.rect(x + 12, y + 62, w - 24, 1, BedrockUI.PANEL_LIGHT);

        int cy = y + 72;
        float frac = fraction();
        if (frac >= 0) shown += (frac - shown) * 0.18f;
        if (state == State.CHOOSING) {
            drawChoosing(x, cy, w, y + h);
        } else if (state == State.DONE && shown < 0.995f) {
            drawRunning(x, cy, w, y + h, shown);
        } else if (state == State.RUNNING) {
            drawRunning(x, cy, w, y + h, frac < 0 ? -1f : shown);
        } else if (state == State.DONE) {
            drawDone(x, cy, w, y + h);
        } else if (state == State.CANCELLED) {
            drawCancelled(x, cy, w, y + h);
        } else {
            drawFailed(x, cy, w, y + h);
        }
    }

    private void drawChoosing(int x, int y, int w, int bottom) {
        fontRenderer.drawString("Choose where to save the kit", x + 12, y, BedrockUI.TEXT, true);
        List<String> lines = fontRenderer.listFormattedStringToWidth(
                "A Save window has opened. If you can't see it, it may be behind the game window.", w - 24);
        int ly = y + 14;
        for (String line : lines) {
            fontRenderer.drawString(line, x + 12, ly, BedrockUI.TEXT_DIM, false);
            ly += 10;
        }
        BedrockUI.loadingDots(x + w / 2, ly + 12);
        int bw = (w - 24 - 8) / 2;
        int by = bottom - 34;
        button(x + 12, by, bw, 24, "Cancel", Style.DARK, true, this::goBack);
        button(x + 12 + bw + 8, by, bw, 24, "Show Save Window", Style.PRIMARY, !dialogShowing, this::chooseLocation);
    }

    private void drawCancelled(int x, int y, int w, int bottom) {
        fontRenderer.drawString("Save cancelled", x + 12, y, BedrockUI.TEXT, true);
        fontRenderer.drawString("Nothing was saved. Pick a location to save the kit.", x + 12, y + 14, BedrockUI.TEXT_DIM, false);
        int bw = (w - 24 - 8) / 2;
        int by = bottom - 34;
        button(x + 12, by, bw, 24, "Close", Style.DARK, true, this::goBack);
        button(x + 12 + bw + 8, by, bw, 24, "Choose Location...", Style.PRIMARY, true, this::chooseLocation);
    }

    private void drawRunning(int x, int y, int w, int bottom, float frac) {
        String pct = frac < 0 ? "" : Math.round(frac * 100) + "%";
        int pctW = fontRenderer.getStringWidth(pct);
        fontRenderer.drawString("Downloading kit...", x + 12, y, BedrockUI.TEXT_DIM, false);
        fontRenderer.drawString(pct, x + w - 12 - pctW, y, BedrockUI.TEXT, true);
        BedrockUI.progressBar(x + 12, y + 12, w - 24, 14, frac);
        long total = totalBytes;
        long done = frac < 0 ? doneBytes : (long) (Math.max(0, total) * frac);
        String stats = InstalledPacks.formatBytes(done) + (total > 0 ? " of " + InstalledPacks.formatBytes(total) : "");
        fontRenderer.drawString(stats, x + 12, y + 32, BedrockUI.TEXT_MUTED, false);
        button(x + (w - 100) / 2, bottom - 34, 100, 24, "Cancel", Style.DARK, true, this::goBack);
    }

    private void drawDone(int x, int y, int w, int bottom) {
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
        fontRenderer.drawString("Kit saved. Open it in File Explorer?", x + 40, y + 9, BedrockUI.TEXT, true);

        String note = "The picture shows the kit and still works as the kit file: upload it to the store's \""
                + EXPORT_FOLDER + "\" Drive folder to publish it, or drop it into a server's beyonder_kits folder.";
        List<String> lines = fontRenderer.listFormattedStringToWidth(note, w - 24);
        int ly = y + 34;
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            fontRenderer.drawString(lines.get(i), x + 12, ly, BedrockUI.TEXT_DIM, false);
            ly += 10;
        }
        File f = savedFile;
        if (f != null) {
            fontRenderer.drawString(BedrockUI.trim(fontRenderer, f.getName() + "  ·  " + InstalledPacks.formatBytes(f.length()), w - 24),
                    x + 12, ly + 2, BedrockUI.TEXT_MUTED, false);
        }

        int bw = (w - 24 - 8) / 2;
        int by = bottom - 34;
        button(x + 12, by, bw, 24, "Close", Style.DARK, true, this::goBack);
        button(x + 12 + bw + 8, by, bw, 24, "Open in Explorer", Style.PRIMARY, true, this::openFolder);
    }

    private void drawFailed(int x, int y, int w, int bottom) {
        if (!failSounded) {
            failSounded = true;
            UiSounds.warn();
        }
        BedrockUI.rect(x + 12, y, w - 24, 26, BedrockUI.OUTLINE);
        BedrockUI.rect(x + 13, y + 1, w - 26, 24, 0xFF4A1A1A);
        BedrockUI.rect(x + 13, y + 1, 3, 24, BedrockUI.RED);
        fontRenderer.drawString("Download failed", x + 24, y + 9, BedrockUI.TEXT, true);
        List<String> lines = fontRenderer.listFormattedStringToWidth(error == null ? "" : error, w - 24);
        int ly = y + 34;
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            fontRenderer.drawString(lines.get(i), x + 12, ly, BedrockUI.TEXT_DIM, false);
            ly += 10;
        }
        button(x + (w - 100) / 2, bottom - 34, 100, 24, "Close", Style.DARK, true, this::goBack);
    }
}
