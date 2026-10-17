package com.matoon.herosmp.npc.kit;

import lucraft.mods.lucraftcore.addonpacks.AddonPackHandler;
import lucraft.mods.lucraftcore.addonpacks.AddonPackInfo;
import lucraft.mods.lucraftcore.utilities.items.ItemInjection;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Which mods and Hero Addons (LucraftCore addon packs) a kit needs.
 * <p>
 * A kit's items and superpower are stored by registry name ({@code heroesexpansion:...},
 * {@code neonvillains:sauron}), so the namespace says where each one comes from. When a kit is
 * saved, {@link #describe} records a {@code Requires} list naming the mod or addon pack behind every
 * namespace, so a player without it can be told exactly what to install. {@link #missing} checks a
 * kit (its raw NBT, since items from absent mods are dropped when the kit is loaded) against what
 * is installed right now. Works on both sides; no client classes.
 */
public final class KitRequirements {

    private KitRequirements() {}

    public static final String TAG = "Requires";

    public enum Kind { MOD, ADDON, UNKNOWN }

    /** One thing a kit needs. */
    public static final class Requirement {
        public final String id;      // namespace, e.g. "heroesexpansion"
        public final String name;    // readable name, e.g. "Heroes Expansion"
        public final Kind kind;
        public final String file;    // addon pack file name, when known ("" otherwise)

        Requirement(String id, String name, Kind kind, String file) {
            this.id = id;
            this.name = name == null || name.isEmpty() ? id : name;
            this.kind = kind;
            this.file = file == null ? "" : file;
        }

        public String kindLabel() {
            return kind == Kind.MOD ? "Mod" : kind == Kind.ADDON ? "Hero Addon" : "Mod or Hero Addon";
        }
    }

    // ---------------------------------------------------------------------
    // Reading a kit
    // ---------------------------------------------------------------------

    /** Every registry name the kit refers to: items, icon, and its superpower's injection id. */
    private static List<String> ids(NBTTagCompound kitTag) {
        List<String> out = new ArrayList<String>();
        NBTTagList contents = kitTag.getTagList("Contents", 10);
        for (int i = 0; i < contents.tagCount(); i++) addId(out, contents.getCompoundTagAt(i).getString("id"));
        addId(out, kitTag.getCompoundTag("Icon").getString("id"));
        if (kitTag.hasKey("Power", 10)) {
            NBTTagCompound power = kitTag.getCompoundTag("Power");
            addId(out, power.getString("id"));
            addId(out, power.getCompoundTag("tag").getString("Injection"));
        }
        return out;
    }

    private static void addId(List<String> out, String id) {
        if (id != null && !id.isEmpty()) out.add(id);
    }

    private static String namespace(String id) {
        int c = id.indexOf(':');
        return (c < 0 ? "minecraft" : id.substring(0, c)).toLowerCase(Locale.ROOT);
    }

    private static boolean builtIn(String ns) {
        // Beyonder itself depends on LucraftCore, so it never needs mentioning.
        return "minecraft".equals(ns) || "herosmp".equals(ns) || "forge".equals(ns) || "lucraftcore".equals(ns);
    }

    /** Whether a registry name from the kit exists in this game (item, or LucraftCore injection). */
    private static boolean resolves(String id, NBTTagCompound kitTag) {
        ResourceLocation rl = new ResourceLocation(id);
        if (ForgeRegistries.ITEMS.containsKey(rl)) return true;
        // Not an item: it may be the power's injection id.
        if (kitTag.hasKey("Power", 10) && id.equals(kitTag.getCompoundTag("Power").getCompoundTag("tag").getString("Injection"))) {
            try {
                return ItemInjection.getInjection(new ItemStack(kitTag.getCompoundTag("Power"))) != null;
            } catch (RuntimeException e) {
                return false;
            }
        }
        return false;
    }

    /** The kit's previously recorded requirements, by namespace. */
    private static Map<String, Requirement> recorded(NBTTagCompound kitTag) {
        Map<String, Requirement> out = new LinkedHashMap<String, Requirement>();
        NBTTagList list = kitTag.getTagList(TAG, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound r = list.getCompoundTagAt(i);
            String id = r.getString("Id").toLowerCase(Locale.ROOT);
            if (id.isEmpty()) continue;
            Kind kind;
            try {
                kind = Kind.valueOf(r.getString("Kind").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                kind = Kind.UNKNOWN;
            }
            out.put(id, new Requirement(id, r.getString("Name"), kind, r.getString("File")));
        }
        return out;
    }

    /**
     * What this game is missing to load the kit fully, one entry per mod / addon. Empty when
     * everything resolves.
     */
    public static List<Requirement> missing(NBTTagCompound kitTag) {
        if (kitTag == null) return Collections.emptyList();
        Map<String, Requirement> known = recorded(kitTag);
        Set<String> missingNs = new LinkedHashSet<String>();
        for (String id : ids(kitTag)) {
            String ns = namespace(id);
            if (!builtIn(ns) && !resolves(id, kitTag)) missingNs.add(ns);
        }
        // Recorded requirements whose items were already lost when the kit was re-saved elsewhere.
        for (Requirement r : known.values()) {
            if (!missingNs.contains(r.id) && !installed(r)) missingNs.add(r.id);
        }
        List<Requirement> out = new ArrayList<Requirement>();
        for (String ns : missingNs) {
            Requirement r = known.get(ns);
            out.add(r != null ? r : new Requirement(ns, ns, Kind.UNKNOWN, ""));
        }
        return out;
    }

    /** Whether a recorded requirement is present (mod loaded, or an addon pack adds that namespace). */
    private static boolean installed(Requirement r) {
        if (Loader.isModLoaded(r.id)) return true;
        return addonForDomain(r.id) != null;
    }

    /** Readable "Heroes Expansion, Neon Heroes" list. */
    public static String names(List<Requirement> list) {
        StringBuilder sb = new StringBuilder();
        for (Requirement r : list) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(r.name);
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------------
    // Writing a kit
    // ---------------------------------------------------------------------

    /**
     * The {@code Requires} list for a kit about to be saved: the mod or addon pack behind every
     * namespace it uses, plus earlier entries for things not installed here (their items were
     * dropped when the kit was loaded, but the kit still needs them).
     */
    public static NBTTagList describe(NBTTagCompound kitTag, NBTTagList previous) {
        Map<String, Requirement> out = new LinkedHashMap<String, Requirement>();
        for (String id : ids(kitTag)) {
            String ns = namespace(id);
            if (builtIn(ns) || out.containsKey(ns)) continue;
            ModContainer mod = Loader.instance().getIndexedModList().get(ns);
            if (mod != null) {
                out.put(ns, new Requirement(ns, mod.getName(), Kind.MOD, ""));
                continue;
            }
            AddonPackInfo pack = addonForDomain(ns);
            if (pack != null) {
                out.put(ns, new Requirement(ns, pack.getName(), Kind.ADDON, pack.getFile() != null ? pack.getFile().getName() : ""));
            }
        }
        if (previous != null) {
            NBTTagCompound holder = new NBTTagCompound();
            holder.setTag(TAG, previous);
            for (Requirement r : recorded(holder).values()) {
                if (!out.containsKey(r.id) && !installed(r)) out.put(r.id, r);
            }
        }
        NBTTagList list = new NBTTagList();
        for (Requirement r : out.values()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("Id", r.id);
            t.setString("Name", r.name);
            t.setString("Kind", r.kind.name());
            if (!r.file.isEmpty()) t.setString("File", r.file);
            ModContainer mod = r.kind == Kind.MOD ? Loader.instance().getIndexedModList().get(r.id) : null;
            if (mod != null) t.setString("Version", mod.getVersion());
            list.appendTag(t);
        }
        return list;
    }

    // ---------------------------------------------------------------------
    // Addon packs
    // ---------------------------------------------------------------------

    private static final Map<String, Set<String>> DOMAINS_BY_FILE = new HashMap<String, Set<String>>();

    /** The loaded addon pack that adds assets under {@code domain}, or null. */
    public static AddonPackInfo addonForDomain(String domain) {
        try {
            for (AddonPackInfo info : AddonPackHandler.ADDON_PACKS) {
                File f = info.getFile();
                if (f != null && domainsOf(f).contains(domain)) return info;
            }
        } catch (Throwable t) {
            // LucraftCore's addon pack list unavailable: treat as no addon.
        }
        return null;
    }

    /** Asset namespaces inside an addon pack (zip/jar or folder), cached per file. */
    public static synchronized Set<String> domainsOf(File packFile) {
        String key = packFile.getAbsolutePath() + "@" + packFile.lastModified();
        Set<String> cached = DOMAINS_BY_FILE.get(key);
        if (cached != null) return cached;
        Set<String> out = new LinkedHashSet<String>();
        if (packFile.isDirectory()) {
            File[] dirs = new File(packFile, "assets").listFiles(File::isDirectory);
            if (dirs != null) for (File d : dirs) out.add(d.getName().toLowerCase(Locale.ROOT));
        } else {
            try (ZipFile zip = new ZipFile(packFile)) {
                Enumeration<? extends ZipEntry> en = zip.entries();
                while (en.hasMoreElements()) {
                    String n = en.nextElement().getName();
                    if (!n.startsWith("assets/")) continue;
                    int slash = n.indexOf('/', 7);
                    if (slash > 7) out.add(n.substring(7, slash).toLowerCase(Locale.ROOT));
                }
            } catch (Exception ignored) {
                // Not a readable zip.
            }
        }
        DOMAINS_BY_FILE.put(key, out);
        return out;
    }
}
