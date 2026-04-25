package com.matoon.herosmp.integration;

import lucraft.mods.lucraftcore.utilities.items.ItemInjection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * A non-physics entity representing a collectible LucraftCore injection vial.
 *
 * Features:
 *  - Renders large (3× scale in {@link RenderLucraftInjection})
 *  - Shows a floating name-tag when a player is within {@link #NAME_TAG_RANGE} blocks;
 *    hides when they move away
 *  - Single-claim: the first player to enter {@link #COLLECT_RANGE} blocks claims it;
 *    the event handler reads {@link #getClaimedBy()} and kills the entity immediately
 *    after granting the power — no other player can trigger it
 */
public class EntityLucraftInjection extends Entity {

    // -------------------------------------------------------------------------
    // DataParameters
    // -------------------------------------------------------------------------

    private static final DataParameter<ItemStack> INJECTION_STACK =
            EntityDataManager.createKey(EntityLucraftInjection.class, DataSerializers.ITEM_STACK);

    private static final DataParameter<Boolean> COLLECTED =
            EntityDataManager.createKey(EntityLucraftInjection.class, DataSerializers.BOOLEAN);

    private static final DataParameter<Integer> FADE_TICKS =
            EntityDataManager.createKey(EntityLucraftInjection.class, DataSerializers.VARINT);

    /** Power display name synced to clients so the renderer can draw the name-tag. */
    private static final DataParameter<String> DISPLAY_NAME =
            EntityDataManager.createKey(EntityLucraftInjection.class, DataSerializers.STRING);

    /** Set server-side; synced via NAME_TAG_VISIBLE so renderer knows to show the tag. */
    private static final DataParameter<Boolean> NAME_TAG_VISIBLE =
            EntityDataManager.createKey(EntityLucraftInjection.class, DataSerializers.BOOLEAN);

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /** Ticks between collection and entity removal (renderer uses this for fade). */
    public static final int FADE_DURATION = 10;

    /** Distance (blocks) within which a player sees the name-tag. */
    public static final double NAME_TAG_RANGE = 6.0;

    /** Distance (blocks) within which a player claims the injection. */
    public static final double COLLECT_RANGE = 1.5;

    // -------------------------------------------------------------------------
    // Server-side state (not synced — event handler reads these on same thread)
    // -------------------------------------------------------------------------

    private int fadeTick = 0;

    /** UUID of the player who claimed this injection; null = unclaimed. */
    private UUID claimedBy = null;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public EntityLucraftInjection(World world) {
        super(world);
        setSize(0.5F, 0.5F);
        noClip  = true;
        motionX = motionY = motionZ = 0;
    }

    public EntityLucraftInjection(World world, double x, double y, double z, ItemStack injectionStack) {
        this(world);
        setPosition(x, y, z);
        dataManager.set(INJECTION_STACK, injectionStack.copy());
        // Sync the power display name so the renderer can draw the name-tag.
        if (!injectionStack.isEmpty()) {
            ItemInjection.Injection inj = ItemInjection.getInjection(injectionStack);
            if (inj != null) dataManager.set(DISPLAY_NAME, inj.getDisplayName());
        }
    }

    // -------------------------------------------------------------------------
    // Entity overrides
    // -------------------------------------------------------------------------

    @Override
    protected void entityInit() {
        dataManager.register(INJECTION_STACK,  ItemStack.EMPTY);
        dataManager.register(COLLECTED,        false);
        dataManager.register(FADE_TICKS,       0);
        dataManager.register(DISPLAY_NAME,     "");
        dataManager.register(NAME_TAG_VISIBLE, false);
    }

    @Override
    public void onUpdate() {
        motionX = motionY = motionZ = 0;
        prevPosX = posX;
        prevPosY = posY;
        prevPosZ = posZ;

        if (!world.isRemote) {
            if (isCollected()) {
                fadeTick++;
                dataManager.set(FADE_TICKS, fadeTick);
                if (fadeTick >= FADE_DURATION) setDead();
            } else {
                updateNameTagVisibility();
            }
        }
    }

    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isEntityInvulnerable(net.minecraft.util.DamageSource source) { return true; }

    // -------------------------------------------------------------------------
    // Name-tag visibility + claim check — single pass over playerEntities
    // -------------------------------------------------------------------------

    private void updateNameTagVisibility() {
        boolean anyInRange = false;
        double collectRangeSq = COLLECT_RANGE * COLLECT_RANGE;
        double nameTagRangeSq = NAME_TAG_RANGE * NAME_TAG_RANGE;

        for (EntityPlayer ep : world.playerEntities) {
            if (ep.isDead) continue;
            double distSq = ep.getDistanceSq(posX, posY, posZ);

            if (distSq < nameTagRangeSq) {
                anyInRange = true;
            }

            // Claim on proximity — first living player wins.
            if (distSq < collectRangeSq && ep instanceof EntityPlayerMP) {
                claim((EntityPlayerMP) ep);
                return; // entity will be killed by event handler; no further processing
            }
        }

        dataManager.set(NAME_TAG_VISIBLE, anyInRange);
    }

    // -------------------------------------------------------------------------
    // Claiming
    // -------------------------------------------------------------------------

    /**
     * Atomically claims this injection for {@code player}.
     * Hides the name-tag, then triggers the fade sequence.
     * The event handler in HungerGamesEvents reads {@link #getClaimedBy()} to know
     * who to grant the power to and calls {@link #setDead()} immediately so no
     * second player can claim it.
     */
    public void claim(EntityPlayerMP player) {
        if (claimedBy != null) return; // already claimed — guard against rapid calls
        claimedBy = player.getUniqueID();
        dataManager.set(NAME_TAG_VISIBLE, false);
        setCollected(true);
    }

    public boolean isClaimed()   { return claimedBy != null; }
    public UUID   getClaimedBy() { return claimedBy; }

    // -------------------------------------------------------------------------
    // NBT persistence
    // -------------------------------------------------------------------------

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        if (compound.hasKey("InjectionStack")) {
            ItemStack stack = new ItemStack(compound.getCompoundTag("InjectionStack"));
            dataManager.set(INJECTION_STACK, stack.isEmpty() ? ItemStack.EMPTY : stack);
        }
        dataManager.set(COLLECTED, compound.getBoolean("Collected"));
        fadeTick = compound.getInteger("FadeTick");
        dataManager.set(FADE_TICKS, fadeTick);
        if (compound.hasKey("ClaimedBy")) {
            try { claimedBy = UUID.fromString(compound.getString("ClaimedBy")); }
            catch (IllegalArgumentException ignored) {}
        }
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        ItemStack stack = getInjectionStack();
        if (!stack.isEmpty()) {
            compound.setTag("InjectionStack", stack.writeToNBT(new NBTTagCompound()));
        }
        compound.setBoolean("Collected", isCollected());
        compound.setInteger("FadeTick",  fadeTick);
        if (claimedBy != null) compound.setString("ClaimedBy", claimedBy.toString());
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public ItemStack getInjectionStack() {
        ItemStack s = dataManager.get(INJECTION_STACK);
        return s == null ? ItemStack.EMPTY : s;
    }

    /** The power's display name, synced to clients for the renderer's name-tag. */
    public String getPowerName()   { return dataManager.get(DISPLAY_NAME); }

    /** True when a player is within {@link #NAME_TAG_RANGE} blocks (server-computed, synced). */
    public boolean isNameTagVisible() { return dataManager.get(NAME_TAG_VISIBLE); }

    public boolean isCollected() { return dataManager.get(COLLECTED); }

    public void setCollected(boolean collected) {
        dataManager.set(COLLECTED, collected);
        if (collected) { fadeTick = 0; dataManager.set(FADE_TICKS, 0); }
    }

    /** Ticks elapsed since collection. Synced — use in renderer for fade progress. */
    public int getFadeTicks() { return dataManager.get(FADE_TICKS); }
}
