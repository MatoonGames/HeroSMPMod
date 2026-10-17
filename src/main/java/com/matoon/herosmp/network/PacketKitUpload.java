package com.matoon.herosmp.network;

import com.matoon.herosmp.npc.kit.KitLibrary;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Client → Server. One chunk of a kit file an operator publishes from the kit editor. When all
 * chunks arrive, the server writes each kit into its own {@code beyonder_kits/} library, so the kit
 * becomes pickable on that server (dedicated ones included). Gated to op level 2 like
 * {@code /heropvp}.
 */
public class PacketKitUpload implements IMessage {

    private int transferId;
    private int index;
    private int totalChunks;
    private byte[] data;

    public PacketKitUpload() {}

    public PacketKitUpload(int transferId, int index, int totalChunks, byte[] data) {
        this.transferId = transferId;
        this.index = index;
        this.totalChunks = totalChunks;
        this.data = data;
    }

    /** Sends a kit file to the connected server. */
    public static void send(byte[] file) {
        int id = new Random().nextInt(Integer.MAX_VALUE);
        List<byte[]> chunks = KitChunks.split(file);
        for (int i = 0; i < chunks.size(); i++) {
            ModNetwork.CHANNEL.sendToServer(new PacketKitUpload(id, i, chunks.size(), chunks.get(i)));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(transferId);
        buf.writeInt(index);
        buf.writeInt(totalChunks);
        buf.writeInt(data == null ? 0 : data.length);
        if (data != null) buf.writeBytes(data);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        transferId = buf.readInt();
        index = buf.readInt();
        totalChunks = buf.readInt();
        int len = buf.readInt();
        if (len < 0 || len > KitChunks.CHUNK_SIZE) throw new IllegalArgumentException("Bad kit chunk size: " + len);
        data = new byte[len];
        buf.readBytes(data);
    }

    // -------------------------------------------------------------------------

    public static class Handler implements IMessageHandler<PacketKitUpload, IMessage> {

        /** In-progress uploads, touched only on the server thread. */
        private static final Map<UUID, Upload> UPLOADS = new HashMap<UUID, Upload>();

        private static final class Upload {
            final int id;
            final KitChunks.Assembler assembler;
            Upload(int id, KitChunks.Assembler assembler) {
                this.id = id;
                this.assembler = assembler;
            }
        }

        @Override
        public IMessage onMessage(PacketKitUpload msg, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> receive(player, msg));
            return null;
        }

        private static void receive(EntityPlayerMP player, PacketKitUpload msg) {
            MinecraftServer server = player.getServer();
            boolean singlePlayerHost = server != null && server.isSinglePlayer()
                    && player.getName().equals(server.getServerOwner());
            if (!singlePlayerHost && !player.canUseCommand(2, "heropvp")) {
                if (msg.index == 0) reply(player, TextFormatting.RED + "You don't have permission to publish kits.");
                return;
            }

            UUID uuid = player.getUniqueID();
            Upload up = UPLOADS.get(uuid);
            try {
                if (up == null || up.id != msg.transferId) {
                    up = new Upload(msg.transferId, new KitChunks.Assembler(msg.totalChunks));
                    UPLOADS.put(uuid, up);
                }
                if (!up.assembler.accept(msg.index, msg.totalChunks, msg.data)) return;
                UPLOADS.remove(uuid);

                KitLibrary.Parsed parsed = KitLibrary.parse(up.assembler.result());
                StringBuilder names = new StringBuilder();
                for (NBTTagCompound kit : parsed.kits) {
                    KitLibrary.write(kit, parsed.worlds);
                    if (names.length() > 0) names.append(", ");
                    names.append(kit.getString("DisplayName"));
                }
                reply(player, TextFormatting.GREEN + "Published kit " + names + " to this server's kit library.");
                server.logInfo(player.getName() + " published kit " + names + " to beyonder_kits");
            } catch (Exception e) {
                UPLOADS.remove(uuid);
                reply(player, TextFormatting.RED + "Couldn't publish the kit: " + e.getMessage());
            }
        }

        private static void reply(EntityPlayerMP player, String text) {
            player.sendMessage(new TextComponentString(text));
        }
    }
}
