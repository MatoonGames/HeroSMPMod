package com.matoon.herosmp.mindstone;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import java.util.*;

/** A lightweight animated violet/cyan enchantment halo rendered only for controlled entities. */
@SideOnly(Side.CLIENT)
public class MindControlAuraRenderer {
    private static final Set<UUID> CONTROLLED=new HashSet<>();
    public static void set(UUID id,boolean enabled){if(enabled)CONTROLLED.add(id);else CONTROLLED.remove(id);}
    @SubscribeEvent public void render(RenderLivingEvent.Post<EntityLivingBase> event){
        EntityLivingBase entity=event.getEntity(); if(!CONTROLLED.contains(entity.getUniqueID()))return;
        double h=entity.height+.18, phase=(entity.ticksExisted+event.getPartialRenderTick())*.18;
        GlStateManager.pushMatrix(); GlStateManager.translate(event.getX(),event.getY(),event.getZ());
        GlStateManager.disableTexture2D(); GlStateManager.disableLighting(); GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE); GlStateManager.depthMask(false);
        Tessellator t=Tessellator.getInstance(); BufferBuilder b=t.getBuffer();
        // Batch all three rings into one draw call. GL_LINES prevents the separate rings
        // from being joined while avoiding three driver submissions per controlled entity.
        b.begin(GL11.GL_LINES,DefaultVertexFormats.POSITION_COLOR);
        for(int band=0;band<3;band++){double y=.22+band*(h-.35)/2,spin=phase+band*2.09;float red=band==1?.18F:.55F,green=.12F+band*.15F,blue=1F;for(int i=0;i<24;i++){for(int point=0;point<2;point++){int index=i+point;double a=spin+index*Math.PI*2/24,r=.48+Math.sin(phase*1.7+index)*.035;b.pos(Math.cos(a)*r,y+Math.sin(a*3+phase)*.055,Math.sin(a)*r).color(red,green,blue,.85F).endVertex();}}}t.draw();
        GlStateManager.depthMask(true); GlStateManager.disableBlend(); GlStateManager.enableTexture2D(); GlStateManager.enableLighting(); GlStateManager.popMatrix();
    }
}
