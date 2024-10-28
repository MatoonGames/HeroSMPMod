package com.matoon.herosmp.server;

import com.matoon.herosmp.CommonProxy;
import com.matoon.herosmp.HeroSMP;
import com.matoon.herosmp.level.LevelHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;

public class ServerProxy extends CommonProxy {

    @Override
    public void registerServerOnly() {
        // Server-only logic, if needed for further customization
        System.out.println("Server-specific initialization.");
        LevelHandler.loadLevelRewards(HeroSMP.config); // Load rewards on the server side
    }
}