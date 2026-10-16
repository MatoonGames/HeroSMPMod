package com.matoon.herosmp.client.gui.editor;

import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.storage.RegionFile;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Top-down render of a saved map, read straight from its Anvil region files on a background thread.
 *
 * The rendered window is a square around a centre point, split into {@link #TILE}-block tiles that
 * are rendered centre-outward and uploaded as textures on the client thread as they finish, so the
 * view fills in progressively. Each column is coloured with the block's vanilla map colour and shaded
 * against its northern neighbour like an in-game map; water darkens with depth. The surface height
 * of every column is kept for snapping placed points to the ground, and chests are collected from
 * the chunks' tile entities.
 */
public final class TopDownMap {

    public static final int TILE = 128;
    private static final int CHUNKS_PER_TILE = TILE / 16;
    private static final int VOID = 0xFF0D0E10;

    public final int originX, originZ, size, tiles;

    private final File regionDir;
    private final int[] pixels;          // ARGB per column in the window
    private final short[] surface;       // Y a player would stand at on the visible surface, -1 = unknown
    private final short[] colorY;        // Y of the coloured block (for shading)
    private final boolean[] tileReady;
    private final ResourceLocation[] tileTex;
    private final DynamicTexture[] tileDyn;
    private final List<BlockPos> chests = Collections.synchronizedList(new ArrayList<BlockPos>());

    private final Block[] idRemap = new Block[4096];
    private final int[] colorCache = new int[4096 * 16];
    private final byte[] waterCache = new byte[4096 * 16];
    /** 0 = players stand inside the block (pressure plate, carpet, flower...), 1 = on top of it. */
    private final byte[] standCache = new byte[4096 * 16];

    private volatile int tilesDone;
    private volatile boolean cancelled;
    private volatile String error;

    /** Window of {@code size} blocks (multiple of TILE) whose north-west corner is (originX, originZ). */
    public TopDownMap(File mapDir, int centerX, int centerZ, int halfSize) {
        int half = Math.max(TILE / 2, Math.min(512, halfSize));
        int s = ((half * 2 + TILE - 1) / TILE) * TILE;
        this.size = s;
        this.tiles = s / TILE;
        this.originX = Math.floorDiv(centerX - s / 2, 16) * 16;
        this.originZ = Math.floorDiv(centerZ - s / 2, 16) * 16;
        this.regionDir = new File(mapDir, "region");
        this.pixels = new int[s * s];
        this.surface = new short[s * s];
        this.colorY = new short[s * s];
        Arrays.fill(surface, (short) -1);
        Arrays.fill(colorY, (short) -1);
        Arrays.fill(pixels, VOID);
        Arrays.fill(colorCache, Integer.MIN_VALUE);
        this.tileReady = new boolean[tiles * tiles];
        this.tileTex = new ResourceLocation[tiles * tiles];
        this.tileDyn = new DynamicTexture[tiles * tiles];
        loadIdRemap(new File(mapDir, "level.dat"));
    }

    public void start() {
        Thread t = new Thread(this::run, "Beyonder map render");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY + 1);
        t.start();
    }

    public void dispose() {
        cancelled = true;
        Minecraft mc = Minecraft.getMinecraft();
        for (int i = 0; i < tileTex.length; i++) {
            if (tileTex[i] != null) {
                mc.getTextureManager().deleteTexture(tileTex[i]);
                tileTex[i] = null;
            }
        }
    }

    public boolean hasRegionData() { return regionDir.isDirectory(); }
    public boolean isFinished() { return tilesDone >= tiles * tiles || error != null; }
    public float progress() { return tilesDone / (float) (tiles * tiles); }
    public String error() { return error; }

    public List<BlockPos> chests() {
        synchronized (chests) { return new ArrayList<BlockPos>(chests); }
    }

    /** Texture for tile (tx, tz) or null while it's still rendering. Client thread only. */
    public ResourceLocation tileTexture(int tx, int tz) {
        return tileTex[tz * tiles + tx];
    }

    /**
     * Y a player would stand at on the column's visible surface (the same surface the map shows, so
     * glass domes and barrier ceilings are looked through), or -1 if outside the window / not
     * rendered yet.
     */
    public int standY(int x, int z) {
        int lx = x - originX, lz = z - originZ;
        if (lx < 0 || lz < 0 || lx >= size || lz >= size) return -1;
        return surface[lz * size + lx];
    }

    // ---------------------------------------------------------------------
    // Rendering thread
    // ---------------------------------------------------------------------

    private void run() {
        if (!regionDir.isDirectory()) {
            error = "This map has no region data.";
            return;
        }
        // Tiles nearest the centre first.
        List<int[]> order = new ArrayList<int[]>();
        for (int tz = 0; tz < tiles; tz++) for (int tx = 0; tx < tiles; tx++) order.add(new int[]{tx, tz});
        final float c = (tiles - 1) / 2f;
        Collections.sort(order, (a, b) -> Float.compare(
                (a[0] - c) * (a[0] - c) + (a[1] - c) * (a[1] - c),
                (b[0] - c) * (b[0] - c) + (b[1] - c) * (b[1] - c)));

        Map<Long, RegionFile> regions = new HashMap<Long, RegionFile>();
        try {
            for (int[] t : order) {
                if (cancelled) return;
                renderTile(t[0], t[1], regions);
                shadeTile(t[0], t[1]);
                final int[] tilePixels = copyTile(t[0], t[1]);
                final int idx = t[1] * tiles + t[0];
                Minecraft.getMinecraft().addScheduledTask(() -> upload(idx, tilePixels));
                tilesDone++;
            }
        } catch (Exception e) {
            error = "Couldn't read the map: " + e.getMessage();
        } finally {
            for (RegionFile r : regions.values()) {
                try { r.close(); } catch (Exception ignored) {}
            }
        }
    }

    private void renderTile(int tx, int tz, Map<Long, RegionFile> regions) {
        int baseChunkX = (originX >> 4) + tx * CHUNKS_PER_TILE;
        int baseChunkZ = (originZ >> 4) + tz * CHUNKS_PER_TILE;
        for (int cz = 0; cz < CHUNKS_PER_TILE; cz++) {
            for (int cx = 0; cx < CHUNKS_PER_TILE; cx++) {
                if (cancelled) return;
                renderChunk(baseChunkX + cx, baseChunkZ + cz, regions);
            }
        }
    }

    private void renderChunk(int chunkX, int chunkZ, Map<Long, RegionFile> regions) {
        int rx = chunkX >> 5, rz = chunkZ >> 5;
        long key = ((long) rx << 32) ^ (rz & 0xFFFFFFFFL);
        RegionFile region = regions.get(key);
        if (region == null) {
            File f = new File(regionDir, "r." + rx + "." + rz + ".mca");
            if (!f.isFile()) return;
            region = new RegionFile(f);
            regions.put(key, region);
        }
        NBTTagCompound level;
        try (DataInputStream in = region.getChunkDataInputStream(chunkX & 31, chunkZ & 31)) {
            if (in == null) return;
            level = CompressedStreamTools.read(in).getCompoundTag("Level");
        } catch (Exception e) {
            return; // corrupt chunk: leave it as void
        }

        byte[][] blocks = new byte[16][];
        byte[][] data = new byte[16][];
        byte[][] add = new byte[16][];
        int topSection = -1;
        NBTTagList sections = level.getTagList("Sections", 10);
        for (int i = 0; i < sections.tagCount(); i++) {
            NBTTagCompound s = sections.getCompoundTagAt(i);
            int y = s.getByte("Y") & 0xFF;
            if (y > 15) continue;
            blocks[y] = s.getByteArray("Blocks");
            data[y] = s.getByteArray("Data");
            add[y] = s.hasKey("Add", 7) ? s.getByteArray("Add") : null;
            if (blocks[y].length != 4096) blocks[y] = null;
            else topSection = Math.max(topSection, y);
        }

        int baseX = chunkX * 16 - originX, baseZ = chunkZ * 16 - originZ;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int px = baseX + x, pz = baseZ + z;
                if (px < 0 || pz < 0 || px >= size || pz >= size) continue;
                int out = pz * size + px;
                int top = -1, color = 0, cy = -1, waterDepth = 0, stand = -1;
                for (int y = topSection * 16 + 15; y >= 0; y--) {
                    int sec = y >> 4;
                    byte[] b = blocks[sec];
                    if (b == null) { y = sec * 16; continue; }
                    int idx = ((y & 15) << 8) | (z << 4) | x;
                    int id = b[idx] & 0xFF;
                    if (add[sec] != null) id |= nibble(add[sec], idx) << 8;
                    if (id == 0) continue;
                    if (top < 0) top = y;
                    int meta = data[sec] != null && data[sec].length == 2048 ? nibble(data[sec], idx) : 0;
                    int key2 = (id << 4) | meta;
                    int col = colorOf(id, meta, key2);
                    if (col == 0) continue; // e.g. glass: look through it
                    if (waterCache[key2] == 1) {
                        if (waterDepth == 0) { color = col; cy = y; stand = y + 1; }
                        waterDepth++;
                        if (waterDepth < 12) continue;
                        break;
                    }
                    if (waterDepth == 0) { color = col; cy = y; stand = y + standCache[key2]; }
                    break;
                }
                // Columns with nothing but see-through blocks: stand on top of whatever is there.
                surface[out] = (short) (stand >= 0 ? stand : (top >= 0 ? top + 1 : -1));
                colorY[out] = (short) (waterDepth > 0 ? 256 + Math.min(waterDepth, 12) : cy);
                pixels[out] = color == 0 ? VOID : color;
            }
        }

        NBTTagList tes = level.getTagList("TileEntities", 10);
        for (int i = 0; i < tes.tagCount(); i++) {
            NBTTagCompound te = tes.getCompoundTagAt(i);
            String id = te.getString("id");
            if (id.endsWith("chest")) chests.add(new BlockPos(te.getInteger("x"), te.getInteger("y"), te.getInteger("z")));
        }
    }

    /** Vanilla-map style relief: brighter facing north-up slopes, darker on the way down. */
    private void shadeTile(int tx, int tz) {
        for (int z = tz * TILE; z < tz * TILE + TILE; z++) {
            for (int x = tx * TILE; x < tx * TILE + TILE; x++) {
                int i = z * size + x;
                int p = pixels[i];
                if (p == VOID) continue;
                int y = colorY[i];
                float f;
                if (y >= 256) {
                    // Water: darker the deeper it is.
                    int depth = y - 256;
                    f = depth <= 2 ? 1.0f : depth <= 5 ? 0.86f : depth <= 9 ? 0.75f : 0.66f;
                } else {
                    int ny = z > 0 ? colorY[i - size] : -1;
                    if (ny < 0 || ny >= 256) f = 0.86f;
                    else f = y > ny ? 1.0f : y == ny ? 0.86f : 0.71f;
                }
                int r = (int) (((p >> 16) & 0xFF) * f), g = (int) (((p >> 8) & 0xFF) * f), b = (int) ((p & 0xFF) * f);
                pixels[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
    }

    private int[] copyTile(int tx, int tz) {
        int[] out = new int[TILE * TILE];
        for (int z = 0; z < TILE; z++) {
            System.arraycopy(pixels, (tz * TILE + z) * size + tx * TILE, out, z * TILE, TILE);
        }
        return out;
    }

    /** Client thread. */
    private void upload(int idx, int[] tilePixels) {
        if (cancelled) return;
        DynamicTexture tex = new DynamicTexture(TILE, TILE);
        System.arraycopy(tilePixels, 0, tex.getTextureData(), 0, tilePixels.length);
        tex.updateDynamicTexture();
        tileDyn[idx] = tex;
        tileTex[idx] = Minecraft.getMinecraft().getTextureManager()
                .getDynamicTextureLocation("beyonder_map_tile_" + System.identityHashCode(this) + "_" + idx, tex);
        tileReady[idx] = true;
    }

    // ---------------------------------------------------------------------
    // Block colours
    // ---------------------------------------------------------------------

    private int colorOf(int id, int meta, int key) {
        int cached = colorCache[key];
        if (cached != Integer.MIN_VALUE) return cached;
        int color = 0;
        byte water = 0;
        try {
            Block block = idRemap[id] != null ? idRemap[id] : Block.getBlockById(id);
            if (block != null && block != Blocks.AIR) {
                IBlockState state;
                try {
                    state = block.getStateFromMeta(meta);
                } catch (Exception e) {
                    state = block.getDefaultState();
                }
                MapColor mc;
                try {
                    mc = state.getMapColor(null, BlockPos.ORIGIN);
                } catch (Exception e) {
                    mc = state.getMaterial().getMaterialMapColor();
                }
                if (mc != null && mc != MapColor.AIR) {
                    color = 0xFF000000 | mc.colorValue;
                    if (mc == MapColor.WATER) water = 1;
                }
                standCache[key] = standsOnTop(state) ? (byte) 1 : (byte) 0;
            }
        } catch (Exception ignored) {
            color = 0;
        }
        colorCache[key] = color;
        waterCache[key] = water;
        return color;
    }

    /** Whether a player stands on top of this block (full-ish collision) rather than inside it. */
    private static boolean standsOnTop(IBlockState state) {
        try {
            net.minecraft.util.math.AxisAlignedBB box = state.getCollisionBoundingBox(null, BlockPos.ORIGIN);
            if (box == null || box == Block.NULL_AABB) return false; // flowers, plates, torches...
            return box.maxY > 0.25; // carpet / snow layers: you stand inside the block space
        } catch (Exception e) {
            return state.getMaterial().blocksMovement();
        }
    }

    /**
     * Forge saves the numeric block-id assignment in level.dat; when it differs from the running
     * game (modded maps), map ids to blocks by registry name so modded blocks get the right colour.
     */
    private void loadIdRemap(File levelDat) {
        if (!levelDat.isFile()) return;
        try (InputStream in = new FileInputStream(levelDat)) {
            NBTTagCompound root = CompressedStreamTools.readCompressed(in);
            NBTTagCompound blocks = root.getCompoundTag("FML").getCompoundTag("Registries").getCompoundTag("minecraft:blocks");
            NBTTagList ids = blocks.getTagList("ids", 10);
            for (int i = 0; i < ids.tagCount(); i++) {
                NBTTagCompound e = ids.getCompoundTagAt(i);
                int id = e.getInteger("V");
                if (id < 0 || id >= idRemap.length) continue;
                Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(e.getString("K")));
                if (b != null && b != Blocks.AIR) idRemap[id] = b;
            }
        } catch (Exception ignored) {
            // No FML data (vanilla save): the running registry's ids are used.
        }
    }

    private static int nibble(byte[] arr, int idx) {
        int v = arr[idx >> 1];
        return (idx & 1) == 0 ? v & 0xF : (v >> 4) & 0xF;
    }
}
