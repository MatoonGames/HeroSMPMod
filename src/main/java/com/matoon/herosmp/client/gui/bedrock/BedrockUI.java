package com.matoon.herosmp.client.gui.bedrock;

import com.matoon.herosmp.client.store.PackThumbnails;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Drawing toolkit that recreates the look of Minecraft Bedrock's "Ore UI" (Marketplace): flat dark
 * surfaces, hard black outlines, chunky buttons with a darker bottom lip that "press" down, and a
 * small fixed palette. Every store screen draws through this so the whole flow looks consistent.
 */
public final class BedrockUI {

    private BedrockUI() {}

    // ---------------------------------------------------------------------
    // Palette
    // ---------------------------------------------------------------------

    public static final int BG          = 0xFF1B1B1C;
    public static final int SURFACE     = 0xFF262728;
    public static final int PANEL       = 0xFF313233;
    public static final int PANEL_HOVER = 0xFF3B3C3E;
    public static final int PANEL_LIGHT = 0xFF48494A;
    public static final int OUTLINE     = 0xFF0C0C0D;
    public static final int DIVIDER     = 0xFF1E1E1F;

    public static final int TEXT        = 0xFFFFFFFF;
    public static final int TEXT_DIM    = 0xFFB1B2B5;
    public static final int TEXT_MUTED  = 0xFF7E7F82;
    public static final int TEXT_DARK   = 0xFF1E1E1F;

    public static final int GREEN       = 0xFF3C8527;
    public static final int GREEN_LIGHT = 0xFF6CC349;
    public static final int RED         = 0xFFD04343;
    public static final int GOLD        = 0xFFF2C230;
    public static final int BLUE        = 0xFF3D7BCC;

    /** Chunky button colour sets: face, hovered face, top highlight, bottom lip, label. */
    public enum Style {
        PRIMARY  (0xFF3C8527, 0xFF4C9D33, 0xFF5FB043, 0xFF1D4D13, 0xFFFFFFFF),
        SECONDARY(0xFFD0D1D4, 0xFFE3E4E6, 0xFFF2F2F3, 0xFF58585A, 0xFF1E1E1F),
        DARK     (0xFF48494A, 0xFF58595B, 0xFF636466, 0xFF1E1E1F, 0xFFFFFFFF),
        DANGER   (0xFFC33636, 0xFFD64545, 0xFFE06464, 0xFF6E1717, 0xFFFFFFFF),
        GOLD     (0xFFE9B320, 0xFFF5C437, 0xFFFFE27A, 0xFF7A5300, 0xFF3B2500),
        DISABLED (0xFF5A5B5D, 0xFF5A5B5D, 0xFF666769, 0xFF2E2F30, 0xFF9A9B9D);

        public final int face, hover, highlight, lip, text;

        Style(int face, int hover, int highlight, int lip, int text) {
            this.face = face;
            this.hover = hover;
            this.highlight = highlight;
            this.lip = lip;
            this.text = text;
        }
    }

    public static final int LIP = 2;

    private static Minecraft mc() { return Minecraft.getMinecraft(); }

    // ---------------------------------------------------------------------
    // Primitives
    // ---------------------------------------------------------------------

    public static void rect(int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        Gui.drawRect(x, y, x + w, y + h, color);
    }

    public static void outline(int x, int y, int w, int h, int t, int color) {
        rect(x, y, w, t, color);
        rect(x, y + h - t, w, t, color);
        rect(x, y + t, t, h - 2 * t, color);
        rect(x + w - t, y + t, t, h - 2 * t, color);
    }

    /** Four-corner gradient (ARGB per corner). */
    public static void gradient(int x, int y, int w, int h, int tl, int tr, int bl, int br) {
        if (w <= 0 || h <= 0) return;
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        vtx(b, x + w, y, tr);
        vtx(b, x, y, tl);
        vtx(b, x, y + h, bl);
        vtx(b, x + w, y + h, br);
        tess.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }

    public static void vGradient(int x, int y, int w, int h, int top, int bottom) {
        gradient(x, y, w, h, top, top, bottom, bottom);
    }

    public static void hGradient(int x, int y, int w, int h, int left, int right) {
        gradient(x, y, w, h, left, right, left, right);
    }

    private static void vtx(BufferBuilder b, double x, double y, int argb) {
        b.pos(x, y, 0).color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF).endVertex();
    }

    /** Float-precision filled rect (for zoomable canvases). */
    public static void rectF(float x1, float y1, float x2, float y2, int argb) {
        if (x2 <= x1 || y2 <= y1) return;
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        vtx(b, x2, y1, argb);
        vtx(b, x1, y1, argb);
        vtx(b, x1, y2, argb);
        vtx(b, x2, y2, argb);
        tess.draw();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    /** Float-precision rectangle outline of thickness {@code t}. */
    public static void outlineF(float x1, float y1, float x2, float y2, float t, int argb) {
        rectF(x1, y1, x2, y1 + t, argb);
        rectF(x1, y2 - t, x2, y2, argb);
        rectF(x1, y1 + t, x1 + t, y2 - t, argb);
        rectF(x2 - t, y1 + t, x2, y2 - t, argb);
    }

    /** Dashed rectangle outline (screen-space dash length). */
    public static void dashedOutlineF(float x1, float y1, float x2, float y2, float t, float dash, int argb) {
        for (float x = x1; x < x2; x += dash * 2) {
            rectF(x, y1, Math.min(x + dash, x2), y1 + t, argb);
            rectF(x, y2 - t, Math.min(x + dash, x2), y2, argb);
        }
        for (float y = y1; y < y2; y += dash * 2) {
            rectF(x1, y, x1 + t, Math.min(y + dash, y2), argb);
            rectF(x2 - t, y, x2, Math.min(y + dash, y2), argb);
        }
    }

    /** Draws the currently bound texture into a box with explicit UVs. */
    public static void texQuad(float x, float y, float w, float h, float u0, float v0, float u1, float v1) {
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        b.pos(x, y + h, 0).tex(u0, v1).endVertex();
        b.pos(x + w, y + h, 0).tex(u1, v1).endVertex();
        b.pos(x + w, y, 0).tex(u1, v0).endVertex();
        b.pos(x, y, 0).tex(u0, v0).endVertex();
        tess.draw();
    }

    /** Inventory-style item slot: recessed square, highlighted when hovered or selected. */
    public static void slot(int x, int y, int s, boolean selected, boolean hovered) {
        rect(x, y, s, s, selected ? GREEN_LIGHT : OUTLINE);
        rect(x + 1, y + 1, s - 2, s - 2, hovered ? 0xFF3A3B3D : 0xFF1E1F21);
        rect(x + 1, y + 1, s - 2, 1, 0xFF141415);
        if (selected) outline(x + 1, y + 1, s - 2, s - 2, 1, GREEN);
    }

    /**
     * Draws a pixel-art icon from text rows: '#' = outline colour, 'X' = fill colour, anything else
     * transparent. One character = one GUI pixel at the given scale.
     */
    public static void pixelIcon(String[] rows, float x, float y, float scale, int outline, int fill) {
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            for (int c = 0; c < row.length(); c++) {
                char ch = row.charAt(c);
                if (ch != '#' && ch != 'X') continue;
                float px = x + c * scale, py = y + r * scale;
                rectF(px, py, px + scale, py + scale, ch == '#' ? outline : fill);
            }
        }
    }

    /** Soft drop shadow under a panel. */
    public static void shadow(int x, int y, int w, int h) {
        rect(x + 2, y + h, w, 2, 0x50000000);
        rect(x + w, y + 2, 2, h, 0x50000000);
    }

    /** Filled circle (triangle fan). */
    public static void disc(float cx, float cy, float r, int argb) {
        if (r <= 0 || (argb >>> 24) == 0) return;
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        b.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        vtx(b, cx, cy, argb);
        int segs = Math.max(12, Math.min(48, (int) (r * 1.5f)));
        for (int i = segs; i >= 0; i--) {
            double a = Math.PI * 2 * i / segs;
            vtx(b, cx + Math.cos(a) * r, cy + Math.sin(a) * r, argb);
        }
        tess.draw();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    /**
     * Diagonal light band sweeping across a box: {@code t} 0..1 moves it from just off the left edge
     * to just off the right. Clipped to the box.
     */
    public static void sheen(int x, int y, int w, int h, float t, int alpha) {
        if (t <= 0 || t >= 1 || w <= 0 || h <= 0) return;
        float band = Math.max(10, Math.min(28, w * 0.18f));
        float skew = h * 0.6f;
        float cx = x - band - skew + (w + band * 2 + skew * 2) * t;
        int peak = (Math.max(0, Math.min(255, alpha)) << 24) | 0xFFFFFF;
        pushClip(x, y, w, h);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        // Left half: transparent -> peak, right half: peak -> transparent; top edge leans right.
        vtx(b, cx + skew, y, peak);
        vtx(b, cx - band + skew, y, 0x00FFFFFF);
        vtx(b, cx - band - skew, y + h, 0x00FFFFFF);
        vtx(b, cx - skew, y + h, peak);
        vtx(b, cx + band + skew, y, 0x00FFFFFF);
        vtx(b, cx + skew, y, peak);
        vtx(b, cx - skew, y + h, peak);
        vtx(b, cx + band - skew, y + h, 0x00FFFFFF);
        tess.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        popClip();
    }

    /** Soft glow around a box: a few stacked translucent outlines fading outwards. */
    public static void glow(int x, int y, int w, int h, int rgb, float strength) {
        if (strength <= 0.01f) return;
        for (int i = 1; i <= 4; i++) {
            int a = (int) (strength * (70 - i * 15));
            if (a <= 0) continue;
            outline(x - i, y - i, w + i * 2, h + i * 2, 1, (a << 24) | (rgb & 0xFFFFFF));
        }
    }

    /** Multiplies a colour's alpha by {@code a} (0..1). */
    public static int fade(int argb, float a) {
        int alpha = (int) (((argb >>> 24) & 0xFF) * Math.max(0, Math.min(1, a)));
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    public static float easeOut(float t) {
        t = Math.max(0, Math.min(1, t));
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    /** Ease-out with a small overshoot, for things that pop in. */
    public static float easeOutBack(float t) {
        t = Math.max(0, Math.min(1, t));
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    public static void clearDepth() {
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
    }

    // ---------------------------------------------------------------------
    // Chunky Bedrock button
    // ---------------------------------------------------------------------

    /**
     * Draws a Bedrock button: black outline, face, 1px top highlight, and a darker bottom lip. When
     * pressed the face sinks onto the lip.
     */
    public static void button(FontRenderer fr, int x, int y, int w, int h, String label,
                              Style style, boolean enabled, boolean hovered, boolean pressed) {
        Style s = enabled ? style : Style.DISABLED;
        boolean down = enabled && pressed;
        int face = enabled && hovered ? s.hover : s.face;

        rect(x, y, w, h, OUTLINE);
        int ix = x + 1, iw = w - 2, iy = y + 1, ih = h - 2;
        if (down) {
            rect(ix, iy + LIP, iw, ih - LIP, face);
            rect(ix, iy + LIP, iw, 1, s.highlight);
        } else {
            rect(ix, iy, iw, ih, s.lip);
            rect(ix, iy, iw, ih - LIP, face);
            rect(ix, iy, iw, 1, s.highlight);
        }

        if (label != null && !label.isEmpty()) {
            int faceTop = iy + (down ? LIP : 0);
            int faceH = ih - LIP;
            String text = fr.getStringWidth(label) > iw - 6 ? fr.trimStringToWidth(label, iw - 8) + "." : label;
            int tx = x + (w - fr.getStringWidth(text)) / 2;
            int ty = faceTop + (faceH - 8) / 2 + 1;
            fr.drawString(text, tx, ty, s.text, false);
        }
    }

    /** Small pixel-art left arrow, centred on (cx, cy). */
    public static void arrowLeft(int cx, int cy, int color) {
        for (int i = 0; i < 4; i++) rect(cx - 3 + i, cy - i, 1, 2 * i + 1, color);
        rect(cx + 1, cy - 1, 4, 3, color);
    }

    public static void arrowRight(int cx, int cy, int color) {
        for (int i = 0; i < 4; i++) rect(cx + 3 - i, cy - i, 1, 2 * i + 1, color);
        rect(cx - 4, cy - 1, 4, 3, color);
    }

    /** Pixel-art check mark, roughly 9x7, top-left at (x, y). */
    public static void check(int x, int y, int color) {
        rect(x, y + 3, 2, 2, color);
        rect(x + 1, y + 4, 2, 2, color);
        rect(x + 2, y + 5, 2, 2, color);
        for (int i = 0; i < 5; i++) rect(x + 4 + i, y + 4 - i, 2, 2, color);
    }

    // ---------------------------------------------------------------------
    // Chips / text
    // ---------------------------------------------------------------------

    /** Small rectangular tag. Returns its width. */
    public static int chip(FontRenderer fr, int x, int y, String text, int bg, int fg) {
        int w = fr.getStringWidth(text) + 8;
        rect(x, y, w, 12, OUTLINE);
        rect(x + 1, y + 1, w - 2, 10, bg);
        fr.drawString(text, x + 4, y + 2, fg, false);
        return w;
    }

    public static int chipWidth(FontRenderer fr, String text) {
        return fr.getStringWidth(text) + 8;
    }

    public static void text(FontRenderer fr, String s, float x, float y, int color, float scale, boolean shadow) {
        if (scale == 1f) {
            fr.drawString(s, x, y, color, shadow);
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(scale, scale, 1f);
        fr.drawString(s, 0, 0, color, shadow);
        GlStateManager.popMatrix();
    }

    public static String trim(FontRenderer fr, String s, int width) {
        if (s == null) return "";
        if (fr.getStringWidth(s) <= width) return s;
        return fr.trimStringToWidth(s, Math.max(0, width - fr.getStringWidth("..."))) + "...";
    }

    // ---------------------------------------------------------------------
    // Images / items
    // ---------------------------------------------------------------------

    /**
     * Draws a thumbnail scaled to cover the box (centre-cropped to the box's aspect). Small pixel-art
     * icons use nearest filtering so they stay crisp; real artwork uses linear filtering.
     */
    public static void imageCover(PackThumbnails.Thumb t, int x, int y, int w, int h, float alpha, boolean forceLinear) {
        if (t == null) return;
        textureCover(t.rl, t.w, t.h, x, y, w, h, alpha, forceLinear || t.w >= 160);
    }

    /** Draws a texture of known pixel size scaled to cover the box, centre-cropped. */
    public static void textureCover(net.minecraft.util.ResourceLocation rl, int texW, int texH,
                                    int x, int y, int w, int h, float alpha, boolean linear) {
        if (w <= 0 || h <= 0) return;
        mc().getTextureManager().bindTexture(rl);
        int filter = linear ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);

        float boxAspect = w / (float) h;
        float imgAspect = texW / (float) Math.max(1, texH);
        float u0 = 0, u1 = 1, v0 = 0, v1 = 1;
        if (imgAspect > boxAspect) {
            float span = boxAspect / imgAspect;
            u0 = (1 - span) / 2f;
            u1 = u0 + span;
        } else {
            float span = imgAspect / boxAspect;
            v0 = (1 - span) / 2f;
            v1 = v0 + span;
        }

        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.color(1f, 1f, 1f, alpha);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        b.pos(x, y + h, 0).tex(u0, v1).endVertex();
        b.pos(x + w, y + h, 0).tex(u1, v1).endVertex();
        b.pos(x + w, y, 0).tex(u1, v0).endVertex();
        b.pos(x, y, 0).tex(u0, v0).endVertex();
        tess.draw();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.disableBlend();
    }

    /** Pre-blurred copy of the disconnected-screen city art (see textures/gui/store_backdrop.png). */
    public static final net.minecraft.util.ResourceLocation BACKDROP =
            new net.minecraft.util.ResourceLocation("herosmp", "textures/gui/store_backdrop.png");
    public static final int BACKDROP_W = 448, BACKDROP_H = 256;

    /**
     * Full-screen backdrop that melts into the UI: the blurred city art at low opacity, washed with
     * the base colour, and dissolving fully into it at the top and bottom edges.
     */
    public static void backdrop(int width, int height) {
        rect(0, 0, width, height, BG);
        textureCover(BACKDROP, BACKDROP_W, BACKDROP_H, 0, 0, width, height, 0.34f, true);
        rect(0, 0, width, height, 0x381B1B1C);
        int band = height * 2 / 5;
        vGradient(0, 0, width, band, 0xD91B1B1C, 0x001B1B1C);
        vGradient(0, height - band, width, band, 0x001B1B1C, 0xF21B1B1C);
    }

    public static final net.minecraft.util.ResourceLocation LOGO =
            new net.minecraft.util.ResourceLocation("herosmp", "textures/gui/beyonder_logo.png");
    public static final int LOGO_W = 400, LOGO_H = 75;

    /** Draws the Beyonder wordmark at the given height, keeping its aspect. Returns the drawn width. */
    public static int logo(int x, int y, int h) {
        int w = Math.round(h * (LOGO_W / (float) LOGO_H));
        texture(LOGO, x, y, w, h);
        return w;
    }

    /** Draws a whole texture into the box with linear filtering (for downscaled artwork). */
    public static void texture(net.minecraft.util.ResourceLocation rl, int x, int y, int w, int h) {
        mc().getTextureManager().bindTexture(rl);
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.color(1f, 1f, 1f, 1f);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        b.pos(x, y + h, 0).tex(0, 1).endVertex();
        b.pos(x + w, y + h, 0).tex(1, 1).endVertex();
        b.pos(x + w, y, 0).tex(1, 0).endVertex();
        b.pos(x, y, 0).tex(0, 0).endVertex();
        tess.draw();
        GlStateManager.disableBlend();
    }

    /** Items whose renderer threw (e.g. needs a loaded world); drawn as a placeholder from then on. */
    private static final java.util.Set<net.minecraft.item.Item> BROKEN_ITEMS =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<net.minecraft.item.Item, Boolean>());

    /**
     * Renders an item icon at an arbitrary scale (16px * scale). Some modded items have custom
     * renderers that assume a world and player exist and throw at the main menu; those are caught,
     * the half-finished render state is unwound, and the item is drawn as a placeholder instead of
     * crashing the game.
     */
    public static void item(ItemStack stack, float x, float y, float scale) {
        if (stack.isEmpty() || BROKEN_ITEMS.contains(stack.getItem())) {
            if (!stack.isEmpty()) itemPlaceholder(x, y, scale);
            return;
        }
        int depth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(scale, scale, 1f);
        RenderHelper.enableGUIStandardItemLighting();
        try {
            mc().getRenderItem().renderItemAndEffectIntoGUI(stack, 0, 0);
        } catch (RuntimeException e) {
            BROKEN_ITEMS.add(stack.getItem());
            System.err.println("[Beyonder] Item " + stack.getItem().getRegistryName()
                    + " can't be drawn here (" + e.getClass().getSimpleName() + "); showing a placeholder.");
            recoverRenderState(depth);
            itemPlaceholder(x, y, scale);
            return;
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
        GlStateManager.disableLighting();
        GlStateManager.color(1f, 1f, 1f, 1f);
        // Items write depth; clear it so anything drawn afterwards stays on top (painter's order).
        clearDepth();
    }

    /**
     * Runs a render call for an item (held item, armor model...) that a mod might have broken. On
     * failure the item is remembered (skipped from then on), GL state is unwound, and false is
     * returned so the caller can draw something else.
     */
    public static boolean safely(net.minecraft.item.Item item, Runnable render) {
        if (item != null && BROKEN_ITEMS.contains(item)) return false;
        int depth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
        try {
            render.run();
            return true;
        } catch (RuntimeException e) {
            if (item != null) BROKEN_ITEMS.add(item);
            System.err.println("[Beyonder] Couldn't render " + (item != null ? item.getRegistryName() : "item")
                    + " here (" + e.getClass().getSimpleName() + ")");
            recoverRenderState(depth);
            return false;
        }
    }

    /** Undoes whatever a failed item render left behind: open vertex buffer, pushed matrices, lighting. */
    public static void recoverRenderState(int modelviewDepth) {
        try {
            Tessellator.getInstance().getBuffer().finishDrawing();
        } catch (IllegalStateException notDrawing) {
            // Buffer wasn't mid-draw; nothing to close.
        }
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        while (GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH) > modelviewDepth) GlStateManager.popMatrix();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableLighting();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
        clearDepth();
    }

    /** Neutral "?" tile used in place of an item that can't be rendered. */
    private static void itemPlaceholder(float x, float y, float scale) {
        float s = 16 * scale;
        rectF(x + 1, y + 1, x + s - 1, y + s - 1, 0xFF3A3B3D);
        rectF(x + 2, y + 2, x + s - 2, y + s - 2, 0xFF4E4F52);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + s / 2f, y + s / 2f, 0);
        GlStateManager.scale(scale, scale, 1f);
        mc().fontRenderer.drawString("?", -mc().fontRenderer.getStringWidth("?") / 2f + 0.5f, -3.5f, 0xFFB1B2B5, false);
        GlStateManager.popMatrix();
    }

    // ---------------------------------------------------------------------
    // Progress bar
    // ---------------------------------------------------------------------

    /**
     * Bedrock progress bar: black outline, dark track, green fill with highlight/shade edges and
     * moving diagonal stripes. {@code fraction < 0} draws an indeterminate sliding block.
     */
    public static void progressBar(int x, int y, int w, int h, float fraction) {
        rect(x, y, w, h, OUTLINE);
        rect(x + 1, y + 1, w - 2, h - 2, 0xFF1E1E1F);
        int iw = w - 2, ih = h - 2, ix = x + 1, iy = y + 1;

        int fx = ix, fw;
        if (fraction < 0) {
            fw = Math.max(20, iw / 4);
            double s = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 420.0);
            fx = ix + (int) ((iw - fw) * s);
        } else {
            fw = (int) (iw * Math.max(0f, Math.min(1f, fraction)));
        }
        if (fw <= 0) return;

        rect(fx, iy, fw, ih, GREEN);
        rect(fx, iy, fw, 2, 0xFF5FB043);
        rect(fx, iy + ih - 2, fw, 2, 0xFF2A6B1C);

        pushClip(fx, iy, fw, ih);
        int period = 14;
        int offset = (int) ((System.currentTimeMillis() / 40L) % period);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int sx = fx - ih - period + offset; sx < fx + fw + ih; sx += period) {
            vtx(b, sx + ih + 6, iy, 0x22FFFFFF);
            vtx(b, sx + ih, iy, 0x22FFFFFF);
            vtx(b, sx, iy + ih, 0x22FFFFFF);
            vtx(b, sx + 6, iy + ih, 0x22FFFFFF);
        }
        tess.draw();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        popClip();
    }

    // ---------------------------------------------------------------------
    // Loading visuals
    // ---------------------------------------------------------------------

    /** Skeleton placeholder block with a light band sweeping across it. */
    public static void skeleton(int x, int y, int w, int h) {
        rect(x, y, w, h, 0xFF2C2D2F);
        long t = System.currentTimeMillis() % 1600L;
        int band = 60;
        int bx = x - band + (int) ((w + 2 * band) * (t / 1600f));
        pushClip(x, y, w, h);
        hGradient(bx, y, band / 2, h, 0x00FFFFFF, 0x14FFFFFF);
        hGradient(bx + band / 2, y, band / 2, h, 0x14FFFFFF, 0x00FFFFFF);
        popClip();
    }

    /** Three pulsing squares, Bedrock-style loading indicator, centred on (cx, cy). */
    public static void loadingDots(int cx, int cy) {
        long t = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            double phase = (t / 220.0) - i * 0.9;
            int a = 90 + (int) (165 * (0.5 + 0.5 * Math.sin(phase)));
            rect(cx - 13 + i * 10, cy - 3, 6, 6, (a << 24) | 0xFFFFFF);
        }
    }

    // ---------------------------------------------------------------------
    // Nested scissor clipping (GUI coordinates)
    // ---------------------------------------------------------------------

    private static final Deque<int[]> CLIP = new ArrayDeque<int[]>();

    public static void pushClip(int x, int y, int w, int h) {
        int[] r = {x, y, x + w, y + h};
        int[] top = CLIP.peek();
        if (top != null) {
            r[0] = Math.max(r[0], top[0]);
            r[1] = Math.max(r[1], top[1]);
            r[2] = Math.min(r[2], top[2]);
            r[3] = Math.min(r[3], top[3]);
        }
        CLIP.push(r);
        apply(r);
    }

    public static void popClip() {
        if (!CLIP.isEmpty()) CLIP.pop();
        int[] top = CLIP.peek();
        if (top == null) GL11.glDisable(GL11.GL_SCISSOR_TEST);
        else apply(top);
    }

    /** Current clip rect as {x1, y1, x2, y2}, or null when unclipped. */
    public static int[] currentClip() {
        return CLIP.peek();
    }

    public static void resetClip() {
        CLIP.clear();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private static void apply(int[] r) {
        Minecraft mc = mc();
        int sf = new ScaledResolution(mc).getScaleFactor();
        int w = Math.max(0, r[2] - r[0]);
        int h = Math.max(0, r[3] - r[1]);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(r[0] * sf, mc.displayHeight - r[3] * sf, w * sf, h * sf);
    }
}
