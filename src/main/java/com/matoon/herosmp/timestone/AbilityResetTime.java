package com.matoon.herosmp.timestone;

import com.matoon.herosmp.registry.ModSounds;
import lucraft.mods.lucraftcore.superpowers.abilities.AbilityAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;

/**
 * Time Stone ability: Reset Time.
 * Instantly resets the tickrate in the player's current dimension to 20 (normal).
 * Only players in that dimension are affected. An action-bar message confirms.
 */
public class AbilityResetTime extends AbilityAction {

    private static final ResourceLocation ICON =
            new ResourceLocation("herosmp", "textures/abilities/reset_time.png");

    public AbilityResetTime(EntityLivingBase entity) {
        super(entity);
    }

    @Override
    public boolean action() {
        if (!(entity instanceof EntityPlayerMP)) return false;
        EntityPlayerMP player = (EntityPlayerMP) entity;
        TimeStoneDimensionManager.resetRate(player, player.dimension);
        player.world.playSound(null, player.posX, player.posY, player.posZ,
                ModSounds.TIME_EFFECT, SoundCategory.PLAYERS, 1.0f, 1.0f);
        return true;
    }

    @Override
    public void drawIcon(Minecraft mc, Gui gui, int x, int y) {
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        mc.getTextureManager().bindTexture(ICON);
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
