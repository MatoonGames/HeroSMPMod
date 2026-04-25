package com.matoon.herosmp.item;

import com.matoon.herosmp.potion.PotionHealthCap;
import com.matoon.herosmp.registry.ModPotions;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/**
 * Drinkable "Health Cap" potion.
 * Drinking it applies the Health Cap effect and immediately clamps health to 20 HP.
 */
public class ItemHealthCapPotion extends Item {

    // Duration in ticks (3 minutes)
    private static final int DURATION = 3600;

    public ItemHealthCapPotion() {
        setTranslationKey("health_cap_potion");
        setRegistryName("herosmp", "health_cap_potion");
        setCreativeTab(CreativeTabs.BREWING);
        setMaxStackSize(1);
    }

    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        return EnumAction.DRINK;
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return 32; // same as vanilla potions
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
            player.addPotionEffect(new PotionEffect(ModPotions.HEALTH_CAP, DURATION, 0, false, true));
            PotionHealthCap.clampHealth(player);
            // Return the empty bottle
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
    public void addInformation(ItemStack stack, World worldIn, List<String> tooltip, net.minecraft.client.util.ITooltipFlag flagIn) {
        tooltip.add(net.minecraft.util.text.TextFormatting.RED + "Prevents health regeneration above 10 hearts");
        tooltip.add(net.minecraft.util.text.TextFormatting.GRAY + "Extra hearts remain as empty hearts");
        tooltip.add(net.minecraft.util.text.TextFormatting.GRAY + "Duration: 3:00");
    }
}
