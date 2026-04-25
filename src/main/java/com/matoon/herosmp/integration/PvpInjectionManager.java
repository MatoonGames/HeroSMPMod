package com.matoon.herosmp.integration;

import com.matoon.herosmp.hungergames.map.LucraftInjectionInventory;
import com.matoon.herosmp.hungergames.map.LucraftInjectionPropertiesInventory;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Manages the global injection pool used by the PvP arena system.
 *
 * The pool is a list of real {@code lucraftcore:injection} ItemStacks — the same items
 * admins take from the LucraftCore creative tab.  Each stack may also carry a
 * "HGInjection" sub-compound (written by {@link LucraftInjectionEntry}) that stores
 * HeroSMP-specific spawn properties (weight, maxOnMap).
 *
 * Admins configure the pool via /heropvp injections, which opens
 * {@link LucraftInjectionInventory}.  Per-injection properties (weight, maxOnMap) and
 * global spawn counts are configured via /heropvp injectionproperties, which opens
 * {@link LucraftInjectionPropertiesInventory}.  The pool is persisted as full ItemStack
 * NBT in the overworld per-world storage under the key "herosmp_pvp_injections".
 *
 * When a PvP match starts, {@link #spawnInjectionsInArena} scatters injection entities
 * around the arena at valid surface positions.
 */
public class PvpInjectionManager {

    private static final String DATA_NAME = "herosmp_pvp_injections";
    private static final Random rand = new Random();

    // -------------------------------------------------------------------------
    // Pool access
    // -------------------------------------------------------------------------

    public List<ItemStack> getPool(MinecraftServer server) {
        return getData(server).getEntries();
    }

    public void setPool(MinecraftServer server, List<ItemStack> stacks) {
        SavedData data = getData(server);
        data.entries.clear();
        for (ItemStack s : stacks) {
            if (LucraftInjectionEntry.isValidInjection(s)) data.entries.add(s.copy());
        }
        data.markDirty();
    }

    public int getGlobalMin(MinecraftServer server) {
        return getData(server).pvpInjMin;
    }

    public int getGlobalMax(MinecraftServer server) {
        return getData(server).pvpInjMax;
    }

    // -------------------------------------------------------------------------
    // GUI — pool editor
    // -------------------------------------------------------------------------

    public void openInjectionMenu(EntityPlayerMP player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        List<ItemStack> entries = getPool(server);
        LucraftInjectionInventory inv = new LucraftInjectionInventory("PvP Arena");
        // PvP uses a single pool — load into the "All Phases" tab (index 3).
        inv.loadTabContents(Collections.emptyList(), Collections.emptyList(),
                            Collections.emptyList(), entries);
        inv.setHideTabs(true);
        player.displayGUIChest(inv);
    }

    // -------------------------------------------------------------------------
    // GUI — properties editor
    // -------------------------------------------------------------------------

    public void openPropertiesMenu(EntityPlayerMP player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        SavedData data = getData(server);
        List<ItemStack> pool = data.getEntries();
        // Load pool into "All Phases" (tab 3); other phase slots are empty and unused.
        LucraftInjectionPropertiesInventory inv = new LucraftInjectionPropertiesInventory(
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), pool,
                0, 0, 0, 0, 0, 0,
                data.pvpInjMin, data.pvpInjMax);
        // PvP has no phases — hide the tab row and lock to "All Phases".
        inv.setActiveTab(3);
        inv.setHideTabs(true);
        player.displayGUIChest(inv);
    }

    /**
     * Called when the admin closes the properties GUI. Saves the edited pool items
     * (from the "All Phases" tab) and the global min/max back to disk.
     */
    public void saveFromPropertiesMenu(MinecraftServer server, LucraftInjectionPropertiesInventory inv) {
        // Save edited pool (phase 3 = "All Phases" tab).
        setPool(server, inv.getPhase(3));
        // Save global min/max for this pool.
        SavedData data = getData(server);
        data.pvpInjMin = inv.getGlobalMin(3);
        data.pvpInjMax = inv.getGlobalMax(3);
        data.markDirty();
    }

    // -------------------------------------------------------------------------
    // Spawning
    // -------------------------------------------------------------------------

    /**
     * Spawns {@link EntityLucraftInjection} entities in the PvP arena around the given
     * centre within the provided half-radius.
     *
     * Spawn count is determined by the admin-configured global min/max:
     *   - If both are 0: fallback = max(1, playerCount / 2)
     *   - Otherwise: clamp(max(min, playerCount / 2), min, max)
     *
     * Per-entry maxOnMap is also respected (0 = unlimited).
     */
    public void spawnInjectionsInArena(WorldServer world, BlockPos center, int halfRange, int playerCount) {
        MinecraftServer server = world.getMinecraftServer();
        List<ItemStack> pool = getPool(server);
        if (pool.isEmpty()) return;

        // Build weighted candidate list.
        List<ItemStack> weighted = new ArrayList<>();
        for (ItemStack entry : pool) {
            int w = LucraftInjectionEntry.getWeight(entry);
            for (int i = 0; i < w; i++) weighted.add(entry);
        }
        if (weighted.isEmpty()) return;

        // Determine spawn count.
        int gMin = getGlobalMin(server);
        int gMax = getGlobalMax(server);
        int spawnCount;
        if (gMin == 0 && gMax == 0) {
            // Default: spawn one per player, minimum 3.
            spawnCount = Math.max(3, playerCount);
        } else {
            int playerBased = Math.max(gMin, playerCount);
            spawnCount = (gMax > 0) ? Math.min(playerBased, gMax) : playerBased;
            spawnCount = Math.max(spawnCount, gMin);
        }

        int minX = center.getX() - halfRange;
        int maxX = center.getX() + halfRange;
        int minZ = center.getZ() - halfRange;
        int maxZ = center.getZ() + halfRange;
        int rangeX = maxX - minX;
        int rangeZ = maxZ - minZ;

        // Divide the arena into a grid of cells — one injection per cell — so they
        // spread evenly across the map (same approach as loot chest placement).
        int cols = (int) Math.round(Math.sqrt(spawnCount));
        int rows = (spawnCount + cols - 1) / cols;

        List<int[]> cells = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                cells.add(new int[]{col, row});
            }
        }
        java.util.Collections.shuffle(cells, rand);

        // Track per-type spawn counts for maxOnMap enforcement.
        Map<String, Integer> spawnedByType = new HashMap<>();

        for (int i = 0; i < spawnCount; i++) {
            int[] cell = cells.get(i % cells.size());
            int cellMinX = minX + (cell[0] * rangeX / cols);
            int cellMaxX = minX + ((cell[0] + 1) * rangeX / cols);
            int cellMinZ = minZ + (cell[1] * rangeZ / rows);
            int cellMaxZ = minZ + ((cell[1] + 1) * rangeZ / rows);

            ItemStack chosen = weighted.get(rand.nextInt(weighted.size()));

            // Enforce maxOnMap per injection type.
            int maxOnMap = LucraftInjectionEntry.getMaxOnMap(chosen);
            if (maxOnMap > 0) {
                String id = LucraftInjectionEntry.getLucraftId(chosen);
                int alreadySpawned = spawnedByType.getOrDefault(id, 0);
                if (alreadySpawned >= maxOnMap) continue;
                spawnedByType.put(id, alreadySpawned + 1);
            }

            // Try random positions within this cell until a valid surface is found.
            for (int attempt = 0; attempt < 20; attempt++) {
                int tx = cellMinX + (cellMaxX > cellMinX ? rand.nextInt(cellMaxX - cellMinX) : 0);
                int tz = cellMinZ + (cellMaxZ > cellMinZ ? rand.nextInt(cellMaxZ - cellMinZ) : 0);

                int surfaceY = world.getHeight(tx, tz);
                int blockY = surfaceY - 1;
                if (blockY < 1) continue;

                BlockPos surfacePos = new BlockPos(tx, blockY, tz);
                net.minecraft.block.state.IBlockState surfaceState = world.getBlockState(surfacePos);

                if (surfaceState.getMaterial().isLiquid()) continue;
                if (world.isAirBlock(surfacePos)) continue;
                if (!surfaceState.isFullBlock() && !surfaceState.getMaterial().isSolid()) continue;

                EntityLucraftInjection injection = new EntityLucraftInjection(
                        world, tx + 0.5, surfaceY + 0.5, tz + 0.5, chosen.copy());
                world.spawnEntity(injection);
                break;
            }
        }
    }

    // -------------------------------------------------------------------------
    // WorldSavedData
    // -------------------------------------------------------------------------

    private SavedData getData(MinecraftServer server) {
        WorldServer world = server.getWorld(0);
        SavedData data = (SavedData) world.getPerWorldStorage().getOrLoadData(SavedData.class, DATA_NAME);
        if (data == null) {
            data = new SavedData();
            world.getPerWorldStorage().setData(DATA_NAME, data);
        }
        return data;
    }

    public static class SavedData extends WorldSavedData {

        private final List<ItemStack> entries = new ArrayList<>();

        /** Global minimum injection entities to spawn per match. 0 = use playerCount/2 fallback. */
        int pvpInjMin = 0;
        /** Global maximum injection entities to spawn per match. 0 = use playerCount/2 fallback. */
        int pvpInjMax = 0;

        public SavedData() { super(DATA_NAME); }
        public SavedData(String name) { super(name); }

        public List<ItemStack> getEntries() { return new ArrayList<>(entries); }

        @Override
        public void readFromNBT(NBTTagCompound nbt) {
            entries.clear();
            // New format: full ItemStack NBT.
            if (nbt.hasKey("Entries")) {
                NBTTagList list = nbt.getTagList("Entries", Constants.NBT.TAG_COMPOUND);
                for (int i = 0; i < list.tagCount(); i++) {
                    ItemStack stack = new ItemStack(list.getCompoundTagAt(i));
                    if (LucraftInjectionEntry.hasInjectionTag(stack)) entries.add(stack);
                }
            }
            pvpInjMin = nbt.hasKey("PvpInjMin") ? nbt.getInteger("PvpInjMin") : 0;
            pvpInjMax = nbt.hasKey("PvpInjMax") ? nbt.getInteger("PvpInjMax") : 0;
            // Legacy migration: old format stored PowerId/Weight/MaxOnMap strings.
            // We cannot reconstruct the real lucraftcore:injection ItemStack from just
            // a string ID without the item registry being available here, so legacy entries
            // are silently dropped — admins must reconfigure the PvP pool once after update.
        }

        @Override
        public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
            NBTTagList list = new NBTTagList();
            for (ItemStack stack : entries) {
                list.appendTag(stack.writeToNBT(new NBTTagCompound()));
            }
            nbt.setTag("Entries", list);
            nbt.setInteger("PvpInjMin", pvpInjMin);
            nbt.setInteger("PvpInjMax", pvpInjMax);
            return nbt;
        }
    }
}
