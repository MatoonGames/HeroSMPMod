package com.matoon.herosmp.client.store;

import com.matoon.herosmp.client.store.DriveClient.DriveEntry;
import com.matoon.herosmp.npc.kit.KitLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Downloads and installs a {@link StorePack} on a background daemon thread. Progress is reported
 * through a shared {@link Progress} object that the download GUI polls each client tick.
 *
 *  - MAP: recursively downloads the pack's Drive folder (only {@code level.dat}, {@code region/**},
 *    and {@code herosmp_config.dat} — everything the HG map system reads) into
 *    {@code <gameDir>/herosmp_hg_maps/<MapName>/}. Auto-discovered by HungerGamesMapManager.
 *  - KIT: downloads the pack's {@code .dat} (the shared kit file format, see {@link KitLibrary})
 *    and writes each of its kits into the global kit library {@code beyonder_kits/}, available in
 *    the worlds the player picked (or all worlds).
 */
public final class PackInstaller {

    private PackInstaller() {}

    public enum State { RUNNING, SUCCESS, ERROR, CANCELLED }

    /** Thread-safe status shared between the installer thread and the GUI. */
    public static final class Progress {
        private volatile State       state       = State.RUNNING;
        private volatile int         totalFiles  = 0;
        private final    AtomicInteger filesDone = new AtomicInteger(0);
        private final    AtomicLong    bytesDone = new AtomicLong(0);
        private volatile String      currentFile = "";
        private volatile String      error       = null;
        private volatile boolean     cancelRequested = false;
        /** Kits written by a kit install (for the requirements check on the success screen). */
        private volatile List<net.minecraft.nbt.NBTTagCompound> kitTags = java.util.Collections.emptyList();
        /** Where an addon install put the pack. */
        private volatile File addonFile;

        public State   state()       { return state; }
        public int     totalFiles()  { return totalFiles; }
        public int     filesDone()   { return filesDone.get(); }
        public long    bytesDone()   { return bytesDone.get(); }
        public String  currentFile() { return currentFile; }
        public String  error()       { return error; }
        public List<net.minecraft.nbt.NBTTagCompound> kitTags() { return kitTags; }
        public File    addonFile()   { return addonFile; }

        public void requestCancel()  { cancelRequested = true; }
        public boolean isCancelled() { return cancelRequested; }

        /** 0..1 completion fraction, by file count. Indeterminate (0) until the total is known. */
        public float fraction() {
            int t = totalFiles;
            return t <= 0 ? 0f : Math.min(1f, filesDone.get() / (float) t);
        }
    }

    /** Starts the install on a daemon thread and returns the live Progress to poll. */
    public static Progress start(final StorePack pack, final List<String> kitWorlds) {
        final Progress p = new Progress();
        Thread t = new Thread(new Runnable() {
            @Override public void run() { PackInstaller.run(pack, kitWorlds, p); }
        }, "HeroSMP pack installer");
        t.setDaemon(true);
        t.start();
        return p;
    }

    private static void run(StorePack pack, List<String> kitWorlds, Progress p) {
        try {
            switch (pack.type) {
                case MAP:
                    if (pack.localSource != null) importMap(pack, p);
                    else installMap(pack, p);
                    break;
                case KIT: installKit(pack, kitWorlds, p); break;
                case ADDON: installAddon(pack, p); break;
                default: throw new IOException("Unsupported pack type: " + pack.type);
            }
            if (!p.isCancelled()) p.state = State.SUCCESS;
            else p.state = State.CANCELLED;
        } catch (Exception e) {
            if (p.isCancelled()) {
                p.state = State.CANCELLED;
            } else {
                p.error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                p.state = State.ERROR;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Map install
    // -------------------------------------------------------------------------

    /** Root-level files kept from a map's Drive folder (besides the region/ folder). */
    private static final String[] MAP_ROOT_FILES = {
            "level.dat", "herosmp_config.dat", "icon.png", "thumbnail.png", "description.txt"
    };

    private static void installMap(StorePack pack, Progress p) throws IOException {
        File gameDir = Minecraft.getMinecraft().gameDir;
        String safeName = sanitize(pack.displayName);
        if (safeName.isEmpty()) throw new IOException("Invalid map name.");
        File mapDir = new File(new File(gameDir, "herosmp_hg_maps"), safeName);

        // Download into a staging folder outside herosmp_hg_maps so a partial download is never
        // picked up as a map, and a reinstall replaces the old files cleanly.
        File staging = new File(new File(gameDir, "herosmp_store_tmp"), safeName);
        InstalledPacks.deleteRecursive(staging);

        try {
            // Pass 1: enumerate the files we want, so the progress bar is determinate.
            List<RemoteFile> files = new ArrayList<RemoteFile>();
            enumerateMap(pack.driveId, "", files, true, p);
            if (p.isCancelled()) return;
            if (files.isEmpty()) throw new IOException("Map folder is empty or unreadable on Drive.");
            p.totalFiles = files.size();

            // Pass 2: download each, preserving the relative directory structure.
            for (RemoteFile rf : files) {
                if (p.isCancelled()) return;
                p.currentFile = rf.relativePath;
                DriveClient.downloadFile(rf.driveId, new File(staging, rf.relativePath), byteSink(p));
                p.filesDone.incrementAndGet();
            }

            swapIntoPlace(staging, mapDir, safeName, p);
        } finally {
            InstalledPacks.deleteRecursive(staging);
        }
    }

    /**
     * Replaces the installed map with the finished staging folder. The old map is moved aside first
     * and only deleted once the new one is in place, so a failed swap (locked file) puts it back
     * instead of leaving no map at all.
     */
    private static void swapIntoPlace(File staging, File mapDir, String safeName, Progress p) throws IOException {
        p.currentFile = "Finishing up...";
        mapDir.getParentFile().mkdirs();
        File backup = null;
        if (mapDir.exists()) {
            backup = new File(mapDir.getParentFile(), "." + mapDir.getName() + ".old-" + System.currentTimeMillis());
            if (!mapDir.renameTo(backup)) backup = null; // can't move it aside: fall through and copy over it
        }
        try {
            if (!staging.renameTo(mapDir)) {
                java.nio.file.Path src = staging.toPath();
                try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(src)) {
                    for (java.nio.file.Path s : (Iterable<java.nio.file.Path>) walk::iterator) {
                        java.nio.file.Path d = mapDir.toPath().resolve(src.relativize(s));
                        if (java.nio.file.Files.isDirectory(s)) java.nio.file.Files.createDirectories(d);
                        else java.nio.file.Files.copy(s, d, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            if (backup != null) {
                InstalledPacks.deleteRecursive(mapDir);
                if (!backup.renameTo(mapDir)) {
                    System.err.println("[Beyonder] Couldn't restore " + mapDir.getName() + "; the old copy is in " + backup.getName());
                }
            }
            throw e;
        }
        if (backup != null) InstalledPacks.deleteRecursive(backup);
        PackThumbnails.invalidateInstalledMap(safeName);
    }

    // -------------------------------------------------------------------------
    // Map upload (from the user's computer)
    // -------------------------------------------------------------------------

    /** True if the folder is a world save (has level.dat or region/). */
    public static boolean isWorldFolder(File dir) {
        return dir.isDirectory() && (new File(dir, "level.dat").isFile() || new File(dir, "region").isDirectory());
    }

    /**
     * The world inside a chosen folder: the folder itself, or its only world sub-folder (people often
     * pick the folder that wraps the world). Null if none.
     */
    public static File findWorld(File chosen) {
        if (isWorldFolder(chosen)) return chosen;
        File[] subs = chosen.listFiles(File::isDirectory);
        File found = null;
        if (subs != null) {
            for (File s : subs) {
                if (!isWorldFolder(s)) continue;
                if (found != null) return null; // several worlds: ambiguous
                found = s;
            }
        }
        return found;
    }

    /** Copies a world folder or extracts a .zip into the maps folder (same files as store installs). */
    private static void importMap(StorePack pack, Progress p) throws IOException {
        File gameDir = Minecraft.getMinecraft().gameDir;
        String safeName = sanitize(pack.displayName);
        if (safeName.isEmpty()) throw new IOException("Invalid map name.");
        File mapDir = new File(new File(gameDir, "herosmp_hg_maps"), safeName);
        File staging = new File(new File(gameDir, "herosmp_store_tmp"), safeName);
        InstalledPacks.deleteRecursive(staging);
        File src = pack.localSource;
        try {
            if (src.isDirectory()) importFolder(src, staging, p);
            else importZip(src, staging, p);
            if (p.isCancelled()) return;
            swapIntoPlace(staging, mapDir, safeName, p);
        } finally {
            InstalledPacks.deleteRecursive(staging);
        }
    }

    private static void importFolder(File chosen, File staging, Progress p) throws IOException {
        File world = findWorld(chosen);
        if (world == null) throw new IOException("That folder isn't a Minecraft world (no level.dat or region folder).");
        List<File> files = new ArrayList<File>();
        File[] root = world.listFiles();
        if (root != null) {
            for (File f : root) {
                if (f.isFile() && isWantedRootFile(f.getName())) files.add(f);
                else if (f.isDirectory() && f.getName().equalsIgnoreCase("region")) collectFiles(f, files);
            }
        }
        if (files.isEmpty()) throw new IOException("The world folder has nothing to copy.");
        p.totalFiles = files.size();
        java.nio.file.Path base = world.toPath();
        for (File f : files) {
            if (p.isCancelled()) return;
            String rel = base.relativize(f.toPath()).toString();
            p.currentFile = rel.replace('\\', '/');
            File dest = new File(staging, rel);
            dest.getParentFile().mkdirs();
            java.nio.file.Files.copy(f.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            p.bytesDone.addAndGet(f.length());
            p.filesDone.incrementAndGet();
        }
    }

    private static void collectFiles(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File c : children) {
            if (c.isDirectory()) collectFiles(c, out);
            else out.add(c);
        }
    }

    private static void importZip(File zipFile, File staging, Progress p) throws IOException {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipFile)) {
            // The world may sit at the zip root or inside a folder: use the shallowest folder that
            // holds level.dat or region/.
            String prefix = null;
            for (java.util.zip.ZipEntry e : java.util.Collections.list(zip.entries())) {
                String n = e.getName().replace('\\', '/');
                String dir = null;
                if (n.endsWith("level.dat") && (n.equals("level.dat") || n.endsWith("/level.dat"))) dir = n.substring(0, n.length() - "level.dat".length());
                else if (n.contains("region/")) dir = n.substring(0, n.indexOf("region/"));
                if (dir != null && (dir.isEmpty() || dir.endsWith("/")) && (prefix == null || dir.length() < prefix.length())) prefix = dir;
            }
            if (prefix == null) throw new IOException("That .zip doesn't contain a Minecraft world.");

            List<java.util.zip.ZipEntry> wanted = new ArrayList<java.util.zip.ZipEntry>();
            for (java.util.zip.ZipEntry e : java.util.Collections.list(zip.entries())) {
                if (e.isDirectory()) continue;
                String n = e.getName().replace('\\', '/');
                if (!n.startsWith(prefix)) continue;
                String rel = n.substring(prefix.length());
                if (rel.contains("..") || rel.startsWith("/")) continue; // never write outside the map
                if (rel.contains("/") ? rel.toLowerCase(java.util.Locale.ROOT).startsWith("region/") : isWantedRootFile(rel)) wanted.add(e);
            }
            if (wanted.isEmpty()) throw new IOException("The world in that .zip has nothing to copy.");
            p.totalFiles = wanted.size();
            byte[] buf = new byte[32 * 1024];
            for (java.util.zip.ZipEntry e : wanted) {
                if (p.isCancelled()) return;
                String rel = e.getName().replace('\\', '/').substring(prefix.length());
                p.currentFile = rel;
                File dest = new File(staging, rel);
                dest.getParentFile().mkdirs();
                try (java.io.InputStream in = zip.getInputStream(e); java.io.FileOutputStream out = new java.io.FileOutputStream(dest)) {
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        p.bytesDone.addAndGet(n);
                    }
                }
                p.filesDone.incrementAndGet();
            }
        }
    }

    /**
     * Recursively lists the wanted map files. At the map root only {@link #MAP_ROOT_FILES} are kept
     * and only the {@code region} folder is descended into; below the root everything is kept.
     */
    private static void enumerateMap(String folderId, String relPath, List<RemoteFile> out,
                                     boolean isRoot, Progress p) throws IOException {
        if (p.isCancelled()) return;
        for (DriveEntry e : DriveClient.listFolder(folderId)) {
            if (e.isFolder) {
                if (isRoot && !e.name.equalsIgnoreCase("region")) continue;
                enumerateMap(e.id, relPath + e.name + "/", out, false, p);
            } else {
                if (isRoot && !isWantedRootFile(e.name)) continue;
                out.add(new RemoteFile(e.id, relPath + e.name));
            }
        }
    }

    private static boolean isWantedRootFile(String name) {
        for (String want : MAP_ROOT_FILES) if (want.equalsIgnoreCase(name)) return true;
        return false;
    }

    // -------------------------------------------------------------------------
    // Kit install
    // -------------------------------------------------------------------------

    private static void installKit(StorePack pack, List<String> kitWorlds, Progress p) throws IOException {
        if (kitWorlds == null) throw new IOException("No worlds selected.");
        p.totalFiles = 1;
        p.currentFile = pack.displayName + ".dat";

        File tmp = File.createTempFile("herosmp_kit_", ".dat");
        try {
            DriveClient.downloadFile(pack.driveId, tmp, byteSink(p));
            if (p.isCancelled()) return;
            byte[] bytes = KitLibrary.readBytes(tmp);
            installKitFile(bytes, kitWorlds);
            p.kitTags = KitLibrary.parse(bytes).kits;
            p.filesDone.incrementAndGet();
        } finally {
            tmp.delete();
        }
    }

    // -------------------------------------------------------------------------
    // Hero Addon install
    // -------------------------------------------------------------------------

    /**
     * Downloads a LucraftCore addon pack into the addon packs folder. It loads the next time the
     * game starts (LucraftCore reads addon packs once, at startup).
     */
    private static void installAddon(StorePack pack, Progress p) throws IOException {
        File target = HeroAddons.targetFor(pack);
        target.getParentFile().mkdirs();
        p.totalFiles = 1;
        p.currentFile = target.getName();
        File part = new File(target.getParentFile(), target.getName() + ".part");
        try {
            File cached = AddonMeta.cacheFile(pack.driveId);
            if (cached.isFile() && cached.length() > 0) {
                // Already fetched whole for its store preview.
                java.nio.file.Files.copy(cached.toPath(), part.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                p.bytesDone.addAndGet(cached.length());
            } else {
                DriveClient.downloadFile(pack.driveId, part, byteSink(p));
            }
            if (p.isCancelled()) return;
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(part)) {
                if (zip.getEntry("addonpack.mcmeta") == null) {
                    throw new IOException("This file isn't a LucraftCore addon pack (no addonpack.mcmeta).");
                }
            } catch (java.util.zip.ZipException e) {
                throw new IOException("The download isn't a valid addon pack zip.");
            }
            try {
                java.nio.file.Files.move(part.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new IOException("Couldn't replace " + target.getName() + " while the game is using it. "
                        + "Remove it from Installed, restart, then install again.");
            }
            HeroAddons.markInstalled(target);
            p.addonFile = target;
            p.filesDone.incrementAndGet();
        } finally {
            part.delete();
        }
    }

    /**
     * Writes every kit in a kit file into the {@link KitLibrary}, available in {@code worlds}
     * (empty = all worlds). Same-key library kits are replaced. Returns the kits' display names.
     */
    public static List<String> installKitFile(byte[] file, List<String> worlds) throws IOException {
        KitLibrary.Parsed parsed = KitLibrary.parse(file);
        List<String> names = new ArrayList<String>();
        for (NBTTagCompound kit : parsed.kits) {
            KitLibrary.write(kit, worlds);
            names.add(kit.getString("DisplayName"));
        }
        return names;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static DriveClient.DownloadListener byteSink(final Progress p) {
        return new DriveClient.DownloadListener() {
            @Override public void bytesTransferred(long delta) { p.bytesDone.addAndGet(delta); }
            @Override public boolean isCancelled() { return p.isCancelled(); }
        };
    }

    /** Strips path separators and traversal so a Drive name can't escape the maps folder. */
    private static String sanitize(String name) {
        String s = name.replace('\\', '_').replace('/', '_').replace("..", "_").trim();
        return s;
    }

    private static final class RemoteFile {
        final String driveId;
        final String relativePath;
        RemoteFile(String driveId, String relativePath) {
            this.driveId = driveId;
            this.relativePath = relativePath;
        }
    }
}
