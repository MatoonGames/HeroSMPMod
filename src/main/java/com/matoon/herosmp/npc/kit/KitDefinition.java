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
    /** Disabled kits stay saved but aren't offered to players (toggled from the Content Packs store). */
    private boolean enabled = true;
    /**
     * Optional LucraftCore injection (a lucraftcore:injection stack) granted when a player picks
     * this kit for a round; empty for none. Taken away again when the player leaves the round.
     */
    private ItemStack power = ItemStack.EMPTY;
    /** The NBT this kit was loaded from (null for new kits); keeps items from mods not installed here. */
    private NBTTagCompound sourceTag;

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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ItemStack getPower() {
        return power.copy();
    }

    public boolean hasPower() {
        return !power.isEmpty();
    }

    public void setPower(ItemStack power) {
        this.power = power == null || power.isEmpty() ? ItemStack.EMPTY : power.copy();
        if (!this.power.isEmpty()) this.power.setCount(1);
    }

    /**
     * The NBT this kit was loaded from, or its current serialized form for a new kit. Use this for
     * requirement checks: items from mods that aren't installed only survive in the raw NBT.
     */
    public NBTTagCompound getSourceTag() {
        return sourceTag != null ? sourceTag : serialize();
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
        if (!enabled) tag.setBoolean("Disabled", true);

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
        if (!power.isEmpty()) tag.setTag("Power", power.writeToNBT(new NBTTagCompound()));
        // Which mods / Hero Addons the kit needs, so players without them can be told what to install.
        NBTTagList requires = KitRequirements.describe(tag, sourceTag != null ? sourceTag.getTagList(KitRequirements.TAG, 10) : null);
        if (requires.tagCount() > 0) tag.setTag(KitRequirements.TAG, requires);
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

        KitDefinition kit = new KitDefinition(key, displayName, icon, contents);
        kit.setEnabled(!tag.getBoolean("Disabled"));
        if (tag.hasKey("Power", 10)) kit.setPower(new ItemStack(tag.getCompoundTag("Power")));
        kit.sourceTag = tag.copy();
        return kit;
    }
}
