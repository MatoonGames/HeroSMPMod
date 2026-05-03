package com.matoon.herosmp.client;

import com.matoon.herosmp.CommonProxy;
import com.matoon.herosmp.events.EventHandler;
import com.matoon.herosmp.hungergames.music.HungerGamesMusicResourcePack;
import com.matoon.herosmp.registry.ModEntities;
import com.matoon.herosmp.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.io.File;
import java.util.List;

public class ClientProxy extends CommonProxy {
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        ModEntities.registerRenderers();
        MinecraftForge.EVENT_BUS.register(new EventHandler());
        // Register model event handler (client-only)
        MinecraftForge.EVENT_BUS.register(new ModItems.ClientRegistrationHandler());
        // Life Link chain renderer (client-only world renderer)
        MinecraftForge.EVENT_BUS.register(new LifeLinkChainRenderer());
        // Infinity Gauntlet power-up overlay and sound
        MinecraftForge.EVENT_BUS.register(new InfPowerUpOverlay());
        // Snap white flash overlay and sound
        MinecraftForge.EVENT_BUS.register(new SnapEffectOverlay());
        // Snap permanent skin overlay (snapper's hand texture until death)
        MinecraftForge.EVENT_BUS.register(new SnapSkinOverlay());

        File musicDir = new File(event.getModConfigurationDirectory().getParentFile(), "herosmp_hg_music");
        try {
            Minecraft mc = FMLClientHandler.instance().getClient();
            List<IResourcePack> packs = ReflectionHelper.getPrivateValue(
                Minecraft.class, mc,
                "defaultResourcePacks", "field_110449_ao");
            packs.add(new HungerGamesMusicResourcePack(musicDir));
        } catch (ReflectionHelper.UnableToFindFieldException e) {
            e.printStackTrace();
        }
    }

    /**
     * After all mods have initialised, reload resources so the dynamic sounds.json
     * from HungerGamesMusicResourcePack is picked up by the SoundHandler.
     * Without this call Minecraft never re-reads sounds.json and the phase music
     * events are never registered, causing all music packets to be silently ignored.
     */
    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        Minecraft mc = FMLClientHandler.instance().getClient();
        if (mc != null) {
            mc.refreshResources();
        }
    }
}
