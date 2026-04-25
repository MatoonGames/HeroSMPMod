package com.matoon.herosmp.item;

import com.matoon.herosmp.potion.PotionHealthCap;
import com.matoon.herosmp.registry.ModPotions;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.potion.PotionUtils;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/**
 * Splash "Health Cap" potion.
 * Thrown like a vanilla splash potion; applies Health Cap to all nearby entities on impact.
 */
public class ItemHealthCapSplashPotion extends Item {

    // Splash potions apply at 75% duration within the radius
    private static final int DURATION = 3600;
    // Radius matches vanilla splash potions (4 blocks)
    private static final double SPLASH_RADIUS = 4.0;

    public ItemHealthCapSplashPotion() {
        setTranslationKey("health_cap_splash_potion");
        setRegistryName("herosmp", "health_cap_splash_potion");
        setCreativeTab(CreativeTabs.BREWING);
        setMaxStackSize(1);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote) {
            // Build a vanilla-style EntityPotion carrying our PotionType so the impact
            // triggers splash logic.  We wrap this in a custom thrown potion entity.
            world.playSound(null, player.posX, player.posY, player.posZ,
                net.minecraft.init.SoundEvents.ENTITY_SPLASH_POTION_THROW,
                net.minecraft.util.SoundCategory.PLAYERS, 0.5f,
                0.4f / (world.rand.nextFloat() * 0.4f + 0.8f));

            // Create a potion ItemStack with our PotionType for the EntityPotion
            ItemStack potionStack = new ItemStack(net.minecraft.init.Items.SPLASH_POTION);
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
        tooltip.add(net.minecraft.util.text.TextFormatting.GRAY + "Affects nearby entities on impact");
        tooltip.add(net.minecraft.util.text.TextFormatting.GRAY + "Duration: 2:15");
    }
}
