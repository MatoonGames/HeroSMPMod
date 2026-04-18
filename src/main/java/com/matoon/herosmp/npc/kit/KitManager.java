package com.matoon.herosmp.npc.kit;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.NonNullList;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class KitManager {

    public List<KitDefinition> getKits(MinecraftServer server) {
        return new ArrayList<KitDefinition>(getData(server).getKits().values());
    }

    public KitDefinition getKit(MinecraftServer server, String key) {
        if (key == null) {
            return null;
        }
        return getData(server).getKits().get(normalizeKey(key));
    }

    public KitDefinition createOrUpdateFromPlayer(MinecraftServer server, EntityPlayerMP player, String displayName, ItemStack icon) {
        String key = normalizeKey(displayName);
        if (key.isEmpty()) {
            key = "kit";
        }

        NonNullList<ItemStack> contents = NonNullList.withSize(41, ItemStack.EMPTY);
        for (int i = 0; i < 36; i++) {
            contents.set(i, player.inventory.mainInventory.get(i).copy());
        }
        for (int i = 0; i < 4; i++) {
            contents.set(36 + i, player.inventory.armorInventory.get(i).copy());
        }
        contents.set(40, player.inventory.offHandInventory.get(0).copy());

        KitDefinition kit = new KitDefinition(key, displayName, icon.copy(), contents);
        KitSavedData data = getData(server);
        data.getKits().put(key, kit);
        data.markDirty();
        return kit;
    }

    public boolean removeKit(MinecraftServer server, String key) {
        KitSavedData data = getData(server);
        KitDefinition removed = data.getKits().remove(normalizeKey(key));
        if (removed != null) {
            data.markDirty();
            return true;
        }
        return false;
    }

    public void applyKitToPlayer(EntityPlayerMP player, KitDefinition kit) {
        clearPlayerInventory(player);
        NonNullList<ItemStack> contents = kit.getContentsCopy();

        for (int i = 0; i < 36; i++) {
            player.inventory.mainInventory.set(i, contents.get(i).copy());
        }
        for (int i = 0; i < 4; i++) {
            player.inventory.armorInventory.set(i, contents.get(36 + i).copy());
        }
        player.inventory.offHandInventory.set(0, contents.get(40).copy());

        player.inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
    }

    public void clearPlayerInventory(EntityPlayerMP player) {
        for (int i = 0; i < player.inventory.mainInventory.size(); i++) {
            player.inventory.mainInventory.set(i, ItemStack.EMPTY);
        }
        for (int i = 0; i < player.inventory.armorInventory.size(); i++) {
            player.inventory.armorInventory.set(i, ItemStack.EMPTY);
        }
        for (int i = 0; i < player.inventory.offHandInventory.size(); i++) {
            player.inventory.offHandInventory.set(i, ItemStack.EMPTY);
        }
        player.inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
    }

    private KitSavedData getData(MinecraftServer server) {
        WorldServer world = server.getWorld(0);
        KitSavedData data = (KitSavedData) world.getPerWorldStorage().getOrLoadData(KitSavedData.class, KitSavedData.DATA_NAME);
        if (data == null) {
            data = new KitSavedData();
            world.getPerWorldStorage().setData(KitSavedData.DATA_NAME, data);
        }
        return data;
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }
}
