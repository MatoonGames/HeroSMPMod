package com.matoon.herosmp.network;

import com.matoon.herosmp.HeroSMP;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import java.util.UUID;

public class PacketMindControlResistance implements IMessage {
    UUID token;int duration,windowStart,windowEnd;boolean escape;
    public PacketMindControlResistance(){}public PacketMindControlResistance(UUID token,int duration,int windowStart,int windowEnd,boolean escape){this.token=token;this.duration=duration;this.windowStart=windowStart;this.windowEnd=windowEnd;this.escape=escape;}
    @Override public void toBytes(ByteBuf b){b.writeLong(token.getMostSignificantBits());b.writeLong(token.getLeastSignificantBits());b.writeInt(duration);b.writeInt(windowStart);b.writeInt(windowEnd);b.writeBoolean(escape);}
    @Override public void fromBytes(ByteBuf b){token=new UUID(b.readLong(),b.readLong());duration=b.readInt();windowStart=b.readInt();windowEnd=b.readInt();escape=b.readBoolean();}
    public static class Handler implements IMessageHandler<PacketMindControlResistance,IMessage>{@Override public IMessage onMessage(PacketMindControlResistance m,MessageContext c){HeroSMP.proxy.handleClientPacket(m);return null;}}
}
