package com.matoon.herosmp.hungergames.world;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Biomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkGeneratorOverworld;
import net.minecraft.world.gen.IChunkGenerator;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Chunk generator for Hunger Games worlds.
 * Supports both procedural generation and pre-loaded world templates.
 */
public class HungerGamesChunkGenerator implements IChunkGenerator {

    private final World world;
    private IChunkGenerator delegate;
    private boolean useTemplate = false;

    public HungerGamesChunkGenerator(World world) {
        this.world = world;
        // Default to overworld-style generation
        this.delegate = new ChunkGeneratorOverworld(
            world,
            world.getSeed(),
            world.getWorldInfo().isMapFeaturesEnabled(),
            world.getWorldInfo().getGeneratorOptions()
        );
    }

    /**
     * Enable template mode - chunks will be loaded from pre-built world data
     * instead of being procedurally generated.
     */
    public void setTemplateMode(boolean useTemplate) {
        this.useTemplate = useTemplate;
    }

    @Override
    public Chunk generateChunk(int x, int z) {
        if (useTemplate) {
            // When using templates, chunks are loaded from region files
            // Return empty chunk - actual data will be loaded from template
            Chunk chunk = new Chunk(this.world, x, z);
            byte[] biomeArray = chunk.getBiomeArray();
            byte plains = (byte) Biome.getIdForBiome(Biomes.PLAINS);
            for (int i = 0; i < biomeArray.length; i++) {
                biomeArray[i] = plains;
            }
            chunk.generateSkylightMap();
            return chunk;
        }
        
        // Use procedural generation
        return delegate.generateChunk(x, z);
    }

    @Override
    public void populate(int x, int z) {
        if (!useTemplate && delegate != null) {
            delegate.populate(x, z);
        }
    }

    @Override
    public boolean generateStructures(Chunk chunkIn, int x, int z) {
        if (!useTemplate && delegate != null) {
            return delegate.generateStructures(chunkIn, x, z);
        }
        return false;
    }

    @Override
    public List<Biome.SpawnListEntry> getPossibleCreatures(EnumCreatureType creatureType, BlockPos pos) {
        if (!useTemplate && delegate != null) {
            return delegate.getPossibleCreatures(creatureType, pos);
        }
        return world.getBiome(pos).getSpawnableList(creatureType);
    }

    @Nullable
    @Override
    public BlockPos getNearestStructurePos(World worldIn, String structureName, BlockPos position, boolean findUnexplored) {
        if (!useTemplate && delegate != null) {
            return delegate.getNearestStructurePos(worldIn, structureName, position, findUnexplored);
        }
        return null;
    }

    @Override
    public void recreateStructures(Chunk chunkIn, int x, int z) {
        if (!useTemplate && delegate != null) {
            delegate.recreateStructures(chunkIn, x, z);
        }
    }

    @Override
    public boolean isInsideStructure(World worldIn, String structureName, BlockPos pos) {
        if (!useTemplate && delegate != null) {
            return delegate.isInsideStructure(worldIn, structureName, pos);
        }
        return false;
    }
}
