package com.matoon.herosmp.client.gui;

import com.matoon.herosmp.client.gui.bedrock.BedrockScreen;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI.Style;
import com.matoon.herosmp.client.store.MapEditorLauncher;
import com.matoon.herosmp.client.store.PackThumbnails;
import com.matoon.herosmp.client.store.StorePack;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.integrated.IntegratedServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Picks the world save a KIT pack installs into. Worlds are folders under {@code <gameDir>/saves}
 * with a {@code level.dat}, sorted by last played. The world currently open (if any) can't be
 * chosen, because its running server would overwrite the change when it saves.
 */
public class GuiWorldSelect extends BedrockScreen {

    private static final int FOOTER_H = 38;
    private static final ItemStack WORLD_ICON = new ItemStack(Blocks.GRASS);

    private final StorePack pack;
    private final List<WorldEntry> worlds = new ArrayList<WorldEntry>();
    private boolean scanned;
    private int selected = -1;

    private final SmoothScroll scroll = new SmoothScroll();
    private float contentH;

    public GuiWorldSelect(GuiScreen parent, StorePack pack) {
        super(parent, "Choose a World");
        this.pack = pack;
    }

    @Override
    public void initGui() {
        super.initGui();
        if (!scanned) {
            scanned = true;
            scanWorlds();
        }
    }

    private void scanWorlds() {
        worlds.clear();
        File[] dirs = new File(mc.gameDir, "saves").listFiles(File::isDirectory);
        if (dirs == null) return;
        IntegratedServer running = mc.getIntegratedServer();
        String openFolder = running != null ? running.getFolderName() : null;
        for (File dir : dirs) {
            if (!new File(dir, "level.dat").isFile()) continue;
            if (MapEditorLauncher.EDITOR_FOLDER.equals(dir.getName())) continue;
            worlds.add(readWorld(dir, dir.getName().equals(openFolder)));
        }
        Collections.sort(worlds, (a, b) -> Long.compare(b.lastPlayed, a.lastPlayed));
    }

    private static WorldEntry readWorld(File dir, boolean open) {
        String name = dir.getName();
        long lastPlayed = dir.lastModified();
        try (InputStream in = new FileInputStream(new File(dir, "level.dat"))) {
            NBTTagCompound data = CompressedStreamTools.readCompressed(in).getCompoundTag("Data");
            if (!data.getString("LevelName").isEmpty()) name = data.getString("LevelName");
            if (data.hasKey("LastPlayed")) lastPlayed = data.getLong("LastPlayed");
        } catch (Exception ignored) {
            // Folder name / mtime fallbacks are fine.
        }
        return new WorldEntry(dir, name, lastPlayed, open);
    }

    @Override
    protected void onScroll(int direction) {
        scroll.add(-direction * 36);
    }

    private void install() {
        if (selected < 0 || selected >= worlds.size()) return;
        mc.displayGuiScreen(new GuiDownloadProgress(parent, pack, worlds.get(selected).dir));
    }

    // ---------------------------------------------------------------------

    @Override
    protected void drawBody(float partialTicks) {
        int cw = Math.min(width - 28, 520);
        int left = (width - cw) / 2;
        int top = HEADER_H + 12;
        float sc = scroll.value();
        int y = top - (int) sc;

        fontRenderer.drawString(BedrockUI.trim(fontRenderer, "Install \"" + pack.displayName + "\" into which world?", cw),
                left, y, BedrockUI.TEXT, true);
        fontRenderer.drawString(BedrockUI.trim(fontRenderer,
                "Its kits are added to that world's kit selection. Existing kits with the same name are replaced.", cw),
                left, y + 12, BedrockUI.TEXT_MUTED, false);
        y += 30;

        if (worlds.isEmpty()) {
            int cx = width / 2;
            BedrockUI.item(WORLD_ICON, cx - 24, y + 8, 3f);
            String a = "No worlds yet";
            String b = "Create a world in Singleplayer first, then come back.";
            fontRenderer.drawString(a, cx - fontRenderer.getStringWidth(a) / 2, y + 64, BedrockUI.TEXT, true);
            fontRenderer.drawString(b, cx - fontRenderer.getStringWidth(b) / 2, y + 78, BedrockUI.TEXT_DIM, false);
            contentH = 0;
            return;
        }

        int rowH = 46;
        SimpleDateFormat fmt = new SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH);
        for (int i = 0; i < worlds.size(); i++) {
            drawRow(i, worlds.get(i), left, y, cw, rowH, fmt);
            y += rowH + 5;
        }

        contentH = y + sc - top + FOOTER_H;
        scroll.clamp(contentH - (height - top));
    }

    private void drawRow(final int index, WorldEntry world, int x, int y, int w, int h, SimpleDateFormat fmt) {
        boolean enabled = !world.open;
        boolean hov = enabled && hover(x, y, w, h);
        boolean sel = index == selected;
        if (enabled) hit(x, y, w, h, () -> selected = index);

        BedrockUI.rect(x, y, w, h, sel ? BedrockUI.GREEN_LIGHT : BedrockUI.OUTLINE);
        int face = sel ? 0xFF2C3B27 : (hov ? BedrockUI.PANEL_HOVER : BedrockUI.PANEL);
        BedrockUI.rect(x + 1, y + 1, w - 2, h - 2, face);
        if (sel) BedrockUI.outline(x + 1, y + 1, w - 2, h - 2, 1, BedrockUI.GREEN);

        int s = h - 12;
        BedrockUI.rect(x + 5, y + 5, s + 2, s + 2, BedrockUI.OUTLINE);
        PackThumbnails.Thumb icon = PackThumbnails.world(world.dir);
        if (icon != null) {
            BedrockUI.imageCover(icon, x + 6, y + 6, s, s, enabled ? 1f : 0.4f, false);
        } else {
            BedrockUI.vGradient(x + 6, y + 6, s, s, 0xFF3E7A32, 0xFF16301A);
            BedrockUI.item(WORLD_ICON, x + 6 + (s - 16) / 2f, y + 6 + (s - 16) / 2f, 1f);
        }

        int tx = x + 6 + s + 10;
        int avail = x + w - 30 - tx;
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, world.name, avail), tx, y + 10,
                enabled ? BedrockUI.TEXT : BedrockUI.TEXT_MUTED, enabled);
        String sub = world.open ? "Open right now. Leave the world to install kits here"
                : world.dir.getName() + "  ·  Last played " + fmt.format(new Date(world.lastPlayed));
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, sub, avail), tx, y + 24,
                world.open ? 0xFFE0A040 : BedrockUI.TEXT_DIM, false);

        if (sel) BedrockUI.check(x + w - 20, y + h / 2 - 4, BedrockUI.GREEN_LIGHT);
    }

    @Override
    protected void drawFooter() {
        int y = height - FOOTER_H;
        BedrockUI.vGradient(0, y - 6, width, 6, 0x00000000, 0x66000000);
        BedrockUI.rect(0, y, width, FOOTER_H, BedrockUI.PANEL);
        BedrockUI.rect(0, y, width, 1, BedrockUI.OUTLINE);
        block(0, y, width, FOOTER_H);

        int bw = 110;
        int bx = width - 10 - bw;
        button(bx, y + 7, bw, 24, "Install Here", Style.PRIMARY, selected >= 0, this::install);
        button(bx - 8 - 90, y + 7, 90, 24, "Cancel", Style.DARK, true, this::goBack);

        String hint = selected >= 0 ? "Selected: " + worlds.get(selected).name : "Select a world";
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, hint, bx - 8 - 90 - 20), 10, y + 15, BedrockUI.TEXT_DIM, false);
    }

    private static final class WorldEntry {
        final File dir;
        final String name;
        final long lastPlayed;
        final boolean open;
        WorldEntry(File dir, String name, long lastPlayed, boolean open) {
            this.dir = dir;
            this.name = name;
            this.lastPlayed = lastPlayed;
            this.open = open;
        }
    }
}
