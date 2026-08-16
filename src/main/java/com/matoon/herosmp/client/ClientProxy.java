package com.matoon.herosmp.client;

import com.matoon.herosmp.CommonProxy;
import com.matoon.herosmp.events.EventHandler;
import com.matoon.herosmp.mindstone.MindControlAbilityBarProvider;
import com.matoon.herosmp.mindstone.MindControlAuraRenderer;
import com.matoon.herosmp.mindstone.MindControlPlayerLock;
import com.matoon.herosmp.hungergames.music.HungerGamesMusicResourcePack;
import com.matoon.herosmp.registry.ModEntities;
import com.matoon.herosmp.registry.ModItems;
import com.matoon.herosmp.network.ClientPacketDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.io.File;
import java.util.List;

public class ClientProxy extends CommonProxy {
    private volatile ISound currentHungerGamesSound;

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
        lucraft.mods.lucraftcore.util.abilitybar.AbilityBarHandler.registerProvider(new MindControlAbilityBarProvider());
        MinecraftForge.EVENT_BUS.register(new MindControlAuraRenderer());
        MinecraftForge.EVENT_BUS.register(new MindControlPlayerLock());

        try {
            Minecraft mc = FMLClientHandler.instance().getClient();
            // Serve downloaded tracks from the client CACHE dir (distinct from the
            // server's source folder) so pruning the cache can never delete an admin's
            // source music on an integrated server, where both share one game dir.
            File cacheDir = com.matoon.herosmp.network.PacketHGMusicChunk.Handler.getCacheDir(mc);
            List<IResourcePack> packs = ReflectionHelper.getPrivateValue(
                Minecraft.class, mc,
                "defaultResourcePacks", "field_110449_ao");
            packs.add(new HungerGamesMusicResourcePack(cacheDir));
        } catch (ReflectionHelper.UnableToFindFieldException e) {
            e.printStackTrace();
        }
    }

    /**
     * After all mods have initialised, do one full resource refresh. This incorporates
     * HungerGamesMusicResourcePack (added to defaultResourcePacks above, after the
     * initial startup load already ran) into the resource manager. That one-time full
     * reload is required because a lightweight sound-only reload cannot add a new pack —
     * it only re-reads sounds.json from packs already registered here. Runtime track
     * downloads then use the cheap {@code PacketHGMusicChunk.Handler.reloadSounds} path.
     */
    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        Minecraft mc = FMLClientHandler.instance().getClient();
        if (mc != null) {
            ModItems.registerSpawnEggColors();
            mc.refreshResources();
        }
    }

    @Override
    public void handleHungerGamesMusic(boolean stop, String phase, String track) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            if (currentHungerGamesSound != null) {
                mc.getSoundHandler().stopSound(currentHungerGamesSound);
                currentHungerGamesSound = null;
            }

            if (!stop && phase != null && !phase.isEmpty() && track != null && !track.isEmpty()) {
                ResourceLocation location =
                        com.matoon.herosmp.hungergames.music.HungerGamesMusicManager.soundLocation(phase, track);
                ISound sound = new PositionedSoundRecord(
                        location, SoundCategory.MUSIC, 1.0f, 1.0f,
                        false, 0, ISound.AttenuationType.NONE, 0f, 0f, 0f);
                mc.getSoundHandler().playSound(sound);
                currentHungerGamesSound = sound;
            }
        });
    }

    @Override
    public void handleClientPacket(IMessage message) {
        ClientPacketDispatcher.handle(message);
    }
}
