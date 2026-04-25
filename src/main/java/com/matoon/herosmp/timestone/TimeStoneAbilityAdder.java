package com.matoon.herosmp.timestone;

import anvil.infinity.api.IAbilityAdder;
import anvil.infinity.api.StoneAbility;
import lucraft.mods.lucraftcore.infinity.EnumInfinityStone;
import lucraft.mods.lucraftcore.superpowers.abilities.Ability;
import net.minecraft.entity.EntityLivingBase;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers HeroSMP's custom Time Stone abilities with InfinityCraft's
 * ability extension API. This class is registered via
 * {@code AbilityAdderHandler.register(new TimeStoneAbilityAdder())} during
 * {@link com.matoon.herosmp.integration.LucraftCoreIntegration#init}.
 *
 * The four abilities added to the Time Stone are:
 * <ul>
 *   <li><b>Slow Time</b> — reduces server tickrate by 5 per press (min 1)</li>
 *   <li><b>Speed Time</b> — increases server tickrate by 5 per press (max 100)</li>
 *   <li><b>Reset Time</b> — instantly resets server tickrate to 20</li>
 *   <li><b>Time Unlocker</b> — toggle that keeps the player's client at 20 tps
 *       regardless of server tickrate, so they are unaffected by time changes</li>
 * </ul>
 */
public class TimeStoneAbilityAdder implements IAbilityAdder {

    @Override
    public List<StoneAbility> addStoneAbilities(EntityLivingBase entity, EnumInfinityStone stone) {
        List<StoneAbility> abilities = new ArrayList<>();

        if (stone == EnumInfinityStone.TIME) {
            abilities.add(new StoneAbility("herosmp:slow_time",     new AbilitySlowTime(entity)));
            abilities.add(new StoneAbility("herosmp:speed_time",    new AbilitySpeedTime(entity)));
            abilities.add(new StoneAbility("herosmp:reset_time",    new AbilityResetTime(entity)));
            abilities.add(new StoneAbility("herosmp:time_unlocker", new AbilityTimeUnlocker(entity)));
        }

        return abilities;
    }
}
