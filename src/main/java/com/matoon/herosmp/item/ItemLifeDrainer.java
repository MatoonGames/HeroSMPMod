package com.matoon.herosmp.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

public class ItemLifeDrainer extends ItemSword {

    public ItemLifeDrainer() {
        super(ToolMaterial.DIAMOND);
        setTranslationKey("life_drainer");
        setRegistryName("herosmp", "life_drainer");
        setCreativeTab(net.minecraft.creativetab.CreativeTabs.COMBAT);
    }

    /**
     * Strip all built-in attribute modifiers (attack damage, attack speed)
     * so the sword deals zero base damage on its own.
     */
    @Override
    public Multimap<String, AttributeModifier> getAttributeModifiers(EntityEquipmentSlot slot, ItemStack stack) {
        return ImmutableMultimap.of();
    }

    @Override
    public boolean hitEntity(ItemStack stack, EntityLivingBase target, EntityLivingBase attacker) {
        if (!attacker.world.isRemote && attacker instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) attacker;

            float targetCurrentHealth = target.getHealth();
            float targetMaxHealth     = target.getMaxHealth();

            if (targetCurrentHealth > targetMaxHealth * 0.25f) {
                // Target is above 20% health — drain 20% of their max health.
                float drain = targetMaxHealth * 0.20f;
                target.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(player), drain);
                player.setAbsorptionAmount(player.getAbsorptionAmount() + drain);
            } else {
                // Target is at or below 20% health — backfire: deal 5% of target's max
                // health as damage to both the target and the attacker.
                target.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(player), targetMaxHealth * 0.05f);
                player.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(player), player.getMaxHealth() * 0.05f);
            }
        }

        stack.damageItem(1, attacker);
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World worldIn, List<String> tooltip, net.minecraft.client.util.ITooltipFlag flagIn) {
        tooltip.add(net.minecraft.util.text.TextFormatting.DARK_RED + "Drains 20% of the target's max health");
        tooltip.add(net.minecraft.util.text.TextFormatting.GOLD + "Grants that health as absorption hearts");
        tooltip.add(net.minecraft.util.text.TextFormatting.DARK_GRAY + "Backfires if target is below 20% health");
    }
}
