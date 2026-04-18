package com.matoon.herosmp.npc.loot;

import net.minecraft.inventory.InventoryBasic;

public class PvpChestLootInventory extends InventoryBasic {

    public static final int SIZE = 54;
    public static final String TITLE = "PvP Chest Loot Pool";

    public PvpChestLootInventory() {
        super(TITLE, false, SIZE);
    }
}
