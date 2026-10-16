package com.matoon.herosmp.network;

import com.matoon.herosmp.HeroSMP;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server → client. Either starts a specific HG music track or stops the
 * currently playing one. Uses SoundHandler.stopSound(ISound) which exists
 * in MC 1.12.2, allowing precise stop without killing other sounds.
 */
public class PacketHGMusicControl implements IMessage {

    private boolean stop;
    private String  phase;
    private String  track;
    // One-shot stinger: play OVER the current looping music instead of replacing it.
    private boolean oneShot;

    public PacketHGMusicControl() {}

    /** Play packet — replaces the current looping track. */
    public PacketHGMusicControl(String phase, String track) {
        this.stop    = false;
        this.phase   = phase;
        this.track   = track;
        this.oneShot = false;
    }

    /** One-shot stinger packet — layers over the current looping track. */
    public PacketHGMusicControl(String phase, String track, boolean oneShot) {
        this.stop    = false;
        this.phase   = phase;
        this.track   = track;
        this.oneShot = oneShot;
    }

    /** Stop packet. */
    public PacketHGMusicControl(boolean stop) {
        this.stop    = stop;
        this.phase   = "";
        this.track   = "";
        this.oneShot = false;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(stop);
        ByteBufUtils.writeUTF8String(buf, phase == null ? "" : phase);
        ByteBufUtils.writeUTF8String(buf, track == null ? "" : track);
        buf.writeBoolean(oneShot);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        stop    = buf.readBoolean();
        phase   = ByteBufUtils.readUTF8String(buf);
        track   = ByteBufUtils.readUTF8String(buf);
        oneShot = buf.readBoolean();
    }

    // -------------------------------------------------------------------------

    public static class Handler implements IMessageHandler<PacketHGMusicControl, IMessage> {
        @Override
        public IMessage onMessage(PacketHGMusicControl msg, MessageContext ctx) {
            HeroSMP.proxy.handleHungerGamesMusic(msg.stop, msg.phase, msg.track, msg.oneShot);
            return null;
        }
    }
}
