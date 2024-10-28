package com.matoon.herosmp;

import com.matoon.herosmp.level.LevelEventHandler;
import net.minecraftforge.common.MinecraftForge;

public class CommonProxy {

    public void init() {
        // Register common event handlers
        MinecraftForge.EVENT_BUS.register(new LevelEventHandler());
    }

    // Override on the ClientProxy to handle client-only code
    public void registerClientOnly() {}

    // Override on the ServerProxy to handle server-only code
    public void registerServerOnly() {}
}