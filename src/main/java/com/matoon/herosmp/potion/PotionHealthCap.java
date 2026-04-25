package com.matoon.herosmp.potion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class PotionHealthCap extends Potion {

    public static final float BASE_MAX_HEALTH = 20.0f;

    private static final ResourceLocation ICON =
            new ResourceLocation("herosmp", "textures/items/health_cap_potion_overlay.png");

    public PotionHealthCap() {
        super(false, 0x8B0000);
        setRegistryName("herosmp", "health_cap");
    }

    @Override
    public void performEffect(EntityLivingBase entity, int amplifier) {
        clampHealth(entity);
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return true;
    }

    // --- Icon rendering ---

    /** Return false so vanilla skips drawing from the vanilla spritesheet. */
    @Override
    public boolean hasStatusIcon() {
        return false;
    }

    /** Draw our texture in the inventory effects panel. */
    @Override
    @SideOnly(Side.CLIENT)
    public void renderInventoryEffect(PotionEffect effect, Gui gui, int x, int y, float z) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(ICON);
        gui.drawTexturedModalRect(x + 6, y + 7, 0, 0, 18, 18);
    }

    /** Draw our texture on the HUD. */
    @Override
    @SideOnly(Side.CLIENT)
    public void renderHUDEffect(PotionEffect effect, Gui gui, int x, int y, float z, float alpha) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(ICON);
        gui.drawTexturedModalRect(x + 3, y + 3, 0, 0, 18, 18);
    }

    // --- Health + absorption clamp ---

    /**
     * Clamps the entity's current health to BASE_MAX_HEALTH and removes any
     * absorption that would push effective HP above the base cap.
     */
    public static void clampHealth(EntityLivingBase entity) {
        // Remove absorption hearts entirely while the cap is active
        if (entity.getAbsorptionAmount() > 0) {
            entity.setAbsorptionAmount(0);
        }
        if (entity.getHealth() > BASE_MAX_HEALTH) {
            entity.setHealth(BASE_MAX_HEALTH);
        }
    }
}
