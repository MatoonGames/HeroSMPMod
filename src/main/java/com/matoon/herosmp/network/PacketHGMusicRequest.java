package com.matoon.herosmp.network;

import com.matoon.herosmp.HeroSMP;
import com.matoon.herosmp.hungergames.music.HungerGamesMusicManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client → Server. Requests a specific music file by phase and filename.
 * The server responds with one or more {@link PacketHGMusicChunk} packets.
 */
public class PacketHGMusicRequest implements IMessage {

    private String phase;
    private String filename;

    public PacketHGMusicRequest() {}

    public PacketHGMusicRequest(String phase, String filename) {
        this.phase    = phase;
        this.filename = filename;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, phase    == null ? "" : phase);
        ByteBufUtils.writeUTF8String(buf, filename == null ? "" : filename);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        phase    = ByteBufUtils.readUTF8String(buf);
        filename = ByteBufUtils.readUTF8String(buf);
    }

    // -------------------------------------------------------------------------

    public static class Handler implements IMessageHandler<PacketHGMusicRequest, IMessage> {

        @Override
        public IMessage onMessage(PacketHGMusicRequest msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                HungerGamesMusicManager mgr = HeroSMP.HUNGER_GAMES_MANAGER.getMusicManager();
                if (mgr == null) return;
                mgr.sendChunks(player, msg.phase, msg.filename);
            });
            return null;
        }
    }
}
