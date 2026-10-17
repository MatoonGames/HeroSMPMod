package com.matoon.herosmp.network;

import com.matoon.herosmp.HeroSMP;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.List;

/**
 * Server → Client. One chunk of a kit file sent by {@code /heropvp kit download <kit>}. The first
 * chunk opens the client's download popup; once all chunks arrive the client saves the file to
 * {@code beyonder_exports/PVP Kits/}.
 */
public class PacketKitTransfer implements IMessage {

    public int transferId;
    public String name;
    public int totalBytes;
    public int index;
    public int totalChunks;
    public byte[] data;

    public PacketKitTransfer() {}

    public PacketKitTransfer(int transferId, String name, int totalBytes, int index, int totalChunks, byte[] data) {
        this.transferId = transferId;
        this.name = name;
        this.totalBytes = totalBytes;
        this.index = index;
        this.totalChunks = totalChunks;
        this.data = data;
    }

    /** Streams a kit file to the player. */
    public static void send(EntityPlayerMP player, String name, byte[] file) {
        int id = player.world.rand.nextInt(Integer.MAX_VALUE);
        List<byte[]> chunks = KitChunks.split(file);
        for (int i = 0; i < chunks.size(); i++) {
            ModNetwork.CHANNEL.sendTo(new PacketKitTransfer(id, name, file.length, i, chunks.size(), chunks.get(i)), player);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(transferId);
        ByteBufUtils.writeUTF8String(buf, name == null ? "" : name);
        buf.writeInt(totalBytes);
        buf.writeInt(index);
        buf.writeInt(totalChunks);
        buf.writeInt(data == null ? 0 : data.length);
        if (data != null) buf.writeBytes(data);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        transferId = buf.readInt();
        name = ByteBufUtils.readUTF8String(buf);
        totalBytes = buf.readInt();
        index = buf.readInt();
        totalChunks = buf.readInt();
        int len = buf.readInt();
        if (len < 0 || len > KitChunks.CHUNK_SIZE) throw new IllegalArgumentException("Bad kit chunk size: " + len);
        data = new byte[len];
        buf.readBytes(data);
    }

    public static class Handler implements IMessageHandler<PacketKitTransfer, IMessage> {
        @Override
        public IMessage onMessage(PacketKitTransfer msg, MessageContext ctx) {
            HeroSMP.proxy.handleClientPacket(msg);
            return null;
        }
    }
}
