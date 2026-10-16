package com.matoon.herosmp.client.store;

import com.matoon.herosmp.client.store.DriveClient.DriveEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Downloads and installs a {@link StorePack} on a background daemon thread. Progress is reported
 * through a shared {@link Progress} object that the download GUI polls each client tick.
 *
 *  - MAP: recursively downloads the pack's Drive folder (only {@code level.dat}, {@code region/**},
 *    and {@code herosmp_config.dat} — everything the HG map system reads) into
 *    {@code <gameDir>/herosmp_hg_maps/<MapName>/}. Auto-discovered by HungerGamesMapManager.
 *  - KIT: downloads the pack's {@code .dat}, then merges its kits into the chosen world's
 *    {@code data/herosmp_kits.dat} (Forge WorldSavedData format: gzip NBT wrapped in a "data"
 *    compound holding a "Kits" list). Safe because the store runs at the main menu with the
 *    world offline.
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

        public State   state()       { return state; }
        public int     totalFiles()  { return totalFiles; }
        public int     filesDone()   { return filesDone.get(); }
        public long    bytesDone()   { return bytesDone.get(); }
        public String  currentFile() { return currentFile; }
        public String  error()       { return error; }

        public void requestCancel()  { cancelRequested = true; }
        public boolean isCancelled() { return cancelRequested; }

        /** 0..1 completion fraction, by file count. Indeterminate (0) until the total is known. */
        public float fraction() {
            int t = totalFiles;
            return t <= 0 ? 0f : Math.min(1f, filesDone.get() / (float) t);
        }
    }

    /** Starts the install on a daemon thread and returns the live Progress to poll. */
    public static Progress start(final StorePack pack, final File targetWorldDir) {
        final Progress p = new Progress();
        Thread t = new Thread(new Runnable() {
            @Override public void run() { PackInstaller.run(pack, targetWorldDir, p); }
        }, "HeroSMP pack installer");
        t.setDaemon(true);
        t.start();
        return p;
    }

    private static void run(StorePack pack, File targetWorldDir, Progress p) {
        try {
            switch (pack.type) {
                case MAP: installMap(pack, p); break;
                case KIT: installKit(pack, targetWorldDir, p); break;
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

            // Swap the finished install into place.
            p.currentFile = "Finishing up...";
            InstalledPacks.deleteRecursive(mapDir);
            mapDir.getParentFile().mkdirs();
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
            PackThumbnails.invalidateInstalledMap(safeName);
        } finally {
            InstalledPacks.deleteRecursive(staging);
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

    private static void installKit(StorePack pack, File targetWorldDir, Progress p) throws IOException {
        if (targetWorldDir == null) throw new IOException("No target world selected.");
        p.totalFiles = 1;
        p.currentFile = pack.displayName + ".dat";

        File tmp = File.createTempFile("herosmp_kit_", ".dat");
        try {
            DriveClient.downloadFile(pack.driveId, tmp, byteSink(p));
            if (p.isCancelled()) return;

            // Read the pack. Accept either a bare { Kits:[...] } file or a full data-wrapped save.
            NBTTagCompound packRoot = readNbt(tmp);
            NBTTagCompound packHolder = packRoot.hasKey("data") ? packRoot.getCompoundTag("data") : packRoot;
            NBTTagList packKits = packHolder.getTagList("Kits", 10);
            if (packKits.tagCount() == 0) throw new IOException("Kit pack contained no kits.");

            // Load the world's existing kit saved-data (if any) and merge by Key.
            File kitsFile = new File(new File(targetWorldDir, "data"), "herosmp_kits.dat");
            Map<String, NBTTagCompound> byKey = new LinkedHashMap<String, NBTTagCompound>();
            if (kitsFile.isFile()) {
                NBTTagCompound wrapper = readNbt(kitsFile);
                NBTTagCompound data = wrapper.hasKey("data") ? wrapper.getCompoundTag("data") : wrapper;
                NBTTagList existing = data.getTagList("Kits", 10);
                for (int i = 0; i < existing.tagCount(); i++) {
                    NBTTagCompound kit = existing.getCompoundTagAt(i);
                    byKey.put(kit.getString("Key"), kit);
                }
            }
            for (int i = 0; i < packKits.tagCount(); i++) {
                NBTTagCompound kit = packKits.getCompoundTagAt(i);
                byKey.put(kit.getString("Key"), kit); // pack overrides same-key kits
            }

            // Rebuild and write back in Forge's WorldSavedData wrapper format.
            NBTTagList merged = new NBTTagList();
            for (NBTTagCompound kit : byKey.values()) merged.appendTag(kit);
            NBTTagCompound dataOut = new NBTTagCompound();
            dataOut.setTag("Kits", merged);
            NBTTagCompound wrapperOut = new NBTTagCompound();
            wrapperOut.setTag("data", dataOut);

            kitsFile.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(kitsFile)) {
                CompressedStreamTools.writeCompressed(wrapperOut, fos);
            }
            p.filesDone.incrementAndGet();
        } finally {
            tmp.delete();
        }
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

    /** Reads an NBT file, trying gzip first then falling back to uncompressed. */
    private static NBTTagCompound readNbt(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            return CompressedStreamTools.readCompressed(in);
        } catch (IOException gzipFail) {
            return CompressedStreamTools.read(f);
        }
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
