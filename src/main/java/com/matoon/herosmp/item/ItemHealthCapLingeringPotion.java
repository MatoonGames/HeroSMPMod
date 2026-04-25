package com.matoon.herosmp.item;

import com.matoon.herosmp.registry.ModPotions;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionUtils;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/**
 * Lingering "Health Cap" potion.
 * Thrown like a vanilla lingering potion; leaves an area effect cloud on impact.
 */
public class ItemHealthCapLingeringPotion extends Item {

    public ItemHealthCapLingeringPotion() {
        setTranslationKey("health_cap_lingering_potion");
        setRegistryName("herosmp", "health_cap_lingering_potion");
        setCreativeTab(CreativeTabs.BREWING);
        setMaxStackSize(1);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote) {
            world.playSound(null, player.posX, player.posY, player.posZ,
                net.minecraft.init.SoundEvents.ENTITY_SPLASH_POTION_THROW,
                net.minecraft.util.SoundCategory.PLAYERS, 0.5f,
                0.4f / (world.rand.nextFloat() * 0.4f + 0.8f));

            // Build a vanilla LINGERING_POTION ItemStack with our PotionType.
            // EntityPotion checks the item type to decide whether to spawn a cloud.
            ItemStack potionStack = new ItemStack(net.minecraft.init.Items.LINGERING_POTION);
            PotionUtils.addPotionToItemStack(potionStack, ModPotions.HEALTH_CAP_TYPE);

            EntityPotion thrownPotion = new EntityPotion(world, player, potionStack);
            thrownPotion.shoot(player, player.rotationPitch, player.rotationYaw,
                -20.0f, 0.5f, 1.0f);
            world.spawnEntity(thrownPotion);

            if (!player.capabilities.isCreativeMode) {
                stack.shrink(1);
            }
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World worldIn, List<String> tooltip, net.minecraft.client.util.ITooltipFlag flagIn) {
        tooltip.add(net.minecraft.util.text.TextFormatting.RED + "Prevents health regeneration above 10 hearts");
        tooltip.add(net.minecraft.util.text.TextFormatting.GRAY + "Leaves an area effect cloud on impact");
        tooltip.add(net.minecraft.util.text.TextFormatting.GRAY + "Duration: 0:45");
    }
}
