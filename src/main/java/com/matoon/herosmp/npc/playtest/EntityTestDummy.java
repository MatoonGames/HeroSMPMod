package com.matoon.herosmp.npc.playtest;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.Locale;

/**
 * Training dummy for kit playtests. It can't die or be knocked back; every hit shows the damage it
 * actually took (after armor, enchantments, potions...) above its head and in the attacker's action
 * bar, along with a running total that resets after a few seconds without hits.
 */
public class EntityTestDummy extends EntityLiving {

    private static final float HEALTH = 1000f;
    private static final int RESET_TICKS = 80;

    private String label = "Test Dummy";
    private float total;
    private int hits;
    private int ticksSinceHit = RESET_TICKS;

    public EntityTestDummy(World world) {
        super(world);
        setSize(0.6f, 1.95f);
        setNoAI(true);
        enablePersistence();
        setAlwaysRenderNameTag(true);
        refreshName();
    }

    public EntityTestDummy(World world, String label) {
        this(world);
        this.label = label;
        refreshName();
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(HEALTH);
        getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
    }

    /** Measures what the hit really did (after armor etc.), then heals so the dummy never dies. */
    @Override
    protected void damageEntity(DamageSource source, float amount) {
        float before = getHealth() + getAbsorptionAmount();
        super.damageEntity(source, amount);
        float dealt = before - (getHealth() + getAbsorptionAmount());
        setHealth(getMaxHealth());
        if (dealt <= 0f || world.isRemote) return;

        if (ticksSinceHit >= RESET_TICKS) {
            total = 0f;
            hits = 0;
        }
        total += dealt;
        hits++;
        ticksSinceHit = 0;
        setCustomNameTag("§c-" + fmt(dealt) + " §7(" + fmt(dealt / 2f) + " hearts)   §fTotal " + fmt(total));

        Entity attacker = source.getTrueSource();
        if (attacker instanceof EntityPlayerMP) {
            ((EntityPlayerMP) attacker).sendStatusMessage(new TextComponentString(
                    "§c" + fmt(dealt) + " damage §7to " + label
                            + "   §8|   §7total §f" + fmt(total) + " §8(" + hits + (hits == 1 ? " hit)" : " hits)")), true);
        }
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (!world.isRemote && ticksSinceHit < RESET_TICKS && ++ticksSinceHit == RESET_TICKS) refreshName();
    }

    private void refreshName() {
        setCustomNameTag("§e" + label + " §7(hit me!)");
    }

    private static String fmt(float v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    // Stays exactly where it was placed.
    @Override public void knockBack(Entity attacker, float strength, double x, double z) {}
    @Override public boolean canBePushed() { return false; }
    @Override protected void collideWithEntity(Entity entity) {}
    @Override protected boolean canDespawn() { return false; }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setString("DummyLabel", label);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        if (tag.hasKey("DummyLabel")) label = tag.getString("DummyLabel");
        refreshName();
    }
}
