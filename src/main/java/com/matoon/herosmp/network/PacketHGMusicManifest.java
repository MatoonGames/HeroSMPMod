package com.matoon.herosmp.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Server → Client. Sent on player login.
 * Lists every music file the server has so the client can request them all.
 */
public class PacketHGMusicManifest implements IMessage {

    private List<FileEntry> entries;

    public PacketHGMusicManifest() {
        this.entries = new ArrayList<>();
    }

    public PacketHGMusicManifest(List<FileEntry> entries) {
        this.entries = entries;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entries.size());
        for (FileEntry e : entries) {
            ByteBufUtils.writeUTF8String(buf, e.phase);
            ByteBufUtils.writeUTF8String(buf, e.filename);
            buf.writeInt(e.totalBytes);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int size = buf.readInt();
        entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String phase    = ByteBufUtils.readUTF8String(buf);
            String filename = ByteBufUtils.readUTF8String(buf);
            int    total    = buf.readInt();
            entries.add(new FileEntry(phase, filename, total));
        }
    }

    public static class FileEntry {
        public final String phase;
        public final String filename;
        public final int    totalBytes;

        public FileEntry(String phase, String filename, int totalBytes) {
            this.phase      = phase;
            this.filename   = filename;
            this.totalBytes = totalBytes;
        }
    }

    // -------------------------------------------------------------------------

    public static class Handler implements IMessageHandler<PacketHGMusicManifest, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketHGMusicManifest msg, MessageContext ctx) {
            if (msg.entries.isEmpty()) return null;

            Minecraft mc = Minecraft.getMinecraft();
            mc.addScheduledTask(() -> {
                // Clear stale cached files so the client always mirrors the server exactly.
                File musicDir = PacketHGMusicChunk.Handler.getMusicDir(mc);
                clearDirectory(musicDir);

                // Track how many files we expect so the chunk handler knows when to reload.
                PacketHGMusicChunk.Handler.setPendingCount(msg.entries.size());
            });

            // Request every file from the server (back on the netty thread is fine for sends).
            for (FileEntry e : msg.entries) {
                ModNetwork.CHANNEL.sendToServer(new PacketHGMusicRequest(e.phase, e.filename));
            }

            return null;
        }

        private static void clearDirectory(File dir) {
            if (dir == null || !dir.exists()) return;
            File[] children = dir.listFiles();
            if (children == null) return;
            for (File child : children) {
                if (child.isDirectory()) {
                    clearDirectory(child);
                    child.delete();
                } else {
                    child.delete();
                }
            }
        }
    }
}
