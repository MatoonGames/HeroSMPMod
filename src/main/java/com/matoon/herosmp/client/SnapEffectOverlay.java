package com.matoon.herosmp.client;

import com.matoon.herosmp.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/**
 * Client-side white flash overlay triggered by the Infinity Gauntlet snap.
 *
 * Flash curve:
 *  - Ticks 0 → FADE_IN_TICKS  : alpha ramps from 0 to 1  (fast in, 0.2 s)
 *  - Ticks FADE_IN_TICKS → total : alpha ramps from 1 to 0  (slow out, 5 s)
 */
@SideOnly(Side.CLIENT)
public class SnapEffectOverlay {

    /** Fade-in duration: 0.2 s × 20 t/s = 4 ticks. */
    private static final int FADE_IN_TICKS  = 4;
    /** Fade-out duration: 5 s × 20 t/s = 100 ticks. */
    private static final int FADE_OUT_TICKS = 100;
    private static final int TOTAL_TICKS    = FADE_IN_TICKS + FADE_OUT_TICKS;

    private static boolean active     = false;
    private static int     tick       = 0;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public static void trigger(int soundIndex) {
        active = true;
        tick   = 0;

        SoundEvent[] sounds = { ModSounds.SNAP_1, ModSounds.SNAP_2, ModSounds.SNAP_3 };
        int idx = Math.max(0, Math.min(soundIndex, sounds.length - 1));
        Minecraft.getMinecraft().getSoundHandler().playSound(
            PositionedSoundRecord.getMasterRecord(sounds[idx], 1.0f));
    }

    // -------------------------------------------------------------------------
    // Tick (game-tick rate, not frame rate)
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!active) return;

        tick++;
        if (tick >= TOTAL_TICKS) {
            active = false;
            tick   = 0;
        }
    }

    // -------------------------------------------------------------------------
    // Render
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!active) return;

        float alpha;
        if (tick < FADE_IN_TICKS) {
            // Ramp up: 0 → 1
            alpha = tick / (float) FADE_IN_TICKS;
        } else {
            // Ramp down: 1 → 0
            int fadeOutTick = tick - FADE_IN_TICKS;
            alpha = 1.0f - (fadeOutTick / (float) FADE_OUT_TICKS);
        }
        alpha = Math.max(0f, Math.min(1f, alpha));
        if (alpha <= 0f) return;

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution res = new ScaledResolution(mc);
        int sw = res.getScaledWidth();
        int sh = res.getScaledHeight();

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1.0f, 1.0f, 1.0f, alpha);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        buf.pos(0,  sh, 0).endVertex();
        buf.pos(sw, sh, 0).endVertex();
        buf.pos(sw,  0, 0).endVertex();
        buf.pos(0,   0, 0).endVertex();
        tess.draw();

        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
