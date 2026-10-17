package com.matoon.herosmp.client.store;

/**
 * One downloadable item in the content-pack storefront.
 *
 * A MAP pack is a Drive FOLDER (a full world save) installed globally into
 * {@code herosmp_hg_maps/}. A KIT pack is a Drive FILE (gzip NBT) installed into a
 * user-selected world save. An ADDON pack is a Drive FILE (a LucraftCore addon pack .zip/.jar)
 * dropped into LucraftCore's addon packs folder; it loads the next time the game starts.
 */
public final class StorePack {

    public enum PackType { MAP, KIT, ADDON }

    public final PackType type;
    public final String   displayName;
    public final String   driveId;
    public final boolean  isFolder;
    /** Set for packs uploaded from the user's computer (a world folder or .zip); null for store packs. */
    public final java.io.File localSource;
    /** Original file name on Drive (kept for addon packs, which install under it); may be null. */
    public final String fileName;

    public StorePack(PackType type, String displayName, String driveId, boolean isFolder) {
        this(type, displayName, driveId, isFolder, null, null);
    }

    /** A Hero Addon (LucraftCore addon pack) file on Drive. */
    public static StorePack addon(String displayName, String driveId, String fileName) {
        return new StorePack(PackType.ADDON, displayName, driveId, false, null, fileName);
    }

    private StorePack(PackType type, String displayName, String driveId, boolean isFolder, java.io.File localSource, String fileName) {
        this.fileName = fileName;
        this.type = type;
        this.displayName = displayName;
        this.driveId = driveId;
        this.isFolder = isFolder;
        this.localSource = localSource;
    }

    /** A map uploaded from a world folder or .zip on the user's computer. */
    public static StorePack localMap(String name, java.io.File source) {
        return new StorePack(PackType.MAP, name, "", source.isDirectory(), source, null);
    }

    /** Short human-readable label for the pack's type, used as a storefront badge. */
    public String typeLabel() {
        switch (type) {
            case MAP: return "Hunger Games Map";
            case KIT: return "PVP Kit";
            case ADDON: return "Hero Addon";
            default:  return "Pack";
        }
    }
}
