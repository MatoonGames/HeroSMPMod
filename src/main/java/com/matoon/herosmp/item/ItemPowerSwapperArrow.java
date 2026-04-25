package com.matoon.herosmp.item;

import com.matoon.herosmp.entity.EntityTaggedArrow;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/**
 * Power Swapper Arrow — extends ItemArrow so bows will load and fire it.
 * createArrow() is the Forge hook called by ItemBow when it fires any ItemArrow.
 */
public class ItemPowerSwapperArrow extends ItemArrow {

    public ItemPowerSwapperArrow() {
        setTranslationKey("power_swapper_arrow");
        setRegistryName("herosmp", "power_swapper_arrow");
        setCreativeTab(CreativeTabs.COMBAT);
        setMaxStackSize(16);
    }

    @Override
    public EntityArrow createArrow(World world, ItemStack stack, EntityLivingBase shooter) {
        return new EntityTaggedArrow(world, shooter, EntityTaggedArrow.Tag.POWER_SWAP);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World worldIn, List<String> tooltip,
            net.minecraft.client.util.ITooltipFlag flagIn) {
        tooltip.add(net.minecraft.util.text.TextFormatting.GOLD + "Swaps superpowers with the target");
        tooltip.add(net.minecraft.util.text.TextFormatting.GRAY + "Fire with a bow or right-click");
        tooltip.add(net.minecraft.util.text.TextFormatting.DARK_GRAY + "Blocked by Totem of Protection");
    }
}
