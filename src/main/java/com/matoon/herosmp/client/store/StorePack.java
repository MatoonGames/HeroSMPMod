package com.matoon.herosmp.client.store;

/**
 * One downloadable item in the content-pack storefront.
 *
 * A MAP pack is a Drive FOLDER (a full world save) installed globally into
 * {@code herosmp_hg_maps/}. A KIT pack is a Drive FILE (gzip NBT) installed into a
 * user-selected world save. The design intentionally leaves room for more pack types.
 */
public final class StorePack {

    public enum PackType { MAP, KIT }

    public final PackType type;
    public final String   displayName;
    public final String   driveId;
    public final boolean  isFolder;

    public StorePack(PackType type, String displayName, String driveId, boolean isFolder) {
        this.type = type;
        this.displayName = displayName;
        this.driveId = driveId;
        this.isFolder = isFolder;
    }

    /** Short human-readable label for the pack's type, used as a storefront badge. */
    public String typeLabel() {
        switch (type) {
            case MAP: return "Hunger Games Map";
            case KIT: return "PVP Kit";
            default:  return "Pack";
        }
    }
}
