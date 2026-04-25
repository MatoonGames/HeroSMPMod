package com.matoon.herosmp.hungergames.world;

import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.common.DimensionManager;

/**
 * WorldProvider for Hunger Games dimensions.
 * Each HG match gets its own dimension with a unique ID.
 * This ensures complete world isolation with separate save files.
 */
public class HungerGamesWorldProvider extends WorldProvider {

    /**
     * Match-mode chunk boundary, in chunk coordinates.
     * Set by HungerGamesWorldManager after initDimension() for active matches.
     * When non-null, chunks outside this box + CHUNK_PADDING are voided by the generator
     * so the client never tries to load the full pre-built region files past the border.
     * Left null for configure-map sessions so editors can roam freely.
     */
    public int matchCenterChunkX  = Integer.MIN_VALUE;
    public int matchCenterChunkZ  = Integer.MIN_VALUE;
    public int matchBorderChunks  = 0; // half-width in chunks (radius)

    /** Extra void padding beyond the border boundary (in chunks). */
    public static final int CHUNK_PADDING = 4;

    @Override
    protected void init() {
        super.init();
        this.hasSkyLight = true;
    }

    @Override
    public DimensionType getDimensionType() {
        try {
            return DimensionManager.getProviderType(getDimension());
        } catch (IllegalArgumentException e) {
            // Fallback to overworld type if dimension type not found
            return DimensionType.OVERWORLD;
        }
    }

    @Override
    public IChunkGenerator createChunkGenerator() {
        // Use custom chunk generator that supports world templates
        return new HungerGamesChunkGenerator(this.world);
    }

    @Override
    public boolean canRespawnHere() {
        // Players should not respawn in HG dimensions
        return false;
    }

    @Override
    public boolean isSurfaceWorld() {
        return true;
    }

    @Override
    public String getSaveFolder() {
        // Each dimension gets its own save folder: DIM-8001, DIM-8002, etc.
        return "DIM" + getDimension();
    }

    @Override
    public boolean canCoordinateBeSpawn(int x, int z) {
        return true;
    }

    @Override
    public float calculateCelestialAngle(long worldTime, float partialTicks) {
        // Normal day/night cycle
        return super.calculateCelestialAngle(worldTime, partialTicks);
    }
}
