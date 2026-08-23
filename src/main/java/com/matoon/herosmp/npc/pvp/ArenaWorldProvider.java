package com.matoon.herosmp.npc.pvp;

import net.minecraft.world.DimensionType;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.DimensionManager;

import java.util.HashMap;
import java.util.Map;

public class ArenaWorldProvider extends WorldProvider {

    /**
     * Arena layout (chunk coordinates):
     *
     *   0     : void (never touched)
     *   1     : populate-only buffer — terrain generated, never decorated.
     *             The overworld generator's populate() step requests the 8
     *             immediate neighbours; restricting decoration to chunks 2-9
     *             means those neighbour requests always land on chunk 1 or
     *             another interior chunk, never on void.
     *   2-9   : playable arena (8 chunks = 128 × 128 blocks, fully decorated)
     *   10    : populate-only buffer (mirror of chunk 1)
     *   11+   : void
     *
     * The world border and enforcePlayerInsideArena both sit at the outer edge
     * of the playable chunks so there is no gap between the visible terrain edge
     * and the boundary wall.
     *
     *   ARENA_MIN_BLOCK =  2 * 16 = 32
     *   ARENA_MAX_BLOCK = 10 * 16 - 1 = 159
     */
    public static final int ARENA_CHUNKS_ACROSS = 8;

    /** Single buffer chunk on each side for populate neighbour safety. */
    public static final int ARENA_FIRST_GEN   = 1;
    public static final int ARENA_LAST_GEN    = 10;

    /** First/last playable (and decorated) chunk. */
    public static final int ARENA_FIRST_CHUNK = 2;
    public static final int ARENA_LAST_CHUNK  = ARENA_FIRST_CHUNK + ARENA_CHUNKS_ACROSS - 1; // 9

    /**
     * Spawn/chest/enforcement bounds use the playable chunk range.
     * ARENA_MIN_BLOCK / ARENA_MAX_BLOCK are kept for spawn and chest placement.
     */
    public static final int ARENA_MIN_BLOCK = ARENA_FIRST_CHUNK * 16;            // 32
    public static final int ARENA_MAX_BLOCK = (ARENA_LAST_CHUNK + 1) * 16 - 1;   // 159

    /**
     * The world border uses block-face coordinates (not block indices) so the wall
     * sits exactly at the chunk boundary with no half-block offset.
     *   ARENA_BORDER_START = first block face of chunk FIRST_GEN = 16.0
     *   ARENA_BORDER_END   = last  block face of chunk LAST_GEN  = 176.0
     *   center             = (16 + 176) / 2 = 96.0
     *   diameter           = 176 - 16      = 160
     */
    public static final double ARENA_BORDER_START    = ARENA_FIRST_GEN * 16;          // 16.0
    public static final double ARENA_BORDER_END      = (ARENA_LAST_GEN + 1) * 16;     // 176.0
    public static final double ARENA_BORDER_CENTER   = (ARENA_BORDER_START + ARENA_BORDER_END) / 2.0; // 96.0
    public static final double ARENA_BORDER_DIAMETER = ARENA_BORDER_END - ARENA_BORDER_START;         // 160.0

    // Per-dimension seed and chunk offset, keyed by dimension ID.
    // The chunk offset shifts where in the infinite overworld noise field the arena
    // is sampled from, guaranteeing a different biome region every match.
    private static final Map<Integer, Long> DIM_SEEDS   = new HashMap<Integer, Long>();
    private static final Map<Integer, int[]> DIM_OFFSETS = new HashMap<Integer, int[]>();
    private static final Map<Integer, Integer> DIM_PLAYABLE_CHUNKS = new HashMap<Integer, Integer>();

    public static synchronized void setArenaDimension(int dimensionId, long seed, int chunkOffsetX, int chunkOffsetZ) {
        setArenaDimension(dimensionId, seed, chunkOffsetX, chunkOffsetZ, ARENA_CHUNKS_ACROSS);
    }

    public static synchronized void setArenaDimension(int dimensionId, long seed, int chunkOffsetX, int chunkOffsetZ,
                                                      int playableChunksAcross) {
        DIM_SEEDS.put(dimensionId, seed);
        DIM_OFFSETS.put(dimensionId, new int[]{chunkOffsetX, chunkOffsetZ});
        DIM_PLAYABLE_CHUNKS.put(dimensionId, Math.max(ARENA_CHUNKS_ACROSS, playableChunksAcross));
    }

    public static synchronized void clearArenaDimension(int dimensionId) {
        DIM_SEEDS.remove(dimensionId);
        DIM_OFFSETS.remove(dimensionId);
        DIM_PLAYABLE_CHUNKS.remove(dimensionId);
    }

    public static synchronized Long getArenaDimensionSeed(int dimensionId) {
        return DIM_SEEDS.get(dimensionId);
    }

    /** Returns {chunkOffsetX, chunkOffsetZ}, or {0,0} if not set. */
    public static synchronized int[] getArenaDimensionOffset(int dimensionId) {
        int[] off = DIM_OFFSETS.get(dimensionId);
        return off != null ? off : new int[]{0, 0};
    }

    public static synchronized int getPlayableChunksAcross(int dimensionId) {
        Integer value = DIM_PLAYABLE_CHUNKS.get(dimensionId);
        return value == null ? ARENA_CHUNKS_ACROSS : value;
    }

    public static int getLastPlayableChunk(int dimensionId) {
        return ARENA_FIRST_CHUNK + getPlayableChunksAcross(dimensionId) - 1;
    }

    public static int getLastGeneratedChunk(int dimensionId) {
        return getLastPlayableChunk(dimensionId) + 1;
    }

    @Override
    protected void init() {
        super.init();
        // Replace the default BiomeProvider with one seeded from the per-match seed.
        // ChunkGeneratorOverworld calls world.getBiomeProvider() for all biome lookups,
        // so this determines which biomes appear. The seed is set in PvpQueueManager
        // before initDimension() is called so it is available here.
        Long seed = getArenaDimensionSeed(getDimension());
        if (seed != null) {
            WorldSettings settings = new WorldSettings(
                    seed, GameType.SURVIVAL, false, false, WorldType.DEFAULT);
            WorldInfo info = new WorldInfo(settings, "arena");
            this.biomeProvider = new BiomeProvider(info);
        }
    }

    @Override
    public DimensionType getDimensionType() {
        try {
            return DimensionManager.getProviderType(getDimension());
        } catch (IllegalArgumentException ignored) {
            return DimensionType.OVERWORLD;
        }
    }

    @Override
    public IChunkGenerator createChunkGenerator() {
        return new BoundedOverworldChunkGenerator(this.world);
    }

    @Override
    public boolean canRespawnHere() {
        return false;
    }

    @Override
    public boolean isSurfaceWorld() {
        return true;
    }

    @Override
    public String getSaveFolder() {
        return "DIM" + getDimension();
    }
}
