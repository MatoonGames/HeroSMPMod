package com.matoon.herosmp.npc.kit;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.storage.WorldSavedData;

import java.util.LinkedHashMap;
import java.util.Map;

public class KitSavedData extends WorldSavedData {

    public static final String DATA_NAME = "herosmp_kits";

    private final Map<String, KitDefinition> kits = new LinkedHashMap<String, KitDefinition>();

    public KitSavedData() {
        super(DATA_NAME);
    }

    public KitSavedData(String name) {
        super(name);
    }

    public Map<String, KitDefinition> getKits() {
        return kits;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        kits.clear();
        NBTTagList list = nbt.getTagList("Kits", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            KitDefinition kit = KitDefinition.deserialize(list.getCompoundTagAt(i));
            kits.put(kit.getKey(), kit);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList list = new NBTTagList();
        for (KitDefinition kit : kits.values()) {
            list.appendTag(kit.serialize());
        }
        compound.setTag("Kits", list);
        return compound;
    }
}
