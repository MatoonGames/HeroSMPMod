package com.matoon.herosmp.hungergames.map;

import net.minecraft.inventory.InventoryBasic;

public class HungerGamesMapLootInventory extends InventoryBasic {

    public static final int SIZE = 54;

    private final String mapName;

    public HungerGamesMapLootInventory(String mapName) {
        super("HG Loot Pool: " + mapName, false, SIZE);
        this.mapName = mapName;
    }

    public String getMapName() { return mapName; }
}
