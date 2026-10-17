package com.matoon.herosmp.client.hud;

import com.matoon.herosmp.client.gui.bedrock.BedrockUI;
import com.matoon.herosmp.hud.RoundBar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.BossInfoClient;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Round progress HUD drawn in place of vanilla boss bars that carry a {@link RoundBar} tag (PVP
 * duels, FFA, training, Crownfall, Hunger Games). Deliberately quiet: one line of text (round
 * label, clock, short extra) over a slim bar in the mode's colour that eases to its value and turns
 * red in the last seconds. Other mods' boss bars are left alone.
 */
@SideOnly(Side.CLIENT)
public final class RoundBarHud {

    private static final int BAR_H = 3, HEIGHT = 16, GAP = 4;

    private static final class BarState {
        float shown = -1;
        long seenAt;
    }

    private final Map<UUID, BarState> states = new HashMap<UUID, BarState>();
    private long lastFrame;

    @SubscribeEvent
    public void onBossBar(RenderGameOverlayEvent.BossInfo event) {
        BossInfoClient info = event.getBossInfo();
        RoundBar.Parsed p = RoundBar.decode(info.getName().getFormattedText());
        if (p == null) return;
        event.setCanceled(true);
        event.setIncrement(HEIGHT + GAP);

        Minecraft mc = Minecraft.getMinecraft();
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        for (Iterator<BarState> it = states.values().iterator(); it.hasNext(); ) {
            if (now - it.next().seenAt > 3000) it.remove();
        }
        BarState st = states.get(info.getUniqueId());
        if (st == null) states.put(info.getUniqueId(), st = new BarState());
        st.seenAt = now;
        float pct = Math.max(0f, Math.min(1f, info.getPercent()));
        st.shown = st.shown < 0 ? pct : st.shown + (pct - st.shown) * Math.min(1f, dt * 6f);

        int sw = new ScaledResolution(mc).getScaledWidth();
        draw(mc.fontRenderer, p, st.shown, sw, event.getY() - 8, now);
    }

    private static int accent(RoundBar.Parsed p) {
        if (p.flag == RoundBar.Flag.CALM) return 0xFF6CC349;
        switch (p.kind) {
            case DUEL:      return 0xFFE5534B;
            case FFA:       return 0xFFFF9A4D;
            case TRAINING:  return 0xFF3FC1D3;
            case CROWNFALL: return 0xFFB57EDC;
            default:        return 0xFFF2C230;
        }
    }

    private static void draw(FontRenderer fr, RoundBar.Parsed p, float pct, int sw, int y, long now) {
        boolean urgent = p.flag == RoundBar.Flag.URGENT;
        float pulse = urgent ? 0.5f + 0.5f * (float) Math.sin(now / 160.0) : 0f;
        int color = urgent ? 0xFFFF4A4A : accent(p);

        // Text line: LABEL  clock  · extra
        String label = p.label.toUpperCase();
        String extra = p.extra.isEmpty() ? "" : "  ·  " + p.extra;
        int lw = fr.getStringWidth(label), vw = fr.getStringWidth(p.value);
        int gap = p.value.isEmpty() ? 0 : 6;
        int ew = fr.getStringWidth(extra);
        int w = Math.max(150, Math.min(220, (int) (sw * 0.28f)));
        if (lw + gap + vw + ew > w + 60) { extra = ""; ew = 0; } // too long: drop the extra
        int tx = (sw - (lw + gap + vw + ew)) / 2;
        fr.drawString(label, tx, y, 0xFFFFFFFF, true);
        fr.drawString(p.value, tx + lw + gap, y, urgent ? 0xFFFF6B6B
                : color, true);
        if (!extra.isEmpty()) fr.drawString(extra, tx + lw + gap + vw, y, 0xFFB8B9BE, true);

        // Slim bar.
        int bx = (sw - w) / 2, by = y + 11;
        BedrockUI.rect(bx - 1, by - 1, w + 2, BAR_H + 2, 0x90000000);
        int fill = Math.round(w * pct);
        if (fill > 0) {
            BedrockUI.rect(bx, by, fill, BAR_H, urgent ? BedrockUI.fade(color, 0.7f + 0.3f * pulse) : color);
            BedrockUI.rect(bx, by, fill, 1, 0x50FFFFFF);
        }
        GlStateManager.color(1f, 1f, 1f, 1f);
    }
}
