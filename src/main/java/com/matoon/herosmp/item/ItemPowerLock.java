package com.matoon.herosmp.item;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

/** Consumable tool used to lock an unlocked superpower pickup. */
public class ItemPowerLock extends Item {
    public ItemPowerLock() {
        setTranslationKey("power_lock");
        setRegistryName("herosmp", "power_lock");
        setCreativeTab(CreativeTabs.MISC);
        setMaxStackSize(16);
    }
}
