package com.matoon.herosmp.npc.kit;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Kit cards: a PNG picture of the kit (shown as the file's icon in File Explorer, Drive, Discord...)
 * that also carries the kit file itself in a private PNG chunk. Image viewers ignore the chunk; the
 * store, the Kit Library and servers read the kit back out of it.
 *
 * Pure Java (no rendering), so dedicated servers can read cards too. Creating the picture happens on
 * the client.
 */
public final class KitCard {

    private KitCard() {}

    /**
     * Chunk type "byKt": lowercase 1st letter = ancillary (viewers may skip it), lowercase 2nd =
     * private, uppercase 3rd = reserved bit clear, lowercase 4th = safe to copy.
     */
    private static final byte[] CHUNK = "byKt".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    public static boolean isPng(byte[] bytes) {
        if (bytes == null || bytes.length < SIGNATURE.length) return false;
        for (int i = 0; i < SIGNATURE.length; i++) if (bytes[i] != SIGNATURE[i]) return false;
        return true;
    }

    /** Inserts the kit file into the PNG (just before IEND). */
    public static byte[] embed(byte[] png, byte[] kitFile) {
        if (!isPng(png)) throw new IllegalArgumentException("Not a PNG");
        int iend = findChunk(png, "IEND".getBytes(StandardCharsets.US_ASCII));
        if (iend < 0) throw new IllegalArgumentException("PNG has no IEND chunk");
        ByteArrayOutputStream out = new ByteArrayOutputStream(png.length + kitFile.length + 12);
        out.write(png, 0, iend);
        writeInt(out, kitFile.length);
        out.write(CHUNK, 0, 4);
        out.write(kitFile, 0, kitFile.length);
        CRC32 crc = new CRC32();
        crc.update(CHUNK);
        crc.update(kitFile);
        writeInt(out, (int) crc.getValue());
        out.write(png, iend, png.length - iend);
        return out.toByteArray();
    }

    /** The kit file inside a kit card, or null if the PNG doesn't carry one (or it's corrupt). */
    public static byte[] extract(byte[] png) {
        if (!isPng(png)) return null;
        int pos = findChunk(png, CHUNK);
        if (pos < 0) return null;
        int len = readInt(png, pos);
        if (len < 0 || pos + 12 + len > png.length) return null;
        byte[] data = new byte[len];
        System.arraycopy(png, pos + 8, data, 0, len);
        CRC32 crc = new CRC32();
        crc.update(CHUNK);
        crc.update(data);
        return (int) crc.getValue() == readInt(png, pos + 8 + len) ? data : null;
    }

    /** Offset of the chunk's length field, or -1. Walks the chunk list rather than searching bytes. */
    private static int findChunk(byte[] png, byte[] type) {
        int pos = SIGNATURE.length;
        while (pos + 12 <= png.length) {
            int len = readInt(png, pos);
            if (len < 0 || pos + 12 + (long) len > png.length) return -1;
            boolean match = true;
            for (int i = 0; i < 4; i++) if (png[pos + 4 + i] != type[i]) { match = false; break; }
            if (match) return pos;
            pos += 12 + len;
        }
        return -1;
    }

    private static int readInt(byte[] b, int p) {
        return ((b[p] & 0xFF) << 24) | ((b[p + 1] & 0xFF) << 16) | ((b[p + 2] & 0xFF) << 8) | (b[p + 3] & 0xFF);
    }

    private static void writeInt(ByteArrayOutputStream out, int v) {
        out.write((v >>> 24) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write(v & 0xFF);
    }
}
