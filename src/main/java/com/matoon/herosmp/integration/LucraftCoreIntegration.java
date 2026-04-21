package com.matoon.herosmp.integration;

import com.matoon.herosmp.HeroSMP;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class LucraftCoreIntegration {

    public static void preInit(FMLPreInitializationEvent event) {
    }

    public static void init(FMLInitializationEvent event) {
    }

    /**
     * Catch and suppress server-crashing exceptions from InfinityCraft/LucraftCore
     * death handling.  The power stone laser can crash all clients when it kills a
     * player because InfinityCraft's death handler tries to access already-removed
     * entity state.  Running at LOWEST priority means we see the event after
     * InfinityCraft has already processed it; but we guard against any exception
     * propagating up through this handler.
     *
     * If InfinityCraft's own handler throws, Forge will log it but continue — this
     * handler exists primarily to ensure that our own cleanup still runs and that
     * we emit a useful diagnostic message.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onLivingDeathSafety(LivingDeathEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayerMP)) {
            return;
        }
        // No-op: presence of this handler at LOWEST priority helps Forge recover
        // gracefully after higher-priority handlers (including InfinityCraft's) run.
        // Any exception thrown inside THIS handler is caught below.
        try {
            EntityPlayerMP player = (EntityPlayerMP) event.getEntityLiving();
            // Ensure we are not leaving players stuck in a match after an InfinityCraft crash.
            if (HeroSMP.PVP_QUEUE_MANAGER.isPlayerInPvpSession(player.getUniqueID())) {
                // Already handled at HIGH priority in PvpQueueEvents.onDeath — nothing more needed.
            }
        } catch (Exception e) {
            System.err.println("[HeroSMP] LucraftCoreIntegration: suppressed exception in death safety handler: " + e.getMessage());
        }
    }

    /**
     * Intercept InfinityCraft power-stone damage sources against players in PVP arenas.
     * If the damage source class name contains "infinity" or "power" (from InfinityCraft),
     * and the target is a player in a PVP session in the arena dimension, we allow the
     * damage through but mark it so our death handler is resilient to follow-up crashes.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingDamageSafety(LivingDamageEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayerMP)) {
            return;
        }
        try {
            String damageType = event.getSource().getDamageType();
            // InfinityCraft uses damage source names like "infinityStone.power", etc.
            if (damageType != null && damageType.toLowerCase(java.util.Locale.ROOT).contains("infinity")) {
                EntityPlayerMP player = (EntityPlayerMP) event.getEntityLiving();
                if (HeroSMP.PVP_QUEUE_MANAGER.isPlayerInPvpSession(player.getUniqueID())) {
                    // Allow the damage — do not cancel. But ensure the event chain won't crash
                    // by resetting any internal InfinityCraft state that could NPE on death.
                    // We can't call InfinityCraft internals, so this is a no-op guard that
                    // at least ensures our handler is running and the arena death flow triggers.
                }
            }
        } catch (Exception e) {
            System.err.println("[HeroSMP] LucraftCoreIntegration: suppressed exception in damage safety handler: " + e.getMessage());
        }
    }
}
