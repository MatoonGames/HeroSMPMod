package com.matoon.herosmp.integration;

import com.matoon.herosmp.HeroSMP;
import lucraft.mods.lucraftcore.superpowers.abilities.Ability;
import lucraft.mods.lucraftcore.superpowers.abilities.AbilityFlight;
import lucraft.mods.lucraftcore.superpowers.abilities.AbilityInvisibility;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketChat;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Applies independent, rechargeable flight and invisibility charges during Hero PVP matches. */
public final class PvpMobilityChargeManager {
    private static final Map<UUID, ChargeState> STATES = new HashMap<UUID, ChargeState>();

    private PvpMobilityChargeManager() {
    }

    public static void tickPlayer(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        if (!HeroSMP.enablePvpMobilityCharges
                || !HeroSMP.PVP_QUEUE_MANAGER.isPlayerInActivePvpMatch(id)) {
            STATES.remove(id);
            return;
        }

        List<Ability> abilities;
        try {
            abilities = Ability.getAbilities(player);
        } catch (Exception ignored) {
            return;
        }

        boolean flightEnabled = false;
        boolean invisibilityEnabled = false;
        for (Ability ability : abilities) {
            if (ability instanceof AbilityFlight && ability.isEnabled()) flightEnabled = true;
            if (ability instanceof AbilityInvisibility && ability.isEnabled()) invisibilityEnabled = true;
        }

        ChargeState state = STATES.get(id);
        if (state == null) {
            state = new ChargeState();
            STATES.put(id, state);
        }

        tickCharge(state.flight, flightEnabled);
        tickCharge(state.invisibility, invisibilityEnabled);

        if (state.flight.exhausted) disableAbilities(abilities, AbilityFlight.class);
        if (state.invisibility.exhausted) disableAbilities(abilities, AbilityInvisibility.class);

        if ((flightEnabled || invisibilityEnabled || state.flight.exhausted || state.invisibility.exhausted)
                && player.ticksExisted % 20 == 0) {
            String message = formatCharge("Flight", state.flight);
            if (flightEnabled || state.flight.exhausted) {
                if (invisibilityEnabled || state.invisibility.exhausted) {
                    message += TextFormatting.DARK_GRAY + " | " + formatCharge("Invisibility", state.invisibility);
                }
            } else {
                message = formatCharge("Invisibility", state.invisibility);
            }
            player.connection.sendPacket(new SPacketChat(new TextComponentString(message), ChatType.GAME_INFO));
        }
    }

    private static void tickCharge(Charge charge, boolean enabled) {
        if (enabled && !charge.exhausted) {
            charge.value = Math.max(0.0F, charge.value - 1.0F / (HeroSMP.pvpMobilityChargeSeconds * 20.0F));
            if (charge.value <= 0.0F) charge.exhausted = true;
        } else if (!enabled) {
            charge.value = Math.min(1.0F, charge.value + 1.0F / (HeroSMP.pvpMobilityRechargeSeconds * 20.0F));
            if (charge.value >= 1.0F) charge.exhausted = false;
        }
    }

    private static void disableAbilities(List<Ability> abilities, Class<? extends Ability> type) {
        for (Ability ability : abilities) {
            if (type.isInstance(ability) && ability.isEnabled()) ability.setEnabled(false);
        }
    }

    private static String formatCharge(String label, Charge charge) {
        int percent = Math.round(charge.value * 100.0F);
        TextFormatting color = charge.exhausted ? TextFormatting.RED
                : percent <= 25 ? TextFormatting.GOLD : TextFormatting.AQUA;
        return TextFormatting.GRAY + label + ": " + color + percent + "%";
    }

    public static void onPlayerLeft(UUID playerId) {
        STATES.remove(playerId);
    }

    private static final class ChargeState {
        private final Charge flight = new Charge();
        private final Charge invisibility = new Charge();
    }

    private static final class Charge {
        private float value = 1.0F;
        private boolean exhausted;
    }
}
