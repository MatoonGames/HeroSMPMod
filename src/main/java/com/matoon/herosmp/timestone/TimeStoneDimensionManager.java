package com.matoon.herosmp.timestone;

import me.guichaguri.tickratechanger.api.TickrateAPI;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages per-dimension tickrate changes made by the Time Stone abilities.
 *
 * Each dimension that has an active tickrate override stores its rate in
 * {@link #dimRates}. When the last Time Stone user leaves that dimension (or
 * loses/disables their abilities), the dimension's rate is cleared and all
 * players still in it are synced back to 20 tps.
 *
 * The global server tickrate is ONLY changed for the dimension the ability
 * user is currently in; players in other dimensions are unaffected.
 */
public final class TimeStoneDimensionManager {

    private TimeStoneDimensionManager() {}

    /**
     * Dimension ID → overridden tickrate. Only present while at least one
     * Time Stone user in that dimension has applied a non-20 rate.
     */
    private static final Map<Integer, Float> dimRates = new HashMap<>();

    private static final float NORMAL_RATE = 20.0f;

    // -------------------------------------------------------------------------
    // Called by abilities
    // -------------------------------------------------------------------------

    /**
     * Apply a new tickrate to {@code dimensionId}, broadcast it to every player
     * in that dimension, and send an action-bar confirmation to the acting player.
     *
     * @param actingPlayer the player who triggered the ability
     * @param dimensionId  dimension to affect
     * @param newRate      clamped target tickrate
     */
    public static void applyRate(EntityPlayerMP actingPlayer, int dimensionId, float newRate) {
        dimRates.put(dimensionId, newRate);
        broadcastToDimension(dimensionId, newRate);
        sendActionBar(actingPlayer, newRate);
    }

    /**
     * Reset the tickrate for {@code dimensionId} back to 20, broadcast it to
     * every player in that dimension, and send an action-bar confirmation to
     * the acting player.
     *
     * @param actingPlayer the player who triggered the ability
     * @param dimensionId  dimension to reset
     */
    public static void resetRate(EntityPlayerMP actingPlayer, int dimensionId) {
        dimRates.remove(dimensionId);
        broadcastToDimension(dimensionId, NORMAL_RATE);
        sendActionBar(actingPlayer, NORMAL_RATE);
    }

    /**
     * Returns the currently stored rate for a dimension, or 20 if none is set.
     */
    public static float getRate(int dimensionId) {
        return dimRates.getOrDefault(dimensionId, NORMAL_RATE);
    }

    // -------------------------------------------------------------------------
    // Called on cleanup events (logout / dimension change / ability lost)
    // -------------------------------------------------------------------------

    /**
     * Called when a Time Stone ability user leaves a dimension or logs out.
     * Checks whether any other Time Stone user remains in that dimension. If
     * none remain, the stored rate is cleared and all players in the dimension
     * are synced back to 20.
     *
     * @param leavingPlayer  the player who left (already removed from world
     *                       player list, so not iterated below)
     * @param dimensionId    the dimension they were in
     */
    public static void onUserLeftDimension(EntityPlayerMP leavingPlayer, int dimensionId) {
        if (!dimRates.containsKey(dimensionId)) return;

        // If any remaining player in the dimension still has an active time ability,
        // leave the rate alone — they are still controlling it.
        if (hasActiveTimeAbilityInDimension(dimensionId, leavingPlayer)) return;

        // Nobody left with the ability in this dimension — restore normal speed.
        dimRates.remove(dimensionId);
        broadcastToDimension(dimensionId, NORMAL_RATE);
    }

    /**
     * Called when the active player disables/loses the ability while staying in
     * the same dimension. Same logic as {@link #onUserLeftDimension}.
     */
    public static void onAbilityDeactivated(EntityPlayerMP player, int dimensionId) {
        if (!dimRates.containsKey(dimensionId)) return;
        if (hasActiveTimeAbilityInDimension(dimensionId, player)) return;
        dimRates.remove(dimensionId);
        broadcastToDimension(dimensionId, NORMAL_RATE);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Send the new tickrate only to players inside {@code dimensionId}.
     * TickrateChanger's per-player changeClientTickrate sends a packet to each
     * player's connection individually.
     */
    static void broadcastToDimension(int dimensionId, float rate) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;

        // TickrateChanger's server tickrate is global — there is no per-dimension server
        // timer. We intentionally do NOT call changeServerTickrate() here because doing so
        // would affect ALL dimensions' server loops, not just dimensionId.
        //
        // Instead we rely entirely on per-player client packets: players inside the target
        // dimension receive the new rate, everyone else keeps their own dimension's rate.
        // The server loop continues running at its existing rate; only client rendering
        // and interpolation are affected.
        for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
            if (p.dimension == dimensionId) {
                TickrateAPI.changeClientTickrate(p, rate);
            } else {
                // Keep players in other dimensions at their dimension's stored rate.
                float otherRate = dimRates.getOrDefault(p.dimension, NORMAL_RATE);
                TickrateAPI.changeClientTickrate(p, otherRate);
            }
        }
    }

    /** Show "Time Speed: Xх" above the hotbar for the acting player only. */
    static void sendActionBar(EntityPlayerMP player, float rate) {
        String display;
        if (rate == NORMAL_RATE) {
            display = TextFormatting.GREEN + "Time Speed: Normal";
        } else if (rate < NORMAL_RATE) {
            display = TextFormatting.AQUA + "Time Speed: " + formatRate(rate) + "x";
        } else {
            display = TextFormatting.YELLOW + "Time Speed: " + formatRate(rate) + "x";
        }

        net.minecraft.network.play.server.SPacketTitle packet = new net.minecraft.network.play.server.SPacketTitle(
                net.minecraft.network.play.server.SPacketTitle.Type.ACTIONBAR,
                new TextComponentString(display),
                -1, 40, 20   // fadeIn=instant, stay=2s, fadeOut=1s
        );
        player.connection.sendPacket(packet);
    }

    private static String formatRate(float rate) {
        // Show as a clean decimal (e.g. "0.5x", "2x", "10x").
        if (rate == Math.floor(rate)) {
            return String.valueOf((int) rate);
        }
        return String.format("%.1f", rate);
    }

    /**
     * Returns true if any player in {@code dimensionId} other than
     * {@code exclude} currently has an active time-manipulation ability
     * (Slow or Speed — not Reset/Unlocker, which are one-shot or separate).
     *
     * For simplicity this checks whether any player in the dimension holds a
     * Time Stone superpower. If so, we assume they may still be using the ability
     * and leave the rate intact.
     */
    private static boolean hasActiveTimeAbilityInDimension(int dimensionId, EntityPlayer exclude) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return false;

        for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
            if (p == exclude) continue;
            if (p.dimension != dimensionId) continue;
            // Check if this player has any of our time abilities (i.e. they have the Time Stone).
            if (lucraft.mods.lucraftcore.superpowers.abilities.Ability.hasAbility(p, AbilitySlowTime.class)
             || lucraft.mods.lucraftcore.superpowers.abilities.Ability.hasAbility(p, AbilitySpeedTime.class)) {
                return true;
            }
        }
        return false;
    }
}
