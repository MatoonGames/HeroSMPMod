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
        pendingConfig.setLootPhase1(existing.getLootPhase1());
        pendingConfig.setLootPhase2(existing.getLootPhase2());
        pendingConfig.setLootPhase3(existing.getLootPhase3());
        pendingConfig.setLootAllPhases(existing.getLootAllPhases());
        pendingConfig.setBreakableBlocks(new ArrayList<>(existing.getBreakableBlocks()));
        pendingConfig.setMapCenter(existing.getMapCenter());
        pendingConfig.setWorldBorderStartRange(existing.getWorldBorderStartRange());
        // Copy injection pools and per-phase min/max counts.
        pendingConfig.setInjectionPhase1(existing.getInjectionPhase1());
        pendingConfig.setInjectionPhase2(existing.getInjectionPhase2());
        pendingConfig.setInjectionPhase3(existing.getInjectionPhase3());
        pendingConfig.setInjectionAllPhases(existing.getInjectionAllPhases());
        pendingConfig.setInjMinPhase1(existing.getInjMinPhase1());
        pendingConfig.setInjMaxPhase1(existing.getInjMaxPhase1());
        pendingConfig.setInjMinPhase2(existing.getInjMinPhase2());
        pendingConfig.setInjMaxPhase2(existing.getInjMaxPhase2());
        pendingConfig.setInjMinPhase3(existing.getInjMinPhase3());
        pendingConfig.setInjMaxPhase3(existing.getInjMaxPhase3());
        pendingConfig.setInjMinAllPhases(existing.getInjMinAllPhases());
        pendingConfig.setInjMaxAllPhases(existing.getInjMaxAllPhases());
    }

    public UUID getPlayerUUID() { return playerUUID; }
    public String getMapName() { return mapName; }
    public int getDimensionId() { return dimensionId; }
    public HungerGamesMapConfig getPendingConfig() { return pendingConfig; }
}
