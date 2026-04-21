package com.matoon.herosmp.hungergames;

import com.matoon.herosmp.hungergames.map.*;
import com.matoon.herosmp.hungergames.music.HungerGamesMusicManager;
import com.matoon.herosmp.hungergames.world.HungerGamesWorldProvider;
import com.matoon.herosmp.npc.pvp.FixedTeleporter;
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
        queue.addLast(id);
        queuedPlayers.add(id);
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

    public synchronized void dequeuePlayer(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        if (!queuedPlayers.remove(id)) {
            sendMessage(player, TextFormatting.YELLOW + "You are not in the queue!");
            return;
        }
        queue.remove(id);
        sendMessage(player, TextFormatting.YELLOW + "Left the Hunger Games queue.");
        if (queuedPlayers.size() < minPlayers) currentQueueCountdown = -1;
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
        while (!queue.isEmpty() && players.size() < maxPlayers) {
            UUID id = queue.removeFirst();
            queuedPlayers.remove(id);
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
            if (p != null && !p.isDead) players.add(p);
        }
        currentQueueCountdown = -1;

        if (players.size() < minPlayers) {
            for (EntityPlayerMP p : players) {
                sendMessage(p, TextFormatting.RED + "Not enough players! Returning to queue...");
                queuePlayer(p);
            }
            return;
        }
        startMatch(server, players, null);
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

        int matchId = matchIdCounter.getAndIncrement();
        HungerGamesMatch match = new HungerGamesMatch(
            matchId, dimensionId, selectedMap != null ? selectedMap : "default", players.size());

        match.setLootPool(cfg.getLootPool());
        match.setBreakableBlocks(cfg.getBreakableBlocks());
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
            BlockPos spawnPos = existing.getLobbySpawn() != null ? existing.getLobbySpawn() : new BlockPos(0, 64, 0);
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
        sendMessage(player, TextFormatting.GRAY + "Hotbar: [1] Spawns  [2] Lobby  [3] Loot  [4] Map Center  [5] Breakable Blocks");
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
                + cfg.getLootPool().size() + " loot, "
                + cfg.getBreakableBlocks().size() + " breakable blocks.");
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

            case "breakable_blocks":
                openBreakableBlocksEditor(player, session);
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
        border.setSize(config.getWorldBorderStartRange() * 2);
    }

    private void openLootPoolEditor(EntityPlayerMP player, HungerGamesConfigureMapSession session) {
        HungerGamesMapLootInventory inv = new HungerGamesMapLootInventory(session.getMapName());
        List<ItemStack> pool = session.getPendingConfig().getLootPool();
        for (int i = 0; i < inv.getSizeInventory() && i < pool.size(); i++) {
            inv.setInventorySlotContents(i, pool.get(i).copy());
        }
        player.displayGUIChest(inv);
    }

    private void openBreakableBlocksEditor(EntityPlayerMP player, HungerGamesConfigureMapSession session) {
        HungerGamesMapBreakableInventory inv = new HungerGamesMapBreakableInventory(session.getMapName());
        List<ItemStack> blocks = session.getPendingConfig().getBreakableBlocks();
        for (int i = 0; i < inv.getSizeInventory() && i < blocks.size(); i++) {
            inv.setInventorySlotContents(i, blocks.get(i).copy());
        }
        player.displayGUIChest(inv);
    }

    public synchronized void handleLootInventoryClose(EntityPlayerMP player, Container container) {
        if (!(container instanceof ContainerChest)) return;
        ContainerChest chest = (ContainerChest) container;
        HungerGamesConfigureMapSession session = configureSessions.get(player.getUniqueID());
        if (session == null) return;

        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < chest.getLowerChestInventory().getSizeInventory(); i++) {
            ItemStack stack = chest.getLowerChestInventory().getStackInSlot(i);
            if (!stack.isEmpty()) items.add(stack.copy());
        }

        if (chest.getLowerChestInventory() instanceof HungerGamesMapLootInventory) {
            session.getPendingConfig().setLootPool(items);
            sendMessage(player, TextFormatting.GREEN + "Loot pool updated: " + items.size() + " item types.");
        } else if (chest.getLowerChestInventory() instanceof HungerGamesMapBreakableInventory) {
            session.getPendingConfig().setBreakableBlocks(items);
            sendMessage(player, TextFormatting.GREEN + "Breakable blocks updated: " + items.size() + " block types.");
        }
    }

    public boolean isInConfigureMode(UUID playerId) {
        return configureSessions.containsKey(playerId);
    }

    private void giveConfigureTools(EntityPlayerMP player) {
        player.inventory.setInventorySlotContents(0,
            createConfigTool(new ItemStack(Items.ENDER_PEARL),  TextFormatting.GREEN  + "Set Round Spawn", "round_spawn"));
        player.inventory.setInventorySlotContents(1,
            createConfigTool(new ItemStack(Items.COMPASS),      TextFormatting.AQUA   + "Set Lobby Spawn", "lobby_spawn"));
        player.inventory.setInventorySlotContents(2,
            createConfigTool(new ItemStack(Items.EMERALD),      TextFormatting.YELLOW + "Loot Pool",        "loot_pool"));
        player.inventory.setInventorySlotContents(3,
            createConfigTool(new ItemStack(Items.WOODEN_AXE),  TextFormatting.GOLD   + "Set Map Center / Border Range", "map_center"));
        player.inventory.setInventorySlotContents(4,
            createConfigTool(new ItemStack(Items.IRON_PICKAXE), TextFormatting.AQUA  + "Breakable Blocks", "breakable_blocks"));
    }

    private static ItemStack createConfigTool(ItemStack base, String displayName, String toolId) {
        base.setStackDisplayName(displayName);
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
        PlayerDataIsolationManager.restorePlayerState(player);
        sendMessage(player, TextFormatting.GREEN + "You have been returned from Hunger Games.");
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
            match.removePlayer(id);
            returnPlayerFromHG(player);
            return true;
        }

        if (PlayerDataIsolationManager.hasStoredState(id)) {
            returnPlayerFromHG(player);
            return true;
        }

        return false;
    }

    public boolean isPlayerInMatch(UUID playerId) {
        return findMatchByPlayer(playerId) != null;
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
    // Teleportation
    // -------------------------------------------------------------------------

    private void teleportToHGWorld(EntityPlayerMP player, WorldServer hgWorld, BlockPos spawn) {
        player.changeDimension(hgWorld.provider.getDimension(),
            new FixedTeleporter(hgWorld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0, 0));
        player.setHealth(20.0F);
        player.getFoodStats().setFoodLevel(20);
    }

    private void returnPlayerFromHG(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        int       returnDim = PlayerDataIsolationManager.getReturnDimension(id);
        double[]  returnPos = PlayerDataIsolationManager.getReturnPosition(id);
        float[]   returnRot = PlayerDataIsolationManager.getReturnRotation(id);

        WorldServer returnWorld = player.getServer().getWorld(returnDim);
        if (returnWorld == null) {
            returnWorld = player.getServer().getWorld(0);
            returnPos   = new double[]{0, 64, 0};
        }
        player.changeDimension(returnDim,
            new FixedTeleporter(returnWorld, returnPos[0], returnPos[1], returnPos[2],
                returnRot[0], returnRot[1]));
        PlayerDataIsolationManager.restorePlayerState(player);
        sendMessage(player, TextFormatting.GREEN + "Returned from Hunger Games.");
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
    public List<String>                    getAvailableMaps(MinecraftServer s) { return getMapManager(s).getAvailableMaps(); }
}
