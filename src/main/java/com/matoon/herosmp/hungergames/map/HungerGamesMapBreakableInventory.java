package com.matoon.herosmp.hungergames.map;

import net.minecraft.inventory.InventoryBasic;

public class HungerGamesMapBreakableInventory extends InventoryBasic {

    public static final int SIZE = 54;

    private final String mapName;

    public HungerGamesMapBreakableInventory(String mapName) {
        super("HG Breakable Blocks: " + mapName, false, SIZE);
        this.mapName = mapName;
    }

    public String getMapName() { return mapName; }
}
