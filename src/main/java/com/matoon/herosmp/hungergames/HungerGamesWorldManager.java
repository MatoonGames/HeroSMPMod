package com.matoon.herosmp.hungergames;

import com.matoon.herosmp.hungergames.map.*;
import com.matoon.herosmp.hungergames.music.HungerGamesMusicManager;
import com.matoon.herosmp.hungergames.world.HungerGamesWorldProvider;
import com.matoon.herosmp.network.ModNetwork;
import com.matoon.herosmp.network.PacketOpenHungerGamesMenu;
import com.matoon.herosmp.npc.pvp.FixedTeleporter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.DimensionType;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.border.WorldBorder;
import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central manager for Hunger Games worlds, matches, and map configure sessions.
 */
public class HungerGamesWorldManager {

    private static final int HG_BASE_DIMENSION_ID   = -8000;
    private static final int HG_DIMENSION_TYPE_BASE_ID = 17800;
    private static final String HG_DIMENSION_TYPE_PREFIX = "herosmp_hg_";
    private static final String LOBBY_MAP_NAME = "Lobby";

    private final AtomicInteger dimensionIdCounter = new AtomicInteger(1);
    private final AtomicInteger matchIdCounter     = new AtomicInteger(1);
    private final Map<Integer, HungerGamesMatch>             activeMatches    = new HashMap<>();
    private final Map<Integer, DimensionType>                registeredDimensionTypes = new HashMap<>();
    private final Deque<UUID>                                queue            = new ArrayDeque<>();
    private final Set<UUID>                                  queuedPlayers    = new HashSet<>();
    private final Map<UUID, HungerGamesConfigureMapSession>  configureSessions = new HashMap<>();
    private final Map<UUID, String>                          pendingChatInputs = new HashMap<>();
    private final Map<UUID, Integer>                         pendingHGSpectators = new HashMap<>();
    private final Map<UUID, Integer>                         spectatorSetupCountdown = new HashMap<>();
    // Players waiting for state restore after changeDimension() completes (2-tick delay).
    private final Map<UUID, Integer>                         pendingHGReturns = new HashMap<>();
    // The loot inventory currently open for a player.
    private final Map<UUID, HungerGamesMapLootInventory>     openLootInventories = new HashMap<>();
    // The injection inventory currently open for a player.
    private final Map<UUID, LucraftInjectionInventory>             openInjectionInventories  = new HashMap<>();
    // The injection properties inventory currently open for a player.
    private final Map<UUID, LucraftInjectionPropertiesInventory>   openInjectionPropInventories = new HashMap<>();

    // Shared lobby dimension — created when the first player queues (if a Lobby map exists).
    private int     lobbyDimensionId = Integer.MIN_VALUE; // MIN_VALUE = not allocated
    private final Set<UUID> playersInLobby = new HashSet<>();
    private BlockPos lobbySpawnPoint = null;

    private HungerGamesMapManager mapManager;
    private HungerGamesMusicManager musicManager;
    private String defaultMapName = null;

    private int minPlayers            = 2;
    private int maxPlayers            = 24;
    private int queueCountdownTicks   = 20 * 20;
    private int currentQueueCountdown = -1;

    // -------------------------------------------------------------------------
    // Queue Management
    // -------------------------------------------------------------------------

    public synchronized void queuePlayer(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        if (isPlayerInMatch(id)) {
            sendMessage(player, TextFormatting.RED + "You are already in a Hunger Games match!");
            return;
        }
        if (queuedPlayers.contains(id)) {
            sendMessage(player, TextFormatting.YELLOW + "You are already in the queue!");
            return;
        }
        if (com.matoon.herosmp.HeroSMP.PVP_QUEUE_MANAGER.isPlayerInPvpSession(id)) {
            sendMessage(player, TextFormatting.RED + "You cannot join Hunger Games while in a PvP match!");
            return;
        }

        // Check for an already-running match still in LOBBY phase with open slots.
        if (tryLateJoinExistingMatch(player)) return;

        queue.addLast(id);
        queuedPlayers.add(id);

        // If a Lobby map exists, send this player into the shared lobby dimension.
        sendToLobbyIfAvailable(player);

        sendMessage(player, TextFormatting.GREEN + "Joined Hunger Games queue! "
            + TextFormatting.GRAY + "(" + queuedPlayers.size() + "/" + maxPlayers + ")");

        if (queuedPlayers.size() >= minPlayers && currentQueueCountdown < 0) {
            currentQueueCountdown = queueCountdownTicks;
            broadcastQueueMessage(player.getServer(), TextFormatting.AQUA
                + "Hunger Games starting in " + (queueCountdownTicks / 20) + "s!");
        }
        if (queuedPlayers.size() >= maxPlayers) {
            startMatchFromQueue(player.getServer());
        }
    }

    /**
     * If there is an active match in LOBBY phase with open slots, slots this player
     * into it immediately rather than putting them in the queue.
     * Returns true if the player was placed into an existing match.
     */
    private boolean tryLateJoinExistingMatch(EntityPlayerMP player) {
        for (HungerGamesMatch match : activeMatches.values()) {
            if (!match.canLateJoin()) continue;

            MinecraftServer server = player.getServer();
            int dimId = match.getDimensionId();
            WorldServer hgWorld = server.getWorld(dimId);
            if (hgWorld == null) continue;

            // Pick a spawn point: reuse the map config spawn list if available.
            HungerGamesMapManager mgr = getMapManager(server);
            HungerGamesMapConfig cfg = mgr.loadMapConfig(match.getTemplateName());
            List<BlockPos> roundSpawns = cfg.getRoundSpawns().isEmpty()
                ? generateSpawnPoints(match.getPlayerOrder().size() + 1)
                : cfg.getRoundSpawns();
            BlockPos spawnPoint = roundSpawns.get(match.getPlayerOrder().size() % roundSpawns.size());
            BlockPos lobbySpawn = cfg.getLobbySpawn() != null ? cfg.getLobbySpawn() : roundSpawns.get(0);

            PlayerDataIsolationManager.savePlayerState(player);
            PlayerDataIsolationManager.clearPlayerState(player);
            teleportToHGWorld(player, hgWorld, lobbySpawn);
            player.setGameType(GameType.SURVIVAL);
            match.lateJoin(server, player, spawnPoint);

            sendMessage(player, TextFormatting.GREEN + "Joined an in-progress lobby — Match #"
                + match.getMatchId() + "! "
                + TextFormatting.GRAY + "(" + match.getPlayerOrder().size() + " players)");
            match.broadcastJoin(server, player.getName());
            return true;
        }
        return false;
    }

    public synchronized void dequeuePlayer(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        if (!queuedPlayers.remove(id)) {
            sendMessage(player, TextFormatting.YELLOW + "You are not in the queue!");
            return;
        }
        queue.remove(id);
        sendMessage(player, TextFormatting.YELLOW + "Left the Hunger Games queue.");
        if (queuedPlayers.size() < minPlayers) currentQueueCountdown = -1;
        // Return the player from the lobby dimension if they were sent there.
        if (playersInLobby.remove(id)) {
            player.setEntityInvulnerable(false);
            returnPlayerFromHG(player);
            if (playersInLobby.isEmpty()) destroyLobbyDimension(player.getServer());
        }
    }

    public synchronized void openHungerGamesMenu(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        boolean queued = queuedPlayers.contains(id) || playersInLobby.contains(id) || isPlayerInMatch(id);
        ModNetwork.CHANNEL.sendTo(new PacketOpenHungerGamesMenu(queued, queuedPlayers.size(), activeMatches.size()), player);
    }

    // -------------------------------------------------------------------------
    // Server Tick
    // -------------------------------------------------------------------------

    public void initMapsDirectory(File mapsDirectory) {
        if (mapManager == null) mapManager = new HungerGamesMapManager(mapsDirectory);
    }

    public void initMusicDirectory(File musicDirectory) {
        if (musicManager == null) musicManager = new HungerGamesMusicManager(musicDirectory);
    }

    public HungerGamesMusicManager getMusicManager() {
        return musicManager;
    }

    public void sendMusicManifest(net.minecraft.entity.player.EntityPlayerMP player) {
        if (musicManager == null) return;
        com.matoon.herosmp.network.ModNetwork.CHANNEL.sendTo(musicManager.buildManifest(), player);
    }

    public synchronized void tick(MinecraftServer server) {
        if (currentQueueCountdown > 0) {
            currentQueueCountdown--;
            if (currentQueueCountdown % 20 == 0) {
                int s = currentQueueCountdown / 20;
                if (s <= 10 || s % 5 == 0) {
                    broadcastQueueMessage(server, TextFormatting.AQUA + "Hunger Games starting in " + s + "s!");
                }
            }
            if (currentQueueCountdown <= 0) startMatchFromQueue(server);
        }

        for (Map.Entry<UUID, Integer> entry : new ArrayList<>(spectatorSetupCountdown.entrySet())) {
            UUID id = entry.getKey();
            int count = entry.getValue() - 1;
            if (count > 0) { spectatorSetupCountdown.put(id, count); continue; }
            spectatorSetupCountdown.remove(id);
            Integer matchId = pendingHGSpectators.remove(id);
            if (matchId == null) continue;
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(id);
            if (player == null) continue;
            HungerGamesMatch match = activeMatches.get(matchId);
            if (match == null || match.isEnded()) {
                if (PlayerDataIsolationManager.hasStoredState(id)) returnPlayerFromHG(player);
            } else {
                match.enterSpectatorMode(server, player);
                sendMessage(player, TextFormatting.GRAY + "Spectating — use "
                    + TextFormatting.WHITE + "/return" + TextFormatting.GRAY + " to leave.");
            }
        }

        // Delayed state restore: apply saved overworld state 2 ticks after changeDimension()
        // completes, giving the client time to finish loading the dimension before we
        // push inventory / capability packets.
        for (Map.Entry<UUID, Integer> entry : new ArrayList<>(pendingHGReturns.entrySet())) {
            UUID id = entry.getKey();
            int count = entry.getValue() - 1;
            if (count > 0) { pendingHGReturns.put(id, count); continue; }
            pendingHGReturns.remove(id);
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(id);
            if (player == null) { PlayerDataIsolationManager.clearStoredState(id); continue; }
            PlayerDataIsolationManager.restorePlayerState(player);
            sendMessage(player, TextFormatting.GREEN + "Returned from Hunger Games.");
        }

        for (HungerGamesMatch match : new ArrayList<>(activeMatches.values())) {
            match.tick(server);
            if (match.isEnded() && match.getPhase() == HungerGamesMatch.GamePhase.ENDING) {
                endMatch(server, match.getMatchId());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Match Creation
    // -------------------------------------------------------------------------

    private void startMatchFromQueue(MinecraftServer server) {
        List<EntityPlayerMP> players = new ArrayList<>();
        Set<UUID> lobbyPlayers = new HashSet<>();
        while (!queue.isEmpty() && players.size() < maxPlayers) {
            UUID id = queue.removeFirst();
            queuedPlayers.remove(id);
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
            if (p != null && !p.isDead) {
                players.add(p);
                if (playersInLobby.remove(id)) lobbyPlayers.add(id);
            }
        }
        currentQueueCountdown = -1;

        if (players.size() < minPlayers) {
            for (EntityPlayerMP p : players) {
                sendMessage(p, TextFormatting.RED + "Not enough players! Returning to queue...");
                // Return lobby players to overworld before re-queuing.
                if (lobbyPlayers.contains(p.getUniqueID())) returnPlayerFromHG(p);
                else queuePlayer(p);
            }
            if (playersInLobby.isEmpty()) destroyLobbyDimension(server);
            return;
        }
        // Destroy the lobby dim now — all its players are moving to the match dim.
        destroyLobbyDimension(server);
        startMatchFromLobby(server, players, lobbyPlayers);
    }

    /**
     * Like startMatch but for players coming from the lobby dimension.
     * Their overworld state is already saved, so we skip savePlayerState and just clear + teleport.
     */
    private void startMatchFromLobby(MinecraftServer server, List<EntityPlayerMP> players, Set<UUID> lobbyPlayers) {
        HungerGamesMapManager mgr = getMapManager(server);
        String selectedMap = defaultMapName != null ? defaultMapName : mgr.pickRandomMap();
        int dimensionId = allocateDimensionId();
        if (selectedMap != null) {
            File worldDir = server.getWorld(0).getSaveHandler().getWorldDirectory().getAbsoluteFile();
            mgr.copyMapWorldToDir(selectedMap, new File(worldDir, "DIM" + dimensionId));
        }
        DimensionType dimType = registerHGDimension(dimensionId);
        if (dimType == null) {
            // Fallback: return everyone to overworld
            for (EntityPlayerMP p : players) returnPlayerFromHG(p);
            return;
        }
        if (!DimensionManager.isDimensionRegistered(dimensionId)) {
            try { DimensionManager.registerDimension(dimensionId, dimType); }
            catch (RuntimeException e) { e.printStackTrace(); for (EntityPlayerMP p : players) returnPlayerFromHG(p); return; }
        }
        try { DimensionManager.initDimension(dimensionId); } catch (RuntimeException e) { e.printStackTrace(); }
        WorldServer hgWorld = server.getWorld(dimensionId);
        if (hgWorld == null) { for (EntityPlayerMP p : players) returnPlayerFromHG(p); return; }

        HungerGamesMapConfig cfg = selectedMap != null ? mgr.loadMapConfig(selectedMap) : new HungerGamesMapConfig("default");
        applyMatchChunkBounds(hgWorld, cfg);
        int matchId = matchIdCounter.getAndIncrement();
        HungerGamesMatch match = new HungerGamesMatch(matchId, dimensionId, selectedMap != null ? selectedMap : "default", players.size());
        match.setLootPools(cfg.getLootPhase1(), cfg.getLootPhase2(), cfg.getLootPhase3(), cfg.getLootAllPhases());
        match.setBreakableBlocks(cfg.getBreakableBlocks());
        match.setInjectionPhasePools(
                cfg.getInjectionPhase1(), cfg.getInjectionPhase2(),
                cfg.getInjectionPhase3(), cfg.getInjectionAllPhases());
        match.setInjectionRanges(
                cfg.getInjMinPhase1(), cfg.getInjMaxPhase1(),
                cfg.getInjMinPhase2(), cfg.getInjMaxPhase2(),
                cfg.getInjMinPhase3(), cfg.getInjMaxPhase3(),
                cfg.getInjMinAllPhases(), cfg.getInjMaxAllPhases());
        match.setMapCenter(cfg.getMapCenter());
        match.setWorldBorderStartRange(cfg.getWorldBorderStartRange());

        List<BlockPos> roundSpawns = cfg.getRoundSpawns().isEmpty()
            ? generateSpawnPoints(players.size()) : cfg.getRoundSpawns();
        BlockPos lobbySpawn = cfg.getLobbySpawn() != null ? cfg.getLobbySpawn() : roundSpawns.get(0);
        match.setLobbySpawnPoint(lobbySpawn);

        for (int i = 0; i < players.size(); i++) {
            EntityPlayerMP player = players.get(i);
            BlockPos roundSpawn = roundSpawns.get(i % roundSpawns.size());
            // State is already saved for lobby players; save now for non-lobby players.
            if (!lobbyPlayers.contains(player.getUniqueID())) {
                PlayerDataIsolationManager.savePlayerState(player);
            } else {
                // Remove lobby invulnerability — the match will apply its own.
                player.setEntityInvulnerable(false);
            }
            PlayerDataIsolationManager.clearPlayerState(player);
            match.addPlayer(player.getUniqueID(), roundSpawn);
            teleportToHGWorld(player, hgWorld, lobbySpawn);
            player.setGameType(GameType.SURVIVAL);
            match.sendBorderToPlayer(server, player);
        }
        if (musicManager != null) match.setMusicManager(musicManager);
        activeMatches.put(matchId, match);
        match.startMatch(server);
    }

    public synchronized int startMatch(MinecraftServer server, List<EntityPlayerMP> players, @Nullable String mapName) {
        HungerGamesMapManager mgr = getMapManager(server);

        String selectedMap = mapName;
        if (selectedMap == null || "default".equals(selectedMap) || "random".equals(selectedMap)) {
            selectedMap = defaultMapName != null ? defaultMapName : mgr.pickRandomMap();
        }

        int dimensionId = allocateDimensionId();

        if (selectedMap != null) {
            File worldDir = server.getWorld(0).getSaveHandler().getWorldDirectory().getAbsoluteFile();
            mgr.copyMapWorldToDir(selectedMap, new File(worldDir, "DIM" + dimensionId));
        }

        DimensionType dimType = registerHGDimension(dimensionId);
        if (dimType == null) return -1;
        if (!DimensionManager.isDimensionRegistered(dimensionId)) {
            try { DimensionManager.registerDimension(dimensionId, dimType); }
            catch (RuntimeException e) { e.printStackTrace(); return -1; }
        }
        try { DimensionManager.initDimension(dimensionId); } catch (RuntimeException e) { e.printStackTrace(); }

        WorldServer hgWorld = server.getWorld(dimensionId);
        if (hgWorld == null) return -1;

        HungerGamesMapConfig cfg = selectedMap != null
            ? mgr.loadMapConfig(selectedMap)
            : new HungerGamesMapConfig("default");

        applyMatchChunkBounds(hgWorld, cfg);
        int matchId = matchIdCounter.getAndIncrement();
        HungerGamesMatch match = new HungerGamesMatch(
            matchId, dimensionId, selectedMap != null ? selectedMap : "default", players.size());

        match.setLootPools(cfg.getLootPhase1(), cfg.getLootPhase2(), cfg.getLootPhase3(), cfg.getLootAllPhases());
        match.setBreakableBlocks(cfg.getBreakableBlocks());
        match.setInjectionPhasePools(
                cfg.getInjectionPhase1(), cfg.getInjectionPhase2(),
                cfg.getInjectionPhase3(), cfg.getInjectionAllPhases());
        match.setInjectionRanges(
                cfg.getInjMinPhase1(), cfg.getInjMaxPhase1(),
                cfg.getInjMinPhase2(), cfg.getInjMaxPhase2(),
                cfg.getInjMinPhase3(), cfg.getInjMaxPhase3(),
                cfg.getInjMinAllPhases(), cfg.getInjMaxAllPhases());
        match.setMapCenter(cfg.getMapCenter());
        match.setWorldBorderStartRange(cfg.getWorldBorderStartRange());

        List<BlockPos> roundSpawns = cfg.getRoundSpawns().isEmpty()
            ? generateSpawnPoints(players.size())
            : cfg.getRoundSpawns();

        BlockPos lobbySpawn = cfg.getLobbySpawn() != null ? cfg.getLobbySpawn() : roundSpawns.get(0);
        match.setLobbySpawnPoint(lobbySpawn);

        for (int i = 0; i < players.size(); i++) {
            EntityPlayerMP player = players.get(i);
            BlockPos roundSpawn = roundSpawns.get(i % roundSpawns.size());
            PlayerDataIsolationManager.savePlayerState(player);
            PlayerDataIsolationManager.clearPlayerState(player);
            match.addPlayer(player.getUniqueID(), roundSpawn);
            teleportToHGWorld(player, hgWorld, lobbySpawn);
            player.setGameType(GameType.SURVIVAL);
            match.sendBorderToPlayer(server, player);
        }

        if (musicManager != null) match.setMusicManager(musicManager);
        activeMatches.put(matchId, match);
        match.startMatch(server);
        return matchId;
    }

    public synchronized void startSoloMatch(MinecraftServer server, EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        if (isPlayerInMatch(id)) {
            sendMessage(player, TextFormatting.RED + "You are already in a match!");
            return;
        }
        if (configureSessions.containsKey(id)) {
            sendMessage(player, TextFormatting.RED + "Exit configure mode first.");
            return;
        }
        sendMessage(player, TextFormatting.GOLD + "Starting solo debug match...");
        startMatch(server, Collections.singletonList(player), null);
    }

    // -------------------------------------------------------------------------
    // Configure Map Session
    // -------------------------------------------------------------------------

    public synchronized void startConfigureMapSession(MinecraftServer server, EntityPlayerMP player, String mapName) {
        UUID id = player.getUniqueID();
        if (configureSessions.containsKey(id)) {
            sendMessage(player, TextFormatting.RED + "Already in configure mode. Use /heropvp hg debug endconfigure to exit.");
            return;
        }
        if (isPlayerInMatch(id)) {
            sendMessage(player, TextFormatting.RED + "You are currently in an active match!");
            return;
        }

        HungerGamesMapManager mgr = getMapManager(server);
        File mapDir = mgr.getMapDir(mapName);
        if (!mapDir.exists()) {
            mapDir.mkdirs();
            sendMessage(player, TextFormatting.YELLOW + "Created new map folder: " + mapName);
        }

        HungerGamesMapConfig existing = mgr.loadMapConfig(mapName);
        int dimensionId = allocateDimensionId();

        if (mgr.getAvailableMaps().contains(mapName)) {
            File worldDir = server.getWorld(0).getSaveHandler().getWorldDirectory().getAbsoluteFile();
            mgr.copyMapWorldToDir(mapName, new File(worldDir, "DIM" + dimensionId));
        }

        DimensionType dimType = registerHGDimension(dimensionId);
        if (dimType == null) {
            sendMessage(player, TextFormatting.RED + "Failed to create configure dimension.");
            return;
        }
        if (!DimensionManager.isDimensionRegistered(dimensionId)) {
            try { DimensionManager.registerDimension(dimensionId, dimType); }
            catch (RuntimeException e) {
                e.printStackTrace();
                sendMessage(player, TextFormatting.RED + "Failed to register configure dimension.");
                return;
            }
        }
        try { DimensionManager.initDimension(dimensionId); } catch (RuntimeException e) { e.printStackTrace(); }

        WorldServer hgWorld = server.getWorld(dimensionId);
        if (hgWorld == null) {
            sendMessage(player, TextFormatting.RED + "Failed to load map world.");
            return;
        }

        HungerGamesConfigureMapSession session = new HungerGamesConfigureMapSession(id, mapName, dimensionId, existing);

        PlayerDataIsolationManager.savePlayerState(player);
        PlayerDataIsolationManager.clearPlayerState(player);
        try {
            BlockPos worldSpawn = mgr.readWorldSpawn(mapName);
            BlockPos spawnPos = existing.getLobbySpawn() != null ? existing.getLobbySpawn()
                              : worldSpawn != null ? worldSpawn : new BlockPos(0, 64, 0);
            teleportToHGWorld(player, hgWorld, spawnPos);
            player.setGameType(GameType.CREATIVE);
            giveConfigureTools(player);
            applyConfigureModeBorder(hgWorld, session.getPendingConfig());
        } catch (Exception e) {
            PlayerDataIsolationManager.restorePlayerState(player);
            destroyHGDimension(server, dimensionId);
            sendMessage(player, TextFormatting.RED + "Failed to enter configure mode. Try again.");
            e.printStackTrace();
            return;
        }

        configureSessions.put(id, session);

        sendMessage(player, TextFormatting.GOLD + "Entered configure mode: " + TextFormatting.WHITE + mapName);
        if (LOBBY_MAP_NAME.equals(mapName)) {
            sendMessage(player, TextFormatting.GRAY + "Hotbar: [1] Lobby Spawn");
        } else {
            sendMessage(player, TextFormatting.GRAY + "Hotbar: [1] Spawns  [2] Lobby  [3] Loot Pool  [4] Loot Properties  [5] Map Center  [6] Breakable Blocks  [7] Injections");
        }
        sendMessage(player, TextFormatting.GRAY + "Use /heropvp hg debug endconfigure to save & exit.");
    }

    public synchronized void endConfigureMapSession(MinecraftServer server, EntityPlayerMP player, boolean save) {
        UUID id = player.getUniqueID();
        HungerGamesConfigureMapSession session = configureSessions.remove(id);
        pendingChatInputs.remove(id);

        if (session == null) {
            sendMessage(player, TextFormatting.YELLOW + "You are not in configure mode.");
            return;
        }

        if (save) {
            // Flush chunks to disk before copying
            WorldServer hgWorld = server.getWorld(session.getDimensionId());
            if (hgWorld != null) {
                try { hgWorld.saveAllChunks(true, null); } catch (Exception e) { e.printStackTrace(); }
            }
            getMapManager(server).saveMapConfig(session.getPendingConfig());
            // Copy edited world back to the maps folder
            File worldDir = server.getWorld(0).getSaveHandler().getWorldDirectory().getAbsoluteFile();
            File dimDir = new File(worldDir, "DIM" + session.getDimensionId());
            getMapManager(server).saveDimToMapDir(session.getMapName(), dimDir);
            defaultMapName = session.getMapName();

            HungerGamesMapConfig cfg = session.getPendingConfig();
            sendMessage(player, TextFormatting.GREEN + "Saved '" + session.getMapName() + "': "
                + cfg.getRoundSpawns().size() + " spawns, "
                + (cfg.getLobbySpawn() != null ? "lobby set" : "no lobby") + ", "
                + (cfg.getMapCenter() != null ? "center set" : "no center") + ", "
                + "border " + cfg.getWorldBorderStartRange() + ", "
                + (cfg.getLootPhase1().size() + cfg.getLootPhase2().size()
                   + cfg.getLootPhase3().size() + cfg.getLootAllPhases().size()) + " loot, "
                + cfg.getBreakableBlocks().size() + " breakable blocks, "
                + (cfg.getInjectionPhase1().size() + cfg.getInjectionPhase2().size()
                   + cfg.getInjectionPhase3().size() + cfg.getInjectionAllPhases().size()) + " injections.");
        }

        returnPlayerFromHG(player);
        destroyHGDimension(server, session.getDimensionId());
    }

    // -------------------------------------------------------------------------
    // Configure Tool Interaction
    // -------------------------------------------------------------------------

    public synchronized void handleConfigureToolUse(EntityPlayerMP player, String toolType, @Nullable BlockPos clickedBlock) {
        HungerGamesConfigureMapSession session = configureSessions.get(player.getUniqueID());
        if (session == null) return;

        HungerGamesMapConfig config = session.getPendingConfig();
        switch (toolType) {
            case "round_spawn":
                if (clickedBlock != null) {
                    BlockPos pos = clickedBlock.up();
                    if (config.hasRoundSpawn(pos)) {
                        config.removeRoundSpawn(pos);
                        sendMessage(player, TextFormatting.RED + "Removed round spawn. ("
                            + config.getRoundSpawns().size() + " total)");
                    } else {
                        config.addRoundSpawn(pos);
                        sendMessage(player, TextFormatting.GREEN + "Added round spawn at "
                            + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                            + " (" + config.getRoundSpawns().size() + " total)");
                    }
                }
                break;

            case "lobby_spawn":
                if (clickedBlock != null) {
                    BlockPos pos = clickedBlock.up();
                    config.setLobbySpawn(pos);
                    sendMessage(player, TextFormatting.GREEN + "Lobby spawn set at "
                        + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
                }
                break;

            case "loot_pool":
                openLootPoolEditor(player, session);
                break;

            case "loot_properties":
                openLootPropertiesEditor(player, session);
                break;

            case "breakable_blocks":
                openBreakableBlocksEditor(player, session);
                break;

            case "injections":
                openInjectionsEditor(player, session);
                break;

            case "injection_properties":
                openInjectionPropertiesEditor(player, session);
                break;

            case "map_center":
                if (clickedBlock != null) {
                    config.setMapCenter(clickedBlock);
                    sendMessage(player, TextFormatting.GREEN + "Map center set at "
                        + clickedBlock.getX() + ", " + clickedBlock.getZ()
                        + ". Right-click air to set border range.");
                    WorldServer hgWorld = player.getServer().getWorld(session.getDimensionId());
                    if (hgWorld != null) applyConfigureModeBorder(hgWorld, config);
                } else {
                    pendingChatInputs.put(player.getUniqueID(), "world_border_range");
                    sendMessage(player, TextFormatting.YELLOW
                        + "Type the world border RADIUS in chat (blocks from center, e.g. 200):");
                }
                break;
        }
    }

    /**
     * Handle a chat message from a player. Returns true if consumed (cancel the chat event).
     */
    public synchronized boolean handlePlayerChat(EntityPlayerMP player, String message) {
        UUID id = player.getUniqueID();
        String inputType = pendingChatInputs.remove(id);
        if (inputType == null) return false;

        if ("world_border_range".equals(inputType)) {
            try {
                int range = Integer.parseInt(message.trim());
                if (range < 10 || range > 30000) {
                    sendMessage(player, TextFormatting.RED + "Range must be between 10 and 30000.");
                    return true;
                }
                HungerGamesConfigureMapSession session = configureSessions.get(id);
                if (session == null) return true;
                session.getPendingConfig().setWorldBorderStartRange(range);
                sendMessage(player, TextFormatting.GREEN + "World border range set to " + range + " blocks.");
                WorldServer hgWorld = player.getServer().getWorld(session.getDimensionId());
                if (hgWorld != null) applyConfigureModeBorder(hgWorld, session.getPendingConfig());
            } catch (NumberFormatException e) {
                sendMessage(player, TextFormatting.RED + "Invalid number: " + message);
            }
        }
        return true;
    }

    private void applyConfigureModeBorder(WorldServer world, HungerGamesMapConfig config) {
        if (config.getMapCenter() == null || config.getWorldBorderStartRange() <= 0) return;
        WorldBorder border = world.getWorldBorder();
        border.setCenter(config.getMapCenter().getX(), config.getMapCenter().getZ());
        border.setTransition(config.getWorldBorderStartRange() * 2);
    }

    private void openLootPoolEditor(EntityPlayerMP player, HungerGamesConfigureMapSession session) {
        HungerGamesMapConfig cfg = session.getPendingConfig();
        HungerGamesMapLootInventory inv = new HungerGamesMapLootInventory(session.getMapName());
        inv.loadTabContents(cfg.getLootPhase1(), cfg.getLootPhase2(),
                            cfg.getLootPhase3(), cfg.getLootAllPhases());
        openLootInventories.put(player.getUniqueID(), inv);
        player.displayGUIChest(inv);
    }

    private void openLootPropertiesEditor(EntityPlayerMP player, HungerGamesConfigureMapSession session) {
        HungerGamesMapConfig cfg = session.getPendingConfig();
        HungerGamesLootPropertiesInventory propInv = new HungerGamesLootPropertiesInventory(
                cfg.getLootPhase1(), cfg.getLootPhase2(),
                cfg.getLootPhase3(), cfg.getLootAllPhases());
        player.displayGUIChest(propInv);
    }

    private void openBreakableBlocksEditor(EntityPlayerMP player, HungerGamesConfigureMapSession session) {
        HungerGamesMapBreakableInventory inv = new HungerGamesMapBreakableInventory(session.getMapName());
        List<ItemStack> blocks = session.getPendingConfig().getBreakableBlocks();
        for (int i = 0; i < inv.getSizeInventory() && i < blocks.size(); i++) {
            inv.setInventorySlotContents(i, blocks.get(i).copy());
        }
        player.displayGUIChest(inv);
    }

    private void openInjectionsEditor(EntityPlayerMP player, HungerGamesConfigureMapSession session) {
        LucraftInjectionInventory inv = new LucraftInjectionInventory(session.getMapName());
        HungerGamesMapConfig cfg = session.getPendingConfig();
        inv.loadTabContents(cfg.getInjectionPhase1(), cfg.getInjectionPhase2(),
                            cfg.getInjectionPhase3(), cfg.getInjectionAllPhases());
        openInjectionInventories.put(player.getUniqueID(), inv);
        player.displayGUIChest(inv);
    }

    private void openInjectionPropertiesEditor(EntityPlayerMP player, HungerGamesConfigureMapSession session) {
        HungerGamesMapConfig cfg = session.getPendingConfig();
        LucraftInjectionPropertiesInventory propInv = new LucraftInjectionPropertiesInventory(
                cfg.getInjectionPhase1(), cfg.getInjectionPhase2(),
                cfg.getInjectionPhase3(), cfg.getInjectionAllPhases(),
                cfg.getInjMinPhase1(), cfg.getInjMaxPhase1(),
                cfg.getInjMinPhase2(), cfg.getInjMaxPhase2(),
                cfg.getInjMinPhase3(), cfg.getInjMaxPhase3(),
                cfg.getInjMinAllPhases(), cfg.getInjMaxAllPhases());
        openInjectionPropInventories.put(player.getUniqueID(), propInv);
        player.displayGUIChest(propInv);
    }

    public synchronized void handleLootInventoryClose(EntityPlayerMP player, Container container) {
        UUID id = player.getUniqueID();
        HungerGamesConfigureMapSession session = configureSessions.get(id);

        // --- Loot pool chest closed ---
        if (container instanceof HungerGamesLootContainer) {
            HungerGamesMapLootInventory inv = ((HungerGamesLootContainer) container).getLootInventory();
            openLootInventories.remove(id);
            if (session == null) return;

            HungerGamesMapConfig cfg = session.getPendingConfig();
            int total = 0;
            List<ItemStack> p1  = inv.getTabItems(0); cfg.setLootPhase1(p1);    total += p1.size();
            List<ItemStack> p2  = inv.getTabItems(1); cfg.setLootPhase2(p2);    total += p2.size();
            List<ItemStack> p3  = inv.getTabItems(2); cfg.setLootPhase3(p3);    total += p3.size();
            List<ItemStack> all = inv.getTabItems(3); cfg.setLootAllPhases(all); total += all.size();
            sendMessage(player, TextFormatting.GREEN + "Loot pools saved: " + total + " item types.");
            giveConfigureTools(player);
            return;
        }

        // --- Loot properties editor closed ---
        if (container instanceof HungerGamesLootPropertiesContainer) {
            HungerGamesLootPropertiesInventory propInv =
                ((HungerGamesLootPropertiesContainer) container).getPropInv();
            if (session == null) return;
            // Write all four phase lists back into the config.
            HungerGamesMapConfig cfg = session.getPendingConfig();
            cfg.setLootPhase1(propInv.getPhase(0));
            cfg.setLootPhase2(propInv.getPhase(1));
            cfg.setLootPhase3(propInv.getPhase(2));
            cfg.setLootAllPhases(propInv.getPhase(3));
            sendMessage(player, TextFormatting.GREEN + "Loot properties saved.");
            giveConfigureTools(player);
            return;
        }

        // --- Injection pool editor closed (saves all 4 phase tabs) ---
        // Only handle if this container was opened by the HG configure-map system.
        // Admins may run /heropvp injections while in configure mode; that opens a PVP editor
        // which is a different LucraftInjectionInventory instance — ignore it here.
        if (container instanceof LucraftInjectionContainer) {
            LucraftInjectionInventory inv = ((LucraftInjectionContainer) container).getInjectionInventory();
            boolean isHgEditor = openInjectionInventories.get(id) == inv;
            openInjectionInventories.remove(id);
            if (!isHgEditor || session == null) return;
            HungerGamesMapConfig cfg = session.getPendingConfig();
            List<net.minecraft.item.ItemStack> p1  = inv.getTabItems(0); cfg.setInjectionPhase1(p1);
            List<net.minecraft.item.ItemStack> p2  = inv.getTabItems(1); cfg.setInjectionPhase2(p2);
            List<net.minecraft.item.ItemStack> p3  = inv.getTabItems(2); cfg.setInjectionPhase3(p3);
            List<net.minecraft.item.ItemStack> all = inv.getTabItems(3); cfg.setInjectionAllPhases(all);
            int total = p1.size() + p2.size() + p3.size() + all.size();
            sendMessage(player, TextFormatting.GREEN + "Injection pools saved: " + total + " injection(s) across all phases.");
            giveConfigureTools(player);
            return;
        }

        // --- Injection properties editor closed ---
        // Only handle if this container was opened by the HG configure-map system.
        if (container instanceof LucraftInjectionPropertiesContainer) {
            LucraftInjectionPropertiesInventory propInv =
                    ((LucraftInjectionPropertiesContainer) container).getPropInv();
            boolean isHgEditor = openInjectionPropInventories.get(id) == propInv;
            openInjectionPropInventories.remove(id);
            if (!isHgEditor || session == null) return;
            HungerGamesMapConfig cfg = session.getPendingConfig();
            cfg.setInjectionPhase1(propInv.getPhase(0));
            cfg.setInjectionPhase2(propInv.getPhase(1));
            cfg.setInjectionPhase3(propInv.getPhase(2));
            cfg.setInjectionAllPhases(propInv.getPhase(3));
            cfg.setInjMinPhase1(propInv.getGlobalMin(0)); cfg.setInjMaxPhase1(propInv.getGlobalMax(0));
            cfg.setInjMinPhase2(propInv.getGlobalMin(1)); cfg.setInjMaxPhase2(propInv.getGlobalMax(1));
            cfg.setInjMinPhase3(propInv.getGlobalMin(2)); cfg.setInjMaxPhase3(propInv.getGlobalMax(2));
            cfg.setInjMinAllPhases(propInv.getGlobalMin(3)); cfg.setInjMaxAllPhases(propInv.getGlobalMax(3));
            int total = propInv.getPhase(0).size() + propInv.getPhase(1).size()
                      + propInv.getPhase(2).size() + propInv.getPhase(3).size();
            sendMessage(player, TextFormatting.GREEN + "Injection properties saved: " + total + " injection(s) across all phases.");
            giveConfigureTools(player);
            return;
        }

        // --- Breakable blocks chest closed ---
        if (container instanceof ContainerChest) {
            ContainerChest chest = (ContainerChest) container;
            if (session == null) return;
            if (chest.getLowerChestInventory() instanceof HungerGamesMapBreakableInventory) {
                List<ItemStack> items = new ArrayList<>();
                for (int i = 0; i < chest.getLowerChestInventory().getSizeInventory(); i++) {
                    ItemStack stack = chest.getLowerChestInventory().getStackInSlot(i);
                    if (!stack.isEmpty()) items.add(stack.copy());
                }
                session.getPendingConfig().setBreakableBlocks(items);
                sendMessage(player, TextFormatting.GREEN + "Breakable blocks updated: " + items.size() + " block types.");
            }
        }
    }

    private static List<ItemStack> getPhase(HungerGamesMapConfig cfg, int tab) {
        switch (tab) {
            case 0: return cfg.getLootPhase1();
            case 1: return cfg.getLootPhase2();
            case 2: return cfg.getLootPhase3();
            default: return cfg.getLootAllPhases();
        }
    }

    private static void setPhase(HungerGamesMapConfig cfg, int tab, List<ItemStack> phase) {
        switch (tab) {
            case 0: cfg.setLootPhase1(phase);    break;
            case 1: cfg.setLootPhase2(phase);    break;
            case 2: cfg.setLootPhase3(phase);    break;
            default: cfg.setLootAllPhases(phase); break;
        }
    }

    public boolean isInConfigureMode(UUID playerId) {
        return configureSessions.containsKey(playerId);
    }

    private void giveConfigureTools(EntityPlayerMP player) {
        HungerGamesConfigureMapSession session = configureSessions.get(player.getUniqueID());
        boolean isLobby = session != null && LOBBY_MAP_NAME.equals(session.getMapName());

        if (isLobby) {
            // Lobby only needs a spawn point — no round spawns, loot, border, or breakables.
            player.inventory.setInventorySlotContents(0,
                createConfigTool(new ItemStack(Items.COMPASS), TextFormatting.AQUA + "Set Lobby Spawn", "lobby_spawn"));
        } else {
            player.inventory.setInventorySlotContents(0,
                createConfigTool(new ItemStack(Items.ENDER_PEARL),  TextFormatting.GREEN  + "Set Round Spawn", "round_spawn"));
            player.inventory.setInventorySlotContents(1,
                createConfigTool(new ItemStack(Items.COMPASS),      TextFormatting.AQUA   + "Set Lobby Spawn", "lobby_spawn"));
            player.inventory.setInventorySlotContents(2,
                createConfigTool(new ItemStack(Items.EMERALD),      TextFormatting.YELLOW + "Loot Pool",       "loot_pool"));
            player.inventory.setInventorySlotContents(3,
                createConfigTool(new ItemStack(Items.GOLD_NUGGET),  TextFormatting.GOLD   + "Loot Properties", "loot_properties"));
            player.inventory.setInventorySlotContents(4,
                createConfigTool(new ItemStack(Items.WOODEN_AXE),  TextFormatting.GOLD   + "Set Map Center / Border Range", "map_center"));
            player.inventory.setInventorySlotContents(5,
                createConfigTool(new ItemStack(Items.IRON_PICKAXE), TextFormatting.AQUA  + "Breakable Blocks", "breakable_blocks"));
            player.inventory.setInventorySlotContents(6,
                createConfigTool(new ItemStack(Items.GLASS_BOTTLE), TextFormatting.LIGHT_PURPLE + "Injections", "injections"));
            player.inventory.setInventorySlotContents(7,
                createConfigTool(new ItemStack(Items.NETHER_STAR), TextFormatting.LIGHT_PURPLE + "Injection Properties", "injection_properties"));
        }
    }

    private static ItemStack createConfigTool(ItemStack base, String displayName, String toolId) {
        base.setStackDisplayName(displayName);
        if (!base.hasTagCompound()) base.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        base.getTagCompound().setString("HGConfigTool", toolId);
        return base;
    }

    @Nullable
    public static String getConfigToolType(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTagCompound()) return null;
        String type = stack.getTagCompound().getString("HGConfigTool");
        return type.isEmpty() ? null : type;
    }

    // -------------------------------------------------------------------------
    // End Match
    // -------------------------------------------------------------------------

    public synchronized void endMatch(MinecraftServer server, int matchId) {
        HungerGamesMatch match = activeMatches.remove(matchId);
        if (match == null) return;

        int dimensionId = match.getDimensionId();
        for (UUID id : match.getPlayerOrder()) {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
            if (p != null && p.dimension == dimensionId) returnPlayerFromHG(p);
        }
        for (UUID id : match.getSpectators()) {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
            if (p != null && p.dimension == dimensionId) returnPlayerFromHG(p);
        }
        match.cleanup(server);
        destroyHGDimension(server, dimensionId);
    }

    // -------------------------------------------------------------------------
    // Event Handlers
    // -------------------------------------------------------------------------

    public synchronized void handlePlayerDeath(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        HungerGamesMatch match = findMatchByPlayer(id);
        if (match == null) return;

        match.removePlayer(id);
        // Queue player to become a spectator on respawn (if match is still ongoing)
        pendingHGSpectators.put(id, match.getMatchId());

        MinecraftServer server = player.getServer();
        String msg = TextFormatting.RED + player.getName() + " was eliminated! "
                   + TextFormatting.GRAY + match.getAlivePlayers().size() + " remaining.";
        match.getPlayerOrder().forEach(pid -> {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(pid);
            if (p != null && p.dimension == match.getDimensionId()) sendMessage(p, msg);
        });
        match.getSpectators().forEach(pid -> {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(pid);
            if (p != null && p.dimension == match.getDimensionId()) sendMessage(p, msg);
        });
    }

    public synchronized void handlePlayerRespawn(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        if (pendingHGSpectators.containsKey(id)) {
            spectatorSetupCountdown.put(id, 3);
        }
    }

    public synchronized void handlePlayerLogout(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        queuedPlayers.remove(id);
        queue.remove(id);
        pendingChatInputs.remove(id);
        pendingHGSpectators.remove(id);
        spectatorSetupCountdown.remove(id);
        pendingHGReturns.remove(id);
        playersInLobby.remove(id);
        // If last lobby player logged out, clean up the lobby dim.
        if (playersInLobby.isEmpty() && lobbyDimensionId != Integer.MIN_VALUE
                && queuedPlayers.isEmpty()) {
            destroyLobbyDimension(player.getServer());
        }

        // Remove from match (player is eliminated), but KEEP stored state so
        // handlePlayerLogin can restore them to the overworld on reconnect.
        HungerGamesMatch match = findMatchByPlayer(id);
        if (match != null) match.removePlayer(id);

        // Destroy configure session dimension; stored state is kept for login handler.
        HungerGamesConfigureMapSession session = configureSessions.remove(id);
        if (session != null) destroyHGDimension(player.getServer(), session.getDimensionId());
    }

    /**
     * Called when a player logs back in. If they have a stored overworld state
     * (from a previous HG session), restore it and teleport them home.
     */
    public synchronized void handlePlayerLogin(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        if (!PlayerDataIsolationManager.hasStoredState(id)) return;

        MinecraftServer server = player.getServer();
        int returnDim       = PlayerDataIsolationManager.getReturnDimension(id);
        double[] returnPos  = PlayerDataIsolationManager.getReturnPosition(id);
        float[]  returnRot  = PlayerDataIsolationManager.getReturnRotation(id);

        WorldServer returnWorld = server.getWorld(returnDim);
        if (returnWorld == null) {
            returnWorld = server.getWorld(0);
            returnPos   = new double[]{0, 64, 0};
            returnRot   = new float[]{0, 0};
        }

        final WorldServer world = returnWorld;
        final double[] pos     = returnPos;
        final float[]  rot     = returnRot;

        player.changeDimension(world.provider.getDimension(),
            new FixedTeleporter(world, pos[0], pos[1], pos[2], rot[0], rot[1]));
        // Queue delayed state restore (same pattern as returnPlayerFromHG).
        pendingHGReturns.put(id, 2);
    }

    // -------------------------------------------------------------------------
    // Public helpers
    // -------------------------------------------------------------------------

    /**
     * Try to return a player from HG (match or configure session).
     * Returns true if handled, false if the player was not in HG.
     */
    public synchronized boolean tryReturnPlayer(MinecraftServer server, EntityPlayerMP player) {
        UUID id = player.getUniqueID();

        HungerGamesConfigureMapSession session = configureSessions.get(id);
        if (session != null) {
            endConfigureMapSession(server, player, true);
            return true;
        }

        HungerGamesMatch match = findMatchByPlayer(id);
        if (match != null) {
            match.clearPlayerUi(player);
            // Use fullyRemovePlayer so playerOrder is also cleared; this ensures
            // findMatchByPlayer() no longer returns this match, allowing re-queue.
            match.fullyRemovePlayer(id);
            // Clear any pending spectator setup so the player can re-queue immediately.
            pendingHGSpectators.remove(id);
            spectatorSetupCountdown.remove(id);
            returnPlayerFromHG(player);
            return true;
        }

        if (PlayerDataIsolationManager.hasStoredState(id)) {
            pendingHGSpectators.remove(id);
            spectatorSetupCountdown.remove(id);
            // If player was in the lobby, remove them from lobby tracking.
            if (playersInLobby.remove(id)) {
                player.setEntityInvulnerable(false);
                queuedPlayers.remove(id);
                queue.remove(id);
                if (queuedPlayers.size() < minPlayers) currentQueueCountdown = -1;
                if (playersInLobby.isEmpty()) destroyLobbyDimension(server);
            }
            returnPlayerFromHG(player);
            return true;
        }

        return false;
    }

    public boolean isPlayerInMatch(UUID playerId) {
        return findMatchByPlayer(playerId) != null;
    }

    public boolean isPlayerInLobby(UUID playerId) {
        return playersInLobby.contains(playerId);
    }

    /** Returns true if the player is queued for or actively in a Hunger Games match or in the lobby. */
    public synchronized boolean isPlayerInMatchOrQueue(UUID playerId) {
        return queuedPlayers.contains(playerId) || isPlayerInMatch(playerId) || playersInLobby.contains(playerId);
    }

    /**
     * Returns the dimension ID of the HG match (or lobby) the player is in, or 0 if not in HG.
     * Used to allow the initial teleport-in without triggering the escape check.
     */
    public synchronized int getPlayerMatchDimension(UUID playerId) {
        HungerGamesMatch match = findMatchByPlayer(playerId);
        if (match != null) return match.getDimensionId();
        if (playersInLobby.contains(playerId) && lobbyDimensionId != Integer.MIN_VALUE) return lobbyDimensionId;
        return 0;
    }

    /** Returns true if the given dimension ID is an active HG match or lobby dimension. */
    public boolean isHGDimension(int dimId) {
        if (dimId == lobbyDimensionId) return true;
        for (HungerGamesMatch match : activeMatches.values()) {
            if (match.getDimensionId() == dimId) return true;
        }
        return false;
    }

    public boolean isHGSpectatorBat(net.minecraft.entity.Entity entity) {
        for (HungerGamesMatch match : activeMatches.values()) {
            if (match.isSpectatorBat(entity)) return true;
        }
        return false;
    }

    public boolean canBreakBlock(UUID playerId, BlockPos pos, net.minecraft.world.World world) {
        HungerGamesMatch match = findMatchByPlayer(playerId);
        if (match == null) return true;
        if (match.isPlayerPlacedBlock(pos)) return true;
        return match.isBreakableBlock(world.getBlockState(pos).getBlock());
    }

    public void trackBlockPlaced(UUID playerId, BlockPos pos) {
        HungerGamesMatch match = findMatchByPlayer(playerId);
        if (match != null) match.addPlayerPlacedBlock(pos);
    }

    public void trackBlockBroken(UUID playerId, BlockPos pos) {
        HungerGamesMatch match = findMatchByPlayer(playerId);
        if (match != null) match.removePlayerPlacedBlock(pos);
    }

    // -------------------------------------------------------------------------
    // Lobby dimension
    // -------------------------------------------------------------------------

    /**
     * If a map named "Lobby" exists, save this player's state and teleport them
     * into the shared lobby dimension, creating it first if necessary.
     */
    private void sendToLobbyIfAvailable(EntityPlayerMP player) {
        MinecraftServer server = player.getServer();
        HungerGamesMapManager mgr = getMapManager(server);
        if (!mgr.getMapDir(LOBBY_MAP_NAME).exists()) return; // No lobby map configured

        // Create lobby dimension on first use.
        if (lobbyDimensionId == Integer.MIN_VALUE) {
            lobbyDimensionId = allocateDimensionId();
            File worldDir = server.getWorld(0).getSaveHandler().getWorldDirectory().getAbsoluteFile();
            mgr.copyMapWorldToDir(LOBBY_MAP_NAME, new File(worldDir, "DIM" + lobbyDimensionId));
            DimensionType dimType = registerHGDimension(lobbyDimensionId);
            if (dimType == null) { lobbyDimensionId = Integer.MIN_VALUE; return; }
            if (!DimensionManager.isDimensionRegistered(lobbyDimensionId)) {
                try { DimensionManager.registerDimension(lobbyDimensionId, dimType); }
                catch (RuntimeException e) { e.printStackTrace(); lobbyDimensionId = Integer.MIN_VALUE; return; }
            }
            try { DimensionManager.initDimension(lobbyDimensionId); } catch (RuntimeException e) { e.printStackTrace(); }
            // Load lobby spawn from config.
            HungerGamesMapConfig lobbyCfg = mgr.loadMapConfig(LOBBY_MAP_NAME);
            lobbySpawnPoint = lobbyCfg.getLobbySpawn() != null ? lobbyCfg.getLobbySpawn() : new BlockPos(0, 64, 0);
        }

        WorldServer lobbyWorld = server.getWorld(lobbyDimensionId);
        if (lobbyWorld == null) return;

        PlayerDataIsolationManager.savePlayerState(player);
        PlayerDataIsolationManager.clearPlayerState(player);
        teleportToHGWorld(player, lobbyWorld, lobbySpawnPoint);
        player.setGameType(GameType.SURVIVAL);
        player.setEntityInvulnerable(true);
        playersInLobby.add(player.getUniqueID());
    }

    private void destroyLobbyDimension(MinecraftServer server) {
        if (lobbyDimensionId == Integer.MIN_VALUE) return;
        destroyHGDimension(server, lobbyDimensionId);
        lobbyDimensionId = Integer.MIN_VALUE;
        lobbySpawnPoint = null;
    }

    // -------------------------------------------------------------------------
    // Teleportation
    // -------------------------------------------------------------------------

    private void teleportToHGWorld(EntityPlayerMP player, WorldServer hgWorld, BlockPos spawn) {
        player.changeDimension(hgWorld.provider.getDimension(),
            new FixedTeleporter(hgWorld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0, 0));
        player.setHealth(20.0F);
        player.getFoodStats().setFoodLevel(20);
    }

    private void returnPlayerFromHG(EntityPlayerMP player) {
        // Wipe all arena state immediately — inventory, effects, superpowers, XP.
        // restorePlayerState() will also clear before restoring, but doing it here
        // ensures nothing leaks during the 2-tick dimension-transition delay.
        PlayerDataIsolationManager.clearPlayerState(player);

        UUID id = player.getUniqueID();
        int       returnDim = PlayerDataIsolationManager.getReturnDimension(id);
        double[]  returnPos = PlayerDataIsolationManager.getReturnPosition(id);
        float[]   returnRot = PlayerDataIsolationManager.getReturnRotation(id);

        WorldServer returnWorld = player.getServer().getWorld(returnDim);
        if (returnWorld == null) {
            returnWorld = player.getServer().getWorld(0);
            returnPos   = new double[]{0, 64, 0};
            returnRot   = new float[]{0, 0};
        }
        player.changeDimension(returnDim,
            new FixedTeleporter(returnWorld, returnPos[0], returnPos[1], returnPos[2],
                returnRot[0], returnRot[1]));
        // Delay state restore by 2 ticks so the client finishes the dimension transition
        // before inventory/capability packets arrive (prevents invisible-player / missing-items bug).
        pendingHGReturns.put(id, 2);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<BlockPos> generateSpawnPoints(int count) {
        List<BlockPos> spawns = new ArrayList<>();
        int radius = 50;
        double step = (2 * Math.PI) / Math.max(count, 1);
        for (int i = 0; i < count; i++) {
            double angle = i * step;
            spawns.add(new BlockPos((int)(Math.cos(angle) * radius), 100, (int)(Math.sin(angle) * radius)));
        }
        return spawns;
    }

    /**
     * Sets the match-mode chunk boundary on the world provider so that
     * {@link com.matoon.herosmp.hungergames.world.HungerGamesChunkGenerator} can void
     * chunks outside the playable area + padding.
     * Only called for actual matches — configure sessions intentionally skip this.
     */
    private void applyMatchChunkBounds(WorldServer hgWorld, HungerGamesMapConfig cfg) {
        if (!(hgWorld.provider instanceof HungerGamesWorldProvider)) return;
        BlockPos center = cfg.getMapCenter();
        if (center == null) return; // no center set — map editor hasn't configured it yet
        HungerGamesWorldProvider provider = (HungerGamesWorldProvider) hgWorld.provider;
        // Convert block-level center to chunk coordinates.
        provider.matchCenterChunkX = center.getX() >> 4;
        provider.matchCenterChunkZ = center.getZ() >> 4;
        // worldBorderStartRange is a radius in blocks; convert to chunks (rounding up).
        provider.matchBorderChunks = (cfg.getWorldBorderStartRange() + 15) >> 4;
    }

    private int allocateDimensionId() {
        return HG_BASE_DIMENSION_ID - dimensionIdCounter.getAndIncrement();
    }

    @Nullable
    private DimensionType registerHGDimension(int dimensionId) {
        if (registeredDimensionTypes.containsKey(dimensionId)) return registeredDimensionTypes.get(dimensionId);
        try {
            String name   = HG_DIMENSION_TYPE_PREFIX + Math.abs(dimensionId);
            String suffix = "_hg_" + Math.abs(dimensionId);
            int typeId    = HG_DIMENSION_TYPE_BASE_ID + Math.abs(dimensionId - HG_BASE_DIMENSION_ID);
            DimensionType dimType = DimensionType.register(name, suffix, typeId,
                HungerGamesWorldProvider.class, false);
            registeredDimensionTypes.put(dimensionId, dimType);
            return dimType;
        } catch (IllegalArgumentException e) {
            for (DimensionType type : DimensionType.values()) {
                if (type.getId() == dimensionId) {
                    registeredDimensionTypes.put(dimensionId, type);
                    return type;
                }
            }
            return null;
        }
    }

    private void destroyHGDimension(MinecraftServer server, int dimensionId) {
        try {
            if (DimensionManager.isDimensionRegistered(dimensionId)) {
                DimensionManager.unregisterDimension(dimensionId);
            }
        } catch (Exception e) { e.printStackTrace(); }

        // Delete the dimension's save folder so stale region files cannot
        // pollute a future match that reuses the same dimension ID.
        try {
            File worldDir = server.getWorld(0).getSaveHandler().getWorldDirectory().getAbsoluteFile();
            File dimDir = new File(worldDir, "DIM" + dimensionId);
            getMapManager(server).deleteDirectory(dimDir);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private synchronized HungerGamesMapManager getMapManager(MinecraftServer server) {
        if (mapManager == null) {
            File worldDir = server.getWorld(0).getSaveHandler().getWorldDirectory().getAbsoluteFile();
            mapManager = new HungerGamesMapManager(new File(worldDir.getParentFile(), "herosmp_hg_maps"));
        }
        return mapManager;
    }

    @Nullable
    private HungerGamesMatch findMatchByPlayer(UUID playerId) {
        for (HungerGamesMatch match : activeMatches.values()) {
            if (match.getPlayerOrder().contains(playerId)) return match;
        }
        return null;
    }

    private void sendMessage(EntityPlayerMP player, String message) {
        player.sendMessage(new TextComponentString(
            TextFormatting.DARK_AQUA + "[HG] " + TextFormatting.RESET + message));
    }

    private void broadcastQueueMessage(MinecraftServer server, String message) {
        TextComponentString component = new TextComponentString(
            TextFormatting.DARK_AQUA + "[HG] " + TextFormatting.RESET + message);
        for (UUID id : queuedPlayers) {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
            if (p != null) p.sendMessage(component);
        }
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public Map<Integer, HungerGamesMatch> getActiveMatches()              { return new HashMap<>(activeMatches); }
    public int                             getQueueSize()                  { return queuedPlayers.size(); }
    public List<String>                    getAvailableMaps(MinecraftServer s) { return getMapManager(s).getPlayableMaps(); }
}
