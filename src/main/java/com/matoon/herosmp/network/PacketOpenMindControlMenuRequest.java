package com.matoon.herosmp.network;

import com.matoon.herosmp.mindstone.MindControlManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import java.util.UUID;

/** Sidebar selection asks the server for a validated companion menu snapshot. */
public class PacketOpenMindControlMenuRequest implements IMessage {
    private UUID target; public PacketOpenMindControlMenuRequest(){} public PacketOpenMindControlMenuRequest(UUID id){target=id;}
    @Override public void toBytes(ByteBuf b){b.writeLong(target.getMostSignificantBits());b.writeLong(target.getLeastSignificantBits());}
    @Override public void fromBytes(ByteBuf b){target=new UUID(b.readLong(),b.readLong());}
    public static class Handler implements IMessageHandler<PacketOpenMindControlMenuRequest,IMessage>{@Override public IMessage onMessage(PacketOpenMindControlMenuRequest m,MessageContext c){EntityPlayerMP p=c.getServerHandler().player;p.getServerWorld().addScheduledTask(()->MindControlManager.openMenu(p,m.target));return null;}}
}
