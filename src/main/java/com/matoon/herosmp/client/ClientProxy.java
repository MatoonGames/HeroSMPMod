package com.matoon.herosmp.client;

import com.matoon.herosmp.CommonProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.common.MinecraftForge;

public class ClientProxy extends CommonProxy {

    @Override
    public void registerClientOnly() {
        // Client-only registration such as GUIs
        registerGUIs();
    }

    private void registerGUIs() {
        // Initialize and register the DisconnectedScreen or other client GUIs
        // Replace with actual GUI setup code as needed
        System.out.println("Initializing client GUIs like DisconnectedScreen");
    }

    public void registerRenderers() {
        MinecraftForge.EVENT_BUS.register(new MultiplayerScoreboardHandler());
    }

}