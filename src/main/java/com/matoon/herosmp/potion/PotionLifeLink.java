package com.matoon.herosmp.potion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Life Link potion effect.
 *
 * When an entity has this effect, all damage they receive is redirected to
 * the entity that originally applied the effect (the "linker"). The actual
 * redirection logic lives in {@link com.matoon.herosmp.events.ArrowEffectHandler}
 * which intercepts LivingDamageEvent.
 *
 * The linker UUID is stored in the effect's NBT via the LifeLinkManager.
 */
public class PotionLifeLink extends Potion {

    private static final ResourceLocation ICON =
            new ResourceLocation("herosmp", "textures/items/life_link_arrow.png");

    public PotionLifeLink() {
        super(false, 0x4444FF);
        setRegistryName("herosmp", "life_link");
    }

    @Override
    public void performEffect(EntityLivingBase entity, int amplifier) {
        // Enforcement handled in ArrowEffectHandler / LifeLinkManager
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return false; // We don't need per-tick performEffect calls
    }

    @Override
    public boolean hasStatusIcon() {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderInventoryEffect(PotionEffect effect, Gui gui, int x, int y, float z) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(ICON);
        gui.drawTexturedModalRect(x + 6, y + 7, 0, 0, 18, 18);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderHUDEffect(PotionEffect effect, Gui gui, int x, int y, float z, float alpha) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(ICON);
        gui.drawTexturedModalRect(x + 3, y + 3, 0, 0, 18, 18);
    }
}
