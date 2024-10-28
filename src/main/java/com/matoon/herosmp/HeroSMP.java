package com.matoon.herosmp;

<<<<<<< HEAD
import com.matoon.herosmp.client.ClientProxy;
import com.matoon.herosmp.server.ServerProxy;
import com.matoon.herosmp.level.LevelHandler;
=======
import com.matoon.herosmp.events.EventHandler;
>>>>>>> parent of 9f6120d (Proxies and Leveling System)
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
<<<<<<< HEAD
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.common.config.Configuration;
=======
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import java.io.File;
>>>>>>> parent of 9f6120d (Proxies and Leveling System)

@Mod(modid = HeroSMP.MODID, name = HeroSMP.NAME, version = HeroSMP.VERSION)
public class HeroSMP {

    public static final String MODID = "herosmp";
    public static final String NAME = "Hero SMP";
    public static final String VERSION = "1.0";

<<<<<<< HEAD
    @SidedProxy(clientSide = "com.matoon.herosmp.client.ClientProxy", serverSide = "com.matoon.herosmp.server.ServerProxy")
    public static CommonProxy proxy;
=======
    // Config fields
    public static Configuration config;
    public static boolean enableGUI = true;  // Default value for GUI enabled
    public static boolean guiMultiplayerOnly = true;  // Default: multiplayer-only
    public static boolean enableScoreboard = true;  // New: Enable/disable scoreboard
>>>>>>> parent of 9f6120d (Proxies and Leveling System)

    @Mod.Instance
    public static HeroSMP instance;

    // Configuration variables
    public static boolean enableScoreboard = true;
    public static boolean enableGUI = true;
    public static boolean guiMultiplayerOnly = true;
    public static Configuration config;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        System.out.println(NAME + " is loading (preInit phase).");

        // Initialize configuration
        loadConfig(event);

<<<<<<< HEAD
        // Initialize common elements (event handlers, registries, etc.)
        proxy.init();

        // Register event handlers
        MinecraftForge.EVENT_BUS.register(new LevelHandler());
=======
        // Register the event handler
        MinecraftForge.EVENT_BUS.register(new EventHandler());
>>>>>>> parent of 9f6120d (Proxies and Leveling System)
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        System.out.println(NAME + " is initializing (init phase).");

        // Register client-only or server-only elements through the proxy
        proxy.registerClientOnly();
        proxy.registerServerOnly();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        System.out.println(NAME + " has finished loading (postInit phase).");
    }

    private void loadConfig(FMLPreInitializationEvent event) {
        config = new Configuration(event.getSuggestedConfigurationFile());
        config.load();

        enableScoreboard = config.getBoolean("enableScoreboard", "general", true, "Enable the scoreboard.");
        enableGUI = config.getBoolean("enableGUI", "general", true, "Enable the GUI.");
        guiMultiplayerOnly = config.getBoolean("guiMultiplayerOnly", "general", true, "Only show GUI in multiplayer.");

        // Save the configuration if it was modified
        if (config.hasChanged()) {
            config.save();
        }
    }
}
