package com.matoon.herosmp.npc.kit;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.NonNullList;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class KitManager {

    /**
     * Every kit usable in this world: the world's own kits, then library kits assigned to this
     * world (or to all worlds). A world kit wins over a library kit with the same key.
     */
    public List<KitDefinition> getKits(MinecraftServer server) {
        Map<String, KitDefinition> out = new LinkedHashMap<String, KitDefinition>(getData(server).getKits());
        for (KitLibrary.Entry e : KitLibrary.forWorld(server.getFolderName())) {
            if (!out.containsKey(e.key())) out.put(e.key(), e.kit());
        }
        return new ArrayList<KitDefinition>(out.values());
    }

    /** Kits players may pick (disabled kits are hidden). */
    public List<KitDefinition> getEnabledKits(MinecraftServer server) {
        List<KitDefinition> out = new ArrayList<KitDefinition>();
        for (KitDefinition kit : getKits(server)) if (kit.isEnabled()) out.add(kit);
        return out;
    }

    public KitDefinition getKit(MinecraftServer server, String key) {
        if (key == null) {
            return null;
        }
        String k = normalizeKey(key);
        KitDefinition kit = getData(server).getKits().get(k);
        if (kit != null) return kit;
        KitLibrary.Entry e = libraryKit(server, k);
        return e != null ? e.kit() : null;
    }

    /** True when the kit comes from the shared library rather than this world's save. */
    public boolean isLibraryKit(MinecraftServer server, String key) {
        String k = normalizeKey(key);
        return !getData(server).getKits().containsKey(k) && libraryKit(server, k) != null;
    }

    private KitLibrary.Entry libraryKit(MinecraftServer server, String key) {
        KitLibrary.Entry e = KitLibrary.get(key);
        return e != null && e.appliesTo(server.getFolderName()) ? e : null;
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
        KitDefinition previous = data.getKits().get(key);
        if (previous != null) kit.setEnabled(previous.isEnabled()); // re-saving a kit keeps its toggle
        data.getKits().put(key, kit);
        data.markDirty();
        return kit;
    }

    /** Saves a kit into this world's own kits (used by kit playtests). */
    public void putWorldKit(MinecraftServer server, KitDefinition kit) {
        KitSavedData data = getData(server);
        data.getKits().put(kit.getKey(), kit);
        data.markDirty();
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

        // The kit's superpower, if it has one (taken away again when the player leaves the round).
        if (kit.hasPower() && com.matoon.herosmp.integration.LucraftInjectionEntry.isValidInjection(kit.getPower())) {
            com.matoon.herosmp.integration.SuperpowerGrant.grant(player, kit.getPower(), "KIT POWER:");
        }
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
        return KitLibrary.normalizeKey(value);
    }
}
