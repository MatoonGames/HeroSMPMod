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
import org.lwjgl.opengl.GL11;
import java.util.*;

/** One cached head texture per companion; no entity model is rendered during normal HUD frames. */
@SideOnly(Side.CLIENT)
public class MindControlAbilityBarProvider implements IAbilityBarProvider {
    private static final ResourceLocation FALLBACK_ICON=new ResourceLocation("herosmp","textures/abilities/mind_control.png");
    private static int nextGlobalCaptureTick;
    private final Map<UUID,CompanionEntry> cached=new HashMap<>(); private List<IAbilityBarEntry> displayed=Collections.emptyList(); private int revision=-1;
    @Override public List<IAbilityBarEntry> getEntries(){
        if(revision==MindControlClientState.revision())return displayed;revision=MindControlClientState.revision();Set<UUID> keep=new HashSet<>();List<IAbilityBarEntry> next=new ArrayList<>();
        for(PacketMindControlEntries.Entry e:MindControlClientState.get()){keep.add(e.id);CompanionEntry entry=cached.get(e.id);if(entry==null||!entry.matches(e)){if(entry!=null)entry.dispose();entry=new CompanionEntry(e);cached.put(e.id,entry);}next.add(entry);}
        Iterator<Map.Entry<UUID,CompanionEntry>> it=cached.entrySet().iterator();while(it.hasNext()){Map.Entry<UUID,CompanionEntry> e=it.next();if(!keep.contains(e.getKey())){e.getValue().dispose();it.remove();}}
        displayed=Collections.unmodifiableList(next);return displayed;
    }
    private static class CompanionEntry implements IAbilityBarEntry {
        final PacketMindControlEntries.Entry entry; Framebuffer mobHead; int nextCaptureAttempt;
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
            if(mobHead==null&&found instanceof EntityLivingBase&&mc.player!=null&&mc.player.ticksExisted>=nextCaptureAttempt&&mc.player.ticksExisted>=nextGlobalCaptureTick){nextGlobalCaptureTick=mc.player.ticksExisted+2;capture(mc,(EntityLivingBase)found);}
            if(mobHead!=null){drawFramebuffer(mobHead,x,y);return;}
            mc.getTextureManager().bindTexture(FALLBACK_ICON);Gui.drawModalRectWithCustomSizedTexture(x,y,0,0,16,16,16,16);
        }
        private void capture(Minecraft mc,EntityLivingBase entity){
            nextCaptureAttempt=mc.player==null?20:mc.player.ticksExisted+40;
            boolean matrices=false;
            try{
                mobHead=new Framebuffer(32,32,true);mobHead.setFramebufferColor(0,0,0,0);mobHead.framebufferClear();mobHead.bindFramebuffer(true);
                GlStateManager.clearColor(0,0,0,0);GlStateManager.clear(16640);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,32,32,0,1000,3000);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();matrices=true;GlStateManager.loadIdentity();GlStateManager.translate(0,0,-2000);GlStateManager.color(1,1,1,1);RenderHelper.enableGUIStandardItemLighting();
                // Put the entity's eye line in the center and push its feet/body below the
                // framebuffer. The result is a real model head portrait, not a stretched crop
                // from an arbitrary section of the mob texture or full-body thumbnail.
                int scale=(int)Math.max(16,Math.min(42,22D/Math.max(.45D,entity.width)));int feet=(int)Math.round(16D+entity.getEyeHeight()*scale);
                GuiInventory.drawEntityOnScreen(16,feet,scale,0,0,entity);RenderHelper.disableStandardItemLighting();
                GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);matrices=false;mc.getFramebuffer().bindFramebuffer(true);GlStateManager.color(1,1,1,1);
            }catch(Throwable t){RenderHelper.disableStandardItemLighting();if(matrices){GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);}if(mobHead!=null)mobHead.deleteFramebuffer();mobHead=null;mc.getFramebuffer().bindFramebuffer(true);}
        }
        private void drawFramebuffer(Framebuffer f,int x,int y){
            GlStateManager.enableTexture2D();GlStateManager.enableAlpha();GlStateManager.enableBlend();GlStateManager.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GlStateManager.color(1,1,1,1);GlStateManager.bindTexture(f.framebufferTexture);
            Tessellator t=Tessellator.getInstance();BufferBuilder b=t.getBuffer();b.begin(7,DefaultVertexFormats.POSITION_TEX);
            b.pos(x,y+16,0).tex(0,0).endVertex();b.pos(x+16,y+16,0).tex(1,0).endVertex();b.pos(x+16,y,0).tex(1,1).endVertex();b.pos(x,y,0).tex(0,1).endVertex();t.draw();GlStateManager.disableBlend();
        }
        @Override public String getDescription(){return "Manage "+entry.name;}
        @Override public boolean renderCooldown(){return false;}@Override public float getCooldownPercentage(){return 0;}@Override public Vec3d getCooldownColor(){return new Vec3d(1,.5,0);}@Override public boolean showKey(){return true;}@Override public EnumAbilityBarColor getColor(){return EnumAbilityBarColor.ORANGE;}
    }
}
