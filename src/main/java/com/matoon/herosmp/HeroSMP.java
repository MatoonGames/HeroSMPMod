package com.matoon.herosmp;

import com.matoon.herosmp.hungergames.HungerGamesWorldManager;
import com.matoon.herosmp.integration.LucraftCoreIntegration;
import com.matoon.herosmp.npc.PvpQueueManager;
import com.matoon.herosmp.npc.command.CommandHeroNpc;
import com.matoon.herosmp.npc.command.CommandPvpMenu;
import com.matoon.herosmp.npc.command.CommandReturn;
import com.matoon.herosmp.npc.kit.KitManager;
import com.matoon.herosmp.npc.loot.PvpChestLootManager;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
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
    public static final HungerGamesWorldManager HUNGER_GAMES_MANAGER = new HungerGamesWorldManager();
    public static final com.matoon.herosmp.integration.PvpInjectionManager PVP_INJECTION_MANAGER =
            new com.matoon.herosmp.integration.PvpInjectionManager();

    public static Configuration config;
    public static boolean enableGUI = true;
    public static boolean guiMultiplayerOnly = true;
    public static boolean enableScoreboard = true;

    // Infinity Gauntlet power-up sequence settings
    public static boolean enableInfPowerUpEffects = true;

    // Time Stone ability charge settings
    public static boolean timeStoneChargeOutsideMatches  = false;
    public static int     timeStoneRechargeSeconds       = 30;
    public static float   timeStoneSlowDrainMultiplier   = 0.67f;
    public static float   timeStoneSpeedDrainMultiplier  = 1.0f;
    public static float   timeStonePvpDrainMultiplier    = 1.0f;
    public static int     mindControlDurationSeconds     = 120;

    // PVP-only flight/invisibility charge settings
    public static boolean enablePvpMobilityCharges       = true;
    public static int     pvpMobilityChargeSeconds       = 15;
    public static int     pvpMobilityRechargeSeconds     = 30;

    @SidedProxy(clientSide = "com.matoon.herosmp.client.ClientProxy", serverSide = "com.matoon.herosmp.server.ServerProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        File configFile = new File(event.getModConfigurationDirectory(), MODID + ".cfg");
        config = new Configuration(configFile);
        loadConfig();

        File mapsDir = new File(event.getModConfigurationDirectory().getParentFile(), "herosmp_hg_maps");
        HUNGER_GAMES_MANAGER.initMapsDirectory(mapsDir);

        File musicDir = new File(event.getModConfigurationDirectory().getParentFile(), "herosmp_hg_music");
        HUNGER_GAMES_MANAGER.initMusicDirectory(musicDir);

        proxy.preInit(event);
        LucraftCoreIntegration.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
        LucraftCoreIntegration.init(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
        PVP_QUEUE_MANAGER.purgeStaleArenaDimensions(event.getServer());
        // Register commands directly as a safety net so dedicated/integrated command wiring
        // remains available even if a proxy registration path is skipped.
        event.registerServerCommand(new CommandHeroNpc());
        event.registerServerCommand(new CommandReturn());
        event.registerServerCommand(new CommandPvpMenu());
        event.registerServerCommand(new com.matoon.herosmp.hungergames.command.CommandHungerGames());
    }

    public static void loadConfig() {
        try {
            config.load();
            enableGUI = config.getBoolean("enableGUI", Configuration.CATEGORY_GENERAL, true, "Set to false to disable the in-game GUI.");
            guiMultiplayerOnly = config.getBoolean("guiMultiplayerOnly", Configuration.CATEGORY_GENERAL, true, "Set to false to display GUI in both singleplayer and multiplayer.");
            enableScoreboard = config.getBoolean("enableScoreboard", Configuration.CATEGORY_GENERAL, true, "Set to false to disable the scoreboard.");

            // Infinity Gauntlet power-up settings
            final String CAT_GAUNTLET = "infinityGauntlet";
            enableInfPowerUpEffects = config.getBoolean("enablePowerUpEffects", CAT_GAUNTLET, true,
                    "Set to false to disable the Infinity Gauntlet power-up sound and overlay effects when all six stones are first slotted.");

            // Time Stone charge settings
            final String CAT_TIMESTONE = "timeStone";
            timeStoneChargeOutsideMatches = config.getBoolean("enableChargeOutsideMatches", CAT_TIMESTONE, false,
                    "Set to true to enable the Time Stone ability charge mechanic outside PVP and Hunger Games matches.");
            timeStoneRechargeSeconds = config.getInt("rechargeSeconds", CAT_TIMESTONE, 30, 1, 3600,
                    "Seconds for the Time Stone charge to recover from 0 to full.");
            timeStoneSlowDrainMultiplier = config.getFloat("slowTimeDrainMultiplier", CAT_TIMESTONE, 0.67f, 0.01f, 100.0f,
                    "Drain rate multiplier when time is slowed. Uses a sqrt(20/rate) curve: rate=1 drains ~4.5x faster than rate=19. Default targets ~10s drain at rate=1 in PVP.");
            timeStoneSpeedDrainMultiplier = config.getFloat("speedTimeDrainMultiplier", CAT_TIMESTONE, 1.0f, 0.01f, 100.0f,
                    "Drain rate multiplier when time is sped up (rate > 20). Linear by deviation. Higher = drains faster.");
            timeStonePvpDrainMultiplier = config.getFloat("pvpDrainMultiplier", CAT_TIMESTONE, 1.0f, 0.01f, 1000.0f,
                    "Additional drain multiplier applied on top of slow/speed multipliers inside PVP and Hunger Games matches.");
            mindControlDurationSeconds = config.getInt("durationSeconds", "mindStone", 120, 1, 3600,
                    "Mind Stone control duration in seconds. Targets also break free at one heart.");

            final String CAT_PVP_ABILITIES = "pvpAbilities";
            enablePvpMobilityCharges = config.getBoolean("enableFlightAndInvisibilityCharges", CAT_PVP_ABILITIES, true,
                    "Limit flight and invisibility abilities to a rechargeable charge while participating in any Hero PVP mode. Outside PVP these abilities remain unlimited.");
            pvpMobilityChargeSeconds = config.getInt("chargeSeconds", CAT_PVP_ABILITIES, 15, 1, 3600,
                    "Seconds of continuous flight or invisibility available from a full PVP charge. Each ability type has its own charge.");
            pvpMobilityRechargeSeconds = config.getInt("rechargeSeconds", CAT_PVP_ABILITIES, 30, 1, 3600,
                    "Seconds required for an empty PVP flight or invisibility charge to refill while that ability is off.");
        } catch (Exception e) {
            System.err.println("Error loading config for " + MODID);
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }
}
