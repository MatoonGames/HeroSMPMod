package com.matoon.herosmp.hungergames.music;

import com.matoon.herosmp.network.ModNetwork;
import com.matoon.herosmp.network.PacketHGMusicChunk;
import com.matoon.herosmp.network.PacketHGMusicControl;
import com.matoon.herosmp.network.PacketHGMusicManifest;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;

public class HungerGamesMusicManager {

    public static final String LOBBY  = "Lobby";
    public static final String PHASE1 = "Phase1";
    public static final String PHASE2 = "Phase2";
    public static final String PHASE3 = "Phase3";

    static final String[] ALL_PHASES = {LOBBY, PHASE1, PHASE2, PHASE3};

    private final File musicDir;
    private final Random random = new Random();
    private final Map<String, List<String>> tracksByPhase = new HashMap<>();
    private List<PacketHGMusicManifest.FileEntry> manifestEntries = Collections.emptyList();

    public HungerGamesMusicManager(File musicDir) {
        this.musicDir = musicDir;
        for (String phase : ALL_PHASES) new File(musicDir, phase).mkdirs();
        scan();
    }

    public File getMusicDir() { return musicDir; }

    public void scan() {
        tracksByPhase.clear();
        List<PacketHGMusicManifest.FileEntry> manifest = new ArrayList<>();
        for (String phase : ALL_PHASES) {
            List<String> tracks = new ArrayList<>();
            File dir = new File(musicDir, phase);
            File[] files = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".ogg"));
            if (files != null) {
                for (File f : files) {
                    tracks.add(f.getName().substring(0, f.getName().length() - 4));
                    manifest.add(new PacketHGMusicManifest.FileEntry(phase, f.getName(), (int) f.length()));
                }
            }
            tracksByPhase.put(phase, tracks);
        }
        manifestEntries = Collections.unmodifiableList(manifest);
    }

    public boolean hasTracks(String phase) {
        List<String> list = tracksByPhase.get(phase);
        return list != null && !list.isEmpty();
    }

    public boolean hasAnyTracks() {
        for (String phase : ALL_PHASES) if (hasTracks(phase)) return true;
        return false;
    }

    @Nullable
    public String pickTrack(String phase) {
        List<String> list = tracksByPhase.getOrDefault(phase, Collections.emptyList());
        return list.isEmpty() ? null : list.get(random.nextInt(list.size()));
    }

    public static ResourceLocation soundLocation(String phase, String trackName) {
        return new ResourceLocation("herosmp", "hg_music." + phase.toLowerCase(Locale.ROOT) + "." + trackName);
    }

    public void sendSpecificTrack(String phase, String track, EntityPlayerMP player) {
        ModNetwork.CHANNEL.sendTo(new PacketHGMusicControl(phase, track), player);
    }

    public void stopMusicForPlayer(EntityPlayerMP player) {
        ModNetwork.CHANNEL.sendTo(new PacketHGMusicControl(true), player);
    }

    public void stopMusicForAll(Iterable<EntityPlayerMP> players) {
        PacketHGMusicControl packet = new PacketHGMusicControl(true);
        for (EntityPlayerMP p : players)
            ModNetwork.CHANNEL.sendTo(packet, p);
    }

    /** Builds a manifest listing every .ogg file on the server. */
    public PacketHGMusicManifest buildManifest() {
        return new PacketHGMusicManifest(manifestEntries);
    }

    /**
     * Reads the requested .ogg file and sends it to the player as sequential
     * {@link PacketHGMusicChunk} packets of up to {@link PacketHGMusicChunk#CHUNK_SIZE} bytes.
     */
    public void sendChunks(EntityPlayerMP player, String phase, String filename) {
        // Validate phase to prevent directory traversal.
        boolean validPhase = false;
        for (String p : ALL_PHASES) { if (p.equals(phase)) { validPhase = true; break; } }
        if (!validPhase) return;

        // Validate filename — must end with .ogg and contain no path separators.
        if (filename == null || !filename.endsWith(".ogg")
                || filename.contains("/") || filename.contains("\\")
                || filename.contains("..")) return;

        File file = new File(new File(musicDir, phase), filename);
        if (!file.exists() || !file.isFile()) return;

        long fileLength = file.length();
        int total = (int) Math.ceil(fileLength / (double) PacketHGMusicChunk.CHUNK_SIZE);
        if (total == 0) total = 1;

        // Stream one network chunk at a time. Large tracks no longer require a second full-file
        // byte array (plus every copied packet chunk) to coexist on the server heap.
        try (InputStream input = Files.newInputStream(file.toPath())) {
            for (int i = 0; i < total; i++) {
                int expected=(int)Math.min(PacketHGMusicChunk.CHUNK_SIZE,fileLength-(long)i*PacketHGMusicChunk.CHUNK_SIZE);
                byte[] chunk=new byte[Math.max(0,expected)];int offset=0;
                while(offset<chunk.length){int read=input.read(chunk,offset,chunk.length-offset);if(read<0)break;offset+=read;}
                if(offset<chunk.length)chunk=Arrays.copyOf(chunk,offset);
                ModNetwork.CHANNEL.sendTo(new PacketHGMusicChunk(phase, filename, i, total, chunk), player);
            }
        } catch (IOException e) {
            System.err.println("[HeroSMP] Could not read music file " + file + ": " + e.getMessage());
        }
    }
}
