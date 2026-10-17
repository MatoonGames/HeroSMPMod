package com.matoon.herosmp.client.store;

import com.matoon.herosmp.HeroSMP;
import com.matoon.herosmp.npc.kit.KitLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.integrated.IntegratedServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Client-side view of PVP kits: the shared {@link KitLibrary} ({@code beyonder_kits/}, which a kit
 * can be assigned to any number of worlds from) and the older kits saved inside a single
 * singleplayer world ({@code data/herosmp_kits.dat}, Forge WorldSavedData format: gzip NBT
 * {data:{Kits:[...]}}). Worlds that are currently open are read-only here, since their running
 * server would overwrite the file when it saves.
 */
public final class InstalledKits {

    private InstalledKits() {}

    public static final class Kit {
        public final String key, displayName;
        public final ItemStack icon;
        public final int itemCount;
        public final boolean enabled;
        /** The kit's NBT (KitDefinition format), for editing / exporting. */
        public final NBTTagCompound tag;
        /** Library kits: worlds it's available in (empty = all). Empty for world kits. */
        public final List<String> worlds;

        Kit(NBTTagCompound tag, List<String> worlds) {
            this.tag = tag;
            this.key = tag.getString("Key");
            this.displayName = tag.getString("DisplayName");
            this.icon = new ItemStack(tag.getCompoundTag("Icon"));
            this.itemCount = tag.getTagList("Contents", 10).tagCount();
            this.enabled = !tag.getBoolean("Disabled");
            this.worlds = worlds;
        }

        public String name() {
            return displayName.isEmpty() ? key : displayName;
        }
    }

    public static final class WorldKits {
        public final File dir;
        public final String worldName;
        public final boolean open;
        public final List<Kit> kits;

        WorldKits(File dir, String worldName, boolean open, List<Kit> kits) {
            this.dir = dir;
            this.worldName = worldName;
            this.open = open;
            this.kits = kits;
        }
    }

    /** A singleplayer world save. */
    public static final class WorldInfo {
        public final File dir;
        public final String name;
        public final long lastPlayed;
        public final boolean open;

        WorldInfo(File dir, String name, long lastPlayed, boolean open) {
            this.dir = dir;
            this.name = name;
            this.lastPlayed = lastPlayed;
            this.open = open;
        }

        public String folder() {
            return dir.getName();
        }
    }

    // ---------------------------------------------------------------------
    // Library
    // ---------------------------------------------------------------------

    public static List<Kit> library() {
        List<Kit> out = new ArrayList<Kit>();
        for (KitLibrary.Entry e : KitLibrary.list()) out.add(new Kit(e.tag, e.worlds));
        return out;
    }

    // ---------------------------------------------------------------------
    // Worlds
    // ---------------------------------------------------------------------

    /** Singleplayer worlds (except the throwaway editor world), most recently played first. */
    public static List<WorldInfo> worlds() {
        Minecraft mc = Minecraft.getMinecraft();
        File[] dirs = new File(mc.gameDir, "saves").listFiles(File::isDirectory);
        if (dirs == null) return Collections.emptyList();
        String openFolder = openWorldFolder();
        List<WorldInfo> out = new ArrayList<WorldInfo>();
        for (File dir : dirs) {
            if (!new File(dir, "level.dat").isFile()) continue;
            if (MapEditorLauncher.EDITOR_FOLDER.equals(dir.getName()) || PlaytestLauncher.FOLDER.equals(dir.getName())) continue;
            String name = dir.getName();
            long lastPlayed = dir.lastModified();
            try (InputStream in = new FileInputStream(new File(dir, "level.dat"))) {
                NBTTagCompound data = CompressedStreamTools.readCompressed(in).getCompoundTag("Data");
                if (!data.getString("LevelName").isEmpty()) name = data.getString("LevelName");
                if (data.hasKey("LastPlayed")) lastPlayed = data.getLong("LastPlayed");
            } catch (Exception ignored) {
                // Folder name / mtime fallbacks are fine.
            }
            out.add(new WorldInfo(dir, name, lastPlayed, dir.getName().equals(openFolder)));
        }
        Collections.sort(out, (a, b) -> Long.compare(b.lastPlayed, a.lastPlayed));
        return out;
    }

    /** Folder of the singleplayer world open right now, or null. */
    public static String openWorldFolder() {
        IntegratedServer running = Minecraft.getMinecraft().getIntegratedServer();
        return running != null ? running.getFolderName() : null;
    }

    /** "All worlds", the single world's name, or "N worlds". */
    public static String describeWorlds(List<String> folders, List<WorldInfo> known) {
        if (folders.isEmpty()) return "All worlds";
        if (folders.size() == 1) {
            for (WorldInfo w : known) if (w.folder().equalsIgnoreCase(folders.get(0))) return w.name;
            return folders.get(0);
        }
        return folders.size() + " worlds";
    }

    // ---------------------------------------------------------------------
    // Kits saved inside a world
    // ---------------------------------------------------------------------

    private static File kitsFile(File worldDir) {
        return new File(new File(worldDir, "data"), "herosmp_kits.dat");
    }

    /** Every world (except the throwaway editor world) that has at least one kit. */
    public static List<WorldKits> scan() {
        Minecraft mc = Minecraft.getMinecraft();
        File[] dirs = new File(mc.gameDir, "saves").listFiles(File::isDirectory);
        if (dirs == null) return Collections.emptyList();
        String openFolder = openWorldFolder();

        List<WorldKits> out = new ArrayList<WorldKits>();
        for (File dir : dirs) {
            if (MapEditorLauncher.EDITOR_FOLDER.equals(dir.getName()) || PlaytestLauncher.FOLDER.equals(dir.getName())) continue;
            File f = kitsFile(dir);
            if (!f.isFile()) continue;
            try {
                NBTTagList list;
                try {
                    list = readKitList(f);
                } catch (IOException busy) {
                    // An open world rewrites this file in place; try once more before skipping it.
                    try { Thread.sleep(15); } catch (InterruptedException ignored) { }
                    list = readKitList(f);
                }
                List<Kit> kits = new ArrayList<Kit>();
                for (int i = 0; i < list.tagCount(); i++) {
                    kits.add(new Kit(list.getCompoundTagAt(i), Collections.<String>emptyList()));
                }
                if (!kits.isEmpty()) out.add(new WorldKits(dir, worldName(dir), dir.getName().equals(openFolder), kits));
            } catch (IOException e) {
                System.err.println("[Beyonder] Couldn't read kits in " + dir.getName() + ": " + e.getMessage());
            }
        }
        Collections.sort(out, (a, b) -> a.worldName.compareToIgnoreCase(b.worldName));
        return out;
    }

    public static int totalKits(List<WorldKits> worlds) {
        int n = 0;
        for (WorldKits w : worlds) n += w.kits.size();
        return n;
    }

    /** Sets a kit's enabled flag. */
    public static void setEnabled(File worldDir, String key, boolean enabled) throws IOException {
        File f = kitsFile(worldDir);
        NBTTagCompound root = readRoot(f);
        NBTTagList list = data(root).getTagList("Kits", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound k = list.getCompoundTagAt(i);
            if (!key.equals(k.getString("Key"))) continue;
            if (enabled) k.removeTag("Disabled");
            else k.setBoolean("Disabled", true);
        }
        write(f, root);
    }

    /** Removes a kit from the world. */
    public static void delete(File worldDir, String key) throws IOException {
        File f = kitsFile(worldDir);
        NBTTagCompound root = readRoot(f);
        NBTTagCompound data = data(root);
        NBTTagList list = data.getTagList("Kits", 10);
        NBTTagList kept = new NBTTagList();
        for (int i = 0; i < list.tagCount(); i++) {
            if (!key.equals(list.getCompoundTagAt(i).getString("Key"))) kept.appendTag(list.getCompoundTagAt(i));
        }
        data.setTag("Kits", kept);
        write(f, root);
    }

    /**
     * Removes a kit from a world, even the one open right now: there the running integrated server
     * owns the save, so the removal is handed to it instead of editing the file under it.
     */
    public static void removeFromWorld(File worldDir, final String key) throws IOException {
        final IntegratedServer running = Minecraft.getMinecraft().getIntegratedServer();
        if (running != null && worldDir.getName().equals(running.getFolderName())) {
            running.addScheduledTask(() -> HeroSMP.KIT_MANAGER.removeKit(running, key));
        } else {
            delete(worldDir, key);
        }
    }

    // ---------------------------------------------------------------------

    private static NBTTagList readKitList(File f) throws IOException {
        return data(readRoot(f)).getTagList("Kits", 10);
    }

    private static NBTTagCompound readRoot(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            return CompressedStreamTools.readCompressed(in);
        }
    }

    /** WorldSavedData files wrap their payload in a "data" compound; tolerate unwrapped files too. */
    private static NBTTagCompound data(NBTTagCompound root) {
        if (!root.hasKey("data", 10)) root.setTag("data", new NBTTagCompound());
        return root.getCompoundTag("data");
    }

    private static void write(File f, NBTTagCompound root) throws IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(root, bytes);
        // Never delete-then-rename (see KitLibrary.writeAtomic): that could lose the world's kits.
        com.matoon.herosmp.npc.kit.KitLibrary.writeAtomic(f, bytes.toByteArray());
    }

    private static String worldName(File dir) {
        try (InputStream in = new FileInputStream(new File(dir, "level.dat"))) {
            String n = CompressedStreamTools.readCompressed(in).getCompoundTag("Data").getString("LevelName");
            if (n != null && !n.isEmpty()) return n;
        } catch (Exception ignored) {
            // Folder name fallback.
        }
        return dir.getName();
    }
}
