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
    private final List<ItemStack> lootPool = new ArrayList<>();
    private final List<ItemStack> breakableBlocks = new ArrayList<>();
    private BlockPos mapCenter = null;
    private int worldBorderStartRange = 200;

    public HungerGamesMapConfig(String mapName) {
        this.mapName = mapName;
    }

    public String getMapName() { return mapName; }
    public List<BlockPos> getRoundSpawns() { return new ArrayList<>(roundSpawns); }
    public BlockPos getLobbySpawn() { return lobbySpawn; }
    public List<ItemStack> getLootPool() { return new ArrayList<>(lootPool); }
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

    public void setLootPool(List<ItemStack> items) {
        lootPool.clear();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) lootPool.add(stack.copy());
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

        NBTTagList lootList = new NBTTagList();
        for (int i = 0; i < lootPool.size(); i++) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Slot", i);
            lootPool.get(i).writeToNBT(tag);
            lootList.appendTag(tag);
        }
        nbt.setTag("LootPool", lootList);

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
        lootPool.clear();
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

        NBTTagList lootList = nbt.getTagList("LootPool", 10);
        for (int i = 0; i < lootList.tagCount(); i++) {
            NBTTagCompound tag = lootList.getCompoundTagAt(i);
            ItemStack stack = new ItemStack(tag);
            if (!stack.isEmpty()) lootPool.add(stack);
        }

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

    public void saveToFile(File mapDir) throws IOException {
        CompressedStreamTools.write(toNBT(), new File(mapDir, "herosmp_config.dat"));
    }

    public void loadFromFile(File mapDir) throws IOException {
        File f = new File(mapDir, "herosmp_config.dat");
        if (f.exists()) fromNBT(CompressedStreamTools.read(f));
    }
}
