package com.matoon.herosmp.timestone;

import lucraft.mods.lucraftcore.superpowers.abilities.AbilityToggle;
import me.guichaguri.tickratechanger.api.TickrateAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

/**
 * Time Stone ability: Time Unlocker.
 * A toggleable ability that pins this player's client timer at 20 tps regardless
 * of any server-side tickrate changes applied by the other Time Stone abilities.
 *
 * Icon swaps between locked (off) and unlocked (on):
 *   - Locked  (off): time_locked.png  — player is subject to the dimension's tickrate
 *   - Unlocked (on): time_unlocked.png — player's client is pinned at 20 tps
 *
 * Limitation: TickrateChanger's client tickrate is a single global timer per
 * client process. Other players will still see this player move at the altered
 * server update rate (because server ticks, and therefore position updates, are
 * global). The unlock only affects how this player's own game loop runs locally.
 *
 * To avoid flooding the connection with a packet every tick, the ability only
 * resends when the dimension's stored rate has actually changed since the last
 * send. firstTick always sends immediately on toggle-on.
 */
public class AbilityTimeUnlocker extends AbilityToggle {

    private static final ResourceLocation ICON_LOCKED =
            new ResourceLocation("herosmp", "textures/abilities/time_locked.png");
    private static final ResourceLocation ICON_UNLOCKED =
            new ResourceLocation("herosmp", "textures/abilities/time_unlocked.png");

    private static final float NORMAL_RATE = 20.0f;

    /** The dimension rate we last observed, used to detect changes. -1 = not yet seen. */
    private float lastSeenDimRate = -1f;

    public AbilityTimeUnlocker(EntityLivingBase entity) {
        super(entity);
    }

    @Override
    public void firstTick() {
        // Always send immediately when the ability is toggled on.
        if (entity instanceof EntityPlayer) {
            TickrateAPI.changeClientTickrate((EntityPlayer) entity, NORMAL_RATE);
            lastSeenDimRate = TimeStoneDimensionManager.getRate(entity.dimension);
        }
    }

    @Override
    public void updateTick() {
        // Only resend when the dimension rate has changed since we last looked.
        // This re-pins the player to 20 if someone else fires Slow/Speed while
        // the unlocker is active, without spamming a packet every tick.
        if (!isEnabled() || !(entity instanceof EntityPlayer)) return;

        float dimRate = TimeStoneDimensionManager.getRate(entity.dimension);
        if (dimRate != lastSeenDimRate) {
            TickrateAPI.changeClientTickrate((EntityPlayer) entity, NORMAL_RATE);
            lastSeenDimRate = dimRate;
        }
    }

    @Override
    public void lastTick() {
        // Re-sync this player's client back to their dimension's current rate when toggled off.
        if (entity instanceof EntityPlayer) {
            float dimRate = TimeStoneDimensionManager.getRate(entity.dimension);
            TickrateAPI.changeClientTickrate((EntityPlayer) entity, dimRate);
            lastSeenDimRate = -1f;
        }
    }

    @Override
    public void drawIcon(Minecraft mc, Gui gui, int x, int y) {
        ResourceLocation icon = isEnabled() ? ICON_UNLOCKED : ICON_LOCKED;
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        mc.getTextureManager().bindTexture(icon);
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, 16, 16, 16, 16);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    @Override
    public NBTTagCompound serializeNBT() {
        return super.serializeNBT();
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        super.deserializeNBT(nbt);
    }
}
