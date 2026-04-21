package com.matoon.herosmp.hungergames.map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HungerGamesMapConfig {

    private final String mapName;
    private final List<BlockPos> roundSpawns = new ArrayList<>();
    private BlockPos lobbySpawn = null;

    // Per-phase loot pools. Items may carry HGLoot NBT properties (weight, globalMax, etc.)
    private final List<ItemStack> lootPhase1    = new ArrayList<>();
    private final List<ItemStack> lootPhase2    = new ArrayList<>();
    private final List<ItemStack> lootPhase3    = new ArrayList<>();
    private final List<ItemStack> lootAllPhases = new ArrayList<>();

    private final List<ItemStack> breakableBlocks = new ArrayList<>();
    private BlockPos mapCenter = null;
    private int worldBorderStartRange = 200;

    public HungerGamesMapConfig(String mapName) {
        this.mapName = mapName;
    }

    public String getMapName() { return mapName; }
    public List<BlockPos> getRoundSpawns() { return new ArrayList<>(roundSpawns); }
    public BlockPos getLobbySpawn() { return lobbySpawn; }
    public List<ItemStack> getLootPhase1()    { return new ArrayList<>(lootPhase1); }
    public List<ItemStack> getLootPhase2()    { return new ArrayList<>(lootPhase2); }
    public List<ItemStack> getLootPhase3()    { return new ArrayList<>(lootPhase3); }
    public List<ItemStack> getLootAllPhases() { return new ArrayList<>(lootAllPhases); }
    public List<ItemStack> getBreakableBlocks() { return new ArrayList<>(breakableBlocks); }
    public BlockPos getMapCenter() { return mapCenter; }
    public int getWorldBorderStartRange() { return worldBorderStartRange; }

    public void setLobbySpawn(BlockPos pos) { this.lobbySpawn = pos; }
    public void setMapCenter(BlockPos pos) { this.mapCenter = pos; }
    public void setWorldBorderStartRange(int range) { this.worldBorderStartRange = range; }

    public void addRoundSpawn(BlockPos pos) { roundSpawns.add(pos); }

    public boolean removeRoundSpawn(BlockPos pos) {
        return roundSpawns.removeIf(p -> p.equals(pos));
    }

    public boolean hasRoundSpawn(BlockPos pos) {
        return roundSpawns.stream().anyMatch(p -> p.equals(pos));
    }

    public void setLootPhase1(List<ItemStack> items)    { copyInto(lootPhase1, items); }
    public void setLootPhase2(List<ItemStack> items)    { copyInto(lootPhase2, items); }
    public void setLootPhase3(List<ItemStack> items)    { copyInto(lootPhase3, items); }
    public void setLootAllPhases(List<ItemStack> items) { copyInto(lootAllPhases, items); }

    private static void copyInto(List<ItemStack> target, List<ItemStack> source) {
        target.clear();
        for (ItemStack stack : source) {
            if (!stack.isEmpty()) target.add(stack.copy());
        }
    }

    public void setBreakableBlocks(List<ItemStack> items) {
        breakableBlocks.clear();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) breakableBlocks.add(stack.copy());
        }
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound nbt = new NBTTagCompound();

        NBTTagList spawnList = new NBTTagList();
        for (BlockPos pos : roundSpawns) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("X", pos.getX());
            tag.setInteger("Y", pos.getY());
            tag.setInteger("Z", pos.getZ());
            spawnList.appendTag(tag);
        }
        nbt.setTag("RoundSpawns", spawnList);

        if (lobbySpawn != null) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("X", lobbySpawn.getX());
            tag.setInteger("Y", lobbySpawn.getY());
            tag.setInteger("Z", lobbySpawn.getZ());
            nbt.setTag("LobbySpawn", tag);
        }

        nbt.setTag("LootPhase1",    serializeLoot(lootPhase1));
        nbt.setTag("LootPhase2",    serializeLoot(lootPhase2));
        nbt.setTag("LootPhase3",    serializeLoot(lootPhase3));
        nbt.setTag("LootAllPhases", serializeLoot(lootAllPhases));

        if (mapCenter != null) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("X", mapCenter.getX());
            tag.setInteger("Y", mapCenter.getY());
            tag.setInteger("Z", mapCenter.getZ());
            nbt.setTag("MapCenter", tag);
        }
        nbt.setInteger("WorldBorderRange", worldBorderStartRange);

        NBTTagList breakableList = new NBTTagList();
        for (int i = 0; i < breakableBlocks.size(); i++) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Slot", i);
            breakableBlocks.get(i).writeToNBT(tag);
            breakableList.appendTag(tag);
        }
        nbt.setTag("BreakableBlocks", breakableList);

        return nbt;
    }

    public void fromNBT(NBTTagCompound nbt) {
        roundSpawns.clear();
        lootPhase1.clear();
        lootPhase2.clear();
        lootPhase3.clear();
        lootAllPhases.clear();
        breakableBlocks.clear();
        lobbySpawn = null;
        mapCenter = null;
        worldBorderStartRange = 200;

        NBTTagList spawnList = nbt.getTagList("RoundSpawns", 10);
        for (int i = 0; i < spawnList.tagCount(); i++) {
            NBTTagCompound tag = spawnList.getCompoundTagAt(i);
            roundSpawns.add(new BlockPos(tag.getInteger("X"), tag.getInteger("Y"), tag.getInteger("Z")));
        }

        if (nbt.hasKey("LobbySpawn")) {
            NBTTagCompound tag = nbt.getCompoundTag("LobbySpawn");
            lobbySpawn = new BlockPos(tag.getInteger("X"), tag.getInteger("Y"), tag.getInteger("Z"));
        }

        // Migrate old single loot pool → AllPhases
        if (nbt.hasKey("LootPool") && !nbt.hasKey("LootAllPhases")) {
            deserializeLoot(nbt.getTagList("LootPool", 10), lootAllPhases);
        }

        deserializeLoot(nbt.getTagList("LootPhase1",    10), lootPhase1);
        deserializeLoot(nbt.getTagList("LootPhase2",    10), lootPhase2);
        deserializeLoot(nbt.getTagList("LootPhase3",    10), lootPhase3);
        deserializeLoot(nbt.getTagList("LootAllPhases", 10), lootAllPhases);

        if (nbt.hasKey("MapCenter")) {
            NBTTagCompound tag = nbt.getCompoundTag("MapCenter");
            mapCenter = new BlockPos(tag.getInteger("X"), tag.getInteger("Y"), tag.getInteger("Z"));
        }
        if (nbt.hasKey("WorldBorderRange")) {
            worldBorderStartRange = nbt.getInteger("WorldBorderRange");
        }
        NBTTagList breakableList = nbt.getTagList("BreakableBlocks", 10);
        for (int i = 0; i < breakableList.tagCount(); i++) {
            NBTTagCompound tag = breakableList.getCompoundTagAt(i);
            ItemStack stack = new ItemStack(tag);
            if (!stack.isEmpty()) breakableBlocks.add(stack);
        }
    }

    private static NBTTagList serializeLoot(List<ItemStack> pool) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < pool.size(); i++) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Slot", i);
            pool.get(i).writeToNBT(tag);
            list.appendTag(tag);
        }
        return list;
    }

    private static void deserializeLoot(NBTTagList list, List<ItemStack> target) {
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            ItemStack stack = new ItemStack(tag);
            if (!stack.isEmpty()) target.add(stack);
        }
    }

    public void saveToFile(File mapDir) throws IOException {
        CompressedStreamTools.write(toNBT(), new File(mapDir, "herosmp_config.dat"));
    }

    public void loadFromFile(File mapDir) throws IOException {
        File f = new File(mapDir, "herosmp_config.dat");
        if (f.exists()) fromNBT(CompressedStreamTools.read(f));
    }
}
