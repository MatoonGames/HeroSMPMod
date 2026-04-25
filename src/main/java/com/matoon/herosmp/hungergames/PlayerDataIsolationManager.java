package com.matoon.herosmp.hungergames;

import lucraft.mods.lucraftcore.superpowers.SuperpowerHandler;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.GameType;
import net.minecraftforge.common.util.Constants;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages complete player data isolation between dimensions.
 * Ensures that player inventories, capabilities, and state don't leak
 * between the main world and Hunger Games dimensions.
 */
public class PlayerDataIsolationManager {

    private static final Map<UUID, PlayerStateSnapshot> storedStates = new HashMap<>();

    /**
     * Save complete player state before entering HG dimension.
     * This includes inventory, armor, offhand, capabilities, position, gamemode, etc.
     */
    public static void savePlayerState(EntityPlayerMP player) {
        UUID playerId = player.getUniqueID();
        PlayerStateSnapshot snapshot = new PlayerStateSnapshot();

        // Save basic info
        snapshot.sourceDimension = player.dimension;
        snapshot.posX = player.posX;
        snapshot.posY = player.posY;
        snapshot.posZ = player.posZ;
        snapshot.rotationYaw = player.rotationYaw;
        snapshot.rotationPitch = player.rotationPitch;
        snapshot.gameMode = player.interactionManager.getGameType();

        // Save health and hunger
        snapshot.health = player.getHealth();
        snapshot.maxHealth = player.getMaxHealth();
        snapshot.foodLevel = player.getFoodStats().getFoodLevel();
        snapshot.foodSaturation = player.getFoodStats().getSaturationLevel();

        // Save XP
        snapshot.experienceLevel = player.experienceLevel;
        snapshot.experienceTotal = player.experienceTotal;
        snapshot.experience = player.experience;

        // Save inventory to NBT
        NBTTagList inventoryList = new NBTTagList();
        player.inventory.writeToNBT(inventoryList);
        snapshot.inventoryData = new NBTTagCompound();
        snapshot.inventoryData.setTag("Inventory", inventoryList);

        // Save potion effects
        snapshot.potionEffects = new NBTTagList();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            NBTTagCompound effectNBT = new NBTTagCompound();
            effect.writeCustomPotionEffectToNBT(effectNBT);
            snapshot.potionEffects.appendTag(effectNBT);
        }

        // Save capabilities (for mod compatibility)
        snapshot.capabilityData = new NBTTagCompound();
        saveCapabilities(player, snapshot.capabilityData);

        storedStates.put(playerId, snapshot);
    }

    /**
     * Restore player state after leaving HG dimension.
     */
    public static void restorePlayerState(EntityPlayerMP player) {
        UUID playerId = player.getUniqueID();
        PlayerStateSnapshot snapshot = storedStates.remove(playerId);

        if (snapshot == null) {
            return; // No stored state
        }

        // Wipe any arena state the player still carries before restoring overworld data.
        clearPlayerState(player);

        // Restore inventory
        player.inventory.readFromNBT(snapshot.inventoryData.getTagList("Inventory", Constants.NBT.TAG_COMPOUND));

        // Restore health and hunger
        player.setHealth(snapshot.health);
        player.getFoodStats().setFoodLevel(snapshot.foodLevel);
        player.getFoodStats().setFoodSaturationLevel(snapshot.foodSaturation);

        // Restore XP
        player.experienceLevel = snapshot.experienceLevel;
        player.experienceTotal = snapshot.experienceTotal;
        player.experience = snapshot.experience;

        // Restore potion effects
        player.clearActivePotions();
        for (int i = 0; i < snapshot.potionEffects.tagCount(); i++) {
            NBTTagCompound effectNBT = snapshot.potionEffects.getCompoundTagAt(i);
            PotionEffect effect = PotionEffect.readCustomPotionEffectFromNBT(effectNBT);
            if (effect != null) {
                player.addPotionEffect(effect);
            }
        }

        // Restore capabilities
        restoreCapabilities(player, snapshot.capabilityData);

        // Restore gamemode
        player.setGameType(snapshot.gameMode);
    }

    /**
     * Get the stored return dimension for a player.
     */
    public static int getReturnDimension(UUID playerId) {
        PlayerStateSnapshot snapshot = storedStates.get(playerId);
        return snapshot != null ? snapshot.sourceDimension : 0; // Default to overworld
    }

    /**
     * Get the stored return position for a player.
     */
    public static double[] getReturnPosition(UUID playerId) {
        PlayerStateSnapshot snapshot = storedStates.get(playerId);
        if (snapshot != null) {
            return new double[]{snapshot.posX, snapshot.posY, snapshot.posZ};
        }
        return new double[]{0, 64, 0}; // Default spawn
    }

    /**
     * Get the stored return rotation for a player.
     */
    public static float[] getReturnRotation(UUID playerId) {
        PlayerStateSnapshot snapshot = storedStates.get(playerId);
        if (snapshot != null) {
            return new float[]{snapshot.rotationYaw, snapshot.rotationPitch};
        }
        return new float[]{0.0F, 0.0F};
    }

    /**
     * Check if a player has stored state.
     */
    public static boolean hasStoredState(UUID playerId) {
        return storedStates.containsKey(playerId);
    }

    /**
     * Clear stored state for a player (cleanup).
     */
    public static void clearStoredState(UUID playerId) {
        storedStates.remove(playerId);
    }

    /**
     * Completely clear a player's current state (for entering HG).
     */
    public static void clearPlayerState(EntityPlayerMP player) {
        // Clear main inventory
        player.inventory.clear();

        // Clear armor
        for (int i = 0; i < player.inventory.armorInventory.size(); i++) {
            player.inventory.armorInventory.set(i, ItemStack.EMPTY);
        }

        // Clear offhand
        for (int i = 0; i < player.inventory.offHandInventory.size(); i++) {
            player.inventory.offHandInventory.set(i, ItemStack.EMPTY);
        }

        // Clear potion effects
        player.clearActivePotions();

        // Reset health and hunger
        player.setHealth(player.getMaxHealth());
        player.getFoodStats().setFoodLevel(20);
        player.getFoodStats().setFoodSaturationLevel(5.0F);

        // Reset XP
        player.experienceLevel = 0;
        player.experienceTotal = 0;
        player.experience = 0.0F;

        // Clear capabilities
        clearCapabilities(player);

        // Reset max health attribute (InfinityCraft/LucraftCore can modify this via the Gauntlet).
        try {
            player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20.0);
            player.setHealth(player.getMaxHealth());
        } catch (Exception e) {
            System.err.println("[HeroSMP] PlayerDataIsolationManager: could not reset max health for "
                    + player.getName() + ": " + e.getMessage());
        }

        // Remove any Lucraft superpower granted during the match.
        try {
            SuperpowerHandler.removeSuperpower(player);
            SuperpowerHandler.syncToAll(player);
        } catch (Exception e) {
            System.err.println("[HeroSMP] PlayerDataIsolationManager: could not remove superpower for "
                    + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Save player capabilities (for mod compatibility).
     * This handles extended inventories from mods like Baubles, Tinkers, etc.
     */
    private static void saveCapabilities(EntityPlayerMP player, NBTTagCompound nbt) {
        try {
            NBTTagCompound capNBT = player.serializeNBT();
            if (capNBT.hasKey("ForgeCaps")) {
                nbt.setTag("ForgeCaps", capNBT.getTag("ForgeCaps"));
            }
        } catch (Exception e) {
            System.err.println("[HeroSMP] Could not save player capabilities (mod compatibility issue): " + e.getMessage());
        }
    }

    /**
     * Restore player capabilities.
     */
    private static void restoreCapabilities(EntityPlayerMP player, NBTTagCompound nbt) {
        if (!nbt.hasKey("ForgeCaps")) return;
        try {
            NBTTagCompound playerNBT = player.serializeNBT();
            playerNBT.setTag("ForgeCaps", nbt.getTag("ForgeCaps"));
            player.deserializeNBT(playerNBT);
        } catch (Exception e) {
            System.err.println("[HeroSMP] Could not restore player capabilities (mod compatibility issue): " + e.getMessage());
        }
    }

    /**
     * Clear player capabilities.
     */
    private static void clearCapabilities(EntityPlayerMP player) {
        try {
            NBTTagCompound playerNBT = player.serializeNBT();
            playerNBT.setTag("ForgeCaps", new NBTTagCompound());
            player.deserializeNBT(playerNBT);
        } catch (Exception e) {
            System.err.println("[HeroSMP] Could not clear player capabilities (mod compatibility issue): " + e.getMessage());
        }
    }

    /**
     * Internal class to store player state snapshot.
     */
    private static class PlayerStateSnapshot {
        int sourceDimension;
        double posX, posY, posZ;
        float rotationYaw, rotationPitch;
        GameType gameMode;
        float health, maxHealth;
        int foodLevel;
        float foodSaturation;
        int experienceLevel, experienceTotal;
        float experience;
        NBTTagCompound inventoryData;
        NBTTagList potionEffects;
        NBTTagCompound capabilityData;
    }
}
