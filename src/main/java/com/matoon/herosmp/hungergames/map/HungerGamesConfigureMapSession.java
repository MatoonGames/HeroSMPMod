package com.matoon.herosmp.hungergames.map;

import java.util.ArrayList;
import java.util.UUID;

public class HungerGamesConfigureMapSession {

    private final UUID playerUUID;
    private final String mapName;
    private final int dimensionId;
    private final HungerGamesMapConfig pendingConfig;

    public HungerGamesConfigureMapSession(UUID playerUUID, String mapName, int dimensionId, HungerGamesMapConfig existing) {
        this.playerUUID = playerUUID;
        this.mapName = mapName;
        this.dimensionId = dimensionId;
        this.pendingConfig = new HungerGamesMapConfig(mapName);
        existing.getRoundSpawns().forEach(pendingConfig::addRoundSpawn);
        pendingConfig.setLobbySpawn(existing.getLobbySpawn());
        pendingConfig.setLootPool(new ArrayList<>(existing.getLootPool()));
        pendingConfig.setBreakableBlocks(new ArrayList<>(existing.getBreakableBlocks()));
        pendingConfig.setMapCenter(existing.getMapCenter());
        pendingConfig.setWorldBorderStartRange(existing.getWorldBorderStartRange());
    }

    public UUID getPlayerUUID() { return playerUUID; }
    public String getMapName() { return mapName; }
    public int getDimensionId() { return dimensionId; }
    public HungerGamesMapConfig getPendingConfig() { return pendingConfig; }
}
