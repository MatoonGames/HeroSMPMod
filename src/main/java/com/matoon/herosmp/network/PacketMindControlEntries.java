package com.matoon.herosmp.network;

import com.matoon.herosmp.HeroSMP;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import java.util.*;

/** Full Soul Stone sidebar snapshot for one client. */
public class PacketMindControlEntries implements IMessage {
    public static class Entry { public UUID id; public String name; public boolean player; public int entityId; public Entry(UUID i, String n, boolean p, int entityId) { id=i; name=n; player=p; this.entityId=entityId; } }
    List<Entry> entries = new ArrayList<>();
    public PacketMindControlEntries() {}
    public PacketMindControlEntries(List<Entry> entries) { this.entries = entries; }
    @Override public void toBytes(ByteBuf b) { b.writeInt(entries.size()); for (Entry e: entries) { b.writeLong(e.id.getMostSignificantBits()); b.writeLong(e.id.getLeastSignificantBits()); ByteBufUtils.writeUTF8String(b, e.name); b.writeBoolean(e.player); b.writeInt(e.entityId); } }
    @Override public void fromBytes(ByteBuf b) { entries = new ArrayList<>(); for (int i=b.readInt(); i>0; i--) entries.add(new Entry(new UUID(b.readLong(), b.readLong()), ByteBufUtils.readUTF8String(b), b.readBoolean(), b.readInt())); }
    public static class Handler implements IMessageHandler<PacketMindControlEntries, IMessage> {
        @Override public IMessage onMessage(PacketMindControlEntries message, MessageContext ctx) { HeroSMP.proxy.handleClientPacket(message); return null; }
    }
}
