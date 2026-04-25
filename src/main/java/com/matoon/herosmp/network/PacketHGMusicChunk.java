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
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server → Client. Carries one chunk of a music file.
 * The client accumulates all chunks then writes the assembled file to disk.
 * When all files from the manifest have been received, resources are reloaded.
 */
public class PacketHGMusicChunk implements IMessage {

    public static final int CHUNK_SIZE = 28_000;

    private String phase;
    private String filename;
    private int    chunkIndex;
    private int    totalChunks;
    private byte[] data;

    public PacketHGMusicChunk() {}

    public PacketHGMusicChunk(String phase, String filename, int chunkIndex, int totalChunks, byte[] data) {
        this.phase       = phase;
        this.filename    = filename;
        this.chunkIndex  = chunkIndex;
        this.totalChunks = totalChunks;
        this.data        = data;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, phase    == null ? "" : phase);
        ByteBufUtils.writeUTF8String(buf, filename == null ? "" : filename);
        buf.writeInt(chunkIndex);
        buf.writeInt(totalChunks);
        buf.writeInt(data == null ? 0 : data.length);
        if (data != null && data.length > 0) buf.writeBytes(data);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        phase       = ByteBufUtils.readUTF8String(buf);
        filename    = ByteBufUtils.readUTF8String(buf);
        chunkIndex  = buf.readInt();
        totalChunks = buf.readInt();
        int len = buf.readInt();
        data = new byte[len];
        if (len > 0) buf.readBytes(data);
    }

    // -------------------------------------------------------------------------

    public static class Handler implements IMessageHandler<PacketHGMusicChunk, IMessage> {

        // key = "phase/filename", value = array of chunk payloads indexed by chunkIndex
        private static final ConcurrentHashMap<String, byte[][]> pending = new ConcurrentHashMap<>();
        // How many files we still need to finish writing before we reload resources.
        private static final AtomicInteger pendingFiles = new AtomicInteger(0);

        /** Called by PacketHGMusicManifest.Handler before requests are sent. */
        public static void setPendingCount(int count) {
            pendingFiles.set(count);
            pending.clear();
        }

        /** Returns the client-side herosmp_hg_music directory. */
        public static File getMusicDir(Minecraft mc) {
            return new File(mc.gameDir, "herosmp_hg_music");
        }

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketHGMusicChunk msg, MessageContext ctx) {
            String key = msg.phase + "/" + msg.filename;

            // Store this chunk.
            byte[][] chunks = pending.computeIfAbsent(key, k -> new byte[msg.totalChunks][]);
            chunks[msg.chunkIndex] = msg.data;

            // Check if all chunks for this file have arrived.
            boolean complete = true;
            for (byte[] c : chunks) {
                if (c == null) { complete = false; break; }
            }

            if (!complete) return null;

            // Assemble and write to disk on the main thread.
            byte[][] finalChunks = pending.remove(key);
            Minecraft mc = Minecraft.getMinecraft();
            mc.addScheduledTask(() -> {
                try {
                    writeFile(mc, msg.phase, msg.filename, finalChunks);
                } catch (IOException e) {
                    System.err.println("[HeroSMP] Failed to write music file " + key + ": " + e.getMessage());
                }

                // When all files are done, reload sound resources.
                if (pendingFiles.decrementAndGet() <= 0) {
                    mc.refreshResources();
                }
            });

            return null;
        }

        private static void writeFile(Minecraft mc, String phase, String filename, byte[][] chunks) throws IOException {
            File phaseDir = new File(getMusicDir(mc), phase);
            phaseDir.mkdirs();
            File out = new File(phaseDir, filename);

            // Calculate total length.
            int total = 0;
            for (byte[] c : chunks) total += c.length;

            byte[] assembled = new byte[total];
            int pos = 0;
            for (byte[] c : chunks) {
                System.arraycopy(c, 0, assembled, pos, c.length);
                pos += c.length;
            }

            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(assembled);
            }
        }
    }
}
