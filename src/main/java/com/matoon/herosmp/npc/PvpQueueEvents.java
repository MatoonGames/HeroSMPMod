package com.matoon.herosmp.npc;

import com.matoon.herosmp.HeroSMP;
import com.matoon.herosmp.hungergames.map.LucraftInjectionContainer;
import com.matoon.herosmp.hungergames.map.LucraftInjectionPropertiesContainer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import java.util.List;
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
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();
        // Handle PvP injection properties editor close.
        // Guard: skip if this player is in an HG configure-map session (their GUI belongs to HG).
        if (event.getContainer() instanceof LucraftInjectionPropertiesContainer
                && !HeroSMP.HUNGER_GAMES_MANAGER.isInConfigureMode(player.getUniqueID())) {
            LucraftInjectionPropertiesContainer c = (LucraftInjectionPropertiesContainer) event.getContainer();
            if (player.getServer() != null) {
                HeroSMP.PVP_INJECTION_MANAGER.saveFromPropertiesMenu(player.getServer(), c.getPropInv());
                int count = HeroSMP.PVP_INJECTION_MANAGER.getPool(player.getServer()).size();
                player.sendMessage(new net.minecraft.util.text.TextComponentString(
                        net.minecraft.util.text.TextFormatting.GREEN + "PvP injection properties saved: " + count + " injection(s) in pool."));
            }
            return;
        }
        // Handle PvP injection pool editor close.
        // Guard: skip if this player is in an HG configure-map session (their GUI belongs to HG).
        if (event.getContainer() instanceof LucraftInjectionContainer
                && !HeroSMP.HUNGER_GAMES_MANAGER.isInConfigureMode(player.getUniqueID())) {
            LucraftInjectionContainer inv = (LucraftInjectionContainer) event.getContainer();
            if (player.getServer() != null) {
                List<net.minecraft.item.ItemStack> items = inv.getInjectionInventory().getTabItems(3);
                HeroSMP.PVP_INJECTION_MANAGER.setPool(player.getServer(), items);
                player.sendMessage(new net.minecraft.util.text.TextComponentString(
                        net.minecraft.util.text.TextFormatting.GREEN + "PvP injection pool saved: " + items.size() + " injection(s)."));
            }
            return;
        }
        HeroSMP.PVP_CHEST_LOOT_MANAGER.handleContainerClosed(player, event.getContainer());
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntityPlayer() instanceof EntityPlayerMP)) {
            return;
        }
        System.out.println("[HeroSMP][EntityInteract] player=" + event.getEntityPlayer().getName()
                + " target=" + event.getTarget().getClass().getSimpleName()
                + " hand=" + event.getHand()
                + " cancelled=" + event.isCanceled());
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
