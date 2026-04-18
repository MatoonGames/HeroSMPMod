package com.matoon.herosmp.npc.pvp;

import net.minecraft.init.Biomes;
import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProviderSingle;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.common.DimensionManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class ArenaWorldProvider extends WorldProvider {
    public static final int ARENA_CHUNKS_ACROSS = 10;
    public static final int ARENA_RADIUS_BLOCKS = (ARENA_CHUNKS_ACROSS * 16) / 2;
    private static final Map<Integer, Long> DIMENSION_SEEDS = new HashMap<Integer, Long>();
    private static final Map<Integer, Integer> DIMENSION_BIOMES = new HashMap<Integer, Integer>();
    private static final AtomicLong FALLBACK_SEED_COUNTER = new AtomicLong(System.nanoTime());

    public static synchronized void setArenaSeed(int dimensionId, long seed) {
        DIMENSION_SEEDS.put(dimensionId, seed);
    }

    public static synchronized void setArenaBiome(int dimensionId, Biome biome) {
        if (biome == null) {
            DIMENSION_BIOMES.remove(dimensionId);
            return;
        }
        DIMENSION_BIOMES.put(dimensionId, Biome.getIdForBiome(biome));
    }

    public static synchronized void clearArenaSeed(int dimensionId) {
        DIMENSION_SEEDS.remove(dimensionId);
    }

    public static synchronized void clearArenaBiome(int dimensionId) {
        DIMENSION_BIOMES.remove(dimensionId);
    }

    public static synchronized long getArenaSeed(int dimensionId, long fallback) {
        Long seed = DIMENSION_SEEDS.get(dimensionId);
        return seed == null ? fallback : seed;
    }

    private static synchronized Long consumeArenaSeed(int dimensionId) {
        return DIMENSION_SEEDS.remove(dimensionId);
    }

    private static synchronized Biome consumeArenaBiome(int dimensionId) {
        Integer id = DIMENSION_BIOMES.remove(dimensionId);
        if (id == null) {
            return null;
        }
        return Biome.getBiome(id);
    }

    @Override
    protected void init() {
        super.init();
        Biome assigned = consumeArenaBiome(getDimension());
        this.biomeProvider = new BiomeProviderSingle(assigned != null ? assigned : Biomes.PLAINS);
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
        long fallback = mix64(
                this.world.getSeed()
                        ^ (341873128712L * (long) getDimension())
                        ^ ThreadLocalRandom.current().nextLong()
                        ^ FALLBACK_SEED_COUNTER.getAndIncrement()
        );
        Long assigned = consumeArenaSeed(getDimension());
        long seed = assigned != null ? assigned.longValue() : fallback;
        return new BoundedOverworldChunkGenerator(this.world, ARENA_CHUNKS_ACROSS, seed);
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
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
