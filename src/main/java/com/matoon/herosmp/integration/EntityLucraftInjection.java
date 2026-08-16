package com.matoon.herosmp.integration;

import io.netty.buffer.ByteBuf;
import lucraft.mods.lucraftcore.utilities.items.ItemInjection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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
public class EntityLucraftInjection extends Entity implements IEntityAdditionalSpawnData {

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

    private static final DataParameter<Boolean> LOCKED =
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

    /** Ten checks per second remain responsive for fast players without per-tick scans. */
    private static final int PROXIMITY_CHECK_INTERVAL = 2;

    // -------------------------------------------------------------------------
    // Server-side state (not synced — event handler reads these on same thread)
    // -------------------------------------------------------------------------

    private int fadeTick = 0;

    /** UUID of the player who claimed this injection; null = unclaimed. */
    private UUID claimedBy = null;

    private static final List<ItemStack> RANDOM_INJECTIONS = new ArrayList<ItemStack>();

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public EntityLucraftInjection(World world) {
        super(world);
        // Matches the large rendered vial and makes right-click ray tracing reliable.
        // noClip keeps this selection volume from physically obstructing players.
        setSize(2.0F, 2.75F);
        noClip  = true;
        motionX = motionY = motionZ = 0;
    }

    public EntityLucraftInjection(World world, double x, double y, double z, ItemStack injectionStack) {
        this(world, x, y, z, injectionStack, false);
    }

    public EntityLucraftInjection(World world, double x, double y, double z,
                                  ItemStack injectionStack, boolean locked) {
        this(world);
        setPosition(x, y, z);
        dataManager.set(INJECTION_STACK, injectionStack.copy());
        dataManager.set(LOCKED, locked);
        // Sync the power display name so the renderer can draw the name-tag.
        if (!injectionStack.isEmpty()) {
            ItemInjection.Injection inj = ItemInjection.getInjection(injectionStack);
            if (inj != null) dataManager.set(DISPLAY_NAME, inj.getDisplayName());
        }
    }

    /**
     * Finds a usable pickup position in a terrain column. Heightmaps can point at
     * snow, foliage, or other non-solid overlays, so search downward for the actual
     * supporting block and allow replaceable blocks in the pickup's space.
     */
    public static BlockPos findSurfaceSpawn(WorldServer world, int x, int z) {
        int startY = Math.min(world.getActualHeight() - 1, world.getHeight(x, z) + 2);
        boolean passedLiquid = false;

        for (int y = startY; y > 0; y--) {
            BlockPos supportPos = new BlockPos(x, y, z);
            net.minecraft.block.state.IBlockState support = world.getBlockState(supportPos);
            net.minecraft.block.material.Material material = support.getMaterial();

            if (material.isLiquid()) {
                passedLiquid = true;
                continue;
            }
            if (!material.isSolid()) continue;
            if (passedLiquid) return null; // do not place a pickup underwater

            BlockPos pickupPos = supportPos.up();
            net.minecraft.block.state.IBlockState feet = world.getBlockState(pickupPos);
            net.minecraft.block.state.IBlockState head = world.getBlockState(pickupPos.up());
            boolean feetClear = world.isAirBlock(pickupPos) || feet.getMaterial().isReplaceable();
            boolean headClear = world.isAirBlock(pickupPos.up()) || head.getMaterial().isReplaceable();
            return feetClear && headClear ? pickupPos : null;
        }
        return null;
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
        dataManager.register(LOCKED,           false);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        motionX = motionY = motionZ = 0;
        prevPosX = posX;
        prevPosY = posY;
        prevPosZ = posZ;

        if (!world.isRemote) {
            if (getInjectionStack().isEmpty()) initializeRandomInjection();
            if (isCollected()) {
                fadeTick++;
                dataManager.set(FADE_TICKS, fadeTick);
                if (fadeTick >= FADE_DURATION) setDead();
            } else if (!isLocked() && (ticksExisted + getEntityId()) % PROXIMITY_CHECK_INTERVAL == 0) {
                checkForCollector();
            }
        }
    }

    @Override public boolean canBeCollidedWith() { return !isDead; }
    @Override public boolean isEntityInvulnerable(net.minecraft.util.DamageSource source) { return true; }

    @Override
    public boolean processInitialInteract(EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (isLocked() && held.getItem() == com.matoon.herosmp.registry.ModItems.POWER_KEY) {
            if (!world.isRemote) {
                setLocked(false);
                consumeInteractionItem(player, held);
                world.playSound(null, posX, posY, posZ,
                        com.matoon.herosmp.registry.ModSounds.UNLOCK_POWER,
                        SoundCategory.PLAYERS, 1.0F, 1.0F);
            }
            return true;
        }
        if (!isLocked() && held.getItem() == com.matoon.herosmp.registry.ModItems.POWER_LOCK) {
            if (!world.isRemote) {
                setLocked(true);
                consumeInteractionItem(player, held);
            }
            return true;
        }
        return false;
    }

    private static void consumeInteractionItem(EntityPlayer player, ItemStack held) {
        if (!player.capabilities.isCreativeMode) held.shrink(1);
    }

    /** Initializes entities created by the spawn egg with a random Lucraft injection. */
    public void initializeRandomInjection() {
        if (RANDOM_INJECTIONS.isEmpty()) {
            Item item = net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.getValue(
                    new net.minecraft.util.ResourceLocation("lucraftcore", "injection"));
            if (item instanceof ItemInjection) {
                NonNullList<ItemStack> candidates = NonNullList.create();
                item.getSubItems(CreativeTabs.SEARCH, candidates);
                for (ItemStack candidate : candidates) {
                    if (LucraftInjectionEntry.isValidInjection(candidate)) {
                        RANDOM_INJECTIONS.add(candidate.copy());
                    }
                }
            }
        }
        if (RANDOM_INJECTIONS.isEmpty()) return;
        ItemStack chosen = RANDOM_INJECTIONS.get(
                ThreadLocalRandom.current().nextInt(RANDOM_INJECTIONS.size())).copy();
        dataManager.set(INJECTION_STACK, chosen);
        ItemInjection.Injection injection = ItemInjection.getInjection(chosen);
        dataManager.set(DISPLAY_NAME, injection == null ? "Injection" : injection.getDisplayName());
        dataManager.set(LOCKED, false);
    }

    // -------------------------------------------------------------------------
    // Collection check (name-tag distance is client-local in the renderer)
    // -------------------------------------------------------------------------

    private void checkForCollector() {
        double collectRangeSq = COLLECT_RANGE * COLLECT_RANGE;

        for (EntityPlayer candidate : world.playerEntities) {
            if (!(candidate instanceof EntityPlayerMP) || candidate.isDead) continue;
            EntityPlayerMP player = (EntityPlayerMP) candidate;
            double distSq = player.getDistanceSq(posX, posY, posZ);

            // Claim on proximity — first living player wins.
            if (distSq < collectRangeSq) {
                claim(player);
                return; // entity will be killed by event handler; no further processing
            }
        }
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
        setCollected(true);
        // Claims run on the server thread. Complete this one directly instead of scanning
        // every loaded entity in every match dimension on every world tick.
        com.matoon.herosmp.hungergames.events.HungerGamesEvents.claimInjection(this, player);
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
        dataManager.set(LOCKED, compound.getBoolean("Locked"));
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
        compound.setBoolean("Locked", isLocked());
        if (claimedBy != null) compound.setString("ClaimedBy", claimedBy.toString());
    }

    // The renderer cannot draw an injection until it has its ItemStack.  Include the
    // stack in Forge's spawn packet rather than relying on the data-manager update,
    // which can otherwise reach the client after the initial render checks.
    @Override
    public void writeSpawnData(ByteBuf buffer) {
        ByteBufUtils.writeItemStack(buffer, getInjectionStack());
        ByteBufUtils.writeUTF8String(buffer, getPowerName());
        buffer.writeBoolean(isLocked());
    }

    @Override
    public void readSpawnData(ByteBuf buffer) {
        ItemStack stack = ByteBufUtils.readItemStack(buffer);
        dataManager.set(INJECTION_STACK, stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack);
        dataManager.set(DISPLAY_NAME, ByteBufUtils.readUTF8String(buffer));
        dataManager.set(LOCKED, buffer.readBoolean());
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

    public boolean isCollected() { return dataManager.get(COLLECTED); }

    public boolean isLocked() { return dataManager.get(LOCKED); }

    public void setLocked(boolean locked) { dataManager.set(LOCKED, locked); }

    public void setCollected(boolean collected) {
        dataManager.set(COLLECTED, collected);
        if (collected) { fadeTick = 0; dataManager.set(FADE_TICKS, 0); }
    }

    /** Ticks elapsed since collection. Synced — use in renderer for fade progress. */
    public int getFadeTicks() { return dataManager.get(FADE_TICKS); }
}
