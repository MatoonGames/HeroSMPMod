package com.matoon.herosmp.hungergames.events;

import com.matoon.herosmp.HeroSMP;
import com.matoon.herosmp.hungergames.HungerGamesWorldManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import java.util.UUID;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Event handler for the Hunger Games system.
 */
public class HungerGamesEvents {

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.side.isServer()
                && event.world.provider.getDimension() == 0) {
            HeroSMP.HUNGER_GAMES_MANAGER.tick(event.world.getMinecraftServer());
        }
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof EntityPlayerMP) {
            HeroSMP.HUNGER_GAMES_MANAGER.handlePlayerDeath((EntityPlayerMP) event.getEntity());
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            HeroSMP.HUNGER_GAMES_MANAGER.handlePlayerRespawn((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent event) {
        if (HeroSMP.HUNGER_GAMES_MANAGER.isHGSpectatorBat(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (HeroSMP.HUNGER_GAMES_MANAGER.isHGSpectatorBat(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            HeroSMP.HUNGER_GAMES_MANAGER.handlePlayerLogout((EntityPlayerMP) event.player);
        }
    }

    /**
     * Restore players from HG when they log back in.
     * If they disconnected while in a match or configure session, their overworld
     * state is still stored — teleport them home automatically.
     */
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            HeroSMP.HUNGER_GAMES_MANAGER.handlePlayerLogin((EntityPlayerMP) event.player);
        }
    }

    /**
     * Intercept chat for pending text inputs (e.g. world border range entry).
     */
    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        if (!(event.getPlayer() instanceof EntityPlayerMP)) return;
        if (HeroSMP.HUNGER_GAMES_MANAGER.handlePlayerChat(
                (EntityPlayerMP) event.getPlayer(), event.getMessage())) {
            event.setCanceled(true);
        }
    }

    /**
     * Handle right-clicking a block with a configure tool.
     */
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntityPlayer() instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();

        ItemStack held = player.getHeldItemMainhand();
        String toolType = HungerGamesWorldManager.getConfigToolType(held);
        if (toolType == null) return;
        if (!HeroSMP.HUNGER_GAMES_MANAGER.isInConfigureMode(player.getUniqueID())) return;

        event.setCanceled(true);
        BlockPos pos = event.getPos();
        HeroSMP.HUNGER_GAMES_MANAGER.handleConfigureToolUse(player, toolType, pos);
    }

    /**
     * Handle right-clicking in the air with a configure tool.
     * Cancels item use for all config tools (prevents ender pearl throw, etc.).
     * Only loot_pool and map_center (air) trigger actual actions.
     */
    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntityPlayer() instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();

        ItemStack held = player.getHeldItemMainhand();
        String toolType = HungerGamesWorldManager.getConfigToolType(held);
        if (toolType == null) return;
        if (!HeroSMP.HUNGER_GAMES_MANAGER.isInConfigureMode(player.getUniqueID())) return;

        event.setCanceled(true);
        if ("loot_pool".equals(toolType) || "map_center".equals(toolType) || "breakable_blocks".equals(toolType)) {
            HeroSMP.HUNGER_GAMES_MANAGER.handleConfigureToolUse(player, toolType, null);
        }
    }

    /**
     * Allow breaking only: blocks placed by players during this round, or blocks
     * in the map's configured breakable-blocks list.
     */
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof EntityPlayerMP)) return;
        UUID id = event.getPlayer().getUniqueID();
        if (!HeroSMP.HUNGER_GAMES_MANAGER.isPlayerInMatch(id)) return;

        BlockPos pos = event.getPos();
        if (!HeroSMP.HUNGER_GAMES_MANAGER.canBreakBlock(id, pos, event.getWorld())) {
            event.setCanceled(true);
        } else {
            HeroSMP.HUNGER_GAMES_MANAGER.trackBlockBroken(id, pos);
        }
    }

    /** Track blocks placed by players during a HG round for player-placed-block breaking. */
    @SubscribeEvent
    public void onBlockPlace(BlockEvent.PlaceEvent event) {
        if (!(event.getPlayer() instanceof EntityPlayerMP)) return;
        HeroSMP.HUNGER_GAMES_MANAGER.trackBlockPlaced(
                event.getPlayer().getUniqueID(), event.getPos());
    }

    /**
     * Save loot pool changes when the player closes the loot-pool inventory GUI.
     */
    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntityPlayer() instanceof EntityPlayerMP)) return;
        HeroSMP.HUNGER_GAMES_MANAGER.handleLootInventoryClose(
            (EntityPlayerMP) event.getEntityPlayer(), event.getContainer());
    }
}
