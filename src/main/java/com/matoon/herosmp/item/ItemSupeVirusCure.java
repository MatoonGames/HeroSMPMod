package com.matoon.herosmp.item;

import com.matoon.herosmp.registry.ModPotions;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/**
 * Supe Virus Cure.
 *
 * A drinkable item that removes the Supe Virus potion effect from the drinker.
 * Returns an empty glass bottle after drinking (non-creative).
 */
public class ItemSupeVirusCure extends Item {

    public ItemSupeVirusCure() {
        setTranslationKey("supe_virus_cure");
        setRegistryName("herosmp", "supe_virus_cure");
        setCreativeTab(CreativeTabs.BREWING);
        setMaxStackSize(1);
    }

    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        return EnumAction.DRINK;
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return 32;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        player.setActiveHand(hand);
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    @Override
    public ItemStack onItemUseFinish(ItemStack stack, World world, EntityLivingBase entity) {
        if (!world.isRemote && entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            if (player.isPotionActive(ModPotions.SUPE_VIRUS)) {
                player.removePotionEffect(ModPotions.SUPE_VIRUS);
            }
            if (!player.capabilities.isCreativeMode) {
                stack.shrink(1);
                player.inventory.addItemStackToInventory(
                        new ItemStack(net.minecraft.init.Items.GLASS_BOTTLE));
            }
        }
        return stack;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World worldIn, List<String> tooltip,
            net.minecraft.client.util.ITooltipFlag flagIn) {
        tooltip.add(net.minecraft.util.text.TextFormatting.GREEN + "Removes the Supe Virus effect");
        tooltip.add(net.minecraft.util.text.TextFormatting.GRAY + "Drink to cure yourself");
    }
}
