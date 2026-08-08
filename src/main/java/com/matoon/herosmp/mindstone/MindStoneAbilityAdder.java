package com.matoon.herosmp.mindstone;

import anvil.infinity.api.IAbilityAdder;
import anvil.infinity.api.StoneAbility;
import lucraft.mods.lucraftcore.infinity.EnumInfinityStone;
import net.minecraft.entity.EntityLivingBase;
import java.util.ArrayList;
import java.util.List;

/** Adds Mind Control while leaving InfinityCraft's normal Mind Stone powers intact. */
public class MindStoneAbilityAdder implements IAbilityAdder {
    @Override public List<StoneAbility> addStoneAbilities(EntityLivingBase entity, EnumInfinityStone stone) {
        List<StoneAbility> result = new ArrayList<>();
        if (stone == EnumInfinityStone.MIND) result.add(new StoneAbility("herosmp:mind_control", new AbilityMindControl(entity)));
        return result;
    }
}
