package com.matoon.herosmp.hungergames.events;

import com.matoon.herosmp.HeroSMP;
import com.matoon.herosmp.hungergames.HungerGamesWorldManager;
import com.matoon.herosmp.integration.EntityLucraftInjection;
import com.matoon.herosmp.integration.LucraftInjectionEntry;
import lucraft.mods.lucraftcore.superpowers.SuperpowerHandler;
import lucraft.mods.lucraftcore.superpowers.abilities.Ability;
import lucraft.mods.lucraftcore.superpowers.abilities.supplier.AbilityContainer;
import lucraft.mods.lucraftcore.superpowers.abilities.supplier.AbilityContainerSuperpower;
import lucraft.mods.lucraftcore.utilities.items.ItemInjection;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import java.util.UUID;
import com.matoon.herosmp.hungergames.world.HungerGamesWorldProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Event handler for the Hunger Games system.
 */
public class HungerGamesEvents {

    private static final net.minecraft.util.ResourceLocation INJECTION_PICKUP_ID =
            new net.minecraft.util.ResourceLocation("herosmp", "lucraft_injection");

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.side.isServer()
                && event.world.provider.getDimension() == 0) {
            HeroSMP.HUNGER_GAMES_MANAGER.tick(event.world.getMinecraftServer());
        }
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getEntity();
        // Players waiting in the HG Lobby just respawn there — they are not eliminated.
        if (HeroSMP.HUNGER_GAMES_MANAGER.isPlayerInLobby(player.getUniqueID())) {
            event.setCanceled(true);
            player.setHealth(player.getMaxHealth());
            return;
        }
        HeroSMP.HUNGER_GAMES_MANAGER.handlePlayerDeath(player);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            HeroSMP.HUNGER_GAMES_MANAGER.handlePlayerRespawn((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent event) {
        if (HeroSMP.HUNGER_GAMES_MANAGER.isHGSpectatorBat(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (HeroSMP.HUNGER_GAMES_MANAGER.isHGSpectatorBat(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            HeroSMP.HUNGER_GAMES_MANAGER.handlePlayerLogout((EntityPlayerMP) event.player);
        }
    }

    /**
     * Restore players from HG when they log back in.
     * If they disconnected while in a match or configure session, their overworld
     * state is still stored — teleport them home automatically.
     */
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            HeroSMP.HUNGER_GAMES_MANAGER.handlePlayerLogin(player);
            HeroSMP.HUNGER_GAMES_MANAGER.sendMusicManifest(player);
        }
    }

    /**
     * Intercept chat for pending text inputs (e.g. world border range entry).
     */
    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        if (!(event.getPlayer() instanceof EntityPlayerMP)) return;
        if (HeroSMP.HUNGER_GAMES_MANAGER.handlePlayerChat(
                (EntityPlayerMP) event.getPlayer(), event.getMessage())) {
            event.setCanceled(true);
        }
    }

    /**
     * Handle right-clicking a block with a configure tool.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onInjectionPickupEggUse(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || stack.getItem() != net.minecraft.init.Items.SPAWN_EGG) return;
        net.minecraft.util.ResourceLocation entityId =
                net.minecraft.item.ItemMonsterPlacer.getNamedIdFrom(stack);
        if (!INJECTION_PICKUP_ID.equals(entityId)) return;

        // Vanilla 1.12's ItemMonsterPlacer only creates EntityLiving instances. The
        // pickup is a plain Entity, so spawn this particular registered egg manually.
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);
        if (event.getWorld().isRemote) return;

        BlockPos spawnPos = event.getPos().offset(event.getFace());
        EntityLucraftInjection pickup = new EntityLucraftInjection(event.getWorld());
        pickup.initializeRandomInjection();
        pickup.setPosition(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D);
        if (event.getWorld().spawnEntity(pickup)
                && !event.getEntityPlayer().capabilities.isCreativeMode) {
            stack.shrink(1);
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntityPlayer() instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();

        ItemStack held = player.getHeldItemMainhand();
        String toolType = HungerGamesWorldManager.getConfigToolType(held);
        if (toolType == null) return;
        if (!HeroSMP.HUNGER_GAMES_MANAGER.isInConfigureMode(player.getUniqueID())) return;

        event.setCanceled(true);
        BlockPos pos = event.getPos();
        HeroSMP.HUNGER_GAMES_MANAGER.handleConfigureToolUse(player, toolType, pos);
    }

    /**
     * Handle right-clicking in the air with a configure tool.
     * Cancels item use for all config tools (prevents ender pearl throw, etc.).
     * Only loot_pool and map_center (air) trigger actual actions.
     */
    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntityPlayer() instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();

        ItemStack held = player.getHeldItemMainhand();
        String toolType = HungerGamesWorldManager.getConfigToolType(held);
        if (toolType == null) return;
        if (!HeroSMP.HUNGER_GAMES_MANAGER.isInConfigureMode(player.getUniqueID())) return;

        event.setCanceled(true);
        if ("loot_pool".equals(toolType) || "loot_properties".equals(toolType)
                || "map_center".equals(toolType) || "breakable_blocks".equals(toolType)
                || "injections".equals(toolType) || "injection_properties".equals(toolType)) {
            HeroSMP.HUNGER_GAMES_MANAGER.handleConfigureToolUse(player, toolType, null);
        }
    }

    /**
     * Allow breaking only: blocks placed by players during this round, or blocks
     * in the map's configured breakable-blocks list.
     */
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof EntityPlayerMP)) return;
        UUID id = event.getPlayer().getUniqueID();
        if (!HeroSMP.HUNGER_GAMES_MANAGER.isPlayerInMatch(id)) return;

        BlockPos pos = event.getPos();
        if (!HeroSMP.HUNGER_GAMES_MANAGER.canBreakBlock(id, pos, event.getWorld())) {
            event.setCanceled(true);
        } else {
            HeroSMP.HUNGER_GAMES_MANAGER.trackBlockBroken(id, pos);
        }
    }

    /** Track blocks placed by players during a HG round for player-placed-block breaking. */
    @SubscribeEvent
    public void onBlockPlace(BlockEvent.PlaceEvent event) {
        if (!(event.getPlayer() instanceof EntityPlayerMP)) return;
        HeroSMP.HUNGER_GAMES_MANAGER.trackBlockPlaced(
                event.getPlayer().getUniqueID(), event.getPos());
    }

    /**
     * Prevent players in a Hunger Games match from leaving the HG dimension via
     * dimension-travel items like the Heroes Expansion Tesseract.
     * If they somehow still escape, HungerGamesMatch.tick() will eliminate them.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onDimensionTravel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.getEntity();
        UUID id = player.getUniqueID();
        if (HeroSMP.HUNGER_GAMES_MANAGER.isPlayerInMatch(id)) {
            // Allow travel into the player's assigned HG dimension (initial teleport-in).
            int matchDim = HeroSMP.HUNGER_GAMES_MANAGER.getPlayerMatchDimension(id);
            if (matchDim != 0 && event.getDimension() == matchDim) {
                return;
            }
            event.setCanceled(true);
            player.sendMessage(new TextComponentString(
                TextFormatting.RED + "You cannot leave the Hunger Games dimension during a match!"));
        }
    }

    /**
     * Save loot pool changes when the player closes the loot-pool inventory GUI.
     */
    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntityPlayer() instanceof EntityPlayerMP)) return;
        HeroSMP.HUNGER_GAMES_MANAGER.handleLootInventoryClose(
            (EntityPlayerMP) event.getEntityPlayer(), event.getContainer());
    }

    /**
     * Completes a claimed {@link EntityLucraftInjection} directly on the server thread.
     * When one has been claimed (atomically, by its own onUpdate proximity check),
     * grant the superpower to the claiming player, play effects, and kill the entity.
     *
     * Using {@link EntityLucraftInjection#isClaimed()} / {@link EntityLucraftInjection#getClaimedBy()}
     * instead of the old isCollected() nearest-player search guarantees exactly one
     * player receives the power — the entity already records who claimed it.
     */
    public static void claimInjection(EntityLucraftInjection injection, EntityPlayerMP player) {
        if (injection == null || player == null || injection.isDead || !injection.isClaimed()) return;
        ItemStack stack = injection.getInjectionStack();
        injection.setDead();
        if (LucraftInjectionEntry.isValidInjection(stack)) grantSuperpower(player, stack, player.world);
    }

    /**
     * Intercepts chunk loads in HG match dimensions and wipes any chunk that falls
     * outside the configured border + padding.
     *
     * This is necessary because {@link net.minecraft.world.gen.IChunkGenerator#generateChunk}
     * is only called for chunks that are absent from the region files.  Pre-built map
     * chunks are read directly from disk by {@link net.minecraft.world.chunk.storage.AnvilChunkLoader}
     * before the generator is consulted, so the bounds check in
     * {@link com.matoon.herosmp.hungergames.world.HungerGamesChunkGenerator} would
     * never fire for them.
     *
     * By clearing the block-storage sections here we ensure out-of-bounds disk chunks
     * arrive at the client as solid air — indistinguishable from chunks that were never
     * on disk at all — without preventing the load itself (which would require a much
     * deeper hook).
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.getWorld() == null) return;
        if (!(event.getWorld().provider instanceof HungerGamesWorldProvider)) return;

        HungerGamesWorldProvider provider = (HungerGamesWorldProvider) event.getWorld().provider;
        // Sentinel means no bounds set (configure session or no map center configured).
        if (provider.matchCenterChunkX == Integer.MIN_VALUE) return;

        int cx = event.getChunk().x;
        int cz = event.getChunk().z;
        int limit = provider.matchBorderChunks + HungerGamesWorldProvider.CHUNK_PADDING;

        if (Math.abs(cx - provider.matchCenterChunkX) > limit
                || Math.abs(cz - provider.matchCenterChunkZ) > limit) {
            // Wipe all block-storage sections so the chunk arrives at the client as air.
            net.minecraft.world.chunk.Chunk chunk = event.getChunk();
            ExtendedBlockStorage[] empty = new ExtendedBlockStorage[16];
            chunk.setStorageArrays(empty);
            chunk.generateSkylightMap();
        }
    }

    private static void grantSuperpower(EntityPlayerMP player, ItemStack injStack, net.minecraft.world.World world) {
        try {
            ItemInjection.Injection injection = ItemInjection.getInjection(injStack);
            if (injection == null) {
                System.err.println("[HeroSMP] grantSuperpower: null injection for "
                        + injStack.getDisplayName());
                return;
            }

            // Remove the player's current superpower first so they never have two,
            // and so LucraftCore's hasSuperpower() guard inside inject() doesn't block the grant.
            if (SuperpowerHandler.hasSuperpower(player)) {
                SuperpowerHandler.removeSuperpower(player);
                SuperpowerHandler.syncToPlayer(player);
            }

            // inject() calls giveSuperpower() internally, fires OnGain ability events,
            // and handles capability setup — identical to right-click item use.
            injection.inject(player, injStack.copy());

            // Set the player to the maximum level of this superpower immediately.
            try {
                AbilityContainer container = Ability.getAbilityContainer(
                        Ability.EnumAbilityContext.SUPERPOWER, player);
                if (container instanceof AbilityContainerSuperpower) {
                    AbilityContainerSuperpower spContainer = (AbilityContainerSuperpower) container;
                    int maxLevel = SuperpowerHandler.getSuperpower(player) != null
                            ? SuperpowerHandler.getSuperpower(player).getMaxLevel() : 1;
                    if (maxLevel > 1) {
                        spContainer.setLevel(maxLevel);
                        spContainer.setXP(0);
                        SuperpowerHandler.syncToPlayer(player);
                    }
                }
            } catch (Exception levelEx) {
                System.err.println("[HeroSMP] grantSuperpower: could not set max level for "
                        + player.getName() + ": " + levelEx.getMessage());
            }

            String display = injection.getDisplayName();
            player.sendMessage(new TextComponentString(
                    TextFormatting.LIGHT_PURPLE + "" + TextFormatting.BOLD + "POWER INJECTED! "
                    + TextFormatting.RESET + TextFormatting.GOLD + display));

            world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0F, 0.5F);
            world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0F, 1.2F);

            ((net.minecraft.world.WorldServer) world).spawnParticle(
                    EnumParticleTypes.SPELL_MOB,
                    player.posX, player.posY + 1.0, player.posZ,
                    40, 0.4, 0.6, 0.4, 0.15);
            ((net.minecraft.world.WorldServer) world).spawnParticle(
                    EnumParticleTypes.END_ROD,
                    player.posX, player.posY + 1.0, player.posZ,
                    20, 0.3, 0.5, 0.3, 0.05);

        } catch (Exception e) {
            System.err.println("[HeroSMP] grantSuperpower: exception granting '"
                    + LucraftInjectionEntry.getLucraftId(injStack) + "' to "
                    + player.getName() + ": " + e.getMessage());
        }
    }
}
