package com.matoon.herosmp.npc;

import com.matoon.herosmp.HeroSMP;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import java.util.UUID;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class PvpQueueEvents {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayerMP)) {
            return;
        }
        try {
            HeroSMP.PVP_QUEUE_MANAGER.handlePlayerDeath((EntityPlayerMP) event.getEntityLiving());
        } catch (Exception e) {
            System.err.println("[HeroSMP] Exception in PVP death handler: " + e.getMessage());
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        HeroSMP.PVP_QUEUE_MANAGER.handlePlayerLogout((EntityPlayerMP) event.player);
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        HeroSMP.PVP_QUEUE_MANAGER.handleRespawn((EntityPlayerMP) event.player);
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        HeroSMP.PVP_QUEUE_MANAGER.handleLogin((EntityPlayerMP) event.player);
    }

    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntityPlayer() instanceof EntityPlayerMP)) {
            return;
        }
        HeroSMP.PVP_CHEST_LOOT_MANAGER.handleContainerClosed((EntityPlayerMP) event.getEntityPlayer(), event.getContainer());
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntityPlayer() instanceof EntityPlayerMP)) {
            return;
        }
        if (HeroSMP.PVP_QUEUE_MANAGER.handleChestHighlightInteract((EntityPlayerMP) event.getEntityPlayer(), event.getTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getEntityPlayer() instanceof EntityPlayerMP)) {
            return;
        }
        if (HeroSMP.PVP_QUEUE_MANAGER.handleChestHighlightInteract((EntityPlayerMP) event.getEntityPlayer(), event.getTarget())) {
            event.setCanceled(true);
        }
    }

    /**
     * Prevent players in a PVP match from leaving to another dimension via items
     * like the Heroes Expansion Tesseract.  Cancelling this event stops the
     * dimension change before it happens, keeping the arena fully contained.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onDimensionTravel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.getEntity();
        UUID playerId = player.getUniqueID();
        if (HeroSMP.PVP_QUEUE_MANAGER.isPlayerInPvpSession(playerId)) {
            // Allow the initial teleport into the arena (player is in pending arrivals).
            if (HeroSMP.PVP_QUEUE_MANAGER.isPendingArenaArrival(playerId)) {
                return;
            }
            event.setCanceled(true);
            player.sendMessage(new net.minecraft.util.text.TextComponentString(
                net.minecraft.util.text.TextFormatting.RED + "You cannot leave the arena during a match!"));
        }
    }

    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent event) {
        if (HeroSMP.PVP_QUEUE_MANAGER.isSpectatorBat(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (HeroSMP.PVP_QUEUE_MANAGER.isSpectatorBat(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (FMLCommonHandler.instance().getMinecraftServerInstance() != null) {
            HeroSMP.PVP_QUEUE_MANAGER.tickMatchProgress(FMLCommonHandler.instance().getMinecraftServerInstance());
            HeroSMP.PVP_QUEUE_MANAGER.tickArenaSafety(FMLCommonHandler.instance().getMinecraftServerInstance());
            HeroSMP.PVP_QUEUE_MANAGER.tickDimensionEscapeCheck(FMLCommonHandler.instance().getMinecraftServerInstance());
            HeroSMP.PVP_QUEUE_MANAGER.tickCleanup(FMLCommonHandler.instance().getMinecraftServerInstance());
        }
    }
}
