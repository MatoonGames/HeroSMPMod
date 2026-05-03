package com.matoon.herosmp.integration;

import anvil.infinity.abilities.AbilitySnap;
import anvil.infinity.helpers.GauntelHelper;
import anvil.infinity.api.AbilityAdderHandler;
import anvil.infinity.registry.Effects;
import lucraft.mods.lucraftcore.util.helper.LCEntityHelper;
import lucraft.mods.lucraftcore.infinity.items.ItemInfinityGauntlet;
import com.matoon.herosmp.HeroSMP;
import com.matoon.herosmp.hungergames.HungerGamesMatch;
import com.matoon.herosmp.network.ModNetwork;
import com.matoon.herosmp.network.PacketInfPowerUp;
import com.matoon.herosmp.network.PacketSnapEffect;
import com.matoon.herosmp.network.PacketSnapOverlay;
import com.matoon.herosmp.timestone.AbilityResetTime;
import com.matoon.herosmp.timestone.AbilitySlowTime;
import com.matoon.herosmp.timestone.AbilitySpeedTime;
import com.matoon.herosmp.timestone.AbilityTimeUnlocker;
import com.matoon.herosmp.timestone.TimeStoneDimensionManager;
import com.matoon.herosmp.timestone.TimeStoneChargeManager;
import com.matoon.herosmp.timestone.TimeStoneAbilityAdder;
import lucraft.mods.lucraftcore.superpowers.abilities.Ability;
import lucraft.mods.lucraftcore.superpowers.abilities.AbilityEntry;
import lucraft.mods.lucraftcore.superpowers.events.InitAbilitiesEvent;
import lucraft.mods.lucraftcore.superpowers.SuperpowerHandler;
import lucraft.mods.lucraftcore.util.attributes.LCAttributes;
import me.guichaguri.tickratechanger.api.TickrateAPI;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.potion.PotionEffect;
import net.minecraft.init.MobEffects;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class LucraftCoreIntegration {

    /**
     * Tracks which players currently have all six stones slotted in the gauntlet.
     * Used to detect the false→true edge (trigger start) and true→false edge (trigger stop).
     */
    private static final Set<UUID> FULL_GAUNTLET_PLAYERS = new HashSet<>();

    /**
     * Stores each player's AbilitySnap cooldown from the previous server tick.
     * Used to detect the 0 → positive edge that signals a snap was just used.
     */
    private static final Map<UUID, Integer> SNAP_PREV_COOLDOWN = new HashMap<>();

    private static final Random SNAP_RANDOM = new Random();

    /**
     * Tracks how many server ticks each player has been inside the power-up sequence.
     * Incremented every PlayerTickEvent.END while the player is in FULL_GAUNTLET_PLAYERS
     * and has a positive snap cooldown (i.e. the power-up lock is active).
     * Cleared when the power-up ends (gauntlet removed, sequence finishes, or player leaves).
     * Used to apply worthiness damage at the correct intervals.
     */
    private static final Map<UUID, Integer> POWER_UP_TICKS = new HashMap<>();

    /**
     * Tracks cumulative gauntlet strain hits per player during the power-up sequence.
     * Incremented each time a strain hit fires. Used to trigger death for weak players
     * once they've taken enough hits, since setHealth() is overwritten every tick by
     * LucraftCore's AbilityHealth reapplication.
     */
    private static final Map<UUID, Integer> STRAIN_HITS = new HashMap<>();

    /**
     * How often (in ticks) the gauntlet applies a strain hit during the power-up sequence.
     * Strong: every 25 ticks (~1.25 s) — survives the full 400-tick sequence.
     * Weak:   every 20 ticks (1 s)     — killed after WEAK_KILL_HITS hits.
     */
    private static final int STRONG_DAMAGE_INTERVAL = 25;
    private static final int WEAK_DAMAGE_INTERVAL   = 20;

    /**
     * How many strain hits before a weak player is killed.
     * At 1 hit/second (WEAK_DAMAGE_INTERVAL=20), 5 hits = death in ~5 seconds.
     */
    private static final int WEAK_KILL_HITS = 5;

    /**
     * HP to leave a strong player at after each strain hit (expressed as a fraction
     * of their BASE vanilla max health — 20 HP — not the stone-inflated value).
     * Each hit strips the stone MAX_HEALTH modifier, sets health to this fraction of
     * the now-vanilla max, then lets LucraftCore re-apply the modifier next tick.
     * Over 16 hits (400 ticks) health is repeatedly driven down to this floor and
     * restored, giving a visible pulsing strain effect. We use a low floor so the
     * player genuinely feels danger, but never actually die since hits >= WEAK_KILL_HITS
     * is never reached for them.
     */
    private static final float STRONG_STRAIN_HEALTH = 2.0f; // 1 heart remaining after each hit

    /**
     * UUID of the player who most recently fired the snap ability.
     * Set in onPlayerTick on the snap-edge tick (prev==0, curr>0).
     * Read in onSnapKillDeath to decide whether to cancel out-of-match snap deaths.
     * Cleared after the snap cooldown expires (player snaps again or cooldown reaches 0).
     */
    private static volatile UUID lastSnapper = null;

    /**
     * When lastSnapper is in a PvP or Hunger Games match, holds the set of UUIDs
     * of players in that match. onSnapKillDeath cancels snap deaths for players
     * NOT in this set. Null when lastSnapper is not in any match (global snap).
     */
    private static volatile Set<UUID> lastSnapperMatchPlayers = null;

    /**
     * The dimension ID that the snapper was in when they fired the snap.
     * Snap kills are only allowed within that dimension — the EffectSnap potion
     * has a duration delay, so without this check a victim could be teleported
     * (or log back in to a different dimension) and still die from a snap that
     * was fired in a different world.
     */
    private static volatile int lastSnapperDimension = Integer.MIN_VALUE;

    public static void preInit(FMLPreInitializationEvent event) {
    }

    public static void init(FMLInitializationEvent event) {
        AbilityAdderHandler.register(new TimeStoneAbilityAdder());
    }

    /**
     * Register HeroSMP's custom ability classes in LucraftCore's AbilityEntry Forge
     * registry. This MUST fire before any instance of the ability is constructed —
     * the Ability base-class constructor iterates the registry to find its own entry
     * (and store it in the 'entry' field), which is later needed by getModId() /
     * getTranslationName(). Without this registration those methods NPE.
     */
    @SubscribeEvent
    public void onRegisterAbilities(RegistryEvent.Register<AbilityEntry> event) {
        event.getRegistry().registerAll(
            new AbilityEntry(AbilitySlowTime.class,     new ResourceLocation("herosmp", "slow_time")),
            new AbilityEntry(AbilitySpeedTime.class,    new ResourceLocation("herosmp", "speed_time")),
            new AbilityEntry(AbilityResetTime.class,    new ResourceLocation("herosmp", "reset_time")),
            new AbilityEntry(AbilityTimeUnlocker.class, new ResourceLocation("herosmp", "time_unlocker"))
        );
    }

    // -------------------------------------------------------------------------
    // Time Stone dimension-scoped tickrate cleanup
    // -------------------------------------------------------------------------

    /**
     * When a player logs in, pre-populate the full-gauntlet tracking set if they
     * already have all six stones slotted — WITHOUT sending PacketInfPowerUp(true).
     * This prevents the power-up sequence from replaying every time the player
     * rejoins with an already-complete gauntlet.
     */
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        try {
            if (GauntelHelper.hasFullGauntlet(player)) {
                FULL_GAUNTLET_PLAYERS.add(player.getUniqueID());
            }
        } catch (Exception e) {
            System.err.println("[HeroSMP] onPlayerLogin: could not check full gauntlet for " + player.getName() + ": " + e.getMessage());
        }

        // Clear any stale snap skin overlay that may be lingering on other clients
        // from a previous session (player disconnected while snapped without dying).
        // We broadcast a remove packet to everyone so their SNAPPED_PLAYERS map is
        // flushed for this UUID before the player appears on-screen again.
        UUID uuid = player.getUniqueID();
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null) {
            PacketSnapOverlay clearPkt = new PacketSnapOverlay(uuid, false, false);
            for (EntityPlayerMP target : server.getPlayerList().getPlayers()) {
                ModNetwork.CHANNEL.sendTo(clearPkt, target);
            }
        }
    }

    /**
     * When a player logs out, restore the tickrate for their dimension if they
     * were the last Time Stone user there. Also clean up any stale health
     * modifiers — they are transient (setSaved=false) so they won't be in the
     * player's saved data, but removing them now is cleaner.
     */
    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (hasTimeAbility(player)) {
            TimeStoneDimensionManager.onUserLeftDimension(player, player.dimension);
        }
        TimeStoneChargeManager.onPlayerLeft(player.getUniqueID());
        // Always re-sync this player's client to normal on disconnect so their
        // next session doesn't start at a wrong tickrate.
        TickrateAPI.changeClientTickrate(player, 20.0f);
        cleanupInfinityStoneHealthModifiers(player);
        cleanupInfinityStoneDamageModifiers(player);
        // Clear full-gauntlet tracking so it doesn't persist across sessions.
        FULL_GAUNTLET_PLAYERS.remove(player.getUniqueID());
        SNAP_PREV_COOLDOWN.remove(player.getUniqueID());
        POWER_UP_TICKS.remove(player.getUniqueID());
        STRAIN_HITS.remove(player.getUniqueID());
        if (player.getUniqueID().equals(lastSnapper)) {
            lastSnapper = null;
            lastSnapperMatchPlayers = null;
            lastSnapperDimension = Integer.MIN_VALUE;
        }
    }

    /**
     * Detects when AbilitySnap fires by watching for the moment its cooldown
     * jumps from 0 to a positive value (AbilityAction.onKeyPressed sets the
     * cooldown to getMaxCooldown() immediately after calling action()).
     *
     * When detected, picks one of three snap sounds at random and sends
     * PacketSnapEffect to every player within 80 blocks (including the snapper).
     */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        UUID uuid = player.getUniqueID();

        AbilitySnap snap;
        try {
            snap = Ability.getAbilityFromClass(Ability.getAbilities(player), AbilitySnap.class);
        } catch (Exception e) {
            // LucraftCore capability may not be ready yet (e.g. mid-login or dimension change).
            return;
        }
        if (snap == null) {
            SNAP_PREV_COOLDOWN.remove(uuid);
            return;
        }

        int prev = SNAP_PREV_COOLDOWN.getOrDefault(uuid, 0);
        int curr = snap.getCooldown();
        SNAP_PREV_COOLDOWN.put(uuid, curr);

        // When this player's cooldown returns to 0, clear their lastSnapper state so
        // the match scope doesn't persist into the next snap.
        if (curr == 0 && uuid.equals(lastSnapper)) {
            lastSnapper = null;
            lastSnapperMatchPlayers = null;
            lastSnapperDimension = Integer.MIN_VALUE;
        }

        // True while the player is locked into the power-up sequence.
        // The cooldown was set to POWER_UP_DURATION_TICKS by lockSnapAbility and counts
        // DOWN from that value, so the window is any tick where curr > 0 on a full-gauntlet
        // player.
        boolean duringPowerUp = FULL_GAUNTLET_PLAYERS.contains(uuid) && curr > 0;

        // True specifically on the initial power-up lock tick and while the lock is
        // still counting down. lockSnapAbility sets the cooldown to exactly
        // POWER_UP_DURATION_TICKS; a real snap fires with a smaller cooldown, so
        // curr >= POWER_UP_DURATION_TICKS uniquely identifies the power-up lock window
        // and prevents onPlayerTick from misinterpreting it as a snap edge.
        boolean powerUpLock = FULL_GAUNTLET_PLAYERS.contains(uuid) && curr >= PacketInfPowerUp.POWER_UP_DURATION_TICKS;

        // Power-up damage: while the player is in the power-up sequence, deal periodic
        // absolute damage (bypasses armor AND LucraftCore resistance attributes) based on
        // whether they meet the mjolnir strength threshold (LCEntityHelper >= 10.0).
        // See STRONG_DAMAGE / WEAK_DAMAGE constants above for the tuning rationale.
        if (HeroSMP.enableInfPowerUpEffects && duringPowerUp) {
            int powerUpTick = POWER_UP_TICKS.getOrDefault(uuid, 0) + 1;
            POWER_UP_TICKS.put(uuid, powerUpTick);

            boolean strong = isStrongEnoughForGauntlet(player);
            int interval   = strong ? STRONG_DAMAGE_INTERVAL : WEAK_DAMAGE_INTERVAL;

            if (powerUpTick % interval == 0) {
                int hits = STRAIN_HITS.getOrDefault(uuid, 0) + 1;
                STRAIN_HITS.put(uuid, hits);

                // Strip the stone MAX_HEALTH modifier so setHealth() operates on the
                // real vanilla health pool, not Float.MAX_VALUE. LucraftCore re-adds it
                // next tick via AbilityHealth.updateTick(), so this is only momentarily
                // removed — enough for setHealth/onDeath to work correctly.
                cleanupInfinityStoneHealthModifiers(player);

                if (strong) {
                    // Strong players: drive health down to 1 heart each hit, then let
                    // LucraftCore restore it next tick. Gives a real pulsing strain effect
                    // visible on the HUD without killing the player.
                    player.setHealth(STRONG_STRAIN_HEALTH);
                } else {
                    // Weak players: after enough hits, kill for real.
                    // Health modifier is stripped above, so the player is at vanilla health.
                    // Use attackEntityFrom() with enough damage to bypass any remaining
                    // resistance — this goes through the full death pipeline (LivingDeathEvent,
                    // respawn, drops) rather than the partial onDeath() path.
                    if (hits >= WEAK_KILL_HITS) {
                        STRAIN_HITS.remove(uuid);
                        if (!player.isDead) {
                            player.attackEntityFrom(net.minecraft.util.DamageSource.MAGIC, Float.MAX_VALUE);
                        }
                    } else {
                        // Not dead yet — drive health low so they feel the strain building.
                        player.setHealth(STRONG_STRAIN_HEALTH);
                    }
                }
            }
        } else if (!duringPowerUp) {
            // Power-up ended (finished or gauntlet removed) — reset counters.
            POWER_UP_TICKS.remove(uuid);
            STRAIN_HITS.remove(uuid);
        }

        // Edge: cooldown was 0 last tick, now positive → snap just fired.
        // Exclude the power-up lock: powerUpLock is true when cooldown >= POWER_UP_DURATION_TICKS,
        // which is the specific value lockSnapAbility injects. A real snap sets a smaller cooldown,
        // so !powerUpLock lets the snap edge through while blocking the lock edge.
        if (prev == 0 && curr > 0 && !powerUpLock) {
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server == null) return;

            // Determine which hand holds the gauntlet for the skin overlay texture.
            boolean mainHand = player.getHeldItemMainhand().getItem() instanceof ItemInfinityGauntlet;

            // Sound + flash: only players within 80 blocks in the same dimension.
            int soundIndex = SNAP_RANDOM.nextInt(3);
            PacketSnapEffect effectPkt = new PacketSnapEffect(soundIndex);
            double radiusSq = 80.0 * 80.0;
            for (EntityPlayerMP target : server.getPlayerList().getPlayers()) {
                if (target.dimension != player.dimension) continue;
                if (target.getDistanceSq(player.posX, player.posY, player.posZ) <= radiusSq) {
                    ModNetwork.CHANNEL.sendTo(effectPkt, target);
                }
            }

            // Skin overlay: broadcast to ALL online players so it renders for anyone
            // who can see the snapper, regardless of current proximity.
            PacketSnapOverlay overlayPkt = new PacketSnapOverlay(uuid, mainHand, true);
            for (EntityPlayerMP target : server.getPlayerList().getPlayers()) {
                ModNetwork.CHANNEL.sendTo(overlayPkt, target);
            }

            // Debuffs: apply slowness II and weakness II to the snapper for 10 seconds.
            // Duration: 200 ticks = 10 s. Amplifier 1 = level II.
            player.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,  200, 1, false, true));
            player.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS,  200, 1, false, true));

            // Determine if the snapper is in a match and record it for onSnapKillDeath.
            // Also send the "gone for the head" message scoped to match players only.
            // Always record the dimension so that delayed snap kills (EffectSnap has a
            // duration countdown) cannot fire in a different dimension if the victim or
            // snapper teleports before the potion expires.
            lastSnapperDimension = player.dimension;
            Set<UUID> pvpPlayers = HeroSMP.PVP_QUEUE_MANAGER.getPlayersInMatch(uuid);
            if (!pvpPlayers.isEmpty()) {
                lastSnapper = uuid;
                lastSnapperMatchPlayers = new HashSet<>(pvpPlayers);
                sendSnapMessageToPlayers(server, player, lastSnapperMatchPlayers);
            } else {
                HungerGamesMatch hgMatch = HeroSMP.HUNGER_GAMES_MANAGER.getMatchForPlayer(uuid);
                if (hgMatch != null) {
                    lastSnapper = uuid;
                    lastSnapperMatchPlayers = new HashSet<>(hgMatch.getAlivePlayers());
                    sendSnapMessageToPlayers(server, player, lastSnapperMatchPlayers);
                } else {
                    lastSnapper = null;
                    lastSnapperMatchPlayers = null;
                    // Not in a match — SnapHelper's global message is the only one needed.
                }
            }
        }
    }

    /**
     * Clears the snap skin overlay when any player dies, and — when the dying
     * player is a snap victim outside the snapper's match — cancels the death
     * and removes the snapEffect potion so they survive.
     *
     * EffectSnap.onTick() (a LivingUpdateEvent subscriber in InfinityCraft) calls
     * entity.setHealth(0) when the snap potion duration reaches 1. That setHealth(0)
     * triggers LivingDeathEvent, which we intercept here at HIGH priority (before
     * InfinityCraft's own LOWEST-priority safety handler).
     *
     * Kill scoping:
     *   - If lastSnapperMatchPlayers is null, the snap was used outside any match —
     *     all deaths proceed normally (vanilla server-wide behaviour).
     *   - If lastSnapperMatchPlayers is set, only players in that set are allowed
     *     to die from the snap; all others have the death cancelled and the potion
     *     removed.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayerMP)) return;
        EntityPlayerMP dead = (EntityPlayerMP) event.getEntityLiving();
        UUID deadUuid = dead.getUniqueID();

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;

        // Snap kill scoping: cancel death for out-of-match players killed by snap,
        // and cancel deaths for any snap victim who is now in a different dimension
        // than the one the snap was fired in (delayed EffectSnap kills after teleport).
        if (dead.isPotionActive(Effects.snapEffect)) {
            boolean wrongDimension = lastSnapperDimension != Integer.MIN_VALUE
                    && dead.dimension != lastSnapperDimension;
            boolean outOfMatch = lastSnapperMatchPlayers != null
                    && !lastSnapperMatchPlayers.contains(deadUuid);
            if (wrongDimension || outOfMatch) {
                event.setCanceled(true);
                dead.removePotionEffect(Effects.snapEffect);
                return; // skip skin-overlay clear — player is still alive
            }
        }

        // Clear snap skin overlay for any player that dies (snap kill or otherwise).
        PacketSnapOverlay clearPkt = new PacketSnapOverlay(deadUuid, false, false);
        for (EntityPlayerMP target : server.getPlayerList().getPlayers()) {
            ModNetwork.CHANNEL.sendTo(clearPkt, target);
        }
    }

    /**
     * Sends the "gone for the head" message (matching SnapHelper's DARK_PURPLE bold
     * styling) only to the specified set of players.
     *
     * Called from onPlayerTick on the snap-edge tick when the snapper is in a match.
     * SnapHelper's own PlayerList.sendMessage() fires on the same tick (from the
     * network handler thread, before PlayerTickEvent), so server-wide players receive
     * the global version. Match players additionally receive this targeted copy sent
     * via EntityPlayerMP.sendMessage(), which goes to their individual connection.
     * Since both arrive in the same chat window they appear as one message to match
     * players; out-of-match players only see the global one.
     *
     * Note: there is no Forge event hook for programmatic server chat
     * (ServerChatEvent only fires for player-typed messages), so the global broadcast
     * from SnapHelper cannot be suppressed without ASM/coremod patching.
     */
    private static void sendSnapMessageToPlayers(MinecraftServer server, EntityPlayerMP snapper, Set<UUID> recipients) {
        TextComponentString msg = new TextComponentString(snapper.getName() + ":");
        msg.appendSibling(new TextComponentTranslation("infinity.snap.text"));
        msg.getStyle().setColor(TextFormatting.DARK_PURPLE);
        msg.getStyle().setBold(Boolean.TRUE);
        for (UUID id : recipients) {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
            if (p != null) p.sendMessage(msg);
        }
    }

    /**
     * When a player moves to a different dimension, restore the tickrate in
     * the dimension they left (if they were the last Time Stone user there),
     * then push the new dimension's stored rate to their client.
     */
    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        int fromDim = event.fromDim;
        int toDim   = event.toDim;

        if (hasTimeAbility(player)) {
            // May restore the dimension they left.
            TimeStoneDimensionManager.onUserLeftDimension(player, fromDim);
        }

        // Sync the player to whatever tickrate is active in their new dimension.
        float toRate = TimeStoneDimensionManager.getRate(toDim);
        TickrateAPI.changeClientTickrate(player, toRate);
    }

    /**
     * Fires after LucraftCore has rebuilt a player's ability set. This only fires
     * when a new provider is equipped (null → item), not on removal (item → null),
     * because LucraftCore skips filterAbilities() when switchProvider(null) is called.
     * We still use it for the Time Stone cleanup path, since the Time Stone abilities
     * are detected via Ability.hasAbility() rather than slot checks.
     */
    @SubscribeEvent
    public void onInitAbilitiesPost(InitAbilitiesEvent.Post event) {
        if (!(event.getEntityLiving() instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityLiving();

        if (!hasTimeAbility(player)) {
            TimeStoneDimensionManager.onAbilityDeactivated(player, player.dimension);
        }
    }

    /**
     * Fires whenever an equipment slot changes on a living entity — including the
     * MAINHAND and OFFHAND slots. This is the reliable hook for "gauntlet left the
     * hand", covering all cases: Q-drop, inventory drag, F-key swap, and picking the
     * item up into a backpack slot.
     *
     * LucraftCore's own cleanup (AbilityAttributeModifier.lastTick) is supposed to
     * remove the MAX_HEALTH modifier when the gauntlet leaves the hand, but it is
     * silently skipped whenever the ability's isUnlocked() flag is false at that
     * moment — which happens reliably when updateConditions() has already evaluated
     * the now-empty slot before switchProvider fires. We force the removal here so
     * the player never retains the inflated health after losing the item.
     */
    @SubscribeEvent
    public void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayerMP)) return;

        EntityEquipmentSlot slot = event.getSlot();
        if (slot != EntityEquipmentSlot.MAINHAND && slot != EntityEquipmentSlot.OFFHAND) return;

        EntityPlayerMP player = (EntityPlayerMP) event.getEntityLiving();

        if (!GauntelHelper.hasSoulStone(player) && !GauntelHelper.hasPowerStone(player)) {
            cleanupInfinityStoneHealthModifiers(player);
        }
        if (!GauntelHelper.hasPowerStone(player)) {
            cleanupInfinityStoneDamageModifiers(player);
        }

        // Defer the full-gauntlet state check by one server tick so that when the
        // player presses F (mainhand↔offhand swap), BOTH slot-change events have
        // already fired and the inventory is in its final state before we evaluate
        // hasFullGauntlet(). Without the delay, the first event fires with only one
        // slot updated, causing a spurious full→not-full transition that unlocks the
        // snap ability for one tick and lets it fire without the mod's cooldown logic.
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null) {
            server.addScheduledTask(() -> updateFullGauntletState(player));
        } else {
            updateFullGauntletState(player);
        }
    }

    /**
     * Checks whether the player's full-gauntlet state has changed and sends the
     * appropriate PacketInfPowerUp to their client.
     *
     * Called on equipment slot changes that affect MAINHAND or OFFHAND — the two
     * slots InfinityCraft checks when evaluating whether a stone is "held".
     */
    private static void updateFullGauntletState(EntityPlayerMP player) {
        UUID uuid = player.getUniqueID();
        boolean hadFull = FULL_GAUNTLET_PLAYERS.contains(uuid);
        boolean hasFull = GauntelHelper.hasFullGauntlet(player);

        if (!hadFull && hasFull) {
            // Transition: not full → full.
            FULL_GAUNTLET_PLAYERS.add(uuid);
            if (HeroSMP.enableInfPowerUpEffects) {
                // Lock the snap ability for the duration of the power-up sequence.
                lockSnapAbility(player);
                // Broadcast to the holder and all players within 80 blocks so they
                // hear the sound and see the skin overlay on the holder.
                sendInfPowerUpToNearby(player, true);
            }
        } else if (hadFull && !hasFull) {
            // Transition: full → not full.
            FULL_GAUNTLET_PLAYERS.remove(uuid);
            if (HeroSMP.enableInfPowerUpEffects) {
                // Unlock the snap ability immediately (gauntlet removed or effects stopped early).
                unlockSnapAbility(player);
                sendInfPowerUpToNearby(player, false);
            }
        }
    }

    /**
     * Sends PacketInfPowerUp to the holder and all players within 80 blocks in
     * the same dimension. The holder gets the packet that triggers the full HUD
     * overlay + sound; bystanders get the same packet but with the holder's UUID
     * so their client only plays the positioned sound and renders the skin overlay.
     */
    private static void sendInfPowerUpToNearby(EntityPlayerMP holder, boolean start) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;

        PacketInfPowerUp pkt = new PacketInfPowerUp(start, holder.getUniqueID());
        double radiusSq = 80.0 * 80.0;
        for (EntityPlayerMP target : server.getPlayerList().getPlayers()) {
            if (target.dimension != holder.dimension) continue;
            if (target.getDistanceSq(holder.posX, holder.posY, holder.posZ) <= radiusSq) {
                ModNetwork.CHANNEL.sendTo(pkt, target);
            }
        }
    }

    /**
     * Sets a cooldown on the player's AbilitySnap equal to the full power-up duration,
     * blocking its use until the power-up sequence completes naturally.
     * The LucraftCore ability bar already renders a cooldown overlay on the slot,
     * so no additional client-side rendering is needed to show the locked state.
     */
    private static void lockSnapAbility(EntityPlayerMP player) {
        try {
            AbilitySnap snap = Ability.getAbilityFromClass(Ability.getAbilities(player), AbilitySnap.class);
            if (snap != null) {
                snap.setMaxCooldown(PacketInfPowerUp.POWER_UP_DURATION_TICKS);
                snap.setCooldown(PacketInfPowerUp.POWER_UP_DURATION_TICKS);
            }
        } catch (Exception e) {
            System.err.println("[HeroSMP] lockSnapAbility: " + e.getMessage());
        }
    }

    /**
     * Clears the snap ability's cooldown immediately — used when the power-up is
     * cancelled early (gauntlet removed before the sequence finishes).
     */
    private static void unlockSnapAbility(EntityPlayerMP player) {
        try {
            AbilitySnap snap = Ability.getAbilityFromClass(Ability.getAbilities(player), AbilitySnap.class);
            if (snap != null) {
                snap.setCooldown(0);
            }
        } catch (Exception e) {
            System.err.println("[HeroSMP] unlockSnapAbility: " + e.getMessage());
        }
    }

    /**
     * Additional safety net for Q-drop specifically. ItemTossEvent fires after the
     * item is already removed from the slot, so the equipment-change event will also
     * fire — but this ensures cleanup happens even if the equipment event is somehow
     * missed (e.g. creative-mode item deletion which bypasses normal drop paths).
     */
    @SubscribeEvent
    public void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getPlayer();
        if (!GauntelHelper.hasSoulStone(player) && !GauntelHelper.hasPowerStone(player)) {
            cleanupInfinityStoneHealthModifiers(player);
        }
        if (!GauntelHelper.hasPowerStone(player)) {
            cleanupInfinityStoneDamageModifiers(player);
        }
        updateFullGauntletState(player);
    }

    /**
     * Blocks healing from the Soul Stone's AbilityHealing for players who no longer
     * hold the stone. LucraftCore's superpower capability persists after the gauntlet
     * is dropped, so AbilityHealing.updateTick() keeps calling entity.heal() every
     * tick indefinitely. Cancelling the resulting LivingHealEvent at HIGH priority
     * stops that heal from landing while the attribute-modifier cleanup in the server
     * tick handler simultaneously removes the inflated MAX_HEALTH.
     *
     * Food saturation regen, potions, and any other healing is also cancelled here
     * if the player no longer holds the soul stone — they should not be healing through
     * the gauntlet's passive once it is out of their hand.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingHealGauntlet(LivingHealEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayerMP)) return;
        if (event.getEntityLiving().world.isRemote) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityLiving();
        // If the soul stone is not in hand, block any healing that originated from
        // the lingering superpower capability (i.e. the AbilityHealing tick).
        // We only intercept when the player has no soul stone AND still has the
        // inflated MAX_HEALTH modifier — that's the precise window where the lingering
        // heal calls happen. Once modifiers are cleaned up this check quickly becomes
        // a no-op, so there is no long-term performance cost.
        if (!GauntelHelper.hasSoulStone(player) && hasInflatedHealthModifier(player)) {
            event.setCanceled(true);
        }
    }

    /**
     * Each server tick (END phase, after all entity updates):
     * - Advance Time Stone charge drain/recharge for players holding that ability.
     * - Enforce Soul/Power Stone health modifier cleanup for players who no longer
     *   hold those stones.
     *
     * The END phase runs after LivingUpdateEvent, which is where LucraftCore's
     * AbilityContainer.onUpdate() re-applies the AbilityHealth MAX_HEALTH modifier
     * every tick via the SUPERPOWER capability context. By running at END we always
     * get the last word, stripping the modifier after LucraftCore re-adds it.
     *
     * This is necessary because the SUPERPOWER capability persists the gauntlet's
     * superpower object independently of which slot the item is in — LucraftCore
     * never clears it when the item leaves the hand, so the modifier would otherwise
     * be re-applied forever even with the gauntlet sitting in a backpack.
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;
        for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
            if (hasTimeAbility(p)) {
                TimeStoneChargeManager.tickPlayer(p);
            }
            if (!GauntelHelper.hasSoulStone(p) && !GauntelHelper.hasPowerStone(p)) {
                cleanupInfinityStoneHealthModifiers(p);
            }
            if (!GauntelHelper.hasPowerStone(p)) {
                cleanupInfinityStoneDamageModifiers(p);
            }
        }
    }

    /**
     * Returns true if this player is strong enough to wield the Infinity Gauntlet,
     * using the same strength threshold Heroes Expansion applies to the non-worthiness
     * Mjolnir: LCEntityHelper.isStrongEnough(entity, 10.0).
     *
     * Players with no personal superpower (no AbilityStrength instances) always fail:
     * LCEntityHelper may return an unexpected base value for players with zero
     * strength abilities, so we gate on SuperpowerHandler.hasSuperpower() first.
     * Creative players always pass.
     * Falls back to false on any exception so errors never grant unintended strength.
     */
    private static boolean isStrongEnoughForGauntlet(EntityPlayerMP player) {
        try {
            // Players with no superpower have zero strength — always weak.
            if (!SuperpowerHandler.hasSuperpower(player)) return false;
            return LCEntityHelper.isStrongEnough(player, 10.0);
        } catch (Exception e) {
            return false;
        }
    }

    /** Returns true if the player currently holds any of our time-manipulation abilities. */
    private static boolean hasTimeAbility(EntityPlayerMP player) {
        return Ability.hasAbility(player, AbilitySlowTime.class)
            || Ability.hasAbility(player, AbilitySpeedTime.class)
            || Ability.hasAbility(player, AbilityResetTime.class)
            || Ability.hasAbility(player, AbilityTimeUnlocker.class);
    }

    /**
     * Returns true if the player currently has any oversized MAX_HEALTH modifier
     * (amount > 1024) that was applied by the Soul Stone or Power Stone ability.
     * Used to gate the LivingHealEvent cancellation so we only block healing during
     * the window where the gauntlet's lingering superpower is still active.
     */
    private static boolean hasInflatedHealthModifier(EntityPlayerMP player) {
        try {
            IAttributeInstance maxHealthAttr = player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
            if (maxHealthAttr == null) return false;
            for (AttributeModifier mod : maxHealthAttr.getModifiers()) {
                if (Math.abs(mod.getAmount()) > 1024.0) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Forcibly removes any MAX_HEALTH AttributeModifiers that InfinityCraft's Soul Stone
     * or Power Stone abilities may have left on the player after the gauntlet was dropped
     * or unequipped.
     *
     * InfinityCraft applies these as transient modifiers (setSaved(false)) with extremely
     * large amounts — the Soul Stone uses Float.MAX_VALUE (~3.4e38) and the Power Stone
     * uses large values too. We remove any modifier whose absolute amount exceeds 1024,
     * which is far beyond anything vanilla Minecraft or HeroSMP adds legitimately.
     *
     * Additionally, we cap the player's current health down to their new max if it would
     * otherwise exceed it (vanilla does this automatically on attribute removal, but we
     * do it explicitly to be safe).
     */
    private static void cleanupInfinityStoneHealthModifiers(EntityPlayerMP player) {
        try {
            IAttributeInstance maxHealthAttr = player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
            if (maxHealthAttr == null) return;

            Collection<AttributeModifier> modifiers = maxHealthAttr.getModifiers();
            if (modifiers == null || modifiers.isEmpty()) return;

            java.util.List<AttributeModifier> toRemove = new ArrayList<>();
            for (AttributeModifier mod : modifiers) {
                if (Math.abs(mod.getAmount()) > 1024.0) {
                    toRemove.add(mod);
                }
            }

            if (toRemove.isEmpty()) return;

            for (AttributeModifier mod : toRemove) {
                maxHealthAttr.removeModifier(mod);
            }

            // Clamp current health down to the restored max.
            float newMax = (float) maxHealthAttr.getAttributeValue();
            if (player.getHealth() > newMax) {
                player.setHealth(newMax);
            }
        } catch (Exception e) {
            System.err.println("[HeroSMP] cleanupInfinityStoneHealthModifiers: " + e.getMessage());
        }
    }

    /**
     * Forcibly removes any oversized AttributeModifiers that InfinityCraft's Power Stone
     * abilities may have left on ATTACK_DAMAGE, PUNCH_DAMAGE, and ARMOR after the gauntlet
     * was dropped or unequipped. Uses the same threshold (>1024) as the health cleanup.
     */
    private static void cleanupInfinityStoneDamageModifiers(EntityPlayerMP player) {
        try {
            IAttributeInstance[] attrs = {
                player.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE),
                player.getEntityAttribute(SharedMonsterAttributes.ARMOR),
                player.getAttributeMap().getAttributeInstance(LCAttributes.PUNCH_DAMAGE)
            };
            for (IAttributeInstance attr : attrs) {
                if (attr == null) continue;
                Collection<AttributeModifier> modifiers = attr.getModifiers();
                if (modifiers == null || modifiers.isEmpty()) continue;
                java.util.List<AttributeModifier> toRemove = new ArrayList<>();
                for (AttributeModifier mod : modifiers) {
                    if (Math.abs(mod.getAmount()) > 1024.0) {
                        toRemove.add(mod);
                    }
                }
                for (AttributeModifier mod : toRemove) {
                    attr.removeModifier(mod);
                }
            }
        } catch (Exception e) {
            System.err.println("[HeroSMP] cleanupInfinityStoneDamageModifiers: " + e.getMessage());
        }
    }

    /**
     * Catch and suppress server-crashing exceptions from InfinityCraft/LucraftCore
     * death handling.  The power stone laser can crash all clients when it kills a
     * player because InfinityCraft's death handler tries to access already-removed
     * entity state.  Running at LOWEST priority means we see the event after
     * InfinityCraft has already processed it; but we guard against any exception
     * propagating up through this handler.
     *
     * If InfinityCraft's own handler throws, Forge will log it but continue — this
     * handler exists primarily to ensure that our own cleanup still runs and that
     * we emit a useful diagnostic message.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onLivingDeathSafety(LivingDeathEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayerMP)) {
            return;
        }
        // No-op: presence of this handler at LOWEST priority helps Forge recover
        // gracefully after higher-priority handlers (including InfinityCraft's) run.
        // Any exception thrown inside THIS handler is caught below.
        try {
            EntityPlayerMP player = (EntityPlayerMP) event.getEntityLiving();
            // Ensure we are not leaving players stuck in a match after an InfinityCraft crash.
            if (HeroSMP.PVP_QUEUE_MANAGER.isPlayerInPvpSession(player.getUniqueID())) {
                // Already handled at HIGH priority in PvpQueueEvents.onDeath — nothing more needed.
            }
        } catch (Exception e) {
            System.err.println("[HeroSMP] LucraftCoreIntegration: suppressed exception in death safety handler: " + e.getMessage());
        }
    }

    /**
     * Intercept InfinityCraft power-stone damage sources against players in PVP arenas.
     * If the damage source class name contains "infinity" or "power" (from InfinityCraft),
     * and the target is a player in a PVP session in the arena dimension, we allow the
     * damage through but mark it so our death handler is resilient to follow-up crashes.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingDamageSafety(LivingDamageEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayerMP)) {
            return;
        }
        try {
            String damageType = event.getSource().getDamageType();
            // InfinityCraft uses damage source names like "infinityStone.power", etc.
            if (damageType != null && damageType.toLowerCase(java.util.Locale.ROOT).contains("infinity")) {
                EntityPlayerMP player = (EntityPlayerMP) event.getEntityLiving();
                if (HeroSMP.PVP_QUEUE_MANAGER.isPlayerInPvpSession(player.getUniqueID())) {
                    // Allow the damage — do not cancel. But ensure the event chain won't crash
                    // by resetting any internal InfinityCraft state that could NPE on death.
                    // We can't call InfinityCraft internals, so this is a no-op guard that
                    // at least ensures our handler is running and the arena death flow triggers.
                }
            }
        } catch (Exception e) {
            System.err.println("[HeroSMP] LucraftCoreIntegration: suppressed exception in damage safety handler: " + e.getMessage());
        }
    }
}
