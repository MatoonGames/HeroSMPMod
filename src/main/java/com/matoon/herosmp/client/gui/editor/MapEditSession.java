package com.matoon.herosmp.client.gui.editor;

import com.matoon.herosmp.client.store.InstalledPacks;
import com.matoon.herosmp.hungergames.map.HungerGamesMapConfig;
import com.matoon.herosmp.hungergames.map.HungerGamesMapManager;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * The map being edited: a working {@link HungerGamesMapConfig} loaded straight from the installed
 * map folder, an unsaved-changes flag, and save with a {@code .bak} backup. Everything here is plain
 * data, so the editor needs no running world.
 */
public final class MapEditSession {

    public static final String CONFIG_FILE = "herosmp_config.dat";

    private static final String[] LIST_KEYS = {
            "LootPhase1", "LootPhase2", "LootPhase3", "LootAllPhases", "BreakableBlocks",
            "InjPhase1", "InjPhase2", "InjPhase3", "InjAllPhases"
    };

    public final String mapName;
    public final File mapDir;
    public final HungerGamesMapConfig config;
    public final boolean lobbyMap;
    /** Entries that referenced items from mods that aren't installed (they'd be lost on save). */
    public final int droppedEntries;
    /** True when the map had no config file yet (defaults were applied). */
    public final boolean newConfig;

    private boolean dirty;

    public MapEditSession(String mapName) {
        this.mapName = mapName;
        this.mapDir = InstalledPacks.mapDir(mapName);
        this.lobbyMap = "Lobby".equalsIgnoreCase(mapName);
        File file = new File(mapDir, CONFIG_FILE);
        this.newConfig = !file.isFile();
        this.config = new HungerGamesMapManager(InstalledPacks.mapsDir()).loadMapConfig(mapName);
        this.droppedEntries = newConfig ? 0 : countDropped(file);
    }

    private int countDropped(File file) {
        try {
            NBTTagCompound raw = CompressedStreamTools.read(file);
            if (raw == null) return 0;
            int rawCount = 0;
            for (String key : LIST_KEYS) rawCount += raw.getTagList(key, 10).tagCount();
            if (raw.hasKey("LootPool") && !raw.hasKey("LootAllPhases")) rawCount += raw.getTagList("LootPool", 10).tagCount();
            int loaded = config.getLootPhase1().size() + config.getLootPhase2().size() + config.getLootPhase3().size()
                    + config.getLootAllPhases().size() + config.getBreakableBlocks().size()
                    + config.getInjectionPhase1().size() + config.getInjectionPhase2().size()
                    + config.getInjectionPhase3().size() + config.getInjectionAllPhases().size();
            return Math.max(0, rawCount - loaded);
        } catch (IOException e) {
            return 0;
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    /** Writes the config, keeping the previous file as {@code herosmp_config.dat.bak}. */
    public void save() throws IOException {
        File file = new File(mapDir, CONFIG_FILE);
        if (file.isFile()) {
            Files.copy(file.toPath(), new File(mapDir, CONFIG_FILE + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        config.saveToFile(mapDir);
        dirty = false;
    }

    /** Replaces the spawn list, keeping order (order decides which player gets which spawn). */
    public void setSpawns(List<BlockPos> spawns) {
        for (BlockPos p : config.getRoundSpawns()) config.removeRoundSpawn(p);
        for (BlockPos p : spawns) config.addRoundSpawn(p);
        markDirty();
    }
}
