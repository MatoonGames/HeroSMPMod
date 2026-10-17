package com.matoon.herosmp.client.store;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lucraft.mods.lucraftcore.addonpacks.AddonPackHandler;
import lucraft.mods.lucraftcore.addonpacks.AddonPackInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Hero Addons on this computer: the LucraftCore addon packs in its addon packs folder.
 * <p>
 * LucraftCore reads addon packs once, at startup, so anything installed or removed here takes
 * effect after a restart; the store says so. A loaded pack's zip is held open by the game, so on
 * Windows deleting it can fail: then it is queued in {@code beyonder_addons_remove.txt} and removed
 * when the game next starts, before LucraftCore loads it (HeroSMP.construct).
 */
@SideOnly(Side.CLIENT)
public final class HeroAddons {

    private HeroAddons() {}

    /** Changed this session, so they need a restart: file names installed / removed. */
    private static final Set<String> INSTALLED_NOW = new HashSet<String>();
    private static final Set<String> REMOVED_NOW = new HashSet<String>();

    /** One addon pack in the folder. */
    public static final class Addon {
        public final File file;
        public final String name, author, description;
        /** Loaded by LucraftCore this session (false = installed since startup, restart to load). */
        public final boolean loaded;
        public final ResourceLocation icon;

        Addon(File file, String name, String author, String description, boolean loaded, ResourceLocation icon) {
            this.file = file;
            this.name = name;
            this.author = author == null ? "" : author;
            this.description = description == null ? "" : description;
            this.loaded = loaded;
            this.icon = icon;
        }
    }

    public static File dir() {
        File d;
        try {
            d = AddonPackHandler.getAddonPacksDir();
        } catch (Throwable t) {
            d = new File("addonpacks");
        }
        if (!d.isAbsolute()) d = new File(Minecraft.getMinecraft().gameDir, d.getPath());
        return d;
    }

    private static boolean isPack(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        return f.isDirectory() ? new File(f, "addonpack.mcmeta").isFile() : (n.endsWith(".zip") || n.endsWith(".jar"));
    }

    private static List<Addon> cached;
    private static long cachedAt;

    /** Addon packs in the folder (minus ones removed this session), by name. Cached for a second. */
    public static List<Addon> list() {
        long now = System.currentTimeMillis();
        if (cached != null && now - cachedAt < 1000) return cached;
        cached = scan();
        cachedAt = now;
        return cached;
    }

    private static void invalidate() {
        cached = null;
    }

    private static List<Addon> scan() {
        File[] files = dir().listFiles();
        if (files == null) return Collections.emptyList();
        List<Addon> out = new ArrayList<Addon>();
        for (File f : files) {
            if (!isPack(f) || REMOVED_NOW.contains(f.getName())) continue;
            AddonPackInfo info = loadedInfo(f);
            if (info != null) {
                ResourceLocation icon = null;
                try {
                    icon = info.getIcon();
                } catch (Throwable ignored) {
                    // No icon registered.
                }
                out.add(new Addon(f, info.getName(), info.getAuthor(), info.getDescription(), true, icon));
            } else {
                String[] meta = readMeta(f);
                out.add(new Addon(f, meta[0] != null ? meta[0] : StoreCatalog.addonTitle(f.getName()), meta[1], meta[2], false, null));
            }
        }
        out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    private static AddonPackInfo loadedInfo(File f) {
        try {
            for (AddonPackInfo info : AddonPackHandler.ADDON_PACKS) {
                if (info.getFile() != null && info.getFile().getAbsoluteFile().equals(f.getAbsoluteFile())) return info;
            }
        } catch (Throwable ignored) {
            // LucraftCore's list unavailable.
        }
        return null;
    }

    /** name, author, description from a pack's addonpack.mcmeta (nulls when unreadable). */
    private static String[] readMeta(File f) {
        String[] out = new String[3];
        try {
            JsonObject o;
            if (f.isDirectory()) {
                try (InputStream in = Files.newInputStream(new File(f, "addonpack.mcmeta").toPath())) {
                    o = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                }
            } else {
                try (ZipFile zip = new ZipFile(f)) {
                    ZipEntry e = zip.getEntry("addonpack.mcmeta");
                    if (e == null) return out;
                    try (InputStream in = zip.getInputStream(e)) {
                        o = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    }
                }
            }
            if (o.has("name")) out[0] = o.get("name").getAsString();
            if (o.has("author")) out[1] = o.get("author").getAsString();
            if (o.has("description")) out[2] = o.get("description").getAsString();
        } catch (Exception ignored) {
            // Leave blanks.
        }
        return out;
    }

    /** Where a store addon installs: its Drive file name, made safe. */
    public static File targetFor(StorePack pack) {
        String n = pack.fileName != null && !pack.fileName.isEmpty() ? pack.fileName : pack.displayName + ".zip";
        n = n.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        String lower = n.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".zip") && !lower.endsWith(".jar")) n += ".zip";
        return new File(dir(), n);
    }

    public static boolean isInstalled(StorePack pack) {
        File t = targetFor(pack);
        if (t.exists() && !REMOVED_NOW.contains(t.getName())) return true;
        String want = norm(pack.displayName);
        for (Addon a : list()) if (norm(a.name).equals(want) || norm(StoreCatalog.addonTitle(a.file.getName())).equals(want)) return true;
        return false;
    }

    /** True if installed this session (or replaced), so it loads after a restart. */
    public static boolean needsRestart(File f) {
        return INSTALLED_NOW.contains(f.getName());
    }

    public static boolean anyPendingRestart() {
        return !INSTALLED_NOW.isEmpty() || !REMOVED_NOW.isEmpty();
    }

    static void markInstalled(File f) {
        invalidate();
        INSTALLED_NOW.add(f.getName());
        REMOVED_NOW.remove(f.getName());
    }

    /**
     * Removes an addon pack. Returns true if it was deleted now; false if the game has it open, in
     * which case it is removed when the game next starts.
     */
    public static boolean remove(File f) {
        invalidate();
        REMOVED_NOW.add(f.getName());
        INSTALLED_NOW.remove(f.getName());
        if (f.isDirectory()) InstalledPacks.deleteRecursive(f);
        else f.delete();
        if (!f.exists()) return true;
        queueRemoval(f);
        return false;
    }

    private static File pendingFile() {
        return new File(Minecraft.getMinecraft().gameDir, "beyonder_addons_remove.txt");
    }

    private static void queueRemoval(File f) {
        try {
            List<String> lines = new ArrayList<String>();
            File pf = pendingFile();
            if (pf.isFile()) lines.addAll(Files.readAllLines(pf.toPath(), StandardCharsets.UTF_8));
            if (!lines.contains(f.getAbsolutePath())) lines.add(f.getAbsolutePath());
            Files.write(pf.toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[Beyonder] Couldn't queue addon removal: " + e.getMessage());
        }
        f.deleteOnExit();
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Store addon that best matches a kit requirement (by recorded file name or pack name), or null. */
    public static StorePack storeMatch(com.matoon.herosmp.npc.kit.KitRequirements.Requirement r, List<StorePack> packs) {
        if (packs == null) return null;
        String file = norm(StoreCatalog.addonTitle(r.file));
        String name = norm(r.name);
        for (StorePack p : packs) {
            if (p.type != StorePack.PackType.ADDON) continue;
            String pf = norm(p.fileName != null ? StoreCatalog.addonTitle(p.fileName) : "");
            String pn = norm(p.displayName);
            if (pn.isEmpty()) continue;
            if ((!file.isEmpty() && (file.equals(pf) || file.equals(pn))) || (!name.isEmpty() && (name.equals(pn) || pn.contains(name) || name.contains(pn)))) {
                return p;
            }
        }
        return null;
    }
}
