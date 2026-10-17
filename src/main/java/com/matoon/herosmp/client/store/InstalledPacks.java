package com.matoon.herosmp.client.store;

import net.minecraft.client.Minecraft;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Client-side view of installed content on disk. Hunger Games maps live globally in
 * {@code <gameDir>/herosmp_hg_maps/} (one folder per map). Kits are per-world saved-data and are not
 * enumerable globally, so they are not listed here.
 */
public final class InstalledPacks {

    private InstalledPacks() {}

    public static File mapsDir() {
        return new File(Minecraft.getMinecraft().gameDir, "herosmp_hg_maps");
    }

    public static File mapDir(String name) {
        return new File(mapsDir(), name);
    }

    /** Names of installed maps: folders containing a {@code region/} dir or {@code level.dat}. */
    public static List<String> listInstalledMaps() {
        File[] subs = mapsDir().listFiles(File::isDirectory);
        if (subs == null) return Collections.emptyList();
        List<String> names = new ArrayList<String>();
        for (File d : subs) {
            if (isMapDir(d) && !d.getName().startsWith(".")) names.add(d.getName()); // dot folders: staging / backups
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public static boolean isMapInstalled(String name) {
        return name != null && !name.isEmpty() && isMapDir(mapDir(name));
    }

    private static boolean isMapDir(File d) {
        return d.isDirectory() && (new File(d, "region").isDirectory() || new File(d, "level.dat").isFile());
    }

    /** Whether the map is in the match rotation (see HungerGamesMapManager.DISABLED_MARKER). */
    public static boolean isMapEnabled(String name) {
        return com.matoon.herosmp.hungergames.map.HungerGamesMapManager.isMapEnabled(mapDir(name));
    }

    /** Adds/removes the disabled marker. Returns the new state. */
    public static boolean setMapEnabled(String name, boolean enabled) {
        File marker = new File(mapDir(name), com.matoon.herosmp.hungergames.map.HungerGamesMapManager.DISABLED_MARKER);
        try {
            if (enabled) marker.delete();
            else marker.createNewFile();
        } catch (java.io.IOException e) {
            System.err.println("[Beyonder] Couldn't toggle map " + name + ": " + e.getMessage());
        }
        return isMapEnabled(name);
    }

    /** Total bytes on disk for an installed map. */
    public static long sizeOf(String name) {
        return sizeOf(mapDir(name));
    }

    private static long sizeOf(File f) {
        if (f.isFile()) return f.length();
        long total = 0;
        File[] children = f.listFiles();
        if (children != null) for (File c : children) total += sizeOf(c);
        return total;
    }

    /**
     * Recursively deletes an installed map folder. Rejects names that could escape the maps
     * directory. Returns true if the folder is gone afterwards.
     */
    public static boolean deleteMap(String name) {
        if (name == null || name.isEmpty() || name.contains("/") || name.contains("\\") || name.contains("..")) {
            return false;
        }
        File target = mapDir(name);
        if (target.getParentFile() == null || !target.getParentFile().equals(mapsDir())) return false;
        deleteRecursive(target);
        PackThumbnails.invalidateInstalledMap(name);
        return !target.exists();
    }

    static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                if (c.isDirectory()) deleteRecursive(c);
                else c.delete();
            }
        }
        f.delete();
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
