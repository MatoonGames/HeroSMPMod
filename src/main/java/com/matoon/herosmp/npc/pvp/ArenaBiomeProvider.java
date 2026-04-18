package com.matoon.herosmp.npc.pvp;

import net.minecraft.init.Biomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;

import java.util.Collections;
import java.util.List;
import java.util.Random;

public class ArenaBiomeProvider extends BiomeProvider {

    private Biome biomeFor(int x, int z) {
        int slotX = ArenaWorldProvider.getArenaSlotXForBlock(x);
        int slotZ = ArenaWorldProvider.getArenaSlotZForBlock(z);
        return ArenaWorldProvider.getArenaSlotBiome(slotX, slotZ);
    }

    @Override
    public Biome getBiome(BlockPos pos) {
        return biomeFor(pos.getX(), pos.getZ());
    }

    @Override
    public Biome getBiome(BlockPos pos, Biome defaultBiome) {
        Biome biome = biomeFor(pos.getX(), pos.getZ());
        return biome == null ? defaultBiome : biome;
    }

    @Override
    public Biome[] getBiomesForGeneration(Biome[] biomes, int x, int z, int width, int height) {
        int size = width * height;
        if (biomes == null || biomes.length < size) {
            biomes = new Biome[size];
        }
        for (int dz = 0; dz < height; dz++) {
            for (int dx = 0; dx < width; dx++) {
                biomes[dx + dz * width] = biomeFor((x + dx) << 2, (z + dz) << 2);
            }
        }
        return biomes;
    }

    @Override
    public Biome[] getBiomes(Biome[] listToReuse, int x, int z, int width, int length) {
        return getBiomes(listToReuse, x, z, width, length, true);
    }

    @Override
    public Biome[] getBiomes(Biome[] listToReuse, int x, int z, int width, int length, boolean cacheFlag) {
        int size = width * length;
        if (listToReuse == null || listToReuse.length < size) {
            listToReuse = new Biome[size];
        }
        for (int dz = 0; dz < length; dz++) {
            for (int dx = 0; dx < width; dx++) {
                listToReuse[dx + dz * width] = biomeFor(x + dx, z + dz);
            }
        }
        return listToReuse;
    }

    @Override
    public boolean areBiomesViable(int x, int z, int radius, List<Biome> allowed) {
        return allowed.contains(biomeFor(x, z));
    }

    @Override
    public BlockPos findBiomePosition(int x, int z, int range, List<Biome> biomes, Random random) {
        Biome biome = biomeFor(x, z);
        return biomes.contains(biome) ? new BlockPos(x, 0, z) : null;
    }

    @Override
    public List<Biome> getBiomesToSpawnIn() {
        return Collections.singletonList(Biomes.PLAINS);
    }
}
