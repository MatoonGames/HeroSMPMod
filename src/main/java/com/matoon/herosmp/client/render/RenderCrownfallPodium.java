package com.matoon.herosmp.client.render;

import com.matoon.herosmp.tileentity.TileEntityCrownfallPodium;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;

/** Renders a Crownfall objective hovering above its physical podium. */
public class RenderCrownfallPodium extends TileEntitySpecialRenderer<TileEntityCrownfallPodium> {
    @Override
    public void render(TileEntityCrownfallPodium tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        ItemStack stack = tile.getDisplayStack();
        if (stack.isEmpty()) return;

        long time = tile.getWorld() == null ? 0L : tile.getWorld().getTotalWorldTime();
        float bob = (float) Math.sin((time + partialTicks) / 10.0F) * 0.06F;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D, y + 1.22D + bob, z + 0.5D);
        GlStateManager.rotate((time + partialTicks) * 2.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.scale(0.8F, 0.8F, 0.8F);
        GlStateManager.enableLighting();
        Minecraft.getMinecraft().getRenderItem().renderItem(stack, ItemCameraTransforms.TransformType.GROUND);
        GlStateManager.popMatrix();
    }
}
