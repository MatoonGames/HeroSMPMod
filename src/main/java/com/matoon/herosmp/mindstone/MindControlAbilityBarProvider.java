package com.matoon.herosmp.mindstone;

import com.matoon.herosmp.network.ModNetwork;
import com.matoon.herosmp.network.PacketMindControlEntries;
import com.matoon.herosmp.network.PacketOpenMindControlMenuRequest;
import lucraft.mods.lucraftcore.util.abilitybar.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import java.nio.ByteBuffer;
import java.util.*;

/** One cached head texture per companion; no entity model is rendered during normal HUD frames. */
@SideOnly(Side.CLIENT)
public class MindControlAbilityBarProvider implements IAbilityBarProvider {
    private final Map<UUID,CompanionEntry> cached=new HashMap<>(); private List<IAbilityBarEntry> displayed=Collections.emptyList(); private int revision=-1;
    @Override public List<IAbilityBarEntry> getEntries(){
        if(revision==MindControlClientState.revision())return displayed;revision=MindControlClientState.revision();Set<UUID> keep=new HashSet<>();List<IAbilityBarEntry> next=new ArrayList<>();
        for(PacketMindControlEntries.Entry e:MindControlClientState.get()){keep.add(e.id);CompanionEntry entry=cached.get(e.id);if(entry==null||!entry.matches(e)){if(entry!=null)entry.dispose();entry=new CompanionEntry(e);cached.put(e.id,entry);}next.add(entry);}
        Iterator<Map.Entry<UUID,CompanionEntry>> it=cached.entrySet().iterator();while(it.hasNext()){Map.Entry<UUID,CompanionEntry> e=it.next();if(!keep.contains(e.getKey())){e.getValue().dispose();it.remove();}}
        displayed=Collections.unmodifiableList(next);return displayed;
    }
    private static class CompanionEntry implements IAbilityBarEntry {
        final PacketMindControlEntries.Entry entry; Framebuffer mobHead; int nextCaptureAttempt,headCropLeft,headCropTop,headCropSize=32;
        CompanionEntry(PacketMindControlEntries.Entry e){entry=e;}
        boolean matches(PacketMindControlEntries.Entry e){return entry.entityId==e.entityId&&entry.player==e.player&&entry.name.equals(e.name);}
        void dispose(){if(mobHead!=null){mobHead.deleteFramebuffer();mobHead=null;}}
        @Override public boolean isActive(){return MindControlClientState.contains(entry.id);}
        @Override public void onButtonPress(){ModNetwork.CHANNEL.sendToServer(new PacketOpenMindControlMenuRequest(entry.id));}
        @Override public void onButtonRelease(){}
        @Override public void drawIcon(Minecraft mc,Gui gui,int x,int y){
            net.minecraft.entity.Entity found=mc.world==null?null:mc.world.getEntityByID(entry.entityId);
            // Player heads are a cheap 2D crop from the already-loaded skin texture.
            if(found instanceof AbstractClientPlayer){AbstractClientPlayer p=(AbstractClientPlayer)found;mc.getTextureManager().bindTexture(p.getLocationSkin());GlStateManager.color(1,1,1,1);Gui.drawScaledCustomSizeModalRect(x,y,8,8,8,8,16,16,64,64);Gui.drawScaledCustomSizeModalRect(x,y,40,8,8,8,16,16,64,64);return;}
            // Mob heads are rendered once into a tiny FBO and reused as a normal 2D texture.
            // Renderers can be unavailable for one client tick as an entity arrives. Retry at a
            // low rate instead of permanently falling back to the generic Mind Stone icon.
            if(mobHead==null&&found instanceof EntityLivingBase&&mc.player!=null&&mc.player.ticksExisted>=nextCaptureAttempt)capture(mc,(EntityLivingBase)found);
            if(mobHead!=null){drawFramebuffer(mobHead,x,y);return;}
            mc.getTextureManager().bindTexture(new ResourceLocation("herosmp","textures/abilities/mind_control.png"));Gui.drawModalRectWithCustomSizedTexture(x,y,0,0,16,16,16,16);
        }
        private void capture(Minecraft mc,EntityLivingBase entity){
            nextCaptureAttempt=mc.player==null?20:mc.player.ticksExisted+40;
            try{
                mobHead=new Framebuffer(64,64,true);mobHead.setFramebufferColor(0,0,0,0);mobHead.framebufferClear();mobHead.bindFramebuffer(true);
                GlStateManager.clearColor(0,0,0,0);GlStateManager.clear(16640);GlStateManager.color(1,1,1,1);RenderHelper.enableGUIStandardItemLighting();
                // Render enough of the model to obtain its real face, then crop a 32px head
                // square. This deliberately matches the 2D player-face format above rather
                // than showing a tiny full-body mob thumbnail in the ability bar.
                int scale=(int)Math.max(18,Math.min(34,Math.min(42D/Math.max(.45D,entity.width),52D/Math.max(.5D,entity.height))));int feet=Math.min(59,(int)(entity.height*scale)+6);
                GuiInventory.drawEntityOnScreen(32,feet,scale,0,0,entity);RenderHelper.disableStandardItemLighting();
                // Find the actual rendered silhouette. The previous fixed crop frequently sampled
                // empty pixels because mob model origins and proportions are not standardized.
                ByteBuffer pixels=BufferUtils.createByteBuffer(64*64*4);GlStateManager.bindTexture(mobHead.framebufferTexture);GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
                int minX=64,maxX=-1,minY=64,maxY=-1;for(int py=0;py<64;py++)for(int px=0;px<64;px++)if((pixels.get((py*64+px)*4+3)&255)>12){minX=Math.min(minX,px);maxX=Math.max(maxX,px);minY=Math.min(minY,py);maxY=Math.max(maxY,py);}
                if(maxX<minX)throw new IllegalStateException("Mob head render was empty");int screenTop=63-maxY,boundHeight=maxY-minY+1,boundWidth=maxX-minX+1;
                headCropSize=Math.max(12,Math.min(32,Math.max((int)(boundHeight*.38F),Math.min(boundWidth,24))));headCropLeft=Math.max(0,Math.min(64-headCropSize,(minX+maxX-headCropSize)/2));headCropTop=Math.max(0,Math.min(64-headCropSize,screenTop));
                mc.getFramebuffer().bindFramebuffer(true);GlStateManager.color(1,1,1,1);
            }catch(Throwable t){if(mobHead!=null)mobHead.deleteFramebuffer();mobHead=null;mc.getFramebuffer().bindFramebuffer(true);}
        }
        private void drawFramebuffer(Framebuffer f,int x,int y){
            GlStateManager.enableTexture2D();GlStateManager.enableAlpha();GlStateManager.enableBlend();GlStateManager.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GlStateManager.color(1,1,1,1);GlStateManager.bindTexture(f.framebufferTexture);
            // Framebuffer texture coordinates are upside down relative to GUI coordinates.
            double u0=headCropLeft/64D,u1=(headCropLeft+headCropSize)/64D,vTop=1D-headCropTop/64D,vBottom=1D-(headCropTop+headCropSize)/64D;
            Tessellator t=Tessellator.getInstance();BufferBuilder b=t.getBuffer();b.begin(7,DefaultVertexFormats.POSITION_TEX);
            b.pos(x,y+16,0).tex(u0,vBottom).endVertex();b.pos(x+16,y+16,0).tex(u1,vBottom).endVertex();b.pos(x+16,y,0).tex(u1,vTop).endVertex();b.pos(x,y,0).tex(u0,vTop).endVertex();t.draw();GlStateManager.disableBlend();
        }
        @Override public String getDescription(){return "Manage "+entry.name;}
        @Override public boolean renderCooldown(){return false;}@Override public float getCooldownPercentage(){return 0;}@Override public Vec3d getCooldownColor(){return new Vec3d(1,.5,0);}@Override public boolean showKey(){return true;}@Override public EnumAbilityBarColor getColor(){return EnumAbilityBarColor.ORANGE;}
    }
}
