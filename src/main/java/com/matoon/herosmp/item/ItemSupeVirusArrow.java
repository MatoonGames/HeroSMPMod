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
 * Supe Virus Arrow — extends ItemArrow so bows will load and fire it.
 * Has no effect on entities without a Lucraft Core superpower.
 */
public class ItemSupeVirusArrow extends ItemArrow {

    public ItemSupeVirusArrow() {
        setTranslationKey("supe_virus_arrow");
        setRegistryName("herosmp", "supe_virus_arrow");
        setCreativeTab(CreativeTabs.COMBAT);
        setMaxStackSize(16);
    }

    @Override
    public EntityArrow createArrow(World world, ItemStack stack, EntityLivingBase shooter) {
        return new EntityTaggedArrow(world, shooter, EntityTaggedArrow.Tag.SUPE_VIRUS);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World worldIn, List<String> tooltip,
            net.minecraft.client.util.ITooltipFlag flagIn) {
        tooltip.add(net.minecraft.util.text.TextFormatting.GREEN + "Infects supes with the Supe Virus");
        tooltip.add(net.minecraft.util.text.TextFormatting.GRAY + "Suppresses regen, drains 2% health/2s");
        tooltip.add(net.minecraft.util.text.TextFormatting.GRAY + "No effect on non-supes");
        tooltip.add(net.minecraft.util.text.TextFormatting.DARK_GRAY + "Cure: Supe Virus Cure item");
    }
}
