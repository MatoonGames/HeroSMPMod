package com.matoon.herosmp.mindstone;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import com.matoon.herosmp.network.ModNetwork;
import com.matoon.herosmp.network.PacketMindControlResistanceResponse;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import java.util.UUID;

/** Keeps camera look intact while taking every gameplay control away from the controlled player. */
@SideOnly(Side.CLIENT)
public class MindControlPlayerLock {
    private static final ResourceLocation CONTROL_OVERLAY=new ResourceLocation("herosmp","textures/gui/mind_controlled.png");
    private static boolean locked;private static long lockEndsAt;
    private static UUID resistanceToken;private static long resistanceStarted;private static int resistanceDuration,resistanceStart,resistanceEnd;private static boolean resistanceEscape,resistanceSent;
    public static void setLocked(boolean value){setLocked(value,0);}
    public static void setLocked(boolean value,int remainingTicks){locked=value;lockEndsAt=value?System.currentTimeMillis()+remainingTicks*50L:0;}
    public static void startResistance(UUID token,int duration,int windowStart,int windowEnd,boolean escape){resistanceToken=token;resistanceDuration=duration;resistanceStart=windowStart;resistanceEnd=windowEnd;resistanceEscape=escape;resistanceStarted=System.currentTimeMillis();resistanceSent=false;}
    public static void finishResistance(){resistanceToken=null;resistanceSent=false;}
    public static boolean isLocked(){return locked;}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.START||!locked)return;
        Minecraft mc=Minecraft.getMinecraft(); if(mc.player==null)return;
        clearAll(mc);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void input(InputEvent.KeyInputEvent e){if(resistanceToken!=null&&Keyboard.getEventKey()==Keyboard.KEY_SPACE&&Keyboard.getEventKeyState()&&!resistanceSent){resistanceSent=true;int elapsed=(int)((System.currentTimeMillis()-resistanceStarted)/50L);ModNetwork.CHANNEL.sendToServer(new PacketMindControlResistanceResponse(resistanceToken,elapsed));}if(locked||resistanceToken!=null)clearAll(Minecraft.getMinecraft());}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void mouse(MouseEvent e){if(locked)clearAll(Minecraft.getMinecraft());}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void movement(InputUpdateEvent e){if(locked){e.getMovementInput().moveForward=0;e.getMovementInput().moveStrafe=0;e.getMovementInput().jump=false;e.getMovementInput().sneak=false;}}
    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Post e){if(e.getType()!=RenderGameOverlayEvent.ElementType.ALL)return;Minecraft mc=Minecraft.getMinecraft();ScaledResolution sr=new ScaledResolution(mc);int cx=sr.getScaledWidth()/2;if(locked){drawControlOverlay(mc,sr);if(lockEndsAt>0){int seconds=(int)Math.max(0,(lockEndsAt-System.currentTimeMillis()+999)/1000);String s="MIND CONTROL  "+seconds+"s";mc.fontRenderer.drawStringWithShadow(s,cx-mc.fontRenderer.getStringWidth(s)/2,sr.getScaledHeight()-58,0xFFFFD86A);}}if(resistanceToken==null)return;long elapsedMs=System.currentTimeMillis()-resistanceStarted;if(elapsedMs>resistanceDuration*50L+750){resistanceToken=null;return;}float tick=elapsedMs/50F,progress=Math.max(0F,Math.min(1F,tick/resistanceDuration));int width=170,left=cx-width/2,y=sr.getScaledHeight()/2+34;Gui.drawRect(left-5,y-20,left+width+5,y+18,0xD008080B);String title=resistanceEscape?"BREAK THE MIND CONTROL":"RESIST THE MIND CONTROL";mc.fontRenderer.drawStringWithShadow(title,cx-mc.fontRenderer.getStringWidth(title)/2,y-16,0xFFFFD86A);Gui.drawRect(left,y,left+width,y+8,0xFF251D2F);int successLeft=left+(int)(width*(resistanceStart/(float)resistanceDuration)),successRight=left+(int)(width*(resistanceEnd/(float)resistanceDuration));Gui.drawRect(successLeft,y,successRight,y+8,0xFF55C878);int marker=left+(int)(width*progress);Gui.drawRect(marker-1,y-3,marker+2,y+11,0xFFFFFFFF);String prompt=resistanceSent?"Attempt registered":"Press SPACE in the green zone";mc.fontRenderer.drawStringWithShadow(prompt,cx-mc.fontRenderer.getStringWidth(prompt)/2,y+11,resistanceSent?0xFFB5ADBF:0xFFFFFFFF);}
    private static void drawControlOverlay(Minecraft mc,ScaledResolution sr){float alpha=.65F+.35F*(float)Math.sin(System.currentTimeMillis()/650D);GlStateManager.disableDepth();GlStateManager.enableBlend();GlStateManager.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GlStateManager.color(1F,1F,1F,alpha);mc.getTextureManager().bindTexture(CONTROL_OVERLAY);Gui.drawScaledCustomSizeModalRect(0,0,0,0,512,288,sr.getScaledWidth(),sr.getScaledHeight(),512,288);GlStateManager.color(1F,1F,1F,1F);GlStateManager.disableBlend();}
    private static void clearAll(Minecraft mc){if(mc.player==null)return;
        clear(mc.gameSettings.keyBindForward); clear(mc.gameSettings.keyBindBack);
        clear(mc.gameSettings.keyBindLeft); clear(mc.gameSettings.keyBindRight);
        clear(mc.gameSettings.keyBindJump); clear(mc.gameSettings.keyBindSneak);
        clear(mc.gameSettings.keyBindSprint); clear(mc.gameSettings.keyBindAttack);
        clear(mc.gameSettings.keyBindUseItem); clear(mc.gameSettings.keyBindDrop); clear(mc.gameSettings.keyBindInventory);
        if(lucraft.mods.lucraftcore.util.abilitybar.AbilityBarKeys.KEYS!=null)for(KeyBinding key:lucraft.mods.lucraftcore.util.abilitybar.AbilityBarKeys.KEYS)clear(key);
        clear(lucraft.mods.lucraftcore.util.abilitybar.AbilityBarKeys.UP);clear(lucraft.mods.lucraftcore.util.abilitybar.AbilityBarKeys.DOWN);
        mc.player.movementInput.moveForward=0; mc.player.movementInput.moveStrafe=0;
        mc.player.movementInput.jump=false; mc.player.movementInput.sneak=false;
    }
    private static void clear(KeyBinding key){if(key!=null)KeyBinding.setKeyBindState(key.getKeyCode(),false);}
}
