package com.matoon.herosmp.hungergames;

import lucraft.mods.lucraftcore.superpowers.SuperpowerHandler;
import com.matoon.herosmp.hungergames.map.HungerGamesLootEntry;
import com.matoon.herosmp.hungergames.music.HungerGamesMusicManager;
import com.matoon.herosmp.integration.EntityLucraftInjection;
import com.matoon.herosmp.integration.LucraftInjectionEntry;
import com.matoon.herosmp.npc.pvp.FixedTeleporter;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketDestroyEntities;
import net.minecraft.network.play.server.SPacketDisplayObjective;
import net.minecraft.network.play.server.SPacketScoreboardObjective;
import net.minecraft.network.play.server.SPacketUpdateScore;
import net.minecraft.network.play.server.SPacketWorldBorder;
import net.minecraft.scoreboard.ScoreCriteria;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.BossInfo;
import net.minecraft.world.BossInfoServer;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.border.WorldBorder;

import java.util.*;

/**
 * Represents an active Hunger Games match instance.
 * Manages game state, scoreboard, world border phases, and player tracking.
 */
public class HungerGamesMatch {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private static final int COUNTDOWN_TICKS   = 5  * 20;
    private static final int GRACE_PERIOD_TICKS = 60 * 20;

    // Phase end thresholds (cumulative elapsed ticks). Assigned in startActive()
    // based on player count: < 8 players → 4/6/8 min phases; ≥ 8 → 8/8/8 min phases.
    private int phase1EndTicks;
    private int phase2EndTicks;
    private int phase3EndTicks;

    // -------------------------------------------------------------------------
    // Match identity
    // -------------------------------------------------------------------------

    private final int    matchId;
    private final int    dimensionId;
    private final String templateName;
    private final int    maxPlayers;

    // -------------------------------------------------------------------------
    // Player tracking
    // -------------------------------------------------------------------------

    private final List<UUID>          playerOrder  = new ArrayList<>();
    private final Set<UUID>           alivePlayers = new HashSet<>();
    private final Set<UUID>           spectators   = new HashSet<>();
    private final Map<UUID, BlockPos> spawnPoints  = new HashMap<>();
    private final Map<UUID, String>   cachedNames  = new HashMap<>();

    // -------------------------------------------------------------------------
    // World config
    // -------------------------------------------------------------------------

    private BlockPos        lobbySpawnPoint;
    private List<ItemStack> lootPhase1    = new ArrayList<>();
    private List<ItemStack> lootPhase2    = new ArrayList<>();
    private List<ItemStack> lootPhase3    = new ArrayList<>();
    private List<ItemStack> lootAllPhases = new ArrayList<>();
    private Set<String>     breakableBlockNames   = new HashSet<>();

    // Per-phase injection pools stored as ItemStacks carrying HGInjection NBT.
    private List<ItemStack> injectionPhase1    = new ArrayList<>();
    private List<ItemStack> injectionPhase2    = new ArrayList<>();
    private List<ItemStack> injectionPhase3    = new ArrayList<>();
    private List<ItemStack> injectionAllPhases = new ArrayList<>();

    // Per-phase global injection count bounds (min/max entities on the map at once).
    // Index: 0=Phase1, 1=Phase2, 2=Phase3, 3=AllPhases (fallback)
    private final int[] injGlobalMin = {0, 0, 0, 3};
    private final int[] injGlobalMax = {0, 0, 0, 8};

    private BlockPos        mapCenter             = null;
    private int             worldBorderStartRange = 0;

    private final Set<BlockPos> playerPlacedBlocks = new HashSet<>();

    // -------------------------------------------------------------------------
    // Phase state
    // -------------------------------------------------------------------------

    private GamePhase phase              = GamePhase.WAITING;
    private int       phaseTicksRemaining = 0;
    private int       totalGameTicks      = 0;

    private int activePhaseElapsed = 0;
    private int activePhase        = 1;

    // -------------------------------------------------------------------------
    // Decorators
    // -------------------------------------------------------------------------

    private BossInfoServer    bossBar;
    private final List<BlockPos>  placedGlassBlocks  = new ArrayList<>();
    private final Map<UUID, UUID> spectatorBatIds    = new HashMap<>();
    private final Random          rand               = new Random();

    // Music
    private HungerGamesMusicManager musicManager     = null;
    private String                  currentMusicPhase = null;
    private String                  currentMusicTrack = null;
    private int                     musicLoopTicker   = 0;
    private static final int MUSIC_LOOP_TICKS = 3600; // 3 minutes

    // Scoreboard — uses a match-local Scoreboard so packets are never sent to the
    // whole server.  We manually push SPacketScoreboardObjective / SPacketUpdateScore
    // / SPacketDisplayObjective only to players in this match.
    private final Scoreboard              localBoard  = new Scoreboard();
    private ScoreObjective                boardObjective;
    private String                        boardObjName;
    private int                           sbTicker  = 0;
    private final Map<String, Integer>    lastSbState = new LinkedHashMap<>();

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public HungerGamesMatch(int matchId, int dimensionId, String templateName, int maxPlayers) {
        this.matchId      = matchId;
        this.dimensionId  = dimensionId;
        this.templateName = templateName;
        this.maxPlayers   = maxPlayers;
    }

    // -------------------------------------------------------------------------
    // Setup setters
    // -------------------------------------------------------------------------

    public void setMusicManager(HungerGamesMusicManager mgr) { this.musicManager = mgr; }

    public void setLobbySpawnPoint(BlockPos pos)    { this.lobbySpawnPoint = pos; }
    public void setLootPools(List<ItemStack> p1, List<ItemStack> p2, List<ItemStack> p3, List<ItemStack> all) {
        lootPhase1    = new ArrayList<>(p1);
        lootPhase2    = new ArrayList<>(p2);
        lootPhase3    = new ArrayList<>(p3);
        lootAllPhases = new ArrayList<>(all);
    }
    public void setMapCenter(BlockPos center)       { this.mapCenter = center; }
    public void setWorldBorderStartRange(int range) { this.worldBorderStartRange = range; }
    public void setBreakableBlocks(List<ItemStack> items) {
        breakableBlockNames.clear();
        for (ItemStack stack : items) {
            if (!stack.isEmpty() && stack.getItem() instanceof ItemBlock) {
                Block block = ((ItemBlock) stack.getItem()).getBlock();
                if (block != Blocks.AIR && block.getRegistryName() != null)
                    breakableBlockNames.add(block.getRegistryName().toString());
            }
        }
    }

    public boolean isBreakableBlock(Block block) {
        return block.getRegistryName() != null && breakableBlockNames.contains(block.getRegistryName().toString());
    }

    public void setInjectionPhasePools(List<ItemStack> p1, List<ItemStack> p2,
                                        List<ItemStack> p3, List<ItemStack> all) {
        injectionPhase1    = new ArrayList<>(p1);
        injectionPhase2    = new ArrayList<>(p2);
        injectionPhase3    = new ArrayList<>(p3);
        injectionAllPhases = new ArrayList<>(all);
    }

    public void setInjectionRanges(int minP1, int maxP1, int minP2, int maxP2,
                                    int minP3, int maxP3, int minAll, int maxAll) {
        injGlobalMin[0] = minP1;  injGlobalMax[0] = maxP1;
        injGlobalMin[1] = minP2;  injGlobalMax[1] = maxP2;
        injGlobalMin[2] = minP3;  injGlobalMax[2] = maxP3;
        injGlobalMin[3] = minAll; injGlobalMax[3] = maxAll;
    }

    /** Legacy setter — kept for API compatibility; callers should use setInjectionPhasePools directly. */
    public void setInjectionPool(List<ItemStack> validInjectionStacks) {
        injectionAllPhases.clear();
        for (ItemStack s : validInjectionStacks) {
            if (LucraftInjectionEntry.isValidInjection(s)) injectionAllPhases.add(s.copy());
        }
        injectionPhase1.clear();
        injectionPhase2.clear();
        injectionPhase3.clear();
    }
    public void addPlayerPlacedBlock(BlockPos pos) { playerPlacedBlocks.add(pos); }
    public void removePlayerPlacedBlock(BlockPos pos) { playerPlacedBlocks.remove(pos); }
    public boolean isPlayerPlacedBlock(BlockPos pos)  { return playerPlacedBlocks.contains(pos); }

    // -------------------------------------------------------------------------
    // Player management
    // -------------------------------------------------------------------------

    public void addPlayer(UUID id, BlockPos spawnPoint) {
        playerOrder.add(id);
        alivePlayers.add(id);
        spawnPoints.put(id, spawnPoint);
    }

    public void removePlayer(UUID id)      { alivePlayers.remove(id); }
    /** Fully removes a player who is voluntarily leaving — clears both tracking lists. */
    public void fullyRemovePlayer(UUID id) { alivePlayers.remove(id); playerOrder.remove(id); }
    public void addSpectator(UUID id)      { spectators.add(id); }
    public void removeSpectator(UUID id)   { spectators.remove(id); }

    // -------------------------------------------------------------------------
    // Match lifecycle
    // -------------------------------------------------------------------------

    public void startMatch(MinecraftServer server) {
        phase = GamePhase.LOBBY;
        phaseTicksRemaining = 30 * 20;

        for (UUID id : playerOrder) {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
            if (p != null) {
                cachedNames.put(id, p.getName());
                p.setEntityInvulnerable(true);
            }
        }

        bossBar = new BossInfoServer(
            new TextComponentString(TextFormatting.GOLD + "Hunger Games - Match #" + matchId),
            BossInfo.Color.RED, BossInfo.Overlay.NOTCHED_10);
        for (UUID id : playerOrder) {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
            if (p != null) bossBar.addPlayer(p);
        }

        initScoreboard(server);
        sendPhaseMusic(server, HungerGamesMusicManager.LOBBY);
        broadcastMessage(server, TextFormatting.GREEN + "Match starting! Waiting in lobby...");
    }

    public void tick(MinecraftServer server) {
        totalGameTicks++;

        switch (phase) {
            case LOBBY:        tickLobby(server);       break;
            case COUNTDOWN:    tickCountdown(server);   break;
            case GRACE_PERIOD: tickGracePeriod(server); break;
            case ACTIVE:       tickActive(server);      break;
            case ENDING:       tickEnding(server);      break;
        }

        tickSpectatorBats(server);
        updateBossBar();

        // Forfeit any alive player who has escaped to a different dimension (e.g. Tesseract).
        if (phase == GamePhase.GRACE_PERIOD || phase == GamePhase.ACTIVE) {
            for (UUID id : new ArrayList<>(alivePlayers)) {
                EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
                if (p != null && p.dimension != dimensionId) {
                    // Player left the HG dimension — eliminate them.
                    p.sendMessage(new net.minecraft.util.text.TextComponentString(
                        net.minecraft.util.text.TextFormatting.RED + "You left the Hunger Games dimension and were eliminated!"));
                    removePlayer(id);
                }
            }
        }

        if (musicManager != null && currentMusicPhase != null && currentMusicTrack != null) {
            if (++musicLoopTicker >= MUSIC_LOOP_TICKS) {
                musicLoopTicker = 0;
                currentMusicTrack = musicManager.pickTrack(currentMusicPhase);
                if (currentMusicTrack != null)
                    for (EntityPlayerMP p : getAllPresentPlayers(server))
                        musicManager.sendSpecificTrack(currentMusicPhase, currentMusicTrack, p);
            }
        }

        if (++sbTicker >= 20) {
            sbTicker = 0;
            syncScoreboard(server);
        }
    }

    // -------------------------------------------------------------------------
    // Phase tick methods
    // -------------------------------------------------------------------------

    private void tickLobby(MinecraftServer server) {
        phaseTicksRemaining--;
        if (phaseTicksRemaining % 20 == 0) {
            int s = phaseTicksRemaining / 20;
            if (s <= 10 || s % 5 == 0)
                broadcastMessage(server, TextFormatting.YELLOW + "Game starts in " + s + "s...");
        }
        if (phaseTicksRemaining <= 0) startCountdown(server);
    }

    private void startCountdown(MinecraftServer server) {
        phase = GamePhase.COUNTDOWN;
        phaseTicksRemaining = COUNTDOWN_TICKS;

        WorldServer hgWorld = server.getWorld(dimensionId);
        for (UUID id : new ArrayList<>(alivePlayers)) {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
            BlockPos spawn = spawnPoints.get(id);
            if (p != null && spawn != null && p.connection != null) {
                p.connection.setPlayerLocation(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0, 0);
                if (hgWorld != null) placeGlassBarrier(hgWorld, spawn);
            }
        }
        broadcastMessage(server, TextFormatting.YELLOW + "Get ready! Game starts in " + (COUNTDOWN_TICKS / 20) + "...");
    }

    private void placeGlassBarrier(WorldServer world, BlockPos spawn) {
        BlockPos[] pos = {
            new BlockPos(spawn.getX() + 1, spawn.getY() + 1, spawn.getZ()),
            new BlockPos(spawn.getX() - 1, spawn.getY() + 1, spawn.getZ()),
            new BlockPos(spawn.getX(),     spawn.getY() + 1, spawn.getZ() + 1),
            new BlockPos(spawn.getX(),     spawn.getY() + 1, spawn.getZ() - 1)
        };
        for (BlockPos p : pos) {
            if (world.isAirBlock(p)) {
                world.setBlockState(p, Blocks.GLASS.getDefaultState(), 3);
                placedGlassBlocks.add(p);
            }
        }
    }

    private void tickCountdown(MinecraftServer server) {
        phaseTicksRemaining--;
        if (phaseTicksRemaining % 20 == 0 && phaseTicksRemaining > 0)
            broadcastMessage(server, TextFormatting.YELLOW + "" + (phaseTicksRemaining / 20) + "...");
        if (phaseTicksRemaining <= 0) {
            WorldServer hgWorld = server.getWorld(dimensionId);
            if (hgWorld != null) {
                for (BlockPos p : placedGlassBlocks)
                    if (hgWorld.getBlockState(p).getBlock() == Blocks.GLASS)
                        hgWorld.setBlockToAir(p);
            }
            placedGlassBlocks.clear();
            broadcastMessage(server, TextFormatting.GREEN + "" + TextFormatting.BOLD + "GO!");
            startGracePeriod(server);
        }
    }

    private void startGracePeriod(MinecraftServer server) {
        phase = GamePhase.GRACE_PERIOD;
        phaseTicksRemaining = GRACE_PERIOD_TICKS;
        sendPhaseMusic(server, HungerGamesMusicManager.PHASE1);
        fillChestsWithLoot(server, 1);
        for (UUID id : new ArrayList<>(alivePlayers)) {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
            if (p != null) {
                // Wipe any leftover superpower before the round begins.
                if (SuperpowerHandler.hasSuperpower(p)) {
                    SuperpowerHandler.removeSuperpower(p);
                    SuperpowerHandler.syncToPlayer(p);
                }
                p.setEntityInvulnerable(true);
            }
        }
        broadcastMessage(server, TextFormatting.GREEN + "Grace period! PvP disabled for " + (GRACE_PERIOD_TICKS / 20) + "s.");
    }

    private void tickGracePeriod(MinecraftServer server) {
        phaseTicksRemaining--;
        if (phaseTicksRemaining == GRACE_PERIOD_TICKS / 2)
            broadcastMessage(server, TextFormatting.GOLD + "Grace period halfway over!");
        if (phaseTicksRemaining == 10 * 20)
            broadcastMessage(server, TextFormatting.RED + "PvP enabled in 10 seconds!");
        if (phaseTicksRemaining <= 0) startActive(server);
    }

    private void startActive(MinecraftServer server) {
        phase = GamePhase.ACTIVE;
        activePhaseElapsed = 0;
        activePhase = 1;

        if (playerOrder.size() < 8) {
            phase1EndTicks =  4 * 60 * 20;  //  4 min
            phase2EndTicks = 10 * 60 * 20;  // +6 min
            phase3EndTicks = 18 * 60 * 20;  // +8 min
        } else {
            phase1EndTicks =  8 * 60 * 20;  //  8 min
            phase2EndTicks = 16 * 60 * 20;  // +8 min
            phase3EndTicks = 24 * 60 * 20;  // +8 min
        }
        phaseTicksRemaining = phase3EndTicks;

        for (UUID id : new ArrayList<>(alivePlayers)) {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
            if (p != null) p.setEntityInvulnerable(false);
        }

        applyWorldBorder(server, worldBorderStartRange * 2);
        broadcastMessage(server, TextFormatting.RED + "" + TextFormatting.BOLD + "PvP ENABLED! Fight to survive!");
        int phase1Minutes = phase1EndTicks / (60 * 20);
        broadcastMessage(server, TextFormatting.YELLOW + "Phase 1 — border stable for " + phase1Minutes + " minutes.");

        spawnInjections(server, 1);
    }

    private void tickActive(MinecraftServer server) {
        phaseTicksRemaining--;
        activePhaseElapsed++;

        int threshold = playerOrder.size() == 1 ? 0 : 1;
        if (alivePlayers.size() <= threshold) { endMatch(server); return; }

        if (activePhase == 1 && activePhaseElapsed >= phase1EndTicks) {
            activePhase = 2;
            fillChestsWithLoot(server, 2);
            // Wipe existing injections and respawn for phase 2.
            removeUncollectedInjections(server);
            spawnInjections(server, 2);
            // Shrink at exactly 0.5 blocks/second: distance = worldBorderStartRange (half the diameter).
            long phase2Ms = (long)(worldBorderStartRange) * 2000L;
            applyWorldBorder(server, worldBorderStartRange, phase2Ms);
            sendPhaseMusic(server, HungerGamesMusicManager.PHASE2);
            broadcastMessage(server, TextFormatting.GOLD + "" + TextFormatting.BOLD
                + "Phase 2! Border is closing! Chests refilled!");
        }

        if (activePhase == 2 && activePhaseElapsed >= phase2EndTicks) {
            activePhase = 3;
            fillChestsWithLoot(server, 3);
            // Wipe existing injections and respawn for phase 3.
            removeUncollectedInjections(server);
            spawnInjections(server, 3);
            // Shrink at exactly 0.5 blocks/second: distance = worldBorderStartRange - 20.
            long phase3Ms = (long)(Math.max(worldBorderStartRange - 20, 1)) * 2000L;
            applyWorldBorder(server, 20, phase3Ms);
            sendPhaseMusic(server, HungerGamesMusicManager.PHASE3);
            broadcastMessage(server, TextFormatting.DARK_RED + "" + TextFormatting.BOLD
                + "Phase 3! Final push! Chests refilled!");
        }

        if (phaseTicksRemaining <= 0) {
            broadcastMessage(server, TextFormatting.YELLOW + "Time's up! It's a draw!");
            endMatch(server);
        }
    }

    private void applyWorldBorder(MinecraftServer server, int diameter) {
        applyWorldBorder(server, diameter, 0);
    }

    /**
     * Set (or transition) the HG world border diameter.
     * Uses WorldBorder.setTransition() which controls the actual visible border,
     * NOT setSize() which only affects worldSize (the chunk-loading limit).
     *
     * Vanilla's PlayerList border listener is only registered for worlds[0] (overworld),
     * so we must manually push SPacketWorldBorder to every player in this match.
     *
     * @param diameter   Target diameter in blocks.
     * @param transitionMs Time in milliseconds for a gradual shrink (0 = instant).
     */
    private void applyWorldBorder(MinecraftServer server, int diameter, long transitionMs) {
        if (mapCenter == null || worldBorderStartRange <= 0) return;
        WorldServer hgWorld = server.getWorld(dimensionId);
        if (hgWorld == null) return;
        WorldBorder border = hgWorld.getWorldBorder();
        border.setCenter(mapCenter.getX(), mapCenter.getZ());
        if (transitionMs > 0) {
            border.setTransition(border.getDiameter(), diameter, transitionMs);
        } else {
            border.setTransition(diameter);
        }
        border.setDamageAmount(0.2);
        border.setDamageBuffer(5.0);
        border.setWarningDistance(20);
        border.setWarningTime(60);
        // Push the full border state to every player currently in this match dimension.
        // This is required because vanilla only auto-syncs the overworld border.
        SPacketWorldBorder pkt = new SPacketWorldBorder(border, SPacketWorldBorder.Action.INITIALIZE);
        for (EntityPlayerMP p : getAllPresentPlayers(server)) {
            if (p.dimension == dimensionId) p.connection.sendPacket(pkt);
        }
    }

    /**
     * Sends the current HG world border state to a single player.
     * Call this after teleporting a player into the HG dimension so they immediately
     * see the border instead of inheriting the overworld's (enormous) border.
     */
    public void sendBorderToPlayer(MinecraftServer server, EntityPlayerMP player) {
        if (mapCenter == null || worldBorderStartRange <= 0) return;
        WorldServer hgWorld = server.getWorld(dimensionId);
        if (hgWorld == null) return;
        player.connection.sendPacket(
            new SPacketWorldBorder(hgWorld.getWorldBorder(), SPacketWorldBorder.Action.INITIALIZE));
    }

    private void tickEnding(MinecraftServer server) {
        phaseTicksRemaining--;
    }

    private void endMatch(MinecraftServer server) {
        phase = GamePhase.ENDING;
        phaseTicksRemaining = 5 * 20;

        if (alivePlayers.size() == 1) {
            UUID winnerId = alivePlayers.iterator().next();
            EntityPlayerMP winner = server.getPlayerList().getPlayerByUUID(winnerId);
            String name = winner != null ? winner.getName() : cachedNames.getOrDefault(winnerId, "Unknown");
            broadcastMessage(server, TextFormatting.GOLD + "" + TextFormatting.BOLD + name + " wins the Hunger Games!");
        } else {
            broadcastMessage(server, TextFormatting.YELLOW + "Match ended in a draw!");
        }

        // Remove any uncollected injection entities from the world.
        removeUncollectedInjections(server);
    }

    /** Kill all remaining {@link EntityLucraftInjection} entities in this match's dimension. */
    private void removeUncollectedInjections(MinecraftServer server) {
        WorldServer world = server.getWorld(dimensionId);
        if (world == null) return;
        for (Entity entity : new ArrayList<>(world.loadedEntityList)) {
            if (entity instanceof EntityLucraftInjection) {
                entity.setDead();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Chest loot fill
    // -------------------------------------------------------------------------

    /**
     * Fill all loaded chests in the HG dimension with phase-appropriate loot.
     *
     * @param phase Active game phase (1, 2, or 3). Phase N items and All-Phases items
     *              are combined into the effective pool for every fill cycle.
     *
     * Per-item properties (stored in HGLoot NBT via HungerGamesLootEntry):
     *   weight      — relative selection probability (higher = more likely)
     *   globalMax   — max total copies spawned across ALL chests this cycle (0 = unlimited)
     *   perChestMax — max copies in a single chest this cycle
     *   minCount    — minimum stack size placed
     *   maxCount    — maximum stack size placed
     */
    /**
     * Spawns {@link EntityLucraftInjection} entities on the surface of the HG world for
     * the given active game phase (1-3).
     *
     * Effective pool = phase-specific items + AllPhases items (merged, always included).
     * Total count is clamped to [globalMin, globalMax] for this phase.
     * Per-entry maxOnMap limits how many of a single injection type can be on the map.
     *
     * Placement rules:
     *   - Must be on a solid surface block (not water, lava, leaves, ice, flowers).
     *   - Spread randomly within the border range around the map center.
     */
    private void spawnInjections(MinecraftServer server, int phase) {
        WorldServer world = server.getWorld(dimensionId);
        if (world == null) return;

        // Build effective pool (phase-specific + allPhases).
        List<ItemStack> phasePool;
        switch (phase) {
            case 1: phasePool = injectionPhase1; break;
            case 2: phasePool = injectionPhase2; break;
            case 3: phasePool = injectionPhase3; break;
            default: phasePool = new ArrayList<>();
        }
        List<ItemStack> effectivePool = new ArrayList<>(phasePool);
        effectivePool.addAll(injectionAllPhases);
        effectivePool.removeIf(s -> !LucraftInjectionEntry.isValidInjection(s));
        if (effectivePool.isEmpty()) return;

        // Effective global min/max for this phase (phase-specific non-zero overrides allPhases fallback).
        int tabIdx = phase - 1; // 0-based
        int gMin = injGlobalMin[tabIdx] > 0 ? injGlobalMin[tabIdx] : injGlobalMin[3];
        int gMax = injGlobalMax[tabIdx] > 0 ? injGlobalMax[tabIdx] : injGlobalMax[3];
        // Resolve 0 fallback for min: use 3 as default if nothing is set.
        if (gMin == 0 && gMax == 0) { gMin = 3; gMax = 8; }
        if (gMax > 0 && gMax < gMin) gMax = gMin;

        // Determine the lower bound from player count, then roll within the configured
        // range. Taking the best of one/two/three rolls makes later phases naturally
        // trend toward more pickups without ever exceeding the administrator's cap.
        int playerBased = Math.max(gMin, playerOrder.size() / 2);
        int lowerBound = gMax > 0 ? Math.min(playerBased, gMax) : playerBased;
        lowerBound = Math.max(lowerBound, gMin);
        int upperBound = gMax > 0 ? gMax : lowerBound + phase * 2;
        int spawnCount = lowerBound;
        for (int roll = 0; roll < Math.max(1, phase); roll++) {
            int candidate = lowerBound + (upperBound > lowerBound
                    ? rand.nextInt(upperBound - lowerBound + 1) : 0);
            spawnCount = Math.max(spawnCount, candidate);
        }

        // Build a weighted list for random selection.
        List<ItemStack> weighted = new ArrayList<>();
        for (ItemStack entry : effectivePool) {
            int w = LucraftInjectionEntry.getWeight(entry);
            for (int i = 0; i < w; i++) weighted.add(entry);
        }

        // Track how many of each superpowerId are already on the map (starts at 0).
        Map<String, Integer> onMapCount = new HashMap<>();

        int range = worldBorderStartRange > 0 ? worldBorderStartRange : 100;
        int cx = mapCenter != null ? mapCenter.getX() : 0;
        int cz = mapCenter != null ? mapCenter.getZ() : 0;

        int spawned = 0;
        int attempts = 0;
        int maxAttempts = spawnCount * 80;

        while (spawned < spawnCount && attempts < maxAttempts) {
            attempts++;

            // Weighted random selection.
            ItemStack chosen = weighted.get(rand.nextInt(weighted.size()));
            String lucraftId = LucraftInjectionEntry.getLucraftId(chosen);
            int maxOnMap     = LucraftInjectionEntry.getMaxOnMap(chosen);

            // Enforce per-type maxOnMap limit (keyed by LucraftCore injection id).
            if (maxOnMap > 0) {
                int current = onMapCount.getOrDefault(lucraftId, 0);
                if (current >= maxOnMap) continue;
            }

            int dx = rand.nextInt(range * 2) - range;
            int dz = rand.nextInt(range * 2) - range;
            int tx = cx + dx;
            int tz = cz + dz;

            BlockPos spawnPos = EntityLucraftInjection.findSurfaceSpawn(world, tx, tz);
            if (spawnPos == null) continue;

            // Pass the real lucraftcore:injection ItemStack so the entity (and its renderer)
            // always display the correct tinted vial for this superpower.
            EntityLucraftInjection injection = new EntityLucraftInjection(
                    world, tx + 0.5, spawnPos.getY() + 0.5, tz + 0.5,
                    chosen.copy(), shouldLockInjection(phase));
            if (world.spawnEntity(injection)) {
                onMapCount.merge(lucraftId, 1, Integer::sum);
                spawned++;
            }
        }
    }

    /** Later phases deliberately have a greater chance to require a key. */
    private boolean shouldLockInjection(int phase) {
        float chance = phase >= 3 ? 0.65F : phase == 2 ? 0.40F : 0.20F;
        return rand.nextFloat() < chance;
    }

    private void fillChestsWithLoot(MinecraftServer server, int phase) {
        WorldServer hgWorld = server.getWorld(dimensionId);
        if (hgWorld == null) return;

        // Build effective pool = current phase pool + all-phases pool (always combined).
        List<ItemStack> phasePool;
        switch (phase) {
            case 2:  phasePool = lootPhase2; break;
            case 3:  phasePool = lootPhase3; break;
            default: phasePool = lootPhase1; break;
        }
        List<ItemStack> effectivePool = new ArrayList<>(phasePool);
        effectivePool.addAll(lootAllPhases);
        boolean needsPowerKeys = hasInjectionPickupsForPhase(phase);
        if (effectivePool.isEmpty() && !needsPowerKeys) return;

        // Build weighted candidate list: each item appears `weight` times.
        List<ItemStack> weighted = new ArrayList<>();
        for (ItemStack entry : effectivePool) {
            int w = HungerGamesLootEntry.getWeight(entry);
            for (int i = 0; i < w; i++) weighted.add(entry);
        }
        // Collect chests.
        List<TileEntityChest> chests = new ArrayList<>();
        for (TileEntity te : new ArrayList<>(hgWorld.loadedTileEntityList))
            if (te instanceof TileEntityChest) chests.add((TileEntityChest) te);
        if (chests.isEmpty()) return;

        // globalSpawned tracks total copies placed this cycle, keyed by item identity.
        Map<ItemStack, Integer> globalSpawned = new IdentityHashMap<>();
        // Pre-populate so the map knows about every entry.
        for (ItemStack entry : effectivePool) globalSpawned.put(entry, 0);

        for (TileEntityChest chest : chests) {
            // Clear the chest.
            for (int i = 0; i < chest.getSizeInventory(); i++) chest.setInventorySlotContents(i, ItemStack.EMPTY);

            int itemCount = 3 + rand.nextInt(5); // 3–7 item placements per chest
            Set<Integer> usedSlots = new HashSet<>();
            Map<ItemStack, Integer> perChestSpawned = new IdentityHashMap<>();

            for (int j = 0; j < itemCount; j++) {
                // Pick a slot.
                int slot = -1, tries = 0;
                do { slot = rand.nextInt(chest.getSizeInventory()); tries++; }
                while (usedSlots.contains(slot) && tries < 40);
                if (usedSlots.contains(slot)) continue; // all slots exhausted

                // Pick a candidate respecting globalMax and perChestMax.
                // Shuffle a copy of the weighted list to avoid always picking the same item
                // when multiple are at cap.
                List<ItemStack> candidates = new ArrayList<>(weighted);
                java.util.Collections.shuffle(candidates, rand);

                ItemStack chosen = null;
                for (ItemStack candidate : candidates) {
                    int gmax  = HungerGamesLootEntry.getGlobalMax(candidate);
                    int cmax  = HungerGamesLootEntry.getPerChestMax(candidate);
                    int gSpawned = globalSpawned.getOrDefault(candidate, 0);
                    int cSpawned = perChestSpawned.getOrDefault(candidate, 0);

                    if (gmax > 0 && gSpawned >= gmax) continue;  // global cap reached
                    if (cSpawned >= cmax) continue;               // per-chest cap reached
                    chosen = candidate;
                    break;
                }
                if (chosen == null) continue; // all items at cap

                // Determine stack size.
                int minC = HungerGamesLootEntry.getMinCount(chosen);
                int maxC = Math.min(HungerGamesLootEntry.getMaxCount(chosen), chosen.getMaxStackSize());
                if (minC > maxC) maxC = minC;
                int count = minC + (maxC > minC ? rand.nextInt(maxC - minC + 1) : 0);

                // Place item (without HGLoot NBT tag so the player gets a clean item).
                ItemStack placed = chosen.copy();
                if (placed.hasTagCompound()) placed.getTagCompound().removeTag("HGLoot");
                placed.setCount(count);
                // Remove display name override from the HGLoot entry if there is one,
                // restoring the item's natural name on the placed stack.
                if (placed.hasTagCompound() && placed.getTagCompound().hasKey("display")) {
                    placed.getTagCompound().getCompoundTag("display").removeTag("Name");
                }
                chest.setInventorySlotContents(slot, placed);
                usedSlots.add(slot);

                // Update spawn counts.
                globalSpawned.put(chosen, globalSpawned.getOrDefault(chosen, 0) + 1);
                perChestSpawned.put(chosen, perChestSpawned.getOrDefault(chosen, 0) + 1);
            }
        }

        if (needsPowerKeys) addPowerKeysToChests(chests, Math.min(phase, chests.size()));
    }

    private boolean hasInjectionPickupsForPhase(int phase) {
        List<ItemStack> phasePool;
        switch (phase) {
            case 2: phasePool = injectionPhase2; break;
            case 3: phasePool = injectionPhase3; break;
            default: phasePool = injectionPhase1; break;
        }
        for (ItemStack stack : phasePool) {
            if (LucraftInjectionEntry.isValidInjection(stack)) return true;
        }
        for (ItemStack stack : injectionAllPhases) {
            if (LucraftInjectionEntry.isValidInjection(stack)) return true;
        }
        return false;
    }

    private void addPowerKeysToChests(List<TileEntityChest> chests, int keyCount) {
        List<TileEntityChest> shuffled = new ArrayList<>(chests);
        java.util.Collections.shuffle(shuffled, rand);
        int placed = 0;
        for (TileEntityChest chest : shuffled) {
            List<Integer> emptySlots = new ArrayList<>();
            for (int slot = 0; slot < chest.getSizeInventory(); slot++) {
                if (chest.getStackInSlot(slot).isEmpty()) emptySlots.add(slot);
            }
            if (emptySlots.isEmpty()) continue;
            int slot = emptySlots.get(rand.nextInt(emptySlots.size()));
            chest.setInventorySlotContents(slot,
                    new ItemStack(com.matoon.herosmp.registry.ModItems.POWER_KEY));
            chest.markDirty();
            if (++placed >= keyCount) break;
        }
    }

    // -------------------------------------------------------------------------
    // Scoreboard — uses a match-local Scoreboard so packets never reach players
    // outside this match.  All packets are sent manually to match participants.
    // -------------------------------------------------------------------------

    private void initScoreboard(MinecraftServer server) {
        boardObjName = "hg" + matchId; // ≤16 chars

        ScoreObjective old = localBoard.getObjective(boardObjName);
        if (old != null) localBoard.removeObjective(old);

        ScoreCriteria criteria = new ScoreCriteria("hg_" + boardObjName);
        boardObjective = localBoard.addScoreObjective(boardObjName, criteria);
        boardObjective.setDisplayName(TextFormatting.GOLD + "The Hero Games");

        // Send the objective creation packet to all participants.
        SPacketScoreboardObjective addObjPacket = new SPacketScoreboardObjective(boardObjective, 0);
        for (EntityPlayerMP p : getAllPresentPlayers(server)) p.connection.sendPacket(addObjPacket);

        lastSbState.clear();
        syncScoreboard(server);
    }

    private Map<String, Integer> buildSbState() {
        Map<String, Integer> state = new LinkedHashMap<>();
        state.put(getPhaseLabel(), getSecondsToEvent());
        int rank = alivePlayers.size();
        for (UUID id : playerOrder) {
            boolean alive = alivePlayers.contains(id);
            String name = cachedNames.getOrDefault(id, "???");
            String entry = (alive ? TextFormatting.GREEN : TextFormatting.DARK_GRAY) + name;
            state.put(entry, alive ? rank-- : 0);
        }
        return state;
    }

    private String getPhaseLabel() {
        switch (phase) {
            case LOBBY:        return TextFormatting.YELLOW   + "Lobby";
            case COUNTDOWN:    return TextFormatting.GOLD     + "Starting...";
            case GRACE_PERIOD: return TextFormatting.GREEN    + "Grace Period";
            case ACTIVE:
                switch (activePhase) {
                    case 1: return TextFormatting.RED      + "Phase 1";
                    case 2: return TextFormatting.GOLD     + "Phase 2";
                    case 3: return TextFormatting.DARK_RED + "Phase 3";
                }
            default: return TextFormatting.GRAY + "Ending";
        }
    }

    private int getSecondsToEvent() {
        switch (phase) {
            case LOBBY: case COUNTDOWN: case GRACE_PERIOD:
                return phaseTicksRemaining / 20;
            case ACTIVE:
                int end = activePhase == 1 ? phase1EndTicks
                        : activePhase == 2 ? phase2EndTicks : phase3EndTicks;
                return Math.max(0, (end - activePhaseElapsed) / 20);
            default: return 0;
        }
    }

    /**
     * Diffs and syncs the local scoreboard, pushing packets only to match participants.
     * Stale entry names (e.g. player died, phase changed) force a full rebuild so
     * old entries are cleared from the client sidebar.
     */
    private void syncScoreboard(MinecraftServer server) {
        if (boardObjective == null) return;
        Map<String, Integer> newState = buildSbState();

        boolean firstSync = lastSbState.isEmpty();
        boolean staleExists = false;
        for (String name : lastSbState.keySet()) {
            if (!newState.containsKey(name)) { staleExists = true; break; }
        }

        List<EntityPlayerMP> participants = getAllPresentPlayers(server);

        if (staleExists) {
            // Remove old objective on all clients, then re-create it.
            SPacketScoreboardObjective removeObjPacket = new SPacketScoreboardObjective(boardObjective, 1);
            for (EntityPlayerMP p : participants) p.connection.sendPacket(removeObjPacket);

            localBoard.removeObjective(boardObjective);
            ScoreCriteria criteria = new ScoreCriteria("hg_" + boardObjName);
            boardObjective = localBoard.addScoreObjective(boardObjName, criteria);
            boardObjective.setDisplayName(TextFormatting.GOLD + "The Hero Games");

            SPacketScoreboardObjective addObjPacket = new SPacketScoreboardObjective(boardObjective, 0);
            for (EntityPlayerMP p : participants) p.connection.sendPacket(addObjPacket);
        }

        // Push score updates for changed (or all, on first sync) entries.
        for (Map.Entry<String, Integer> e : newState.entrySet()) {
            if (firstSync || staleExists || !e.getValue().equals(lastSbState.get(e.getKey()))) {
                net.minecraft.scoreboard.Score score = localBoard.getOrCreateScore(e.getKey(), boardObjective);
                score.setScorePoints(e.getValue());
                // SPacketUpdateScore(Score) uses Action.CHANGE automatically.
                SPacketUpdateScore scorePacket = new SPacketUpdateScore(score);
                for (EntityPlayerMP p : participants) p.connection.sendPacket(scorePacket);
            }
        }

        // Always re-push the display-in-sidebar packet.  This also handles newly joined
        // spectators and players who just changed dimension (dimension changes reset the
        // client-side scoreboard display slot).
        if (boardObjective != null) {
            SPacketDisplayObjective display = new SPacketDisplayObjective(1, boardObjective);
            for (UUID id : playerOrder) {
                EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
                if (p != null && p.dimension == dimensionId) p.connection.sendPacket(display);
            }
            for (UUID id : spectators) {
                EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
                if (p != null && p.dimension == dimensionId) p.connection.sendPacket(display);
            }
        }

        lastSbState.clear();
        lastSbState.putAll(newState);
    }

    private void cleanupScoreboard(MinecraftServer server) {
        if (boardObjective == null) return;
        // Remove the objective on every participant's client (clears their sidebar).
        SPacketScoreboardObjective removePacket = new SPacketScoreboardObjective(boardObjective, 1);
        for (UUID id : playerOrder) {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
            if (p != null) p.connection.sendPacket(removePacket);
        }
        for (UUID id : spectators) {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
            if (p != null) p.connection.sendPacket(removePacket);
        }
        localBoard.removeObjective(boardObjective);
        boardObjective = null;
        lastSbState.clear();
    }

    /**
     * Sends the full local scoreboard state (objective definition + all scores +
     * sidebar display slot) to a single player.  Used when a spectator first joins
     * so they immediately see the sidebar without waiting for the next syncScoreboard tick.
     */
    private void sendScoreboardToPlayer(EntityPlayerMP player) {
        if (boardObjective == null) return;
        player.connection.sendPacket(new SPacketScoreboardObjective(boardObjective, 0));
        for (Map.Entry<String, Integer> e : lastSbState.entrySet()) {
            net.minecraft.scoreboard.Score score = localBoard.getOrCreateScore(e.getKey(), boardObjective);
            score.setScorePoints(e.getValue());
            player.connection.sendPacket(new SPacketUpdateScore(score));
        }
        player.connection.sendPacket(new SPacketDisplayObjective(1, boardObjective));
    }

    /** Removes this player from the boss bar, clears their sidebar scoreboard, and removes grace-period invulnerability. */
    public void clearPlayerUi(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        if (bossBar != null) bossBar.removePlayer(player);
        if (boardObjective != null)
            player.connection.sendPacket(new SPacketScoreboardObjective(boardObjective, 1));
        player.setEntityInvulnerable(false);
        if (musicManager != null) musicManager.stopMusicForPlayer(player);
        if (spectatorBatIds.containsKey(id)) removeSpectatorBat(player.getServer(), id);
        spectators.remove(id);
    }

    // -------------------------------------------------------------------------
    // Spectator bat system
    // -------------------------------------------------------------------------

    public void enterSpectatorMode(MinecraftServer server, EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        spectators.add(id);
        WorldServer hgWorld = server.getWorld(dimensionId);
        if (hgWorld == null) return;

        BlockPos anchor = lobbySpawnPoint != null ? lobbySpawnPoint
                        : (mapCenter != null ? mapCenter : new BlockPos(0, 64, 0));
        double sx = anchor.getX() + 0.5, sy = anchor.getY() + 10.0, sz = anchor.getZ() + 0.5;

        if (player.dimension != dimensionId) {
            player.changeDimension(dimensionId,
                new FixedTeleporter(hgWorld, sx, sy, sz, player.rotationYaw, player.rotationPitch));
        } else {
            player.connection.setPlayerLocation(sx, sy, sz, player.rotationYaw, player.rotationPitch);
        }
        player.setGameType(GameType.SPECTATOR);
        player.setSpectatingEntity(player);
        player.sendPlayerAbilities();

        if (bossBar != null) bossBar.addPlayer(player);
        if (boardObjective != null) sendScoreboardToPlayer(player);
        sendBorderToPlayer(server, player);
        if (musicManager != null && currentMusicPhase != null && currentMusicTrack != null)
            musicManager.sendSpecificTrack(currentMusicPhase, currentMusicTrack, player);
        spawnSpectatorBat(hgWorld, player, id);
    }

    private void spawnSpectatorBat(WorldServer world, EntityPlayerMP spectator, UUID specId) {
        removeSpectatorBat(world.getMinecraftServer(), specId);
        EntityBat bat = new EntityBat(world);
        double eyeY = spectator.posY + spectator.getEyeHeight();
        bat.setPositionAndRotation(spectator.posX, eyeY, spectator.posZ,
                spectator.rotationYaw, spectator.rotationPitch);
        bat.setNoGravity(true);
        bat.setSilent(true);
        bat.setEntityInvulnerable(true);
        bat.setIsBatHanging(false);
        world.spawnEntity(bat);
        spectatorBatIds.put(specId, bat.getUniqueID());
        spectator.connection.sendPacket(new SPacketDestroyEntities(bat.getEntityId()));
    }

    private void removeSpectatorBat(MinecraftServer server, UUID specId) {
        UUID batId = spectatorBatIds.remove(specId);
        if (batId == null) return;
        for (WorldServer w : server.worlds) {
            Entity e = w.getEntityFromUuid(batId);
            if (e != null) { e.setDead(); break; }
        }
    }

    private void tickSpectatorBats(MinecraftServer server) {
        if (spectatorBatIds.isEmpty()) return;
        for (UUID specId : new ArrayList<>(spectatorBatIds.keySet())) {
            EntityPlayerMP sp = server.getPlayerList().getPlayerByUUID(specId);
            if (sp == null || sp.isDead) { removeSpectatorBat(server, specId); continue; }
            WorldServer world = server.getWorld(sp.dimension);
            if (world == null) { removeSpectatorBat(server, specId); continue; }

            UUID batUUID = spectatorBatIds.get(specId);
            if (batUUID == null) continue;
            Entity ent = world.getEntityFromUuid(batUUID);
            EntityBat bat = ent instanceof EntityBat ? (EntityBat) ent : null;

            if (bat == null || bat.isDead) {
                spawnSpectatorBat(world, sp, specId);
                continue;
            }
            bat.setNoGravity(true);
            bat.setSilent(true);
            bat.setEntityInvulnerable(true);
            bat.setIsBatHanging(false);
            bat.setHealth(bat.getMaxHealth());
            bat.motionX = bat.motionY = bat.motionZ = 0;
            bat.fallDistance = 0;
            // When the spectator is viewing another entity's perspective, their server-side
            // body is repositioned to the spectated entity for chunk loading purposes.
            // Placing the bat at the spectator's body position would put it on the
            // spectated player's head, blocking their vision and projectiles.
            // Instead, park the bat out of the way below the world.
            net.minecraft.entity.Entity spectatingTarget = sp.getSpectatingEntity();
            if (spectatingTarget != null && spectatingTarget != sp) {
                bat.setPositionAndRotation(sp.posX, -128.0D, sp.posZ, 0.0F, 0.0F);
            } else {
                double eyeY = sp.posY + sp.getEyeHeight();
                bat.setPositionAndRotation(sp.posX, eyeY, sp.posZ, sp.rotationYaw, sp.rotationPitch);
                bat.rotationYawHead = sp.rotationYaw;
                bat.renderYawOffset = sp.rotationYaw;
            }
        }
    }

    public boolean isSpectatorBat(Entity entity) {
        if (!(entity instanceof EntityBat)) return false;
        UUID eid = entity.getUniqueID();
        for (UUID batId : spectatorBatIds.values()) {
            if (eid.equals(batId)) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Boss bar
    // -------------------------------------------------------------------------

    private void updateBossBar() {
        if (bossBar == null) return;

        String title;
        float  progress;

        switch (phase) {
            case LOBBY:
                title    = TextFormatting.YELLOW + "Starting in " + (phaseTicksRemaining / 20) + "s";
                progress = (float) phaseTicksRemaining / (30 * 20);
                break;
            case COUNTDOWN:
                title    = TextFormatting.GOLD + "Get Ready - " + (phaseTicksRemaining / 20) + "s";
                progress = (float) phaseTicksRemaining / COUNTDOWN_TICKS;
                break;
            case GRACE_PERIOD:
                title    = TextFormatting.GREEN + "Grace Period - " + (phaseTicksRemaining / 20) + "s";
                progress = (float) phaseTicksRemaining / GRACE_PERIOD_TICKS;
                break;
            case ACTIVE: {
                int phaseEnd = activePhase == 1 ? phase1EndTicks
                             : activePhase == 2 ? phase2EndTicks : phase3EndTicks;
                int left = phaseEnd - activePhaseElapsed;
                int m = left / (60 * 20), s = (left / 20) % 60;
                title    = TextFormatting.RED + "Phase " + activePhase + " — " + alivePlayers.size()
                           + " alive — " + String.format("%d:%02d", m, s);
                progress = (float) left / (8 * 60 * 20);
                break;
            }
            case ENDING:
                title    = TextFormatting.GOLD + "Match Ending...";
                progress = 1.0F - ((float) phaseTicksRemaining / (5 * 20));
                break;
            default:
                title = ""; progress = 0; break;
        }

        bossBar.setName(new TextComponentString(title));
        bossBar.setPercent(Math.max(0.0F, Math.min(1.0F, progress)));
    }

    // -------------------------------------------------------------------------
    // Broadcast / cleanup
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Music helpers
    // -------------------------------------------------------------------------

    private void sendPhaseMusic(MinecraftServer server, String phase) {
        if (musicManager == null) return;
        currentMusicPhase = phase;
        currentMusicTrack = musicManager.pickTrack(phase);
        musicLoopTicker = 0;
        if (currentMusicTrack == null) return;
        for (EntityPlayerMP p : getAllPresentPlayers(server))
            musicManager.sendSpecificTrack(phase, currentMusicTrack, p);
    }

    private List<EntityPlayerMP> getAllPresentPlayers(MinecraftServer server) {
        List<EntityPlayerMP> list = new ArrayList<>();
        for (UUID id : playerOrder) {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
            if (p != null) list.add(p);
        }
        for (UUID id : spectators) {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
            if (p != null && !playerOrder.contains(id)) list.add(p);
        }
        return list;
    }

    private void broadcastMessage(MinecraftServer server, String message) {
        TextComponentString c = new TextComponentString(
            TextFormatting.DARK_AQUA + "[HG] " + TextFormatting.RESET + message);
        for (UUID id : playerOrder) {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
            if (p != null && p.dimension == dimensionId) p.sendMessage(c);
        }
        for (UUID id : spectators) {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
            if (p != null && p.dimension == dimensionId) p.sendMessage(c);
        }
    }

    public void cleanup(MinecraftServer server) {
        if (musicManager != null) musicManager.stopMusicForAll(getAllPresentPlayers(server));
        for (UUID specId : new ArrayList<>(spectatorBatIds.keySet())) removeSpectatorBat(server, specId);
        cleanupScoreboard(server);
        // Reset the world border to a safe large value so departing players aren't
        // damaged and the next match in this dimension slot starts clean.
        WorldServer hgWorld = server.getWorld(dimensionId);
        if (hgWorld != null) hgWorld.getWorldBorder().setTransition(60000000);
        if (bossBar != null) {
            for (UUID id : playerOrder) {
                EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
                if (p != null) bossBar.removePlayer(p);
            }
            for (UUID id : spectators) {
                EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
                if (p != null) bossBar.removePlayer(p);
            }
            bossBar = null;
        }
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public int       getMatchId()           { return matchId; }
    public int       getDimensionId()       { return dimensionId; }
    public String    getTemplateName()      { return templateName; }
    public GamePhase getPhase()             { return phase; }
    public Set<UUID> getAlivePlayers()      { return new HashSet<>(alivePlayers); }
    public Set<UUID> getSpectators()        { return new HashSet<>(spectators); }
    public List<UUID> getPlayerOrder()      { return new ArrayList<>(playerOrder); }
    public BlockPos  getSpawnPoint(UUID id) { return spawnPoints.get(id); }
    public boolean isActive()   { return phase == GamePhase.COUNTDOWN || phase == GamePhase.GRACE_PERIOD || phase == GamePhase.ACTIVE; }
    public boolean isEnded()    { return phase == GamePhase.ENDING; }
    public boolean isPvPEnabled() { return phase == GamePhase.ACTIVE; }

    /** Announces to all current match participants that a new player joined the lobby. */
    public void broadcastJoin(MinecraftServer server, String playerName) {
        broadcastMessage(server, TextFormatting.AQUA + playerName
            + TextFormatting.GREEN + " joined the lobby! ("
            + playerOrder.size() + " players)");
        // Sync scoreboard so new player name appears.
        syncScoreboard(server);
    }

    /** True if the match is still in LOBBY phase and has room for more players. */
    public boolean canLateJoin() {
        return phase == GamePhase.LOBBY && playerOrder.size() < maxPlayers;
    }

    /**
     * Adds a late-joining player into an already-running LOBBY phase.
     * The player is saved, cleared, teleported to the lobby spawn, given invulnerability,
     * shown the scoreboard/boss bar, and queued for a spawn point on countdown.
     * Returns the spawn point assigned to the player (from the round spawn list).
     */
    public BlockPos lateJoin(MinecraftServer server, EntityPlayerMP player, BlockPos spawnPoint) {
        UUID id = player.getUniqueID();
        cachedNames.put(id, player.getName());
        addPlayer(id, spawnPoint);
        player.setEntityInvulnerable(true);
        if (bossBar != null) bossBar.addPlayer(player);
        if (boardObjective != null) sendScoreboardToPlayer(player);
        sendBorderToPlayer(server, player);
        sendPhaseMusic(server, HungerGamesMusicManager.LOBBY);
        return spawnPoint;
    }

    // -------------------------------------------------------------------------
    // Game phase enum
    // -------------------------------------------------------------------------

    public enum GamePhase {
        WAITING, LOBBY, COUNTDOWN, GRACE_PERIOD, ACTIVE, ENDING
    }
}
