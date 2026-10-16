package com.matoon.herosmp.client.gui.editor;

import com.matoon.herosmp.client.gui.bedrock.BedrockScreen;
import com.matoon.herosmp.client.gui.bedrock.BedrockTextField;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI.Style;
import com.matoon.herosmp.client.store.InstalledPacks;
import com.matoon.herosmp.hungergames.map.HungerGamesMapConfig;
import com.matoon.herosmp.hungergames.map.HungerGamesMapManager;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.util.List;

/**
 * Visual layout editor: a top-down render of the map's real terrain with the map center, world
 * border (and its later phases), play-area bounds, chests, spawn points and lobby spawn drawn on
 * top. Points are placed and dragged directly on the map and snap to the ground; the inspector on
 * the right edits the same values numerically.
 */
final class LayoutTab extends EditorTab {

    enum Tool { SELECT, SPAWN, LOBBY, CENTER }
    private enum Drag { NONE, PAN, SPAWN, LOBBY, CENTER, BORDER }

    private static final int PANEL_W = 176;
    private static final int TOOLBAR_H = 28;
    private static final int PIN = 11;
    private static final int GRAB = 6;

    private TopDownMap map;
    private float viewX, viewZ, zoom = 1f;
    private boolean fitted;
    private int cx, cy, cw, ch, panelX;

    private Tool tool = Tool.SELECT;
    private Drag drag = Drag.NONE;
    private int dragIndex = -1;
    private int selectedSpawn = -1;
    private float panMouseX, panMouseY, panViewX, panViewZ;

    private final BedrockScreen.SmoothScroll panelScroll = new BedrockScreen.SmoothScroll();
    private float panelContentH, panelViewH;
    private final BedrockTextField borderField;
    private final NumberStepper circleCount, circleRadius;
    private int circleN = 12, circleR = 30;

    LayoutTab(GuiMapEditor ed) {
        super(ed);
        borderField = BedrockTextField.number(ed.font(), 10, 30000, this::setBorder);
        circleCount = new NumberStepper(ed, 1, 64);
        circleRadius = new NumberStepper(ed, 3, 2000);
        if (session.lobbyMap) tool = Tool.LOBBY;
        int spawns = session.config.getRoundSpawns().size();
        if (spawns > 0) circleN = spawns;
        circleR = Math.max(3, Math.min(60, config().getWorldBorderStartRange() / 4));
    }

    private HungerGamesMapConfig config() {
        return session.config;
    }

    // ---------------------------------------------------------------------
    // Terrain
    // ---------------------------------------------------------------------

    private void ensureMap() {
        if (map != null) return;
        BlockPos c = initialFocus();
        int half = config().getWorldBorderStartRange() + 96;
        map = new TopDownMap(session.mapDir, c.getX(), c.getZ(), half);
        map.start();
        viewX = c.getX() + 0.5f;
        viewZ = c.getZ() + 0.5f;
        fitted = false;
    }

    private BlockPos initialFocus() {
        HungerGamesMapConfig c = config();
        if (c.getMapCenter() != null) return c.getMapCenter();
        if (c.getLobbySpawn() != null) return c.getLobbySpawn();
        BlockPos spawn = new HungerGamesMapManager(InstalledPacks.mapsDir()).readWorldSpawn(session.mapName);
        if (spawn != null) return spawn;
        return regionCentroid();
    }

    /** Middle of the saved region files, for maps without any known point. */
    private BlockPos regionCentroid() {
        File[] files = new File(session.mapDir, "region").listFiles((d, n) -> n.endsWith(".mca"));
        if (files == null || files.length == 0) return BlockPos.ORIGIN;
        long sx = 0, sz = 0;
        int n = 0;
        for (File f : files) {
            String[] parts = f.getName().split("\\.");
            if (parts.length != 4) continue;
            try {
                sx += Integer.parseInt(parts[1]);
                sz += Integer.parseInt(parts[2]);
                n++;
            } catch (NumberFormatException ignored) {
                // Skip odd file names.
            }
        }
        if (n == 0) return BlockPos.ORIGIN;
        return new BlockPos((int) (sx * 512 / n) + 256, 64, (int) (sz * 512 / n) + 256);
    }

    private void rerender() {
        if (map != null) map.dispose();
        map = null;
        ensureMap();
    }

    @Override
    void dispose() {
        if (map != null) map.dispose();
        map = null;
    }

    // ---------------------------------------------------------------------
    // Coordinates
    // ---------------------------------------------------------------------

    private float toScreenX(double wx) { return (float) (cx + cw / 2f + (wx - viewX) * zoom); }
    private float toScreenY(double wz) { return (float) (cy + ch / 2f + (wz - viewZ) * zoom); }
    private double toWorldX(float sx) { return viewX + (sx - cx - cw / 2f) / zoom; }
    private double toWorldZ(float sy) { return viewZ + (sy - cy - ch / 2f) / zoom; }

    private boolean inCanvas(int x, int y) {
        return x >= cx && y >= cy && x < cx + cw && y < cy + ch;
    }

    /** Where a player would stand on the visible surface of column (bx, bz). */
    private BlockPos snapStand(int bx, int bz, int fallbackY) {
        int s = map != null ? map.standY(bx, bz) : -1;
        return new BlockPos(bx, s >= 0 ? s : fallbackY, bz);
    }

    /** Map center stores the clicked block itself (configure-mode convention): one below standing height. */
    private BlockPos snapCenter(int bx, int bz) {
        int s = map != null ? map.standY(bx, bz) : -1;
        return new BlockPos(bx, s > 0 ? s - 1 : 64, bz);
    }

    private void fit() {
        HungerGamesMapConfig c = config();
        int r = c.getMapCenter() != null ? c.getWorldBorderStartRange() + 24 : (map != null ? map.size / 2 : 200);
        zoom = clampZoom(Math.min(cw, ch) / (2f * r));
        BlockPos f = c.getMapCenter() != null ? c.getMapCenter() : initialFocus();
        viewX = f.getX() + 0.5f;
        viewZ = f.getZ() + 0.5f;
    }

    private static float clampZoom(float z) {
        return Math.max(0.08f, Math.min(10f, z));
    }

    // ---------------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------------

    @Override
    void draw(int x, int y, int w, int h) {
        ensureMap();
        int pw = Math.min(PANEL_W, w / 2);
        panelX = x + w - pw;
        cx = x;
        cw = w - pw - 10;
        cy = y + TOOLBAR_H + 6;
        ch = h - TOOLBAR_H - 6;
        if (!fitted && cw > 0 && ch > 0) {
            fit();
            fitted = true;
        }

        drawToolbar(x, y, cw);
        drawCanvas();
        drawInspector(panelX, y, pw, h);
    }

    // 11x11 pixel icons: '#' outline, 'X' fill.
    private static final String[] ICON_SELECT = {
            "#..........", "##.........", "#X#........", "#XX#.......", "#XXX#......", "#XXXX#.....",
            "#XXXXX#....", "#XXX###....", "#X##X#.....", "##..#X#....", "....###...."};
    private static final String[] ICON_SPAWN = {
            "..#####....", ".#XXXXX#...", "#XX###XX#..", "#XX#.#XX#..", "#XX###XX#..", ".#XXXXX#...",
            "..#XXX#..#.", "...#X#..###", "....#....#.", "...........", "..........."};
    private static final String[] ICON_LOBBY = {
            "##.........", "#X#######..", "#XXXXXXXX#.", "#XXXXXXXX#.", "#XXXXXXXX#.", "#X#######..",
            "#X#........", "#X#........", "#X#........", "#X#........", "###........"};
    private static final String[] ICON_CENTER = {
            ".....#.....", ".....#.....", "...#####...", "..#.....#..", "..#..X..#..", "###.XXX.###",
            "..#..X..#..", "..#.....#..", "...#####...", ".....#.....", ".....#....."};
    private static final String[] ICON_FIT = {
            "###.....###", "#.........#", "#.........#", "...........", "....XXX....", "....XXX....",
            "....XXX....", "...........", "#.........#", "#.........#", "###.....###"};
    private static final String[] ICON_ZOOM_IN = {
            "..####.....", ".#....#....", "#..XX..#...", "#.XXXX.#...", "#..XX..#...", ".#....#....",
            "..#####....", "......##...", ".......##..", "........##.", "..........."};
    private static final String[] ICON_ZOOM_OUT = {
            "..####.....", ".#....#....", "#......#...", "#.XXXX.#...", "#......#...", ".#....#....",
            "..#####....", "......##...", ".......##..", "........##.", "..........."};

    /**
     * Editor toolbar: a recessed strip holding a "Tools" group (the active tool is highlighted green
     * and shows as pressed) and a "View" group (zoom out / zoom level / zoom in / fit).
     */
    private void drawToolbar(int x, int y, int w) {
        BedrockUI.rect(x, y, w, TOOLBAR_H, BedrockUI.OUTLINE);
        BedrockUI.rect(x + 1, y + 1, w - 2, TOOLBAR_H - 2, 0xFF1B1C1D);
        BedrockUI.rect(x + 1, y + 1, w - 2, 1, 0xFF141415);

        int bx = x + 6;
        ed.font().drawString("TOOLS", bx, y + 11, BedrockUI.TEXT_MUTED, false);
        bx += ed.font().getStringWidth("TOOLS") + 6;
        bx += toolButton(bx, y + 3, ICON_SELECT, "Select", "1", Tool.SELECT, "Select & move points, drag the border handle") + 2;
        if (!session.lobbyMap) {
            bx += toolButton(bx, y + 3, ICON_SPAWN, "Spawn", "2", Tool.SPAWN, "Click the map to add a spawn point") + 2;
        }
        bx += toolButton(bx, y + 3, ICON_LOBBY, "Lobby", "3", Tool.LOBBY, "Click the map to place the lobby spawn") + 2;
        if (!session.lobbyMap) {
            toolButton(bx, y + 3, ICON_CENTER, "Center", "4", Tool.CENTER, "Click the map to set the map center");
        }

        // View group, right-aligned.
        int vx = x + w - 6;
        vx -= 24;
        iconButton(vx, y + 3, ICON_FIT, "Fit map to view", this::fit);
        vx -= 26;
        iconButton(vx, y + 3, ICON_ZOOM_IN, "Zoom in", () -> zoomAround(cx + cw / 2f, cy + ch / 2f, 1.25f));
        String pct = Math.round(zoom * 100) + "%";
        int pw = Math.max(34, ed.font().getStringWidth(pct) + 8);
        vx -= pw + 2;
        BedrockUI.rect(vx, y + 5, pw, 18, 0xFF111213);
        ed.font().drawString(pct, vx + (pw - ed.font().getStringWidth(pct)) / 2, y + 10, BedrockUI.TEXT_DIM, false);
        vx -= 26;
        iconButton(vx, y + 3, ICON_ZOOM_OUT, "Zoom out", () -> zoomAround(cx + cw / 2f, cy + ch / 2f, 0.8f));
        vx -= 6 + ed.font().getStringWidth("VIEW");
        if (vx > bx + 60) ed.font().drawString("VIEW", vx, y + 11, BedrockUI.TEXT_MUTED, false);
    }

    private int toolButton(int x, int y, String[] icon, String label, String key, final Tool t, String help) {
        int bw = 11 + 6 + ed.font().getStringWidth(label) + 14;
        int bh = TOOLBAR_H - 6;
        boolean active = tool == t;
        boolean hov = ed.hover(x, y, bw, bh);
        BedrockUI.button(ed.font(), x, y, bw, bh, null, active ? Style.PRIMARY : Style.DARK, true, hov, active);
        int sink = active ? BedrockUI.LIP : 0;
        BedrockUI.pixelIcon(icon, x + 7, y + 4 + sink, 1f, active ? 0xFF14300C : 0xFF0C0C0D, 0xFFFFFFFF);
        ed.font().drawString(label, x + 7 + 11 + 6, y + 6 + sink, 0xFFFFFFFF, false);
        ed.hit(x, y, bw, bh, () -> tool = t);
        if (hov) ed.setTooltip(label + " §7(" + key + ")", "§7" + help);
        return bw;
    }

    private void iconButton(int x, int y, String[] icon, String help, Runnable action) {
        int s = TOOLBAR_H - 6;
        boolean hov = ed.hover(x, y, 24, s);
        boolean down = hov && org.lwjgl.input.Mouse.isButtonDown(0);
        BedrockUI.button(ed.font(), x, y, 24, s, null, Style.DARK, true, hov, down);
        BedrockUI.pixelIcon(icon, x + 7, y + 4 + (down ? BedrockUI.LIP : 0), 1f, 0xFF0C0C0D, 0xFFFFFFFF);
        ed.hit(x, y, 24, s, action);
        if (hov) ed.setTooltip(help);
    }

    private void drawCanvas() {
        BedrockUI.rect(cx, cy, cw, ch, BedrockUI.OUTLINE);
        BedrockUI.pushClip(cx + 1, cy + 1, cw - 2, ch - 2);
        BedrockUI.rect(cx + 1, cy + 1, cw - 2, ch - 2, 0xFF0D0E10);

        drawTiles();
        HungerGamesMapConfig c = config();
        BlockPos center = c.getMapCenter();
        int r = c.getWorldBorderStartRange();
        if (center != null && !session.lobbyMap) {
            drawPlayAreaShade(center, r);
            drawBorders(center, r);
        }
        drawChests();
        if (!session.lobbyMap) drawSpawns();
        drawLobby();
        if (center != null && !session.lobbyMap) drawCenter(center);
        if (center != null && !session.lobbyMap && tool == Tool.SELECT) drawBorderHandle(center, r);

        drawStatus();
        BedrockUI.popClip();
    }

    private void drawTiles() {
        Minecraft mc = Minecraft.getMinecraft();
        float tileScreen = TopDownMap.TILE * zoom;
        for (int tz = 0; tz < map.tiles; tz++) {
            for (int tx = 0; tx < map.tiles; tx++) {
                float sx = toScreenX(map.originX + tx * TopDownMap.TILE);
                float sy = toScreenY(map.originZ + tz * TopDownMap.TILE);
                if (sx > cx + cw || sy > cy + ch || sx + tileScreen < cx || sy + tileScreen < cy) continue;
                ResourceLocation rl = map.tileTexture(tx, tz);
                if (rl == null) {
                    BedrockUI.rectF(sx, sy, sx + tileScreen, sy + tileScreen, 0xFF17181B);
                    continue;
                }
                mc.getTextureManager().bindTexture(rl);
                BedrockUI.texQuad(sx, sy, tileScreen, tileScreen, 0, 0, 1, 1);
            }
        }
    }

    /** Darkens everything that gets wiped to air at match time (outside the kept chunk square). */
    private void drawPlayAreaShade(BlockPos center, int r) {
        int limit = (int) Math.ceil(r / 16.0) + 4;
        int ccx = center.getX() >> 4, ccz = center.getZ() >> 4;
        float x1 = toScreenX((ccx - limit) * 16), x2 = toScreenX((ccx + limit + 1) * 16);
        float z1 = toScreenY((ccz - limit) * 16), z2 = toScreenY((ccz + limit + 1) * 16);
        int shade = 0x99000000;
        BedrockUI.rectF(cx, cy, cx + cw, z1, shade);
        BedrockUI.rectF(cx, z2, cx + cw, cy + ch, shade);
        BedrockUI.rectF(cx, Math.max(z1, cy), x1, Math.min(z2, cy + ch), shade);
        BedrockUI.rectF(x2, Math.max(z1, cy), cx + cw, Math.min(z2, cy + ch), shade);
    }

    private void drawBorders(BlockPos center, int r) {
        // Phase 1 (full), phase 2 (half the radius), final (diameter 20).
        square(center, r, 0xFFFF5050, false);
        square(center, r / 2f, 0xFFFFA030, true);
        square(center, 10, 0xFFFFE070, true);
    }

    private void square(BlockPos center, float half, int color, boolean dashed) {
        float x1 = toScreenX(center.getX() - half), x2 = toScreenX(center.getX() + half);
        float z1 = toScreenY(center.getZ() - half), z2 = toScreenY(center.getZ() + half);
        if (x2 - x1 < 3) return;
        BedrockUI.outlineF(x1 - 1, z1 - 1, x2 + 1, z2 + 1, 3f, 0x55000000);
        if (dashed) BedrockUI.dashedOutlineF(x1, z1, x2, z2, 1f, 4f, color);
        else BedrockUI.outlineF(x1, z1, x2, z2, 1f, color);
    }

    private void drawChests() {
        if (zoom < 0.35f) return;
        float s = Math.max(1.5f, Math.min(4f, zoom * 0.8f));
        for (BlockPos p : map.chests()) {
            float sx = toScreenX(p.getX() + 0.5), sy = toScreenY(p.getZ() + 0.5);
            if (sx < cx - 4 || sy < cy - 4 || sx > cx + cw + 4 || sy > cy + ch + 4) continue;
            BedrockUI.rectF(sx - s - 0.5f, sy - s - 0.5f, sx + s + 0.5f, sy + s + 0.5f, 0xFF1A1206);
            BedrockUI.rectF(sx - s, sy - s, sx + s, sy + s, 0xFFC08A3E);
        }
    }

    private void drawSpawns() {
        List<BlockPos> spawns = config().getRoundSpawns();
        for (int i = 0; i < spawns.size(); i++) {
            BlockPos p = spawns.get(i);
            boolean sel = i == selectedSpawn;
            pin(p, String.valueOf(i + 1), sel ? 0xFF7FE35A : 0xFF3C8527, sel ? BedrockUI.TEXT_DARK : BedrockUI.TEXT,
                    "Spawn #" + (i + 1), sel);
        }
    }

    private void drawLobby() {
        BlockPos l = config().getLobbySpawn();
        if (l != null) pin(l, "L", BedrockUI.GOLD, BedrockUI.TEXT_DARK, "Lobby spawn", false);
    }

    private void pin(BlockPos p, String label, int fill, int textColor, String name, boolean selected) {
        float sx = toScreenX(p.getX() + 0.5), sy = toScreenY(p.getZ() + 0.5);
        int w = Math.max(PIN, ed.font().getStringWidth(label) + 5);
        int x = Math.round(sx - w / 2f), y = Math.round(sy - PIN / 2f);
        if (x > cx + cw || y > cy + ch || x + w < cx || y + PIN < cy) return;
        if (selected) BedrockUI.rect(x - 2, y - 2, w + 4, PIN + 4, 0xAAFFFFFF);
        BedrockUI.rect(x, y, w, PIN, BedrockUI.OUTLINE);
        BedrockUI.rect(x + 1, y + 1, w - 2, PIN - 2, fill);
        ed.font().drawString(label, x + (w - ed.font().getStringWidth(label)) / 2f + 0.5f, y + 2, textColor, false);
        if (ed.hover(x, y, w, PIN) && drag == Drag.NONE) {
            ed.setTooltip(name, "§7" + p.getX() + ", " + p.getY() + ", " + p.getZ());
        }
    }

    private void drawCenter(BlockPos c) {
        float sx = toScreenX(c.getX() + 0.5), sy = toScreenY(c.getZ() + 0.5);
        BedrockUI.rectF(sx - 7, sy - 1.5f, sx + 7, sy + 1.5f, BedrockUI.OUTLINE);
        BedrockUI.rectF(sx - 1.5f, sy - 7, sx + 1.5f, sy + 7, BedrockUI.OUTLINE);
        BedrockUI.rectF(sx - 6, sy - 0.5f, sx + 6, sy + 0.5f, 0xFFFFFFFF);
        BedrockUI.rectF(sx - 0.5f, sy - 6, sx + 0.5f, sy + 6, 0xFFFFFFFF);
        BedrockUI.rectF(sx - 2, sy - 2, sx + 2, sy + 2, 0xFFFF5050);
    }

    private void drawBorderHandle(BlockPos c, int r) {
        float hx = toScreenX(c.getX() + r), hy = toScreenY(c.getZ());
        boolean hov = Math.abs(ed.mouseX() - hx) <= GRAB && Math.abs(ed.mouseY() - hy) <= GRAB && drag == Drag.NONE;
        BedrockUI.rectF(hx - 4, hy - 4, hx + 4, hy + 4, BedrockUI.OUTLINE);
        BedrockUI.rectF(hx - 3, hy - 3, hx + 3, hy + 3, hov || drag == Drag.BORDER ? 0xFFFFFFFF : 0xFFFF5050);
        if (hov) ed.setTooltip("Border radius " + r, "§7Drag to resize");
    }

    private void drawStatus() {
        String text;
        if (map.error() != null) {
            text = map.error();
        } else if (!map.isFinished()) {
            text = "Rendering terrain " + Math.round(map.progress() * 100) + "%";
        } else if (inCanvas(ed.mouseX(), ed.mouseY())) {
            int bx = (int) Math.floor(toWorldX(ed.mouseX())), bz = (int) Math.floor(toWorldZ(ed.mouseY()));
            int sy = map.standY(bx, bz);
            text = "X " + bx + "  Z " + bz + (sy >= 0 ? "  ·  stand Y " + sy : "");
        } else {
            text = map.chests().size() + " chests  ·  drag to pan, scroll to zoom";
        }
        int w = ed.font().getStringWidth(text) + 10;
        BedrockUI.rect(cx + 4, cy + ch - 17, w, 13, 0xC0000000);
        ed.font().drawString(text, cx + 9, cy + ch - 14, map.error() != null ? 0xFFFF8080 : BedrockUI.TEXT_DIM, false);
        String hint = toolHint();
        if (hint != null) {
            int hw = ed.font().getStringWidth(hint) + 10;
            BedrockUI.rect(cx + 4, cy + 4, hw, 13, 0xC0000000);
            ed.font().drawString(hint, cx + 9, cy + 7, BedrockUI.TEXT, false);
        }
    }

    private String toolHint() {
        switch (tool) {
            case SPAWN: return "Click to add a spawn point";
            case LOBBY: return "Click to place the lobby spawn";
            case CENTER: return "Click to set the map center";
            default: return null;
        }
    }

    // ---------------------------------------------------------------------
    // Inspector
    // ---------------------------------------------------------------------

    private void drawInspector(int x, int y, int w, int h) {
        panel(x, y, w, h);
        panelViewH = h - 8;
        panelScroll.clamp(panelContentH - panelViewH);
        BedrockUI.pushClip(x + 1, y + 1, w - 2, h - 2);
        int ix = x + 8, iw = w - 16;
        int iy = y + 8 - (int) panelScroll.value();
        int start = iy;
        HungerGamesMapConfig c = config();

        if (!session.lobbyMap) {
            // Center.
            iy = heading(ed, ix, iy, iw, "Map Center");
            final BlockPos center = c.getMapCenter();
            if (center == null) {
                iy = note(ed, ix, iy, iw, "Not set. The border and play area are placed around the center; "
                        + "use the Center tool.", 0xFFE0A040) + 6;
            } else {
                ed.font().drawString(center.getX() + ", " + center.getY() + ", " + center.getZ(), ix, iy, BedrockUI.TEXT, false);
                iy += 12;
                ed.button(ix, iy, iw, 20, "Show on Map", Style.DARK, true, () -> { viewX = center.getX() + 0.5f; viewZ = center.getZ() + 0.5f; });
                iy += 26;
            }

            // Border.
            iy = heading(ed, ix, iy, iw, "World Border");
            final int r = c.getWorldBorderStartRange();
            ed.font().drawString("Radius", ix, iy + 5, BedrockUI.TEXT_DIM, false);
            borderField.draw(ed, ix + iw - 52, iy, 52, 18, r);
            ed.font().drawString("blocks", ix + iw - 52 - 6 - ed.font().getStringWidth("blocks"), iy + 5, BedrockUI.TEXT_MUTED, false);
            iy += 22;
            ed.slider(ix, iy, iw, radiusToSlider(r), t -> setBorder(sliderToRadius(t)));
            iy += 20;
            iy = note(ed, ix, iy, iw, "Starts " + (2 * r) + " x " + (2 * r) + " (red)", BedrockUI.TEXT_DIM);
            iy = note(ed, ix, iy, iw, "Phase 2 shrinks to " + r + " x " + r + " (orange)", BedrockUI.TEXT_DIM);
            iy = note(ed, ix, iy, iw, "Final ring 20 x 20 (yellow)", BedrockUI.TEXT_DIM);
            iy = note(ed, ix, iy, iw, "Shaded area is cleared at match start.", BedrockUI.TEXT_MUTED) + 8;
            ed.button(ix, iy, iw, 20, "Re-render Around Center", Style.DARK, center != null, this::rerender);
            iy += 28;
        }

        // Lobby.
        iy = heading(ed, ix, iy, iw, "Lobby Spawn");
        final BlockPos lobby = c.getLobbySpawn();
        if (lobby == null) {
            iy = note(ed, ix, iy, iw, session.lobbyMap ? "Not set. Use the Lobby tool."
                    : "Not set. Players wait at spawn #1 instead.", 0xFFE0A040) + 6;
        } else {
            ed.font().drawString(lobby.getX() + ", " + lobby.getY() + ", " + lobby.getZ(), ix, iy, BedrockUI.TEXT, false);
            iy += 12;
            int third = (iw - 8) / 3;
            ed.button(ix, iy, third, 20, "Show", Style.DARK, true, () -> { viewX = lobby.getX() + 0.5f; viewZ = lobby.getZ() + 0.5f; });
            ed.button(ix + third + 4, iy, (third - 2) / 2, 20, "+", Style.DARK, true, () -> nudgeLobby(1));
            ed.button(ix + third + 4 + (third - 2) / 2 + 2, iy, (third - 2) / 2, 20, "-", Style.DARK, true, () -> nudgeLobby(-1));
            if (ed.hover(ix + third + 4, iy, third, 20)) ed.setTooltip("Raise / lower by one block");
            ed.button(ix + 2 * third + 8, iy, iw - 2 * third - 8, 20, "Clear", Style.DANGER, true, () -> {
                config().setLobbySpawn(null);
                session.markDirty();
            });
            iy += 24;
            iy = note(ed, ix, iy, iw, "Glass is see-through on the map; for a glass-floored lobby, raise Y to match.",
                    BedrockUI.TEXT_MUTED) + 6;
        }

        if (!session.lobbyMap) iy = drawSpawnSection(ix, iy, iw);

        panelContentH = iy - start + 8;
        BedrockUI.popClip();
    }

    private int drawSpawnSection(int ix, int iy, int iw) {
        final List<BlockPos> spawns = config().getRoundSpawns();
        iy = heading(ed, ix, iy, iw, "Spawn Points (" + spawns.size() + ")");
        iy = note(ed, ix, iy, iw, "Players get spawns in this order. With more players than spawns, spawns are shared.",
                BedrockUI.TEXT_MUTED) + 6;

        iy += circleCount.draw(ed, ix, iy, iw, "Count", circleN, v -> circleN = v) + 4;
        iy += circleRadius.draw(ed, ix, iy, iw, "Radius", circleR, v -> circleR = v) + 4;
        ed.button(ix, iy, iw, 20, "Arrange in a Circle", Style.PRIMARY, config().getMapCenter() != null, () -> {
            if (spawns.isEmpty()) arrangeCircle();
            else ed.confirm("Replace spawn points?", "This replaces all " + spawns.size() + " spawns with "
                    + circleN + " spawns in a circle around the map center.", "Replace", Style.PRIMARY, this::arrangeCircle);
        });
        iy += 26;

        for (int i = 0; i < spawns.size(); i++) {
            final int idx = i;
            final BlockPos p = spawns.get(i);
            boolean sel = i == selectedSpawn;
            int rowH = 18;
            boolean hov = ed.hover(ix, iy, iw, rowH);
            BedrockUI.rect(ix, iy, iw, rowH, sel ? 0xFF2C3B27 : hov ? 0xFF323335 : 0x00000000);
            ed.hit(ix, iy, iw - 54, rowH, () -> {
                selectedSpawn = idx;
                viewX = p.getX() + 0.5f;
                viewZ = p.getZ() + 0.5f;
            });
            ed.font().drawString("#" + (i + 1), ix + 3, iy + 5, sel ? BedrockUI.GREEN_LIGHT : BedrockUI.TEXT, false);
            ed.font().drawString(BedrockUI.trim(ed.font(), p.getX() + ", " + p.getY() + ", " + p.getZ(), iw - 82),
                    ix + 24, iy + 5, BedrockUI.TEXT_DIM, false);
            int bx = ix + iw - 52;
            ed.button(bx, iy + 1, 16, 16, "+", Style.DARK, true, () -> nudgeY(idx, 1));
            ed.button(bx + 17, iy + 1, 16, 16, "-", Style.DARK, true, () -> nudgeY(idx, -1));
            ed.button(bx + 36, iy + 1, 16, 16, "x", Style.DANGER, true, () -> removeSpawn(idx));
            if (ed.hover(bx, iy + 1, 33, 16)) ed.setTooltip("Raise / lower by one block");
            iy += rowH + 1;
        }

        if (!spawns.isEmpty()) {
            iy += 6;
            ed.button(ix, iy, iw, 20, "Clear All Spawns", Style.DANGER, true, () -> ed.confirm("Clear all spawns?",
                    "Removes all " + spawns.size() + " spawn points from this map.", "Clear", Style.DANGER, () -> {
                        session.setSpawns(java.util.Collections.<BlockPos>emptyList());
                        selectedSpawn = -1;
                    }));
            iy += 20;
        }
        return iy;
    }

    // ---------------------------------------------------------------------
    // Edits
    // ---------------------------------------------------------------------

    private void arrangeCircle() {
        BlockPos c = config().getMapCenter();
        if (c == null) return;
        List<BlockPos> out = new java.util.ArrayList<BlockPos>();
        for (int i = 0; i < circleN; i++) {
            double a = -Math.PI / 2 + Math.PI * 2 * i / circleN;
            int bx = c.getX() + (int) Math.round(Math.cos(a) * circleR);
            int bz = c.getZ() + (int) Math.round(Math.sin(a) * circleR);
            out.add(snapStand(bx, bz, c.getY() + 1));
        }
        session.setSpawns(out);
        selectedSpawn = -1;
        ed.toast("Placed " + circleN + " spawns");
    }

    private void moveSpawn(int idx, BlockPos to) {
        List<BlockPos> spawns = config().getRoundSpawns();
        if (idx < 0 || idx >= spawns.size() || spawns.get(idx).equals(to)) return;
        spawns.set(idx, to);
        session.setSpawns(spawns);
    }

    private void nudgeY(int idx, int dy) {
        List<BlockPos> spawns = config().getRoundSpawns();
        if (idx < 0 || idx >= spawns.size()) return;
        BlockPos p = spawns.get(idx);
        moveSpawn(idx, new BlockPos(p.getX(), Math.max(0, Math.min(255, p.getY() + dy)), p.getZ()));
    }

    // Border slider is logarithmic: fine control for typical arenas (half the bar covers 10..~550),
    // while still reaching the 30000 maximum.
    private static final double BORDER_MIN = 10, BORDER_MAX = 30000;

    private static float radiusToSlider(int r) {
        return (float) (Math.log(Math.max(BORDER_MIN, r) / BORDER_MIN) / Math.log(BORDER_MAX / BORDER_MIN));
    }

    private static int sliderToRadius(double t) {
        double v = BORDER_MIN * Math.pow(BORDER_MAX / BORDER_MIN, t);
        int step = v < 200 ? 1 : v < 1000 ? 5 : v < 5000 ? 25 : 100;
        return (int) Math.max(BORDER_MIN, Math.min(BORDER_MAX, Math.round(v / step) * step));
    }

    private void setBorder(int r) {
        if (r == config().getWorldBorderStartRange()) return;
        config().setWorldBorderStartRange(r);
        session.markDirty();
    }

    private void nudgeLobby(int dy) {
        BlockPos l = config().getLobbySpawn();
        if (l == null) return;
        config().setLobbySpawn(new BlockPos(l.getX(), Math.max(0, Math.min(255, l.getY() + dy)), l.getZ()));
        session.markDirty();
    }

    private void removeSpawn(int idx) {
        List<BlockPos> spawns = config().getRoundSpawns();
        if (idx < 0 || idx >= spawns.size()) return;
        spawns.remove(idx);
        session.setSpawns(spawns);
        if (selectedSpawn == idx) selectedSpawn = -1;
        else if (selectedSpawn > idx) selectedSpawn--;
    }

    // ---------------------------------------------------------------------
    // Mouse / keys
    // ---------------------------------------------------------------------

    @Override
    void onMouseDown(int x, int y, int button) {
        if (!inCanvas(x, y)) return;
        if (button == 1 || button == 2) {
            startPan(x, y);
            return;
        }
        if (button != 0) return;
        int bx = (int) Math.floor(toWorldX(x)), bz = (int) Math.floor(toWorldZ(y));
        HungerGamesMapConfig c = config();

        switch (tool) {
            case SPAWN: {
                List<BlockPos> spawns = c.getRoundSpawns();
                spawns.add(snapStand(bx, bz, 64));
                session.setSpawns(spawns);
                selectedSpawn = spawns.size() - 1;
                drag = Drag.SPAWN;
                dragIndex = selectedSpawn;
                return;
            }
            case LOBBY:
                c.setLobbySpawn(snapStand(bx, bz, 64));
                session.markDirty();
                drag = Drag.LOBBY;
                return;
            case CENTER:
                c.setMapCenter(snapCenter(bx, bz));
                session.markDirty();
                drag = Drag.CENTER;
                return;
            default:
                break;
        }

        // Select tool: grab a pin, the border handle, or pan.
        if (!session.lobbyMap) {
            List<BlockPos> spawns = c.getRoundSpawns();
            for (int i = spawns.size() - 1; i >= 0; i--) {
                if (nearPin(spawns.get(i), x, y)) {
                    selectedSpawn = i;
                    drag = Drag.SPAWN;
                    dragIndex = i;
                    return;
                }
            }
        }
        if (c.getLobbySpawn() != null && nearPin(c.getLobbySpawn(), x, y)) {
            drag = Drag.LOBBY;
            return;
        }
        BlockPos center = c.getMapCenter();
        if (center != null && !session.lobbyMap) {
            if (nearPin(center, x, y)) {
                drag = Drag.CENTER;
                return;
            }
            float hx = toScreenX(center.getX() + c.getWorldBorderStartRange()), hy = toScreenY(center.getZ());
            if (Math.abs(x - hx) <= GRAB && Math.abs(y - hy) <= GRAB) {
                drag = Drag.BORDER;
                return;
            }
        }
        selectedSpawn = -1;
        startPan(x, y);
    }

    private boolean nearPin(BlockPos p, int x, int y) {
        float sx = toScreenX(p.getX() + 0.5), sy = toScreenY(p.getZ() + 0.5);
        return Math.abs(x - sx) <= PIN / 2f + 1 && Math.abs(y - sy) <= PIN / 2f + 1;
    }

    private void startPan(int x, int y) {
        drag = Drag.PAN;
        panMouseX = x;
        panMouseY = y;
        panViewX = viewX;
        panViewZ = viewZ;
    }

    @Override
    void onDrag(int x, int y, int button) {
        int bx = (int) Math.floor(toWorldX(x)), bz = (int) Math.floor(toWorldZ(y));
        HungerGamesMapConfig c = config();
        switch (drag) {
            case PAN:
                viewX = panViewX - (x - panMouseX) / zoom;
                viewZ = panViewZ - (y - panMouseY) / zoom;
                break;
            case SPAWN:
                moveSpawn(dragIndex, snapStand(bx, bz, 64));
                break;
            case LOBBY: {
                BlockPos to = snapStand(bx, bz, 64);
                if (!to.equals(c.getLobbySpawn())) {
                    c.setLobbySpawn(to);
                    session.markDirty();
                }
                break;
            }
            case CENTER: {
                BlockPos to = snapCenter(bx, bz);
                if (!to.equals(c.getMapCenter())) {
                    c.setMapCenter(to);
                    session.markDirty();
                }
                break;
            }
            case BORDER: {
                BlockPos center = c.getMapCenter();
                if (center == null) break;
                int r = Math.max(Math.abs(bx - center.getX()), Math.abs(bz - center.getZ()));
                r = Math.max(10, Math.min(30000, r));
                if (r != c.getWorldBorderStartRange()) {
                    c.setWorldBorderStartRange(r);
                    session.markDirty();
                }
                break;
            }
            default:
                break;
        }
    }

    @Override
    void onRelease(int x, int y, int button) {
        drag = Drag.NONE;
        dragIndex = -1;
    }

    @Override
    void onScroll(int direction) {
        int mx = ed.mouseX(), my = ed.mouseY();
        if (inCanvas(mx, my)) zoomAround(mx, my, direction > 0 ? 1.25f : 0.8f);
        else if (mx >= panelX) panelScroll.add(-direction * 30);
    }

    private void zoomAround(float sx, float sy, float factor) {
        double wx = toWorldX(sx), wz = toWorldZ(sy);
        zoom = clampZoom(zoom * factor);
        viewX = (float) (wx - (sx - cx - cw / 2f) / zoom);
        viewZ = (float) (wz - (sy - cy - ch / 2f) / zoom);
    }

    @Override
    void onKey(char c, int key) {
        if (selectedSpawn < 0) {
            if (key == Keyboard.KEY_1) tool = Tool.SELECT;
            else if (key == Keyboard.KEY_2 && !session.lobbyMap) tool = Tool.SPAWN;
            else if (key == Keyboard.KEY_3) tool = Tool.LOBBY;
            else if (key == Keyboard.KEY_4 && !session.lobbyMap) tool = Tool.CENTER;
            return;
        }
        List<BlockPos> spawns = config().getRoundSpawns();
        if (selectedSpawn >= spawns.size()) { selectedSpawn = -1; return; }
        BlockPos p = spawns.get(selectedSpawn);
        switch (key) {
            case Keyboard.KEY_DELETE:
            case Keyboard.KEY_BACK:
                removeSpawn(selectedSpawn);
                break;
            case Keyboard.KEY_LEFT:  moveSpawn(selectedSpawn, snapStand(p.getX() - 1, p.getZ(), p.getY())); break;
            case Keyboard.KEY_RIGHT: moveSpawn(selectedSpawn, snapStand(p.getX() + 1, p.getZ(), p.getY())); break;
            case Keyboard.KEY_UP:    moveSpawn(selectedSpawn, snapStand(p.getX(), p.getZ() - 1, p.getY())); break;
            case Keyboard.KEY_DOWN:  moveSpawn(selectedSpawn, snapStand(p.getX(), p.getZ() + 1, p.getY())); break;
            case Keyboard.KEY_PRIOR: nudgeY(selectedSpawn, 1); break;
            case Keyboard.KEY_NEXT:  nudgeY(selectedSpawn, -1); break;
            default: break;
        }
    }
}
