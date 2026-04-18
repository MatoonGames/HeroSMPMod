package com.matoon.herosmp.npc.loot;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.NonNullList;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.List;

public class PvpChestLootManager {

    public void openLootMenu(EntityPlayerMP player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        PvpChestLootSavedData data = getData(server);
        PvpChestLootInventory inventory = new PvpChestLootInventory();
        NonNullList<ItemStack> entries = data.getEntries();
        for (int i = 0; i < inventory.getSizeInventory() && i < entries.size(); i++) {
            inventory.setInventorySlotContents(i, entries.get(i).copy());
        }
        player.displayGUIChest(inventory);
    }

    public void handleContainerClosed(EntityPlayerMP player, Container container) {
        if (!(container instanceof ContainerChest)) {
            return;
        }
        ContainerChest containerChest = (ContainerChest) container;
        if (!(containerChest.getLowerChestInventory() instanceof PvpChestLootInventory)) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        PvpChestLootSavedData data = getData(server);
        NonNullList<ItemStack> entries = data.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            entries.set(i, ItemStack.EMPTY);
        }
        for (int i = 0; i < containerChest.getLowerChestInventory().getSizeInventory() && i < entries.size(); i++) {
            entries.set(i, containerChest.getLowerChestInventory().getStackInSlot(i).copy());
        }
        data.markDirty();
    }

    public List<ItemStack> getLootPool(MinecraftServer server) {
        PvpChestLootSavedData data = getData(server);
        List<ItemStack> pool = new ArrayList<ItemStack>();
        for (ItemStack stack : data.getEntries()) {
            if (!stack.isEmpty()) {
                pool.add(stack.copy());
            }
        }
        return pool;
    }

    private PvpChestLootSavedData getData(MinecraftServer server) {
        WorldServer world = server.getWorld(0);
        if (world == null) {
            throw new IllegalStateException("Overworld not loaded");
        }

        PvpChestLootSavedData data = (PvpChestLootSavedData) world.getPerWorldStorage().getOrLoadData(PvpChestLootSavedData.class, PvpChestLootSavedData.DATA_NAME);
        if (data == null) {
            data = new PvpChestLootSavedData();
            world.getPerWorldStorage().setData(PvpChestLootSavedData.DATA_NAME, data);
        }
        return data;
    }
}
