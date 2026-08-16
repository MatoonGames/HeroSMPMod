package com.matoon.herosmp.network;

import com.matoon.herosmp.HeroSMP;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import java.util.UUID;

/** Rare add/remove replication for the client-only Mind Stone aura. */
public class PacketMindControlAura implements IMessage {
    UUID entity; boolean enabled;
    public PacketMindControlAura() {} public PacketMindControlAura(UUID entity, boolean enabled){this.entity=entity;this.enabled=enabled;}
    @Override public void toBytes(ByteBuf b){b.writeLong(entity.getMostSignificantBits());b.writeLong(entity.getLeastSignificantBits());b.writeBoolean(enabled);}
    @Override public void fromBytes(ByteBuf b){entity=new UUID(b.readLong(),b.readLong());enabled=b.readBoolean();}
    public static class Handler implements IMessageHandler<PacketMindControlAura,IMessage>{ @Override public IMessage onMessage(PacketMindControlAura m,MessageContext c){HeroSMP.proxy.handleClientPacket(m);return null;} }
}
