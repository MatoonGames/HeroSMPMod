package com.matoon.herosmp.timestone;

import com.matoon.herosmp.HeroSMP;
import com.matoon.herosmp.network.ModNetwork;
import com.matoon.herosmp.network.PacketTimeStoneCharge;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the per-player Time Stone ability charge.
 *
 * <p>Charge is a value in [0.0, 1.0] where 1.0 = full and 0.0 = depleted.
 * It drains while the player's dimension runs at a non-normal tickrate and
 * recharges once the dimension returns to 20 tps.
 *
 * <p>Drain direction matters:
 * <ul>
 *   <li>Rate &lt; 20 (slowed): uses {@code slowTimeDrainMultiplier} config.</li>
 *   <li>Rate &gt; 20 (sped up): uses {@code speedTimeDrainMultiplier} config.</li>
 * </ul>
 * Both are further scaled by {@code pvpDrainMultiplier} inside PVP/HG matches.
 *
 * <p>When charge hits 0 the dimension's tickrate is immediately reset to normal.
 * Abilities stay locked until charge returns to 1.0.
 *
 * <p>Server-side state: {@link #charges} / {@link #depleted}.
 * Client-side mirror: {@link #clientCharge} / {@link #clientDepleted},
 * written by {@link PacketTimeStoneCharge}.
 */
public final class TimeStoneChargeManager {

    private TimeStoneChargeManager() {}

    // -------------------------------------------------------------------------
    // Server-side state
    // -------------------------------------------------------------------------

    /** UUID → charge value (0.0–1.0). Missing entry treated as 1.0 (full). */
    private static final Map<UUID, Float> charges = new HashMap<>();

    /**
     * UUID → true while charge is 0 and not yet fully restored.
     * Cleared when charge reaches 1.0.
     */
    private static final Map<UUID, Boolean> depleted = new HashMap<>();

    /**
     * Throttle: tracks how many ticks since the last packet was sent for
     * each player, so we don't flood the connection every tick.
     */
    private static final Map<UUID, Integer> ticksSinceSync = new HashMap<>();

    /** Maximum tickrate deviation from 20 (min=1, max=100 → max deviation = 80). */
    private static final float MAX_DEVIATION = 80.0f;

    /** How often (in ticks) to push charge updates to the client. */
    private static final int SYNC_INTERVAL = 4;

    // -------------------------------------------------------------------------
    // Client-side mirror (written by PacketTimeStoneCharge handler)
    // -------------------------------------------------------------------------

    private static float clientCharge = 1.0f;
    private static boolean clientDepleted = false;

    // -------------------------------------------------------------------------
    // Server-side API
    // -------------------------------------------------------------------------

    /**
     * Called once per server tick for each player who holds a Time Stone ability.
     * Drains or recharges this player's charge, resets the dimension rate if
     * just depleted, then sends a sync packet to the client as needed.
     */
    public static void tickPlayer(EntityPlayerMP player) {
        if (!isChargeActive(player)) return;

        UUID id = player.getUniqueID();
        float charge = charges.getOrDefault(id, 1.0f);
        boolean wasDepleted = depleted.getOrDefault(id, false);

        float dimRate  = TimeStoneDimensionManager.getRate(player.dimension);
        float rawDelta = dimRate - 20.0f;   // positive = sped up, negative = slowed

        if (rawDelta != 0.0f) {
            // Apply additional PVP/HG multiplier when inside a match.
            float pvpMult = isInMatch(player) ? HeroSMP.timeStonePvpDrainMultiplier : 1.0f;

            float rechargeSeconds = HeroSMP.timeStoneRechargeSeconds;
            float drainFactor;

            if (rawDelta < 0.0f) {
                // Slowing time: square-root of inverse ratio keeps rate=1 significantly heavier
                // than mild slows without the 20x spike of a raw inverse. sqrt(20/rate):
                //   rate=1  → 4.47x,  rate=5 → 2.0x,  rate=10 → 1.41x,  rate=15 → 1.15x
                drainFactor = (float) Math.sqrt(20.0f / dimRate) * HeroSMP.timeStoneSlowDrainMultiplier;
            } else {
                // Speeding up time: linear by deviation, same as before.
                float deviation = Math.abs(rawDelta);
                drainFactor = (deviation / MAX_DEVIATION) * HeroSMP.timeStoneSpeedDrainMultiplier;
            }

            // Base drain unit: 1/(rechargeSeconds * 20 tps) = full drain in rechargeSeconds at factor=1.
            float drainPerTick = drainFactor / (rechargeSeconds * 20.0f) * pvpMult;

            charge = Math.max(0.0f, charge - drainPerTick);

            // On the tick charge first hits 0: reset the dimension, play glass break, force-sync.
            if (charge == 0.0f && !wasDepleted) {
                depleted.put(id, true);
                charges.put(id, 0.0f);
                TimeStoneDimensionManager.resetRate(player, player.dimension);
                player.world.playSound(null, player.posX, player.posY, player.posZ,
                        SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 0.8f);
                ModNetwork.CHANNEL.sendTo(new PacketTimeStoneCharge(0.0f, true), player);
                ticksSinceSync.put(id, 0);
                return;
            }
        } else {
            // Recharge: takes rechargeSeconds to go from 0 → full.
            float rechargeSeconds = HeroSMP.timeStoneRechargeSeconds;
            float rechargePerTick = 1.0f / (rechargeSeconds * 20.0f);
            charge = Math.min(1.0f, charge + rechargePerTick);
            if (charge >= 1.0f) {
                charge = 1.0f;
                depleted.remove(id);
            }
        }

        charges.put(id, charge);

        // Routine sync on interval, or immediately when depletion state changes.
        boolean nowDepleted = depleted.getOrDefault(id, false);
        int ticks = ticksSinceSync.getOrDefault(id, SYNC_INTERVAL);
        if (ticks >= SYNC_INTERVAL || nowDepleted != wasDepleted) {
            ModNetwork.CHANNEL.sendTo(new PacketTimeStoneCharge(charge, nowDepleted), player);
            ticksSinceSync.put(id, 0);
        } else {
            ticksSinceSync.put(id, ticks + 1);
        }
    }

    /** Returns true if the charge mechanic should apply to this player right now. */
    public static boolean isChargeActive(EntityPlayerMP player) {
        if (isInMatch(player)) return true;
        return HeroSMP.timeStoneChargeOutsideMatches;
    }

    /** Returns true if the player is currently in a PVP or Hunger Games match/queue. */
    public static boolean isInMatch(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        return HeroSMP.PVP_QUEUE_MANAGER.isPlayerInPvpSession(id)
            || HeroSMP.HUNGER_GAMES_MANAGER.isPlayerInMatchOrQueue(id);
    }

    /** Returns true if this player's charge is currently depleted (locked). */
    public static boolean isDepleted(UUID playerId) {
        return depleted.getOrDefault(playerId, false);
    }

    /** Returns this player's current charge (0.0–1.0). 1.0 if no entry exists. */
    public static float getCharge(UUID playerId) {
        return charges.getOrDefault(playerId, 1.0f);
    }

    /** Remove all state for a player who has logged out or lost their Time Stone. */
    public static void onPlayerLeft(UUID playerId) {
        charges.remove(playerId);
        depleted.remove(playerId);
        ticksSinceSync.remove(playerId);
    }

    // -------------------------------------------------------------------------
    // Client-side API (called from PacketTimeStoneCharge handler and drawIcon)
    // -------------------------------------------------------------------------

    /** Called by PacketTimeStoneCharge handler on the client thread. */
    @SideOnly(Side.CLIENT)
    public static void setClientCharge(float charge, boolean isDepeted) {
        clientCharge = charge;
        clientDepleted = isDepeted;
    }

    /** Returns the last synced charge value for the local player's icon rendering. */
    @SideOnly(Side.CLIENT)
    public static float getClientCharge() {
        return clientCharge;
    }

    /** Returns whether the local player's charge is depleted (for greying out the icon). */
    @SideOnly(Side.CLIENT)
    public static boolean isClientDepleted() {
        return clientDepleted;
    }
}
