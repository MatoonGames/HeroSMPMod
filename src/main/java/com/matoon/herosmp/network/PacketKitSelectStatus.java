package com.matoon.herosmp.network;

import com.matoon.herosmp.HeroSMP;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fighter-select roster for a PVP round: everyone in the match and the kit each has locked in
 * (empty while still choosing). Sent to every fighter whenever someone joins the select screen or
 * locks a kit, so the screen can show opponents' picks and the VS splash.
 */
public class PacketKitSelectStatus implements IMessage {

    public static final class Fighter {
        public final UUID id;
        public final String name;
        /** Locked kit key, or "" while still choosing. */
        public final String kitKey;

        public Fighter(UUID id, String name, String kitKey) {
            this.id = id;
            this.name = name == null ? "" : name;
            this.kitKey = kitKey == null ? "" : kitKey;
        }
    }

    public List<Fighter> fighters = new ArrayList<Fighter>();

    public PacketKitSelectStatus() {}

    public PacketKitSelectStatus(List<Fighter> fighters) {
        this.fighters = fighters;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int n = buf.readByte() & 0xFF;
        fighters = new ArrayList<Fighter>(n);
        for (int i = 0; i < n; i++) {
            UUID id = new UUID(buf.readLong(), buf.readLong());
            String name = ByteBufUtils.readUTF8String(buf);
            String kit = ByteBufUtils.readUTF8String(buf);
            fighters.add(new Fighter(id, name, kit));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int n = Math.min(255, fighters.size());
        buf.writeByte(n);
        for (int i = 0; i < n; i++) {
            Fighter f = fighters.get(i);
            buf.writeLong(f.id.getMostSignificantBits());
            buf.writeLong(f.id.getLeastSignificantBits());
            ByteBufUtils.writeUTF8String(buf, f.name);
            ByteBufUtils.writeUTF8String(buf, f.kitKey);
        }
    }

    public static class Handler implements IMessageHandler<PacketKitSelectStatus, IMessage> {
        @Override
        public IMessage onMessage(PacketKitSelectStatus message, MessageContext ctx) {
            HeroSMP.proxy.handleClientPacket(message);
            return null;
        }
    }
}
