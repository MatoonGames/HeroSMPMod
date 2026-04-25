package com.matoon.herosmp.integration;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

/**
 * Renders a {@link EntityLucraftInjection} as a floating, spinning, bobbing item.
 *
 * The item rendered is the actual {@code lucraftcore:injection} ItemStack stored on
 * the entity ({@link EntityLucraftInjection#getInjectionStack()}).  Because it is a
 * real ItemStack with the correct "Injection" NBT tag, LucraftCore's own
 * {@link lucraft.mods.lucraftcore.utilities.items.ItemInjection.InjectionItemColor}
 * colour handler automatically tints tint-layer 1 with the superpower's capsule colour.
 * No custom colour baking is needed here.
 *
 * Animation:
 *   - Continuous bob:  sin-wave on the Y axis using {@code ticksExisted + partialTicks}.
 *   - Continuous spin: 3 degrees per tick around the Y axis.
 *   - On collection:   scale and alpha linearly shrink from 1 → 0 over
 *                      {@link EntityLucraftInjection#FADE_DURATION} ticks,
 *                      driven by the synced {@link EntityLucraftInjection#getFadeTicks()}
 *                      DataParameter (NOT raw ticksExisted which is not re-synced).
 */
@SideOnly(Side.CLIENT)
public class RenderLucraftInjection extends Render<EntityLucraftInjection> {

    public RenderLucraftInjection(RenderManager renderManager) {
        super(renderManager);
    }

    @Override
    public void doRender(EntityLucraftInjection entity, double x, double y, double z,
                         float entityYaw, float partialTicks) {

        ItemStack stack = entity.getInjectionStack();
        if (stack.isEmpty()) return;

        // Continuous idle animation — ticksExisted increments normally on the client
        // and is never reset, so it is safe for the bob/spin loop.
        float age  = entity.ticksExisted + partialTicks;
        float bob  = MathHelper.sin(age / 20.0F) * 0.18F;
        float spin = age * 3.0F % 360.0F;

        // Base render scale — 3× larger than a normal ground item.
        final float BASE_SCALE = 3.0F;

        // Fade progress from the synced DataParameter.
        float scale = BASE_SCALE;
        float alpha = 1.0F;
        if (entity.isCollected()) {
            float progress = Math.min(1.0F,
                    (entity.getFadeTicks() + partialTicks) / (float) EntityLucraftInjection.FADE_DURATION);
            scale = BASE_SCALE * (1.0F - progress);
            alpha = 1.0F - progress;
        }
        if (scale <= 0.01F) return;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y + bob + 0.5, z);
        GlStateManager.rotate(spin, 0.0F, 1.0F, 0.0F);
        GlStateManager.scale(scale, scale, scale);

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                                  GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);

        // Render the real lucraftcore:injection ItemStack.
        // ItemInjection.InjectionItemColor will automatically tint the liquid layer
        // with the registered superpower's capsule_color — no manual tinting needed.
        RenderHelper.enableStandardItemLighting();
        Minecraft.getMinecraft().getRenderItem().renderItem(stack, ItemCameraTransforms.TransformType.GROUND);
        RenderHelper.disableStandardItemLighting();

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();

        // Draw the name-tag if this player is within range.
        if (entity.isNameTagVisible() && !entity.isCollected()) {
            String label = TextFormatting.LIGHT_PURPLE + "" + TextFormatting.BOLD + entity.getPowerName();
            renderNameTag(entity, label, x, y, z, partialTicks);
        }
    }

    /**
     * Draws a floating name-tag above the entity — the same technique used by
     * {@code RenderLivingBase} but adapted for plain {@link Entity} subclasses.
     */
    private void renderNameTag(EntityLucraftInjection entity, String label,
                               double x, double y, double z, float partialTicks) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        if (fr == null) return;

        double dist = renderManager.renderViewEntity != null
                ? renderManager.renderViewEntity.getDistanceSq(entity) : 0;
        if (dist > 64 * 64) return; // don't render beyond 64 blocks

        float yOffset = 1.8F; // height above entity origin (item is ~0.5 tall + BASE_SCALE visual)

        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y + yOffset, (float) z);
        GlStateManager.glNormal3f(0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);

        final float nameScale = 0.025F;
        GlStateManager.scale(-nameScale, -nameScale, nameScale);

        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        int width = fr.getStringWidth(label);
        int halfW = width / 2;

        // Semi-transparent black background panel — use Gui.drawRect to avoid
        // opening a new Tessellator batch on every rendered frame.
        Gui.drawRect(-halfW - 1, -1, halfW + 1, fr.FONT_HEIGHT, 0x40000000);

        // Render the text (with depth test disabled so it's always visible through blocks).
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        fr.drawStringWithShadow(label, -halfW, 0, 0xFFFFFFFF);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);

        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    @Nullable
    @Override
    protected ResourceLocation getEntityTexture(EntityLucraftInjection entity) {
        return null;
    }
}
