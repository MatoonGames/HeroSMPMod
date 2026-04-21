package com.matoon.herosmp.hungergames.music;

import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.MetadataSerializer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Collections;
import java.util.Set;

/**
 * Client-side resource pack that dynamically serves sounds.json and .ogg files
 * from the herosmp_hg_music directory, enabling server-admins to drop in custom
 * phase music without rebuilding the mod.
 *
 * Folder layout:  herosmp_hg_music/Lobby/, /Phase1/, /Phase2/, /Phase3/
 * Sound events:   herosmp:hg_music.lobby.<basename>, herosmp:hg_music.phase1.<basename>, etc.
 */
@SideOnly(Side.CLIENT)
public class HungerGamesMusicResourcePack implements IResourcePack {

    private final File musicDir;

    public HungerGamesMusicResourcePack(File musicDir) {
        this.musicDir = musicDir;
        for (String phase : HungerGamesMusicManager.ALL_PHASES)
            new File(musicDir, phase).mkdirs();
    }

    @Override
    public InputStream getInputStream(ResourceLocation location) throws IOException {
        String domain = domain(location);
        String path   = path(location);
        if (!"herosmp".equals(domain)) throw new FileNotFoundException(location.toString());

        if ("sounds.json".equals(path)) {
            String json = generateSoundsJson();
            if (json == null) throw new FileNotFoundException("No HG music tracks found");
            return new ByteArrayInputStream(json.getBytes("UTF-8"));
        }

        if (path.startsWith("sounds/hg_music/")) {
            File file = resolveOggFile(path);
            if (file != null) return new FileInputStream(file);
        }

        throw new FileNotFoundException("HG music resource not found: " + path);
    }

    @Override
    public boolean resourceExists(ResourceLocation location) {
        String domain = domain(location);
        String path   = path(location);
        if (!"herosmp".equals(domain)) return false;
        if ("sounds.json".equals(path)) return hasAnyOggFiles();
        if (path.startsWith("sounds/hg_music/")) return resolveOggFile(path) != null;
        return false;
    }

    // ResourceLocation getter names changed across MC versions; parse toString() which is always "namespace:path"
    private static String domain(ResourceLocation loc) {
        String s = loc.toString();
        int colon = s.indexOf(':');
        return colon >= 0 ? s.substring(0, colon) : s;
    }

    private static String path(ResourceLocation loc) {
        String s = loc.toString();
        int colon = s.indexOf(':');
        return colon >= 0 ? s.substring(colon + 1) : "";
    }

    @Override
    public Set<String> getResourceDomains() {
        return Collections.singleton("herosmp");
    }

    @Override
    public <T extends IMetadataSection> T getPackMetadata(MetadataSerializer serializer, String section) {
        return null;
    }

    @Override
    public BufferedImage getPackImage() throws IOException {
        throw new FileNotFoundException("No pack image");
    }

    @Override
    public String getPackName() {
        return "herosmp_hg_music";
    }

    // -------------------------------------------------------------------------

    private String generateSoundsJson() {
        StringBuilder sb = new StringBuilder("{\n");
        boolean any = false;
        boolean first = true;
        for (String phase : HungerGamesMusicManager.ALL_PHASES) {
            File dir = new File(musicDir, phase);
            File[] files = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".ogg"));
            if (files == null) continue;
            for (File f : files) {
                String base = f.getName().substring(0, f.getName().length() - 4);
                String phaseLower = phase.toLowerCase(java.util.Locale.ROOT);
                if (!first) sb.append(",\n");
                first = false;
                any = true;
                sb.append("  \"hg_music.").append(phaseLower).append('.').append(base).append("\": {");
                sb.append("\"sounds\": [{\"name\": \"herosmp:hg_music/").append(phaseLower).append('/').append(base).append("\", \"stream\": true}]");
                sb.append("}");
            }
        }
        sb.append("\n}");
        return any ? sb.toString() : null;
    }

    private File resolveOggFile(String resourcePath) {
        // resourcePath format: "sounds/hg_music/lobby/track.ogg"
        if (!resourcePath.startsWith("sounds/hg_music/")) return null;
        String sub = resourcePath.substring("sounds/hg_music/".length());
        int slash = sub.indexOf('/');
        if (slash < 0) return null;
        String phaseLower = sub.substring(0, slash);
        String filename   = sub.substring(slash + 1);

        for (String phase : HungerGamesMusicManager.ALL_PHASES) {
            if (phase.toLowerCase(java.util.Locale.ROOT).equals(phaseLower)) {
                File f = new File(new File(musicDir, phase), filename);
                return (f.exists() && f.isFile()) ? f : null;
            }
        }
        return null;
    }

    private boolean hasAnyOggFiles() {
        for (String phase : HungerGamesMusicManager.ALL_PHASES) {
            File dir = new File(musicDir, phase);
            File[] files = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".ogg"));
            if (files != null && files.length > 0) return true;
        }
        return false;
    }
}
