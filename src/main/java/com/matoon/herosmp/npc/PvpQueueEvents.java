package com.matoon.herosmp.npc;

import com.matoon.herosmp.HeroSMP;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class PvpQueueEvents {

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayerMP)) {
            return;
        }
        HeroSMP.PVP_QUEUE_MANAGER.handlePlayerDeath((EntityPlayerMP) event.getEntityLiving());
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

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (FMLCommonHandler.instance().getMinecraftServerInstance() != null) {
            HeroSMP.PVP_QUEUE_MANAGER.tickMatchProgress(FMLCommonHandler.instance().getMinecraftServerInstance());
            HeroSMP.PVP_QUEUE_MANAGER.tickArenaSafety(FMLCommonHandler.instance().getMinecraftServerInstance());
            HeroSMP.PVP_QUEUE_MANAGER.tickCleanup(FMLCommonHandler.instance().getMinecraftServerInstance());
        }
    }
}
