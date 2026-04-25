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
 * Life Link Arrow — extends ItemArrow so bows will load and fire it.
 * When the arrow hits a player or mob, the target gets the Life Link potion
 * effect for 45 seconds. All damage the target receives is redirected to the
 * shooter for the duration. Chains are followed and cycles are prevented.
 */
public class ItemLifeLinkArrow extends ItemArrow {

    public ItemLifeLinkArrow() {
        setTranslationKey("life_link_arrow");
        setRegistryName("herosmp", "life_link_arrow");
        setCreativeTab(CreativeTabs.COMBAT);
        setMaxStackSize(16);
    }

    @Override
    public EntityArrow createArrow(World world, ItemStack stack, EntityLivingBase shooter) {
        return new EntityTaggedArrow(world, shooter, EntityTaggedArrow.Tag.LIFE_LINK);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World worldIn, List<String> tooltip,
            net.minecraft.client.util.ITooltipFlag flagIn) {
        tooltip.add(net.minecraft.util.text.TextFormatting.AQUA + "Links the target to you for 45 seconds");
        tooltip.add(net.minecraft.util.text.TextFormatting.GRAY + "All damage they take is redirected to you");
        tooltip.add(net.minecraft.util.text.TextFormatting.GRAY + "Chains: A\u2192B\u2192C means A's damage goes to C");
        tooltip.add(net.minecraft.util.text.TextFormatting.DARK_GRAY + "Blocked by Totem of Protection");
    }
}
