package com.matoon.herosmp.client.store;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cached per-pack metadata from Drive: folder listings (shared by artwork and description lookups)
 * and the optional {@code description.txt} shown on the product page.
 */
public final class PackMeta {

    private PackMeta() {}

    public static final String DESCRIPTION_FILE = "description.txt";
    private static final int MAX_DESCRIPTION_CHARS = 1200;

    private static final ConcurrentHashMap<String, List<DriveClient.DriveEntry>> LISTINGS =
            new ConcurrentHashMap<String, List<DriveClient.DriveEntry>>();
    private static final ConcurrentHashMap<String, String> DESCRIPTIONS = new ConcurrentHashMap<String, String>();
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    /** Blocking, cached folder listing. */
    public static List<DriveClient.DriveEntry> list(String folderId) throws IOException {
        List<DriveClient.DriveEntry> cached = LISTINGS.get(folderId);
        if (cached != null) return cached;
        List<DriveClient.DriveEntry> fresh = DriveClient.listFolder(folderId);
        LISTINGS.put(folderId, fresh);
        return fresh;
    }

    /**
     * Description for a store pack folder. Returns null while loading, "" when the pack has none.
     * Kicks off the fetch on first call.
     */
    public static String remoteDescription(final String folderId) {
        String d = DESCRIPTIONS.get(folderId);
        if (d != null) return d;
        if (!IN_FLIGHT.add(folderId)) return null;

        Thread t = new Thread(() -> {
            File tmp = null;
            try {
                String id = null;
                for (DriveClient.DriveEntry e : list(folderId)) {
                    if (!e.isFolder && e.name.equalsIgnoreCase(DESCRIPTION_FILE)) { id = e.id; break; }
                }
                if (id == null) { DESCRIPTIONS.put(folderId, ""); return; }
                tmp = File.createTempFile("hs_desc_", ".txt");
                DriveClient.downloadFile(id, tmp, null);
                DESCRIPTIONS.put(folderId, clean(new String(Files.readAllBytes(tmp.toPath()), StandardCharsets.UTF_8)));
            } catch (Exception e) {
                DESCRIPTIONS.put(folderId, "");
            } finally {
                IN_FLIGHT.remove(folderId);
                if (tmp != null) tmp.delete();
            }
        }, "HeroSMP pack description");
        t.setDaemon(true);
        t.start();
        return null;
    }

    /** Description shipped inside an installed map folder, or "" if none. */
    public static String localDescription(String mapName) {
        File f = new File(InstalledPacks.mapDir(mapName), DESCRIPTION_FILE);
        if (!f.isFile()) return "";
        try {
            return clean(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return "";
        }
    }

    private static final ConcurrentHashMap<String, net.minecraft.nbt.NBTTagCompound> KITS =
            new ConcurrentHashMap<String, net.minecraft.nbt.NBTTagCompound>();
    private static final Set<String> KIT_FAILED = ConcurrentHashMap.newKeySet();

    /**
     * The first kit in a store kit pack (a {@code .dat} or kit card on Drive), for drawing its figure.
     * Null while downloading or if the file can't be read; the download starts on first call.
     */
    public static net.minecraft.nbt.NBTTagCompound remoteKitTag(final String fileId) {
        net.minecraft.nbt.NBTTagCompound tag = KITS.get(fileId);
        if (tag != null || KIT_FAILED.contains(fileId) || !IN_FLIGHT.add("kit:" + fileId)) return tag;
        Thread t = new Thread(() -> {
            File tmp = null;
            try {
                tmp = File.createTempFile("beyonder_kitpack_", ".bin");
                DriveClient.downloadFile(fileId, tmp, null);
                KITS.put(fileId, com.matoon.herosmp.npc.kit.KitLibrary.parse(
                        com.matoon.herosmp.npc.kit.KitLibrary.readBytes(tmp)).kits.get(0));
            } catch (Exception e) {
                KIT_FAILED.add(fileId);
            } finally {
                IN_FLIGHT.remove("kit:" + fileId);
                if (tmp != null) tmp.delete();
            }
        }, "Beyonder kit preview");
        t.setDaemon(true);
        t.start();
        return null;
    }

    public static void clear() {
        LISTINGS.clear();
        DESCRIPTIONS.clear();
        KITS.clear();
        KIT_FAILED.clear();
    }

    private static String clean(String s) {
        s = s.replace("\r", "").trim();
        return s.length() > MAX_DESCRIPTION_CHARS ? s.substring(0, MAX_DESCRIPTION_CHARS) + "..." : s;
    }
}
