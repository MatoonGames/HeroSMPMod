package com.matoon.herosmp.npc.pvp;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Biomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkGeneratorOverworld;
import net.minecraft.world.gen.IChunkGenerator;

import java.util.Collections;
import java.util.List;

public class BoundedOverworldChunkGenerator implements IChunkGenerator {

    private final World world;
    private final IChunkGenerator delegate;
    private final int minChunk;
    private final int maxChunk;

    public BoundedOverworldChunkGenerator(World world, int chunksAcross, long seed) {
        this.world = world;
        this.delegate = new ChunkGeneratorOverworld(
                world,
                seed,
                world.getWorldInfo().isMapFeaturesEnabled(),
                world.getWorldInfo().getGeneratorOptions()
        );
        int size = Math.max(2, chunksAcross);
        this.minChunk = -size / 2;
        this.maxChunk = this.minChunk + size - 1;
    }

    private boolean inBounds(int chunkX, int chunkZ) {
        return chunkX >= minChunk && chunkX <= maxChunk && chunkZ >= minChunk && chunkZ <= maxChunk;
    }

    @Override
    public Chunk generateChunk(int x, int z) {
        if (!inBounds(x, z)) {
            Chunk chunk = new Chunk(this.world, x, z);
            byte[] biomeArray = chunk.getBiomeArray();
            byte plains = (byte) Biome.getIdForBiome(Biomes.PLAINS);
            for (int i = 0; i < biomeArray.length; i++) {
                biomeArray[i] = plains;
            }
            chunk.generateSkylightMap();
            return chunk;
        }
        return delegate.generateChunk(x, z);
    }

    @Override
    public void populate(int x, int z) {
        if (inBounds(x, z)) {
            delegate.populate(x, z);
        }
    }

    @Override
    public boolean generateStructures(Chunk chunkIn, int x, int z) {
        if (inBounds(x, z)) {
            return delegate.generateStructures(chunkIn, x, z);
        }
        return false;
    }

    @Override
    public List<Biome.SpawnListEntry> getPossibleCreatures(EnumCreatureType creatureType, BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        if (!inBounds(chunkX, chunkZ)) {
            return Collections.emptyList();
        }
        return delegate.getPossibleCreatures(creatureType, pos);
    }

    @Override
    public BlockPos getNearestStructurePos(World worldIn, String structureName, BlockPos position, boolean findUnexplored) {
        return delegate.getNearestStructurePos(worldIn, structureName, position, findUnexplored);
    }

    @Override
    public void recreateStructures(Chunk chunkIn, int x, int z) {
        if (inBounds(x, z)) {
            delegate.recreateStructures(chunkIn, x, z);
        }
    }

    @Override
    public boolean isInsideStructure(World worldIn, String structureName, BlockPos pos) {
        return delegate.isInsideStructure(worldIn, structureName, pos);
    }
}
