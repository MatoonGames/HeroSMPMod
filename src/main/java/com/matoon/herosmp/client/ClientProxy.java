package com.matoon.herosmp.client;

import com.matoon.herosmp.CommonProxy;
import com.matoon.herosmp.events.EventHandler;
import com.matoon.herosmp.registry.ModEntities;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        ModEntities.registerRenderers();
        MinecraftForge.EVENT_BUS.register(new EventHandler());
    }
}
