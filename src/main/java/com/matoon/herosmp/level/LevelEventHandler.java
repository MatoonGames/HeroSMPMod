package com.matoon.herosmp.level;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;

public class LevelEventHandler {

    private static Map<EntityPlayerMP, Integer> playerLevels = new HashMap<>();

    @SubscribeEvent
    public void onPlayerTick(LivingEvent.LivingUpdateEvent event) {
        if (event.getEntity() instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.getEntity();

            // Use the experience level getter method instead of direct field access if available
            int currentLevel = player.experienceLevel;

            if (!playerLevels.containsKey(player)) {
                playerLevels.put(player, currentLevel);
            } else {
                int previousLevel = playerLevels.get(player);
                if (currentLevel > previousLevel) {
                    LevelHandler.handlePlayerLevelUp(player, currentLevel);
                    playerLevels.put(player, currentLevel); // Update stored level
                }
            }
        }
    }
}
