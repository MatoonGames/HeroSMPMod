package com.matoon.herosmp.network;

import com.matoon.herosmp.hungergames.music.HungerGamesMusicManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
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

    public PacketHGMusicControl() {}

    /** Play packet. */
    public PacketHGMusicControl(String phase, String track) {
        this.stop  = false;
        this.phase = phase;
        this.track = track;
    }

    /** Stop packet. */
    public PacketHGMusicControl(boolean stop) {
        this.stop  = stop;
        this.phase = "";
        this.track = "";
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(stop);
        ByteBufUtils.writeUTF8String(buf, phase == null ? "" : phase);
        ByteBufUtils.writeUTF8String(buf, track == null ? "" : track);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        stop  = buf.readBoolean();
        phase = ByteBufUtils.readUTF8String(buf);
        track = ByteBufUtils.readUTF8String(buf);
    }

    // -------------------------------------------------------------------------

    public static class Handler implements IMessageHandler<PacketHGMusicControl, IMessage> {

        /** Tracks the currently playing HG music sound so we can stop it precisely. */
        private static volatile ISound currentSound = null;

        @Override
        public IMessage onMessage(PacketHGMusicControl msg, MessageContext ctx) {
            Minecraft mc = Minecraft.getMinecraft();
            mc.addScheduledTask(() -> {
                // Stop whatever is currently playing first
                if (currentSound != null) {
                    mc.getSoundHandler().stopSound(currentSound);
                    currentSound = null;
                }
                if (!msg.stop && !msg.phase.isEmpty() && !msg.track.isEmpty()) {
                    ResourceLocation loc = HungerGamesMusicManager.soundLocation(msg.phase, msg.track);
                    // NONE attenuation = full volume everywhere, unaffected by position
                    ISound sound = new PositionedSoundRecord(
                        loc, SoundCategory.MUSIC, 1.0f, 1.0f,
                        false, 0, ISound.AttenuationType.NONE, 0f, 0f, 0f);
                    mc.getSoundHandler().playSound(sound);
                    currentSound = sound;
                }
            });
            return null;
        }
    }
}
