package com.matoon.herosmp.hungergames;

import com.matoon.herosmp.hungergames.music.HungerGamesMusicManager;
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
    private static final int PHASE1_END_TICKS   = 8  * 60 * 20;  //  9 600
    private static final int PHASE2_END_TICKS   = 16 * 60 * 20;  // 19 200
    private static final int PHASE3_END_TICKS   = 24 * 60 * 20;  // 28 800

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
    private List<ItemStack> lootPool              = new ArrayList<>();
    private Set<String>     breakableBlockNames   = new HashSet<>();
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

    // Scoreboard (uses the server's ServerScoreboard so packet sending is automatic)
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
    public void setLootPool(List<ItemStack> pool)   { lootPool = new ArrayList<>(pool); }
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
        if (!lootPool.isEmpty()) fillChestsWithLoot(server);
        for (UUID id : new ArrayList<>(alivePlayers)) {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
            if (p != null) p.setEntityInvulnerable(true);
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
        phaseTicksRemaining = PHASE3_END_TICKS;
        activePhaseElapsed = 0;
        activePhase = 1;

        for (UUID id : new ArrayList<>(alivePlayers)) {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
            if (p != null) p.setEntityInvulnerable(false);
        }

        applyWorldBorder(server, worldBorderStartRange * 2);
        broadcastMessage(server, TextFormatting.RED + "" + TextFormatting.BOLD + "PvP ENABLED! Fight to survive!");
        broadcastMessage(server, TextFormatting.YELLOW + "Phase 1 — border stable for 8 minutes.");
    }

    private void tickActive(MinecraftServer server) {
        phaseTicksRemaining--;
        activePhaseElapsed++;

        int threshold = playerOrder.size() == 1 ? 0 : 1;
        if (alivePlayers.size() <= threshold) { endMatch(server); return; }

        if (activePhase == 1 && activePhaseElapsed >= PHASE1_END_TICKS) {
            activePhase = 2;
            fillChestsWithLoot(server);
            applyWorldBorder(server, worldBorderStartRange);
            sendPhaseMusic(server, HungerGamesMusicManager.PHASE2);
            broadcastMessage(server, TextFormatting.GOLD + "" + TextFormatting.BOLD
                + "Phase 2! Border is closing! Chests refilled!");
        }

        if (activePhase == 2 && activePhaseElapsed >= PHASE2_END_TICKS) {
            activePhase = 3;
            fillChestsWithLoot(server);
            applyWorldBorder(server, 20);
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
        if (mapCenter == null || worldBorderStartRange <= 0) return;
        WorldServer hgWorld = server.getWorld(dimensionId);
        if (hgWorld == null) return;
        WorldBorder border = hgWorld.getWorldBorder();
        border.setCenter(mapCenter.getX(), mapCenter.getZ());
        border.setSize(diameter);
        border.setDamageAmount(0.5);
        border.setDamageBuffer(5.0);
        border.setWarningDistance(20);
        border.setWarningTime(30);
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
    }

    // -------------------------------------------------------------------------
    // Chest loot fill
    // -------------------------------------------------------------------------

    private void fillChestsWithLoot(MinecraftServer server) {
        WorldServer hgWorld = server.getWorld(dimensionId);
        if (hgWorld == null || lootPool.isEmpty()) return;

        List<TileEntityChest> chests = new ArrayList<>();
        for (TileEntity te : new ArrayList<>(hgWorld.loadedTileEntityList))
            if (te instanceof TileEntityChest) chests.add((TileEntityChest) te);

        for (TileEntityChest chest : chests) {
            int itemCount = 3 + rand.nextInt(5);
            for (int i = 0; i < chest.getSizeInventory(); i++) chest.setInventorySlotContents(i, ItemStack.EMPTY);
            Set<Integer> used = new HashSet<>();
            for (int j = 0; j < itemCount; j++) {
                int slot, tries = 0;
                do { slot = rand.nextInt(chest.getSizeInventory()); tries++; }
                while (used.contains(slot) && tries < 30);
                if (!used.contains(slot)) {
                    used.add(slot);
                    ItemStack item = lootPool.get(rand.nextInt(lootPool.size())).copy();
                    int maxCount = Math.min(item.getMaxStackSize(), 4);
                    item.setCount(1 + (maxCount > 1 ? rand.nextInt(maxCount) : 0));
                    chest.setInventorySlotContents(slot, item);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Scoreboard — uses the server's ServerScoreboard so packet sending is
    // handled automatically for all connected clients.
    // -------------------------------------------------------------------------

    private void initScoreboard(MinecraftServer server) {
        boardObjName = "hg" + matchId; // ≤16 chars
        Scoreboard sb = server.getEntityWorld().getScoreboard();

        ScoreObjective old = sb.getObjective(boardObjName);
        if (old != null) sb.removeObjective(old);

        ScoreCriteria criteria = new ScoreCriteria("hg_" + boardObjName);
        boardObjective = sb.addScoreObjective(boardObjName, criteria);
        boardObjective.setDisplayName(TextFormatting.GOLD + "The Hero Games");

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
                int end = activePhase == 1 ? PHASE1_END_TICKS
                        : activePhase == 2 ? PHASE2_END_TICKS : PHASE3_END_TICKS;
                return Math.max(0, (end - activePhaseElapsed) / 20);
            default: return 0;
        }
    }

    /**
     * Diffs and syncs the scoreboard with the server scoreboard.
     * Stale entry names (e.g. player died, phase changed) force a full rebuild
     * of the objective to clear them from client displays.
     */
    private void syncScoreboard(MinecraftServer server) {
        if (boardObjective == null) return;
        Scoreboard sb = server.getEntityWorld().getScoreboard();
        Map<String, Integer> newState = buildSbState();

        boolean firstSync = lastSbState.isEmpty();
        boolean staleExists = false;
        for (String name : lastSbState.keySet()) {
            if (!newState.containsKey(name)) { staleExists = true; break; }
        }

        if (staleExists) {
            sb.removeObjective(boardObjective);
            ScoreCriteria criteria = new ScoreCriteria("hg_" + boardObjName);
            boardObjective = sb.addScoreObjective(boardObjName, criteria);
            boardObjective.setDisplayName(TextFormatting.GOLD + "The Hero Games");
            for (Map.Entry<String, Integer> e : newState.entrySet())
                sb.getOrCreateScore(e.getKey(), boardObjective).setScorePoints(e.getValue());
        } else if (firstSync) {
            for (Map.Entry<String, Integer> e : newState.entrySet())
                sb.getOrCreateScore(e.getKey(), boardObjective).setScorePoints(e.getValue());
        } else {
            for (Map.Entry<String, Integer> e : newState.entrySet()) {
                Integer prev = lastSbState.get(e.getKey());
                if (!e.getValue().equals(prev))
                    sb.getOrCreateScore(e.getKey(), boardObjective).setScorePoints(e.getValue());
            }
        }

        // Always re-push the display packet to players currently in the HG dimension.
        // This recovers from dimension-change resets on the client side.
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
        // Clear sidebar display for each participant individually
        SPacketDisplayObjective clearPacket = new SPacketDisplayObjective(1, null);
        for (UUID id : playerOrder) {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
            if (p != null) p.connection.sendPacket(clearPacket);
        }
        for (UUID id : spectators) {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
            if (p != null) p.connection.sendPacket(clearPacket);
        }
        server.getEntityWorld().getScoreboard().removeObjective(boardObjective);
        boardObjective = null;
        lastSbState.clear();
    }

    /** Removes this player from the boss bar, clears their sidebar scoreboard, and removes grace-period invulnerability. */
    public void clearPlayerUi(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        if (bossBar != null) bossBar.removePlayer(player);
        if (boardObjective != null)
            player.connection.sendPacket(new SPacketDisplayObjective(1, null));
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
        if (boardObjective != null)
            player.connection.sendPacket(new SPacketDisplayObjective(1, boardObjective));
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
            double eyeY = sp.posY + sp.getEyeHeight();
            bat.setPositionAndRotation(sp.posX, eyeY, sp.posZ, sp.rotationYaw, sp.rotationPitch);
            bat.rotationYawHead = sp.rotationYaw;
            bat.renderYawOffset = sp.rotationYaw;
            bat.motionX = bat.motionY = bat.motionZ = 0;
            bat.setNoGravity(true);
            bat.setSilent(true);
            bat.setEntityInvulnerable(true);
            bat.setIsBatHanging(false);
            bat.setHealth(bat.getMaxHealth());
            bat.fallDistance = 0;
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
                int phaseEnd = activePhase == 1 ? PHASE1_END_TICKS
                             : activePhase == 2 ? PHASE2_END_TICKS : PHASE3_END_TICKS;
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

    // -------------------------------------------------------------------------
    // Game phase enum
    // -------------------------------------------------------------------------

    public enum GamePhase {
        WAITING, LOBBY, COUNTDOWN, GRACE_PERIOD, ACTIVE, ENDING
    }
}
