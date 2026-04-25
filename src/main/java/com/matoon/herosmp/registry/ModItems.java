package com.matoon.herosmp.registry;

import com.matoon.herosmp.item.ItemHealthCapLingeringPotion;
import com.matoon.herosmp.item.ItemHealthCapPotion;
import com.matoon.herosmp.item.ItemHealthCapSplashPotion;
import com.matoon.herosmp.item.ItemLifeDrainer;
import com.matoon.herosmp.item.ItemLifeLinkArrow;
import com.matoon.herosmp.item.ItemPowerSwapperArrow;
import com.matoon.herosmp.item.ItemSupeVirusArrow;
import com.matoon.herosmp.item.ItemSupeVirusCure;
import com.matoon.herosmp.item.ItemTotemOfProtection;
import com.matoon.herosmp.item.ItemTotemOfReversal;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ModItems {

    public static final ItemLifeDrainer LIFE_DRAINER = new ItemLifeDrainer();

    public static final ItemHealthCapPotion HEALTH_CAP_POTION = new ItemHealthCapPotion();
    public static final ItemHealthCapSplashPotion HEALTH_CAP_SPLASH_POTION = new ItemHealthCapSplashPotion();
    public static final ItemHealthCapLingeringPotion HEALTH_CAP_LINGERING_POTION = new ItemHealthCapLingeringPotion();

    // --- Arrow items ---
    public static final ItemPowerSwapperArrow POWER_SWAPPER_ARROW = new ItemPowerSwapperArrow();
    public static final ItemLifeLinkArrow LIFE_LINK_ARROW = new ItemLifeLinkArrow();
    public static final ItemSupeVirusArrow SUPE_VIRUS_ARROW = new ItemSupeVirusArrow();

    // --- Cure ---
    public static final ItemSupeVirusCure SUPE_VIRUS_CURE = new ItemSupeVirusCure();

    // --- Totems ---
    public static final ItemTotemOfProtection TOTEM_OF_PROTECTION = new ItemTotemOfProtection();
    public static final ItemTotemOfReversal TOTEM_OF_REVERSAL = new ItemTotemOfReversal();

    /** Registered on the common event bus (both sides). */
    public static class RegistrationHandler {
        @SubscribeEvent
        public void onRegisterItems(RegistryEvent.Register<Item> event) {
            event.getRegistry().register(LIFE_DRAINER);
            event.getRegistry().register(HEALTH_CAP_POTION);
            event.getRegistry().register(HEALTH_CAP_SPLASH_POTION);
            event.getRegistry().register(HEALTH_CAP_LINGERING_POTION);
            event.getRegistry().register(POWER_SWAPPER_ARROW);
            event.getRegistry().register(LIFE_LINK_ARROW);
            event.getRegistry().register(SUPE_VIRUS_ARROW);
            event.getRegistry().register(SUPE_VIRUS_CURE);
            event.getRegistry().register(TOTEM_OF_PROTECTION);
            event.getRegistry().register(TOTEM_OF_REVERSAL);
        }
    }

    /** Registered on the common event bus from the client proxy only. */
    @SideOnly(Side.CLIENT)
    public static class ClientRegistrationHandler {
        @SubscribeEvent
        public void onRegisterModels(ModelRegistryEvent event) {
            ModelLoader.setCustomModelResourceLocation(
                    LIFE_DRAINER, 0,
                    new ModelResourceLocation("herosmp:life_drainer", "inventory"));
            ModelLoader.setCustomModelResourceLocation(
                    HEALTH_CAP_POTION, 0,
                    new ModelResourceLocation("herosmp:health_cap_potion", "inventory"));
            ModelLoader.setCustomModelResourceLocation(
                    HEALTH_CAP_SPLASH_POTION, 0,
                    new ModelResourceLocation("herosmp:health_cap_splash_potion", "inventory"));
            ModelLoader.setCustomModelResourceLocation(
                    HEALTH_CAP_LINGERING_POTION, 0,
                    new ModelResourceLocation("herosmp:health_cap_lingering_potion", "inventory"));
            ModelLoader.setCustomModelResourceLocation(
                    POWER_SWAPPER_ARROW, 0,
                    new ModelResourceLocation("herosmp:power_swapper_arrow", "inventory"));
            ModelLoader.setCustomModelResourceLocation(
                    LIFE_LINK_ARROW, 0,
                    new ModelResourceLocation("herosmp:life_link_arrow", "inventory"));
            ModelLoader.setCustomModelResourceLocation(
                    SUPE_VIRUS_ARROW, 0,
                    new ModelResourceLocation("herosmp:supe_virus_arrow", "inventory"));
            ModelLoader.setCustomModelResourceLocation(
                    SUPE_VIRUS_CURE, 0,
                    new ModelResourceLocation("herosmp:supe_virus_cure", "inventory"));
            ModelLoader.setCustomModelResourceLocation(
                    TOTEM_OF_PROTECTION, 0,
                    new ModelResourceLocation("herosmp:totem_of_protection", "inventory"));
            ModelLoader.setCustomModelResourceLocation(
                    TOTEM_OF_REVERSAL, 0,
                    new ModelResourceLocation("herosmp:totem_of_reversal", "inventory"));
        }
    }
}
