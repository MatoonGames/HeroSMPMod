package com.matoon.herosmp.server;

import com.matoon.herosmp.CommonProxy;
import com.matoon.herosmp.HeroSMP; // Import HeroSMP here
import com.matoon.herosmp.level.LevelHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class ServerProxy extends CommonProxy {
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);

        // Server-specific initialization (like loading rewards)
        LevelHandler.loadLevelRewards(HeroSMP.config); // Loads level rewards from config
    }
}