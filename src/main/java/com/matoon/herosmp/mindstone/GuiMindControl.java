package com.matoon.herosmp.mindstone;

import com.matoon.herosmp.network.ModNetwork;
import com.matoon.herosmp.network.PacketMindControlMenuAction;
import com.matoon.herosmp.network.PacketMindControlAbilityAction;
import com.matoon.herosmp.network.PacketOpenMindControlMenu.AbilityInfo;
import lucraft.mods.lucraftcore.superpowers.abilities.Ability;
import lucraft.mods.lucraftcore.superpowers.Superpower;
import lucraft.mods.lucraftcore.superpowers.SuperpowerHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import org.lwjgl.input.Mouse;
import java.io.IOException;
import java.util.*;

/** Compact Mind Stone portal: gold magic, readable inventory, and a full-lit entity vision. */
@SideOnly(Side.CLIENT)
public class GuiMindControl extends GuiScreen {
    private final UUID target; private final int entityId; private final String name; private final boolean playerInventory; private final List<ItemStack> items; private final float snapshotHealth,maxHealth; private final int armor,remainingTicks; private final List<AbilityInfo> abilities; private final String superpowerId,superpowerName; private boolean abilityAutocast, abilitiesTab;
    private int left,top,w,h,abilityScroll; private long openedWorldTick; private List<Ability> frameAbilities=Collections.emptyList();

    public GuiMindControl(UUID target,int entityId,String name,boolean playerInventory,List<ItemStack> items,float health,float maxHealth,int armor,int remainingTicks,List<AbilityInfo> abilities,boolean abilityAutocast,String superpowerId,String superpowerName){this.target=target;this.entityId=entityId;this.name=name;this.playerInventory=playerInventory;this.items=items;this.snapshotHealth=health;this.maxHealth=maxHealth;this.armor=armor;this.remainingTicks=remainingTicks;this.abilities=abilities;this.abilityAutocast=abilityAutocast;this.superpowerId=superpowerId;this.superpowerName=superpowerName;}

    @Override public void initGui(){
        w=Math.min(356,width-16); h=Math.min(218,height-16); left=(width-w)/2; top=(height-h)/2;openedWorldTick=mc.world==null?0:mc.world.getTotalWorldTime();
        int gap=8,bw=(w-32-gap)/2,by=top+h-27;
        buttonList.add(new PortalButton(0,left+16,by,bw,18,"DRAIN HEALTH",0xFFD7A928));
        buttonList.add(new PortalButton(1,left+16+bw+gap,by,bw,18,"RELEASE CONTROL",0xFFC94B58));
        buttonList.add(new PortalButton(2,left+w-61,top+9,49,15,"RETURN",0xFFFFD86A));
        updateTabButtons();
    }

    @Override protected void actionPerformed(GuiButton b)throws IOException{
        if(b.id<2){ModNetwork.CHANNEL.sendToServer(new PacketMindControlMenuAction(target,b.id==0));Minecraft.getMinecraft().displayGuiScreen(null);}
        else if(b.id==2){abilitiesTab=false; updateTabButtons();}
    }

    @Override public void drawScreen(int mouseX,int mouseY,float partial){
        drawDefaultBackground();
        Gui.drawRect(left-1,top-1,left+w+1,top+h+1,0xFF9B7620);
        Gui.drawRect(left,top,left+w,top+h,0xF408080A);
        drawGradientRect(left+1,top+1,left+w-1,top+36,0xFF30230D,0xFF11100D);
        drawString(fontRenderer,"MIND STONE",left+12,top+9,0xFFFFD86A);
        drawString(fontRenderer,"A window into "+name,left+12,top+22,0xFFE9E3D2);

        EntityLivingBase entity=null;
        if(mc.world!=null&&mc.world.getEntityByID(entityId) instanceof EntityLivingBase)entity=(EntityLivingBase)mc.world.getEntityByID(entityId);
        if(abilitiesTab) { drawAbilities(entity,mouseX,mouseY); super.drawScreen(mouseX,mouseY,partial); return; }

        int portalX=left+80,portalY=top+106;
        drawPortal(portalX,portalY,55,partial);
        drawPrimaryAbility(entity,portalX-54,top+41);
        drawCenteredString(fontRenderer,"CONTROLLED",portalX,top+171,0xFFFFCE55);

        int panelX=left+166,panelY=top+45,panelRight=left+w-11;
        drawString(fontRenderer,playerInventory?"INVENTORY + HOTBAR":"EQUIPMENT",panelX+4,panelY+2,0xFFFFD86A);
        Gui.drawRect(panelX+4,panelY+14,panelRight,panelY+15,0xFF72571B);
        drawItems(panelX+4,panelY+21);

        GlStateManager.color(1F,1F,1F,1F);GlStateManager.enableDepth();RenderHelper.enableGUIStandardItemLighting();
        if(entity!=null)GuiInventory.drawEntityOnScreen(portalX,top+160,43,portalX-mouseX,top+112-mouseY,entity);
        else drawCenteredString(fontRenderer,"OUT OF RANGE",portalX,top+105,0xFFFFFFFF);
        RenderHelper.disableStandardItemLighting();GlStateManager.disableDepth();GlStateManager.color(1F,1F,1F,1F);
        drawStats(entity,panelX+4,panelRight,top+157);
        if(mouseX>=portalX-56&&mouseY>=top+39&&mouseX<portalX-28&&mouseY<top+69) drawSuperpowerTooltip(mouseX,mouseY);
        super.drawScreen(mouseX,mouseY,partial);
    }

    private void updateTabButtons(){ for(GuiButton b:buttonList)if(b.id==2)b.visible=abilitiesTab; }
    private Ability localAbility(EntityLivingBase ignored,String key){for(Ability a:frameAbilities)if(key.equals(a.getKey()))return a;return null;}
    private Superpower localSuperpower(EntityLivingBase entity){return entity==null?null:SuperpowerHandler.getSuperpower(entity);}
    /** The upper-left sigil is the injected superpower itself, never merely one of its abilities. */
    private void drawPrimaryAbility(EntityLivingBase entity,int x,int y){
        if(superpowerId.isEmpty())return; Gui.drawRect(x-2,y-2,x+26,y+26,0xA00E0B10);Gui.drawRect(x-1,y-1,x+25,y+25,0xFF8B681B);Superpower superpower=localSuperpower(entity);
        // Superpower renderers inherit the previous GUI colour/blend state. Isolate them so a
        // gold portal/font draw can never tint their texture or turn transparent pixels white.
        GlStateManager.pushMatrix();GlStateManager.color(1F,1F,1F,1F);GlStateManager.enableAlpha();
        if(superpower!=null){RenderHelper.enableGUIStandardItemLighting();float oldZ=zLevel,oldItemZ=mc.getRenderItem().zLevel;zLevel=100F;mc.getRenderItem().zLevel=100F;GlStateManager.enableLighting();GlStateManager.enableRescaleNormal();GlStateManager.translate(x+4,y+4,0F);GlStateManager.scale(.5D,.5D,.5D);superpower.renderIcon(mc,0,0);mc.getRenderItem().zLevel=oldItemZ;zLevel=oldZ;RenderHelper.disableStandardItemLighting();GlStateManager.disableLighting();GlStateManager.enableBlend();}
        else {Gui.drawRect(5,5,19,19,0xFF5C3510);drawCenteredString(fontRenderer,"✦",12,6,0xFFFFD86A);}
        GlStateManager.color(1F,1F,1F,1F);GlStateManager.disableBlend();GlStateManager.popMatrix();
    }
    private void drawSuperpowerTooltip(int mouseX,int mouseY){
        List<String> tip=new ArrayList<>();tip.add(TextFormatting.GOLD+superpowerName);tip.add(TextFormatting.GRAY+"Injected Lucraft superpower");
        int count=Math.min(8,abilities.size());for(int i=0;i<count;i++){AbilityInfo info=abilities.get(i);tip.add(TextFormatting.YELLOW+"- "+TextFormatting.WHITE+info.name+(info.triggerable?TextFormatting.GRAY+" (active)":TextFormatting.DARK_GRAY+" (passive)"));}
        if(abilities.size()>count)tip.add(TextFormatting.GRAY+"+ "+(abilities.size()-count)+" more abilities");tip.add(TextFormatting.GOLD+"Click to command its abilities");drawHoveringText(tip,mouseX,mouseY);
    }
    private void drawAbilities(EntityLivingBase entity,int mouseX,int mouseY){
        frameAbilities=entity==null?Collections.emptyList():Ability.getAbilities(entity);
        int x=left+13, right=left+w-13; drawString(fontRenderer,superpowerName.isEmpty()?"NO INJECTED SUPERPOWER":superpowerName.toUpperCase(Locale.ROOT),x,top+48,0xFFFFD86A);drawString(fontRenderer,"Its active abilities can be compelled; passive abilities are observed.",x,top+61,0xFFE9E3D2);
        int toggleY=top+75;Gui.drawRect(x,toggleY,right,toggleY+20,0xB01B1712);drawString(fontRenderer,"COMBAT AUTOPILOT",x+8,toggleY+6,0xFFFFD86A);int toggleX=right-53;Gui.drawRect(toggleX,toggleY+4,right-6,toggleY+16,abilityAutocast?0xFF36783A:0xFF5D2426);drawCenteredString(fontRenderer,abilityAutocast?"ON":"OFF",(toggleX+right-6)/2,toggleY+6,0xFFFFFFFF);
        int y=top+102; if(abilities.isEmpty())drawString(fontRenderer,"This mind has no usable Lucraft actions.",x,y+8,0xFFB9AF96);
        abilityScroll=Math.max(0,Math.min(abilityScroll,Math.max(0,abilities.size()-4)));
        for(int row=0;row<4&&abilityScroll+row<abilities.size();row++,y+=25){int i=abilityScroll+row;AbilityInfo info=abilities.get(i);Gui.drawRect(x,y,right,y+22,0xB0121015);Gui.drawRect(x,y,x+2,y+22,info.ready?0xFFFFC951:0xFF72571B);Ability ability=localAbility(entity,info.key);GlStateManager.color(1F,1F,1F,1F);if(ability!=null)ability.drawIcon(mc,this,x+6,y+2);else {Gui.drawRect(x+6,y+2,x+24,y+20,0xFF55320F);drawCenteredString(fontRenderer,"✦",x+15,y+5,0xFFFFD86A);}GlStateManager.color(1F,1F,1F,1F);drawString(fontRenderer,info.name,x+30,y+4,info.ready?0xFFFFFFFF:0xFFAE9F7D);drawString(fontRenderer,!info.triggerable?"PASSIVE":info.ready?"INVOKE":"RECHARGING",right-70,y+7,info.ready?0xFFFFD86A:0xFF8D7A51);
            if(mouseX>=x&&mouseY>=y&&mouseX<right&&mouseY<y+22){List<String> tip=new ArrayList<>();tip.add(info.name);tip.addAll(fontRenderer.listFormattedStringToWidth(info.description,190));tip.add(!info.triggerable?"§8Passive ability":info.ready?"§aClick to invoke":"§6Still recharging");drawHoveringText(tip,mouseX,mouseY);}
        }
        if(abilities.size()>4){int trackTop=top+102,trackBottom=top+199,thumb=Math.max(14,(97*4)/abilities.size()),range=97-thumb,offset=(int)(range*(abilityScroll/(float)(abilities.size()-4)));Gui.drawRect(right-3,trackTop,right-1,trackBottom,0xFF392A15);Gui.drawRect(right-3,trackTop+offset,right-1,trackTop+offset+thumb,0xFFFFC951);}
    }
    @Override protected void mouseClicked(int mouseX,int mouseY,int button)throws IOException { if(!abilitiesTab&&button==0&&!superpowerId.isEmpty()&&mouseX>=left+24&&mouseX<left+52&&mouseY>=top+39&&mouseY<top+69){abilitiesTab=true;updateTabButtons();return;}super.mouseClicked(mouseX,mouseY,button); if(!abilitiesTab||button!=0)return; int x=left+13,right=left+w-13,toggleY=top+75;if(mouseX>=x&&mouseX<right&&mouseY>=toggleY&&mouseY<toggleY+20){abilityAutocast=!abilityAutocast;ModNetwork.CHANNEL.sendToServer(PacketMindControlAbilityAction.autocast(target,abilityAutocast));return;}int y=top+102;for(int row=0;row<4&&abilityScroll+row<abilities.size();row++,y+=25){AbilityInfo info=abilities.get(abilityScroll+row);if(mouseX>=x&&mouseX<right&&mouseY>=y&&mouseY<y+22&&info.triggerable&&info.ready){ModNetwork.CHANNEL.sendToServer(PacketMindControlAbilityAction.trigger(target,info.key));return;}} }
    @Override public void handleMouseInput()throws IOException {super.handleMouseInput();if(abilitiesTab){int wheel=Mouse.getEventDWheel();if(wheel!=0){abilityScroll-=Integer.signum(wheel);abilityScroll=Math.max(0,Math.min(abilityScroll,Math.max(0,abilities.size()-4)));}}}

    private void drawPortal(int cx,int cy,int radius,float partial){
        double time=(mc.world.getTotalWorldTime()+partial)*.075D;
        GlStateManager.pushMatrix();GlStateManager.disableTexture2D();GlStateManager.disableLighting();GlStateManager.enableBlend();GlStateManager.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);GlStateManager.depthMask(false);
        Tessellator tess=Tessellator.getInstance();BufferBuilder b=tess.getBuffer();
        // Warm corona fading into a black center.
        b.begin(GL11.GL_TRIANGLE_FAN,DefaultVertexFormats.POSITION_COLOR);b.pos(cx,cy,0).color(8,5,1,245).endVertex();
        for(int i=0;i<=48;i++){double a=i*Math.PI*2/48;b.pos(cx+Math.cos(a)*radius,cy+Math.sin(a)*radius*.78,0).color(255,180,28,20).endVertex();}tess.draw();
        // Counter-rotating warped rings.
        for(int ring=0;ring<5;ring++){double spin=time*(ring%2==0?1D:-.68D)+ring*1.21D,r=radius-ring*8D+Math.sin(time*1.4+ring)*2.2D;b.begin(GL11.GL_LINE_STRIP,DefaultVertexFormats.POSITION_COLOR);for(int i=0;i<=48;i++){double a=spin+i*Math.PI*2/48,warp=Math.sin(a*(2+ring%3)+time*1.8)*2.8;float alpha=.88F-ring*.10F;b.pos(cx+Math.cos(a)*(r+warp),cy+Math.sin(a)*(r+warp)*.78,0).color(1F,.48F+ring*.07F,.05F,alpha).endVertex();}tess.draw();}
        // Broken rune arcs keep the portal from reading as simple concentric circles.
        b.begin(GL11.GL_LINES,DefaultVertexFormats.POSITION_COLOR);for(int rune=0;rune<16;rune++){double a=time*.38+rune*Math.PI*2/16,r=radius+5+Math.sin(rune*4.7)*3;b.pos(cx+Math.cos(a)*r,cy+Math.sin(a)*r*.78,0).color(1F,.86F,.28F,.9F).endVertex();b.pos(cx+Math.cos(a+.10)*(r+4),cy+Math.sin(a+.10)*(r+4)*.78,0).color(1F,.42F,.03F,.18F).endVertex();}tess.draw();
        GlStateManager.depthMask(true);GlStateManager.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GlStateManager.disableBlend();GlStateManager.enableTexture2D();GlStateManager.enableLighting();GlStateManager.color(1F,1F,1F,1F);GlStateManager.popMatrix();
    }

    private void drawItems(int x0,int y0){
        RenderHelper.enableGUIStandardItemLighting();
        if(playerInventory){
            // Vanilla layout: main inventory first, separated hotbar below it.
            for(int i=9;i<36;i++)drawSlot(items.size()>i?items.get(i):ItemStack.EMPTY,x0+((i-9)%9)*19,y0+((i-9)/9)*19);
            for(int i=0;i<9;i++)drawSlot(items.size()>i?items.get(i):ItemStack.EMPTY,x0+i*19,y0+62);
        }else{
            for(int i=0;i<items.size();i++)drawSlot(items.get(i),x0+(i%6)*19,y0+(i/6)*19);
        }
        RenderHelper.disableStandardItemLighting();GlStateManager.color(1F,1F,1F,1F);
    }

    private void drawSlot(ItemStack stack,int x,int y){
        Gui.drawRect(x-1,y-1,x+18,y+18,0xFF80651F);Gui.drawRect(x,y,x+17,y+17,0xFF242116);
        if(!stack.isEmpty()){mc.getRenderItem().renderItemAndEffectIntoGUI(stack,x,y);mc.getRenderItem().renderItemOverlayIntoGUI(fontRenderer,stack,x,y,null);}
    }

    private void drawStats(EntityLivingBase entity,int x,int right,int y){
        float health=entity==null?snapshotHealth:entity.getHealth(), maximum=entity==null?maxHealth:entity.getMaxHealth();
        int width=right-x,filled=(int)(Math.max(0F,Math.min(1F,health/Math.max(1F,maximum)))*width);
        drawString(fontRenderer,String.format(Locale.ROOT,"HEALTH  %.1f / %.1f",health,maximum),x,y,0xFFFFFFFF);
        Gui.drawRect(x,y+11,right,y+16,0xFF29120D);Gui.drawRect(x,y+11,x+filled,y+16,health/Math.max(1F,maximum)>.3F?0xFFE34D35:0xFFFFB12E);
        double distance=entity==null||mc.player==null?-1:Math.sqrt(mc.player.getDistanceSq(entity));
        long elapsed=mc.world==null?0:Math.max(0,mc.world.getTotalWorldTime()-openedWorldTick);int seconds=(int)Math.max(0,(remainingTicks-elapsed)/20);
        String detail="ARMOR "+armor+"   "+(distance<0?"DIST --":"DIST "+String.format(Locale.ROOT,"%.1fm",distance))+"   TIME "+seconds+"s";
        drawString(fontRenderer,detail,x,y+20,0xFFE8DDBE);
    }

    @Override public boolean doesGuiPauseGame(){return false;}

    private static class PortalButton extends GuiButton{
        final int accent;PortalButton(int id,int x,int y,int w,int h,String text,int accent){super(id,x,y,w,h,text);this.accent=accent;}
        @Override public void drawButton(Minecraft mc,int mx,int my,float p){if(!visible)return;hovered=mx>=x&&my>=y&&mx<x+width&&my<y+height;Gui.drawRect(x,y,x+width,y+height,hovered?0xFF332B19:0xE8161512);Gui.drawRect(x,y,x+width,y+1,accent);drawCenteredString(mc.fontRenderer,displayString,x+width/2,y+5,hovered?0xFFFFFFFF:accent);}
    }
}
