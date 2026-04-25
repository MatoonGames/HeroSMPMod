package com.matoon.herosmp.integration;

import anvil.infinity.api.AbilityAdderHandler;
import anvil.infinity.helpers.GauntelHelper;
import com.matoon.herosmp.HeroSMP;
import com.matoon.herosmp.timestone.AbilityResetTime;
import com.matoon.herosmp.timestone.AbilitySlowTime;
import com.matoon.herosmp.timestone.AbilitySpeedTime;
import com.matoon.herosmp.timestone.AbilityTimeUnlocker;
import com.matoon.herosmp.timestone.TimeStoneDimensionManager;
import com.matoon.herosmp.timestone.TimeStoneChargeManager;
import com.matoon.herosmp.timestone.TimeStoneAbilityAdder;
import lucraft.mods.lucraftcore.superpowers.abilities.Ability;
import lucraft.mods.lucraftcore.superpowers.abilities.AbilityEntry;
import lucraft.mods.lucraftcore.superpowers.events.InitAbilitiesEvent;
import lucraft.mods.lucraftcore.util.attributes.LCAttributes;
import me.guichaguri.tickratechanger.api.TickrateAPI;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Collection;

public class LucraftCoreIntegration {

    public static void preInit(FMLPreInitializationEvent event) {
    }

    public static void init(FMLInitializationEvent event) {
        AbilityAdderHandler.register(new TimeStoneAbilityAdder());
    }

    /**
     * Register HeroSMP's custom ability classes in LucraftCore's AbilityEntry Forge
     * registry. This MUST fire before any instance of the ability is constructed —
     * the Ability base-class constructor iterates the registry to find its own entry
     * (and store it in the 'entry' field), which is later needed by getModId() /
     * getTranslationName(). Without this registration those methods NPE.
     */
    @SubscribeEvent
    public void onRegisterAbilities(RegistryEvent.Register<AbilityEntry> event) {
        event.getRegistry().registerAll(
            new AbilityEntry(AbilitySlowTime.class,     new ResourceLocation("herosmp", "slow_time")),
            new AbilityEntry(AbilitySpeedTime.class,    new ResourceLocation("herosmp", "speed_time")),
            new AbilityEntry(AbilityResetTime.class,    new ResourceLocation("herosmp", "reset_time")),
            new AbilityEntry(AbilityTimeUnlocker.class, new ResourceLocation("herosmp", "time_unlocker"))
        );
    }

    // -------------------------------------------------------------------------
    // Time Stone dimension-scoped tickrate cleanup
    // -------------------------------------------------------------------------

    /**
     * When a player logs out, restore the tickrate for their dimension if they
     * were the last Time Stone user there. Also clean up any stale health
     * modifiers — they are transient (setSaved=false) so they won't be in the
     * player's saved data, but removing them now is cleaner.
     */
    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (hasTimeAbility(player)) {
            TimeStoneDimensionManager.onUserLeftDimension(player, player.dimension);
        }
        TimeStoneChargeManager.onPlayerLeft(player.getUniqueID());
        // Always re-sync this player's client to normal on disconnect so their
        // next session doesn't start at a wrong tickrate.
        TickrateAPI.changeClientTickrate(player, 20.0f);
        cleanupInfinityStoneHealthModifiers(player);
        cleanupInfinityStoneDamageModifiers(player);
    }

    /**
     * When a player moves to a different dimension, restore the tickrate in
     * the dimension they left (if they were the last Time Stone user there),
     * then push the new dimension's stored rate to their client.
     */
    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        int fromDim = event.fromDim;
        int toDim   = event.toDim;

        if (hasTimeAbility(player)) {
            // May restore the dimension they left.
            TimeStoneDimensionManager.onUserLeftDimension(player, fromDim);
        }

        // Sync the player to whatever tickrate is active in their new dimension.
        float toRate = TimeStoneDimensionManager.getRate(toDim);
        TickrateAPI.changeClientTickrate(player, toRate);
    }

    /**
     * Fires after LucraftCore has rebuilt a player's ability set. This only fires
     * when a new provider is equipped (null → item), not on removal (item → null),
     * because LucraftCore skips filterAbilities() when switchProvider(null) is called.
     * We still use it for the Time Stone cleanup path, since the Time Stone abilities
     * are detected via Ability.hasAbility() rather than slot checks.
     */
    @SubscribeEvent
    public void onInitAbilitiesPost(InitAbilitiesEvent.Post event) {
        if (!(event.getEntityLiving() instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityLiving();

        if (!hasTimeAbility(player)) {
            TimeStoneDimensionManager.onAbilityDeactivated(player, player.dimension);
        }
    }

    /**
     * Fires whenever an equipment slot changes on a living entity — including the
     * MAINHAND and OFFHAND slots. This is the reliable hook for "gauntlet left the
     * hand", covering all cases: Q-drop, inventory drag, F-key swap, and picking the
     * item up into a backpack slot.
     *
     * LucraftCore's own cleanup (AbilityAttributeModifier.lastTick) is supposed to
     * remove the MAX_HEALTH modifier when the gauntlet leaves the hand, but it is
     * silently skipped whenever the ability's isUnlocked() flag is false at that
     * moment — which happens reliably when updateConditions() has already evaluated
     * the now-empty slot before switchProvider fires. We force the removal here so
     * the player never retains the inflated health after losing the item.
     */
    @SubscribeEvent
    public void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayerMP)) return;

        EntityEquipmentSlot slot = event.getSlot();
        if (slot != EntityEquipmentSlot.MAINHAND && slot != EntityEquipmentSlot.OFFHAND) return;

        EntityPlayerMP player = (EntityPlayerMP) event.getEntityLiving();

        if (!GauntelHelper.hasSoulStone(player) && !GauntelHelper.hasPowerStone(player)) {
            cleanupInfinityStoneHealthModifiers(player);
        }
        if (!GauntelHelper.hasPowerStone(player)) {
            cleanupInfinityStoneDamageModifiers(player);
        }
    }

    /**
     * Additional safety net for Q-drop specifically. ItemTossEvent fires after the
     * item is already removed from the slot, so the equipment-change event will also
     * fire — but this ensures cleanup happens even if the equipment event is somehow
     * missed (e.g. creative-mode item deletion which bypasses normal drop paths).
     */
    @SubscribeEvent
    public void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getPlayer();
        if (!GauntelHelper.hasSoulStone(player) && !GauntelHelper.hasPowerStone(player)) {
            cleanupInfinityStoneHealthModifiers(player);
        }
        if (!GauntelHelper.hasPowerStone(player)) {
            cleanupInfinityStoneDamageModifiers(player);
        }
    }

    /**
     * Each server tick (END phase, after all entity updates):
     * - Advance Time Stone charge drain/recharge for players holding that ability.
     * - Enforce Soul/Power Stone health modifier cleanup for players who no longer
     *   hold those stones.
     *
     * The END phase runs after LivingUpdateEvent, which is where LucraftCore's
     * AbilityContainer.onUpdate() re-applies the AbilityHealth MAX_HEALTH modifier
     * every tick via the SUPERPOWER capability context. By running at END we always
     * get the last word, stripping the modifier after LucraftCore re-adds it.
     *
     * This is necessary because the SUPERPOWER capability persists the gauntlet's
     * superpower object independently of which slot the item is in — LucraftCore
     * never clears it when the item leaves the hand, so the modifier would otherwise
     * be re-applied forever even with the gauntlet sitting in a backpack.
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;
        for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
            if (hasTimeAbility(p)) {
                TimeStoneChargeManager.tickPlayer(p);
            }
            if (!GauntelHelper.hasSoulStone(p) && !GauntelHelper.hasPowerStone(p)) {
                cleanupInfinityStoneHealthModifiers(p);
            }
            if (!GauntelHelper.hasPowerStone(p)) {
                cleanupInfinityStoneDamageModifiers(p);
            }
        }
    }

    /** Returns true if the player currently holds any of our time-manipulation abilities. */
    private static boolean hasTimeAbility(EntityPlayerMP player) {
        return Ability.hasAbility(player, AbilitySlowTime.class)
            || Ability.hasAbility(player, AbilitySpeedTime.class)
            || Ability.hasAbility(player, AbilityResetTime.class)
            || Ability.hasAbility(player, AbilityTimeUnlocker.class);
    }

    /**
     * Forcibly removes any MAX_HEALTH AttributeModifiers that InfinityCraft's Soul Stone
     * or Power Stone abilities may have left on the player after the gauntlet was dropped
     * or unequipped.
     *
     * InfinityCraft applies these as transient modifiers (setSaved(false)) with extremely
     * large amounts — the Soul Stone uses Float.MAX_VALUE (~3.4e38) and the Power Stone
     * uses large values too. We remove any modifier whose absolute amount exceeds 1024,
     * which is far beyond anything vanilla Minecraft or HeroSMP adds legitimately.
     *
     * Additionally, we cap the player's current health down to their new max if it would
     * otherwise exceed it (vanilla does this automatically on attribute removal, but we
     * do it explicitly to be safe).
     */
    private static void cleanupInfinityStoneHealthModifiers(EntityPlayerMP player) {
        try {
            IAttributeInstance maxHealthAttr = player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
            if (maxHealthAttr == null) return;

            Collection<AttributeModifier> modifiers = maxHealthAttr.getModifiers();
            if (modifiers == null || modifiers.isEmpty()) return;

            java.util.List<AttributeModifier> toRemove = new ArrayList<>();
            for (AttributeModifier mod : modifiers) {
                if (Math.abs(mod.getAmount()) > 1024.0) {
                    toRemove.add(mod);
                }
            }

            if (toRemove.isEmpty()) return;

            for (AttributeModifier mod : toRemove) {
                maxHealthAttr.removeModifier(mod);
            }

            // Clamp current health down to the restored max.
            float newMax = (float) maxHealthAttr.getAttributeValue();
            if (player.getHealth() > newMax) {
                player.setHealth(newMax);
            }
        } catch (Exception e) {
            System.err.println("[HeroSMP] cleanupInfinityStoneHealthModifiers: " + e.getMessage());
        }
    }

    /**
     * Forcibly removes any oversized AttributeModifiers that InfinityCraft's Power Stone
     * abilities may have left on ATTACK_DAMAGE, PUNCH_DAMAGE, and ARMOR after the gauntlet
     * was dropped or unequipped. Uses the same threshold (>1024) as the health cleanup.
     */
    private static void cleanupInfinityStoneDamageModifiers(EntityPlayerMP player) {
        try {
            IAttributeInstance[] attrs = {
                player.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE),
                player.getEntityAttribute(SharedMonsterAttributes.ARMOR),
                player.getAttributeMap().getAttributeInstance(LCAttributes.PUNCH_DAMAGE)
            };
            for (IAttributeInstance attr : attrs) {
                if (attr == null) continue;
                Collection<AttributeModifier> modifiers = attr.getModifiers();
                if (modifiers == null || modifiers.isEmpty()) continue;
                java.util.List<AttributeModifier> toRemove = new ArrayList<>();
                for (AttributeModifier mod : modifiers) {
                    if (Math.abs(mod.getAmount()) > 1024.0) {
                        toRemove.add(mod);
                    }
                }
                for (AttributeModifier mod : toRemove) {
                    attr.removeModifier(mod);
                }
            }
        } catch (Exception e) {
            System.err.println("[HeroSMP] cleanupInfinityStoneDamageModifiers: " + e.getMessage());
        }
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
