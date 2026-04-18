package com.matoon.herosmp.npc.pvp;

import net.minecraft.init.Biomes;
import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.common.DimensionManager;

import java.util.HashMap;
import java.util.Map;

public class ArenaWorldProvider extends WorldProvider {
    public static final int ARENA_CHUNKS_ACROSS = 10;
    public static final int ARENA_RADIUS_BLOCKS = (ARENA_CHUNKS_ACROSS * 16) / 2;
    public static final int SLOT_REGION_CHUNKS = 32;
    public static final int SLOT_REGION_BLOCKS = SLOT_REGION_CHUNKS * 16;
    public static final int ARENA_MIN_LOCAL_CHUNK = (SLOT_REGION_CHUNKS - ARENA_CHUNKS_ACROSS) / 2;
    public static final int ARENA_MAX_LOCAL_CHUNK = ARENA_MIN_LOCAL_CHUNK + ARENA_CHUNKS_ACROSS - 1;
    public static final int ARENA_MIN_LOCAL_BLOCK = ARENA_MIN_LOCAL_CHUNK * 16;
    public static final int ARENA_MAX_LOCAL_BLOCK = ARENA_MAX_LOCAL_CHUNK * 16 + 15;
    private static final Map<Long, Long> SLOT_SEEDS = new HashMap<Long, Long>();
    private static final Map<Long, Integer> SLOT_BIOMES = new HashMap<Long, Integer>();

    public static synchronized void setArenaSlotSeed(int slotX, int slotZ, long seed) {
        SLOT_SEEDS.put(slotKey(slotX, slotZ), seed);
    }

    public static synchronized void setArenaSlotBiome(int slotX, int slotZ, Biome biome) {
        long key = slotKey(slotX, slotZ);
        if (biome == null) {
            SLOT_BIOMES.remove(key);
            return;
        }
        SLOT_BIOMES.put(key, Biome.getIdForBiome(biome));
    }

    public static synchronized void clearArenaSlot(int slotX, int slotZ) {
        long key = slotKey(slotX, slotZ);
        SLOT_SEEDS.remove(key);
        SLOT_BIOMES.remove(key);
    }

    public static synchronized Long getArenaSlotSeed(int slotX, int slotZ) {
        return SLOT_SEEDS.get(slotKey(slotX, slotZ));
    }

    public static synchronized Biome getArenaSlotBiome(int slotX, int slotZ) {
        Integer id = SLOT_BIOMES.get(slotKey(slotX, slotZ));
        return id == null ? Biomes.PLAINS : Biome.getBiome(id, Biomes.PLAINS);
    }

    public static int getArenaSlotXForChunk(int chunkX) {
        return Math.floorDiv(chunkX, SLOT_REGION_CHUNKS);
    }

    public static int getArenaSlotZForChunk(int chunkZ) {
        return Math.floorDiv(chunkZ, SLOT_REGION_CHUNKS);
    }

    public static int getLocalChunkInSlot(int chunkCoordinate) {
        return Math.floorMod(chunkCoordinate, SLOT_REGION_CHUNKS);
    }

    public static int getArenaSlotXForBlock(int blockX) {
        return Math.floorDiv(blockX, SLOT_REGION_BLOCKS);
    }

    public static int getArenaSlotZForBlock(int blockZ) {
        return Math.floorDiv(blockZ, SLOT_REGION_BLOCKS);
    }

    private static long slotKey(int slotX, int slotZ) {
        return (((long) slotX) << 32) ^ (slotZ & 0xFFFFFFFFL);
    }

    @Override
    protected void init() {
        super.init();
        this.biomeProvider = new ArenaBiomeProvider();
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
