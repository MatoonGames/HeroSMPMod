package com.matoon.herosmp.network;

import com.matoon.herosmp.mindstone.MindControlPlayerLock;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Immediately dismisses the resistance overlay once the server resolves the attempt. */
public class PacketMindControlResistanceResult implements IMessage {
    @Override public void toBytes(ByteBuf b){}@Override public void fromBytes(ByteBuf b){}
    @SideOnly(Side.CLIENT)public static class Handler implements IMessageHandler<PacketMindControlResistanceResult,IMessage>{@Override public IMessage onMessage(PacketMindControlResistanceResult m,MessageContext c){net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(MindControlPlayerLock::finishResistance);return null;}}
}
