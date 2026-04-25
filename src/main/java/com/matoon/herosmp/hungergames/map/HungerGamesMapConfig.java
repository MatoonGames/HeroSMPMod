package com.matoon.herosmp.hungergames.map;

import com.matoon.herosmp.integration.LucraftInjectionEntry;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HungerGamesMapConfig {

    private final String mapName;
    private final List<BlockPos> roundSpawns = new ArrayList<>();
    private BlockPos lobbySpawn = null;

    // Per-phase loot pools. Items may carry HGLoot NBT properties (weight, globalMax, etc.)
    private final List<ItemStack> lootPhase1    = new ArrayList<>();
    private final List<ItemStack> lootPhase2    = new ArrayList<>();
    private final List<ItemStack> lootPhase3    = new ArrayList<>();
    private final List<ItemStack> lootAllPhases = new ArrayList<>();

    private final List<ItemStack> breakableBlocks = new ArrayList<>();

    // Per-phase injection pools (each entry carries HGInjection NBT).
    // These are stored as ItemStacks so the injection properties inventory can manage them.
    private final List<ItemStack> injectionPhase1    = new ArrayList<>();
    private final List<ItemStack> injectionPhase2    = new ArrayList<>();
    private final List<ItemStack> injectionPhase3    = new ArrayList<>();
    private final List<ItemStack> injectionAllPhases = new ArrayList<>();

    // Per-phase global injection counts: how many total injection entities are on the map.
    // minInjections: minimum to guarantee are present; maxInjections: hard cap.
    // Phase-specific values override AllPhases when non-zero.
    private int injMinPhase1     = 0;
    private int injMaxPhase1     = 0;
    private int injMinPhase2     = 0;
    private int injMaxPhase2     = 0;
    private int injMinPhase3     = 0;
    private int injMaxPhase3     = 0;
    private int injMinAllPhases  = 3;
    private int injMaxAllPhases  = 8;

    private BlockPos mapCenter = null;
    private int worldBorderStartRange = 200;

    public HungerGamesMapConfig(String mapName) {
        this.mapName = mapName;
    }

    public String getMapName() { return mapName; }
    public List<BlockPos> getRoundSpawns() { return new ArrayList<>(roundSpawns); }
    public BlockPos getLobbySpawn() { return lobbySpawn; }
    public List<ItemStack> getLootPhase1()    { return new ArrayList<>(lootPhase1); }
    public List<ItemStack> getLootPhase2()    { return new ArrayList<>(lootPhase2); }
    public List<ItemStack> getLootPhase3()    { return new ArrayList<>(lootPhase3); }
    public List<ItemStack> getLootAllPhases() { return new ArrayList<>(lootAllPhases); }
    public List<ItemStack> getBreakableBlocks() { return new ArrayList<>(breakableBlocks); }

    public List<ItemStack> getInjectionPhase1()    { return new ArrayList<>(injectionPhase1); }
    public List<ItemStack> getInjectionPhase2()    { return new ArrayList<>(injectionPhase2); }
    public List<ItemStack> getInjectionPhase3()    { return new ArrayList<>(injectionPhase3); }
    public List<ItemStack> getInjectionAllPhases() { return new ArrayList<>(injectionAllPhases); }

    public int getInjMinPhase1()    { return injMinPhase1; }
    public int getInjMaxPhase1()    { return injMaxPhase1; }
    public int getInjMinPhase2()    { return injMinPhase2; }
    public int getInjMaxPhase2()    { return injMaxPhase2; }
    public int getInjMinPhase3()    { return injMinPhase3; }
    public int getInjMaxPhase3()    { return injMaxPhase3; }
    public int getInjMinAllPhases() { return injMinAllPhases; }
    public int getInjMaxAllPhases() { return injMaxAllPhases; }

    public BlockPos getMapCenter() { return mapCenter; }
    public int getWorldBorderStartRange() { return worldBorderStartRange; }

    public void setLobbySpawn(BlockPos pos) { this.lobbySpawn = pos; }
    public void setMapCenter(BlockPos pos) { this.mapCenter = pos; }
    public void setWorldBorderStartRange(int range) { this.worldBorderStartRange = range; }

    public void addRoundSpawn(BlockPos pos) { roundSpawns.add(pos); }

    public boolean removeRoundSpawn(BlockPos pos) {
        return roundSpawns.removeIf(p -> p.equals(pos));
    }

    public boolean hasRoundSpawn(BlockPos pos) {
        return roundSpawns.stream().anyMatch(p -> p.equals(pos));
    }

    public void setLootPhase1(List<ItemStack> items)    { copyInto(lootPhase1, items); }
    public void setLootPhase2(List<ItemStack> items)    { copyInto(lootPhase2, items); }
    public void setLootPhase3(List<ItemStack> items)    { copyInto(lootPhase3, items); }
    public void setLootAllPhases(List<ItemStack> items) { copyInto(lootAllPhases, items); }

    public void setInjectionPhase1(List<ItemStack> items)    { copyInto(injectionPhase1, items); }
    public void setInjectionPhase2(List<ItemStack> items)    { copyInto(injectionPhase2, items); }
    public void setInjectionPhase3(List<ItemStack> items)    { copyInto(injectionPhase3, items); }
    public void setInjectionAllPhases(List<ItemStack> items) { copyInto(injectionAllPhases, items); }

    public void setInjMinPhase1(int v)    { injMinPhase1    = Math.max(0, v); }
    public void setInjMaxPhase1(int v)    { injMaxPhase1    = Math.max(0, v); }
    public void setInjMinPhase2(int v)    { injMinPhase2    = Math.max(0, v); }
    public void setInjMaxPhase2(int v)    { injMaxPhase2    = Math.max(0, v); }
    public void setInjMinPhase3(int v)    { injMinPhase3    = Math.max(0, v); }
    public void setInjMaxPhase3(int v)    { injMaxPhase3    = Math.max(0, v); }
    public void setInjMinAllPhases(int v) { injMinAllPhases = Math.max(0, v); }
    public void setInjMaxAllPhases(int v) { injMaxAllPhases = Math.max(0, v); }

    /**
     * Returns the effective (min, max) injection count for a given active game phase (1-3).
     * Phase-specific non-zero values take precedence over the AllPhases fallback.
     * Returns {min, max} as a 2-element array.
     */
    public int[] getEffectiveInjectionRange(int phase) {
        int min, max;
        switch (phase) {
            case 1:
                min = injMinPhase1 > 0 ? injMinPhase1 : injMinAllPhases;
                max = injMaxPhase1 > 0 ? injMaxPhase1 : injMaxAllPhases;
                break;
            case 2:
                min = injMinPhase2 > 0 ? injMinPhase2 : injMinAllPhases;
                max = injMaxPhase2 > 0 ? injMaxPhase2 : injMaxAllPhases;
                break;
            case 3:
                min = injMinPhase3 > 0 ? injMinPhase3 : injMinAllPhases;
                max = injMaxPhase3 > 0 ? injMaxPhase3 : injMaxAllPhases;
                break;
            default:
                min = injMinAllPhases;
                max = injMaxAllPhases;
        }
        if (max < min && max != 0) max = min;
        return new int[]{min, max};
    }

    /**
     * Returns the effective injection pool for a given active game phase (1-3).
     * Phase-specific items and AllPhases items are merged (AllPhases always included).
     */
    public List<ItemStack> getEffectiveInjectionPool(int phase) {
        List<ItemStack> phasePool;
        switch (phase) {
            case 1: phasePool = injectionPhase1; break;
            case 2: phasePool = injectionPhase2; break;
            case 3: phasePool = injectionPhase3; break;
            default: phasePool = new ArrayList<>();
        }
        List<ItemStack> result = new ArrayList<>(phasePool);
        result.addAll(injectionAllPhases);
        return result;
    }

    /**
     * Seeds {@code config} with the default loot preset used for new maps.
     * All items go into Phase 1 (applied as the "all-phases" fallback loot table).
     * Weights and counts match the Breeze 2 reference configuration.
     */
    public static void applyDefaultLootPreset(HungerGamesMapConfig config) {
        List<ItemStack> phase1 = new ArrayList<>();

        // Helper: item(item, weight, minCount, maxCount, perChestMax)
        // globalMax is 0 (unlimited) for all entries.
        phase1.add(loot(Items.ARROW,                   40, 2, 4, 4));
        phase1.add(loot(Items.BOW,                     25, 1, 1, 1));
        phase1.add(loot(Items.LEATHER_HELMET,          24, 1, 1, 1));
        phase1.add(loot(Items.LEATHER_CHESTPLATE,      24, 1, 1, 1));
        phase1.add(loot(Items.LEATHER_LEGGINGS,        25, 1, 1, 1));
        phase1.add(loot(Items.LEATHER_BOOTS,           25, 1, 1, 1));
        phase1.add(loot(Items.CHAINMAIL_HELMET,        19, 1, 1, 1));
        phase1.add(loot(Items.CHAINMAIL_CHESTPLATE,    19, 1, 1, 1));
        phase1.add(loot(Items.CHAINMAIL_LEGGINGS,      19, 1, 1, 1));
        phase1.add(loot(Items.CHAINMAIL_BOOTS,         19, 1, 1, 1));
        phase1.add(loot(Items.GOLDEN_HELMET,           19, 1, 1, 1));
        phase1.add(loot(Items.GOLDEN_CHESTPLATE,       19, 1, 1, 1));
        phase1.add(loot(Items.GOLDEN_LEGGINGS,         19, 1, 1, 1));
        phase1.add(loot(Items.GOLDEN_BOOTS,            19, 1, 1, 1));
        phase1.add(loot(Items.IRON_HELMET,             12, 1, 1, 1));
        phase1.add(loot(Items.IRON_CHESTPLATE,         12, 1, 1, 1));
        phase1.add(loot(Items.IRON_LEGGINGS,           12, 1, 1, 1));
        phase1.add(loot(Items.IRON_BOOTS,              12, 1, 1, 1));
        phase1.add(loot(Items.IRON_SWORD,              10, 1, 1, 1));
        phase1.add(loot(Items.WOODEN_SWORD,            25, 1, 1, 1));
        phase1.add(loot(Items.STONE_SWORD,             20, 1, 1, 1));
        phase1.add(loot(Items.GOLDEN_SWORD,            25, 1, 1, 1));
        phase1.add(loot(Items.SHIELD,                  18, 1, 1, 1));
        phase1.add(loot(Items.LAVA_BUCKET,             12, 1, 1, 1));
        phase1.add(loot(Items.WATER_BUCKET,            16, 1, 1, 1));
        phase1.add(loot(Items.GOLDEN_APPLE,             7, 1, 1, 1));
        phase1.add(loot(Items.COOKED_BEEF,             38, 1, 5, 4));
        phase1.add(loot(Items.COOKED_CHICKEN,          38, 1, 1, 1));
        phase1.add(loot(Items.COOKED_FISH,             38, 1, 1, 1));
        phase1.add(lootPotion(Items.POTIONITEM,        20, "minecraft:night_vision"));
        phase1.add(lootPotion(Items.POTIONITEM,        20, "minecraft:strong_swiftness"));
        phase1.add(lootPotion(Items.SPLASH_POTION,     20, "minecraft:strong_harming"));
        phase1.add(lootPotion(Items.POTIONITEM,        20, "minecraft:regeneration"));
        phase1.add(lootPotion(Items.LINGERING_POTION,  20, "minecraft:regeneration"));
        phase1.add(lootPotion(Items.SPLASH_POTION,     20, "minecraft:poison"));
        phase1.add(loot(Items.FLINT_AND_STEEL,         25, 1, 1, 1));
        phase1.add(loot(Item.getItemFromBlock(Blocks.TNT), 12, 1, 1, 1));

        // Modded item — looked up by registry name; silently skipped if the mod isn't loaded.
        Item chitauriGun = Item.getByNameOrId("heroesexpansion:chitauri_gun");
        if (chitauriGun != null && chitauriGun != Items.AIR) {
            phase1.add(loot(chitauriGun, 9, 1, 1, 1));
        }

        config.setLootPhase1(phase1);
    }

    private static ItemStack loot(Item item, int weight, int minCount, int maxCount, int perChestMax) {
        ItemStack stack = new ItemStack(item);
        HungerGamesLootEntry.embed(stack, weight, 0, perChestMax, minCount, maxCount);
        return stack;
    }

    private static ItemStack lootPotion(Item item, int weight, String potionId) {
        ItemStack stack = new ItemStack(item);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Potion", potionId);
        stack.setTagCompound(tag);
        HungerGamesLootEntry.embed(stack, weight, 0, 1, 1, 1);
        return stack;
    }

    private static void copyInto(List<ItemStack> target, List<ItemStack> source) {
        target.clear();
        for (ItemStack stack : source) {
            if (!stack.isEmpty()) target.add(stack.copy());
        }
    }

    public void setBreakableBlocks(List<ItemStack> items) {
        breakableBlocks.clear();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) breakableBlocks.add(stack.copy());
        }
    }


    public NBTTagCompound toNBT() {
        NBTTagCompound nbt = new NBTTagCompound();

        NBTTagList spawnList = new NBTTagList();
        for (BlockPos pos : roundSpawns) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("X", pos.getX());
            tag.setInteger("Y", pos.getY());
            tag.setInteger("Z", pos.getZ());
            spawnList.appendTag(tag);
        }
        nbt.setTag("RoundSpawns", spawnList);

        if (lobbySpawn != null) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("X", lobbySpawn.getX());
            tag.setInteger("Y", lobbySpawn.getY());
            tag.setInteger("Z", lobbySpawn.getZ());
            nbt.setTag("LobbySpawn", tag);
        }

        nbt.setTag("LootPhase1",    serializeLoot(lootPhase1));
        nbt.setTag("LootPhase2",    serializeLoot(lootPhase2));
        nbt.setTag("LootPhase3",    serializeLoot(lootPhase3));
        nbt.setTag("LootAllPhases", serializeLoot(lootAllPhases));

        if (mapCenter != null) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("X", mapCenter.getX());
            tag.setInteger("Y", mapCenter.getY());
            tag.setInteger("Z", mapCenter.getZ());
            nbt.setTag("MapCenter", tag);
        }
        nbt.setInteger("WorldBorderRange", worldBorderStartRange);

        NBTTagList breakableList = new NBTTagList();
        for (int i = 0; i < breakableBlocks.size(); i++) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Slot", i);
            breakableBlocks.get(i).writeToNBT(tag);
            breakableList.appendTag(tag);
        }
        nbt.setTag("BreakableBlocks", breakableList);

        // Per-phase injection pools
        nbt.setTag("InjPhase1",    serializeLoot(injectionPhase1));
        nbt.setTag("InjPhase2",    serializeLoot(injectionPhase2));
        nbt.setTag("InjPhase3",    serializeLoot(injectionPhase3));
        nbt.setTag("InjAllPhases", serializeLoot(injectionAllPhases));

        // Per-phase global injection counts
        nbt.setInteger("InjMinP1",  injMinPhase1);
        nbt.setInteger("InjMaxP1",  injMaxPhase1);
        nbt.setInteger("InjMinP2",  injMinPhase2);
        nbt.setInteger("InjMaxP2",  injMaxPhase2);
        nbt.setInteger("InjMinP3",  injMinPhase3);
        nbt.setInteger("InjMaxP3",  injMaxPhase3);
        nbt.setInteger("InjMinAll", injMinAllPhases);
        nbt.setInteger("InjMaxAll", injMaxAllPhases);

        return nbt;
    }

    public void fromNBT(NBTTagCompound nbt) {
        roundSpawns.clear();
        lootPhase1.clear();
        lootPhase2.clear();
        lootPhase3.clear();
        lootAllPhases.clear();
        breakableBlocks.clear();
        injectionPhase1.clear();
        injectionPhase2.clear();
        injectionPhase3.clear();
        injectionAllPhases.clear();
        lobbySpawn = null;
        mapCenter = null;
        worldBorderStartRange = 200;
        injMinPhase1 = 0; injMaxPhase1 = 0;
        injMinPhase2 = 0; injMaxPhase2 = 0;
        injMinPhase3 = 0; injMaxPhase3 = 0;
        injMinAllPhases = 3; injMaxAllPhases = 8;

        NBTTagList spawnList = nbt.getTagList("RoundSpawns", 10);
        for (int i = 0; i < spawnList.tagCount(); i++) {
            NBTTagCompound tag = spawnList.getCompoundTagAt(i);
            roundSpawns.add(new BlockPos(tag.getInteger("X"), tag.getInteger("Y"), tag.getInteger("Z")));
        }

        if (nbt.hasKey("LobbySpawn")) {
            NBTTagCompound tag = nbt.getCompoundTag("LobbySpawn");
            lobbySpawn = new BlockPos(tag.getInteger("X"), tag.getInteger("Y"), tag.getInteger("Z"));
        }

        // Migrate old single loot pool → AllPhases
        if (nbt.hasKey("LootPool") && !nbt.hasKey("LootAllPhases")) {
            deserializeLoot(nbt.getTagList("LootPool", 10), lootAllPhases);
        }

        deserializeLoot(nbt.getTagList("LootPhase1",    10), lootPhase1);
        deserializeLoot(nbt.getTagList("LootPhase2",    10), lootPhase2);
        deserializeLoot(nbt.getTagList("LootPhase3",    10), lootPhase3);
        deserializeLoot(nbt.getTagList("LootAllPhases", 10), lootAllPhases);

        if (nbt.hasKey("MapCenter")) {
            NBTTagCompound tag = nbt.getCompoundTag("MapCenter");
            mapCenter = new BlockPos(tag.getInteger("X"), tag.getInteger("Y"), tag.getInteger("Z"));
        }
        if (nbt.hasKey("WorldBorderRange")) {
            worldBorderStartRange = nbt.getInteger("WorldBorderRange");
        }
        NBTTagList breakableList = nbt.getTagList("BreakableBlocks", 10);
        for (int i = 0; i < breakableList.tagCount(); i++) {
            NBTTagCompound tag = breakableList.getCompoundTagAt(i);
            ItemStack stack = new ItemStack(tag);
            if (!stack.isEmpty()) breakableBlocks.add(stack);
        }

        // Per-phase injection pools — loaded stacks are validated by isValidInjection()
        // so any stale glass-bottle proxies from before the update are silently dropped.
        deserializeInjections(nbt.getTagList("InjPhase1",    10), injectionPhase1);
        deserializeInjections(nbt.getTagList("InjPhase2",    10), injectionPhase2);
        deserializeInjections(nbt.getTagList("InjPhase3",    10), injectionPhase3);
        deserializeInjections(nbt.getTagList("InjAllPhases", 10), injectionAllPhases);

        // Legacy migration: old flat InjectionPool format stored glass-bottle proxies or
        // PowerId strings — neither can be reconstructed into a real lucraftcore:injection
        // ItemStack without the item registry, so we intentionally skip it.
        // Admins must reconfigure the injection pool once after updating to this version.

        // Per-phase global injection counts
        if (nbt.hasKey("InjMinP1"))  injMinPhase1    = nbt.getInteger("InjMinP1");
        if (nbt.hasKey("InjMaxP1"))  injMaxPhase1    = nbt.getInteger("InjMaxP1");
        if (nbt.hasKey("InjMinP2"))  injMinPhase2    = nbt.getInteger("InjMinP2");
        if (nbt.hasKey("InjMaxP2"))  injMaxPhase2    = nbt.getInteger("InjMaxP2");
        if (nbt.hasKey("InjMinP3"))  injMinPhase3    = nbt.getInteger("InjMinP3");
        if (nbt.hasKey("InjMaxP3"))  injMaxPhase3    = nbt.getInteger("InjMaxP3");
        if (nbt.hasKey("InjMinAll")) injMinAllPhases = nbt.getInteger("InjMinAll");
        if (nbt.hasKey("InjMaxAll")) injMaxAllPhases = nbt.getInteger("InjMaxAll");
    }

    private static NBTTagList serializeLoot(List<ItemStack> pool) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < pool.size(); i++) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Slot", i);
            pool.get(i).writeToNBT(tag);
            list.appendTag(tag);
        }
        return list;
    }

    private static void deserializeLoot(NBTTagList list, List<ItemStack> target) {
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            ItemStack stack = new ItemStack(tag);
            if (!stack.isEmpty()) target.add(stack);
        }
    }

    /**
     * Like {@link #deserializeLoot} but uses the structural {@link LucraftInjectionEntry#hasInjectionTag}
     * check (not the registry-backed isValidInjection) so stacks survive load even when
     * LucraftCore's injection registry isn't yet populated.  Final filtering happens at
     * spawn time inside spawnInjections.
     */
    private static void deserializeInjections(NBTTagList list, List<ItemStack> target) {
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            ItemStack stack = new ItemStack(tag);
            // Use the structural check (hasInjectionTag) rather than the registry-backed
            // isValidInjection so stacks are never silently dropped when the LucraftCore
            // injection registry hasn't been populated yet at load time.
            // Invalid stacks are filtered out later at spawn time (spawnInjections removeIf).
            if (LucraftInjectionEntry.hasInjectionTag(stack)) target.add(stack);
        }
    }

    public void saveToFile(File mapDir) throws IOException {
        CompressedStreamTools.write(toNBT(), new File(mapDir, "herosmp_config.dat"));
    }

    public void loadFromFile(File mapDir) throws IOException {
        File f = new File(mapDir, "herosmp_config.dat");
        if (f.exists()) fromNBT(CompressedStreamTools.read(f));
    }
}
