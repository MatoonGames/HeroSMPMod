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
 * Supe Virus potion effect.
 *
 * Only affects entities that have a Lucraft superpower/injection. If the
 * afflicted entity has a superpower:
 *   - Suppresses ALL regeneration (including natural/food regen) every tick
 *   - Deals 5% of max health as damage every second (20 ticks)
 *   - Displays as green/poison hearts on the HUD
 *
 * Tick logic and regen suppression are handled in
 * {@link com.matoon.herosmp.events.ArrowEffectHandler}.
 *
 * Lasts 45 seconds (900 ticks) by default.
 */
public class PotionSupeVirus extends Potion {

    private static final ResourceLocation ICON =
            new ResourceLocation("herosmp", "textures/items/supe_virus_arrow.png");

    // Bypasses armor and resistance so the 2% drain is felt regardless of gear.
    private static final net.minecraft.util.DamageSource SUPE_VIRUS_DAMAGE =
            new net.minecraft.util.DamageSource("supe_virus").setDamageBypassesArmor().setMagicDamage();

    public PotionSupeVirus() {
        // isBadEffect=true → renders as a "harmful" potion; color matches poison green
        super(true, 0x44CC44);
        setRegistryName("herosmp", "supe_virus");
    }

    @Override
    public void performEffect(EntityLivingBase entity, int amplifier) {
        if (entity.world.isRemote) return;
        // Damage tick: 5% of max health every second, bypassing armor and resistance.
        float damage = entity.getMaxHealth() * 0.05f;
        // Reset hurtResistantTime so the virus damage is never swallowed by
        // the 20-tick invulnerability window from other sources.
        entity.hurtResistantTime = 0;
        entity.attackEntityFrom(SUPE_VIRUS_DAMAGE, damage);
    }

    /**
     * Fire performEffect every 20 ticks (1 second).
     */
    @Override
    public boolean isReady(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    /** Render as a harmful (red border) potion in the inventory — we override the icon. */
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
