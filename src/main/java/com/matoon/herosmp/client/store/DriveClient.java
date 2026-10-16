package com.matoon.herosmp.client.store;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal, dependency-free Google Drive client for PUBLIC ("anyone with the link") folders.
 *
 * It uses two unofficial-but-long-stable keyless endpoints so no Google API key has to be
 * embedded in the mod:
 *   - Folder listing:  https://drive.google.com/embeddedfolderview?id=&lt;folderId&gt;
 *       returns an HTML page whose entries carry  id="entry-&lt;ID&gt;", a
 *       flip-entry-title">&lt;NAME&gt; label, and a folder-vs-file href
 *       (/drive/folders/&lt;id&gt; vs /file/d/&lt;id&gt;).
 *   - File download:   https://drive.google.com/uc?export=download&id=&lt;fileId&gt;
 *       returns the raw bytes for small files; large files first return an HTML
 *       "virus scan" interstitial whose confirm form we re-submit.
 *
 * All methods block and must be called off the client main thread.
 */
public final class DriveClient {

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Beyonder-ContentPacks";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS    = 30_000;
    private static final int DOWNLOAD_BUFFER    = 32 * 1024;

    private DriveClient() {}

    /** One child of a Drive folder. */
    public static final class DriveEntry {
        public final String  id;
        public final String  name;
        public final boolean isFolder;

        public DriveEntry(String id, String name, boolean isFolder) {
            this.id = id;
            this.name = name;
            this.isFolder = isFolder;
        }
    }

    /** Reports bytes streamed to disk and lets the caller abort a download. */
    public interface DownloadListener {
        void bytesTransferred(long delta);
        boolean isCancelled();
    }

    // -------------------------------------------------------------------------
    // Folder listing
    // -------------------------------------------------------------------------

    /** Lists the immediate children of a public Drive folder. Throws on network/parse failure. */
    public static List<DriveEntry> listFolder(String folderId) throws IOException {
        String html = httpGetString("https://drive.google.com/embeddedfolderview?id=" + folderId);
        List<DriveEntry> out = new ArrayList<>();

        // Each child is an element carrying id="entry-<ID>"; split on that marker so each
        // chunk holds exactly one entry's href + title.
        String[] parts = html.split("id=\"entry-");
        for (int i = 1; i < parts.length; i++) {
            String chunk = parts[i];
            int q = chunk.indexOf('"');
            if (q <= 0) continue;
            String id = chunk.substring(0, q);

            boolean isFolder = chunk.contains("/drive/folders/");

            String name = id;
            int t = chunk.indexOf("flip-entry-title\">");
            if (t >= 0) {
                int start = t + "flip-entry-title\">".length();
                int end = chunk.indexOf('<', start);
                if (end > start) name = unescapeHtml(chunk.substring(start, end).trim());
            }
            out.add(new DriveEntry(id, name, isFolder));
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // File download
    // -------------------------------------------------------------------------

    /**
     * Downloads a public Drive file to {@code dest}, streaming to disk and reporting progress.
     * Handles the large-file confirm interstitial. Throws on failure or cancellation.
     */
    public static void downloadFile(String fileId, File dest, DownloadListener listener) throws IOException {
        dest.getParentFile().mkdirs();
        String url = "https://drive.google.com/uc?export=download&id=" + fileId;

        HttpURLConnection conn = open(url);
        String cookie = firstSetCookie(conn);
        String type = conn.getContentType();

        if (type != null && type.toLowerCase().contains("text/html")) {
            // Interstitial — read the confirm form and re-request the real download URL.
            String page = readAll(conn.getInputStream());
            conn.disconnect();
            String confirmUrl = extractConfirmUrl(page, fileId);
            if (confirmUrl == null) throw new IOException("Drive returned a confirmation page we could not parse.");
            conn = open(confirmUrl);
            if (cookie != null) conn.setRequestProperty("Cookie", cookie);
            String type2 = conn.getContentType();
            if (type2 != null && type2.toLowerCase().contains("text/html")) {
                conn.disconnect();
                throw new IOException("Drive would not release the file without interactive confirmation.");
            }
        }

        streamToFile(conn, dest, listener);
    }

    private static void streamToFile(HttpURLConnection conn, File dest, DownloadListener listener) throws IOException {
        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[DOWNLOAD_BUFFER];
            int read;
            while ((read = in.read(buf)) >= 0) {
                if (listener != null && listener.isCancelled()) {
                    throw new IOException("Download cancelled.");
                }
                out.write(buf, 0, read);
                if (listener != null && read > 0) listener.bytesTransferred(read);
            }
        } finally {
            conn.disconnect();
        }
    }

    // -------------------------------------------------------------------------
    // HTTP helpers
    // -------------------------------------------------------------------------

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", UA);
        return conn;
    }

    private static String httpGetString(String url) throws IOException {
        HttpURLConnection conn = open(url);
        try {
            int code = conn.getResponseCode();
            if (code / 100 != 2) throw new IOException("HTTP " + code + " for " + url);
            return readAll(conn.getInputStream());
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int read;
        try (InputStream s = in) {
            while ((read = s.read(buf)) >= 0) bos.write(buf, 0, read);
        }
        return new String(bos.toByteArray(), "UTF-8");
    }

    private static String firstSetCookie(HttpURLConnection conn) {
        String sc = conn.getHeaderField("Set-Cookie");
        if (sc == null) return null;
        int semi = sc.indexOf(';');
        return semi > 0 ? sc.substring(0, semi) : sc;
    }

    /**
     * Pulls the real download URL out of a Drive confirmation page. Handles both the classic
     * {@code ?confirm=TOKEN} form and the newer drive.usercontent.google.com form with hidden
     * inputs, rebuilding it as a GET URL.
     */
    private static String extractConfirmUrl(String page, String fileId) {
        // Newer flow: a <form ... action="https://drive.usercontent.google.com/download"> with
        // hidden <input name=.. value=..> fields.
        int formAction = page.indexOf("action=\"");
        if (page.contains("drive.usercontent.google.com") && formAction >= 0) {
            int s = formAction + "action=\"".length();
            int e = page.indexOf('"', s);
            if (e > s) {
                String action = unescapeHtml(page.substring(s, e));
                StringBuilder url = new StringBuilder(action);
                boolean first = action.indexOf('?') < 0;
                int idx = 0;
                while (true) {
                    int ni = page.indexOf("name=\"", idx);
                    if (ni < 0) break;
                    int ns = ni + "name=\"".length();
                    int ne = page.indexOf('"', ns);
                    int vi = page.indexOf("value=\"", ne);
                    if (ne < 0 || vi < 0) break;
                    int vs = vi + "value=\"".length();
                    int ve = page.indexOf('"', vs);
                    if (ve < 0) break;
                    String name = page.substring(ns, ne);
                    String value = unescapeHtml(page.substring(vs, ve));
                    url.append(first ? '?' : '&').append(name).append('=').append(value);
                    first = false;
                    idx = ve + 1;
                }
                return url.toString();
            }
        }
        // Classic flow: find confirm=TOKEN anywhere on the page.
        int c = page.indexOf("confirm=");
        if (c >= 0) {
            int s = c + "confirm=".length();
            int e = s;
            while (e < page.length() && "&\"'".indexOf(page.charAt(e)) < 0) e++;
            String token = page.substring(s, e);
            return "https://drive.google.com/uc?export=download&confirm=" + token + "&id=" + fileId;
        }
        return null;
    }

    private static String unescapeHtml(String s) {
        return s.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&#x27;", "'");
    }
}
