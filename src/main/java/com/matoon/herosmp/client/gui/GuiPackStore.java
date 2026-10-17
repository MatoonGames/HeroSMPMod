package com.matoon.herosmp.client.gui;

import com.matoon.herosmp.HeroSMP;
import com.matoon.herosmp.client.gui.bedrock.BedrockScreen;
import com.matoon.herosmp.client.gui.editor.GuiKitEditor;
import com.matoon.herosmp.client.gui.bedrock.BedrockTextField;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI.Style;
import com.matoon.herosmp.client.gui.bedrock.UiSounds;
import com.matoon.herosmp.client.store.InstalledKits;
import com.matoon.herosmp.client.store.InstalledPacks;
import com.matoon.herosmp.client.store.PackInstaller;
import com.matoon.herosmp.client.store.PackMeta;
import com.matoon.herosmp.client.store.PackThumbnails;
import com.matoon.herosmp.client.store.StoreCatalog;
import com.matoon.herosmp.client.store.StorePack;
import com.matoon.herosmp.npc.kit.KitLibrary;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Mouse;

import java.io.IOException;
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
 * Discover: a search bar, a rotating featured hero banner, then one horizontally scrolling row of
 * tiles per category, each with "View all" for a full grid. Installed: Maps and Kits sub-tabs where
 * each installed map / kit can be enabled or disabled, edited, or deleted. Tiles and map rows open
 * {@link GuiPackDetail}, the product page.
 */
public class GuiPackStore extends BedrockScreen {

    private enum Tab { DISCOVER, INSTALLED }
    private enum InstalledTab { MAPS, KITS, WORLD, ADDONS }

    private static final int GAP = 8;
    private static final int MIN_TILE_W = 112;
    private static final long HERO_INTERVAL_MS = 7000L;
    private static final int BAR_H = 30;

    private Tab tab = Tab.DISCOVER;
    private InstalledTab installedTab = InstalledTab.MAPS;

    // Catalog (written by the fetch thread).
    private volatile boolean loading = true;
    private volatile String error;
    private volatile List<StorePack> packs = Collections.emptyList();
    private boolean fetchStarted;

    // Discover: search + "View all".
    private BedrockTextField search;
    private String query = "";
    private StorePack.PackType viewAll;

    // Installed state, rescanned whenever this screen is (re)shown.
    private List<String> installedMaps = Collections.emptyList();
    private final Set<String> installedLower = new HashSet<String>();
    private final Set<String> disabledLower = new HashSet<String>();
    private final Map<String, Long> installedSizes = new HashMap<String, Long>();
    private long installedTotal;
    private List<InstalledKits.WorldKits> installedKits = Collections.emptyList();
    private List<InstalledKits.Kit> libraryKits = Collections.emptyList();
    private List<InstalledKits.WorldInfo> knownWorlds = Collections.emptyList();
    private int installedKitCount;
    private List<com.matoon.herosmp.client.store.HeroAddons.Addon> installedAddons = Collections.emptyList();
    private final Set<String> installedAddonNames = new HashSet<String>();
    /** Mods / Hero Addons each installed kit is missing here (by kit tag), worked out on refresh. */
    private final Map<net.minecraft.nbt.NBTTagCompound, List<com.matoon.herosmp.npc.kit.KitRequirements.Requirement>> kitMissing =
            new java.util.IdentityHashMap<net.minecraft.nbt.NBTTagCompound, List<com.matoon.herosmp.npc.kit.KitRequirements.Requirement>>();

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
        if (search == null) {
            search = BedrockTextField.text(fontRenderer, 48, q -> {
                query = q.trim();
                page.set(0);
            });
        }
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
        }, "Beyonder store fetch");
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
        disabledLower.clear();
        installedSizes.clear();
        installedTotal = 0;
        for (String name : installedMaps) {
            String lower = name.toLowerCase(Locale.ROOT);
            installedLower.add(lower);
            if (!InstalledPacks.isMapEnabled(name)) disabledLower.add(lower);
            long size = InstalledPacks.sizeOf(name);
            installedSizes.put(name, size);
            installedTotal += size;
        }
        installedKits = InstalledKits.scan();
        libraryKits = InstalledKits.library();
        knownWorlds = InstalledKits.worlds();
        installedKitCount = libraryKits.size() + InstalledKits.totalKits(installedKits);
        installedAddons = com.matoon.herosmp.client.store.HeroAddons.list();
        installedAddonNames.clear();
        for (com.matoon.herosmp.client.store.HeroAddons.Addon a : installedAddons) {
            installedAddonNames.add(normName(a.name));
            installedAddonNames.add(normName(com.matoon.herosmp.client.store.StoreCatalog.addonTitle(a.file.getName())));
        }
        kitMissing.clear();
        for (InstalledKits.Kit k : libraryKits) kitMissing.put(k.tag, com.matoon.herosmp.npc.kit.KitRequirements.missing(k.tag));
        for (InstalledKits.WorldKits w : installedKits) for (InstalledKits.Kit k : w.kits) kitMissing.put(k.tag, com.matoon.herosmp.npc.kit.KitRequirements.missing(k.tag));
    }

    private static String normName(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private List<com.matoon.herosmp.npc.kit.KitRequirements.Requirement> missingFor(InstalledKits.Kit kit) {
        List<com.matoon.herosmp.npc.kit.KitRequirements.Requirement> m = kitMissing.get(kit.tag);
        return m != null ? m : Collections.<com.matoon.herosmp.npc.kit.KitRequirements.Requirement>emptyList();
    }

    private boolean isInstalled(StorePack p) {
        if (p.type == StorePack.PackType.ADDON) {
            return installedAddonNames.contains(normName(p.displayName)) || com.matoon.herosmp.client.store.HeroAddons.targetFor(p).exists();
        }
        return p.type == StorePack.PackType.MAP && installedLower.contains(p.displayName.toLowerCase(Locale.ROOT));
    }

    private boolean isDisabled(StorePack p) {
        return p.type == StorePack.PackType.MAP && disabledLower.contains(p.displayName.toLowerCase(Locale.ROOT));
    }

    private List<StorePack> ofType(StorePack.PackType type) {
        List<StorePack> out = new ArrayList<StorePack>();
        for (StorePack p : packs) if (p.type == type) out.add(p);
        return out;
    }

    private List<StorePack> matching(List<StorePack> source, String q) {
        if (q.isEmpty()) return source;
        String needle = q.toLowerCase(Locale.ROOT);
        List<StorePack> out = new ArrayList<StorePack>();
        for (StorePack p : source) {
            if (p.displayName.toLowerCase(Locale.ROOT).contains(needle)
                    || PackArt.title(p).toLowerCase(Locale.ROOT).contains(needle)
                    || PackArt.subtitle(p).toLowerCase(Locale.ROOT).contains(needle)) {
                out.add(p);
            }
        }
        return out;
    }

    /** The store's entry for an installed map (so the product page can reinstall), else a local stub. */
    private StorePack packForInstalled(String name) {
        for (StorePack p : packs) {
            if (p.type == StorePack.PackType.MAP && p.displayName.equalsIgnoreCase(name)) return p;
        }
        return new StorePack(StorePack.PackType.MAP, name, "", true);
    }

    private static String categoryName(StorePack.PackType type) {
        return type == StorePack.PackType.MAP ? "Hunger Games Maps" : type == StorePack.PackType.ADDON ? "Hero Addons" : "PVP Kits";
    }

    // ---------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------

    private void switchTab(Tab t) {
        if (tab == t) return;
        tab = t;
        page.set(0);
        transition();
        if (t == Tab.INSTALLED) refreshInstalled();
    }

    private void openViewAll(StorePack.PackType type) {
        viewAll = type;
        page.set(0);
        transition();
    }

    private void openDetail(StorePack p) {
        mc.displayGuiScreen(new GuiPackDetail(this, p));
    }

    private void download(StorePack p) {
        if (p.type == StorePack.PackType.KIT) installKitPack(this, p);
        else mc.displayGuiScreen(new GuiDownloadProgress(this, p, null));
    }

    /** Kit packs go into the Kit Library; first choose which worlds they're available in. */
    static void installKitPack(final GuiScreen from, final StorePack pack) {
        final net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        mc.displayGuiScreen(new GuiKitWorldPicker(from, "Install \"" + pack.displayName + "\"",
                "Pick the worlds where players can choose this kit. It's saved in your Kit Library.",
                Collections.<String>emptyList(), "Install",
                worlds -> mc.displayGuiScreen(new GuiDownloadProgress(from, pack, worlds))));
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

    private void toggleMap(String name) {
        boolean nowEnabled = InstalledPacks.setMapEnabled(name, !InstalledPacks.isMapEnabled(name));
        refreshInstalled();
        toast(name + (nowEnabled ? " is back in the match rotation" : " won't be picked for matches"));
    }

    /** {@code world == null} for Kit Library kits. */
    private void toggleKit(InstalledKits.Kit kit, InstalledKits.WorldKits world) {
        try {
            if (world == null) KitLibrary.setEnabled(kit.key, !kit.enabled);
            else InstalledKits.setEnabled(world.dir, kit.key, !kit.enabled);
            refreshInstalled();
            toast(kit.name() + (kit.enabled ? " disabled" : " enabled"));
        } catch (IOException e) {
            toast("Couldn't update the kit: " + e.getMessage());
        }
    }

    private void deleteKit(final InstalledKits.Kit kit, final InstalledKits.WorldKits world) {
        String where = world == null ? "your Kit Library (every world it was available in)" : world.worldName;
        confirm("Delete " + kit.name() + "?",
                "Removes this kit from " + where + ". Players won't be able to pick it anymore.",
                "Delete", Style.DANGER, () -> {
                    try {
                        if (world == null) KitLibrary.delete(kit.key);
                        else InstalledKits.delete(world.dir, kit.key);
                        refreshInstalled();
                        toast("Deleted " + kit.name());
                    } catch (IOException e) {
                        toast("Couldn't delete the kit: " + e.getMessage());
                    }
                });
    }

    // ---------------------------------------------------------------------
    // Uploads (from the user's computer)
    // ---------------------------------------------------------------------

    /** Pick a world folder or .zip; it's installed like a store map, with the same progress screen. */
    private void uploadMap() {
        SaveFileDialog.openFolderOrZip("Upload a Map", chosen -> {
            if (chosen == null) return;
            final String name;
            if (chosen.isDirectory()) {
                java.io.File world = PackInstaller.findWorld(chosen);
                if (world == null) {
                    toastWarn("That folder isn't a Minecraft world");
                    return;
                }
                name = world.getName();
            } else {
                String n = chosen.getName();
                name = n.toLowerCase(Locale.ROOT).endsWith(".zip") ? n.substring(0, n.length() - 4) : n;
            }
            final StorePack pack = StorePack.localMap(name, chosen);
            Runnable install = () -> mc.displayGuiScreen(new GuiDownloadProgress(this, pack, null));
            if (InstalledPacks.isMapInstalled(name)) {
                confirm("Replace " + name + "?", "A map called " + name + " is already installed. Uploading replaces it "
                        + "(its settings file is replaced too if the upload has one).", "Replace", Style.DANGER, install);
            } else {
                install.run();
            }
        });
    }

    /** Pick kit files (kit cards or .dat), choose their worlds, and add them to the Kit Library. */
    private void uploadKits() {
        SaveFileDialog.openFiles("Upload Kits", new String[]{".png", ".dat"}, files -> {
            if (files.isEmpty()) return;
            final List<byte[]> valid = new ArrayList<byte[]>();
            int kits = 0;
            List<String> rejected = new ArrayList<String>();
            for (java.io.File f : files) {
                try {
                    byte[] bytes = KitLibrary.readBytes(f);
                    kits += KitLibrary.parse(bytes).kits.size();
                    valid.add(bytes);
                } catch (IOException e) {
                    rejected.add(f.getName());
                }
            }
            if (valid.isEmpty()) {
                toast(files.size() == 1 ? "That file doesn't contain a kit" : "None of those files contain a kit");
                return;
            }
            final int total = kits;
            final String skipped = rejected.isEmpty() ? "" : " (skipped " + rejected.size() + " file" + (rejected.size() == 1 ? "" : "s") + " without a kit)";
            mc.displayGuiScreen(new GuiKitWorldPicker(this, "Upload " + total + (total == 1 ? " kit" : " kits"),
                    "Pick the worlds where players can choose " + (total == 1 ? "it" : "them") + ". Saved in your Kit Library.",
                    Collections.<String>emptyList(), "Add to Library", worlds -> {
                        List<String> names = new ArrayList<String>();
                        try {
                            for (byte[] b : valid) names.addAll(PackInstaller.installKitFile(b, worlds));
                        } catch (IOException e) {
                            mc.displayGuiScreen(this);
                            toast("Couldn't add the kit: " + e.getMessage());
                            return;
                        }
                        installedTab = InstalledTab.KITS;
                        mc.displayGuiScreen(this);
                        toast("Added " + (names.size() == 1 ? names.get(0) : names.size() + " kits") + skipped);
                        List<com.matoon.herosmp.npc.kit.KitRequirements.Requirement> missing = new ArrayList<com.matoon.herosmp.npc.kit.KitRequirements.Requirement>();
                        for (byte[] b : valid) {
                            try {
                                for (net.minecraft.nbt.NBTTagCompound k : KitLibrary.parse(b).kits) {
                                    for (com.matoon.herosmp.npc.kit.KitRequirements.Requirement r : com.matoon.herosmp.npc.kit.KitRequirements.missing(k)) {
                                        boolean dup = false;
                                        for (com.matoon.herosmp.npc.kit.KitRequirements.Requirement o : missing) dup |= o.id.equals(r.id);
                                        if (!dup) missing.add(r);
                                    }
                                }
                            } catch (IOException ignored) {
                                // Already validated above.
                            }
                        }
                        if (!missing.isEmpty()) MissingModsOverlay.show(this, names.size() == 1 ? names.get(0) : "these kits", missing);
                    }));
        });
    }

    /** Saves the kit as a standalone file: the template for the store's PVP Kits folder. */
    private void downloadKit(InstalledKits.Kit kit) {
        try {
            byte[] file = KitLibrary.toFileBytes(Collections.singletonList(kit.tag), Collections.<String>emptyList());
            GuiKitDownload.local(this, kit.name(), file);
        } catch (IOException e) {
            toast("Couldn't create the file: " + e.getMessage());
        }
    }

    @Override
    protected void goBack() {
        if (tab == Tab.DISCOVER && viewAll != null) {
            viewAll = null;
            page.set(0);
        } else if (tab == Tab.DISCOVER && !query.isEmpty()) {
            search.setText("");
            query = "";
            page.set(0);
        } else {
            super.goBack();
        }
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
        cue(UiSounds.Cue.TAB);
        button(x, 4, segW, 22, "Discover", tab == Tab.DISCOVER ? Style.PRIMARY : Style.DARK, true,
                () -> switchTab(Tab.DISCOVER));
        int total = installedMaps.size() + installedKitCount;
        String inst = "Installed" + (total == 0 ? "" : " (" + total + ")");
        cue(UiSounds.Cue.TAB);
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
        int barY = HEADER_H + 8;
        int top = barY + BAR_H;

        if (tab == Tab.DISCOVER && error != null && !loading) {
            drawError(error);
            return;
        }

        // Scrolling content, clipped below the pinned bar.
        float scroll = page.value();
        int y = top - (int) scroll;
        BedrockUI.pushClip(0, top - 4, width, height - top + 4);
        if (tab == Tab.DISCOVER) y = drawDiscover(left, y, cw);
        else if (installedTab == InstalledTab.MAPS) y = drawInstalledMaps(left, y, cw);
        else if (installedTab == InstalledTab.KITS) y = drawInstalledKits(left, y, cw);
        else if (installedTab == InstalledTab.ADDONS) y = drawInstalledAddons(left, y, cw);
        else y = drawWorldKits(left, y, cw);
        BedrockUI.popClip();

        contentH = y + scroll - top;
        page.clamp(contentH - (height - top));
        drawScrollbar(top, scroll);

        // Pinned bar over the top of the content.
        BedrockUI.clearDepth();
        if (tab == Tab.DISCOVER) drawSearchBar(left, barY, cw);
        else drawInstalledTabs(left, barY, cw);
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
    // Discover
    // ---------------------------------------------------------------------

    private void drawSearchBar(int left, int y, int cw) {
        int x = left, w = cw;
        if (viewAll != null) {
            int bw = fontRenderer.getStringWidth("All categories") + 26;
            boolean hov = hover(x, y, bw, 22);
            boolean down = hov && Mouse.isButtonDown(0);
            BedrockUI.button(fontRenderer, x, y, bw, 22, null, Style.DARK, true, hov, down);
            int sink = down ? BedrockUI.LIP : 0;
            BedrockUI.arrowLeft(x + 10, y + 10 + sink, 0xFFFFFFFF);
            fontRenderer.drawString("All categories", x + 18, y + 7 + sink, 0xFFFFFFFF, false);
            hit(x, y, bw, 22, () -> { viewAll = null; page.set(0); transition(); });
            x += bw + 6;
            w -= bw + 6;
        }
        String placeholder = viewAll != null ? "Search " + categoryName(viewAll) + "..." : "Search maps, kits and addons...";
        search.draw(this, x, y, w, 22, placeholder);
        // Magnifier glyph on the right of the field.
        BedrockUI.pixelIcon(new String[]{
                "..####.....", ".#....#....", "#......#...", "#......#...", "#......#...", ".#....#....",
                "..#####....", "......##...", ".......##..", "........##.", "..........."
        }, x + w - 18, y + 6, 1f, 0xFF7E7F82, 0xFF7E7F82);
        if (!query.isEmpty()) {
            button(x + w - 40, y + 3, 16, 16, "x", Style.DARK, true, () -> { search.setText(""); query = ""; page.set(0); });
        }
    }

    private int drawDiscover(int left, int y, int cw) {
        if (loading) return drawSkeleton(left, y, cw);

        if (viewAll != null || !query.isEmpty()) {
            List<StorePack> source = viewAll != null ? ofType(viewAll) : packs;
            List<StorePack> results = matching(source, query);
            String title = viewAll != null ? categoryName(viewAll)
                    : "Results for \"" + BedrockUI.trim(fontRenderer, query, cw / 2) + "\"";
            String sub = viewAll != null
                    ? (query.isEmpty() ? "Everything in this category" : results.size() + " matching \"" + query + "\"")
                    : results.size() + (results.size() == 1 ? " pack" : " packs");
            return drawGrid(title, sub, results, left, y, cw);
        }

        List<StorePack> maps = ofType(StorePack.PackType.MAP);
        List<StorePack> kits = ofType(StorePack.PackType.KIT);
        if (!maps.isEmpty()) y = drawHero(maps, left, y, cw) + 20;
        y = drawSection(StorePack.PackType.MAP, "Battle arenas for Hero Games matches", maps, left, y, cw) + 20;
        y = drawSection(StorePack.PackType.KIT, "Ready-made loadouts for PVP arenas", kits, left, y, cw) + 20;
        List<StorePack> addons = ofType(StorePack.PackType.ADDON);
        if (!addons.isEmpty()) {
            y = drawSection(StorePack.PackType.ADDON, "LucraftCore addon packs: new heroes and superpowers", addons, left, y, cw) + 12;
        }
        return y;
    }

    /** Full wrapped grid (search results / View all). */
    private int drawGrid(String title, String subtitle, List<StorePack> items, int left, int y, int cw) {
        fontRenderer.drawString(title, left, y, BedrockUI.TEXT, true);
        fontRenderer.drawString(subtitle, left, y + 12, BedrockUI.TEXT_MUTED, false);
        y += 28;
        if (items.isEmpty()) {
            BedrockUI.rect(left, y, cw, 60, BedrockUI.OUTLINE);
            BedrockUI.rect(left + 1, y + 1, cw - 2, 58, BedrockUI.SURFACE);
            String a = "No packs found";
            String b = query.isEmpty() ? "New packs show up here as soon as they're added." : "Try a different search.";
            fontRenderer.drawString(a, left + (cw - fontRenderer.getStringWidth(a)) / 2, y + 18, BedrockUI.TEXT_DIM, false);
            fontRenderer.drawString(b, left + (cw - fontRenderer.getStringWidth(b)) / 2, y + 32, BedrockUI.TEXT_MUTED, false);
            return y + 60;
        }
        int tw = tileW(cw), th = tileH(tw), n = perRow(cw);
        for (int i = 0; i < items.size(); i++) {
            int tx = left + (i % n) * (tw + GAP);
            int ty = y + (i / n) * (th + GAP);
            drawTile(items.get(i), tx, ty, tw);
        }
        int rowsCount = (items.size() + n - 1) / n;
        return y + rowsCount * (th + GAP);
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
            boolean disabled = isDisabled(pack);
            BedrockUI.chip(fontRenderer, tx + BedrockUI.chipWidth(fontRenderer, "FEATURED") + 4, y + 12,
                    disabled ? "DISABLED" : "INSTALLED", disabled ? 0xFF5A5B5D : BedrockUI.GREEN, BedrockUI.TEXT);
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

    private int drawSection(final StorePack.PackType type, String subtitle, List<StorePack> items,
                            int left, int y, int cw) {
        String title = categoryName(type);
        String key = type.name();
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

        // Header controls: [View all] then the scroll arrows (when the row overflows).
        int rightX = left + cw;
        if (maxOff > 0) {
            final SmoothScroll r = rs;
            boolean canLeft = rs.target() > 0, canRight = rs.target() < maxOff;
            arrowButton(rightX - 20, y - 2, false, canRight, () -> { r.add(step); r.clamp(maxOff); });
            arrowButton(rightX - 44, y - 2, true, canLeft, () -> { r.add(-step); r.clamp(maxOff); });
            rightX -= 50;
        }
        int vw = fontRenderer.getStringWidth("View all") + 16;
        button(rightX - vw, y - 2, vw, 20, "View all", Style.DARK, true, () -> openViewAll(type));

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
        if (pack.type == StorePack.PackType.ADDON) {
            drawAddonTile(pack, x, y, w);
            return;
        }
        int thumbH = thumbH(w);
        int h = tileH(w);
        boolean hov = hover(x, y, w, h);
        hit(x, y, w, h, () -> openDetail(pack));

        float lift = BedrockUI.easeOut(anim(pack, hov, 7f));
        int dy = y - Math.round(lift * 3);
        boolean installed = isInstalled(pack);
        boolean disabled = installed && isDisabled(pack);

        if (lift > 0.02f) {
            BedrockUI.glow(x, dy, w, h, installed ? BedrockUI.GREEN_LIGHT : 0xFFFFFF, lift);
            BedrockUI.rect(x + 2, dy + h, w, Math.round(lift * 3) + 1, BedrockUI.fade(0x60000000, lift));
        }
        BedrockUI.rect(x, dy, w, h, BedrockUI.OUTLINE);
        PackArt.draw(pack, installed, x + 1, dy + 1, w - 2, thumbH, hov);
        BedrockUI.sheen(x + 1, dy + 1, w - 2, thumbH, hoverSweep("tile:" + pack.displayName, hov), 55);
        if (disabled) BedrockUI.rect(x + 1, dy + 1, w - 2, thumbH, 0x99000000);
        BedrockUI.vGradient(x + 1, dy + 1, w - 2, 18, 0x70000000, 0x00000000);

        if (disabled) BedrockUI.chip(fontRenderer, x + 4, dy + 4, "DISABLED", 0xFF5A5B5D, BedrockUI.TEXT);
        else if (installed) BedrockUI.chip(fontRenderer, x + 4, dy + 4, "INSTALLED", BedrockUI.GREEN, BedrockUI.TEXT);
        else BedrockUI.chip(fontRenderer, x + 4, dy + 4, "FREE", BedrockUI.PANEL, BedrockUI.TEXT);

        int infoY = dy + 1 + thumbH;
        BedrockUI.rect(x + 1, infoY, w - 2, h - 2 - thumbH, hov ? BedrockUI.PANEL_HOVER : BedrockUI.PANEL);
        BedrockUI.rect(x + 1, infoY, w - 2, 1, BedrockUI.OUTLINE);
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, PackArt.title(pack), w - 10), x + 5, infoY + 6, BedrockUI.TEXT, true);
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, PackArt.subtitle(pack), w - 10), x + 5, infoY + 19, BedrockUI.TEXT_MUTED, false);

        if (lift > 0.02f) BedrockUI.outline(x - 1, dy - 1, w + 2, h + 2, 1, BedrockUI.fade(0xFFFFFFFF, lift));
    }

    /**
     * Hero Addon tile, deliberately unlike maps and kits: a border that flows between the logo's
     * two colours with a spark running round it, an automatic shimmer and twinkles over the art, a
     * "HERO ADDON" ribbon, and an info strip tinted with the logo's colours.
     */
    private void drawAddonTile(final StorePack pack, int x, int y, int w) {
        int thumbH = thumbH(w);
        int h = tileH(w);
        boolean hov = hover(x, y, w, h);
        hit(x, y, w, h, () -> openDetail(pack));
        com.matoon.herosmp.client.store.AddonMeta.Info info = com.matoon.herosmp.client.store.AddonMeta.remote(pack);
        int c1 = info != null ? info.primary : 0xFF7A5AC8, c2 = info != null ? info.secondary : 0xFF2C1E4A;
        long now = System.currentTimeMillis();
        float t = (now % 100000L) / 1000f;

        float lift = BedrockUI.easeOut(anim(pack, hov, 7f));
        int dy = y - Math.round(lift * 4);
        boolean installed = isInstalled(pack);

        // Glow in the logo's colour, always a little, more on hover.
        BedrockUI.glow(x, dy, w, h, c1 & 0xFFFFFF, 0.35f + 0.65f * lift);
        if (lift > 0.02f) BedrockUI.rect(x + 3, dy + h + 1, w, Math.round(lift * 4) + 1, BedrockUI.fade(0x60000000, lift));

        // Flowing two-colour border (2px) with a bright spark travelling round it.
        float flow = 0.5f + 0.5f * (float) Math.sin(t * 1.6);
        int edgeA = PackArt.mix(c1, c2, flow), edgeB = PackArt.mix(c2, c1, flow);
        BedrockUI.gradient(x - 2, dy - 2, w + 4, h + 4, PackArt.mix(edgeA, 0xFFFFFFFF, 0.25f), edgeB, edgeB, edgeA);
        BedrockUI.rect(x, dy, w, h, BedrockUI.OUTLINE);
        drawSpark(x - 2, dy - 2, w + 4, h + 4, (t % 3f) / 3f);

        // Art: logo on its own colours (or loading / initials).
        PackArt.draw(pack, installed, x + 1, dy + 1, w - 2, thumbH, hov);
        // Shimmer that sweeps across by itself every few seconds (and again on hover).
        float auto = ((now + Math.abs(pack.displayName.hashCode())) % 3600L) / 1200f;
        BedrockUI.sheen(x + 1, dy + 1, w - 2, thumbH, auto, 45);
        BedrockUI.sheen(x + 1, dy + 1, w - 2, thumbH, hoverSweep("tile:" + pack.displayName, hov), 70);
        // Twinkles.
        for (int i = 0; i < 4; i++) {
            int seed = Math.abs((pack.displayName.hashCode() * 31 + i * 977) % 1000);
            float tw = (float) Math.sin(t * (2.2 + i * 0.7) + seed);
            if (tw < 0.55f) continue;
            float a = (tw - 0.55f) / 0.45f;
            int sx = x + 6 + (seed * 37) % Math.max(1, w - 12), sy = dy + 6 + (seed * 53) % Math.max(1, thumbH - 12);
            int col = BedrockUI.fade(0xFFFFFFFF, a);
            BedrockUI.rect(sx, sy - 2, 1, 5, col);
            BedrockUI.rect(sx - 2, sy, 5, 1, col);
        }

        // Ribbon.
        String ribbon = "HERO ADDON";
        int rw = fontRenderer.getStringWidth(ribbon) + 18;
        BedrockUI.gradient(x + 1, dy + 4, rw, 12, c1, PackArt.mix(c1, c2, 0.5f), c1, PackArt.mix(c1, c2, 0.5f));
        BedrockUI.rect(x + 1, dy + 4, rw, 1, 0x60FFFFFF);
        BedrockUI.pixelIcon(new String[]{"..XX", ".XX.", "XXXX", ".XX.", "XX..", "X..."}, x + 4, dy + 7, 1f, 0xFFFFF4A0, 0xFFFFF4A0);
        fontRenderer.drawString(ribbon, x + 11, dy + 6, 0xFFFFFFFF, true);
        if (installed) {
            int iw = BedrockUI.chipWidth(fontRenderer, "INSTALLED");
            BedrockUI.chip(fontRenderer, x + w - 4 - iw, dy + 4, "INSTALLED", BedrockUI.GREEN, BedrockUI.TEXT);
        }

        // Info strip in the logo's colours.
        int infoY = dy + 1 + thumbH;
        int stripTop = PackArt.mix(c2, 0xFF000000, 0.55f), stripBottom = PackArt.mix(c2, 0xFF000000, 0.80f);
        BedrockUI.vGradient(x + 1, infoY, w - 2, h - 2 - thumbH, hov ? PackArt.mix(stripTop, 0xFFFFFFFF, 0.08f) : stripTop, stripBottom);
        BedrockUI.rect(x + 1, infoY, w - 2, 1, PackArt.mix(c1, 0xFF000000, 0.2f));
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, PackArt.title(pack), w - 10), x + 5, infoY + 6, BedrockUI.TEXT, true);
        String by = info != null && !info.author.isEmpty() ? "by " + info.author : "Hero Addon";
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, by, w - 10), x + 5, infoY + 19, PackArt.mix(c1, 0xFFFFFFFF, 0.55f), false);
    }

    /** A short bright streak at position {@code f} (0..1) along a rectangle's perimeter, clockwise from top-left. */
    private static void drawSpark(int x, int y, int w, int h, float f) {
        float per = 2f * (w + h), d = f * per;
        int len = 14;
        for (int i = 0; i < len; i++) {
            float pos = d - i;
            if (pos < 0) pos += per;
            float px, py;
            if (pos < w) { px = x + pos; py = y; }
            else if (pos < w + h) { px = x + w - 1; py = y + (pos - w); }
            else if (pos < 2 * w + h) { px = x + w - 1 - (pos - w - h); py = y + h - 1; }
            else { px = x; py = y + h - 1 - (pos - 2 * w - h); }
            int a = (int) (230 * (1f - i / (float) len));
            BedrockUI.rectF(px, py, px + 2, py + 2, (a << 24) | 0xFFFFFF);
        }
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
    // Installed: tab strip, filters, card grids
    // ---------------------------------------------------------------------

    private enum InstalledFilter { ALL, ENABLED, DISABLED }

    private InstalledFilter installedFilter = InstalledFilter.ALL;
    /** Sliding underline under the active installed tab (eased each frame). */
    private float tabLineX = -1, tabLineW;

    private static final int CARD_MIN_W = 168, CARD_GAP = 10, ACTION_H = 22;
    private static final String[] PLAY_ICON = {"#....", "##...", "###..", "####.", "###..", "##...", "#...."};
    private static final String[] DOTS_ICON = {"##", "##", "..", "##", "##", "..", "##", "##"};

    private boolean hasWorldKits() {
        return !installedKits.isEmpty();
    }

    private void showInstalledTab(InstalledTab t) {
        if (installedTab == t) return;
        installedTab = t;
        installedFilter = InstalledFilter.ALL;
        page.set(0);
        transition();
    }

    /** Pinned bar: the installed sub-tabs on the left, that tab's main actions on the right. */
    private void drawInstalledTabs(int left, int y, int cw) {
        if (installedTab == InstalledTab.WORLD && !hasWorldKits()) installedTab = InstalledTab.KITS;
        int barBottom = y + BAR_H - 4;
        BedrockUI.rect(left, barBottom, cw, 1, BedrockUI.PANEL_LIGHT);

        int x = left;
        int[] activeSpan = null;
        Object[][] tabs = {
                {InstalledTab.MAPS, "Maps", installedMaps.size(), new ItemStack(Items.FILLED_MAP)},
                {InstalledTab.KITS, "Kit Library", libraryKits.size(), new ItemStack(Items.DIAMOND_SWORD)},
                {InstalledTab.WORLD, "World Kits", InstalledKits.totalKits(installedKits), new ItemStack(Blocks.GRASS)},
                {InstalledTab.ADDONS, "Hero Addons", installedAddons.size(), new ItemStack(Items.NETHER_STAR)},
        };
        for (Object[] t : tabs) {
            final InstalledTab id = (InstalledTab) t[0];
            if (id == InstalledTab.WORLD && !hasWorldKits()) continue;
            String label = (String) t[1];
            int count = (Integer) t[2];
            String badge = String.valueOf(count);
            int bw = fontRenderer.getStringWidth(badge) + 8;
            int tw = 8 + 16 + 5 + fontRenderer.getStringWidth(label) + 6 + bw + 8;
            boolean active = installedTab == id;
            boolean hov = !active && hover(x, y, tw, barBottom - y);
            float h = anim("itab:" + id, hov, 10f);
            if (h > 0.01f) BedrockUI.rect(x, y, tw, barBottom - y, BedrockUI.fade(0x22FFFFFF, h));
            BedrockUI.item((ItemStack) t[3], x + 8, y + 3, 1f);
            if (!active) BedrockUI.rect(x + 8, y + 3, 16, 16, 0x66313233); // dim the icon of inactive tabs
            int tx = x + 8 + 16 + 5;
            fontRenderer.drawString(label, tx, y + 7, active ? 0xFFFFFFFF : BedrockUI.TEXT_DIM, active);
            int bx = tx + fontRenderer.getStringWidth(label) + 6;
            BedrockUI.rect(bx, y + 5, bw, 11, active ? BedrockUI.GREEN : 0xFF4A4B4D);
            fontRenderer.drawString(badge, bx + 4, y + 7, 0xFFFFFFFF, false);
            if (active) activeSpan = new int[]{x, tw};
            if (!active) {
                cue(UiSounds.Cue.TAB);
                hit(x, y, tw, barBottom - y, () -> showInstalledTab(id));
            }
            x += tw + 2;
        }
        // Underline glides to the active tab.
        if (activeSpan != null) {
            if (tabLineX < 0) { tabLineX = activeSpan[0]; tabLineW = activeSpan[1]; }
            tabLineX += (activeSpan[0] - tabLineX) * 0.25f;
            tabLineW += (activeSpan[1] - tabLineW) * 0.25f;
            BedrockUI.rectF(tabLineX, barBottom - 2, tabLineX + tabLineW, barBottom + 1, BedrockUI.GREEN_LIGHT);
        }

        // Actions for this tab, right-aligned.
        int ax = left + cw;
        if (installedTab == InstalledTab.MAPS) {
            int w = fontRenderer.getStringWidth("Upload Map") + 20;
            ax -= w;
            button(ax, y + 1, w, 20, "Upload Map", Style.PRIMARY, true, this::uploadMap);
            if (hover(ax, y + 1, w, 20)) setTooltip("Upload a map", "§7Pick a world folder or a .zip from your computer");
        } else if (installedTab == InstalledTab.KITS) {
            int nw = fontRenderer.getStringWidth("+ New Kit") + 20;
            ax -= nw;
            button(ax, y + 1, nw, 20, "+ New Kit", Style.PRIMARY, true, () -> mc.displayGuiScreen(GuiKitEditor.create(this)));
            int uw = fontRenderer.getStringWidth("Upload") + 20;
            ax -= uw + 6;
            button(ax, y + 1, uw, 20, "Upload", Style.DARK, true, this::uploadKits);
            if (hover(ax, y + 1, uw, 20)) setTooltip("Upload kits", "§7Pick kit files (.png kit cards or .dat) from your computer");
        } else if (installedTab == InstalledTab.ADDONS) {
            int w = fontRenderer.getStringWidth("Open Folder") + 20;
            ax -= w;
            button(ax, y + 1, w, 20, "Open Folder", Style.DARK, true, () -> SaveFileDialog.reveal(com.matoon.herosmp.client.store.HeroAddons.dir()));
            if (hover(ax, y + 1, w, 20)) setTooltip("LucraftCore's addon packs folder", "§7Drop addon pack .zip files here too");
        }
    }

    /** "All / Enabled / Disabled" chips plus a short summary on the right. Returns the y below. */
    private int drawFilterRow(int left, int y, int cw, int all, int enabled, String summary) {
        int x = left;
        Object[][] chips = {
                {InstalledFilter.ALL, "All", all},
                {InstalledFilter.ENABLED, "Enabled", enabled},
                {InstalledFilter.DISABLED, "Disabled", all - enabled},
        };
        for (Object[] c : chips) {
            final InstalledFilter f = (InstalledFilter) c[0];
            String label = c[1] + "  " + c[2];
            int w = fontRenderer.getStringWidth(label) + 16;
            boolean active = installedFilter == f;
            boolean hov = !active && hover(x, y, w, 16);
            BedrockUI.rect(x, y, w, 16, active ? BedrockUI.GREEN_LIGHT : hov ? 0xFF6A6B6D : BedrockUI.OUTLINE);
            BedrockUI.rect(x + 1, y + 1, w - 2, 14, active ? 0xFF2F5A22 : hov ? 0xFF3B3C3E : 0xFF262728);
            fontRenderer.drawString(label, x + 8, y + 4, active ? 0xFFFFFFFF : BedrockUI.TEXT_DIM, false);
            if (!active) {
                cue(UiSounds.Cue.TAB);
                hit(x, y, w, 16, () -> { installedFilter = f; page.set(0); });
            }
            x += w + 4;
        }
        if (summary != null && !summary.isEmpty()) {
            String s = BedrockUI.trim(fontRenderer, summary, left + cw - x - 10);
            fontRenderer.drawString(s, left + cw - fontRenderer.getStringWidth(s), y + 4, BedrockUI.TEXT_MUTED, false);
        }
        return y + 26;
    }

    private boolean passesFilter(boolean enabled) {
        return installedFilter == InstalledFilter.ALL || (installedFilter == InstalledFilter.ENABLED) == enabled;
    }

    private int cardCols(int cw) {
        return Math.max(1, (cw + CARD_GAP) / (CARD_MIN_W + CARD_GAP));
    }

    private int cardW(int cw) {
        int n = cardCols(cw);
        return (cw - (n - 1) * CARD_GAP) / n;
    }

    private int drawInstalledMaps(int left, int y, int cw) {
        if (installedMaps.isEmpty()) {
            drawInstalledEmpty(y, new ItemStack(Items.FILLED_MAP), "No maps installed",
                    "Download maps from Discover, or upload a world with Upload Map.", "Browse Maps", StorePack.PackType.MAP);
            return y + 140;
        }
        int enabled = installedMaps.size() - disabledLower.size();
        y = drawFilterRow(left, y, cw, installedMaps.size(), enabled,
                InstalledPacks.formatBytes(installedTotal) + " on disk  ·  disabled maps are never picked for matches");

        List<String> list = new ArrayList<String>();
        for (String n : installedMaps) if (passesFilter(!disabledLower.contains(n.toLowerCase(Locale.ROOT)))) list.add(n);
        if (list.isEmpty()) return drawFilteredEmpty(left, y, cw, "maps");

        int cols = cardCols(cw), w = cardW(cw);
        int artH = w * 9 / 16, h = artH + 40 + ACTION_H + 8;
        for (int i = 0; i < list.size(); i++) {
            int cx = left + (i % cols) * (w + CARD_GAP);
            int cy = y + (i / cols) * (h + CARD_GAP);
            drawMapCard(list.get(i), cx, cy, w, h, artH);
        }
        return y + ((list.size() + cols - 1) / cols) * (h + CARD_GAP);
    }

    private void drawMapCard(final String name, int x, int y, int w, int h, int artH) {
        final StorePack pack = packForInstalled(name);
        final boolean enabled = !disabledLower.contains(name.toLowerCase(Locale.ROOT));
        boolean hov = hover(x, y, w, h);
        float lift = BedrockUI.easeOut(anim("mapcard:" + name, hov, 8f));
        int dy = y - Math.round(lift * 2);
        cardFrame(x, dy, w, h, lift, enabled ? BedrockUI.GREEN_LIGHT : 0xFF8A8B8D);

        // Artwork (click for the full page).
        PackArt.draw(pack, true, x + 1, dy + 1, w - 2, artH, hover(x, dy, w, artH));
        if (!enabled) BedrockUI.rect(x + 1, dy + 1, w - 2, artH, 0xA0000000);
        BedrockUI.vGradient(x + 1, dy + 1 + artH - 20, w - 2, 20, 0x00000000, 0x90000000);
        statusPill(x + 5, dy + 5, enabled, enabled ? "IN ROTATION" : "DISABLED");
        hit(x, y, w, artH, () -> openDetail(pack));
        if (hover(x, dy, w, artH)) setTooltip(name, "§7Click for details");

        // Name + switch.
        int iy = dy + 1 + artH;
        BedrockUI.rect(x + 1, iy, w - 2, 1, BedrockUI.OUTLINE);
        int togX = x + w - 8 - TOGGLE_W;
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, name, togX - x - 16), x + 8, iy + 8,
                enabled ? BedrockUI.TEXT : BedrockUI.TEXT_DIM, enabled);
        Long size = installedSizes.get(name);
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, InstalledPacks.formatBytes(size == null ? 0 : size)
                + "  ·  Hunger Games map", togX - x - 16), x + 8, iy + 21, BedrockUI.TEXT_MUTED, false);
        toggle(togX, iy + 9, enabled, true, () -> toggleMap(name));
        if (hover(togX, iy + 9, TOGGLE_W, TOGGLE_H)) {
            setTooltip(enabled ? "In the match rotation" : "Out of the rotation", "§7Click to " + (enabled ? "disable" : "enable"));
        }

        // Actions: Edit (with the visual editor on its dots), Playtest, more.
        int ay = y + h - ACTION_H - 6;
        int iconW = 24, editW = w - 16 - 2 * (iconW + 4);
        splitButton(x + 8, ay, editW, ACTION_H, "Edit", Style.PRIMARY, () -> editMap(name),
                (ar, ab) -> EditMenu.open(this, name, ar, ab));
        if (hover(x + 8, ay, editW - 18, ACTION_H)) setTooltip("Edit in the world", "§7The dots open the Visual Editor");
        int px = x + 8 + editW + 4;
        iconButton(px, ay, iconW, ACTION_H, PLAY_ICON, Style.DARK, () -> Playtest.map(this, name));
        if (hover(px, ay, iconW, ACTION_H)) setTooltip("Playtest", "§7Start a solo Hunger Games match on this map");
        final int mxBtn = px + iconW + 4;
        final int ab = ay + ACTION_H;
        iconButton(mxBtn, ay, iconW, ACTION_H, DOTS_ICON, Style.DARK, () -> openMenu(mxBtn + iconW, ab,
                new String[]{"View details", "Delete map"},
                new Runnable[]{() -> openDetail(pack), () -> deleteMap(name)}));
        if (hover(mxBtn, ay, iconW, ACTION_H)) setTooltip("More");
    }

    private int drawInstalledKits(int left, int y, int cw) {
        if (libraryKits.isEmpty()) {
            drawInstalledEmpty(y, new ItemStack(Items.DIAMOND_SWORD), "Your Kit Library is empty",
                    hasWorldKits() ? "Make one with + New Kit, or edit a kit under World Kits to move it here."
                            : "Make one with + New Kit, or install a kit pack from Discover.",
                    "Browse Kits", StorePack.PackType.KIT);
            return y + 140;
        }
        int enabled = 0;
        for (InstalledKits.Kit k : libraryKits) if (k.enabled) enabled++;
        y = drawFilterRow(left, y, cw, libraryKits.size(), enabled, "Shared kits: pick which worlds can use each one");
        List<InstalledKits.Kit> list = new ArrayList<InstalledKits.Kit>();
        for (InstalledKits.Kit k : libraryKits) if (passesFilter(k.enabled)) list.add(k);
        if (list.isEmpty()) return drawFilteredEmpty(left, y, cw, "kits");
        return drawKitGrid(list, null, false, left, y, cw);
    }

    /** Older kits that live inside one world's save, grouped by world. */
    private int drawWorldKits(int left, int y, int cw) {
        y = sectionNote(left, y, cw, "These kits belong to a single world. Edit one to move it into your Kit Library.");
        for (InstalledKits.WorldKits world : installedKits) {
            BedrockUI.rect(left, y, cw, 22, BedrockUI.OUTLINE);
            BedrockUI.rect(left + 1, y + 1, cw - 2, 20, BedrockUI.SURFACE);
            BedrockUI.item(new ItemStack(Blocks.GRASS), left + 4, y + 3, 1f);
            fontRenderer.drawString(world.worldName, left + 24, y + 7, BedrockUI.TEXT, true);
            String meta = world.dir.getName() + "  ·  " + world.kits.size() + (world.kits.size() == 1 ? " kit" : " kits");
            fontRenderer.drawString(meta, left + 30 + fontRenderer.getStringWidth(world.worldName), y + 7, BedrockUI.TEXT_MUTED, false);
            if (world.open) {
                String warn = "Open now: some changes wait until you leave";
                fontRenderer.drawString(warn, left + cw - 6 - fontRenderer.getStringWidth(warn), y + 7, 0xFFE0A040, false);
            }
            y += 30;
            y = drawKitGrid(world.kits, world, world.open, left, y, cw) + 6;
        }
        return y;
    }

    private int sectionNote(int left, int y, int cw, String text) {
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, text, cw), left, y + 2, BedrockUI.TEXT_MUTED, false);
        return y + 18;
    }

    private int drawKitGrid(List<InstalledKits.Kit> kits, InstalledKits.WorldKits world, boolean locked, int left, int y, int cw) {
        int cols = cardCols(cw), w = cardW(cw);
        int artH = 84, h = artH + 40 + ACTION_H + 8;
        for (int i = 0; i < kits.size(); i++) {
            int cx = left + (i % cols) * (w + CARD_GAP);
            int cy = y + (i / cols) * (h + CARD_GAP);
            drawKitCard(kits.get(i), world, locked, cx, cy, w, h, artH);
        }
        return y + ((kits.size() + cols - 1) / cols) * (h + CARD_GAP);
    }

    private void drawKitCard(final InstalledKits.Kit kit, final InstalledKits.WorldKits world, final boolean locked,
                             int x, int y, int w, int h, int artH) {
        String id = (world != null ? world.dir.getName() + "/" : "") + kit.key;
        boolean hov = hover(x, y, w, h);
        float lift = BedrockUI.easeOut(anim("kitcard:" + id, hov, 8f));
        int dy = y - Math.round(lift * 2);
        cardFrame(x, dy, w, h, lift, kit.enabled ? BedrockUI.GREEN_LIGHT : 0xFF8A8B8D);

        // The kit's figure on a dark stage; swings while hovered.
        BedrockUI.vGradient(x + 1, dy + 1, w - 2, artH, 0xFF23243A, 0xFF121320);
        try {
            KitAvatar.draw(KitAvatar.of(kit.tag), "kit:" + id, x + 1, dy + 1, w - 2, artH, hover(x, dy, w, artH));
        } catch (RuntimeException e) {
            if (!kit.icon.isEmpty()) BedrockUI.item(kit.icon, x + w / 2f - 16, dy + artH / 2f - 16, 2f);
        }
        if (!kit.enabled) BedrockUI.rect(x + 1, dy + 1, w - 2, artH, 0xA0000000);
        statusPill(x + 5, dy + 5, kit.enabled, kit.enabled ? "ENABLED" : "DISABLED");
        String power = powerName(kit);
        if (!power.isEmpty()) {
            String pn = BedrockUI.trim(fontRenderer, power.toUpperCase(), w - 20);
            int pw = BedrockUI.chipWidth(fontRenderer, pn);
            BedrockUI.chip(fontRenderer, x + w - 5 - pw, dy + artH - 14, pn, 0xFF7A3FC0, BedrockUI.TEXT);
        }

        final List<com.matoon.herosmp.npc.kit.KitRequirements.Requirement> missing = missingFor(kit);
        if (!missing.isEmpty()) {
            int mw = BedrockUI.chipWidth(fontRenderer, "NEEDS MODS");
            int mx0 = x + w - 5 - mw;
            BedrockUI.chip(fontRenderer, mx0, dy + 5, "NEEDS MODS", 0xFFC33636, BedrockUI.TEXT);
            hit(mx0, dy + 5, mw, 12, () -> MissingModsOverlay.show(this, kit.name(), missing));
            if (hover(mx0, dy + 5, mw, 12)) setTooltip("Missing: " + com.matoon.herosmp.npc.kit.KitRequirements.names(missing), "§7Click to see what to install");
        }

        int iy = dy + 1 + artH;
        BedrockUI.rect(x + 1, iy, w - 2, 1, BedrockUI.OUTLINE);
        int togX = x + w - 8 - TOGGLE_W;
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, kit.name(), togX - x - 16), x + 8, iy + 8,
                kit.enabled ? BedrockUI.TEXT : BedrockUI.TEXT_DIM, kit.enabled);
        String where = world != null ? world.worldName : InstalledKits.describeWorlds(kit.worlds, knownWorlds);
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, kit.itemCount + " items  ·  " + where, togX - x - 16),
                x + 8, iy + 21, BedrockUI.TEXT_MUTED, false);
        toggle(togX, iy + 9, kit.enabled, !locked, () -> toggleKit(kit, world));
        if (hover(togX, iy + 9, TOGGLE_W, TOGGLE_H)) {
            if (locked) setTooltip("Leave " + world.worldName + " to change this");
            else setTooltip(kit.enabled ? "Players can pick it" : "Hidden from players", "§7Click to " + (kit.enabled ? "disable" : "enable"));
        }

        int ay = y + h - ACTION_H - 6;
        int iconW = 24, editW = w - 16 - 2 * (iconW + 4);
        button(x + 8, ay, editW, ACTION_H, "Edit", Style.PRIMARY, true,
                () -> mc.displayGuiScreen(GuiKitEditor.edit(this, kit, world != null ? world.dir : null)));
        if (world != null && hover(x + 8, ay, editW, ACTION_H)) setTooltip("Edit", "§7Saving moves this kit into your Kit Library");
        int px = x + 8 + editW + 4;
        iconButton(px, ay, iconW, ACTION_H, PLAY_ICON, Style.DARK, () -> MissingModsOverlay.guard(this, kit.name(), kit.tag,
                "Playtest anyway", () -> Playtest.kit(this, kit.tag, kit.name())));
        if (hover(px, ay, iconW, ACTION_H)) setTooltip("Playtest", "§7Try the kit on test dummies, then in a solo PVP match");
        final int mxBtn = px + iconW + 4;
        final int ab = ay + ACTION_H;
        iconButton(mxBtn, ay, iconW, ACTION_H, DOTS_ICON, Style.DARK, () -> {
            List<String> labels = new ArrayList<String>();
            List<Runnable> actions = new ArrayList<Runnable>();
            if (!missing.isEmpty()) { labels.add("Missing mods..."); actions.add(() -> MissingModsOverlay.show(this, kit.name(), missing)); }
            labels.add("Download kit file");
            actions.add(() -> downloadKit(kit));
            if (!locked) { labels.add("Delete kit"); actions.add(() -> deleteKit(kit, world)); }
            openMenu(mxBtn + iconW, ab, labels.toArray(new String[0]), actions.toArray(new Runnable[0]));
        });
        if (hover(mxBtn, ay, iconW, ACTION_H)) setTooltip("More", "§7Download, delete");
    }

    private static String powerName(InstalledKits.Kit kit) {
        if (!kit.tag.hasKey("Power", 10)) return "";
        ItemStack st = new ItemStack(kit.tag.getCompoundTag("Power"));
        return com.matoon.herosmp.integration.LucraftInjectionEntry.isValidInjection(st)
                ? com.matoon.herosmp.integration.LucraftInjectionEntry.getDisplayName(st) : "";
    }

    /** Card border: lifts and glows while hovered; a thin coloured top edge shows the status. */
    private void cardFrame(int x, int y, int w, int h, float lift, int statusColor) {
        if (lift > 0.02f) {
            BedrockUI.glow(x, y, w, h, 0xFFFFFF, lift * 0.8f);
            BedrockUI.rect(x + 2, y + h, w, Math.round(lift * 2) + 1, BedrockUI.fade(0x60000000, lift));
        }
        BedrockUI.rect(x, y, w, h, lift > 0.02f ? BedrockUI.fade(0xFFFFFFFF, 0.25f + 0.5f * lift) : BedrockUI.OUTLINE);
        BedrockUI.rect(x + 1, y + 1, w - 2, h - 2, BedrockUI.PANEL);
        BedrockUI.rect(x + 1, y + h - ACTION_H - 13, w - 2, 1, 0xFF3A3B3D);
        BedrockUI.rect(x + 1, y + h - ACTION_H - 12, w - 2, ACTION_H + 11, BedrockUI.SURFACE);
        BedrockUI.rect(x + 1, y + 1, w - 2, 1, statusColor);
    }

    private void statusPill(int x, int y, boolean on, String text) {
        int w = fontRenderer.getStringWidth(text) + 14;
        BedrockUI.rect(x, y, w, 12, 0xC0000000);
        BedrockUI.disc(x + 6, y + 6, 2.5f, on ? BedrockUI.GREEN_LIGHT : 0xFF8A8B8D);
        fontRenderer.drawString(text, x + 11, y + 2, on ? 0xFFFFFFFF : BedrockUI.TEXT_DIM, false);
    }

    /** Square button showing a pixel icon instead of a label. */
    private void iconButton(int x, int y, int w, int h, String[] icon, Style style, Runnable action) {
        button(x, y, w, h, null, style, true, action);
        boolean down = hover(x, y, w, h) && Mouse.isButtonDown(0);
        int sink = down ? BedrockUI.LIP : 0;
        int iw = icon[0].length(), ih = icon.length;
        BedrockUI.pixelIcon(icon, x + (w - iw) / 2f, y + (h - BedrockUI.LIP - ih) / 2f + sink, 1f, style.text, style.text);
    }

    private int drawInstalledAddons(int left, int y, int cw) {
        if (com.matoon.herosmp.client.store.HeroAddons.anyPendingRestart()) {
            BedrockUI.rect(left, y, cw, 26, 0xFFE0A040);
            BedrockUI.rect(left + 1, y + 1, cw - 2, 24, 0xFF3A2A12);
            fontRenderer.drawString("Restart Minecraft to apply your Hero Addon changes.", left + 8, y + 9, 0xFFFFD27A, false);
            int qw = fontRenderer.getStringWidth("Quit Game") + 18;
            button(left + cw - qw - 4, y + 3, qw, 20, "Quit Game", Style.GOLD, true, () -> confirm("Quit Minecraft?",
                    "Start it again from your launcher to load your Hero Addon changes.", "Quit", Style.PRIMARY, mc::shutdown));
            y += 34;
        }
        if (installedAddons.isEmpty()) {
            drawInstalledEmpty(y, new ItemStack(Items.NETHER_STAR), "No Hero Addons installed",
                    "Get LucraftCore addon packs from Discover, or drop .zip files in the folder.", "Browse Addons",
                    StorePack.PackType.ADDON);
            return y + 140;
        }
        int cols = cardCols(cw), w = cardW(cw);
        int artH = 64, h = artH + 40 + ACTION_H + 8;
        for (int i = 0; i < installedAddons.size(); i++) {
            int cx = left + (i % cols) * (w + CARD_GAP);
            int cy = y + (i / cols) * (h + CARD_GAP);
            drawAddonCard(installedAddons.get(i), cx, cy, w, h, artH);
        }
        return y + ((installedAddons.size() + cols - 1) / cols) * (h + CARD_GAP);
    }

    private void drawAddonCard(final com.matoon.herosmp.client.store.HeroAddons.Addon a, int x, int y, int w, int h, int artH) {
        boolean hov = hover(x, y, w, h);
        float lift = BedrockUI.easeOut(anim("addoncard:" + a.file.getName(), hov, 8f));
        int dy = y - Math.round(lift * 2);
        boolean pending = !a.loaded || com.matoon.herosmp.client.store.HeroAddons.needsRestart(a.file);
        cardFrame(x, dy, w, h, lift, pending ? 0xFFE0A040 : BedrockUI.GREEN_LIGHT);

        // The pack's own logo (read from its zip) on its own colours; its initials if it has none.
        com.matoon.herosmp.client.store.AddonMeta.Info meta = com.matoon.herosmp.client.store.AddonMeta.local(a.file);
        if (!PackArt.drawAddonIcon(meta, x + 1, dy + 1, w - 2, artH, hover(x, dy, w, artH))) {
            PackArt.addonMonogram(a.name, x + 1, dy + 1, w - 2, artH);
        }
        statusPill(x + 5, dy + 5, !pending, pending ? "RESTART TO LOAD" : "LOADED");

        int iy = dy + 1 + artH;
        BedrockUI.rect(x + 1, iy, w - 2, 1, BedrockUI.OUTLINE);
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, a.name, w - 16), x + 8, iy + 8, BedrockUI.TEXT, true);
        String sub = (a.author.isEmpty() ? "" : "by " + a.author + "  ·  ") + InstalledPacks.formatBytes(a.file.length());
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, sub, w - 16), x + 8, iy + 21, BedrockUI.TEXT_MUTED, false);
        if (hover(x, dy, w, artH) && !a.description.isEmpty()) setTooltip(fontRenderer.listFormattedStringToWidth(a.description, 200));

        int ay = y + h - ACTION_H - 6;
        int iconW = 24, mainW = w - 16 - iconW - 4;
        button(x + 8, ay, mainW, ACTION_H, "Show File", Style.DARK, true, () -> SaveFileDialog.reveal(a.file));
        final int mxBtn = x + 8 + mainW + 4;
        final int ab = ay + ACTION_H;
        iconButton(mxBtn, ay, iconW, ACTION_H, DOTS_ICON, Style.DARK, () -> openMenu(mxBtn + iconW, ab,
                new String[]{"Remove addon"}, new Runnable[]{() -> confirm("Remove " + a.name + "?",
                        "Deletes the addon pack. Its heroes and powers go away after you restart Minecraft, and kits that use them won't load.",
                        "Remove", Style.DANGER, () -> {
                            boolean now = com.matoon.herosmp.client.store.HeroAddons.remove(a.file);
                            refreshInstalled();
                            toast(now ? "Removed " + a.name + " (restart to finish)" : "Removed " + a.name + " (deleted when the game restarts)");
                        })}));
        if (hover(mxBtn, ay, iconW, ACTION_H)) setTooltip("More");
    }

    private int drawFilteredEmpty(int left, int y, int cw, String what) {
        String s = installedFilter == InstalledFilter.ENABLED ? "No enabled " + what + "." : "No disabled " + what + ".";
        fontRenderer.drawString(s, left + (cw - fontRenderer.getStringWidth(s)) / 2, y + 30, BedrockUI.TEXT_DIM, false);
        return y + 70;
    }

    private void drawInstalledEmpty(int y, ItemStack icon, String a, String b, String browse, final StorePack.PackType type) {
        int cx = width / 2;
        float bob = (float) Math.sin(System.currentTimeMillis() / 600.0) * 2f;
        KitAvatar.glow(cx, y + 30, 38, 0.45f);
        BedrockUI.item(icon, cx - 24, y + 6 + bob, 3f);
        fontRenderer.drawString(a, cx - fontRenderer.getStringWidth(a) / 2, y + 62, BedrockUI.TEXT, true);
        fontRenderer.drawString(b, cx - fontRenderer.getStringWidth(b) / 2, y + 76, BedrockUI.TEXT_DIM, false);
        button(cx - 60, y + 94, 120, 24, browse, Style.PRIMARY, true, () -> {
            switchTab(Tab.DISCOVER);
            openViewAll(type);
        });
    }
}
