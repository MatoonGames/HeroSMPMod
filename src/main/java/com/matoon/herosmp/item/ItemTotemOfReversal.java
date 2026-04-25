package com.matoon.herosmp.item;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/**
 * Totem of Reversal.
 *
 * When the holder would be hit by one of our custom arrow effects
 * (Power Swapper, Life Link, Supe Virus), the effect is reversed: the
 * original shooter receives the effect instead of the holder.
 *
 * Only one totem is consumed per arrow hit. The reversal logic lives in
 * {@link com.matoon.herosmp.events.ArrowEffectHandler#tryConsumeReversalTotem}.
 */
public class ItemTotemOfReversal extends Item {

    public ItemTotemOfReversal() {
        setTranslationKey("totem_of_reversal");
        setRegistryName("herosmp", "totem_of_reversal");
        setCreativeTab(CreativeTabs.COMBAT);
        setMaxStackSize(1);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World worldIn, List<String> tooltip,
            net.minecraft.client.util.ITooltipFlag flagIn) {
        tooltip.add(net.minecraft.util.text.TextFormatting.LIGHT_PURPLE + "Reverses an arrow effect back at the shooter");
        tooltip.add(net.minecraft.util.text.TextFormatting.GRAY + "The shooter receives what they sent at you");
        tooltip.add(net.minecraft.util.text.TextFormatting.DARK_GRAY + "Consumed on use");
    }
}
