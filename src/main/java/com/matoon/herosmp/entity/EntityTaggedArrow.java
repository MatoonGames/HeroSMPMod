package com.matoon.herosmp.entity;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * A plain arrow entity that carries a {@link Tag} identifying which HeroSMP
 * arrow item fired it. The actual effect is applied in
 * {@link com.matoon.herosmp.events.ArrowEffectHandler} when this arrow hits a
 * living entity.
 */
public class EntityTaggedArrow extends EntityArrow {

    public enum Tag {
        POWER_SWAP,
        LIFE_LINK,
        SUPE_VIRUS
    }

    private static final String NBT_KEY = "HeroArrowTag";

    private Tag tag = Tag.POWER_SWAP;

    /** Required by EntityRegistry deserialization. */
    public EntityTaggedArrow(World world) {
        super(world);
        pickupStatus = PickupStatus.DISALLOWED;
    }

    public EntityTaggedArrow(World world, EntityLivingBase shooter, Tag tag) {
        super(world, shooter);
        this.tag = tag;
        pickupStatus = PickupStatus.DISALLOWED;
        // Arrows do not deal damage on their own — the effect IS the "damage".
        setDamage(0.0);
        setKnockbackStrength(0);
    }

    public Tag getTag() {
        return tag;
    }

    // Required abstract method — never called since pickup is disallowed.
    @Override
    protected net.minecraft.item.ItemStack getArrowStack() {
        return net.minecraft.item.ItemStack.EMPTY;
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound nbt) {
        super.writeEntityToNBT(nbt);
        nbt.setString(NBT_KEY, tag.name());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound nbt) {
        super.readEntityFromNBT(nbt);
        if (nbt.hasKey(NBT_KEY)) {
            try {
                tag = Tag.valueOf(nbt.getString(NBT_KEY));
            } catch (IllegalArgumentException ignored) {
                tag = Tag.POWER_SWAP;
            }
        }
    }
}
