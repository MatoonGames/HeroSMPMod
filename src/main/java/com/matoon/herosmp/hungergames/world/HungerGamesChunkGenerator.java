package com.matoon.herosmp.hungergames.world;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Biomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.IChunkGenerator;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Chunk generator for Hunger Games dimensions.
 *
 * HG dimensions are always pre-built map copies — their chunks come from the
 * region files copied into the dimension's save folder before the dimension is
 * initialised.  This generator is only invoked for chunks that do NOT exist in
 * those region files (i.e. a player somehow reaches the edge of the map).  For
 * those out-of-bounds chunks we return a void/empty chunk so that no random
 * terrain or cross-dimension data can bleed in.
 *
 * In active match mode the provider sets {@link HungerGamesWorldProvider#matchCenterChunkX},
 * {@link HungerGamesWorldProvider#matchCenterChunkZ}, and
 * {@link HungerGamesWorldProvider#matchBorderChunks}.  Any chunk request whose
 * coordinates fall outside that radius + {@link HungerGamesWorldProvider#CHUNK_PADDING}
 * is immediately returned as a void chunk — the chunk loader never touches disk for
 * those positions.  This dramatically reduces the number of region-file reads on
 * match join for large maps.
 *
 * Configure-map sessions leave those fields at their default sentinel values so
 * editors can roam across the full map without hitting artificial voids.
 */
public class HungerGamesChunkGenerator implements IChunkGenerator {

    private final World world;

    public HungerGamesChunkGenerator(World world) {
        this.world = world;
    }

    @Override
    public Chunk generateChunk(int x, int z) {
        // If this dimension has match-mode bounds set, void any chunk that is outside
        // the border radius + CHUNK_PADDING without touching the region files at all.
        if (world.provider instanceof HungerGamesWorldProvider) {
            HungerGamesWorldProvider provider = (HungerGamesWorldProvider) world.provider;
            if (provider.matchCenterChunkX != Integer.MIN_VALUE) {
                int limit = provider.matchBorderChunks + HungerGamesWorldProvider.CHUNK_PADDING;
                int dx = Math.abs(x - provider.matchCenterChunkX);
                int dz = Math.abs(z - provider.matchCenterChunkZ);
                if (dx > limit || dz > limit) {
                    return emptyChunk(x, z);
                }
            }
        }

        // Return an empty (void) chunk for any position not covered by the map's
        // region files.  Minecraft loads existing chunks from disk before calling
        // this method, so real map chunks are never affected.
        return emptyChunk(x, z);
    }

    private Chunk emptyChunk(int x, int z) {
        Chunk chunk = new Chunk(this.world, x, z);
        byte[] biomeArray = chunk.getBiomeArray();
        byte plains = (byte) Biome.getIdForBiome(Biomes.PLAINS);
        for (int i = 0; i < biomeArray.length; i++) {
            biomeArray[i] = plains;
        }
        chunk.generateSkylightMap();
        return chunk;
    }

    @Override public void populate(int x, int z) {}
    @Override public boolean generateStructures(Chunk chunkIn, int x, int z) { return false; }
    @Override public List<Biome.SpawnListEntry> getPossibleCreatures(EnumCreatureType creatureType, BlockPos pos) {
        return world.getBiome(pos).getSpawnableList(creatureType);
    }
    @Nullable
    @Override public BlockPos getNearestStructurePos(World worldIn, String structureName, BlockPos position, boolean findUnexplored) { return null; }
    @Override public void recreateStructures(Chunk chunkIn, int x, int z) {}
    @Override public boolean isInsideStructure(World worldIn, String structureName, BlockPos pos) { return false; }
}
