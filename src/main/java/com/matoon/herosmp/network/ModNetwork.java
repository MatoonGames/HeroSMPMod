package com.matoon.herosmp.network;

import com.matoon.herosmp.HeroSMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public final class ModNetwork {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(HeroSMP.MODID);

    private ModNetwork() {
    }

    public static void init() {
        int id = 0;
        CHANNEL.registerMessage(PacketOpenKitSelection.Handler.class, PacketOpenKitSelection.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(PacketSelectKit.Handler.class, PacketSelectKit.class, id++, Side.SERVER);
        CHANNEL.registerMessage(PacketOpenPvpMenu.Handler.class, PacketOpenPvpMenu.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(PacketPvpMenuAction.Handler.class, PacketPvpMenuAction.class, id++, Side.SERVER);
        CHANNEL.registerMessage(PacketOpenNpcEditor.Handler.class, PacketOpenNpcEditor.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(PacketSaveNpcEditor.Handler.class, PacketSaveNpcEditor.class, id++, Side.SERVER);
        CHANNEL.registerMessage(PacketHGMusicControl.Handler.class, PacketHGMusicControl.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(PacketOpenHungerGamesMenu.Handler.class, PacketOpenHungerGamesMenu.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(PacketHungerGamesMenuAction.Handler.class, PacketHungerGamesMenuAction.class, id++, Side.SERVER);
        CHANNEL.registerMessage(PacketHGMusicManifest.Handler.class, PacketHGMusicManifest.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(PacketHGMusicRequest.Handler.class, PacketHGMusicRequest.class, id++, Side.SERVER);
        CHANNEL.registerMessage(PacketHGMusicChunk.Handler.class, PacketHGMusicChunk.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(PacketTimeStoneCharge.Handler.class, PacketTimeStoneCharge.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(PacketLifeLink.Handler.class, PacketLifeLink.class, id++, Side.CLIENT);
    }
}
