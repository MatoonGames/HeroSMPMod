package com.matoon.herosmp.integration;

import lucraft.mods.lucraftcore.utilities.items.ItemInjection;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;

/**
 * Utility for reading and writing HeroSMP-specific spawn properties (weight, maxOnMap)
 * on top of a real {@code lucraftcore:injection} ItemStack.
 *
 * LucraftCore's own injection data lives under the "Injection" NBT key (a ResourceLocation
 * string); HeroSMP's properties live in a separate "HGInjection" sub-compound so the two
 * systems never interfere with each other.
 *
 * The item stored in the config GUI and pool lists is always a genuine
 * {@code lucraftcore:injection} ItemStack — admins simply take one from the LucraftCore
 * creative tab (or /give) and drop it into the injection pool chest.
 *
 * Spawn properties (editable in the Injection Properties GUI):
 *   weight    — relative spawn chance compared to other injections (1–1000, default 10)
 *   maxOnMap  — max simultaneous copies of this injection type on the map; 0 = unlimited
 *
 * Anvil rename format: {@code "w:10 max:0"} to adjust weight/maxOnMap in-game.
 */
public final class LucraftInjectionEntry {

    // HeroSMP sub-compound key — never collides with LucraftCore's own "Injection" key.
    public static final String NBT_KEY    = "HGInjection";
    public static final String NBT_WEIGHT = "Weight";
    public static final String NBT_MAX    = "MaxOnMap";

    // -------------------------------------------------------------------------
    // Validity check
    // -------------------------------------------------------------------------

    /**
     * Returns true if {@code stack} is a valid {@code lucraftcore:injection} item
     * with a registered injection payload.  This is the single gate used by all
     * systems (spawn pools, config GUI filters, entity creation).
     *
     * Requires the LucraftCore injection registry to be populated at call time.
     * Use {@link #hasInjectionTag} for deserialization / storage checks where the
     * registry may not yet be available.
     */
    public static boolean isValidInjection(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        // Reject plain glass bottles or any non-injection item.
        if (!(item instanceof ItemInjection)) return false;
        // ItemInjection.getInjection() returns null for empty syringes.
        return ItemInjection.getInjection(stack) != null;
    }

    /**
     * Structural check — returns true if {@code stack} is an {@code ItemInjection}
     * item with a non-empty {@code "Injection"} NBT tag, WITHOUT consulting the
     * LucraftCore registry.  Use this for NBT deserialization and persistence so
     * that stacks are never silently dropped just because the registry lookup failed
     * (e.g. injection not yet registered at load time, or a temporarily absent mod).
     */
    public static boolean hasInjectionTag(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof ItemInjection)) return false;
        if (!stack.hasTagCompound()) return false;
        String id = stack.getTagCompound().getString("Injection");
        return id != null && !id.isEmpty();
    }

    // -------------------------------------------------------------------------
    // HGInjection property embed / read
    // -------------------------------------------------------------------------

    /**
     * Embeds HeroSMP spawn properties into a copy of {@code stack}.
     * The LucraftCore "Injection" tag is left untouched.
     */
    public static ItemStack withProperties(ItemStack stack, int weight, int maxOnMap) {
        ItemStack copy = stack.copy();
        if (!copy.hasTagCompound()) copy.setTagCompound(new NBTTagCompound());
        NBTTagCompound hg = new NBTTagCompound();
        hg.setInteger(NBT_WEIGHT, clamp(weight,  1, 1000));
        hg.setInteger(NBT_MAX,    clamp(maxOnMap, 0,  999));
        copy.getTagCompound().setTag(NBT_KEY, hg);
        refreshLore(copy);
        return copy;
    }

    /**
     * Returns the spawn weight stored on {@code stack}, defaulting to 10.
     */
    public static int getWeight(ItemStack stack) {
        if (!stack.hasTagCompound() || !stack.getTagCompound().hasKey(NBT_KEY)) return 10;
        return stack.getTagCompound().getCompoundTag(NBT_KEY).getInteger(NBT_WEIGHT);
    }

    /**
     * Returns the max-on-map value stored on {@code stack}, defaulting to 0 (unlimited).
     */
    public static int getMaxOnMap(ItemStack stack) {
        if (!stack.hasTagCompound() || !stack.getTagCompound().hasKey(NBT_KEY)) return 0;
        return stack.getTagCompound().getCompoundTag(NBT_KEY).getInteger(NBT_MAX);
    }

    /**
     * Convenience: updates weight and maxOnMap on the given stack in-place.
     * Does not copy — modifies the passed stack directly.
     */
    public static void embed(ItemStack stack, int weight, int maxOnMap) {
        if (stack.isEmpty()) return;
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound hg = new NBTTagCompound();
        hg.setInteger(NBT_WEIGHT, clamp(weight,  1, 1000));
        hg.setInteger(NBT_MAX,    clamp(maxOnMap, 0,  999));
        stack.getTagCompound().setTag(NBT_KEY, hg);
    }

    // -------------------------------------------------------------------------
    // Display name / lore helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the display name for the injection, sourced from LucraftCore's own
     * {@link ItemInjection.Injection#getDisplayName()} so it matches the in-game item.
     * Falls back to the ResourceLocation string if unavailable.
     */
    public static String getDisplayName(ItemStack stack) {
        if (!isValidInjection(stack)) return "Unknown";
        ItemInjection.Injection injection = ItemInjection.getInjection(stack);
        return injection != null ? injection.getDisplayName() : getLucraftId(stack);
    }

    /**
     * Returns the LucraftCore injection ResourceLocation string stored in the
     * "Injection" NBT key (e.g. "heroesexpansion:kryptonian").
     */
    public static String getLucraftId(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTagCompound()) return "";
        return stack.getTagCompound().getString("Injection");
    }

    /**
     * Rebuilds the lore lines on an injection ItemStack to show the current
     * HeroSMP spawn properties alongside the injection name from LucraftCore.
     */
    public static void refreshLore(ItemStack stack) {
        if (!isValidInjection(stack)) return;
        String name     = getDisplayName(stack);
        int    weight   = getWeight(stack);
        int    maxOnMap = getMaxOnMap(stack);
        addLore(stack,
                TextFormatting.GRAY   + "Power: "      + TextFormatting.WHITE  + name,
                TextFormatting.GRAY   + "Weight: "     + TextFormatting.YELLOW + weight,
                TextFormatting.GRAY   + "Max on map: " + TextFormatting.AQUA
                        + (maxOnMap == 0 ? "unlimited" : String.valueOf(maxOnMap)),
                TextFormatting.DARK_GRAY + "Rename in anvil: \"w:10 max:0\"");
    }

    // -------------------------------------------------------------------------
    // Anvil rename support
    // -------------------------------------------------------------------------

    /**
     * Parses a renamed display string of the form {@code "w:10 max:0"} and returns
     * a new ItemStack with the updated HGInjection properties applied.
     * Unrecognised tokens are ignored; LucraftCore data is preserved.
     */
    public static ItemStack applyAnvilString(ItemStack stack, String text) {
        if (!isValidInjection(stack)) return stack;
        String clean = net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(text);
        if (clean == null) clean = text;
        clean = clean.trim();

        int weight   = getWeight(stack);
        int maxOnMap = getMaxOnMap(stack);

        for (String token : clean.split("\\s+")) {
            String[] parts = token.split(":", 2);
            if (parts.length != 2) continue;
            String key = parts[0].toLowerCase();
            int val;
            try { val = Integer.parseInt(parts[1]); } catch (NumberFormatException e) { continue; }
            switch (key) {
                case "w":   weight   = clamp(val, 1, 1000); break;
                case "max": maxOnMap = clamp(val, 0,  999); break;
            }
        }

        return withProperties(stack, weight, maxOnMap);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    public static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    private static void addLore(ItemStack stack, String... lines) {
        NBTTagCompound tag     = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        NBTTagCompound display = tag.hasKey("display") ? tag.getCompoundTag("display") : new NBTTagCompound();
        net.minecraft.nbt.NBTTagList lore = new net.minecraft.nbt.NBTTagList();
        for (String line : lines) lore.appendTag(new NBTTagString(line));
        display.setTag("Lore", lore);
        tag.setTag("display", display);
        stack.setTagCompound(tag);
    }
}
