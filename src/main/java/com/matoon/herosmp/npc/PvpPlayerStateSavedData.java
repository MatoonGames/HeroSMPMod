package com.matoon.herosmp.npc;

import lucraft.mods.lucraftcore.superpowers.SuperpowerHandler;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.UUID;

/**
 * Persists the player's pre-PVP state (inventory, health, potion effects, XP,
 * game mode, position) to disk so it survives server crashes.
 *
 * Each player entry is keyed by their UUID.  On match start the player's full
 * state is written here; on return (normal finish OR login after crash) the
 * state is read back, applied, and then deleted.
 */
public class PvpPlayerStateSavedData extends WorldSavedData {

    public static final String DATA_NAME = "herosmp_pvp_player_states";

    private NBTTagCompound data = new NBTTagCompound();

    public PvpPlayerStateSavedData() {
        super(DATA_NAME);
    }

    public PvpPlayerStateSavedData(String name) {
        super(name);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Save a player's full state before sending them into a PVP arena. */
    public void saveState(EntityPlayerMP player) {
        NBTTagCompound entry = new NBTTagCompound();

        // Position / dimension / rotation
        entry.setInteger("Dim", player.dimension);
        entry.setDouble("PX", player.posX);
        entry.setDouble("PY", player.posY);
        entry.setDouble("PZ", player.posZ);
        entry.setFloat("Yaw", player.rotationYaw);
        entry.setFloat("Pitch", player.rotationPitch);

        // Game mode
        entry.setInteger("GameType", player.interactionManager.getGameType().getID());

        // Health / food
        entry.setFloat("Health", player.getHealth());
        entry.setInteger("FoodLevel", player.getFoodStats().getFoodLevel());
        entry.setFloat("FoodSat", player.getFoodStats().getSaturationLevel());

        // XP
        entry.setInteger("XpLevel", player.experienceLevel);
        entry.setInteger("XpTotal", player.experienceTotal);
        entry.setFloat("Xp", player.experience);

        // Inventory
        NBTTagList inv = new NBTTagList();
        player.inventory.writeToNBT(inv);
        entry.setTag("Inventory", inv);

        // Potion effects
        NBTTagList effects = new NBTTagList();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            NBTTagCompound effectNBT = new NBTTagCompound();
            effect.writeCustomPotionEffectToNBT(effectNBT);
            effects.appendTag(effectNBT);
        }
        entry.setTag("Effects", effects);

        // Forge capabilities (covers mod-added hearts, infinity stones, etc.)
        try {
            NBTTagCompound capNBT = player.serializeNBT();
            if (capNBT.hasKey("ForgeCaps")) {
                entry.setTag("ForgeCaps", capNBT.getTag("ForgeCaps"));
            }
        } catch (Exception e) {
            System.err.println("[HeroSMP] PvpPlayerStateSavedData: could not save capabilities for "
                    + player.getName() + ": " + e.getMessage());
        }

        data.setTag(player.getUniqueID().toString(), entry);
        markDirty();
    }

    /** Returns true if we have a stored pre-PVP state for this player. */
    public boolean hasState(UUID playerId) {
        return data.hasKey(playerId.toString());
    }

    /**
     * Restore the player's pre-PVP state and delete it from storage.
     * Also clears any arena items/effects currently on the player first.
     */
    public void restoreAndClear(EntityPlayerMP player) {
        String key = player.getUniqueID().toString();
        if (!data.hasKey(key)) return;

        NBTTagCompound entry = data.getCompoundTag(key);

        // Wipe arena state cleanly before restoring
        clearArenaState(player);

        // Inventory
        player.inventory.readFromNBT(entry.getTagList("Inventory", Constants.NBT.TAG_COMPOUND));

        // Health / food
        player.setHealth(entry.getFloat("Health"));
        player.getFoodStats().setFoodLevel(entry.getInteger("FoodLevel"));
        player.getFoodStats().setFoodSaturationLevel(entry.getFloat("FoodSat"));

        // XP
        player.experienceLevel = entry.getInteger("XpLevel");
        player.experienceTotal = entry.getInteger("XpTotal");
        player.experience      = entry.getFloat("Xp");

        // Potion effects
        player.clearActivePotions();
        NBTTagList effects = entry.getTagList("Effects", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < effects.tagCount(); i++) {
            PotionEffect effect = PotionEffect.readCustomPotionEffectFromNBT(effects.getCompoundTagAt(i));
            if (effect != null) player.addPotionEffect(effect);
        }

        // Forge capabilities
        if (entry.hasKey("ForgeCaps")) {
            try {
                NBTTagCompound playerNBT = player.serializeNBT();
                playerNBT.setTag("ForgeCaps", entry.getTag("ForgeCaps"));
                player.deserializeNBT(playerNBT);
            } catch (Exception e) {
                System.err.println("[HeroSMP] PvpPlayerStateSavedData: could not restore capabilities for "
                        + player.getName() + ": " + e.getMessage());
            }
        }

        // Game mode
        GameType gameType = GameType.getByID(entry.getInteger("GameType"));
        player.setGameType(gameType == null ? GameType.SURVIVAL : gameType);

        data.removeTag(key);
        markDirty();
    }

    /** Get the stored return dimension (-1 if not stored). */
    public int getReturnDimension(UUID playerId) {
        String key = playerId.toString();
        if (!data.hasKey(key)) return 0;
        return data.getCompoundTag(key).getInteger("Dim");
    }

    /** Get the stored return position as [x, y, z]. */
    public double[] getReturnPosition(UUID playerId) {
        String key = playerId.toString();
        if (!data.hasKey(key)) return new double[]{0, 64, 0};
        NBTTagCompound entry = data.getCompoundTag(key);
        return new double[]{entry.getDouble("PX"), entry.getDouble("PY"), entry.getDouble("PZ")};
    }

    /** Get the stored return rotation as [yaw, pitch]. */
    public float[] getReturnRotation(UUID playerId) {
        String key = playerId.toString();
        if (!data.hasKey(key)) return new float[]{0, 0};
        NBTTagCompound entry = data.getCompoundTag(key);
        return new float[]{entry.getFloat("Yaw"), entry.getFloat("Pitch")};
    }

    /** Remove state without restoring (used after manual restore or cleanup). */
    public void clearState(UUID playerId) {
        String key = playerId.toString();
        if (data.hasKey(key)) {
            data.removeTag(key);
            markDirty();
        }
    }

    // -------------------------------------------------------------------------
    // WorldSavedData
    // -------------------------------------------------------------------------

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        data = nbt.hasKey("Players") ? nbt.getCompoundTag("Players") : new NBTTagCompound();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setTag("Players", data);
        return nbt;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Wipe items, effects, XP from player before restoring their real state. */
    private static void clearArenaState(EntityPlayerMP player) {
        // Inventory
        for (int i = 0; i < player.inventory.mainInventory.size(); i++)
            player.inventory.mainInventory.set(i, ItemStack.EMPTY);
        for (int i = 0; i < player.inventory.armorInventory.size(); i++)
            player.inventory.armorInventory.set(i, ItemStack.EMPTY);
        for (int i = 0; i < player.inventory.offHandInventory.size(); i++)
            player.inventory.offHandInventory.set(i, ItemStack.EMPTY);

        // Effects
        player.clearActivePotions();

        // Reset health / hunger
        player.setHealth(player.getMaxHealth());
        player.getFoodStats().setFoodLevel(20);
        player.getFoodStats().setFoodSaturationLevel(5.0F);

        // Reset XP
        player.experienceLevel = 0;
        player.experienceTotal = 0;
        player.experience      = 0.0F;

        // Clear forge capabilities (clears mod-added hearts, infinity stones, etc.)
        try {
            NBTTagCompound playerNBT = player.serializeNBT();
            playerNBT.setTag("ForgeCaps", new NBTTagCompound());
            player.deserializeNBT(playerNBT);
        } catch (Exception e) {
            System.err.println("[HeroSMP] PvpPlayerStateSavedData: could not clear capabilities for "
                    + player.getName() + ": " + e.getMessage());
        }

        // Reset max health attribute (InfinityCraft/LucraftCore can bump this via the Gauntlet).
        try {
            player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20.0);
            player.setHealth(Math.min(player.getHealth(), 20.0F));
        } catch (Exception e) {
            System.err.println("[HeroSMP] PvpPlayerStateSavedData: could not reset max health for "
                    + player.getName() + ": " + e.getMessage());
        }

        // Remove any Lucraft superpower granted during the match.
        try {
            SuperpowerHandler.removeSuperpower(player);
            SuperpowerHandler.syncToAll(player);
        } catch (Exception e) {
            System.err.println("[HeroSMP] PvpPlayerStateSavedData: could not remove superpower for "
                    + player.getName() + ": " + e.getMessage());
        }

        player.inventory.markDirty();
    }

    /** Load-or-create helper analogous to KitSavedData. */
    public static PvpPlayerStateSavedData get(net.minecraft.server.MinecraftServer server) {
        WorldServer world = server.getWorld(0);
        PvpPlayerStateSavedData d = (PvpPlayerStateSavedData)
                world.getPerWorldStorage().getOrLoadData(PvpPlayerStateSavedData.class, DATA_NAME);
        if (d == null) {
            d = new PvpPlayerStateSavedData();
            world.getPerWorldStorage().setData(DATA_NAME, d);
        }
        return d;
    }
}
