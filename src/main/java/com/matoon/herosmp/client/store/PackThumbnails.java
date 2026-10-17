package com.matoon.herosmp.client.store;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and caches pack artwork as GUI textures. Each pack may provide a high-resolution
 * {@code thumbnail.png} (store art, ideally 16:9 such as 640x360); otherwise the world's 64x64
 * {@code icon.png} is used.
 *
 * Local art is decoded on demand. Remote art is fetched on a daemon thread and the
 * {@link DynamicTexture} is created on the client thread. A {@code null} return means "not ready /
 * none", and callers draw a placeholder.
 */
public final class PackThumbnails {

    private PackThumbnails() {}

    /** An uploaded texture plus its pixel size (needed for aspect-correct cropping). */
    public static final class Thumb {
        public final ResourceLocation rl;
        public final int w, h;
        Thumb(ResourceLocation rl, int w, int h) { this.rl = rl; this.w = w; this.h = h; }
    }

    public static final String[] ART_NAMES = {"thumbnail.png", "icon.png"};

    private static final ConcurrentHashMap<String, Thumb> READY = new ConcurrentHashMap<String, Thumb>();
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    private static final List<ResourceLocation> ALLOCATED = new ArrayList<ResourceLocation>();

    // ---------------------------------------------------------------------

    /** Art for an installed map folder. */
    public static Thumb installedMap(String mapName) {
        File dir = InstalledPacks.mapDir(mapName);
        File[] candidates = new File[ART_NAMES.length];
        for (int i = 0; i < ART_NAMES.length; i++) candidates[i] = new File(dir, ART_NAMES[i]);
        return local("map_" + mapName, candidates);
    }

    /** Art for a world save (its icon.png). */
    public static Thumb world(File worldDir) {
        return local("world_" + worldDir.getName(), new File(worldDir, "icon.png"));
    }

    /** First existing file wins. Decoded synchronously (local files are tiny). */
    public static Thumb local(String key, File... candidates) {
        String k = "hs_l_" + safe(key);
        Thumb t = READY.get(k);
        if (t != null) return t;
        if (FAILED.contains(k)) return null;
        for (File f : candidates) {
            if (f == null || !f.isFile()) continue;
            try {
                BufferedImage img = ImageIO.read(f);
                if (img != null) return upload(k, img);
            } catch (Exception ignored) {
                // Try the next candidate.
            }
        }
        FAILED.add(k);
        return null;
    }

    /** Art for a store pack whose Drive folder may hold thumbnail.png / icon.png. Async. */
    public static Thumb remote(String key, final String driveFolderId) {
        final String k = "hs_r_" + safe(key);
        Thumb t = READY.get(k);
        if (t != null) return t;
        if (FAILED.contains(k) || !IN_FLIGHT.add(k)) return null;

        Thread th = new Thread(() -> {
            File tmp = null;
            try {
                String artId = null;
                List<DriveClient.DriveEntry> listing = PackMeta.list(driveFolderId);
                outer:
                for (String want : ART_NAMES) {
                    for (DriveClient.DriveEntry e : listing) {
                        if (!e.isFolder && e.name.equalsIgnoreCase(want)) { artId = e.id; break outer; }
                    }
                }
                if (artId == null) { FAILED.add(k); return; }

                tmp = File.createTempFile("hs_thumb_", ".png");
                DriveClient.downloadFile(artId, tmp, null);
                final BufferedImage img = ImageIO.read(tmp);
                if (img == null) { FAILED.add(k); return; }
                Minecraft.getMinecraft().addScheduledTask(() -> { upload(k, img); });
            } catch (Exception e) {
                FAILED.add(k);
            } finally {
                IN_FLIGHT.remove(k);
                if (tmp != null) tmp.delete();
            }
        }, "HeroSMP pack art");
        th.setDaemon(true);
        th.start();
        return null;
    }

    /** Texture for an already-decoded image (e.g. a Hero Addon's logo), uploaded once. Client thread only. */
    public static Thumb image(String key, BufferedImage img) {
        String k = "hs_i_" + safe(key);
        Thumb t = READY.get(k);
        return t != null ? t : upload(k, img);
    }

    /** Client thread only. */
    private static Thumb upload(String key, BufferedImage img) {
        Thumb existing = READY.get(key);
        if (existing != null) return existing;
        DynamicTexture tex = new DynamicTexture(img);
        ResourceLocation rl = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation(key, tex);
        Thumb t = new Thumb(rl, img.getWidth(), img.getHeight());
        READY.put(key, t);
        synchronized (ALLOCATED) { ALLOCATED.add(rl); }
        return t;
    }

    /** Forget a local entry (after install / delete) so it reloads next frame. Any thread. */
    public static void invalidateInstalledMap(String mapName) {
        String k = "hs_l_" + safe("map_" + mapName);
        FAILED.remove(k);
        final Thumb t = READY.remove(k);
        if (t != null) {
            // Texture deletion touches GL, so it must happen on the client thread.
            Minecraft.getMinecraft().addScheduledTask(() -> {
                try { Minecraft.getMinecraft().getTextureManager().deleteTexture(t.rl); } catch (Exception ignored) {}
                synchronized (ALLOCATED) { ALLOCATED.remove(t.rl); }
            });
        }
    }

    public static void disposeAll() {
        Minecraft mc = Minecraft.getMinecraft();
        synchronized (ALLOCATED) {
            for (ResourceLocation rl : ALLOCATED) {
                try { mc.getTextureManager().deleteTexture(rl); } catch (Exception ignored) {}
            }
            ALLOCATED.clear();
        }
        READY.clear();
        FAILED.clear();
    }

    private static String safe(String s) {
        return s == null ? "null" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }
}
