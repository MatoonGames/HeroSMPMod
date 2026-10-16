package com.matoon.herosmp.client.gui;

import com.matoon.herosmp.client.gui.bedrock.BedrockUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * Main-menu entry button: a chunky Bedrock-style gold button (hard outline, gradient face,
 * highlight edge, darker lip that presses down) with a chest icon and a soft sheen that sweeps
 * across the face every few seconds. The sheen is faint and brief, so it catches the eye without
 * flashing.
 */
public class GoldSheenButton extends GuiButton {

    private static final long SWEEP_MS = 900L;   // duration of one pass
    private static final long CYCLE_MS = 4200L;  // pass + rest
    private static final ItemStack ICON = new ItemStack(Blocks.CHEST);

    // Hover state published for the main-menu logo transition (MainMenuLogoFx).
    private static volatile boolean lastHovered;
    private static volatile long lastDrawnAt;

    public GoldSheenButton(int buttonId, int x, int y, int widthIn, int heightIn, String text) {
        super(buttonId, x, y, widthIn, heightIn, text);
    }

    /** True while the Content Packs button is being drawn this frame and is hovered. */
    public static boolean isMainMenuButtonHovered() {
        return lastHovered && System.currentTimeMillis() - lastDrawnAt < 200L;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;
        this.hovered = mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width && mouseY < this.y + this.height;
        lastHovered = this.hovered;
        lastDrawnAt = System.currentTimeMillis();
        boolean down = this.hovered && Mouse.isButtonDown(0);
        int lip = BedrockUI.LIP;

        int x = this.x, y = this.y, w = this.width, h = this.height;
        int ix = x + 1, iw = w - 2;
        int faceY = y + 1 + (down ? lip : 0);
        int faceH = h - 2 - lip;

        // Outline, lip, face.
        BedrockUI.rect(x, y, w, h, BedrockUI.OUTLINE);
        if (!down) BedrockUI.rect(ix, y + 1, iw, h - 2, 0xFF7A5300);
        int top = this.hovered ? 0xFFFFE58A : 0xFFFFD45C;
        int bottom = this.hovered ? 0xFFF0B42A : 0xFFDB9E17;
        BedrockUI.vGradient(ix, faceY, iw, faceH, top, bottom);
        BedrockUI.rect(ix, faceY, iw, 1, 0xFFFFF4C4);
        BedrockUI.rect(ix, faceY + faceH - 1, iw, 1, 0xFFC08A10);

        drawSheen(ix, faceY, iw, faceH);

        // Icon + label.
        BedrockUI.item(ICON, ix + 3, faceY + (faceH - 16) / 2f, 1f);
        GlStateManager.disableDepth();
        int labelX = ix + 22 + (iw - 22 - mc.fontRenderer.getStringWidth(this.displayString)) / 2;
        int labelY = faceY + (faceH - 8) / 2 + 1;
        mc.fontRenderer.drawString(this.displayString, labelX, labelY + 1, 0x60FFF4C4, false);
        mc.fontRenderer.drawString(this.displayString, labelX, labelY, 0xFF3B2500, false);
        GlStateManager.enableDepth();
    }

    /** Soft diagonal highlight sweeping left to right, clipped to the face. */
    private void drawSheen(int fx, int fy, int fw, int fh) {
        long t = System.currentTimeMillis() % CYCLE_MS;
        if (t > SWEEP_MS) return;
        float p = t / (float) SWEEP_MS;
        p = p * p * (3 - 2 * p); // ease in-out
        float band = 14f, slant = 8f;
        float cx = fx - band - slant + p * (fw + 2 * (band + slant));
        int alpha = this.hovered ? 120 : 90;

        BedrockUI.pushClip(fx, fy, fw, fh);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        // Left half: clear edge to bright centre.
        b.pos(cx - band + slant, fy, 0).color(255, 255, 255, 0).endVertex();
        b.pos(cx - band - slant, fy + fh, 0).color(255, 255, 255, 0).endVertex();
        b.pos(cx - slant, fy + fh, 0).color(255, 255, 255, alpha).endVertex();
        b.pos(cx + slant, fy, 0).color(255, 255, 255, alpha).endVertex();
        // Right half: bright centre to clear edge.
        b.pos(cx + slant, fy, 0).color(255, 255, 255, alpha).endVertex();
        b.pos(cx - slant, fy + fh, 0).color(255, 255, 255, alpha).endVertex();
        b.pos(cx + band - slant, fy + fh, 0).color(255, 255, 255, 0).endVertex();
        b.pos(cx + band + slant, fy, 0).color(255, 255, 255, 0).endVertex();
        tess.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        BedrockUI.popClip();
    }
}
