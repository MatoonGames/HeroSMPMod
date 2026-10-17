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
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.*;

public class HungerGamesMusicManager {

    public static final String LOBBY  = "Lobby";
    public static final String PHASE1 = "Phase1";
    public static final String PHASE2 = "Phase2";
    public static final String PHASE3 = "Phase3";

    /** Looping phase music — these folders feed the random per-phase rotation. */
    static final String[] ALL_PHASES = {LOBBY, PHASE1, PHASE2, PHASE3};

    // One-shot stinger categories. These are NOT part of the looping rotation; they
    // are fired once on key game events and layer OVER whatever phase music is playing.
    public static final String STING_COUNTDOWN = "Countdown";
    public static final String STING_VICTORY   = "Victory";
    public static final String STING_DEATH     = "Death";
    /** Fighter-select theme for PVP rounds (plays while players pick kits, stops when the screen closes). */
    public static final String STING_KIT_SELECT = "KitSelect";
    /** Hit when the fighter-select VS splash slams in. */
    public static final String STING_FIGHT      = "Fight";

    static final String[] ALL_STINGERS = {STING_COUNTDOWN, STING_VICTORY, STING_DEATH, STING_KIT_SELECT, STING_FIGHT};

    /** Every folder the manager scans, serves in the manifest, and streams on request. */
    static final String[] ALL_FOLDERS = concat(ALL_PHASES, ALL_STINGERS);

    /** Fallback loop length used when a track's real duration could not be determined. */
    private static final int DEFAULT_LOOP_TICKS = 3600; // 3 minutes

    private final File musicDir;
    private final Random random = new Random();
    private final Map<String, List<String>> tracksByPhase = new HashMap<>();
    // Real playback length per track, keyed "folder/trackName". -1 when unknown.
    private final Map<String, Integer> durationTicks = new HashMap<>();
    private List<PacketHGMusicManifest.FileEntry> manifestEntries = Collections.emptyList();

    public HungerGamesMusicManager(File musicDir) {
        this.musicDir = musicDir;
        for (String folder : ALL_FOLDERS) new File(musicDir, folder).mkdirs();
        scan();
    }

    private static String[] concat(String[] a, String[] b) {
        String[] result = new String[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    public File getMusicDir() { return musicDir; }

    public void scan() {
        tracksByPhase.clear();
        durationTicks.clear();
        List<PacketHGMusicManifest.FileEntry> manifest = new ArrayList<>();
        for (String folder : ALL_FOLDERS) {
            List<String> tracks = new ArrayList<>();
            File dir = new File(musicDir, folder);
            File[] files = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".ogg"));
            if (files != null) {
                for (File f : files) {
                    String name = f.getName().substring(0, f.getName().length() - 4);
                    tracks.add(name);
                    manifest.add(new PacketHGMusicManifest.FileEntry(folder, f.getName(), (int) f.length()));
                    durationTicks.put(folder + "/" + name, readOggDurationTicks(f));
                }
            }
            tracksByPhase.put(folder, tracks);
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

    /**
     * Picks a random track for the phase, avoiding {@code exclude} when more than one
     * track is available so consecutive loops don't replay the same song.
     */
    @Nullable
    public String pickTrackExcluding(String phase, @Nullable String exclude) {
        List<String> list = tracksByPhase.getOrDefault(phase, Collections.emptyList());
        if (list.isEmpty()) return null;
        if (exclude == null || list.size() == 1) return list.get(random.nextInt(list.size()));
        String pick;
        do { pick = list.get(random.nextInt(list.size())); } while (pick.equals(exclude));
        return pick;
    }

    /**
     * Real playback length of a track in server ticks (20 ticks = 1 second), or -1 if
     * the file is missing or its duration could not be parsed. Callers should fall back
     * to a sensible default when this returns a non-positive value.
     */
    public int getTrackDurationTicks(String phase, String track) {
        if (track == null) return -1;
        Integer d = durationTicks.get(phase + "/" + track);
        return d != null ? d : -1;
    }

    public static ResourceLocation soundLocation(String phase, String trackName) {
        return new ResourceLocation("herosmp", "hg_music." + phase.toLowerCase(Locale.ROOT) + "." + trackName);
    }

    public void sendSpecificTrack(String phase, String track, EntityPlayerMP player) {
        ModNetwork.CHANNEL.sendTo(new PacketHGMusicControl(phase, track), player);
    }

    /**
     * Fires a one-shot stinger from the given category to every supplied player.
     * All listeners hear the same randomly-chosen track, and it plays OVER the current
     * looping phase music rather than replacing it. No-op if the category has no tracks.
     */
    public void sendStingerToAll(String category, Iterable<EntityPlayerMP> players) {
        String track = pickTrack(category);
        if (track == null) return;
        PacketHGMusicControl packet = new PacketHGMusicControl(category, track, true);
        for (EntityPlayerMP p : players)
            ModNetwork.CHANNEL.sendTo(packet, p);
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
        // Validate folder to prevent directory traversal (phases + stingers are all valid).
        boolean validPhase = false;
        for (String p : ALL_FOLDERS) { if (p.equals(phase)) { validPhase = true; break; } }
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

    // -------------------------------------------------------------------------
    // OGG Vorbis duration parsing
    //
    // Byte length is unreliable for duration (Vorbis is variable-bitrate), so we read
    // it directly from the container: duration = lastGranulePosition / sampleRate.
    //   - sampleRate comes from the Vorbis identification header in the first OGG page.
    //   - the final granule position (total sample count) lives in the last OggS page.
    // Returns the duration in server ticks, or -1 if the file isn't valid OGG Vorbis.
    // -------------------------------------------------------------------------

    private static int readOggDurationTicks(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long length = raf.length();
            if (length < 32) return -1;

            long sampleRate = readSampleRate(raf);
            if (sampleRate <= 0) return -1;

            long granule = readLastGranule(raf, length);
            if (granule <= 0) return -1;

            double seconds = (double) granule / (double) sampleRate;
            return (int) Math.round(seconds * 20.0);
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    /** Reads the audio sample rate from the Vorbis identification header (first page). */
    private static long readSampleRate(RandomAccessFile raf) throws IOException {
        raf.seek(0);
        if (raf.read() != 'O' || raf.read() != 'g' || raf.read() != 'g' || raf.read() != 'S')
            return -1;
        // Page header is 27 bytes; byte 26 is the segment count, followed by the segment
        // table. The first packet (the identification header) starts right after it.
        raf.seek(26);
        int segCount = raf.readUnsignedByte();
        raf.skipBytes(segCount);
        // Identification packet: 0x01 'v' 'o' 'r' 'b' 'i' 's', then version(4), channels(1),
        // sampleRate(4, little-endian).
        if (raf.read() != 1) return -1; // packet type must be 'identification'
        raf.skipBytes(6 + 4 + 1); // "vorbis" + vorbis_version + audio_channels
        return readLE32(raf);
    }

    /** Scans the file tail for the last OggS page and reads its 64-bit granule position. */
    private static long readLastGranule(RandomAccessFile raf, long length) throws IOException {
        int scan = (int) Math.min(length, 65536L);
        byte[] buf = new byte[scan];
        raf.seek(length - scan);
        raf.readFully(buf);
        for (int i = buf.length - 14; i >= 0; i--) {
            if (buf[i] == 'O' && buf[i + 1] == 'g' && buf[i + 2] == 'g' && buf[i + 3] == 'S') {
                long granule = 0;
                for (int b = 0; b < 8; b++) // granule position: 8 bytes LE at page offset +6
                    granule |= ((long) (buf[i + 6 + b] & 0xFF)) << (8 * b);
                return granule;
            }
        }
        return -1;
    }

    private static long readLE32(RandomAccessFile raf) throws IOException {
        int b0 = raf.read(), b1 = raf.read(), b2 = raf.read(), b3 = raf.read();
        if ((b0 | b1 | b2 | b3) < 0) return -1;
        return ((long) b0) | ((long) b1 << 8) | ((long) b2 << 16) | ((long) b3 << 24);
    }
}
