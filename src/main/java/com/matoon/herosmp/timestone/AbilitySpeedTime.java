package com.matoon.herosmp.timestone;

import com.matoon.herosmp.registry.ModSounds;
import lucraft.mods.lucraftcore.superpowers.abilities.AbilityAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketTitle;
import net.minecraft.util.ResourceLocation;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

/**
 * Time Stone ability: Speed Up Time.
 * Each activation increases the tickrate in the player's current dimension by 5
 * (maximum 100.0). Only players inside the same dimension are affected; the
 * world outside is untouched. An action-bar message confirms the new speed.
 *
 * Blocked when the player's Time Stone charge is depleted (in PVP/HG, or if
 * configured to apply outside matches). The charge bar is drawn in the bottom
 * 2px of the ability icon slot.
 */
public class AbilitySpeedTime extends AbilityAction {

    private static final ResourceLocation ICON =
            new ResourceLocation("herosmp", "textures/abilities/pixel_fast_forward.png");

    private static final float STEP     = 5.0f;
    private static final float MAX_RATE = 100.0f;

    public AbilitySpeedTime(EntityLivingBase entity) {
        super(entity);
    }

    @Override
    public boolean action() {
        if (!(entity instanceof EntityPlayerMP)) return false;
        EntityPlayerMP player = (EntityPlayerMP) entity;

        // Block if charge mechanic is active and the charge is depleted.
        if (TimeStoneChargeManager.isChargeActive(player)
                && TimeStoneChargeManager.isDepleted(player.getUniqueID())) {
            player.connection.sendPacket(new SPacketTitle(
                    SPacketTitle.Type.ACTIONBAR,
                    new TextComponentString(TextFormatting.RED + "Time Stone Recharging..."),
                    -1, 30, 10));
            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.6f, 1.2f);
            return false;
        }

        int dim = player.dimension;
        float current = TimeStoneDimensionManager.getRate(dim);
        float next = Math.min(MAX_RATE, current + STEP);
        TimeStoneDimensionManager.applyRate(player, dim, next);
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

        // Charge bar — bottom 2 px of the icon slot (like Minecraft durability bar).
        float charge = TimeStoneChargeManager.getClientCharge();
        Gui.drawRect(x, y + 14, x + 16, y + 16, 0xFF000000);
        int barWidth = (int) (charge * 16);
        if (barWidth > 0) {
            int colour = charge > 0.5f ? 0xFF00FF00 : charge > 0.25f ? 0xFFFFFF00 : 0xFFFF4400;
            Gui.drawRect(x, y + 14, x + barWidth, y + 16, colour);
        }
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
