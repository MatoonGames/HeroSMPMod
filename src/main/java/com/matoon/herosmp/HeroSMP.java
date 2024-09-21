package com.matoon.herosmp;

import com.matoon.herosmp.events.EventHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import java.io.File;

@Mod(modid = HeroSMP.MODID, name = HeroSMP.NAME, version = HeroSMP.VERSION)
public class HeroSMP {

    public static final String MODID = "herosmp";
    public static final String NAME = "Hero SMP";
    public static final String VERSION = "1.0";

    // Config fields
    public static Configuration config;
    public static boolean enableGUI = true;  // Default value for GUI enabled
    public static boolean guiMultiplayerOnly = true;  // Default: multiplayer-only
    public static boolean enableScoreboard = true;  // New: Enable/disable scoreboard

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Initialize the config
        File configFile = new File(event.getModConfigurationDirectory(), MODID + ".cfg");
        config = new Configuration(configFile);

        // Load the config
        loadConfig();

        // Register the event handler
        MinecraftForge.EVENT_BUS.register(new EventHandler());
    }

    public static void loadConfig() {
        try {
            config.load();
            enableGUI = config.getBoolean("enableGUI", Configuration.CATEGORY_GENERAL, true, "Set to false to disable the in-game GUI.");
            guiMultiplayerOnly = config.getBoolean("guiMultiplayerOnly", Configuration.CATEGORY_GENERAL, true, "Set to false to display GUI in both singleplayer and multiplayer.");
            enableScoreboard = config.getBoolean("enableScoreboard", Configuration.CATEGORY_GENERAL, true, "Set to false to disable the scoreboard.");
        } catch (Exception e) {
            System.err.println("Error loading config for " + MODID);
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }
}
