package com.matoon.herosmp.item;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

/** Consumable key used to unlock a locked superpower injection pickup. */
public class ItemPowerKey extends Item {
    public ItemPowerKey() {
        setTranslationKey("power_key");
        setRegistryName("herosmp", "power_key");
        setCreativeTab(CreativeTabs.MISC);
        setMaxStackSize(16);
    }
}
