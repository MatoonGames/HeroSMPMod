package com.matoon.herosmp;

import com.matoon.herosmp.events.ArrowEffectHandler;
import com.matoon.herosmp.hungergames.events.HungerGamesEvents;
import com.matoon.herosmp.integration.LucraftCoreIntegration;
import com.matoon.herosmp.mindstone.MindControlManager;
import com.matoon.herosmp.npc.PvpQueueEvents;
import com.matoon.herosmp.npc.command.CommandHeroNpc;
import com.matoon.herosmp.npc.command.CommandPvpMenu;
import com.matoon.herosmp.npc.command.CommandReturn;
import com.matoon.herosmp.network.ModNetwork;
import com.matoon.herosmp.registry.ModEntities;
import com.matoon.herosmp.registry.ModItems;
import com.matoon.herosmp.registry.ModPotions;
import com.matoon.herosmp.registry.ModSounds;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {
    public void preInit(FMLPreInitializationEvent event) {
        ModEntities.registerEntities();
        ModNetwork.init();
        MinecraftForge.EVENT_BUS.register(new ModSounds());
        MinecraftForge.EVENT_BUS.register(new ModItems.RegistrationHandler());
        MinecraftForge.EVENT_BUS.register(new ModPotions());
        MinecraftForge.EVENT_BUS.register(new PvpQueueEvents());
        MinecraftForge.EVENT_BUS.register(new HungerGamesEvents());
        MinecraftForge.EVENT_BUS.register(new LucraftCoreIntegration());
        MinecraftForge.EVENT_BUS.register(new ArrowEffectHandler());
        MinecraftForge.EVENT_BUS.register(new MindControlManager());
    }

    public void init(FMLInitializationEvent event) {
    }

    /**
     * Handles a Hunger Games music control packet on the physical client.
     * The common/server implementation is intentionally a no-op so dedicated servers
     * never have to load Minecraft's client-only audio classes.
     */
    public void handleHungerGamesMusic(boolean stop, String phase, String track) {
    }

    /** Dispatches packets whose effects use physical-client-only classes. */
    public void handleClientPacket(IMessage message) {
    }

    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandHeroNpc());
        event.registerServerCommand(new CommandReturn());
        event.registerServerCommand(new CommandPvpMenu());
    }
}
