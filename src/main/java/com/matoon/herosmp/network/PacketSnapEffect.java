package com.matoon.herosmp.network;

import com.matoon.herosmp.HeroSMP;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Server → Client packet that triggers the snap sound and white flash overlay
 * on the receiving player's screen.
 *
 * {@code soundIndex} is 0, 1, or 2, chosen server-side so every client in range
 * plays the same sound.
 */
public class PacketSnapEffect implements IMessage {

    int soundIndex;

    /** Required no-arg constructor for Forge deserialization. */
    public PacketSnapEffect() {}

    public PacketSnapEffect(int soundIndex) {
        this.soundIndex = soundIndex;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(soundIndex);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        soundIndex = buf.readByte();
    }

    public static class Handler implements IMessageHandler<PacketSnapEffect, IMessage> {
        @Override
        public IMessage onMessage(PacketSnapEffect msg, MessageContext ctx) {
            HeroSMP.proxy.handleClientPacket(msg);
            return null;
        }
    }
}
