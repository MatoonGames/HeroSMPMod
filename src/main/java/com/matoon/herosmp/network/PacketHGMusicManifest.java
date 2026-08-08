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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
            Minecraft mc = Minecraft.getMinecraft();
            // Do EVERYTHING on the client main thread — reconcile the cache, set the
            // pending count, and only THEN send the download requests. Sending the
            // requests after setPendingCount (which clears the chunk-accumulation map)
            // on the same thread guarantees no chunk can arrive and be wiped mid-flight,
            // fixing the race that previously stranded files and prevented the reload.
            mc.addScheduledTask(() -> reconcileAndDownload(mc, msg.entries));
            return null;
        }

        /**
         * Brings the client cache in line with the server's manifest: deletes tracks the
         * server no longer has, keeps ones that already match (so rejoins don't
         * re-download), and requests only the files that are missing or the wrong size.
         */
        @SideOnly(Side.CLIENT)
        private static void reconcileAndDownload(Minecraft mc, List<FileEntry> entries) {
            File cacheDir = PacketHGMusicChunk.Handler.getCacheDir(mc);

            // Relative "Phase/filename" paths the server currently offers.
            Set<String> wanted = new HashSet<>();
            for (FileEntry e : entries) wanted.add(e.phase + "/" + e.filename);

            // Remove cached files the server no longer has.
            boolean pruned = pruneStale(cacheDir, wanted);

            // Download anything missing or size-mismatched; keep exact matches.
            List<FileEntry> toDownload = new ArrayList<>();
            for (FileEntry e : entries) {
                File f = new File(new File(cacheDir, e.phase), e.filename);
                if (!f.isFile() || f.length() != e.totalBytes) toDownload.add(e);
            }

            if (toDownload.isEmpty()) {
                // Cache already current. Only re-register sounds if we removed stale
                // tracks; otherwise the startup/previous-login registration still holds.
                if (pruned) PacketHGMusicChunk.Handler.reloadSounds(mc);
                return;
            }

            PacketHGMusicChunk.Handler.setPendingCount(toDownload.size());
            for (FileEntry e : toDownload) {
                ModNetwork.CHANNEL.sendToServer(new PacketHGMusicRequest(e.phase, e.filename));
            }
        }

        /**
         * Deletes cached "phase/filename" files not present in {@code wanted}.
         * Never touches the server's source directory (the cache dir is separate).
         * Returns true if anything was deleted.
         */
        @SideOnly(Side.CLIENT)
        private static boolean pruneStale(File cacheDir, Set<String> wanted) {
            if (cacheDir == null || !cacheDir.isDirectory()) return false;
            File[] phaseDirs = cacheDir.listFiles(File::isDirectory);
            if (phaseDirs == null) return false;

            boolean deleted = false;
            for (File phaseDir : phaseDirs) {
                File[] files = phaseDir.listFiles(File::isFile);
                if (files == null) continue;
                for (File f : files) {
                    String rel = phaseDir.getName() + "/" + f.getName();
                    if (!wanted.contains(rel) && f.delete()) deleted = true;
                }
            }
            return deleted;
        }
    }
}
