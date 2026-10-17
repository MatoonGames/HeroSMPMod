package com.matoon.herosmp.client.gui;

import net.minecraft.client.Minecraft;

import java.awt.Desktop;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The operating system's own "Save As" window (Windows Explorer's on Windows), via AWT. It runs on
 * its own thread so the game keeps rendering while it's open; the answer is delivered back on the
 * client thread. Also opens Explorer with a saved file selected.
 */
public final class SaveFileDialog {

    private SaveFileDialog() {}

    private static File lastDir;

    /** Where the dialog starts: the last folder used, else the user's Downloads folder. */
    public static File startDir() {
        if (lastDir != null && lastDir.isDirectory()) return lastDir;
        File downloads = new File(System.getProperty("user.home"), "Downloads");
        return downloads.isDirectory() ? downloads : new File(System.getProperty("user.home"));
    }

    /** False when there's no desktop to show a window on (the caller should save somewhere fixed). */
    public static boolean available() {
        return !GraphicsEnvironment.isHeadless();
    }

    /**
     * Asks where to save. {@code onChosen} gets the file (with {@code extension} enforced), or null if
     * the user cancelled; it runs on the client thread.
     */
    public static void ask(final String title, final String suggestedName, final String extension,
                           final Consumer<File> onChosen) {
        Thread t = new Thread(() -> {
            File chosen = null;
            Frame owner = null;
            try {
                owner = new Frame();
                owner.setUndecorated(true);
                owner.setAlwaysOnTop(true); // keep the dialog in front of the game window
                FileDialog dialog = new FileDialog(owner, title, FileDialog.SAVE);
                dialog.setDirectory(startDir().getAbsolutePath());
                dialog.setFile(suggestedName + extension);
                dialog.setFilenameFilter((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(extension));
                dialog.setAlwaysOnTop(true);
                dialog.setVisible(true); // blocks this thread until the user answers
                if (dialog.getFile() != null && dialog.getDirectory() != null) {
                    String name = dialog.getFile();
                    if (!name.toLowerCase(Locale.ROOT).endsWith(extension)) name += extension;
                    chosen = new File(dialog.getDirectory(), name);
                    lastDir = chosen.getParentFile();
                }
            } catch (Throwable e) {
                System.err.println("[Beyonder] Save dialog unavailable: " + e);
                chosen = null;
            } finally {
                if (owner != null) owner.dispose();
            }
            final File result = chosen;
            Minecraft.getMinecraft().addScheduledTask(() -> onChosen.accept(result));
        }, "Beyonder save dialog");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Native "Open" window for picking one or more files with one of {@code extensions}.
     * {@code onChosen} gets the files (empty if cancelled) on the client thread.
     */
    public static void openFiles(final String title, final String[] extensions,
                                 final Consumer<java.util.List<File>> onChosen) {
        Thread t = new Thread(() -> {
            java.util.List<File> chosen = new java.util.ArrayList<File>();
            Frame owner = null;
            try {
                owner = new Frame();
                owner.setUndecorated(true);
                owner.setAlwaysOnTop(true);
                FileDialog dialog = new FileDialog(owner, title, FileDialog.LOAD);
                dialog.setDirectory(startDir().getAbsolutePath());
                dialog.setMultipleMode(true);
                dialog.setFilenameFilter((dir, name) -> hasExtension(name, extensions));
                // Windows ignores FilenameFilter; a wildcard file name filters the list there.
                StringBuilder pattern = new StringBuilder();
                for (String ext : extensions) pattern.append(pattern.length() > 0 ? ";" : "").append('*').append(ext);
                dialog.setFile(pattern.toString());
                dialog.setAlwaysOnTop(true);
                dialog.setVisible(true);
                for (File f : dialog.getFiles()) {
                    if (f.isFile() && hasExtension(f.getName(), extensions)) chosen.add(f);
                }
                if (!chosen.isEmpty()) lastDir = chosen.get(0).getParentFile();
            } catch (Throwable e) {
                System.err.println("[Beyonder] Open dialog unavailable: " + e);
            } finally {
                if (owner != null) owner.dispose();
            }
            Minecraft.getMinecraft().addScheduledTask(() -> onChosen.accept(chosen));
        }, "Beyonder open dialog");
        t.setDaemon(true);
        t.start();
    }

    /**
     * "Open" window that accepts a folder or a .zip (the native Windows picker can't select
     * folders, so this uses Swing's chooser with the system look). Null if cancelled.
     */
    public static void openFolderOrZip(final String title, final Consumer<File> onChosen) {
        Thread t = new Thread(() -> {
            final File[] result = new File[1];
            try {
                javax.swing.SwingUtilities.invokeAndWait(() -> {
                    javax.swing.JFrame owner = null;
                    try {
                        try {
                            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
                        } catch (Exception ignored) {
                            // Default look is fine.
                        }
                        owner = new javax.swing.JFrame();
                        owner.setUndecorated(true);
                        owner.setAlwaysOnTop(true);
                        owner.setLocationRelativeTo(null);
                        owner.setVisible(true);
                        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser(startDir());
                        chooser.setDialogTitle(title);
                        chooser.setFileSelectionMode(javax.swing.JFileChooser.FILES_AND_DIRECTORIES);
                        chooser.setAcceptAllFileFilterUsed(false);
                        chooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
                            @Override public boolean accept(File f) { return f.isDirectory() || hasExtension(f.getName(), new String[]{".zip"}); }
                            @Override public String getDescription() { return "World folders and .zip files"; }
                        });
                        chooser.setApproveButtonText("Upload");
                        if (chooser.showOpenDialog(owner) == javax.swing.JFileChooser.APPROVE_OPTION) {
                            result[0] = chooser.getSelectedFile();
                            if (result[0] != null) lastDir = result[0].getParentFile();
                        }
                    } finally {
                        if (owner != null) owner.dispose();
                    }
                });
            } catch (Throwable e) {
                System.err.println("[Beyonder] Folder dialog unavailable: " + e);
            }
            final File chosen = result[0];
            Minecraft.getMinecraft().addScheduledTask(() -> onChosen.accept(chosen));
        }, "Beyonder folder dialog");
        t.setDaemon(true);
        t.start();
    }

    private static boolean hasExtension(String name, String[] extensions) {
        String l = name.toLowerCase(Locale.ROOT);
        for (String ext : extensions) if (l.endsWith(ext)) return true;
        return false;
    }

    /** Opens File Explorer with the file selected (falls back to just opening its folder). */
    public static void reveal(File file) {
        try {
            File f = file.getCanonicalFile();
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("win")) {
                new ProcessBuilder("explorer.exe", "/select," + f.getPath()).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", "-R", f.getPath()).start();
            } else if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(f.getParentFile());
            }
        } catch (Exception e) {
            try {
                Desktop.getDesktop().open(file.getAbsoluteFile().getParentFile());
            } catch (Exception ignored) {
                System.err.println("[Beyonder] Couldn't open " + file + ": " + e.getMessage());
            }
        }
    }
}
