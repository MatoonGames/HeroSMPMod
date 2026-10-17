package com.matoon.herosmp.client.gui.kitselect;

import com.matoon.herosmp.client.gui.bedrock.BedrockUI;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

/**
 * The fighting-game look shared by the fighter select and the round HUD: slanted cards
 * (parallelograms), italic outlined announcer text and ring gauges.
 */
public final class ArenaDraw {

    private ArenaDraw() {}

    private static final FloatBuffer SHEAR = BufferUtils.createFloatBuffer(16);

    /** Italic "announcer" text with a heavy black outline; {@code (x, y)} is its top-left (or top-centre). */
    public static void slantText(FontRenderer fontRenderer, String s, float x, float y, float scale, int color, boolean centered) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        SHEAR.clear();
        SHEAR.put(new float[]{1, 0, 0, 0, -0.22f, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1});
        SHEAR.flip();
        GlStateManager.multMatrix(SHEAR);
        GlStateManager.scale(scale, scale, 1);
        float w = fontRenderer.getStringWidth(s);
        float ox = centered ? -w / 2f : 0;
        int outline = BedrockUI.fade(0xFF000000, ((color >>> 24) & 0xFF) / 255f);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) fontRenderer.drawString(s, ox + dx * 0.75f, dy * 0.75f, outline, false);
            }
        }
        fontRenderer.drawString(s, ox + 1.2f, 1.2f, outline, false);
        fontRenderer.drawString(s, ox, 0, color, false);
        GlStateManager.popMatrix();
    }

    /** Parallelogram: the top edge is shifted right by {@code skew}. Vertical gradient. */
    public static void para(float x, float y, float w, float h, float skew, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        beginColor();
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        vtx(b, x + w + skew, y, top);
        vtx(b, x + skew, y, top);
        vtx(b, x, y + h, bottom);
        vtx(b, x + w, y + h, bottom);
        Tessellator.getInstance().draw();
        endColor();
    }

    /** Ring segment from 12 o'clock, clockwise, covering {@code frac} of the circle. */
    public static void arc(float cx, float cy, float r0, float r1, float frac, int color) {
        if (frac <= 0) return;
        beginColor();
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        int segs = Math.max(2, (int) (48 * Math.min(1f, frac)));
        for (int i = 0; i <= segs; i++) {
            double a = -Math.PI / 2 + Math.PI * 2 * Math.min(1f, frac) * i / segs;
            vtx(b, cx + Math.cos(a) * r1, cy + Math.sin(a) * r1, color);
            vtx(b, cx + Math.cos(a) * r0, cy + Math.sin(a) * r0, color);
        }
        Tessellator.getInstance().draw();
        endColor();
    }

    public static void beginColor() {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
    }

    public static void endColor() {
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    public static void vtx(BufferBuilder b, double x, double y, int argb) {
        b.pos(x, y, 0).color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF).endVertex();
    }
}
