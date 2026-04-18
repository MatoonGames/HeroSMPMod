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
    }
}
