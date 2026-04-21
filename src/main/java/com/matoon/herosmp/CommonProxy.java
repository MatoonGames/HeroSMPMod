package com.matoon.herosmp;

import com.matoon.herosmp.hungergames.events.HungerGamesEvents;
import com.matoon.herosmp.integration.LucraftCoreIntegration;
import com.matoon.herosmp.npc.PvpQueueEvents;
import com.matoon.herosmp.npc.command.CommandHeroNpc;
import com.matoon.herosmp.npc.command.CommandPvpMenu;
import com.matoon.herosmp.npc.command.CommandReturn;
import com.matoon.herosmp.network.ModNetwork;
import com.matoon.herosmp.registry.ModEntities;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {
    public void preInit(FMLPreInitializationEvent event) {
        ModEntities.registerEntities();
        ModNetwork.init();
        MinecraftForge.EVENT_BUS.register(new PvpQueueEvents());
        MinecraftForge.EVENT_BUS.register(new HungerGamesEvents());
        MinecraftForge.EVENT_BUS.register(new LucraftCoreIntegration());
    }

    public void init(FMLInitializationEvent event) {
    }

    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandHeroNpc());
        event.registerServerCommand(new CommandReturn());
        event.registerServerCommand(new CommandPvpMenu());
    }
}
