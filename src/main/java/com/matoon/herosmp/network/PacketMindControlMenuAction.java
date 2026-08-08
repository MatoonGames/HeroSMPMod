package com.matoon.herosmp.network;

import com.matoon.herosmp.mindstone.MindControlManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import java.util.UUID;

public class PacketMindControlMenuAction implements IMessage {
    private UUID target; private boolean drain; public PacketMindControlMenuAction(){} public PacketMindControlMenuAction(UUID target,boolean drain){this.target=target;this.drain=drain;}
    @Override public void toBytes(ByteBuf b){b.writeLong(target.getMostSignificantBits());b.writeLong(target.getLeastSignificantBits());b.writeBoolean(drain);}
    @Override public void fromBytes(ByteBuf b){target=new UUID(b.readLong(),b.readLong());drain=b.readBoolean();}
    public static class Handler implements IMessageHandler<PacketMindControlMenuAction,IMessage>{@Override public IMessage onMessage(PacketMindControlMenuAction m,MessageContext c){EntityPlayerMP p=c.getServerHandler().player;p.getServerWorld().addScheduledTask(()->MindControlManager.menuAction(p,m.target,m.drain));return null;}}
}
