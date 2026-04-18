package com.matoon.herosmp.npc.loot;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.NonNullList;
import net.minecraft.world.storage.WorldSavedData;

public class PvpChestLootSavedData extends WorldSavedData {

    public static final String DATA_NAME = "herosmp_pvp_chest_loot";

    private final NonNullList<ItemStack> entries = NonNullList.withSize(PvpChestLootInventory.SIZE, ItemStack.EMPTY);

    public PvpChestLootSavedData() {
        super(DATA_NAME);
    }

    public PvpChestLootSavedData(String name) {
        super(name);
    }

    public NonNullList<ItemStack> getEntries() {
        return entries;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        for (int i = 0; i < entries.size(); i++) {
            entries.set(i, ItemStack.EMPTY);
        }

        NBTTagList list = nbt.getTagList("Entries", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            int slot = entry.getInteger("Slot");
            if (slot < 0 || slot >= entries.size()) {
                continue;
            }
            entries.set(slot, new ItemStack(entry));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < entries.size(); i++) {
            ItemStack stack = entries.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("Slot", i);
            stack.writeToNBT(entry);
            list.appendTag(entry);
        }
        compound.setTag("Entries", list);
        return compound;
    }
}
