package com.matoon.herosmp.integration;

import lucraft.mods.lucraftcore.superpowers.SuperpowerHandler;
import lucraft.mods.lucraftcore.superpowers.abilities.Ability;
import lucraft.mods.lucraftcore.superpowers.abilities.supplier.AbilityContainer;
import lucraft.mods.lucraftcore.superpowers.abilities.supplier.AbilityContainerSuperpower;
import lucraft.mods.lucraftcore.utilities.items.ItemInjection;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.WorldServer;

/**
 * Gives a player the superpower inside a LucraftCore injection stack, exactly as if they had used
 * the syringe, at the power's maximum level. Shared by Hunger Games injection pickups and kits with
 * a superpower. The player's previous superpower (if any) is replaced; match exits already strip
 * powers gained in the arena.
 */
public final class SuperpowerGrant {

    private SuperpowerGrant() {}

    /**
     * @param headline chat prefix, e.g. "POWER INJECTED!" or "KIT POWER:"
     * @return whether the power was granted
     */
    public static boolean grant(EntityPlayerMP player, ItemStack injStack, String headline) {
        try {
            ItemInjection.Injection injection = ItemInjection.getInjection(injStack);
            if (injection == null) {
                System.err.println("[HeroSMP] grantSuperpower: null injection for " + injStack.getDisplayName());
                return false;
            }

            // Remove the current superpower first so the player never has two, and so LucraftCore's
            // hasSuperpower() guard inside inject() doesn't block the grant.
            if (SuperpowerHandler.hasSuperpower(player)) {
                SuperpowerHandler.removeSuperpower(player);
                SuperpowerHandler.syncToPlayer(player);
            }

            // inject() calls giveSuperpower() internally, fires OnGain ability events, and handles
            // capability setup: identical to right-click item use.
            injection.inject(player, injStack.copy());

            // Straight to the power's maximum level.
            try {
                AbilityContainer container = Ability.getAbilityContainer(Ability.EnumAbilityContext.SUPERPOWER, player);
                if (container instanceof AbilityContainerSuperpower) {
                    AbilityContainerSuperpower spContainer = (AbilityContainerSuperpower) container;
                    int maxLevel = SuperpowerHandler.getSuperpower(player) != null
                            ? SuperpowerHandler.getSuperpower(player).getMaxLevel() : 1;
                    if (maxLevel > 1) {
                        spContainer.setLevel(maxLevel);
                        spContainer.setXP(0);
                        SuperpowerHandler.syncToPlayer(player);
                    }
                }
            } catch (Exception levelEx) {
                System.err.println("[HeroSMP] grantSuperpower: could not set max level for "
                        + player.getName() + ": " + levelEx.getMessage());
            }

            player.sendMessage(new TextComponentString(TextFormatting.LIGHT_PURPLE + "" + TextFormatting.BOLD + headline + " "
                    + TextFormatting.RESET + TextFormatting.GOLD + injection.getDisplayName()));
            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0F, 0.5F);
            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0F, 1.2F);
            if (player.world instanceof WorldServer) {
                WorldServer ws = (WorldServer) player.world;
                ws.spawnParticle(EnumParticleTypes.SPELL_MOB, player.posX, player.posY + 1.0, player.posZ, 40, 0.4, 0.6, 0.4, 0.15);
                ws.spawnParticle(EnumParticleTypes.END_ROD, player.posX, player.posY + 1.0, player.posZ, 20, 0.3, 0.5, 0.3, 0.05);
            }
            return true;
        } catch (Exception e) {
            System.err.println("[HeroSMP] grantSuperpower: exception granting '"
                    + LucraftInjectionEntry.getLucraftId(injStack) + "' to " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }
}
