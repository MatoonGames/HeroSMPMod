package com.matoon.herosmp.client.store;

import com.matoon.herosmp.client.store.DriveClient.DriveEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Builds the storefront catalog from a public Google Drive folder.
 *
 * Preferred layout — one category subfolder per pack type:
 *   &lt;root&gt;/Hunger Games Maps/&lt;MapName&gt;/   (each child folder = one map pack)
 *   &lt;root&gt;/PVP Kits/&lt;KitPack&gt;.dat         (each child file   = one kit pack)
 *
 * Backwards-compatible fallback: if neither category folder exists, every folder directly
 * under the root is treated as a map pack (so a flat folder of maps works out of the box).
 */
public final class StoreCatalog {

    public static final String CAT_MAPS = "Hunger Games Maps";
    public static final String CAT_KITS = "PVP Kits";

    private StoreCatalog() {}

    /** Outcome of a catalog fetch: either a (possibly empty) pack list, or an error message. */
    public static final class Result {
        public final List<StorePack> packs;
        public final String          error;

        private Result(List<StorePack> packs, String error) {
            this.packs = packs;
            this.error = error;
        }
    }

    /** Blocking fetch — call off the client main thread. Never throws; errors land in Result.error. */
    public static Result fetch(String rootFolderId) {
        try {
            List<DriveEntry> root = DriveClient.listFolder(rootFolderId);
            List<StorePack> packs = new ArrayList<>();
            boolean foundCategory = false;

            for (DriveEntry e : root) {
                if (!e.isFolder) continue;
                if (e.name.equalsIgnoreCase(CAT_MAPS)) {
                    foundCategory = true;
                    for (DriveEntry m : DriveClient.listFolder(e.id)) {
                        if (m.isFolder) packs.add(new StorePack(StorePack.PackType.MAP, m.name, m.id, true));
                    }
                } else if (e.name.equalsIgnoreCase(CAT_KITS)) {
                    foundCategory = true;
                    for (DriveEntry k : DriveClient.listFolder(e.id)) {
                        if (!k.isFolder) {
                            packs.add(new StorePack(StorePack.PackType.KIT, stripExtension(k.name), k.id, false));
                        }
                    }
                }
            }

            // Fallback: a flat folder of map folders (no category subfolders yet).
            if (!foundCategory) {
                for (DriveEntry e : root) {
                    if (e.isFolder) packs.add(new StorePack(StorePack.PackType.MAP, e.name, e.id, true));
                }
            }

            return new Result(packs, null);
        } catch (Exception ex) {
            String msg = ex.getMessage();
            return new Result(Collections.<StorePack>emptyList(),
                    msg != null ? msg : ex.getClass().getSimpleName());
        }
    }

    private static String stripExtension(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".dat")) return name.substring(0, name.length() - 4);
        return name;
    }
}
