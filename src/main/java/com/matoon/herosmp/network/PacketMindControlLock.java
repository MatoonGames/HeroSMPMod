package com.matoon.herosmp.network;

import com.matoon.herosmp.mindstone.MindControlPlayerLock;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Sent to the victim only: viewing remains available, all gameplay input is suppressed. */
public class PacketMindControlLock implements IMessage {
    private boolean locked;private int remainingTicks; public PacketMindControlLock(){} public PacketMindControlLock(boolean locked){this(locked,0);}public PacketMindControlLock(boolean locked,int remainingTicks){this.locked=locked;this.remainingTicks=remainingTicks;}
    @Override public void toBytes(ByteBuf b){b.writeBoolean(locked);b.writeInt(remainingTicks);} @Override public void fromBytes(ByteBuf b){locked=b.readBoolean();remainingTicks=b.readInt();}
    @SideOnly(Side.CLIENT) public static class Handler implements IMessageHandler<PacketMindControlLock,IMessage>{@Override public IMessage onMessage(PacketMindControlLock m,MessageContext c){net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(()->MindControlPlayerLock.setLocked(m.locked,m.remainingTicks));return null;}}
}
