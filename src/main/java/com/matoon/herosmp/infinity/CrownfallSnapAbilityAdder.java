package com.matoon.herosmp.infinity;

import anvil.infinity.api.IAbilityAdder;
import anvil.infinity.api.StoneAbility;
import lucraft.mods.lucraftcore.infinity.EnumInfinityStone;
import net.minecraft.entity.EntityLivingBase;

import java.util.ArrayList;
import java.util.List;

/** Replaces InfinityCraft's existing "snap" map entry with an action-aware subclass. */
public class CrownfallSnapAbilityAdder implements IAbilityAdder {
    @Override
    public List<StoneAbility> addStoneAbilities(EntityLivingBase entity, EnumInfinityStone stone) {
        List<StoneAbility> abilities = new ArrayList<StoneAbility>();
        if (stone == EnumInfinityStone.SOUL) {
            // ItemSoulStone inserts its native entry before extension adders run. Using
            // the same key intentionally replaces that one without adding a second icon.
            abilities.add(new StoneAbility("snap", new AbilityCrownfallSnap(entity)));
        }
        return abilities;
    }
}
