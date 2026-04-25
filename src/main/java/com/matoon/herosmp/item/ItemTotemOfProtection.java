package com.matoon.herosmp.item;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/**
 * Totem of Protection.
 *
 * When the holder would be hit by one of our custom arrow effects
 * (Power Swapper, Life Link, Supe Virus), the effect is blocked and one
 * totem is consumed from the player's inventory.
 *
 * Works like a vanilla totem — it just needs to be anywhere in the inventory.
 * Check and consumption logic is in
 * {@link com.matoon.herosmp.events.ArrowEffectHandler#tryConsumeProtectionTotem}.
 */
public class ItemTotemOfProtection extends Item {

    public ItemTotemOfProtection() {
        setTranslationKey("totem_of_protection");
        setRegistryName("herosmp", "totem_of_protection");
        setCreativeTab(CreativeTabs.COMBAT);
        setMaxStackSize(1);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World worldIn, List<String> tooltip,
            net.minecraft.client.util.ITooltipFlag flagIn) {
        tooltip.add(net.minecraft.util.text.TextFormatting.YELLOW + "Blocks one arrow effect directed at you");
        tooltip.add(net.minecraft.util.text.TextFormatting.GRAY + "Protects against: Power Swap, Life Link, Supe Virus");
        tooltip.add(net.minecraft.util.text.TextFormatting.DARK_GRAY + "Consumed on use");
    }
}
