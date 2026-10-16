package com.matoon.herosmp.client.gui;

import com.matoon.herosmp.HeroSMP;
import com.matoon.herosmp.client.gui.bedrock.BedrockScreen;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI.Style;
import com.matoon.herosmp.client.store.InstalledPacks;
import com.matoon.herosmp.client.store.PackMeta;
import com.matoon.herosmp.client.store.PackThumbnails;
import com.matoon.herosmp.client.store.StoreCatalog;
import com.matoon.herosmp.client.store.StorePack;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Content store styled after Minecraft Bedrock's Marketplace.
 *
 * Discover: a rotating featured hero banner, then one horizontally scrolling row of tiles per
 * category. Installed: a management list of on-disk maps with Edit / Delete. Tiles and rows open
 * {@link GuiPackDetail}, the product page.
 */
public class GuiPackStore extends BedrockScreen {

    private enum Tab { DISCOVER, INSTALLED }

    private static final int GAP = 8;
    private static final int MIN_TILE_W = 112;
    private static final long HERO_INTERVAL_MS = 7000L;

    private Tab tab = Tab.DISCOVER;

    // Catalog (written by the fetch thread).
    private volatile boolean loading = true;
    private volatile String error;
    private volatile List<StorePack> packs = Collections.emptyList();
    private boolean fetchStarted;

    // Installed state, rescanned whenever this screen is (re)shown.
    private List<String> installedMaps = Collections.emptyList();
    private final Set<String> installedLower = new HashSet<String>();
    private final Map<String, Long> installedSizes = new HashMap<String, Long>();
    private long installedTotal;

    private final SmoothScroll page = new SmoothScroll();
    private final Map<String, SmoothScroll> rows = new HashMap<String, SmoothScroll>();
    private float contentH;

    private int heroIndex;
    private long heroSince = System.currentTimeMillis();

    public GuiPackStore(GuiScreen parent) {
        super(parent, "Content Packs");
        this.headerLogo = true;
    }

    /** Store opened straight onto the Installed tab. */
    public static GuiPackStore installedTab(GuiScreen parent) {
        GuiPackStore s = new GuiPackStore(parent);
        s.tab = Tab.INSTALLED;
        return s;
    }

    // ---------------------------------------------------------------------
    // Data
    // ---------------------------------------------------------------------

    @Override
    public void initGui() {
        super.initGui();
        if (!fetchStarted) {
            fetchStarted = true;
            startFetch();
        }
        refreshInstalled();
    }

    private void startFetch() {
        loading = true;
        error = null;
        final String folderId = HeroSMP.storeDriveFolderId;
        Thread t = new Thread(() -> {
            StoreCatalog.Result r = StoreCatalog.fetch(folderId);
            packs = r.packs;
            error = r.error;
            loading = false;
        }, "HeroSMP store fetch");
        t.setDaemon(true);
        t.start();
    }

    private void retry() {
        PackMeta.clear();
        PackThumbnails.disposeAll();
        startFetch();
    }

    private void refreshInstalled() {
        installedMaps = InstalledPacks.listInstalledMaps();
        installedLower.clear();
        installedSizes.clear();
        installedTotal = 0;
        for (String name : installedMaps) {
            installedLower.add(name.toLowerCase(Locale.ROOT));
            long size = InstalledPacks.sizeOf(name);
            installedSizes.put(name, size);
            installedTotal += size;
        }
    }

    private boolean isInstalled(StorePack p) {
        return p.type == StorePack.PackType.MAP && installedLower.contains(p.displayName.toLowerCase(Locale.ROOT));
    }

    private List<StorePack> ofType(StorePack.PackType type) {
        List<StorePack> out = new ArrayList<StorePack>();
        for (StorePack p : packs) if (p.type == type) out.add(p);
        return out;
    }

    /** The store's entry for an installed map (so the product page can reinstall), else a local stub. */
    private StorePack packForInstalled(String name) {
        for (StorePack p : packs) {
            if (p.type == StorePack.PackType.MAP && p.displayName.equalsIgnoreCase(name)) return p;
        }
        return new StorePack(StorePack.PackType.MAP, name, "", true);
    }

    // ---------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------

    private void switchTab(Tab t) {
        if (tab == t) return;
        tab = t;
        page.set(0);
        if (t == Tab.INSTALLED) refreshInstalled();
    }

    private void openDetail(StorePack p) {
        mc.displayGuiScreen(new GuiPackDetail(this, p));
    }

    private void download(StorePack p) {
        if (p.type == StorePack.PackType.KIT) mc.displayGuiScreen(new GuiWorldSelect(this, p));
        else mc.displayGuiScreen(new GuiDownloadProgress(this, p, null));
    }

    void editMap(final String name) {
        EditMenu.editInWorld(this, name);
    }

    void deleteMap(final String name) {
        confirm("Delete " + name + "?",
                "This permanently removes the map's files from herosmp_hg_maps. You can download it again from the store.",
                "Delete", Style.DANGER, () -> {
                    InstalledPacks.deleteMap(name);
                    refreshInstalled();
                    toast("Deleted " + name);
                });
    }

    // ---------------------------------------------------------------------
    // Header
    // ---------------------------------------------------------------------

    private int tabSegW() { return width < 320 ? 64 : 84; }
    private int tabsX()   { return width - 8 - tabSegW() * 2 - 4; }

    @Override
    protected int headerTitleLimit() {
        return tabsX() - 10;
    }

    @Override
    protected void drawHeaderExtras() {
        int segW = tabSegW();
        int x = tabsX();
        button(x, 4, segW, 22, "Discover", tab == Tab.DISCOVER ? Style.PRIMARY : Style.DARK, true,
                () -> switchTab(Tab.DISCOVER));
        String inst = "Installed" + (installedMaps.isEmpty() ? "" : " (" + installedMaps.size() + ")");
        button(x + segW + 4, 4, segW, 22, inst, tab == Tab.INSTALLED ? Style.PRIMARY : Style.DARK, true,
                () -> switchTab(Tab.INSTALLED));
    }

    // ---------------------------------------------------------------------
    // Body
    // ---------------------------------------------------------------------

    private int contentWidth() { return Math.min(width - 28, 640); }

    @Override
    protected void onScroll(int direction) {
        page.add(-direction * 42);
    }

    @Override
    protected void drawBody(float partialTicks) {
        int cw = contentWidth();
        int left = (width - cw) / 2;
        int top = HEADER_H + 12;
        float scroll = page.value();
        int y = top - (int) scroll;

        if (tab == Tab.DISCOVER) {
            if (loading) {
                y = drawSkeleton(left, y, cw);
            } else if (error != null) {
                drawError(error);
                return;
            } else {
                List<StorePack> maps = ofType(StorePack.PackType.MAP);
                List<StorePack> kits = ofType(StorePack.PackType.KIT);
                if (!maps.isEmpty()) y = drawHero(maps, left, y, cw) + 20;
                y = drawSection("maps", "Hunger Games Maps", "Battle arenas for Hero Games matches", maps, left, y, cw) + 20;
                y = drawSection("kits", "PVP Kits", "Ready-made loadouts for PVP arenas", kits, left, y, cw) + 12;
            }
        } else {
            y = drawInstalled(left, y, cw);
        }

        contentH = y + scroll - top;
        page.clamp(contentH - (height - top));
        drawScrollbar(top, scroll);
    }

    private void drawScrollbar(int top, float scroll) {
        float viewH = height - top;
        if (contentH <= viewH) return;
        int x = width - 6;
        BedrockUI.rect(x, top, 3, height - top - 4, 0x26FFFFFF);
        float max = contentH - viewH;
        int thumbH = Math.max(18, (int) ((height - top - 4) * (viewH / contentH)));
        int thumbY = top + (int) ((height - top - 4 - thumbH) * Math.min(1f, scroll / max));
        BedrockUI.rect(x, thumbY, 3, thumbH, 0xA0FFFFFF);
    }

    // ---------------------------------------------------------------------
    // Tile metrics
    // ---------------------------------------------------------------------

    private int perRow(int cw) { return Math.max(2, (cw + GAP) / (MIN_TILE_W + GAP)); }
    private int tileW(int cw)  { int n = perRow(cw); return (cw - (n - 1) * GAP) / n; }
    private int thumbH(int tw) { return tw * 9 / 16; }
    private int tileH(int tw)  { return thumbH(tw) + 36; }

    // ---------------------------------------------------------------------
    // Hero
    // ---------------------------------------------------------------------

    private int drawHero(List<StorePack> maps, int left, int y, int cw) {
        int heroH = Math.max(100, Math.min(156, (int) (cw * 0.30f)));
        boolean hovered = hover(left, y, cw, heroH);
        long now = System.currentTimeMillis();
        if (heroIndex >= maps.size()) heroIndex = 0;
        if (!hovered && maps.size() > 1 && now - heroSince > HERO_INTERVAL_MS) {
            heroIndex = (heroIndex + 1) % maps.size();
            heroSince = now;
        }
        final StorePack pack = maps.get(heroIndex);
        boolean installed = isInstalled(pack);
        PackThumbnails.Thumb art = PackArt.thumb(pack, installed);

        BedrockUI.shadow(left - 1, y - 1, cw + 2, heroH + 2);
        BedrockUI.rect(left - 1, y - 1, cw + 2, heroH + 2, BedrockUI.OUTLINE);
        hit(left, y, cw, heroH, () -> openDetail(pack));

        BedrockUI.pushClip(left, y, cw, heroH);

        // Backdrop: the art blown up and softened, then darkened toward the text side.
        if (art != null) {
            BedrockUI.imageCover(art, left, y, cw, heroH, 1f, true);
            BedrockUI.rect(left, y, cw, heroH, 0x55000000);
        } else {
            BedrockUI.vGradient(left, y, cw, heroH, 0xFF2F5E28, 0xFF132611);
        }
        BedrockUI.hGradient(left, y, cw * 2 / 3, heroH, 0xF0121213, 0x00121213);
        BedrockUI.vGradient(left, y + heroH - 44, cw, 44, 0x00000000, 0xB0000000);

        // Framed, crisp key art on the right.
        boolean showFrame = cw >= 300;
        int textMax = cw - 28;
        if (showFrame) {
            int fh = heroH - 34;
            int fw = art != null && art.w >= art.h * 1.3f ? fh * 16 / 9 : fh;
            fw = Math.min(fw, (int) (cw * 0.42f));
            if (art != null && art.w >= art.h * 1.3f) fh = fw * 9 / 16;
            int fx = left + cw - fw - 16;
            int fy = y + (heroH - fh) / 2 - 4;
            BedrockUI.shadow(fx - 2, fy - 2, fw + 4, fh + 4);
            BedrockUI.rect(fx - 2, fy - 2, fw + 4, fh + 4, BedrockUI.OUTLINE);
            BedrockUI.rect(fx - 1, fy - 1, fw + 2, fh + 2, 0xFFE8E8E8);
            if (art != null) BedrockUI.imageCover(art, fx, fy, fw, fh, 1f, false);
            else PackArt.placeholder(pack.type, fx, fy, fw, fh);
            textMax = fx - 18 - (left + 14);
        }

        int tx = left + 14;
        BedrockUI.chip(fontRenderer, tx, y + 12, "FEATURED", BedrockUI.GOLD, BedrockUI.TEXT_DARK);
        if (installed) {
            BedrockUI.chip(fontRenderer, tx + BedrockUI.chipWidth(fontRenderer, "FEATURED") + 4, y + 12,
                    "INSTALLED", BedrockUI.GREEN, BedrockUI.TEXT);
        }
        String title = BedrockUI.trim(fontRenderer, pack.displayName, Math.max(40, textMax / 2));
        BedrockUI.text(fontRenderer, title, tx, y + 31, BedrockUI.TEXT, 2f, true);
        fontRenderer.drawString(pack.typeLabel() + "  ·  Free", tx, y + 52, BedrockUI.TEXT_DIM, false);

        int by = y + heroH - 34;
        int primaryW;
        if (installed) {
            primaryW = 104;
            splitButton(tx, by, primaryW, 22, "Edit Map", Style.PRIMARY, () -> editMap(pack.displayName),
                    (ar, ab) -> EditMenu.open(this, pack.displayName, ar, ab));
        } else {
            primaryW = 86;
            button(tx, by, primaryW, 22, "Download", Style.PRIMARY, true, () -> download(pack));
        }
        button(tx + primaryW + 6, by, 70, 22, "Details", Style.DARK, true, () -> openDetail(pack));

        // Page dots.
        if (maps.size() > 1) {
            int dotsW = 0;
            for (int i = 0; i < maps.size(); i++) dotsW += (i == heroIndex ? 12 : 5) + 4;
            int dx = left + (cw - dotsW) / 2;
            int dy = y + heroH - 7;
            for (int i = 0; i < maps.size(); i++) {
                final int idx = i;
                int dw = i == heroIndex ? 12 : 5;
                BedrockUI.rect(dx, dy, dw, 3, i == heroIndex ? 0xFFFFFFFF : 0x80FFFFFF);
                hit(dx - 2, dy - 4, dw + 4, 10, () -> { heroIndex = idx; heroSince = System.currentTimeMillis(); });
                dx += dw + 4;
            }
        }

        // Quick fade when the slide changes.
        long since = now - heroSince;
        if (since < 280) BedrockUI.rect(left, y, cw, heroH, ((int) ((1 - since / 280f) * 170)) << 24);

        BedrockUI.popClip();
        return y + heroH;
    }

    // ---------------------------------------------------------------------
    // Category rows
    // ---------------------------------------------------------------------

    private int drawSection(String key, String title, String subtitle, List<StorePack> items,
                            int left, int y, int cw) {
        fontRenderer.drawString(title, left, y, BedrockUI.TEXT, true);
        BedrockUI.chip(fontRenderer, left + fontRenderer.getStringWidth(title) + 6, y - 2,
                String.valueOf(items.size()), BedrockUI.PANEL_LIGHT, BedrockUI.TEXT_DIM);
        fontRenderer.drawString(subtitle, left, y + 12, BedrockUI.TEXT_MUTED, false);

        int rowY = y + 28;
        int tw = tileW(cw), th = tileH(tw);

        if (items.isEmpty()) {
            BedrockUI.rect(left, rowY, cw, 50, BedrockUI.OUTLINE);
            BedrockUI.rect(left + 1, rowY + 1, cw - 2, 48, BedrockUI.SURFACE);
            String a = "Nothing here yet";
            String b = "New packs show up here as soon as they're added to the store.";
            fontRenderer.drawString(a, left + (cw - fontRenderer.getStringWidth(a)) / 2, rowY + 14, BedrockUI.TEXT_DIM, false);
            b = BedrockUI.trim(fontRenderer, b, cw - 16);
            fontRenderer.drawString(b, left + (cw - fontRenderer.getStringWidth(b)) / 2, rowY + 28, BedrockUI.TEXT_MUTED, false);
            return rowY + 50;
        }

        SmoothScroll rs = rows.get(key);
        if (rs == null) { rs = new SmoothScroll(); rows.put(key, rs); }
        int totalW = items.size() * tw + (items.size() - 1) * GAP;
        final int maxOff = Math.max(0, totalW - cw);
        rs.clamp(maxOff);
        final int step = (tw + GAP) * Math.max(1, perRow(cw) - 1);

        if (maxOff > 0) {
            final SmoothScroll r = rs;
            boolean canLeft = rs.target() > 0, canRight = rs.target() < maxOff;
            int ax = left + cw - 46;
            arrowButton(ax, y - 2, true, canLeft, () -> { r.add(-step); r.clamp(maxOff); });
            arrowButton(ax + 24, y - 2, false, canRight, () -> { r.add(step); r.clamp(maxOff); });
        }

        float off = rs.value();
        BedrockUI.pushClip(left - 3, rowY - 4, cw + 6, th + 8);
        for (int i = 0; i < items.size(); i++) {
            int tx = left + i * (tw + GAP) - (int) off;
            if (tx + tw < left - 4 || tx > left + cw + 4) continue;
            drawTile(items.get(i), tx, rowY, tw);
        }
        // Edge shadows hint at more content off to the side (translucent so the backdrop shows).
        if (off > 1) BedrockUI.hGradient(left - 3, rowY - 4, 18, th + 8, 0x90000000, 0x00000000);
        if (off < maxOff - 1) BedrockUI.hGradient(left + cw - 15, rowY - 4, 18, th + 8, 0x00000000, 0x90000000);
        BedrockUI.popClip();
        return rowY + th + 2;
    }

    private void arrowButton(int x, int y, boolean left, boolean enabled, Runnable action) {
        boolean hov = enabled && hover(x, y, 20, 20);
        boolean down = hov && Mouse.isButtonDown(0);
        BedrockUI.button(fontRenderer, x, y, 20, 20, null, Style.DARK, enabled, hov, down);
        int cy = y + 9 + (down ? BedrockUI.LIP : 0);
        int color = enabled ? 0xFFFFFFFF : 0xFF8A8B8D;
        if (left) BedrockUI.arrowLeft(x + 9, cy, color);
        else BedrockUI.arrowRight(x + 10, cy, color);
        if (enabled) hit(x, y, 20, 20, action);
    }

    private void drawTile(final StorePack pack, int x, int y, int w) {
        int thumbH = thumbH(w);
        int h = tileH(w);
        boolean hov = hover(x, y, w, h);
        hit(x, y, w, h, () -> openDetail(pack));

        int dy = hov ? y - 2 : y;
        boolean installed = isInstalled(pack);

        if (hov) BedrockUI.shadow(x, dy, w, h);
        BedrockUI.rect(x, dy, w, h, BedrockUI.OUTLINE);
        PackArt.draw(pack, installed, x + 1, dy + 1, w - 2, thumbH);
        BedrockUI.vGradient(x + 1, dy + 1, w - 2, 18, 0x70000000, 0x00000000);

        if (installed) BedrockUI.chip(fontRenderer, x + 4, dy + 4, "INSTALLED", BedrockUI.GREEN, BedrockUI.TEXT);
        else BedrockUI.chip(fontRenderer, x + 4, dy + 4, "FREE", BedrockUI.PANEL, BedrockUI.TEXT);

        int infoY = dy + 1 + thumbH;
        BedrockUI.rect(x + 1, infoY, w - 2, h - 2 - thumbH, hov ? BedrockUI.PANEL_HOVER : BedrockUI.PANEL);
        BedrockUI.rect(x + 1, infoY, w - 2, 1, BedrockUI.OUTLINE);
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, pack.displayName, w - 10), x + 5, infoY + 6, BedrockUI.TEXT, true);
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, pack.typeLabel(), w - 10), x + 5, infoY + 19, BedrockUI.TEXT_MUTED, false);

        if (hov) BedrockUI.outline(x - 1, dy - 1, w + 2, h + 2, 1, 0xFFFFFFFF);
    }

    // ---------------------------------------------------------------------
    // Loading / error
    // ---------------------------------------------------------------------

    private int drawSkeleton(int left, int y, int cw) {
        int heroH = Math.max(100, Math.min(156, (int) (cw * 0.30f)));
        BedrockUI.skeleton(left, y, cw, heroH);
        y += heroH + 20;
        int tw = tileW(cw), th = tileH(tw), n = perRow(cw);
        for (int s = 0; s < 2; s++) {
            BedrockUI.skeleton(left, y, 120, 9);
            BedrockUI.skeleton(left, y + 13, 190, 7);
            int rowY = y + 28;
            for (int i = 0; i < n; i++) BedrockUI.skeleton(left + i * (tw + GAP), rowY, tw, th);
            y = rowY + th + 20;
        }
        return y;
    }

    private void drawError(String message) {
        int w = Math.min(300, width - 40);
        List<String> lines = fontRenderer.listFormattedStringToWidth(message, w - 24);
        if (lines.size() > 3) lines = lines.subList(0, 3);
        int h = 112 + lines.size() * 10;
        int x = (width - w) / 2;
        int y = HEADER_H + Math.max(10, (height - HEADER_H - h) / 2);

        BedrockUI.shadow(x, y, w, h);
        BedrockUI.rect(x, y, w, h, BedrockUI.OUTLINE);
        BedrockUI.rect(x + 1, y + 1, w - 2, h - 2, BedrockUI.PANEL);
        BedrockUI.item(new ItemStack(Blocks.BARRIER), x + w / 2f - 16, y + 12, 2f);

        String t = "Couldn't reach the store";
        fontRenderer.drawString(t, x + (w - fontRenderer.getStringWidth(t)) / 2, y + 52, BedrockUI.TEXT, true);
        int ly = y + 66;
        for (String l : lines) {
            fontRenderer.drawString(l, x + (w - fontRenderer.getStringWidth(l)) / 2, ly, BedrockUI.TEXT_DIM, false);
            ly += 10;
        }
        button(x + (w - 110) / 2, y + h - 34, 110, 24, "Try Again", Style.PRIMARY, true, this::retry);
    }

    // ---------------------------------------------------------------------
    // Installed tab
    // ---------------------------------------------------------------------

    private int drawInstalled(int left, int y, int cw) {
        String title = "Installed Maps";
        fontRenderer.drawString(title, left, y, BedrockUI.TEXT, true);
        BedrockUI.chip(fontRenderer, left + fontRenderer.getStringWidth(title) + 6, y - 2,
                String.valueOf(installedMaps.size()), BedrockUI.PANEL_LIGHT, BedrockUI.TEXT_DIM);
        if (!installedMaps.isEmpty()) {
            String total = InstalledPacks.formatBytes(installedTotal) + " on disk";
            fontRenderer.drawString(total, left + cw - fontRenderer.getStringWidth(total), y, BedrockUI.TEXT_DIM, false);
        }
        fontRenderer.drawString(BedrockUI.trim(fontRenderer,
                "Maps are shared by every world. PVP Kits are added to the world you pick when installing.", cw),
                left, y + 12, BedrockUI.TEXT_MUTED, false);
        y += 30;

        if (installedMaps.isEmpty()) {
            drawInstalledEmpty(y);
            return y + 140;
        }

        int rowH = 54;
        for (String name : installedMaps) {
            drawInstalledRow(name, left, y, cw, rowH);
            y += rowH + 6;
        }
        return y;
    }

    private void drawInstalledRow(final String name, int x, int y, int w, int h) {
        boolean hov = hover(x, y, w, h);
        final StorePack pack = packForInstalled(name);
        hit(x, y, w, h, () -> openDetail(pack));

        BedrockUI.rect(x, y, w, h, BedrockUI.OUTLINE);
        BedrockUI.rect(x + 1, y + 1, w - 2, h - 2, hov ? BedrockUI.PANEL_HOVER : BedrockUI.PANEL);
        BedrockUI.rect(x + 1, y + 1, w - 2, 1, 0xFF424346);

        int th = h - 12;
        int tw = th * 16 / 9;
        BedrockUI.rect(x + 5, y + 5, tw + 2, th + 2, BedrockUI.OUTLINE);
        PackArt.draw(pack, true, x + 6, y + 6, tw, th);

        int bw = 64, editW = 82, bh = 22;
        int by = y + (h - bh) / 2 - 1;
        int delX = x + w - 8 - bw;
        int editX = delX - 6 - editW;

        int textX = x + 6 + tw + 10;
        int avail = editX - 10 - textX;
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, name, avail), textX, y + 10, BedrockUI.TEXT, true);
        Long size = installedSizes.get(name);
        String sub = "Hunger Games Map  ·  " + InstalledPacks.formatBytes(size == null ? 0 : size);
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, sub, avail), textX, y + 23, BedrockUI.TEXT_DIM, false);
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, "herosmp_hg_maps/" + name, avail), textX, y + 35,
                BedrockUI.TEXT_MUTED, false);

        splitButton(editX, by, editW, bh, "Edit", Style.PRIMARY, () -> editMap(name),
                (ar, ab) -> EditMenu.open(this, name, ar, ab));
        button(delX, by, bw, bh, "Delete", Style.DANGER, true, () -> deleteMap(name));
    }

    private void drawInstalledEmpty(int y) {
        int cx = width / 2;
        BedrockUI.item(new ItemStack(Blocks.CHEST), cx - 24, y + 6, 3f);
        String a = "Nothing installed yet";
        String b = "Maps you download from Discover show up here.";
        fontRenderer.drawString(a, cx - fontRenderer.getStringWidth(a) / 2, y + 62, BedrockUI.TEXT, true);
        fontRenderer.drawString(b, cx - fontRenderer.getStringWidth(b) / 2, y + 76, BedrockUI.TEXT_DIM, false);
        button(cx - 60, y + 94, 120, 24, "Browse Store", Style.PRIMARY, true, () -> switchTab(Tab.DISCOVER));
    }
}
