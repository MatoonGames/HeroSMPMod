package com.matoon.herosmp.network;

import com.matoon.herosmp.timestone.TimeStoneChargeManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server → Client packet that syncs the local player's Time Stone charge value.
 * Sent at most every 4 ticks while the charge mechanic is active, and immediately
 * when the depletion state changes.
 */
public class PacketTimeStoneCharge implements IMessage {

    private float charge;
    private boolean depleted;

    /** Required no-arg constructor for Forge deserialization. */
    public PacketTimeStoneCharge() {}

    public PacketTimeStoneCharge(float charge, boolean depleted) {
        this.charge = charge;
        this.depleted = depleted;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeFloat(charge);
        buf.writeBoolean(depleted);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.charge = buf.readFloat();
        this.depleted = buf.readBoolean();
    }

    public static class Handler implements IMessageHandler<PacketTimeStoneCharge, IMessage> {
        @Override
        public IMessage onMessage(PacketTimeStoneCharge message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() ->
                TimeStoneChargeManager.setClientCharge(message.charge, message.depleted)
            );
            return null;
        }
    }
}
