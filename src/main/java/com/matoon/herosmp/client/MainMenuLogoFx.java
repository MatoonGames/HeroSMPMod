package com.matoon.herosmp.client;

import com.matoon.herosmp.client.gui.GoldSheenButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * While the "Content Packs" button is hovered, the main menu's Minecraft logo burns away into the
 * Beyonder logo, and burns back when the hover ends.
 *
 * The vanilla title and "Java Edition" textures are swapped for a blank texture only while the
 * effect is active (through {@link HideableTexture}); this class then redraws both logos itself
 * as a grid of small cells. Each cell flips from Minecraft to Beyonder when the burn progress
 * passes its own threshold (a left-to-right ramp, 2D value noise, and a bottom-up bias), giving
 * an irregular, climbing fire front. Cells just ahead of it scorch and blacken; flames, tongues and
 * embers are drawn additively on top. When idle the vanilla menu renders completely untouched.
 */
@SideOnly(Side.CLIENT)
public class MainMenuLogoFx {

    private static final ResourceLocation MC_TITLE = new ResourceLocation("textures/gui/title/minecraft.png");
    private static final ResourceLocation MC_EDITION = new ResourceLocation("textures/gui/title/edition.png");
    private static final ResourceLocation BEYONDER = new ResourceLocation("herosmp", "textures/gui/beyonder_logo_large.png");

    // Vanilla layout (GuiMainMenu.drawScreen): title at x = width/2 - 137, y = 30, 274px visible;
    // "Java Edition" 98x14 at (+88, 67).
    private static final int LOGO_W = 274;
    private static final int LOGO_Y = 30, TITLE_H = 44;
    private static final int EDITION_X = 88, EDITION_Y = 67, EDITION_W = 98, EDITION_H = 14;
    private static final int AREA_TOP = LOGO_Y, AREA_BOTTOM = EDITION_Y + EDITION_H, AREA_H = AREA_BOTTOM - AREA_TOP;
    // Beyonder wordmark (800x150) fitted to the logo width, centred on the title + edition area.
    private static final int BEY_H = Math.round(LOGO_W * 150f / 800f);
    private static final int BEY_Y = AREA_TOP + (AREA_H - BEY_H) / 2;

    // Burn grid.
    private static final int STRIP = 2;
    private static final int COLS = LOGO_W / STRIP;
    private static final int ROWS = 6;
    private static final float CELL_H = AREA_H / (float) ROWS;

    private static final float DURATION_S = 0.95f;
    private static final float CHAR_ZONE = 0.11f;
    private static final float FIRE_BEHIND = 0.09f, FIRE_AHEAD = 0.045f;

    private final float[][] threshold = new float[COLS][ROWS];
    private final Random rand = new Random();
    private final List<Ember> embers = new ArrayList<Ember>();

    private HideableTexture titleTex, editionTex;
    private Field splashField;

    private float progress;     // 0 = Minecraft, 1 = Beyonder (linear in time)
    private long lastNanos;
    private float ambientSpawn;

    public MainMenuLogoFx() {
        // Smooth 2D value noise on a coarse lattice (knots every 5 columns / 2 rows).
        Random r = new Random(0xBE10DE5L);
        int kx = COLS / 5 + 2, ky = ROWS / 2 + 2;
        float[][] k = new float[kx][ky];
        for (int x = 0; x < kx; x++) for (int y = 0; y < ky; y++) k[x][y] = r.nextFloat();

        for (int i = 0; i < COLS; i++) {
            for (int j = 0; j < ROWS; j++) {
                float fx = i / 5f, fy = j / 2f;
                int ax = (int) fx, ay = (int) fy;
                float tx = ease(fx - ax), ty = ease(fy - ay);
                float top = k[ax][ay] + (k[ax + 1][ay] - k[ax][ay]) * tx;
                float bot = k[ax][ay + 1] + (k[ax + 1][ay + 1] - k[ax][ay + 1]) * tx;
                float noise = top + (bot - top) * ty;
                float ramp = i / (float) (COLS - 1);
                float rise = 1f - j / (float) (ROWS - 1);   // upper cells catch a little later
                threshold[i][j] = 0.74f * ramp + 0.16f * noise + 0.07f * rise + 0.01f;
            }
        }
    }

    // ---------------------------------------------------------------------
    // Events
    // ---------------------------------------------------------------------

    @SubscribeEvent
    public void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.getGui() instanceof GuiMainMenu)) return;
        installTextures();
        progress = 0;
        embers.clear();
        lastNanos = System.nanoTime();
    }

    @SubscribeEvent
    public void onDrawPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (!(event.getGui() instanceof GuiMainMenu)) return;
        boolean hide = progress > 0f;
        if (titleTex != null) titleTex.hidden = hide;
        if (editionTex != null) editionTex.hidden = hide;
    }

    @SubscribeEvent
    public void onDrawPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.getGui() instanceof GuiMainMenu)) return;
        GuiMainMenu gui = (GuiMainMenu) event.getGui();

        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastNanos) / 1.0e9f);
        lastNanos = now;

        float before = progress;
        float target = GoldSheenButton.isMainMenuButtonHovered() ? 1f : 0f;
        float step = dt / DURATION_S;
        progress = target > progress ? Math.min(target, progress + step) : Math.max(target, progress - step);

        // Vanilla textures are only hidden for this frame's own draw; restore them for anyone else.
        if (titleTex != null) titleTex.hidden = false;
        if (editionTex != null) editionTex.hidden = false;

        boolean logosVisible = progress > 0f || before > 0f;
        if (!logosVisible && embers.isEmpty()) return;

        float front = ease(progress) * 1.04f; // slight overshoot so every cell fully flips
        boolean moving = progress > 0f && progress < 1f;
        int x0 = gui.width / 2 - 137;

        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();

        if (logosVisible) {
            drawLogos(x0, front, moving);
            if (moving) {
                drawFire(x0, front, now);
                spawnFrontEmbers(x0, front, dt, progress != before);
            }
        }
        if (progress >= 1f) drawAmbient(x0, dt, now);
        updateAndDrawEmbers(dt);

        if (progress > 0f) redrawSplash(gui);

        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableDepth();
    }

    // ---------------------------------------------------------------------
    // Logos
    // ---------------------------------------------------------------------

    private void drawLogos(int x0, float front, boolean moving) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        GlStateManager.enableTexture2D();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Minecraft title: unburned cells, scorched just ahead of the front.
        if (titleTex != null) {
            GlStateManager.bindTexture(titleTex.real.getGlTextureId());
            b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
            for (int i = 0; i < COLS; i++) {
                for (int j = 0; j < ROWS; j++) {
                    if (front > threshold[i][j]) continue;
                    float y0 = Math.max(cellTop(j), LOGO_Y), y1 = Math.min(cellTop(j + 1), LOGO_Y + TITLE_H);
                    if (y1 <= y0) continue;
                    float[] c = charColor(threshold[i][j] - front);
                    int sx = i * STRIP;
                    // Slice A covers local x 0..154 (v 0..44); slice B covers 155..309 (v 45..89).
                    titleCell(b, x0, sx, y0, y1, 0, 0, c);
                    titleCell(b, x0, sx, y0, y1, 155, 45, c);
                }
            }
            tess.draw();
        }

        // "Java Edition" under the title.
        if (editionTex != null) {
            GlStateManager.bindTexture(editionTex.real.getGlTextureId());
            b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
            for (int i = 0; i < COLS; i++) {
                int a = Math.max(i * STRIP, EDITION_X), e = Math.min(i * STRIP + STRIP, EDITION_X + EDITION_W);
                if (e <= a) continue;
                for (int j = 0; j < ROWS; j++) {
                    if (front > threshold[i][j]) continue;
                    float y0 = Math.max(cellTop(j), EDITION_Y), y1 = Math.min(cellTop(j + 1), EDITION_Y + EDITION_H);
                    if (y1 <= y0) continue;
                    float[] c = charColor(threshold[i][j] - front);
                    quadTex(b, x0 + a, y0, x0 + e, y1,
                            (a - EDITION_X) / 128f, (y0 - EDITION_Y) / 16f,
                            (e - EDITION_X) / 128f, (y1 - EDITION_Y) / 16f, c);
                }
            }
            tess.draw();
        }

        // Beyonder wordmark: burned cells, glowing hot right behind the front.
        Minecraft.getMinecraft().getTextureManager().bindTexture(BEYONDER);
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        for (int i = 0; i < COLS; i++) {
            int sx = i * STRIP;
            float u0 = sx / (float) LOGO_W, u1 = (sx + STRIP) / (float) LOGO_W;
            for (int j = 0; j < ROWS; j++) {
                if (front <= threshold[i][j]) continue;
                float y0 = Math.max(cellTop(j), BEY_Y), y1 = Math.min(cellTop(j + 1), BEY_Y + BEY_H);
                if (y1 <= y0) continue;
                float heat = moving ? Math.max(0f, 1f - (front - threshold[i][j]) / 0.10f) : 0f;
                float[] c = {1f, 1f - heat * 0.22f, 1f - heat * 0.5f, 1f};
                quadTex(b, x0 + sx, y0, x0 + sx + STRIP, y1, u0, (y0 - BEY_Y) / BEY_H, u1, (y1 - BEY_Y) / BEY_H, c);
            }
        }
        tess.draw();
    }

    private static float cellTop(int j) {
        return AREA_TOP + j * CELL_H;
    }

    private static void titleCell(BufferBuilder b, int x0, int sx, float y0, float y1, int sliceStart, int v, float[] c) {
        int s = Math.max(sx, sliceStart), t = Math.min(sx + STRIP, sliceStart + 155);
        if (t <= s) return;
        float v0 = v + (y0 - LOGO_Y), v1 = v + (y1 - LOGO_Y);
        quadTex(b, x0 + s, y0, x0 + t, y1, (s - sliceStart) / 256f, v0 / 256f, (t - sliceStart) / 256f, v1 / 256f, c);
    }

    /** Tint for Minecraft cells just ahead of the fire: scorched orange, then soot right at the flame. */
    private static float[] charColor(float distAhead) {
        if (distAhead >= CHAR_ZONE) return new float[]{1f, 1f, 1f, 1f};
        float k = 1f - distAhead / CHAR_ZONE; // 0 far away, 1 touching the flame
        float soot = k > 0.65f ? (k - 0.65f) / 0.35f : 0f;
        float m = 1f - 0.7f * soot;
        return new float[]{(1f - 0.10f * k) * m, (1f - 0.60f * k) * m, (1f - 0.85f * k) * m, 1f};
    }

    // ---------------------------------------------------------------------
    // Fire
    // ---------------------------------------------------------------------

    /** Heat of a cell relative to the front: 1 on the line, fading behind and (less) ahead. */
    private static float heat(float d) {
        float h;
        if (d >= 0 && d < FIRE_BEHIND) h = 1f - d / FIRE_BEHIND;
        else if (d < 0 && d > -FIRE_AHEAD) h = 1f + d / FIRE_AHEAD;
        else return 0f;
        return h * h * (3 - 2 * h);
    }

    private void drawFire(int x0, float front, long now) {
        double time = now / 1.0e9;
        GlStateManager.disableTexture2D();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); // additive glow
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        for (int i = 0; i < COLS; i++) {
            float x1 = x0 + i * STRIP, x2 = x1 + STRIP;
            float columnHeat = 0f;
            int hottestRow = ROWS - 1;
            for (int j = 0; j < ROWS; j++) {
                float h = heat(front - threshold[i][j]);
                if (h <= 0f) continue;
                float flicker = 0.62f + 0.38f * (float) (0.5 + 0.5 * Math.sin(time * 19.0 + i * 1.3 + j * 2.1)
                        * Math.sin(time * 7.0 + i * 0.5));
                h *= flicker;
                if (h > columnHeat) { columnHeat = h; hottestRow = j; }
                // Cell glow: bright yellow core over an orange base.
                float y0 = cellTop(j), y1 = cellTop(j + 1);
                col(b, x2, y0, 255, 215, 110, (int) (190 * h));
                col(b, x1, y0, 255, 215, 110, (int) (190 * h));
                col(b, x1, y1, 255, 95, 15, (int) (210 * h));
                col(b, x2, y1, 255, 95, 15, (int) (210 * h));
            }
            if (columnHeat <= 0f) continue;
            // Flame tongue rising from the hottest cell, licking above the logo.
            float from = cellTop(hottestRow) + CELL_H * 0.5f;
            float to = LOGO_Y - 6f - 20f * columnHeat * (0.7f + 0.3f * (float) Math.sin(time * 11.0 + i * 0.9));
            col(b, x2 + 0.5f, to, 255, 140, 30, 0);
            col(b, x1 - 0.5f, to, 255, 140, 30, 0);
            col(b, x1 - 0.5f, from, 255, 200, 80, (int) (150 * columnHeat));
            col(b, x2 + 0.5f, from, 255, 200, 80, (int) (150 * columnHeat));
        }
        tess.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();
    }

    /** Warm, slowly breathing under-glow and a light ember drift while fully Beyonder. */
    private void drawAmbient(int x0, float dt, long now) {
        double time = now / 1.0e9;
        float pulse = 0.75f + 0.25f * (float) Math.sin(time * 2.2);
        GlStateManager.disableTexture2D();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        col(b, x0 + LOGO_W, AREA_BOTTOM - 14, 255, 110, 20, 0);
        col(b, x0, AREA_BOTTOM - 14, 255, 110, 20, 0);
        col(b, x0, AREA_BOTTOM + 2, 255, 110, 20, (int) (70 * pulse));
        col(b, x0 + LOGO_W, AREA_BOTTOM + 2, 255, 110, 20, (int) (70 * pulse));
        tess.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();

        ambientSpawn += dt * 7f;
        while (ambientSpawn >= 1f) {
            ambientSpawn -= 1f;
            embers.add(new Ember(x0 + rand.nextFloat() * LOGO_W, AREA_BOTTOM - rand.nextFloat() * 6f,
                    (rand.nextFloat() - 0.5f) * 6f, -12f - rand.nextFloat() * 16f,
                    0.9f + rand.nextFloat() * 0.9f, 0.8f + rand.nextFloat() * 0.6f));
        }
    }

    // ---------------------------------------------------------------------
    // Embers
    // ---------------------------------------------------------------------

    private void spawnFrontEmbers(int x0, float front, float dt, boolean moving) {
        if (!moving) return;
        List<int[]> hot = new ArrayList<int[]>();
        for (int i = 0; i < COLS; i++) {
            for (int j = 0; j < ROWS; j++) {
                if (Math.abs(front - threshold[i][j]) < 0.03f) hot.add(new int[]{i, j});
            }
        }
        if (hot.isEmpty()) return;
        int count = Math.max(1, Math.round(dt * 120f));
        for (int n = 0; n < count; n++) {
            int[] c = hot.get(rand.nextInt(hot.size()));
            float x = x0 + c[0] * STRIP + rand.nextFloat() * STRIP;
            float y = cellTop(c[1]) + rand.nextFloat() * CELL_H;
            embers.add(new Ember(x, y, (rand.nextFloat() - 0.5f) * 24f, -22f - rand.nextFloat() * 36f,
                    0.45f + rand.nextFloat() * 0.75f, 0.9f + rand.nextFloat() * 0.9f));
        }
    }

    private void updateAndDrawEmbers(float dt) {
        if (embers.isEmpty()) return;
        GlStateManager.disableTexture2D();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (Iterator<Ember> it = embers.iterator(); it.hasNext(); ) {
            Ember e = it.next();
            e.age += dt;
            if (e.age >= e.life) { it.remove(); continue; }
            e.vy -= 10f * dt;                                        // heat rises
            e.vx += MathHelper.sin(e.age * 9f + e.seed) * 20f * dt;  // flutter
            e.x += e.vx * dt;
            e.y += e.vy * dt;

            float t = e.age / e.life;
            int g = (int) (t < 0.4f ? 230 - t / 0.4f * 110 : 120 - (t - 0.4f) / 0.6f * 90);
            int bl = (int) (t < 0.3f ? 120 - t / 0.3f * 100 : 20);
            int a = (int) (255 * Math.pow(1 - t, 1.4));
            float s = e.size * (1 - 0.4f * t);
            col(b, e.x + s, e.y - s, 255, g, bl, a);
            col(b, e.x - s, e.y - s, 255, g, bl, a);
            col(b, e.x - s, e.y + s, 255, g, bl, a);
            col(b, e.x + s, e.y + s, 255, g, bl, a);
        }
        tess.draw();
        GlStateManager.enableTexture2D();
    }

    private static final class Ember {
        float x, y, vx, vy, age;
        final float life, size, seed;
        Ember(float x, float y, float vx, float vy, float life, float size) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.life = life; this.size = size; this.seed = x * 0.37f;
        }
    }

    // ---------------------------------------------------------------------
    // Splash text (redrawn so it stays on top of our logo, exactly as vanilla draws it)
    // ---------------------------------------------------------------------

    private void redrawSplash(GuiMainMenu gui) {
        try {
            if (splashField == null) splashField = ReflectionHelper.findField(GuiMainMenu.class, "splashText", "field_73975_c");
            String splash = (String) splashField.get(gui);
            if (splash == null || splash.isEmpty()) return;
            Minecraft mc = Minecraft.getMinecraft();
            GlStateManager.enableTexture2D();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.pushMatrix();
            GlStateManager.translate(gui.width / 2f + 90f, 70f, 0f);
            GlStateManager.rotate(-20f, 0f, 0f, 1f);
            float f = 1.8f - MathHelper.abs(MathHelper.sin((Minecraft.getSystemTime() % 1000L) / 1000f * ((float) Math.PI * 2f)) * 0.1f);
            f = f * 100f / (mc.fontRenderer.getStringWidth(splash) + 32);
            GlStateManager.scale(f, f, f);
            gui.drawCenteredString(mc.fontRenderer, splash, 0, -8, -256);
            GlStateManager.popMatrix();
        } catch (Exception ignored) {
            // The splash is cosmetic; never break the menu over it.
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static float ease(float t) {
        return t * t * (3 - 2 * t);
    }

    private static void quadTex(BufferBuilder b, float x1, float y1, float x2, float y2,
                                float u0, float v0, float u1, float v1, float[] c) {
        b.pos(x1, y2, 0).tex(u0, v1).color(c[0], c[1], c[2], c[3]).endVertex();
        b.pos(x2, y2, 0).tex(u1, v1).color(c[0], c[1], c[2], c[3]).endVertex();
        b.pos(x2, y1, 0).tex(u1, v0).color(c[0], c[1], c[2], c[3]).endVertex();
        b.pos(x1, y1, 0).tex(u0, v0).color(c[0], c[1], c[2], c[3]).endVertex();
    }

    private static void col(BufferBuilder b, float x, float y, int r, int g, int bl, int a) {
        b.pos(x, y, 0).color(r, Math.max(0, Math.min(255, g)), Math.max(0, Math.min(255, bl)),
                Math.max(0, Math.min(255, a))).endVertex();
    }

    // ---------------------------------------------------------------------
    // Hiding the vanilla title while the effect runs
    // ---------------------------------------------------------------------

    private void installTextures() {
        TextureManager tm = Minecraft.getMinecraft().getTextureManager();
        titleTex = wrap(tm, MC_TITLE);
        editionTex = wrap(tm, MC_EDITION);
    }

    private static HideableTexture wrap(TextureManager tm, ResourceLocation rl) {
        ITextureObject current = tm.getTexture(rl);
        if (current instanceof HideableTexture) return (HideableTexture) current;
        HideableTexture h = new HideableTexture(current != null ? current : new SimpleTexture(rl));
        tm.loadTexture(rl, h);
        return h;
    }

    /**
     * Wraps a vanilla texture so it can be swapped for a transparent 1x1 texture on demand. All
     * loading/reloading is delegated, so resource-pack changes keep working normally.
     */
    private static final class HideableTexture implements ITextureObject {
        private static DynamicTexture blank;

        final ITextureObject real;
        boolean hidden;

        HideableTexture(ITextureObject real) { this.real = real; }

        @Override public void setBlurMipmap(boolean blur, boolean mipmap) { real.setBlurMipmap(blur, mipmap); }
        @Override public void restoreLastBlurMipmap() { real.restoreLastBlurMipmap(); }
        @Override public void loadTexture(IResourceManager rm) throws IOException { real.loadTexture(rm); }

        @Override
        public int getGlTextureId() {
            if (!hidden) return real.getGlTextureId();
            if (blank == null) {
                blank = new DynamicTexture(1, 1); // zero-initialised: fully transparent
                blank.updateDynamicTexture();
            }
            return blank.getGlTextureId();
        }
    }
}
