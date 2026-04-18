package com.matoon.herosmp.npc.kit;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.NonNullList;

public class KitDefinition {

    private final String key;
    private final String displayName;
    private final ItemStack icon;
    private final NonNullList<ItemStack> contents;

    public KitDefinition(String key, String displayName, ItemStack icon, NonNullList<ItemStack> contents) {
        this.key = key;
        this.displayName = displayName;
        this.icon = icon.copy();
        this.contents = NonNullList.withSize(contents.size(), ItemStack.EMPTY);
        for (int i = 0; i < contents.size(); i++) {
            this.contents.set(i, contents.get(i).copy());
        }
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ItemStack getIcon() {
        return icon.copy();
    }

    public NonNullList<ItemStack> getContentsCopy() {
        NonNullList<ItemStack> copy = NonNullList.withSize(contents.size(), ItemStack.EMPTY);
        for (int i = 0; i < contents.size(); i++) {
            copy.set(i, contents.get(i).copy());
        }
        return copy;
    }

    public NBTTagCompound serialize() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Key", key);
        tag.setString("DisplayName", displayName);

        NBTTagCompound iconTag = new NBTTagCompound();
        icon.writeToNBT(iconTag);
        tag.setTag("Icon", iconTag);

        NBTTagList list = new NBTTagList();
        for (int i = 0; i < contents.size(); i++) {
            ItemStack stack = contents.get(i);
            if (stack.isEmpty()) {
                continue;
            }

            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("Slot", i);
            stack.writeToNBT(entry);
            list.appendTag(entry);
        }
        tag.setTag("Contents", list);
        return tag;
    }

    public static KitDefinition deserialize(NBTTagCompound tag) {
        String key = tag.getString("Key");
        String displayName = tag.getString("DisplayName");
        ItemStack icon = new ItemStack(tag.getCompoundTag("Icon"));

        NonNullList<ItemStack> contents = NonNullList.withSize(41, ItemStack.EMPTY);
        NBTTagList list = tag.getTagList("Contents", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            int slot = entry.getInteger("Slot");
            if (slot < 0 || slot >= contents.size()) {
                continue;
            }
            contents.set(slot, new ItemStack(entry));
        }

        return new KitDefinition(key, displayName, icon, contents);
    }
}
