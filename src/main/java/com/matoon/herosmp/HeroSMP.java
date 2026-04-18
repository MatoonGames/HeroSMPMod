package com.matoon.herosmp;

import com.matoon.herosmp.npc.PvpQueueManager;
import com.matoon.herosmp.npc.command.CommandHeroNpc;
import com.matoon.herosmp.npc.command.CommandPvpMenu;
import com.matoon.herosmp.npc.command.CommandReturn;
import com.matoon.herosmp.npc.kit.KitManager;
import com.matoon.herosmp.npc.loot.PvpChestLootManager;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

import java.io.File;

@Mod(modid = HeroSMP.MODID, name = HeroSMP.NAME, version = HeroSMP.VERSION)
public class HeroSMP {

    public static final String MODID = "herosmp";
    public static final String NAME = "Hero SMP";
    public static final String VERSION = "1.0";

    public static final PvpQueueManager PVP_QUEUE_MANAGER = new PvpQueueManager();
    public static final KitManager KIT_MANAGER = new KitManager();
    public static final PvpChestLootManager PVP_CHEST_LOOT_MANAGER = new PvpChestLootManager();

    public static Configuration config;
    public static boolean enableGUI = true;
    public static boolean guiMultiplayerOnly = true;
    public static boolean enableScoreboard = true;

    @SidedProxy(clientSide = "com.matoon.herosmp.client.ClientProxy", serverSide = "com.matoon.herosmp.server.ServerProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        File configFile = new File(event.getModConfigurationDirectory(), MODID + ".cfg");
        config = new Configuration(configFile);
        loadConfig();
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
        // Register commands directly as a safety net so dedicated/integrated command wiring
        // remains available even if a proxy registration path is skipped.
        event.registerServerCommand(new CommandHeroNpc());
        event.registerServerCommand(new CommandReturn());
        event.registerServerCommand(new CommandPvpMenu());
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
