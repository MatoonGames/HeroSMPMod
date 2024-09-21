package com.matoon.herosmp.level;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.entity.living.LivingEvent;

import java.util.HashMap;
import java.util.Map;

public class LevelHandler {

    private static Map<EntityPlayerMP, Integer> playerLevels = new HashMap<>();
    private static Map<Integer, ItemStack[]> levelRewards = new HashMap<>();

    public static void loadLevelRewards(Configuration config) {
        // Clear existing rewards
        levelRewards.clear();

        // Iterate over each level in the config (level1, level2, level3, etc.)
        int maxLevels = 100; // Assuming a max of 100 levels, adjust as needed
        for (int i = 1; i <= maxLevels; i++) {
            String levelKey = "level" + i;
            String rewardString = config.getString(levelKey, Configuration.CATEGORY_GENERAL, "", "Items for level " + i);

            if (!rewardString.isEmpty()) {
                try {
                    // Convert the reward string into ItemStack objects (using NBT if applicable)
                    ItemStack[] rewards = parseItemStackFromString(rewardString);
                    levelRewards.put(i, rewards);
                } catch (NBTException e) {
                    System.err.println("Error parsing items for " + levelKey + ": " + e.getMessage());
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(LivingEvent.LivingUpdateEvent event) {
        if (event.getEntity() instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.getEntity();
            int currentLevel = player.experienceLevel;

            if (!playerLevels.containsKey(player)) {
                playerLevels.put(player, currentLevel);
            } else {
                int previousLevel = playerLevels.get(player);
                if (currentLevel > previousLevel) {
                    handlePlayerLevelUp(player, currentLevel);
                    playerLevels.put(player, currentLevel);
                }
            }
        }
    }

    public static void handlePlayerLevelUp(EntityPlayerMP player, int newLevel) {
        // Send congratulatory message
        player.sendMessage(new TextComponentString(TextFormatting.GOLD + "Congratulations! You've reached Level " + newLevel + "!"));

        // Give rewards if available for this level
        ItemStack[] rewards = getRewardsForLevel(newLevel);
        if (rewards != null) {
            for (ItemStack reward : rewards) {
                player.inventory.addItemStackToInventory(reward);
                player.sendMessage(new TextComponentString("You have received: " + reward.getDisplayName()));
            }
        }
    }

    private static ItemStack[] getRewardsForLevel(int level) {
        return levelRewards.getOrDefault(level, null); // Returns null if no rewards are found
    }

    private static ItemStack[] parseItemStackFromString(String itemString) throws NBTException {
        // This function assumes the item string is in NBT format and converts it to ItemStacks
        String[] itemNBTStrings = itemString.split(","); // Split by commas for multiple items
        ItemStack[] itemStacks = new ItemStack[itemNBTStrings.length];

        for (int i = 0; i < itemNBTStrings.length; i++) {
            itemStacks[i] = new ItemStack(JsonToNBT.getTagFromJson(itemNBTStrings[i]));
        }

        return itemStacks;
    }
}
