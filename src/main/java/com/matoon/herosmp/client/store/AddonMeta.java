package com.matoon.herosmp.client.store;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * What a Hero Addon says about itself: the name, author and description in its
 * {@code addonpack.mcmeta}, and its icon (the mcmeta's {@code "icon"}, usually {@code logo.png}).
 * <p>
 * For store packs this is read without downloading the whole zip: one byte-range request fetches
 * the zip's central directory from the end of the file, then one request per entry fetches just the
 * mcmeta and the icon. If Drive won't serve ranges, the zip is downloaded once into the store cache
 * (and the installer reuses that copy). Installed packs are read straight from disk.
 */
@SideOnly(Side.CLIENT)
public final class AddonMeta {

    private AddonMeta() {}

    /** A pack's own description of itself. Fields may be empty; {@link #icon()} may be null. */
    public static final class Info {
        public final String name, author, description;
        /** The logo's two main colours (opaque ARGB), for gradients and borders that match it. */
        public final int primary, secondary;
        private final BufferedImage image;
        private final String key;

        Info(String key, String name, String author, String description, BufferedImage image) {
            this.key = key;
            this.name = name == null ? "" : name;
            this.author = author == null ? "" : author;
            this.description = description == null ? "" : description;
            this.image = image;
            int[] pal = image != null ? palette(image) : new int[]{0xFF5B3A9A, 0xFF1E1236};
            this.primary = pal[0];
            this.secondary = pal[1];
        }

        /** The icon as a GUI texture (client thread), or null when the pack has none. */
        public PackThumbnails.Thumb icon() {
            return image == null ? null : PackThumbnails.image("addon_" + key, image);
        }
    }

    private static final Info NONE = new Info("none", "", "", "", null);
    private static final Map<String, Info> READY = new ConcurrentHashMap<String, Info>();
    private static final Map<String, Boolean> IN_FLIGHT = new ConcurrentHashMap<String, Boolean>();

    /** Info for a store addon, or null while it's still being fetched (or if it can't be read). */
    public static Info remote(final StorePack pack) {
        if (pack.driveId == null || pack.driveId.isEmpty()) return null;
        final String key = "r_" + pack.driveId;
        Info i = READY.get(key);
        if (i != null) return i == NONE ? null : i;
        if (IN_FLIGHT.putIfAbsent(key, Boolean.TRUE) != null) return null;
        Thread t = new Thread(() -> {
            Info info;
            try {
                info = readRemote(key, pack.driveId);
            } catch (Exception e) {
                info = null;
            }
            READY.put(key, info != null ? info : NONE);
            IN_FLIGHT.remove(key);
        }, "Beyonder addon info");
        t.setDaemon(true);
        t.start();
        return null;
    }

    /** True while a store addon's info is still being fetched. */
    public static boolean loading(StorePack pack) {
        return pack.driveId != null && IN_FLIGHT.containsKey("r_" + pack.driveId);
    }

    /** Info for an addon pack on disk (zip/jar or folder); read once and cached by path + mtime. */
    public static Info local(File pack) {
        String key = "l_" + pack.getAbsolutePath().hashCode() + "_" + pack.lastModified();
        Info i = READY.get(key);
        if (i != null) return i == NONE ? null : i;
        Info info;
        try {
            info = pack.isDirectory() ? readFolder(key, pack) : readZipFile(key, pack);
        } catch (Exception e) {
            info = null;
        }
        READY.put(key, info != null ? info : NONE);
        return info;
    }

    /** Store-cache copy of a remote addon zip (made only when range reads fail). */
    public static File cacheFile(String driveId) {
        return new File(new File(Minecraft.getMinecraft().gameDir, "beyonder_store_cache/addons"), driveId + ".zip");
    }

    // ---------------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------------

    private static Info parse(String key, byte[] mcmeta, Map<String, byte[]> files, String iconName) {
        String name = "", author = "", desc = "";
        try {
            JsonObject o = new JsonParser().parse(new String(mcmeta, StandardCharsets.UTF_8)).getAsJsonObject();
            if (o.has("name")) name = o.get("name").getAsString();
            if (o.has("author")) author = o.get("author").getAsString();
            if (o.has("description")) desc = o.get("description").getAsString();
        } catch (Exception ignored) {
            // Bad JSON: leave the fields blank.
        }
        BufferedImage img = null;
        byte[] png = files.get(iconName);
        if (png != null) {
            try {
                img = ImageIO.read(new ByteArrayInputStream(png));
                if (img != null && Math.max(img.getWidth(), img.getHeight()) > 256) img = shrink(img, 256);
            } catch (IOException ignored) {
                // No usable icon.
            }
        }
        return new Info(key, name, author, desc, img);
    }

    /** The icon path the mcmeta names (default logo.png). */
    private static String iconName(byte[] mcmeta) {
        try {
            JsonObject o = new JsonParser().parse(new String(mcmeta, StandardCharsets.UTF_8)).getAsJsonObject();
            if (o.has("icon")) return o.get("icon").getAsString().replace('\\', '/');
        } catch (Exception ignored) {
            // Fall through.
        }
        return "logo.png";
    }

    private static Info readZipFile(String key, File f) throws IOException {
        try (ZipFile zip = new ZipFile(f)) {
            ZipEntry meta = zip.getEntry("addonpack.mcmeta");
            if (meta == null) return null;
            byte[] mcmeta = readAll(zip.getInputStream(meta));
            String icon = iconName(mcmeta);
            Map<String, byte[]> files = new HashMap<String, byte[]>();
            ZipEntry ie = zip.getEntry(icon);
            if (ie != null) files.put(icon, readAll(zip.getInputStream(ie)));
            return parse(key, mcmeta, files, icon);
        }
    }

    private static Info readFolder(String key, File dir) throws IOException {
        File meta = new File(dir, "addonpack.mcmeta");
        if (!meta.isFile()) return null;
        byte[] mcmeta = Files.readAllBytes(meta.toPath());
        String icon = iconName(mcmeta);
        Map<String, byte[]> files = new HashMap<String, byte[]>();
        File ic = new File(dir, icon);
        if (ic.isFile()) files.put(icon, Files.readAllBytes(ic.toPath()));
        return parse(key, mcmeta, files, icon);
    }

    private static Info readRemote(String key, String driveId) throws IOException {
        File cached = cacheFile(driveId);
        if (cached.isFile() && cached.length() > 0) return readZipFile(key, cached);
        try {
            return readRemoteRanges(key, driveId);
        } catch (IOException rangeFailed) {
            // No range support: fetch the whole zip once (the installer reuses it).
            File part = new File(cached.getParentFile(), cached.getName() + ".part");
            DriveClient.downloadFile(driveId, part, null);
            Files.move(part.toPath(), cached.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return readZipFile(key, cached);
        }
    }

    // ---------------------------------------------------------------------
    // Remote zip, read by byte ranges
    // ---------------------------------------------------------------------

    private static final class CdEntry {
        String name;
        int method;
        long compSize, localOffset;
    }

    private static Info readRemoteRanges(String key, String driveId) throws IOException {
        DriveClient.Range tail = DriveClient.readRange(driveId, "bytes=-65557"); // max EOCD + comment
        byte[] t = tail.data;
        int eocd = -1;
        for (int i = t.length - 22; i >= 0; i--) {
            if (u32(t, i) == 0x06054b50L) { eocd = i; break; }
        }
        if (eocd < 0 || tail.total <= 0) throw new IOException("Not a zip");
        long cdSize = u32(t, eocd + 12), cdOffset = u32(t, eocd + 16);
        long tailStart = tail.total - t.length;
        byte[] cd;
        if (cdOffset >= tailStart) {
            int from = (int) (cdOffset - tailStart);
            cd = java.util.Arrays.copyOfRange(t, from, (int) Math.min(t.length, from + cdSize));
        } else {
            cd = DriveClient.readRange(driveId, "bytes=" + cdOffset + "-" + (cdOffset + cdSize - 1)).data;
        }

        Map<String, CdEntry> entries = new HashMap<String, CdEntry>();
        int p = 0;
        while (p + 46 <= cd.length && u32(cd, p) == 0x02014b50L) {
            CdEntry e = new CdEntry();
            e.method = u16(cd, p + 10);
            e.compSize = u32(cd, p + 20);
            int nameLen = u16(cd, p + 28), extraLen = u16(cd, p + 30), commentLen = u16(cd, p + 32);
            e.localOffset = u32(cd, p + 42);
            e.name = new String(cd, p + 46, nameLen, StandardCharsets.UTF_8);
            entries.put(e.name.toLowerCase(Locale.ROOT), e);
            p += 46 + nameLen + extraLen + commentLen;
        }
        CdEntry metaEntry = entries.get("addonpack.mcmeta");
        if (metaEntry == null) throw new IOException("No addonpack.mcmeta");
        byte[] mcmeta = fetchEntry(driveId, metaEntry);
        String icon = iconName(mcmeta);
        Map<String, byte[]> files = new HashMap<String, byte[]>();
        CdEntry iconEntry = entries.get(icon.toLowerCase(Locale.ROOT));
        if (iconEntry != null && iconEntry.compSize < 4_000_000) files.put(icon, fetchEntry(driveId, iconEntry));
        return parse(key, mcmeta, files, icon);
    }

    /** One entry's bytes: its local header (whose name/extra lengths we only learn here), then data. */
    private static byte[] fetchEntry(String driveId, CdEntry e) throws IOException {
        long end = e.localOffset + 30 + 1024 + e.compSize; // generous room for the local name + extra field
        byte[] r = DriveClient.readRange(driveId, "bytes=" + e.localOffset + "-" + end).data;
        if (u32(r, 0) != 0x04034b50L) throw new IOException("Bad local header");
        int start = 30 + u16(r, 26) + u16(r, 28);
        if (start + e.compSize > r.length) throw new IOException("Short read");
        byte[] comp = java.util.Arrays.copyOfRange(r, start, (int) (start + e.compSize));
        if (e.method == 0) return comp;
        if (e.method != 8) throw new IOException("Unsupported compression " + e.method);
        Inflater inf = new Inflater(true);
        try {
            inf.setInput(comp);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (java.util.zip.DataFormatException ex) {
            throw new IOException(ex);
        } finally {
            inf.end();
        }
    }

    /**
     * The image's dominant colour and a second, clearly different one: pixels are bucketed by hue
     * (weighted by how saturated and bright they are, ignoring transparent, near-black and grey
     * ones) and each colour is the average of its bucket. Grey images fall back to their average
     * colour and a darker shade of it.
     */
    static int[] palette(BufferedImage img) {
        final int buckets = 24;
        double[] weight = new double[buckets], r = new double[buckets], g = new double[buckets], b = new double[buckets];
        double ar = 0, ag = 0, ab = 0, an = 0;
        int step = Math.max(1, Math.max(img.getWidth(), img.getHeight()) / 96);
        float[] hsb = new float[3];
        for (int y = 0; y < img.getHeight(); y += step) {
            for (int x = 0; x < img.getWidth(); x += step) {
                int argb = img.getRGB(x, y);
                if ((argb >>> 24) < 128) continue;
                int pr = (argb >> 16) & 0xFF, pg = (argb >> 8) & 0xFF, pb = argb & 0xFF;
                ar += pr; ag += pg; ab += pb; an++;
                java.awt.Color.RGBtoHSB(pr, pg, pb, hsb);
                if (hsb[2] < 0.15f || hsb[1] < 0.22f) continue;
                int k = Math.min(buckets - 1, (int) (hsb[0] * buckets));
                double wgt = hsb[1] * hsb[2];
                weight[k] += wgt; r[k] += pr * wgt; g[k] += pg * wgt; b[k] += pb * wgt;
            }
        }
        int first = -1;
        for (int k = 0; k < buckets; k++) if (weight[k] > 0 && (first < 0 || weight[k] > weight[first])) first = k;
        if (first < 0) {
            int avg = an > 0 ? rgb(ar / an, ag / an, ab / an) : 0xFF5B3A9A;
            return new int[]{avg, shade(avg, 0.45f)};
        }
        int second = -1;
        for (int k = 0; k < buckets; k++) {
            int d = Math.abs(k - first);
            d = Math.min(d, buckets - d);
            if (d >= 3 && weight[k] > weight[first] * 0.08 && (second < 0 || weight[k] > weight[second])) second = k;
        }
        int c1 = rgb(r[first] / weight[first], g[first] / weight[first], b[first] / weight[first]);
        int c2 = second >= 0 ? rgb(r[second] / weight[second], g[second] / weight[second], b[second] / weight[second]) : shade(c1, 0.45f);
        return new int[]{c1, c2};
    }

    private static int rgb(double r, double g, double b) {
        return 0xFF000000 | ((int) Math.round(r) << 16) | ((int) Math.round(g) << 8) | (int) Math.round(b);
    }

    private static int shade(int argb, float f) {
        return rgb(((argb >> 16) & 0xFF) * f, ((argb >> 8) & 0xFF) * f, (argb & 0xFF) * f);
    }

    /** Big logos (often 1000+ px) scaled down so their textures stay small; nearest-neighbour keeps pixel art crisp. */
    private static BufferedImage shrink(BufferedImage src, int max) {
        float f = max / (float) Math.max(src.getWidth(), src.getHeight());
        int w = Math.max(1, Math.round(src.getWidth() * f)), h = Math.max(1, Math.round(src.getHeight() * f));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static long u32(byte[] b, int i) {
        return (b[i] & 0xFFL) | ((b[i + 1] & 0xFFL) << 8) | ((b[i + 2] & 0xFFL) << 16) | ((b[i + 3] & 0xFFL) << 24);
    }

    private static int u16(byte[] b, int i) {
        return (b[i] & 0xFF) | ((b[i + 1] & 0xFF) << 8);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = s.read(buf)) >= 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }
}
