package com.matoon.herosmp.npc.pvp;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Biomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.ChunkGeneratorOverworld;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.storage.WorldInfo;

import java.util.Collections;
import java.util.List;

/**
 * Delegates terrain generation to ChunkGeneratorOverworld in AMPLIFIED mode.
 *
 * Generation region:  FIRST_GEN .. LAST_GEN  (includes 2-chunk buffer on each side)
 * Playable region:    FIRST_CHUNK .. LAST_CHUNK  (inside world border)
 * Decoration region:  FIRST_CHUNK+1 .. LAST_CHUNK-1  (interior only, so populate's
 *                     neighbour requests always land on generated chunks)
 *
 * A large random chunk offset shifts which part of the infinite noise field is
 * sampled, guaranteeing different terrain and biomes every match.
 */
public class BoundedOverworldChunkGenerator implements IChunkGenerator {

    private final World world;
    private final int   dimId;

    private IChunkGenerator delegate;
    private int     offsetX;
    private int     offsetZ;
    private boolean initDone = false;

    public BoundedOverworldChunkGenerator(World world) {
        this.world = world;
        this.dimId = world.provider.getDimension();
    }

    /** True if this chunk should have terrain generated (includes 1-chunk buffer). */
    private boolean inGenBounds(int cx, int cz) {
        int last = ArenaWorldProvider.getLastGeneratedChunk(dimId);
        return cx >= ArenaWorldProvider.ARENA_FIRST_GEN && cx <= last
                && cz >= ArenaWorldProvider.ARENA_FIRST_GEN && cz <= last;
    }

    /**
     * Only decorate playable chunks (FIRST_PLAY..LAST_PLAY).
     * The buffer chunks (FIRST_GEN and LAST_GEN) are generated but never decorated,
     * so populate()'s 8-neighbour requests always land on generated chunks.
     */
    private boolean canDecorate(int cx, int cz) {
        int last = ArenaWorldProvider.getLastPlayableChunk(dimId);
        return cx >= ArenaWorldProvider.ARENA_FIRST_CHUNK && cx <= last
                && cz >= ArenaWorldProvider.ARENA_FIRST_CHUNK && cz <= last;
    }

    private boolean ensureInit() {
        if (initDone) return delegate != null;
        Long seed = ArenaWorldProvider.getArenaDimensionSeed(dimId);
        if (seed == null) return false;
        int[] off = ArenaWorldProvider.getArenaDimensionOffset(dimId);
        offsetX = off[0];
        offsetZ = off[1];
        // Build a WorldInfo with AMPLIFIED world type so the delegate uses amplified generation.
        WorldSettings settings = new WorldSettings(seed, net.minecraft.world.GameType.SURVIVAL, false, false, WorldType.AMPLIFIED);
        WorldInfo amplifiedInfo = new WorldInfo(settings, "arena");
        delegate = new ChunkGeneratorOverworld(world, seed, amplifiedInfo.isMapFeaturesEnabled(), amplifiedInfo.getGeneratorOptions());
        initDone = true;
        return true;
    }

    private int dx(int cx) { return cx + offsetX; }
    private int dz(int cz) { return cz + offsetZ; }

    private Chunk emptyChunk(int x, int z) {
        Chunk chunk = new Chunk(world, x, z);
        byte plains = (byte) Biome.getIdForBiome(Biomes.PLAINS);
        byte[] biomes = chunk.getBiomeArray();
        for (int i = 0; i < biomes.length; i++) biomes[i] = plains;
        chunk.generateSkylightMap();
        return chunk;
    }

    @Override
    public Chunk generateChunk(int x, int z) {
        if (!ensureInit() || !inGenBounds(x, z)) return emptyChunk(x, z);
        Chunk generated = delegate.generateChunk(dx(x), dz(z));
        // Re-key the chunk to actual arena coords (not the offset coords the delegate used).
        ChunkPrimer primer = new ChunkPrimer();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int y = 0; y < 256; y++) {
                    primer.setBlockState(lx, y, lz, generated.getBlockState(lx, y, lz));
                }
            }
        }
        Chunk result = new Chunk(world, primer, x, z);
        byte[] srcBiomes  = generated.getBiomeArray();
        byte[] destBiomes = result.getBiomeArray();
        System.arraycopy(srcBiomes, 0, destBiomes, 0, srcBiomes.length);
        result.generateSkylightMap();
        return result;
    }

    @Override
    public void populate(int x, int z) {
        if (!ensureInit() || !canDecorate(x, z)) return;
        // Use real chunk coords (x, z) — NOT the offset coords.
        // ChunkGeneratorOverworld.populate() calls world.getChunk(x±1, z±1) to load
        // neighbours before placing features. Passing offset coords would cause those
        // neighbour requests to go to far-out-of-bounds positions, returning empty
        // chunks and silently skipping all biome decoration (trees, foliage, etc.).
        delegate.populate(x, z);
    }

    @Override
    public boolean generateStructures(Chunk chunk, int x, int z) {
        if (!ensureInit() || !canDecorate(x, z)) return false;
        return delegate.generateStructures(chunk, x, z);
    }

    @Override
    public List<Biome.SpawnListEntry> getPossibleCreatures(EnumCreatureType type, BlockPos pos) {
        if (!ensureInit() || !inGenBounds(pos.getX() >> 4, pos.getZ() >> 4)) return Collections.emptyList();
        return delegate.getPossibleCreatures(type, pos);
    }

    @Override
    public BlockPos getNearestStructurePos(World worldIn, String name, BlockPos pos, boolean findUnexplored) {
        if (!ensureInit()) return null;
        return delegate.getNearestStructurePos(worldIn, name, pos, findUnexplored);
    }

    @Override
    public void recreateStructures(Chunk chunk, int x, int z) {
        if (!ensureInit() || !canDecorate(x, z)) return;
        delegate.recreateStructures(chunk, x, z);
    }

    @Override
    public boolean isInsideStructure(World worldIn, String name, BlockPos pos) {
        if (!ensureInit()) return false;
        return delegate.isInsideStructure(worldIn, name, pos);
    }
}
