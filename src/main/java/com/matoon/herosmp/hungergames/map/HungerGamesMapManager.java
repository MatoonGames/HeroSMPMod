package com.matoon.herosmp.hungergames.map;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

public class HungerGamesMapManager {

    private final File mapsDirectory;
    private final Random random = new Random();

    public HungerGamesMapManager(File mapsDirectory) {
        this.mapsDirectory = mapsDirectory;
        mapsDirectory.mkdirs();
    }

    public File getMapsDirectory() { return mapsDirectory; }

    public List<String> getAvailableMaps() {
        List<String> maps = new ArrayList<>();
        if (!mapsDirectory.exists()) return maps;
        File[] subdirs = mapsDirectory.listFiles(File::isDirectory);
        if (subdirs == null) return maps;
        for (File dir : subdirs) {
            if (new File(dir, "region").isDirectory() || new File(dir, "level.dat").exists()) {
                maps.add(dir.getName());
            }
        }
        return maps;
    }

    /** Returns all maps eligible for actual HG matches — excludes reserved names like "Lobby". */
    public List<String> getPlayableMaps() {
        List<String> maps = getAvailableMaps();
        maps.removeIf(name -> name.equalsIgnoreCase("Lobby"));
        return maps;
    }

    public String pickRandomMap() {
        List<String> maps = getPlayableMaps();
        return maps.isEmpty() ? null : maps.get(random.nextInt(maps.size()));
    }

    public File getMapDir(String mapName) {
        return new File(mapsDirectory, mapName);
    }

    public HungerGamesMapConfig loadMapConfig(String mapName) {
        HungerGamesMapConfig config = new HungerGamesMapConfig(mapName);
        try {
            config.loadFromFile(getMapDir(mapName));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return config;
    }

    public void saveMapConfig(HungerGamesMapConfig config) {
        try {
            config.saveToFile(getMapDir(config.getMapName()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean saveDimToMapDir(String mapName, File dimDir) {
        if (!dimDir.exists()) return false;
        File mapDir = getMapDir(mapName);
        mapDir.mkdirs();
        try {
            copyDirectory(dimDir.toPath(), mapDir.toPath());
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean copyMapWorldToDir(String mapName, File targetDir) {
        File mapDir = getMapDir(mapName);
        if (!mapDir.exists()) return false;
        try {
            copyDirectory(mapDir.toPath(), targetDir.toPath());
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            paths.forEach(src -> {
                if (src.getFileName() != null && src.getFileName().toString().equals("herosmp_config.dat")) {
                    return; // Skip map config — it lives only in the source folder
                }
                Path dst = target.resolve(source.relativize(src));
                try {
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dst);
                    } else {
                        Files.createDirectories(dst.getParent());
                        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }
}
