package com.matoon.herosmp.network;

import com.matoon.herosmp.mindstone.MindControlManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import java.util.UUID;

public class PacketMindControlResistanceResponse implements IMessage {
    private UUID token;private int elapsedTicks;public PacketMindControlResistanceResponse(){}public PacketMindControlResistanceResponse(UUID token,int elapsedTicks){this.token=token;this.elapsedTicks=elapsedTicks;}
    @Override public void toBytes(ByteBuf b){b.writeLong(token.getMostSignificantBits());b.writeLong(token.getLeastSignificantBits());b.writeInt(elapsedTicks);}@Override public void fromBytes(ByteBuf b){token=new UUID(b.readLong(),b.readLong());elapsedTicks=b.readInt();}
    public static class Handler implements IMessageHandler<PacketMindControlResistanceResponse,IMessage>{@Override public IMessage onMessage(PacketMindControlResistanceResponse m,MessageContext c){EntityPlayerMP p=c.getServerHandler().player;p.getServerWorld().addScheduledTask(()->MindControlManager.resistanceResponse(p,m.token,m.elapsedTicks));return null;}}
}
