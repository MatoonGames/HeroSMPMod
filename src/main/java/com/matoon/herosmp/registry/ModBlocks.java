package com.matoon.herosmp.registry;

import com.matoon.herosmp.block.BlockCrownfallPodium;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class ModBlocks {
    public static final BlockCrownfallPodium CROWNFALL_PODIUM = new BlockCrownfallPodium();

    private ModBlocks() {
    }

    public static class RegistrationHandler {
        @SubscribeEvent
        public void onRegisterBlocks(RegistryEvent.Register<Block> event) {
            event.getRegistry().register(CROWNFALL_PODIUM);
        }

        @SubscribeEvent
        public void onRegisterItems(RegistryEvent.Register<Item> event) {
            event.getRegistry().register(new ItemBlock(CROWNFALL_PODIUM)
                    .setRegistryName(CROWNFALL_PODIUM.getRegistryName()));
        }
    }

    public static class ClientRegistrationHandler {
        @SubscribeEvent
        public void onRegisterModels(ModelRegistryEvent event) {
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(CROWNFALL_PODIUM), 0,
                    new ModelResourceLocation("herosmp:crownfall_podium", "inventory"));
        }
    }
}
