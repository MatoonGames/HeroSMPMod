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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BoundedOverworldChunkGenerator implements IChunkGenerator {

    private final World world;
    private final Map<Long, IChunkGenerator> delegates = new HashMap<Long, IChunkGenerator>();

    public BoundedOverworldChunkGenerator(World world) {
        this.world = world;
    }

    private long slotKey(int slotX, int slotZ) {
        return (((long) slotX) << 32) ^ (slotZ & 0xFFFFFFFFL);
    }

    private boolean inArenaBounds(int chunkX, int chunkZ) {
        int localX = ArenaWorldProvider.getLocalChunkInSlot(chunkX);
        int localZ = ArenaWorldProvider.getLocalChunkInSlot(chunkZ);
        return localX >= ArenaWorldProvider.ARENA_MIN_LOCAL_CHUNK
                && localX <= ArenaWorldProvider.ARENA_MAX_LOCAL_CHUNK
                && localZ >= ArenaWorldProvider.ARENA_MIN_LOCAL_CHUNK
                && localZ <= ArenaWorldProvider.ARENA_MAX_LOCAL_CHUNK;
    }

    private IChunkGenerator delegateFor(int chunkX, int chunkZ) {
        int slotX = ArenaWorldProvider.getArenaSlotXForChunk(chunkX);
        int slotZ = ArenaWorldProvider.getArenaSlotZForChunk(chunkZ);
        Long seed = ArenaWorldProvider.getArenaSlotSeed(slotX, slotZ);
        if (seed == null) {
            return null;
        }

        long key = slotKey(slotX, slotZ);
        IChunkGenerator cached = delegates.get(key);
        if (cached != null) {
            return cached;
        }

        IChunkGenerator delegate = new ChunkGeneratorOverworld(
                world,
                seed.longValue(),
                world.getWorldInfo().isMapFeaturesEnabled(),
                world.getWorldInfo().getGeneratorOptions()
        );
        delegates.put(key, delegate);
        return delegate;
    }

    @Override
    public Chunk generateChunk(int x, int z) {
        IChunkGenerator delegate = delegateFor(x, z);
        if (delegate == null || !inArenaBounds(x, z)) {
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
        IChunkGenerator delegate = delegateFor(x, z);
        if (delegate != null && inArenaBounds(x, z)) {
            delegate.populate(x, z);
        }
    }

    @Override
    public boolean generateStructures(Chunk chunkIn, int x, int z) {
        IChunkGenerator delegate = delegateFor(x, z);
        if (delegate != null && inArenaBounds(x, z)) {
            return delegate.generateStructures(chunkIn, x, z);
        }
        return false;
    }

    @Override
    public List<Biome.SpawnListEntry> getPossibleCreatures(EnumCreatureType creatureType, BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        IChunkGenerator delegate = delegateFor(chunkX, chunkZ);
        if (delegate == null || !inArenaBounds(chunkX, chunkZ)) {
            return Collections.emptyList();
        }
        return delegate.getPossibleCreatures(creatureType, pos);
    }

    @Override
    public BlockPos getNearestStructurePos(World worldIn, String structureName, BlockPos position, boolean findUnexplored) {
        IChunkGenerator delegate = delegateFor(position.getX() >> 4, position.getZ() >> 4);
        return delegate == null ? null : delegate.getNearestStructurePos(worldIn, structureName, position, findUnexplored);
    }

    @Override
    public void recreateStructures(Chunk chunkIn, int x, int z) {
        IChunkGenerator delegate = delegateFor(x, z);
        if (delegate != null && inArenaBounds(x, z)) {
            delegate.recreateStructures(chunkIn, x, z);
        }
    }

    @Override
    public boolean isInsideStructure(World worldIn, String structureName, BlockPos pos) {
        IChunkGenerator delegate = delegateFor(pos.getX() >> 4, pos.getZ() >> 4);
        return delegate != null && delegate.isInsideStructure(worldIn, structureName, pos);
    }
}
