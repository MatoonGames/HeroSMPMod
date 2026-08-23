package com.matoon.herosmp.client.render;

import com.matoon.herosmp.tileentity.TileEntityCrownfallPodium;
import lucraft.mods.lucraftcore.infinity.items.ItemInfinityGauntlet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/** Renders a through-walls purple locator beam above the Crownfall gauntlet. */
public class CrownfallGauntletIndicatorRenderer {
    private static final String CROWNFALL_ITEM_TAG = "HeroSmpCrownfall";

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.getRenderViewEntity() == null) return;

        double cameraX = mc.getRenderManager().viewerPosX;
        double cameraY = mc.getRenderManager().viewerPosY;
        double cameraZ = mc.getRenderManager().viewerPosZ;
        float partial = event.getPartialTicks();

        beginIndicatorRender();
        for (TileEntity tile : mc.world.loadedTileEntityList) {
            if (tile instanceof TileEntityCrownfallPodium
                    && ((TileEntityCrownfallPodium) tile).isGauntlet()
                    && !((TileEntityCrownfallPodium) tile).getDisplayStack().isEmpty()) {
                renderBeam(tile.getPos().getX() + 0.5D - cameraX,
                        tile.getPos().getY() + 1.4D - cameraY,
                        tile.getPos().getZ() + 0.5D - cameraZ);
            }
        }
        for (Entity entity : mc.world.loadedEntityList) {
            if (!(entity instanceof EntityItem)) continue;
            ItemStack stack = ((EntityItem) entity).getItem();
            if (!isCrownfallGauntlet(stack)) continue;
            double x = entity.prevPosX + (entity.posX - entity.prevPosX) * partial;
            double y = entity.prevPosY + (entity.posY - entity.prevPosY) * partial;
            double z = entity.prevPosZ + (entity.posZ - entity.prevPosZ) * partial;
            renderBeam(x - cameraX, y + 0.4D - cameraY, z - cameraZ);
        }
        endIndicatorRender();
    }

    private static boolean isCrownfallGauntlet(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemInfinityGauntlet
                && stack.hasTagCompound() && stack.getTagCompound().getBoolean(CROWNFALL_ITEM_TAG);
    }

    private static void beginIndicatorRender() {
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
    }

    private static void renderBeam(double x, double y, double z) {
        double width = 0.07D;
        double top = y + 192.0D;
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        quad(buffer, x - width, y, z, x + width, top, z);
        quad(buffer, x, y, z - width, x, top, z + width);
        tessellator.draw();
    }

    private static void quad(BufferBuilder buffer, double x1, double y1, double z1,
                             double x2, double y2, double z2) {
        buffer.pos(x1, y1, z1).color(190, 55, 255, 100).endVertex();
        buffer.pos(x2, y1, z2).color(190, 55, 255, 100).endVertex();
        buffer.pos(x2, y2, z2).color(225, 155, 255, 20).endVertex();
        buffer.pos(x1, y2, z1).color(225, 155, 255, 20).endVertex();
    }

    private static void endIndicatorRender() {
        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }
}
