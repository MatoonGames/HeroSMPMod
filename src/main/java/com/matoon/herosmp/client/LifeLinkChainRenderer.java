package com.matoon.herosmp.client;

import com.matoon.herosmp.client.LifeLinkClientMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.Map;
import java.util.UUID;

/**
 * Client-side renderer that draws the life_link_chain texture as a line
 * between each linked entity pair.
 *
 * The chain tiles the texture along the line from the linked entity's position
 * to the linker's position. Rendering uses quads in world space, always facing
 * the camera (billboard style).
 */
@SideOnly(Side.CLIENT)
public class LifeLinkChainRenderer {

    private static final ResourceLocation CHAIN_TEXTURE =
            new ResourceLocation("herosmp", "textures/items/life_link_chain.png");

    /** Width of the chain in world units. */
    private static final float CHAIN_WIDTH = 0.2f;

    /** How many texture repeats per block of chain length. */
    private static final float TEX_REPEAT_PER_BLOCK = 1.0f;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) return;

        Map<UUID, UUID> links = LifeLinkClientMap.getLinks();
        if (links.isEmpty()) return;

        float partialTicks = event.getPartialTicks();
        double cx = mc.player.lastTickPosX + (mc.player.posX - mc.player.lastTickPosX) * partialTicks;
        double cy = mc.player.lastTickPosY + (mc.player.posY - mc.player.lastTickPosY) * partialTicks;
        double cz = mc.player.lastTickPosZ + (mc.player.posZ - mc.player.lastTickPosZ) * partialTicks;

        GlStateManager.pushMatrix();
        GlStateManager.translate(-cx, -cy, -cz);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        mc.getTextureManager().bindTexture(CHAIN_TEXTURE);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        for (Map.Entry<UUID, UUID> entry : links.entrySet()) {
            UUID linkedUuid = entry.getKey();
            UUID linkerUuid = entry.getValue();

            EntityLivingBase linkedEntity  = findEntityByUUID(mc, linkedUuid);
            EntityLivingBase linkerEntity  = findEntityByUUID(mc, linkerUuid);
            if (linkedEntity == null || linkerEntity == null) continue;

            double lx1 = lerp(linkedEntity.lastTickPosX, linkedEntity.posX, partialTicks);
            double ly1 = lerp(linkedEntity.lastTickPosY, linkedEntity.posY, partialTicks) + linkedEntity.height * 0.5;
            double lz1 = lerp(linkedEntity.lastTickPosZ, linkedEntity.posZ, partialTicks);

            double lx2 = lerp(linkerEntity.lastTickPosX, linkerEntity.posX, partialTicks);
            double ly2 = lerp(linkerEntity.lastTickPosY, linkerEntity.posY, partialTicks) + linkerEntity.height * 0.5;
            double lz2 = lerp(linkerEntity.lastTickPosZ, linkerEntity.posZ, partialTicks);

            drawChain(buffer, tessellator, lx1, ly1, lz1, lx2, ly2, lz2, cx, cy, cz);
        }

        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private void drawChain(BufferBuilder buffer, Tessellator tessellator,
                           double x1, double y1, double z1,
                           double x2, double y2, double z2,
                           double camX, double camY, double camZ) {
        // Direction vector of the chain.
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.01) return;

        double ndx = dx / length;
        double ndy = dy / length;
        double ndz = dz / length;

        // Perpendicular vector: cross(chainDir, up) to get side offset.
        // Using camera-facing billboard: cross(chainDir, viewDir) for a ribbon.
        double viewX = camX - (x1 + x2) * 0.5;
        double viewY = camY - (y1 + y2) * 0.5;
        double viewZ = camZ - (z1 + z2) * 0.5;
        double viewLen = Math.sqrt(viewX * viewX + viewY * viewY + viewZ * viewZ);
        if (viewLen < 0.01) return;
        viewX /= viewLen;
        viewY /= viewLen;
        viewZ /= viewLen;

        // Cross product: chain direction × view direction = side axis.
        double sx = ndy * viewZ - ndz * viewY;
        double sy = ndz * viewX - ndx * viewZ;
        double sz = ndx * viewY - ndy * viewX;
        double sLen = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sLen < 0.001) return;
        sx = sx / sLen * CHAIN_WIDTH;
        sy = sy / sLen * CHAIN_WIDTH;
        sz = sz / sLen * CHAIN_WIDTH;

        float texV = (float) (length * TEX_REPEAT_PER_BLOCK);

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);

        // Two quads (crossing ribbon for thickness perception)
        renderQuad(buffer, x1, y1, z1, x2, y2, z2, sx, sy, sz, texV);
        // Second quad perpendicular to first for cross-hatch depth illusion.
        // Cross(side, chainDir) gives the second perpendicular axis.
        double s2x = sy * ndz - sz * ndy;
        double s2y = sz * ndx - sx * ndz;
        double s2z = sx * ndy - sy * ndx;
        double s2Len = Math.sqrt(s2x * s2x + s2y * s2y + s2z * s2z);
        if (s2Len > 0.001) {
            s2x = s2x / s2Len * CHAIN_WIDTH;
            s2y = s2y / s2Len * CHAIN_WIDTH;
            s2z = s2z / s2Len * CHAIN_WIDTH;
            renderQuad(buffer, x1, y1, z1, x2, y2, z2, s2x, s2y, s2z, texV);
        }

        tessellator.draw();
    }

    private void renderQuad(BufferBuilder buffer,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             double sx, double sy, double sz,
                             float texV) {
        buffer.pos(x1 - sx, y1 - sy, z1 - sz).tex(0, 0).color(255, 255, 255, 200).endVertex();
        buffer.pos(x1 + sx, y1 + sy, z1 + sz).tex(1, 0).color(255, 255, 255, 200).endVertex();
        buffer.pos(x2 + sx, y2 + sy, z2 + sz).tex(1, texV).color(255, 255, 255, 200).endVertex();
        buffer.pos(x2 - sx, y2 - sy, z2 - sz).tex(0, texV).color(255, 255, 255, 200).endVertex();
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }

    private EntityLivingBase findEntityByUUID(Minecraft mc, UUID uuid) {
        for (Entity e : mc.world.loadedEntityList) {
            if (e instanceof EntityLivingBase && e.getUniqueID().equals(uuid)) {
                return (EntityLivingBase) e;
            }
        }
        return null;
    }
}
