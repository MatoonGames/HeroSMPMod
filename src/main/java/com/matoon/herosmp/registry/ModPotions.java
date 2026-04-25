package com.matoon.herosmp.registry;

import com.matoon.herosmp.potion.PotionHealthCap;
import com.matoon.herosmp.potion.PotionLifeLink;
import com.matoon.herosmp.potion.PotionSupeVirus;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.potion.PotionType;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ModPotions {

    // --- Effects ---
    public static final PotionHealthCap HEALTH_CAP = new PotionHealthCap();
    public static final PotionLifeLink  LIFE_LINK   = new PotionLifeLink();
    public static final PotionSupeVirus SUPE_VIRUS  = new PotionSupeVirus();

    // --- PotionType (links the effect to the three bottle variants) ---
    // 3600 ticks = 3 minutes
    public static PotionType HEALTH_CAP_TYPE;

    @SubscribeEvent
    public void onRegisterPotions(RegistryEvent.Register<Potion> event) {
        event.getRegistry().register(HEALTH_CAP);
        event.getRegistry().register(LIFE_LINK);
        event.getRegistry().register(SUPE_VIRUS);
    }

    @SubscribeEvent
    public void onRegisterPotionTypes(RegistryEvent.Register<PotionType> event) {
        HEALTH_CAP_TYPE = new PotionType("herosmp.health_cap",
                new PotionEffect(HEALTH_CAP, 3600, 0));
        HEALTH_CAP_TYPE.setRegistryName("herosmp", "health_cap");
        event.getRegistry().register(HEALTH_CAP_TYPE);
        // Life Link and Supe Virus are applied directly via PotionEffect, not via bottles.
        // No PotionType registration needed for them.
    }

    /**
     * Enforces the health cap every tick on any living entity that has the effect.
     * This is the primary enforcement — PotionHealthCap.performEffect() is a fallback.
     */
    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (event.getEntityLiving().isPotionActive(HEALTH_CAP)) {
            PotionHealthCap.clampHealth(event.getEntityLiving());
        }
    }
}
