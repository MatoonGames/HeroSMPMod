package com.matoon.herosmp.infinity;

import anvil.infinity.abilities.AbilitySnap;
import com.matoon.herosmp.HeroSMP;
import com.matoon.herosmp.integration.LucraftCoreIntegration;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Exact Snap action hook used by HeroSMP.
 *
 * Outside Crownfall this delegates completely to InfinityCraft. During an active
 * Crownfall round, a valid six-Stone snap ends the match without invoking
 * InfinityCraft's world-wide dusting implementation.
 */
public class AbilityCrownfallSnap extends AbilitySnap {
    public AbilityCrownfallSnap(EntityLivingBase entity) {
        super(entity);
    }

    @Override
    public boolean action() {
        if (entity instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) entity;
            if (HeroSMP.PVP_QUEUE_MANAGER.handleCrownfallSnap(player)) {
                LucraftCoreIntegration.handleDirectCrownfallSnapEffects(player);
                return true;
            }
        }
        return super.action();
    }
}
