package com.matoon.herosmp.network;

import com.matoon.herosmp.mindstone.MindControlManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import java.util.UUID;

/** Sent only by a Soul Stone companion sidebar entry. */
public class PacketReleaseMindControl implements IMessage {
    private UUID target;
    public PacketReleaseMindControl() {}
    public PacketReleaseMindControl(UUID target) { this.target=target; }
    @Override public void toBytes(ByteBuf b) { b.writeLong(target.getMostSignificantBits()); b.writeLong(target.getLeastSignificantBits()); }
    @Override public void fromBytes(ByteBuf b) { target=new UUID(b.readLong(), b.readLong()); }
    public static class Handler implements IMessageHandler<PacketReleaseMindControl, IMessage> {
        @Override public IMessage onMessage(PacketReleaseMindControl message, MessageContext ctx) { EntityPlayerMP p=ctx.getServerHandler().player; p.getServerWorld().addScheduledTask(() -> MindControlManager.release(p, message.target, true)); return null; }
    }
}
