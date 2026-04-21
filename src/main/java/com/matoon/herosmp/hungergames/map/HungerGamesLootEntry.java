package com.matoon.herosmp.hungergames.map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Utility class for reading and writing per-item loot properties embedded in
 * an ItemStack's NBT tag under the key "HGLoot".
 *
 * Properties:
 *   weight      — relative chance of selection (default 10, range 1–1000)
 *   globalMax   — max total spawned across all chests per fill cycle; 0 = unlimited (default 0)
 *   perChestMax — max copies in a single chest per fill cycle (default 1, range 1–999)
 *   minCount    — minimum stack size placed (default 1, range 1–64)
 *   maxCount    — maximum stack size placed (default 1, range 1–64)
 *
 * Anvil string format:  "w:10 gmax:0 cmax:1 min:1 max:1"
 */
public final class HungerGamesLootEntry {

    private static final String TAG_KEY = "HGLoot";

    private HungerGamesLootEntry() {}

    // -------------------------------------------------------------------------
    // Embed / read
    // -------------------------------------------------------------------------

    public static void embed(ItemStack stack, int weight, int globalMax, int perChestMax, int minCount, int maxCount) {
        if (stack.isEmpty()) return;
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound loot = new NBTTagCompound();
        loot.setInteger("weight",      clamp(weight,      1, 1000));
        loot.setInteger("globalMax",   clamp(globalMax,   0,  999));
        loot.setInteger("perChestMax", clamp(perChestMax, 1,  999));
        loot.setInteger("minCount",    clamp(minCount,    1,   64));
        loot.setInteger("maxCount",    clamp(maxCount,    1,   64));
        stack.getTagCompound().setTag(TAG_KEY, loot);
    }

    public static boolean hasEntry(ItemStack stack) {
        return !stack.isEmpty() && stack.hasTagCompound() && stack.getTagCompound().hasKey(TAG_KEY);
    }

    public static int getWeight(ItemStack stack) {
        return getInt(stack, "weight", 10);
    }

    public static int getGlobalMax(ItemStack stack) {
        return getInt(stack, "globalMax", 0);
    }

    public static int getPerChestMax(ItemStack stack) {
        return getInt(stack, "perChestMax", 1);
    }

    public static int getMinCount(ItemStack stack) {
        return getInt(stack, "minCount", 1);
    }

    public static int getMaxCount(ItemStack stack) {
        return getInt(stack, "maxCount", 1);
    }

    // -------------------------------------------------------------------------
    // Anvil string encode / decode
    // -------------------------------------------------------------------------

    /** Produces a string like "w:10 gmax:0 cmax:1 min:1 max:1" showing current values. */
    public static String toAnvilString(ItemStack stack) {
        return "w:"    + getWeight(stack)
             + " gmax:" + getGlobalMax(stack)
             + " cmax:" + getPerChestMax(stack)
             + " min:"  + getMinCount(stack)
             + " max:"  + getMaxCount(stack);
    }

    /**
     * Parses a property string and embeds the result into a copy of the item.
     * Unrecognised tokens are ignored; missing keys keep their existing or default values.
     */
    public static ItemStack applyAnvilString(ItemStack stack, String text) {
        if (stack.isEmpty()) return stack;
        ItemStack result = stack.copy();

        // Strip display-name formatting codes that the vanilla anvil prepends
        String clean = net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(text);
        if (clean == null) clean = text;
        clean = clean.trim();

        int weight      = getWeight(result);
        int globalMax   = getGlobalMax(result);
        int perChestMax = getPerChestMax(result);
        int minCount    = getMinCount(result);
        int maxCount    = getMaxCount(result);

        for (String token : clean.split("\\s+")) {
            String[] parts = token.split(":", 2);
            if (parts.length != 2) continue;
            String key = parts[0].toLowerCase();
            int val;
            try { val = Integer.parseInt(parts[1]); } catch (NumberFormatException e) { continue; }
            switch (key) {
                case "w":    weight      = clamp(val, 1,  1000); break;
                case "gmax": globalMax   = clamp(val, 0,   999); break;
                case "cmax": perChestMax = clamp(val, 1,   999); break;
                case "min":  minCount    = clamp(val, 1,    64); break;
                case "max":  maxCount    = clamp(val, 1,    64); break;
            }
        }
        // Ensure min <= max
        if (minCount > maxCount) maxCount = minCount;

        embed(result, weight, globalMax, perChestMax, minCount, maxCount);
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static int getInt(ItemStack stack, String key, int defaultVal) {
        if (!hasEntry(stack)) return defaultVal;
        NBTTagCompound loot = stack.getTagCompound().getCompoundTag(TAG_KEY);
        return loot.hasKey(key) ? loot.getInteger(key) : defaultVal;
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }
}
