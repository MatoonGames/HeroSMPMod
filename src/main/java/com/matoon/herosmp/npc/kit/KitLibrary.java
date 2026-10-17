package com.matoon.herosmp.npc.kit;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Global kit library: {@code <gameDir>/beyonder_kits/<key>.dat}, shared by every world (and, in
 * singleplayer, by the client store). Each file uses the one kit file format used everywhere —
 * what {@code /heropvp kit download} produces, what the store's {@code PVP Kits} Drive folder holds:
 * gzip NBT {@code { Kits: [ <KitDefinition.serialize()> ], Worlds: [ "<world folder>", ... ] }},
 * where a missing / empty {@code Worlds} list means every world.
 *
 * Works on NBT so it has no client or world dependencies; files are re-read only when their mtime
 * changes, so edits made by the store show up on a running integrated server straight away.
 */
public final class KitLibrary {

    private KitLibrary() {}

    private static File dir;
    private static final Map<String, Cached> CACHE = new HashMap<String, Cached>();
    /** Unreadable files already reported, so a broken file doesn't spam the log on every refresh. */
    private static final java.util.Set<String> WARNED = new java.util.HashSet<String>();

    public static void init(File directory) {
        dir = directory;
        if (!dir.isDirectory()) dir.mkdirs();
    }

    public static File dir() {
        return dir;
    }

    /** A kit file's contents. */
    public static final class Parsed {
        public final List<NBTTagCompound> kits;
        /** World folders the kits apply to; empty = every world. */
        public final List<String> worlds;

        public Parsed(List<NBTTagCompound> kits, List<String> worlds) {
            this.kits = kits;
            this.worlds = worlds;
        }
    }

    /** One library kit (the first kit of a library file). */
    public static final class Entry {
        public final File file;
        public final NBTTagCompound tag;
        public final List<String> worlds;
        private KitDefinition kit;

        Entry(File file, NBTTagCompound tag, List<String> worlds) {
            this.file = file;
            this.tag = tag;
            this.worlds = Collections.unmodifiableList(worlds);
        }

        public String key() {
            return tag.getString("Key");
        }

        public String displayName() {
            String n = tag.getString("DisplayName");
            return n.isEmpty() ? key() : n;
        }

        public boolean enabled() {
            return !tag.getBoolean("Disabled");
        }

        public boolean allWorlds() {
            return worlds.isEmpty();
        }

        public boolean appliesTo(String worldFolder) {
            return KitLibrary.appliesTo(worlds, worldFolder);
        }

        /** The kit itself (builds ItemStacks, so only call once items are registered). */
        public KitDefinition kit() {
            if (kit == null) kit = KitDefinition.deserialize(tag);
            return kit;
        }
    }

    private static final class Cached {
        final long modified, length;
        final List<Entry> entries;
        Cached(long modified, long length, List<Entry> entries) {
            this.modified = modified;
            this.length = length;
            this.entries = entries;
        }
    }

    // ---------------------------------------------------------------------
    // Library
    // ---------------------------------------------------------------------

    /** Every kit in the library, sorted by display name. Unreadable files are skipped. */
    public static synchronized List<Entry> list() {
        List<Entry> out = new ArrayList<Entry>();
        if (dir != null) recoverInterruptedWrites();
        // .dat kit files, plus kit cards (.png pictures carrying a kit) dropped in by hand.
        File[] files = dir == null ? null : dir.listFiles((d, n) -> {
            String l = n.toLowerCase(Locale.ROOT);
            return l.endsWith(".dat") || l.endsWith(".png");
        });
        if (files == null) return out;
        Map<String, Cached> seen = new HashMap<String, Cached>();
        for (File f : files) {
            String path = f.getAbsolutePath();
            Cached c = CACHE.get(path);
            if (c == null || c.modified != f.lastModified() || c.length != f.length()) {
                long modified = f.lastModified(), length = f.length();
                List<Entry> entries = read(f);
                // A failed read (file briefly locked by antivirus / OneDrive / a writer) is not
                // cached, otherwise the kit would stay hidden until the file changed again.
                if (entries == null) continue;
                c = new Cached(modified, length, entries);
            }
            seen.put(path, c);
            out.addAll(c.entries);
        }
        CACHE.clear();
        CACHE.putAll(seen);
        Collections.sort(out, (a, b) -> a.displayName().compareToIgnoreCase(b.displayName()));
        return out;
    }

    /** The kits in a library file, or null if it couldn't be read (locked or broken; retried next time). */
    private static List<Entry> read(File f) {
        Parsed p;
        try {
            p = parse(readBytes(f));
        } catch (IOException first) {
            try {
                Thread.sleep(15); // one quick retry: the usual cause is another process holding the file
                p = parse(readBytes(f));
            } catch (IOException | InterruptedException e) {
                // Not cached, so it's tried again on the next refresh (locked now, fine later).
                if (WARNED.add(f.getAbsolutePath())) {
                    System.err.println("[Beyonder] Couldn't read kit file " + f.getName() + ": " + e.getMessage());
                }
                return null;
            }
        }
        WARNED.remove(f.getAbsolutePath());
        List<Entry> out = new ArrayList<Entry>();
        for (NBTTagCompound kit : p.kits) {
            if (!kit.getString("Key").isEmpty()) out.add(new Entry(f, kit, p.worlds));
        }
        return out;
    }

    /**
     * A save that was cut off between writing {@code x.dat.tmp} and moving it over {@code x.dat}
     * (older versions deleted the original first) leaves the kit only in the .tmp file. Put it back.
     */
    private static void recoverInterruptedWrites() {
        File[] tmps = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".dat.tmp"));
        if (tmps == null) return;
        for (File tmp : tmps) {
            File real = new File(dir, tmp.getName().substring(0, tmp.getName().length() - 4));
            if (real.exists() || tmp.length() == 0) continue;
            try {
                parse(readBytes(tmp));
                java.nio.file.Files.move(tmp.toPath(), real.toPath());
                System.out.println("[Beyonder] Recovered kit file " + real.getName());
            } catch (IOException ignored) {
                // Not a complete kit file; leave it alone.
            }
        }
    }

    public static Entry get(String key) {
        String k = normalizeKey(key);
        for (Entry e : list()) if (e.key().equals(k)) return e;
        return null;
    }

    /** Library kits available in the given world folder. */
    public static List<Entry> forWorld(String worldFolder) {
        List<Entry> out = new ArrayList<Entry>();
        for (Entry e : list()) if (e.appliesTo(worldFolder)) out.add(e);
        return out;
    }

    public static File fileFor(String key) {
        return new File(dir, normalizeKey(key) + ".dat");
    }

    /**
     * Writes (or replaces) the library file for this kit. A file can hold several kits (packs or
     * files dropped in by hand); only this kit is taken out of it, the others stay where they are.
     */
    public static synchronized void write(NBTTagCompound kitTag, List<String> worlds) throws IOException {
        String key = normalizeKey(kitTag.getString("Key"));
        if (key.isEmpty()) throw new IOException("Kit has no name.");
        byte[] bytes = toFileBytes(Collections.singletonList(kitTag), worlds);
        Entry existing = get(key);
        if (existing != null && isDat(existing.file) && othersIn(existing.file, key).isEmpty()) {
            writeAtomic(existing.file, bytes); // the usual case: the kit's own file
            return;
        }
        if (existing != null) removeFromFile(existing.file, key);
        writeAtomic(freeFileFor(key), bytes);
    }

    /** Deletes the library kit with this key (other kits sharing its file are kept). */
    public static synchronized boolean delete(String key) {
        Entry e = get(key);
        if (e == null) return false;
        try {
            removeFromFile(e.file, normalizeKey(key));
            return true;
        } catch (IOException ex) {
            System.err.println("[Beyonder] Couldn't delete kit " + key + ": " + ex.getMessage());
            return false;
        }
    }

    private static boolean isDat(File f) {
        return f.getName().toLowerCase(Locale.ROOT).endsWith(".dat");
    }

    /** Kits in the file other than {@code key}. */
    private static List<NBTTagCompound> othersIn(File f, String key) throws IOException {
        List<NBTTagCompound> others = new ArrayList<NBTTagCompound>();
        for (NBTTagCompound k : parse(readBytes(f)).kits) {
            if (!normalizeKey(k.getString("Key")).equals(key)) others.add(k);
        }
        return others;
    }

    /** Takes one kit out of a library file: deletes the file if nothing else is in it. */
    private static void removeFromFile(File f, String key) throws IOException {
        Parsed p = parse(readBytes(f));
        List<NBTTagCompound> others = othersIn(f, key);
        if (others.isEmpty()) {
            java.nio.file.Files.deleteIfExists(f.toPath());
        } else if (isDat(f)) {
            writeAtomic(f, toFileBytes(others, p.worlds));
        } else {
            // A kit card holding several kits: keep the rest as a plain kit file.
            String base = f.getName().replaceAll("(?i)\\.png$", "");
            writeAtomic(freeFile(normalizeKey(base) + "_kits"), toFileBytes(others, p.worlds));
            java.nio.file.Files.deleteIfExists(f.toPath());
        }
    }

    /** {@code <key>.dat}, or {@code <key>_2.dat}... if that name is taken by a file holding other kits. */
    private static File freeFileFor(String key) {
        File f = fileFor(key);
        if (!f.exists()) return f;
        try {
            if (othersIn(f, key).isEmpty()) return f;
        } catch (IOException ignored) {
            // Unreadable: don't overwrite it.
        }
        return freeFile(normalizeKey(key));
    }

    private static File freeFile(String base) {
        for (int i = 2; ; i++) {
            File f = new File(dir, base + "_" + i + ".dat");
            if (!f.exists()) return f;
        }
    }

    /** Flips the kit's Disabled flag, keeping its worlds. */
    public static synchronized void setEnabled(String key, boolean enabled) throws IOException {
        Entry e = get(key);
        if (e == null) throw new IOException("Kit not found: " + key);
        NBTTagCompound tag = e.tag.copy();
        if (enabled) tag.removeTag("Disabled");
        else tag.setBoolean("Disabled", true);
        write(tag, e.worlds);
    }

    // ---------------------------------------------------------------------
    // File format
    // ---------------------------------------------------------------------

    public static boolean appliesTo(List<String> worlds, String worldFolder) {
        if (worlds == null || worlds.isEmpty()) return true;
        if (worldFolder == null) return false;
        for (String w : worlds) if (w.equalsIgnoreCase(worldFolder)) return true;
        return false;
    }

    public static NBTTagCompound toFileTag(List<NBTTagCompound> kits, List<String> worlds) {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound k : kits) list.appendTag(k.copy());
        root.setTag("Kits", list);
        if (worlds != null && !worlds.isEmpty()) {
            NBTTagList w = new NBTTagList();
            for (String s : worlds) w.appendTag(new NBTTagString(s));
            root.setTag("Worlds", w);
        }
        return root;
    }

    public static byte[] toFileBytes(List<NBTTagCompound> kits, List<String> worlds) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(toFileTag(kits, worlds), out);
        return out.toByteArray();
    }

    public static byte[] toFileBytes(KitDefinition kit, List<String> worlds) throws IOException {
        return toFileBytes(Collections.singletonList(kit.serialize()), worlds);
    }

    /**
     * Reads a kit file. Accepts the library format, a bare {@code {Kits:[...]}} file, a world's
     * {@code herosmp_kits.dat} (wrapped in {@code data}), gzip or uncompressed, and kit cards
     * ({@link KitCard}: a PNG with the kit file embedded).
     */
    public static Parsed parse(byte[] bytes) throws IOException {
        if (KitCard.isPng(bytes)) {
            // A kit card: the kit file travels inside the picture.
            bytes = KitCard.extract(bytes);
            if (bytes == null) throw new IOException("This image doesn't contain a kit.");
        }
        NBTTagCompound root;
        try {
            root = CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes));
        } catch (IOException gzipFail) {
            root = CompressedStreamTools.read(new java.io.DataInputStream(new ByteArrayInputStream(bytes)));
        }
        return parse(root);
    }

    public static Parsed parse(NBTTagCompound root) throws IOException {
        NBTTagCompound holder = root.hasKey("data", 10) ? root.getCompoundTag("data") : root;
        NBTTagList list = holder.getTagList("Kits", 10);
        List<NBTTagCompound> kits = new ArrayList<NBTTagCompound>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound k = list.getCompoundTagAt(i);
            String key = normalizeKey(k.getString("Key").isEmpty() ? k.getString("DisplayName") : k.getString("Key"));
            if (key.isEmpty()) continue;
            k.setString("Key", key);
            if (k.getString("DisplayName").isEmpty()) k.setString("DisplayName", key);
            kits.add(k);
        }
        if (kits.isEmpty()) throw new IOException("The file contains no kits.");
        List<String> worlds = new ArrayList<String>();
        NBTTagList w = holder.getTagList("Worlds", 8);
        for (int i = 0; i < w.tagCount(); i++) {
            String s = w.getStringTagAt(i).trim();
            if (!s.isEmpty() && !worlds.contains(s)) worlds.add(s);
        }
        return new Parsed(kits, worlds);
    }

    public static String normalizeKey(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    /** A file name (no extension) safe on every OS, keeping the kit's display name readable. */
    public static String safeFileName(String name) {
        String s = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").replace("..", "_").trim();
        return s.isEmpty() ? "kit" : s;
    }

    // ---------------------------------------------------------------------
    // IO
    // ---------------------------------------------------------------------

    public static byte[] readBytes(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.max(32, f.length()));
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    /**
     * Writes a file safely: the bytes go to {@code <name>.tmp}, which then replaces the file in one
     * move. The old file is never deleted first; on Windows a delete while another process (antivirus,
     * OneDrive, the indexer) has the file open only marks it for deletion, and the following rename
     * then fails, which used to lose the kit. A locked target is retried for a moment, then written in
     * place as a last resort.
     */
    public static void writeAtomic(File f, byte[] bytes) throws IOException {
        File parent = f.getAbsoluteFile().getParentFile();
        if (parent != null) parent.mkdirs();
        File tmp = new File(parent, f.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(bytes);
        }
        java.nio.file.Path from = tmp.toPath(), to = f.toPath();
        IOException last = null;
        for (int attempt = 0; attempt < 6; attempt++) {
            try {
                try {
                    java.nio.file.Files.move(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    java.nio.file.Files.move(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (IOException e) {
                last = e;
                try {
                    Thread.sleep(25L * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        // Still locked: write in place, keeping the .tmp copy if even that fails.
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(bytes);
        } catch (IOException e) {
            if (last != null) e.addSuppressed(last);
            throw e;
        }
        tmp.delete();
    }
}
