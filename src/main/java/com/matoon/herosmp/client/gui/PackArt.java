package com.matoon.herosmp.client.gui;

import com.matoon.herosmp.client.gui.bedrock.BedrockUI;
import com.matoon.herosmp.client.store.PackThumbnails;
import com.matoon.herosmp.client.store.StorePack;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/** Resolves and draws a pack's artwork, falling back to a styled placeholder. */
final class PackArt {

    private PackArt() {}

    private static final ItemStack MAP_ICON = new ItemStack(Blocks.GRASS);
    private static final ItemStack KIT_ICON = new ItemStack(Items.DIAMOND_SWORD);

    /** Installed art wins (works offline); otherwise the store's Drive art, fetched lazily. */
    static PackThumbnails.Thumb thumb(StorePack pack, boolean installed) {
        if (pack.type != StorePack.PackType.MAP) return null;
        if (installed) {
            PackThumbnails.Thumb local = PackThumbnails.installedMap(pack.displayName);
            if (local != null) return local;
        }
        if (pack.isFolder && pack.driveId != null && !pack.driveId.isEmpty()) {
            return PackThumbnails.remote(pack.displayName, pack.driveId);
        }
        return null;
    }

    /** Fills the box with the pack's art (cover-cropped) or a placeholder. */
    static void draw(StorePack pack, boolean installed, int x, int y, int w, int h) {
        PackThumbnails.Thumb t = thumb(pack, installed);
        if (t != null) {
            BedrockUI.imageCover(t, x, y, w, h, 1f, false);
        } else {
            placeholder(pack.type, x, y, w, h);
        }
    }

    static void placeholder(StorePack.PackType type, int x, int y, int w, int h) {
        boolean map = type == StorePack.PackType.MAP;
        BedrockUI.vGradient(x, y, w, h, map ? 0xFF3E7A32 : 0xFF7A3232, map ? 0xFF16301A : 0xFF2E1414);
        // Subtle diagonal light for depth.
        BedrockUI.gradient(x, y, w, h, 0x26FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x00000000);

        float scale = Math.max(1f, Math.min(4f, (float) Math.floor(Math.min(w, h) / 30f)));
        int size = (int) (16 * scale);
        BedrockUI.item(map ? MAP_ICON : KIT_ICON, x + (w - size) / 2f, y + (h - size) / 2f, scale);
    }
}
