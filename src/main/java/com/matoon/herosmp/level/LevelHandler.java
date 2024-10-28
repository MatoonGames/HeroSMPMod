package com.matoon.herosmp.level;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.config.Configuration;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTException;
import net.minecraft.util.ResourceLocation;

import java.util.List;
import java.util.ArrayList;

public class LevelHandler {

    // Store the rewards per level in a hardcoded map
    public static Map<Integer, ItemStack[]> levelRewards = new HashMap<>();

    // This method loads the level rewards from the config file
    public static void loadLevelRewards(Configuration config) {
        levelRewards.clear(); // Clear previous rewards
        System.out.println("Starting to load level rewards from config...");

        for (String key : config.getCategory(Configuration.CATEGORY_GENERAL).keySet()) {
            System.out.println("Found config key: " + key);  // Log the key found
            if (key.startsWith("level")) {
                String rewardString = config.getString(key, Configuration.CATEGORY_GENERAL, "", "Items for " + key);

                if (!rewardString.isEmpty()) {
                    try {
                        System.out.println("Loading rewards for " + key + ": " + rewardString);
                        ItemStack[] rewards = parseItemStackFromString(rewardString);

                        if (rewards.length > 0) {
                            int level = Integer.parseInt(key.replace("level", ""));
                            levelRewards.put(level, rewards);
                            System.out.println("Rewards parsed for level " + level + ": " + Arrays.toString(rewards));
                        } else {
                            System.out.println("Failed to parse rewards for " + key);
                        }

                    } catch (Exception e) {
                        System.err.println("Error parsing items for " + key + ": " + e.getMessage());
                    }
                } else {
                    System.out.println("No rewards found in config for " + key);
                }
            }
        }
    }

    // This method parses a reward string into an array of ItemStacks with NBT support
    public static ItemStack[] parseItemStackFromString(String rewardString) {
        if (rewardString.isEmpty()) {
            System.out.println("Empty reward string");
            return new ItemStack[0];
        }

        System.out.println("Parsing reward string: " + rewardString);

        // Split by commas for individual item stacks
        String[] itemStrings = rewardString.split(",");
        ItemStack[] itemStacks = new ItemStack[itemStrings.length];

        for (int i = 0; i < itemStrings.length; i++) {
            String[] parts = itemStrings[i].trim().split(" ");
            if (parts.length < 2) {
                System.err.println("Invalid reward format: " + itemStrings[i]);
                continue;
            }

            String itemName = parts[0];
            int quantity;
            try {
                quantity = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid quantity for reward: " + parts[1]);
                continue;
            }

            // Use Item.REGISTRY to get item by name
            Item item = Item.REGISTRY.getObject(new ResourceLocation(itemName));
            if (item == null) {
                System.err.println("Invalid item: " + itemName);
                continue;
            }

            ItemStack itemStack = new ItemStack(item, quantity);

            // Handle NBT data if present
            if (parts.length >= 3) {
                try {
                    String nbtData = itemStrings[i].substring(itemStrings[i].indexOf("{")); // Extract NBT
                    NBTTagCompound nbt = JsonToNBT.getTagFromJson(nbtData);
                    itemStack.setTagCompound(nbt);
                } catch (Exception e) {
                    System.err.println("Error parsing NBT for item: " + itemStrings[i]);
                    e.printStackTrace();
                }
            }

            itemStacks[i] = itemStack;
        }

        return itemStacks;
    }

    // Handle player leveling up and give rewards
    public static void handlePlayerLevelUp(EntityPlayerMP player, int newLevel) {
        // Log the player level up
        System.out.println("Player " + player.getName() + " leveled up to level " + newLevel);

        // Send a congratulatory message to the player
        player.sendMessage(new TextComponentString(TextFormatting.GOLD + "Congratulations! You've reached Level " + newLevel + "!"));

        // Fetch rewards for the new level
        ItemStack[] rewards = levelRewards.get(newLevel);

        if (rewards == null || rewards.length == 0) {
            System.out.println("No rewards found for level " + newLevel);
            return;
        }

        // Loop through the rewards and add them to the player's inventory
        for (ItemStack reward : rewards) {
            if (reward == null) {
                player.sendMessage(new TextComponentString(TextFormatting.RED + "One of your rewards could not be given due to an error."));
                continue; // Skip null rewards
            }

            try {
                player.inventory.addItemStackToInventory(reward);
                System.out.println("Gave player " + player.getName() + " item: " + reward.getDisplayName());
            } catch (Exception e) {
                player.sendMessage(new TextComponentString(TextFormatting.RED + "You were supposed to get: " + reward.getDisplayName() + ", but an error occurred: " + e.getMessage()));
                System.err.println("Failed to give player " + player.getName() + " item: " + reward.getDisplayName() + " - " + e.getMessage());
            }
        }
    }
}
