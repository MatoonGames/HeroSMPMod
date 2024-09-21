package com.matoon.herosmp.level;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;

public class LevelEventHandler {

    // Store previous experience levels for each player to detect changes
    private static Map<EntityPlayerMP, Integer> playerLevels = new HashMap<>();

    @SubscribeEvent
    public void onPlayerTick(LivingEvent.LivingUpdateEvent event) {
        // Ensure we're working with a player on the server side
        if (event.getEntity() instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.getEntity();

            // Get the current experience level of the player
            int currentLevel = player.experienceLevel;

            // Check if the player's level has changed
            if (!playerLevels.containsKey(player)) {
                playerLevels.put(player, currentLevel);
            } else {
                int previousLevel = playerLevels.get(player);

                // If the level has increased, trigger the level-up event
                if (currentLevel > previousLevel) {
                    LevelHandler.handlePlayerLevelUp(player, currentLevel);
                    playerLevels.put(player, currentLevel); // Update stored level
                }
            }
        }
    }
}
