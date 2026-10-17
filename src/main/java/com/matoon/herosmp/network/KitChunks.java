package com.matoon.herosmp.network;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits kit files into packet-sized pieces and puts them back together. Kit files travel in
 * chunks both ways because client → server custom payloads are capped at ~32 KB in 1.12.
 */
public final class KitChunks {

    public static final int CHUNK_SIZE = 24_000;
    /** Largest kit file accepted over the network. */
    public static final int MAX_BYTES = 4 * 1024 * 1024;

    private KitChunks() {}

    public static List<byte[]> split(byte[] data) {
        List<byte[]> out = new ArrayList<byte[]>();
        for (int pos = 0; pos < data.length || out.isEmpty(); pos += CHUNK_SIZE) {
            int len = Math.min(CHUNK_SIZE, data.length - pos);
            byte[] c = new byte[Math.max(0, len)];
            if (len > 0) System.arraycopy(data, pos, c, 0, len);
            out.add(c);
        }
        return out;
    }

    /** Reassembles one transfer. Rejects inconsistent or oversized transfers. */
    public static final class Assembler {
        private final int total;
        private final byte[][] chunks;
        private int received;
        private long bytes;

        public Assembler(int totalChunks) {
            if (totalChunks <= 0 || totalChunks > MAX_BYTES / CHUNK_SIZE + 1) {
                throw new IllegalArgumentException("Bad chunk count: " + totalChunks);
            }
            this.total = totalChunks;
            this.chunks = new byte[totalChunks][];
        }

        /** Stores a chunk; returns true once every chunk has arrived. */
        public boolean accept(int index, int totalChunks, byte[] data) {
            if (totalChunks != total || index < 0 || index >= total || data == null || data.length > CHUNK_SIZE) {
                throw new IllegalArgumentException("Bad chunk " + index + "/" + totalChunks);
            }
            if (chunks[index] == null) {
                chunks[index] = data;
                received++;
                bytes += data.length;
            }
            return received == total;
        }

        public int received() { return received; }
        public int total() { return total; }
        public long bytes() { return bytes; }
        public boolean complete() { return received == total; }

        public byte[] result() {
            if (!complete()) throw new IllegalStateException("Transfer incomplete");
            byte[] out = new byte[(int) bytes];
            int pos = 0;
            for (byte[] c : chunks) {
                System.arraycopy(c, 0, out, pos, c.length);
                pos += c.length;
            }
            return out;
        }
    }
}
