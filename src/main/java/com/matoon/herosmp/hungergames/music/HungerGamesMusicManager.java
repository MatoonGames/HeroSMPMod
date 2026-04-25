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

    public HungerGamesMusicManager(File musicDir) {
        this.musicDir = musicDir;
        for (String phase : ALL_PHASES) new File(musicDir, phase).mkdirs();
        scan();
    }

    public File getMusicDir() { return musicDir; }

    public void scan() {
        tracksByPhase.clear();
        for (String phase : ALL_PHASES) {
            List<String> tracks = new ArrayList<>();
            File dir = new File(musicDir, phase);
            File[] files = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".ogg"));
            if (files != null) {
                for (File f : files)
                    tracks.add(f.getName().substring(0, f.getName().length() - 4));
            }
            tracksByPhase.put(phase, tracks);
        }
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
        List<PacketHGMusicManifest.FileEntry> entries = new ArrayList<>();
        for (String phase : ALL_PHASES) {
            File dir = new File(musicDir, phase);
            File[] files = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".ogg"));
            if (files == null) continue;
            for (File f : files)
                entries.add(new PacketHGMusicManifest.FileEntry(phase, f.getName(), (int) f.length()));
        }
        return new PacketHGMusicManifest(entries);
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

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            System.err.println("[HeroSMP] Could not read music file " + file + ": " + e.getMessage());
            return;
        }

        int total = (int) Math.ceil(bytes.length / (double) PacketHGMusicChunk.CHUNK_SIZE);
        if (total == 0) total = 1;

        for (int i = 0; i < total; i++) {
            int start  = i * PacketHGMusicChunk.CHUNK_SIZE;
            int end    = Math.min(start + PacketHGMusicChunk.CHUNK_SIZE, bytes.length);
            byte[] chunk = Arrays.copyOfRange(bytes, start, end);
            ModNetwork.CHANNEL.sendTo(new PacketHGMusicChunk(phase, filename, i, total, chunk), player);
        }
    }
}
