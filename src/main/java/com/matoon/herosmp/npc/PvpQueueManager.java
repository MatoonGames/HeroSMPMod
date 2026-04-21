package com.matoon.herosmp.npc;

import com.matoon.herosmp.HeroSMP;
import com.matoon.herosmp.network.ModNetwork;
import com.matoon.herosmp.network.PacketOpenKitSelection;
import com.matoon.herosmp.network.PacketOpenPvpMenu;
import com.matoon.herosmp.npc.kit.KitDefinition;
import com.matoon.herosmp.npc.pvp.ArenaWorldProvider;
import com.matoon.herosmp.npc.pvp.FixedTeleporter;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityShulker;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.item.EntityFireworkRocket;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.server.SPacketDestroyEntities;
import net.minecraft.network.play.server.SPacketTitle;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameType;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.BossInfo;
import net.minecraft.world.BossInfoServer;
import net.minecraft.world.DimensionType;
import net.minecraft.world.ILockableContainer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.common.DimensionManager;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

public class PvpQueueManager {

    private static final int ARENA_CHUNKS_ACROSS = ArenaWorldProvider.ARENA_CHUNKS_ACROSS;
    private static final int ARENA_MIN_CHUNK = ArenaWorldProvider.ARENA_MIN_LOCAL_CHUNK;
    private static final int ARENA_MAX_CHUNK = ArenaWorldProvider.ARENA_MAX_LOCAL_CHUNK;
    private static final int ARENA_MIN_BLOCK = ArenaWorldProvider.ARENA_MIN_LOCAL_BLOCK;
    private static final int ARENA_MAX_BLOCK = ArenaWorldProvider.ARENA_MAX_LOCAL_BLOCK;
    private static final int ARENA_SPAWN_EDGE_PADDING = 20;
    private static final int ARENA_FIRST_SPAWN_X = ARENA_MIN_BLOCK + ARENA_SPAWN_EDGE_PADDING;
    private static final int ARENA_SECOND_SPAWN_X = ARENA_MAX_BLOCK - ARENA_SPAWN_EDGE_PADDING;
    private static final int ARENA_SPAWN_Z = 0;
    private static final int ARENA_DIMENSION_ID = -7000;
    private static final int ARENA_DIMENSION_TYPE_ID = 17770;
    private static final String ARENA_DIMENSION_TYPE_NAME = "herosmp_pvp";
    private static final String ARENA_DIMENSION_TYPE_SUFFIX = "_herosmp_pvp";
    private static final int ARENA_GENERATION_ATTEMPTS = 8;
    private static final int CLEANUP_UNLOAD_RETRY_TICKS = 20;
    private static final int CLEANUP_DELETE_DELAY_TICKS = 60;
    private static final int ARENA_SLOT_CHUNKS = ArenaWorldProvider.SLOT_REGION_CHUNKS;
    private static final int ARENA_SLOT_BLOCKS = ArenaWorldProvider.SLOT_REGION_BLOCKS;
    private static final int MAX_MATCH_CHESTS = 7;
    private static final int MIN_MATCH_CHESTS = 4;
    private static final int CHEST_PLACEMENT_ATTEMPTS = 180;
    private static final int CHEST_EDGE_PADDING = 6;
    private static final int PREP_FREEZE_SECONDS = 20;
    private static final int PREP_COUNTDOWN_SECONDS = 3;
    private static final int PLAYER_REVEAL_AT_SECONDS = 10;
    private static final int PREP_FREEZE_TICKS = PREP_FREEZE_SECONDS * 20;
    private static final int PREP_COUNTDOWN_TICKS = PREP_COUNTDOWN_SECONDS * 20;
    private static final int VICTORY_SEQUENCE_TICKS = 60;
    private static final int FFA_MAX_PLAYERS = 4;
    private static final int FFA_MIN_PLAYERS = 2;
    private static final int FFA_QUEUE_WAIT_TICKS = 20 * 20;
    private static final Biome[] ARENA_BIOME_POOL = new Biome[]{
            Biomes.FOREST,
            Biomes.BIRCH_FOREST,
            Biomes.ROOFED_FOREST,
            Biomes.PLAINS,
            Biomes.TAIGA,
            Biomes.REDWOOD_TAIGA,
            Biomes.EXTREME_HILLS,
            Biomes.SAVANNA,
            Biomes.MESA,
            Biomes.DESERT,
            Biomes.JUNGLE,
            Biomes.SWAMPLAND,
            Biomes.ICE_PLAINS
    };

    private static final String CHAT_PREFIX = TextFormatting.DARK_AQUA + "[HeroPvP] " + TextFormatting.GRAY;

    private final Deque<UUID> queue = new ArrayDeque<UUID>();
    private final Deque<UUID> ffaQueue = new ArrayDeque<UUID>();
    private final Set<UUID> queuedPlayers = new HashSet<UUID>();
    private final Set<UUID> ffaQueuedPlayers = new HashSet<UUID>();
    private final Map<UUID, ActiveMatch> playerToMatch = new HashMap<UUID, ActiveMatch>();
    private final Map<UUID, FfaMatch> playerToFfaMatch = new HashMap<UUID, FfaMatch>();
    private final Map<UUID, SoloMatch> soloMatches = new HashMap<UUID, SoloMatch>();
    private final Map<UUID, ReturnState> pendingReturns = new HashMap<UUID, ReturnState>();
    private final Map<UUID, PlayerInventorySnapshot> pendingInventoryReturns = new HashMap<UUID, PlayerInventorySnapshot>();
    private final Map<Long, ArenaCleanupState> pendingArenaCleanup = new HashMap<Long, ArenaCleanupState>();
    private final Set<UUID> awaitingKitSelection = new HashSet<UUID>();
    private final Map<UUID, BlockPos> chestHighlightTargets = new HashMap<UUID, BlockPos>();
    private final Map<UUID, SpectatorSession> spectators = new HashMap<UUID, SpectatorSession>();
    private final Map<UUID, VictorySequence> pendingVictorySequences = new HashMap<UUID, VictorySequence>();
    /** Players who have been assigned a match but have not yet arrived in the arena dimension.
     *  Dimension-escape checks are suppressed for these players. */
    private final Set<UUID> pendingArenaArrivals = new HashSet<UUID>();
    private final AtomicInteger matchCounter = new AtomicInteger(0);
    private final AtomicLong arenaSeedSequence = new AtomicLong(System.nanoTime());
    private final Set<Long> allocatedArenaSlots = new HashSet<Long>();
    private DimensionType arenaDimensionType;
    private int ffaQueueCountdownTicks = -1;

    public synchronized void joinQueue(EntityPlayerMP player, EntityStaticNpc npc) {
        queueQuick(player);
    }

    public synchronized void queueQuick(EntityPlayerMP player) {
        UUID playerId = player.getUniqueID();
        if (playerToMatch.containsKey(playerId) || playerToFfaMatch.containsKey(playerId) || soloMatches.containsKey(playerId) || spectators.containsKey(playerId)) {
            send(player, TextFormatting.RED + "You are already in an active PvP session.");
            return;
        }

        if (queuedPlayers.contains(playerId) || ffaQueuedPlayers.contains(playerId)) {
            send(player, TextFormatting.YELLOW + "You are already queued. " + TextFormatting.GRAY + "Position: " + getQueuePosition(playerId));
            return;
        }

        if (HeroSMP.HUNGER_GAMES_MANAGER.isPlayerInMatchOrQueue(playerId)) {
            send(player, TextFormatting.RED + "You cannot queue for PvP while in a Hunger Games match!");
            return;
        }

        EntityPlayerMP opponent = pollNextAvailableOpponent(player);
        if (opponent == null) {
            queue.addLast(playerId);
            queuedPlayers.add(playerId);
            send(player, TextFormatting.GREEN + "Queued for PvP" + TextFormatting.GRAY + " (position " + getQueuePosition(playerId) + ")" + TextFormatting.DARK_GRAY + " | Waiting for opponent...");
            return;
        }

        queuedPlayers.remove(opponent.getUniqueID());
        startMatch(player, opponent);
    }

    public synchronized void queueMode(EntityPlayerMP player, String modeKey) {
        if ("ffa".equalsIgnoreCase(modeKey) || "freeforall".equalsIgnoreCase(modeKey)) {
            queueFfa(player);
            return;
        }
        queueQuick(player);
    }

    private void queueFfa(EntityPlayerMP player) {
        UUID playerId = player.getUniqueID();
        if (playerToMatch.containsKey(playerId) || playerToFfaMatch.containsKey(playerId) || soloMatches.containsKey(playerId) || spectators.containsKey(playerId)) {
            send(player, TextFormatting.RED + "You are already in an active PvP session.");
            return;
        }
        if (queuedPlayers.contains(playerId) || ffaQueuedPlayers.contains(playerId)) {
            send(player, TextFormatting.YELLOW + "You are already queued.");
            return;
        }
        if (HeroSMP.HUNGER_GAMES_MANAGER.isPlayerInMatchOrQueue(playerId)) {
            send(player, TextFormatting.RED + "You cannot queue for PvP while in a Hunger Games match!");
            return;
        }

        ffaQueue.addLast(playerId);
        ffaQueuedPlayers.add(playerId);
        send(player, TextFormatting.GREEN + "Queued for Free For All" + TextFormatting.GRAY + " (" + ffaQueuedPlayers.size() + "/" + FFA_MAX_PLAYERS + ")");

        if (ffaQueuedPlayers.size() >= FFA_MAX_PLAYERS) {
            startFfaFromQueue(player.getServer(), true);
            return;
        }

        if (ffaQueuedPlayers.size() >= FFA_MIN_PLAYERS && ffaQueueCountdownTicks < 0) {
            ffaQueueCountdownTicks = FFA_QUEUE_WAIT_TICKS;
            sendQueuedFfaPlayers(player.getServer(), TextFormatting.AQUA + "FFA starting in 20s. Waiting for more players...");
        }
    }

    public synchronized void cancelQueue(EntityPlayerMP player) {
        UUID playerId = player.getUniqueID();
        boolean removed = false;
        if (queuedPlayers.remove(playerId)) {
            queue.remove(playerId);
            removed = true;
        }
        if (ffaQueuedPlayers.remove(playerId)) {
            ffaQueue.remove(playerId);
            removed = true;
        }
        if (!removed) {
            send(player, TextFormatting.YELLOW + "You are not currently queued.");
            return;
        }
        send(player, TextFormatting.YELLOW + "You left the PvP queue.");
    }

    public synchronized void openPvpMenu(EntityPlayerMP player) {
        boolean queued = queuedPlayers.contains(player.getUniqueID()) || ffaQueuedPlayers.contains(player.getUniqueID());
        ModNetwork.CHANNEL.sendTo(new PacketOpenPvpMenu(buildMenuSnapshot(player.getServer()), queued), player);
    }

    public synchronized void trySpectateMatch(EntityPlayerMP spectator, int matchId) {
        UUID spectatorId = spectator.getUniqueID();
        if (spectators.containsKey(spectatorId) || playerToMatch.containsKey(spectatorId) || playerToFfaMatch.containsKey(spectatorId) || soloMatches.containsKey(spectatorId) || queuedPlayers.contains(spectatorId) || ffaQueuedPlayers.contains(spectatorId)) {
            send(spectator, TextFormatting.RED + "You cannot spectate while queued or in a PvP session.");
            return;
        }

        ActiveMatch match = findMatchById(matchId);
        FfaMatch ffa = match == null ? findFfaMatchById(matchId) : null;
        SoloMatch solo = match == null && ffa == null ? findSoloMatchById(matchId) : null;
        if (match == null && solo == null && ffa == null) {
            send(spectator, TextFormatting.RED + "That match is no longer active.");
            return;
        }

        int dimensionId = match != null ? match.dimensionId : (ffa != null ? ffa.dimensionId : solo.dimensionId);
        WorldServer world = spectator.getServer().getWorld(dimensionId);
        if (world == null) {
            send(spectator, TextFormatting.RED + "Arena world unavailable.");
            return;
        }

        ReturnState returnState = ReturnState.capture(spectator);
        double sx;
        double sy;
        double sz;
        if (match != null) {
            spectators.put(spectatorId, new SpectatorSession(returnState, SpectateTarget.ACTIVE_MATCH, match.matchId));
            match.spectators.add(spectatorId);
            sx = (match.firstSpawn.getX() + match.secondSpawn.getX()) / 2.0D + 0.5D;
            sy = Math.max(match.firstSpawn.getY(), match.secondSpawn.getY()) + 18.0D;
            sz = (match.firstSpawn.getZ() + match.secondSpawn.getZ()) / 2.0D + 0.5D;
            send(spectator, TextFormatting.AQUA + "Now spectating match #" + match.matchId + TextFormatting.GRAY + ". Use /return to leave.");
        } else if (ffa != null) {
            spectators.put(spectatorId, new SpectatorSession(returnState, SpectateTarget.FFA_MATCH, ffa.matchId));
            ffa.spectators.add(spectatorId);
            sx = ffa.slot.centerX + 0.5D;
            sy = 90.0D;
            sz = ffa.slot.centerZ + 0.5D;
            send(spectator, TextFormatting.AQUA + "Now spectating FFA #" + ffa.matchId + TextFormatting.GRAY + ". Use /return to leave.");
        } else {
            int soloId = solo.matchId;
            spectators.put(spectatorId, new SpectatorSession(returnState, SpectateTarget.DEBUG_SOLO, soloId));
            solo.spectators.add(spectatorId);
            sx = solo.spawn.getX() + 0.5D;
            sy = solo.spawn.getY() + 18.0D;
            sz = solo.spawn.getZ() + 0.5D;
            send(spectator, TextFormatting.AQUA + "Now spectating debug solo #" + soloId + TextFormatting.GRAY + ". Use /return to leave.");
        }

        moveToDimension(spectator, world, sx, sy, sz, spectator.rotationYaw, spectator.rotationPitch);
        spectator.setGameType(GameType.SPECTATOR);
        spectator.setSpectatingEntity(spectator);
        spectator.sendPlayerAbilities();
        SpectatorSession session = spectators.get(spectatorId);
        if (session != null) {
            spawnSpectatorBat(world, spectator, session);
        }
        if (match != null && match.bossBar != null) {
            match.bossBar.addPlayer(spectator);
        } else if (ffa != null && ffa.bossBar != null) {
            ffa.bossBar.addPlayer(spectator);
        } else if (solo != null && solo.bossBar != null) {
            solo.bossBar.addPlayer(spectator);
        }
    }

    public synchronized void returnFromSpectate(EntityPlayerMP player) {
        SpectatorSession session = spectators.remove(player.getUniqueID());
        if (session == null) {
            send(player, TextFormatting.YELLOW + "You are not spectating a match.");
            return;
        }

        removeSpectatorBat(player.getServer(), session);

        if (session.target == SpectateTarget.ACTIVE_MATCH) {
            ActiveMatch match = findMatchById(session.targetId);
            if (match != null) {
                match.spectators.remove(player.getUniqueID());
                if (match.bossBar != null) {
                    match.bossBar.removePlayer(player);
                }
            }
        } else if (session.target == SpectateTarget.FFA_MATCH) {
            FfaMatch ffa = findFfaMatchById(session.targetId);
            if (ffa != null) {
                ffa.spectators.remove(player.getUniqueID());
                if (ffa.bossBar != null) {
                    ffa.bossBar.removePlayer(player);
                }
            }
        } else {
            SoloMatch solo = findSoloMatchById(session.targetId);
            if (solo != null) {
                solo.spectators.remove(player.getUniqueID());
                if (solo.bossBar != null) {
                    solo.bossBar.removePlayer(player);
                }
            }
        }

        restorePlayer(player, session.returnState);
        send(player, TextFormatting.GREEN + "Returned from spectating.");
    }

    public synchronized void handleReturnCommand(EntityPlayerMP player) {
        if (spectators.containsKey(player.getUniqueID())) {
            returnFromSpectate(player);
            return;
        }

        SoloMatch solo = soloMatches.get(player.getUniqueID());
        if (solo != null) {
            exitDebugSoloMatch(player);
            return;
        }

        ActiveMatch match = playerToMatch.get(player.getUniqueID());
        if (match != null) {
            forfeitMatch(player.getServer(), player, match);
            return;
        }
        FfaMatch ffa = playerToFfaMatch.get(player.getUniqueID());
        if (ffa != null) {
            forfeitFfaMatch(player.getServer(), player, ffa);
            return;
        }

        send(player, TextFormatting.YELLOW + "You are not in PvP or spectating.");
    }

    private List<PacketOpenPvpMenu.ActiveMatchEntry> buildMenuSnapshot(MinecraftServer server) {
        List<PacketOpenPvpMenu.ActiveMatchEntry> entries = new ArrayList<PacketOpenPvpMenu.ActiveMatchEntry>();
        Set<Integer> seen = new HashSet<Integer>();
        for (ActiveMatch match : playerToMatch.values()) {
            if (!seen.add(match.matchId)) {
                continue;
            }

            EntityPlayerMP first = server.getPlayerList().getPlayerByUUID(match.firstPlayer);
            EntityPlayerMP second = server.getPlayerList().getPlayerByUUID(match.secondPlayer);
            String firstName = first != null ? first.getName() : "Offline";
            String secondName = second != null ? second.getName() : "Offline";
            String status = match.phase == MatchPhase.ACTIVE ? "In Progress" : "Preparing";
            entries.add(new PacketOpenPvpMenu.ActiveMatchEntry(match.matchId, firstName, secondName, status));
        }
        for (FfaMatch ffa : playerToFfaMatch.values()) {
            if (!seen.add(ffa.matchId)) {
                continue;
            }
            List<String> names = new ArrayList<String>();
            for (UUID playerId : ffa.playerOrder) {
                EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(playerId);
                if (p != null) {
                    names.add(p.getName());
                }
            }
            String first = names.isEmpty() ? "FFA" : names.get(0);
            String second = names.size() > 1 ? names.get(1) + (names.size() > 2 ? " +" + (names.size() - 2) : "") : "Free For All";
            String status = ffa.phase == MatchPhase.ACTIVE ? "FFA - In Progress" : "FFA - Preparing";
            entries.add(new PacketOpenPvpMenu.ActiveMatchEntry(ffa.matchId, first, second, status));
        }

        for (SoloMatch solo : soloMatches.values()) {
            EntityPlayerMP soloPlayer = server.getPlayerList().getPlayerByUUID(solo.playerId);
            String soloName = soloPlayer != null ? soloPlayer.getName() : "Offline";
            String status = solo.phase == MatchPhase.ACTIVE ? "Debug Solo - In Progress" : "Debug Solo - Preparing";
            entries.add(new PacketOpenPvpMenu.ActiveMatchEntry(solo.matchId, soloName, "Debug Solo", status));
        }
        return entries;
    }

    private ActiveMatch findMatchById(int matchId) {
        for (ActiveMatch match : playerToMatch.values()) {
            if (match.matchId == matchId) {
                return match;
            }
        }
        return null;
    }

    private SoloMatch findSoloMatchById(int matchId) {
        for (SoloMatch solo : soloMatches.values()) {
            if (solo.matchId == matchId) {
                return solo;
            }
        }
        return null;
    }

    private FfaMatch findFfaMatchById(int matchId) {
        for (FfaMatch ffa : playerToFfaMatch.values()) {
            if (ffa.matchId == matchId) {
                return ffa;
            }
        }
        return null;
    }

    public synchronized void startDebugSoloMatch(EntityPlayerMP player) {
        UUID playerId = player.getUniqueID();
        if (playerToMatch.containsKey(playerId) || soloMatches.containsKey(playerId)) {
            send(player, TextFormatting.RED + "You are already in an active PvP session.");
            return;
        }
        if (queuedPlayers.remove(playerId)) {
            queue.remove(playerId);
        }
        if (ffaQueuedPlayers.remove(playerId)) {
            ffaQueue.remove(playerId);
        }
        if (ffaQueuedPlayers.remove(playerId)) {
            ffaQueue.remove(playerId);
        }

        ArenaPreparedArena preparedArena = prepareArena(player.getServer(), "debugsolo", false);
        if (preparedArena == null) {
            send(player, TextFormatting.RED + "Failed to create debug arena.");
            return;
        }

        ReturnState returnState = ReturnState.capture(player);
        PlayerInventorySnapshot inventorySnapshot = PlayerInventorySnapshot.capture(player);
        PvpPlayerStateSavedData.get(player.getServer()).saveState(player);
        HeroSMP.KIT_MANAGER.clearPlayerInventory(player);
        player.setGameType(GameType.SURVIVAL);
        List<BlockPos> chestPositions = spawnMatchLootChests(preparedArena.world, preparedArena.slot);
        soloMatches.put(playerId, new SoloMatch(nextMatchId(), playerId, returnState, inventorySnapshot, preparedArena.dimensionId, preparedArena.slot, preparedArena.firstSpawn, chestPositions));

        pendingArenaArrivals.add(playerId);
        preGenerateSpawnArea(preparedArena.world, preparedArena.firstSpawn.getX(), preparedArena.firstSpawn.getZ(), 4);
        teleportToArena(player, preparedArena.world, preparedArena.firstSpawn.getX() + 0.5D, preparedArena.firstSpawn.getY(), preparedArena.firstSpawn.getZ() + 0.5D, 90.0F);
        openKitSelection(player);
        send(player, TextFormatting.LIGHT_PURPLE + "Debug solo arena ready." + TextFormatting.GRAY + " Use /heropvp debugexit to leave.");
    }

    public synchronized void exitDebugSoloMatch(EntityPlayerMP player) {
        SoloMatch soloMatch = soloMatches.remove(player.getUniqueID());
        awaitingKitSelection.remove(player.getUniqueID());
        if (soloMatch == null) {
            send(player, TextFormatting.YELLOW + "You are not in a debug solo match.");
            return;
        }

        WorldServer world = player.getServer().getWorld(soloMatch.dimensionId);
        if (world != null) {
            removeChestHighlights(world, soloMatch.chestHighlightIds);
        }
        soloMatch.clearBossBar(player.getServer());
        returnSpectatorsForSolo(player.getServer(), soloMatch);
        player.removePotionEffect(MobEffects.SLOWNESS);
        player.removePotionEffect(MobEffects.JUMP_BOOST);
        player.removePotionEffect(MobEffects.GLOWING);
        restorePlayer(player, soloMatch.returnState);
        soloMatch.inventory.restore(player);
        clearPersistedState(player);
        teardownArenaSlot(player.getServer(), soloMatch.slot);
        send(player, TextFormatting.GREEN + "Exited debug solo arena.");
    }

    public synchronized void selectKit(EntityPlayerMP player, String kitKey) {
        if (!awaitingKitSelection.contains(player.getUniqueID())) {
            return;
        }

        KitDefinition kit = HeroSMP.KIT_MANAGER.getKit(player.getServer(), kitKey);
        if (kit == null) {
            send(player, TextFormatting.RED + "Kit not found: " + kitKey);
            openKitSelection(player);
            return;
        }

        HeroSMP.KIT_MANAGER.applyKitToPlayer(player, kit);
        awaitingKitSelection.remove(player.getUniqueID());
        send(player, TextFormatting.GREEN + "Selected kit " + TextFormatting.WHITE + kit.getDisplayName());
    }

    private void openKitSelection(EntityPlayerMP player) {
        List<KitDefinition> kits = HeroSMP.KIT_MANAGER.getKits(player.getServer());
        if (kits.isEmpty()) {
            awaitingKitSelection.remove(player.getUniqueID());
            send(player, TextFormatting.RED + "No kits configured. Ask staff to create kits with /heropvp kit create.");
            return;
        }

        List<PacketOpenKitSelection.KitEntry> entries = new ArrayList<PacketOpenKitSelection.KitEntry>(kits.size());
        for (KitDefinition kit : kits) {
            entries.add(new PacketOpenKitSelection.KitEntry(kit.getKey(), kit.getDisplayName(), kit.getIcon()));
        }

        awaitingKitSelection.add(player.getUniqueID());
        ModNetwork.CHANNEL.sendTo(new PacketOpenKitSelection(entries), player);
    }

    private EntityPlayerMP pollNextAvailableOpponent(EntityPlayerMP requester) {
        while (!queue.isEmpty()) {
            UUID queued = queue.removeFirst();
            if (queued.equals(requester.getUniqueID())) {
                continue;
            }

            EntityPlayerMP possible = requester.getServer().getPlayerList().getPlayerByUUID(queued);
            if (possible != null && !playerToMatch.containsKey(queued) && !soloMatches.containsKey(queued) && !possible.isDead) {
                queuedPlayers.remove(queued);
                return possible;
            }

            queuedPlayers.remove(queued);
        }
        return null;
    }

    private void startMatch(EntityPlayerMP first, EntityPlayerMP second) {
        ArenaPreparedArena preparedArena = prepareArena(first.getServer(), "match", true);
        if (preparedArena == null) {
            send(first, TextFormatting.RED + "Failed to create PvP arena.");
            send(second, TextFormatting.RED + "Failed to create PvP arena.");
            return;
        }

        ReturnState firstReturn = ReturnState.capture(first);
        ReturnState secondReturn = ReturnState.capture(second);
        PlayerInventorySnapshot firstInventory = PlayerInventorySnapshot.capture(first);
        PlayerInventorySnapshot secondInventory = PlayerInventorySnapshot.capture(second);
        // Persist state to disk so it survives a server crash
        PvpPlayerStateSavedData stateData = PvpPlayerStateSavedData.get(first.getServer());
        stateData.saveState(first);
        stateData.saveState(second);
        HeroSMP.KIT_MANAGER.clearPlayerInventory(first);
        HeroSMP.KIT_MANAGER.clearPlayerInventory(second);
        first.setGameType(GameType.SURVIVAL);
        second.setGameType(GameType.SURVIVAL);

        List<BlockPos> chestPositions = spawnMatchLootChests(preparedArena.world, preparedArena.slot);
        ActiveMatch match = new ActiveMatch(
                nextMatchId(),
                first.getUniqueID(),
                second.getUniqueID(),
                firstReturn,
                secondReturn,
                firstInventory,
                secondInventory,
                preparedArena.dimensionId,
                preparedArena.slot,
                preparedArena.firstSpawn,
                preparedArena.secondSpawn,
                chestPositions
        );
        playerToMatch.put(first.getUniqueID(), match);
        playerToMatch.put(second.getUniqueID(), match);

        preGenerateSpawnArea(preparedArena.world, preparedArena.firstSpawn.getX(), preparedArena.firstSpawn.getZ(), 4);
        preGenerateSpawnArea(preparedArena.world, preparedArena.secondSpawn.getX(), preparedArena.secondSpawn.getZ(), 4);
        pendingArenaArrivals.add(first.getUniqueID());
        pendingArenaArrivals.add(second.getUniqueID());
        teleportToArena(first, preparedArena.world, preparedArena.firstSpawn.getX() + 0.5D, preparedArena.firstSpawn.getY(), preparedArena.firstSpawn.getZ() + 0.5D, 90.0F);
        teleportToArena(second, preparedArena.world, preparedArena.secondSpawn.getX() + 0.5D, preparedArena.secondSpawn.getY(), preparedArena.secondSpawn.getZ() + 0.5D, -90.0F);

        openKitSelection(first);
        openKitSelection(second);

        send(first, TextFormatting.GOLD + "Match found! " + TextFormatting.AQUA + "Opponent: " + TextFormatting.WHITE + second.getName());
        send(second, TextFormatting.GOLD + "Match found! " + TextFormatting.AQUA + "Opponent: " + TextFormatting.WHITE + first.getName());
    }

    private void startFfaFromQueue(MinecraftServer server, boolean immediate) {
        List<EntityPlayerMP> players = new ArrayList<EntityPlayerMP>();
        while (!ffaQueue.isEmpty() && players.size() < FFA_MAX_PLAYERS) {
            UUID nextId = ffaQueue.removeFirst();
            ffaQueuedPlayers.remove(nextId);
            EntityPlayerMP next = server.getPlayerList().getPlayerByUUID(nextId);
            if (next == null || next.isDead) {
                continue;
            }
            if (playerToMatch.containsKey(nextId) || playerToFfaMatch.containsKey(nextId) || soloMatches.containsKey(nextId) || spectators.containsKey(nextId)) {
                continue;
            }
            players.add(next);
        }

        if (players.size() < FFA_MIN_PLAYERS) {
            ffaQueueCountdownTicks = -1;
            for (EntityPlayerMP player : players) {
                ffaQueue.addLast(player.getUniqueID());
                ffaQueuedPlayers.add(player.getUniqueID());
            }
            return;
        }
        ffaQueueCountdownTicks = -1;
        startFfaMatch(players, immediate);
    }

    private void startFfaMatch(List<EntityPlayerMP> players, boolean immediate) {
        if (players.size() < FFA_MIN_PLAYERS) {
            return;
        }

        ArenaPreparedArena preparedArena = prepareArena(players.get(0).getServer(), "ffa", false);
        if (preparedArena == null) {
            for (EntityPlayerMP player : players) {
                send(player, TextFormatting.RED + "Failed to create FFA arena.");
            }
            return;
        }

        List<BlockPos> spawnPoints = createFfaSpawnPoints(preparedArena.world, preparedArena.slot, players.size());
        if (spawnPoints.size() < players.size()) {
            teardownArenaSlot(players.get(0).getServer(), preparedArena.slot);
            for (EntityPlayerMP player : players) {
                send(player, TextFormatting.RED + "Failed to find enough FFA spawn points.");
            }
            return;
        }

        List<BlockPos> chestPositions = spawnMatchLootChests(preparedArena.world, preparedArena.slot);
        FfaMatch match = new FfaMatch(nextMatchId(), preparedArena.dimensionId, preparedArena.slot, chestPositions);
        PvpPlayerStateSavedData ffaStateData = PvpPlayerStateSavedData.get(players.get(0).getServer());
        for (int i = 0; i < players.size(); i++) {
            EntityPlayerMP player = players.get(i);
            UUID playerId = player.getUniqueID();
            BlockPos spawn = spawnPoints.get(i);
            match.playerOrder.add(playerId);
            match.returnStates.put(playerId, ReturnState.capture(player));
            match.inventorySnapshots.put(playerId, PlayerInventorySnapshot.capture(player));
            match.spawns.put(playerId, new BlockPos(spawn));
            match.alivePlayers.add(playerId);
            playerToFfaMatch.put(playerId, match);
            ffaStateData.saveState(player);
            HeroSMP.KIT_MANAGER.clearPlayerInventory(player);
            player.setGameType(GameType.SURVIVAL);
        }

        for (UUID playerId : match.playerOrder) {
            EntityPlayerMP player = players.get(0).getServer().getPlayerList().getPlayerByUUID(playerId);
            if (player == null) {
                continue;
            }
            pendingArenaArrivals.add(playerId);
            BlockPos spawn = match.spawns.get(playerId);
            preGenerateSpawnArea(preparedArena.world, spawn.getX(), spawn.getZ(), 4);
            float yaw = (float) (Math.atan2(0.5D - (spawn.getZ() + 0.5D), 0.5D - (spawn.getX() + 0.5D)) * 180.0D / Math.PI) - 90.0F;
            teleportToArena(player, preparedArena.world, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, yaw);
            openKitSelection(player);
            send(player, TextFormatting.GOLD + "FFA found! " + TextFormatting.GRAY + players.size() + " players joined.");
        }
        if (immediate) {
            sendFfaChat(players.get(0).getServer(), match, TextFormatting.AQUA + "Lobby filled. Starting immediately.");
            match.phaseTicksRemaining = 1;
        }
    }

    private List<BlockPos> createFfaSpawnPoints(WorldServer world, ArenaSlot slot, int playerCount) {
        List<BlockPos> points = new ArrayList<BlockPos>();
        int minX = slot.minBlockX + ARENA_SPAWN_EDGE_PADDING;
        int maxX = slot.maxBlockX - ARENA_SPAWN_EDGE_PADDING;
        int minZ = slot.minBlockZ + ARENA_SPAWN_EDGE_PADDING;
        int maxZ = slot.maxBlockZ - ARENA_SPAWN_EDGE_PADDING;
        int[][] corners = new int[][]{
                {minX, minZ},
                {maxX, minZ},
                {maxX, maxZ},
                {minX, maxZ}
        };
        for (int i = 0; i < corners.length && points.size() < playerCount; i++) {
            BlockPos spawn = findNaturalSpawn(world, slot, corners[i][0], corners[i][1]);
            if (spawn != null) {
                points.add(spawn);
            }
        }
        return points;
    }

    public synchronized void handlePlayerDeath(EntityPlayerMP deadPlayer) {
        UUID playerId = deadPlayer.getUniqueID();

        SoloMatch solo = soloMatches.get(playerId);
        if (solo != null) {
            send(deadPlayer, TextFormatting.RED + "You died in the debug solo arena. Returning after respawn...");
            WorldServer world = deadPlayer.getServer().getWorld(solo.dimensionId);
            if (world != null) {
                removeChestHighlights(world, solo.chestHighlightIds);
            }
            solo.clearBossBar(deadPlayer.getServer());
            returnSpectatorsForSolo(deadPlayer.getServer(), solo);
            pendingReturns.put(playerId, solo.returnState);
            pendingInventoryReturns.put(playerId, solo.inventory);
            teardownArenaSlot(deadPlayer.getServer(), solo.slot);
            awaitingKitSelection.remove(playerId);
            soloMatches.remove(playerId);
            return;
        }

        ActiveMatch match = playerToMatch.get(playerId);
        if (match != null) {
            UUID winnerId = match.getOpponent(playerId);
            EntityPlayerMP winner = deadPlayer.getServer().getPlayerList().getPlayerByUUID(winnerId);

            if (winner != null) {
                send(winner, TextFormatting.GREEN + "Victory! " + TextFormatting.GRAY + "You won the duel.");
            }
            send(deadPlayer, TextFormatting.RED + "Defeat. " + TextFormatting.GRAY + "Returning after respawn...");

            endMatch(deadPlayer.getServer(), match, playerId);
            return;
        }

        FfaMatch ffa = playerToFfaMatch.get(playerId);
        if (ffa != null) {
            eliminateFfaPlayer(deadPlayer.getServer(), ffa, playerId, true, TextFormatting.RED + deadPlayer.getName() + " was eliminated.");
        }
    }

    public synchronized void handlePlayerLogout(EntityPlayerMP player) {
        UUID playerId = player.getUniqueID();
        awaitingKitSelection.remove(playerId);

        SpectatorSession spectatorSession = spectators.remove(playerId);
        if (spectatorSession != null) {
            removeSpectatorBat(player.getServer(), spectatorSession);
            if (spectatorSession.target == SpectateTarget.ACTIVE_MATCH) {
                ActiveMatch spectated = findMatchById(spectatorSession.targetId);
                if (spectated != null) {
                    spectated.spectators.remove(playerId);
                }
            } else if (spectatorSession.target == SpectateTarget.FFA_MATCH) {
                FfaMatch spectatedFfa = findFfaMatchById(spectatorSession.targetId);
                if (spectatedFfa != null) {
                    spectatedFfa.spectators.remove(playerId);
                }
            } else {
                SoloMatch spectatedSolo = findSoloMatchById(spectatorSession.targetId);
                if (spectatedSolo != null) {
                    spectatedSolo.spectators.remove(playerId);
                }
            }
            pendingReturns.put(playerId, spectatorSession.returnState);
            return;
        }

        if (queuedPlayers.remove(playerId)) {
            queue.remove(playerId);
        }

        VictorySequence victorySequence = pendingVictorySequences.remove(playerId);
        if (victorySequence != null) {
            pendingReturns.put(playerId, victorySequence.returnState);
            pendingInventoryReturns.put(playerId, victorySequence.inventorySnapshot);
            teardownArenaSlot(player.getServer(), victorySequence.slot);
            return;
        }

        SoloMatch solo = soloMatches.remove(playerId);
        if (solo != null) {
            WorldServer world = player.getServer().getWorld(solo.dimensionId);
            if (world != null) {
                removeChestHighlights(world, solo.chestHighlightIds);
            }
            solo.clearBossBar(player.getServer());
            returnSpectatorsForSolo(player.getServer(), solo);
            pendingReturns.put(playerId, solo.returnState);
            pendingInventoryReturns.put(playerId, solo.inventory);
            teardownArenaSlot(player.getServer(), solo.slot);
            return;
        }

        ActiveMatch match = playerToMatch.get(playerId);
        if (match == null) {
            FfaMatch ffa = playerToFfaMatch.get(playerId);
            if (ffa != null) {
                eliminateFfaPlayer(player.getServer(), ffa, playerId, false, TextFormatting.YELLOW + player.getName() + " left the FFA.");
                return;
            }
            if (isArenaDimension(player.dimension)) {
                pendingReturns.put(playerId, ReturnState.overworldSpawn(player.getServer()));
            }
            return;
        }

        ReturnState quitterReturn = match.getReturnState(playerId);
        PlayerInventorySnapshot quitterInventory = match.getInventorySnapshot(playerId);
        pendingReturns.put(playerId, quitterReturn);
        pendingInventoryReturns.put(playerId, quitterInventory);

        UUID winnerId = match.getOpponent(playerId);
        EntityPlayerMP winner = player.getServer().getPlayerList().getPlayerByUUID(winnerId);
        if (winner != null) {
            send(winner, TextFormatting.GREEN + "Opponent disconnected. " + TextFormatting.GRAY + "Win by default.");
        }

        endMatch(player.getServer(), match, playerId);
    }

    public synchronized void handleRespawn(EntityPlayerMP player) {
        if (restorePendingState(player)) {
            send(player, TextFormatting.GREEN + "Returned to your original location.");
        }
    }

    public synchronized void handleLogin(EntityPlayerMP player) {
        if (restorePendingState(player)) {
            send(player, TextFormatting.GREEN + "Returned to your original location.");
            return;
        }

        // Crash-recovery: player has a persisted PVP state but no in-memory record
        // (e.g. the server crashed while they were in an arena).
        PvpPlayerStateSavedData stateData = PvpPlayerStateSavedData.get(player.getServer());
        if (stateData.hasState(player.getUniqueID())) {
            int returnDim    = stateData.getReturnDimension(player.getUniqueID());
            double[] pos     = stateData.getReturnPosition(player.getUniqueID());
            float[]  rot     = stateData.getReturnRotation(player.getUniqueID());
            WorldServer returnWorld = player.getServer().getWorld(returnDim);
            if (returnWorld == null) {
                returnWorld = player.getServer().getWorld(0);
                pos = new double[]{returnWorld.getSpawnPoint().getX(), returnWorld.getSpawnPoint().getY(), returnWorld.getSpawnPoint().getZ()};
                rot = new float[]{0, 0};
            }
            moveToDimension(player, returnWorld, pos[0], pos[1], pos[2], rot[0], rot[1]);
            stateData.restoreAndClear(player);
            send(player, TextFormatting.GREEN + "Returned to your original location after server restart.");
            return;
        }

        if (isArenaDimension(player.dimension)) {
            SpectatorSession session = spectators.remove(player.getUniqueID());
            removeSpectatorBat(player.getServer(), session);
            restorePlayer(player, ReturnState.overworldSpawn(player.getServer()));
            send(player, TextFormatting.YELLOW + "You were moved out of an expired arena.");
        }
    }

    private boolean restorePendingState(EntityPlayerMP player) {
        boolean restored = false;
        SpectatorSession session = spectators.remove(player.getUniqueID());
        removeSpectatorBat(player.getServer(), session);
        ReturnState returnState = pendingReturns.remove(player.getUniqueID());
        if (returnState != null) {
            restorePlayer(player, returnState);
            restored = true;
        }

        PlayerInventorySnapshot snapshot = pendingInventoryReturns.remove(player.getUniqueID());
        if (snapshot != null) {
            snapshot.restore(player);
            PvpPlayerStateSavedData.get(player.getServer()).clearState(player.getUniqueID());
            restored = true;
        }

        awaitingKitSelection.remove(player.getUniqueID());
        return restored;
    }

    private void endMatch(MinecraftServer server, ActiveMatch match, @Nullable UUID loserId) {
        playerToMatch.remove(match.firstPlayer);
        playerToMatch.remove(match.secondPlayer);
        awaitingKitSelection.remove(match.firstPlayer);
        awaitingKitSelection.remove(match.secondPlayer);
        WorldServer arenaWorld = server.getWorld(match.dimensionId);
        if (arenaWorld != null) {
            removeChestHighlights(arenaWorld, match.chestHighlightIds);
        }
        match.clearBossBar(server);
        returnSpectatorsForMatch(server, match);

        List<EntityPlayerMP> players = new ArrayList<EntityPlayerMP>(2);
        EntityPlayerMP first = server.getPlayerList().getPlayerByUUID(match.firstPlayer);
        EntityPlayerMP second = server.getPlayerList().getPlayerByUUID(match.secondPlayer);
        if (first != null) {
            players.add(first);
        }
        if (second != null) {
            players.add(second);
        }

        EntityPlayerMP winner = null;
        if (loserId != null) {
            winner = (first != null && !first.getUniqueID().equals(loserId)) ? first : second;
        }
        boolean winnerCelebration = winner != null && !winner.isDead;

        for (EntityPlayerMP participant : players) {
            ReturnState returnState = match.getReturnState(participant.getUniqueID());
            PlayerInventorySnapshot inventorySnapshot = match.getInventorySnapshot(participant.getUniqueID());
            participant.removePotionEffect(MobEffects.SLOWNESS);
            participant.removePotionEffect(MobEffects.JUMP_BOOST);
            participant.removePotionEffect(MobEffects.GLOWING);

            if (loserId != null && participant.getUniqueID().equals(loserId) && participant.isDead) {
                pendingReturns.put(participant.getUniqueID(), returnState);
                pendingInventoryReturns.put(participant.getUniqueID(), inventorySnapshot);
                continue;
            }

            if (loserId != null && participant.getUniqueID().equals(loserId)) {
                restorePlayer(participant, returnState);
                inventorySnapshot.restore(participant);
                clearPersistedState(participant);
                send(participant, TextFormatting.RED + "Defeat." + TextFormatting.GRAY + " Returned to original location.");
                continue;
            }

            if (winnerCelebration && participant.getUniqueID().equals(winner.getUniqueID())) {
                beginVictorySequence(participant, returnState, inventorySnapshot, match.dimensionId, match.slot);
                continue;
            }

            restorePlayer(participant, returnState);
            inventorySnapshot.restore(participant);
            clearPersistedState(participant);
            send(participant, TextFormatting.GREEN + "Duel complete. " + TextFormatting.GRAY + "Returned to original location.");
        }

        if (!winnerCelebration) {
            teardownArenaSlot(server, match.slot);
        }
    }

    /** Remove the on-disk state entry for a player after a normal match end. */
    private void clearPersistedState(EntityPlayerMP player) {
        PvpPlayerStateSavedData.get(player.getServer()).clearState(player.getUniqueID());
        pendingArenaArrivals.remove(player.getUniqueID());
    }

    private void restorePlayer(EntityPlayerMP player, ReturnState returnState) {
        WorldServer destinationWorld = player.getServer().getWorld(returnState.dimension);
        if (destinationWorld == null) {
            destinationWorld = player.getServer().getWorld(0);
        }
        if (destinationWorld == null) {
            return;
        }

        player.setGameType(returnState.gameType);
        moveToDimension(player, destinationWorld, returnState.pos.getX() + 0.5D, returnState.pos.getY(), returnState.pos.getZ() + 0.5D, returnState.yaw, returnState.pitch);
        player.setSpectatingEntity(player);
        player.setInvisible(false);
        player.sendPlayerAbilities();
    }

    private void teleportToArena(EntityPlayerMP player, WorldServer arenaWorld, double x, double y, double z, float yaw) {
        moveToDimension(player, arenaWorld, x, y, z, yaw, 0.0F);
        player.fallDistance = 0.0F;
        player.setHealth(player.getMaxHealth());
        player.getFoodStats().setFoodLevel(20);
    }

    private void moveToDimension(EntityPlayerMP player, WorldServer destination, double x, double y, double z, float yaw, float pitch) {
        if (player.dimension != destination.provider.getDimension()) {
            player.changeDimension(destination.provider.getDimension(), new FixedTeleporter(destination, x, y, z, yaw, pitch));
            return;
        }
        player.connection.setPlayerLocation(x, y, z, yaw, pitch);
        destination.updateEntityWithOptionalForce(player, false);
    }

    private ArenaAllocation createArena(MinecraftServer server, String suffix) {
        WorldServer arenaWorld = ensureArenaWorld(server);
        if (arenaWorld == null) {
            return null;
        }

        ArenaSlot slot = allocateArenaSlot();
        if (slot == null) {
            return null;
        }

        ArenaWorldProvider.setArenaSlotSeed(slot.slotX, slot.slotZ, nextUniqueArenaSeed());
        ArenaWorldProvider.setArenaSlotBiome(slot.slotX, slot.slotZ, pickRandomArenaBiome());
        deleteArenaSlotRegionFile(slot);
        return new ArenaAllocation(ARENA_DIMENSION_ID, arenaWorld, slot);
    }

    private DimensionType getOrCreateArenaDimensionType() {
        if (arenaDimensionType != null) {
            return arenaDimensionType;
        }

        try {
            arenaDimensionType = DimensionType.register(
                    ARENA_DIMENSION_TYPE_NAME,
                    ARENA_DIMENSION_TYPE_SUFFIX,
                    ARENA_DIMENSION_TYPE_ID,
                    ArenaWorldProvider.class,
                    false
            );
            return arenaDimensionType;
        } catch (IllegalArgumentException ignored) {
            for (DimensionType type : DimensionType.values()) {
                if (ARENA_DIMENSION_TYPE_NAME.equals(type.getName())) {
                    arenaDimensionType = type;
                    return arenaDimensionType;
                }
            }
            return null;
        }
    }

    private WorldServer ensureArenaWorld(MinecraftServer server) {
        DimensionType dimensionType = getOrCreateArenaDimensionType();
        if (dimensionType == null) {
            return null;
        }
        if (!DimensionManager.isDimensionRegistered(ARENA_DIMENSION_ID)) {
            try {
                DimensionManager.registerDimension(ARENA_DIMENSION_ID, dimensionType);
            } catch (RuntimeException ex) {
                return null;
            }
        }
        try {
            DimensionManager.initDimension(ARENA_DIMENSION_ID);
        } catch (RuntimeException ignored) {
        }
        return server.getWorld(ARENA_DIMENSION_ID);
    }

    @Nullable
    private ArenaSlot allocateArenaSlot() {
        for (int index = 1; index < Integer.MAX_VALUE; index++) {
            int slotX = slotCoordinateForIndex(index);
            int slotZ = 0;
            long key = arenaSlotKey(slotX, slotZ);
            if (allocatedArenaSlots.contains(key) || pendingArenaCleanup.containsKey(key)) {
                continue;
            }
            ArenaSlot slot = new ArenaSlot(slotX, slotZ);
            allocatedArenaSlots.add(key);
            return slot;
        }
        return null;
    }

    private int slotCoordinateForIndex(int index) {
        if (index == 0) {
            return 0;
        }
        int magnitude = (index + 1) / 2;
        return (index % 2 == 0) ? magnitude : -magnitude;
    }

    private long arenaSlotKey(ArenaSlot slot) {
        return arenaSlotKey(slot.slotX, slot.slotZ);
    }

    private long arenaSlotKey(int slotX, int slotZ) {
        return (((long) slotX) << 32) ^ (slotZ & 0xFFFFFFFFL);
    }

    private ArenaPreparedArena prepareArena(MinecraftServer server, String suffix, boolean needsOppositeSpawns) {
        for (int attempt = 0; attempt < ARENA_GENERATION_ATTEMPTS; attempt++) {
            ArenaAllocation arena = createArena(server, suffix);
            if (arena == null) {
                continue;
            }

            preGenerateEntireArena(arena.world, arena.slot);

            BlockPos firstSpawn = findNaturalSpawn(arena.world, arena.slot, arena.slot.toWorldX(ARENA_FIRST_SPAWN_X), arena.slot.toWorldZ(ARENA_SPAWN_Z));
            if (firstSpawn == null) {
                teardownArenaSlot(server, arena.slot);
                continue;
            }

            BlockPos secondSpawn = null;
            if (needsOppositeSpawns) {
                secondSpawn = findNaturalSpawn(arena.world, arena.slot, arena.slot.toWorldX(ARENA_SECOND_SPAWN_X), arena.slot.toWorldZ(ARENA_SPAWN_Z));
                if (secondSpawn == null || firstSpawn.distanceSq(secondSpawn) < 96.0D * 96.0D) {
                    teardownArenaSlot(server, arena.slot);
                    continue;
                }
            }

            return new ArenaPreparedArena(arena.dimensionId, arena.world, arena.slot, firstSpawn, secondSpawn);
        }

        return null;
    }

    private BlockPos findNaturalSpawn(WorldServer world, ArenaSlot slot, int x, int z) {
        int minX = slot.minBlockX + 2;
        int maxX = slot.maxBlockX - 2;
        int minZ = slot.minBlockZ + 2;
        int maxZ = slot.maxBlockZ - 2;
        int clampedX = Math.max(minX, Math.min(maxX, x));
        int clampedZ = Math.max(minZ, Math.min(maxZ, z));

        for (int radius = 0; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int tx = clampedX + dx * 4;
                    int tz = clampedZ + dz * 4;
                    if (tx < minX || tx > maxX || tz < minZ || tz > maxZ) {
                        continue;
                    }

                    preGenerateSpawnArea(world, tx, tz, 2);
                    BlockPos candidate = topSpawnAt(world, tx, tz);
                    if (candidate != null && hasStableGround(world, candidate)) {
                        return candidate;
                    }
                }
            }
        }

        BlockPos fullScan = findBestSpawnAcrossArena(world, clampedX, clampedZ, minX, maxX, minZ, maxZ);
        if (fullScan != null) {
            return fullScan;
        }

        preGenerateSpawnArea(world, clampedX, clampedZ, 3);
        BlockPos fallback = topSpawnAt(world, clampedX, clampedZ);
        return fallback != null && hasStableGround(world, fallback) ? fallback : null;
    }

    private BlockPos findBestSpawnAcrossArena(WorldServer world, int preferredX, int preferredZ, int minX, int maxX, int minZ, int maxZ) {
        BlockPos best = null;
        int bestScore = Integer.MAX_VALUE;

        for (int tx = minX; tx <= maxX; tx += 2) {
            for (int tz = minZ; tz <= maxZ; tz += 2) {
                preGenerateSpawnArea(world, tx, tz, 1);
                BlockPos candidate = topSpawnAt(world, tx, tz);
                if (candidate == null || !hasStableGround(world, candidate)) {
                    continue;
                }

                int dx = Math.abs(tx - preferredX);
                int dz = Math.abs(tz - preferredZ);
                int score = dx * 2 + dz;
                if (score < bestScore) {
                    best = candidate;
                    bestScore = score;
                }
            }
        }

        return best;
    }

    private BlockPos topSpawnAt(WorldServer world, int x, int z) {
        int top = world.getPrecipitationHeight(new BlockPos(x, 0, z)).getY() + 3;
        top = Math.min(top, 255);
        for (int y = top; y >= 1; y--) {
            BlockPos floor = new BlockPos(x, y, z);
            if (world.getBlockState(floor).getMaterial().blocksMovement()
                    && world.isAirBlock(floor.up())
                    && world.isAirBlock(floor.up().up())) {
                return floor.up();
            }
        }
        return null;
    }

    private boolean hasStableGround(WorldServer world, BlockPos spawn) {
        BlockPos below = spawn.down();
        if (!world.getBlockState(below).getMaterial().blocksMovement()) {
            return false;
        }
        return world.isAirBlock(spawn) && world.isAirBlock(spawn.up());
    }

    private void preGenerateSpawnArea(WorldServer world, int x, int z, int radiusChunks) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                world.getChunk(chunkX + dx, chunkZ + dz);
            }
        }
    }

    private void preGenerateEntireArena(WorldServer world, ArenaSlot slot) {
        for (int chunkX = slot.minChunkX; chunkX <= slot.maxChunkX; chunkX++) {
            for (int chunkZ = slot.minChunkZ; chunkZ <= slot.maxChunkZ; chunkZ++) {
                world.getChunk(chunkX, chunkZ);
            }
        }
    }

    private Biome pickRandomArenaBiome() {
        return ARENA_BIOME_POOL[ThreadLocalRandom.current().nextInt(ARENA_BIOME_POOL.length)];
    }

    private long nextUniqueArenaSeed() {
        long z = arenaSeedSequence.getAndIncrement() + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private List<BlockPos> spawnMatchLootChests(WorldServer world, ArenaSlot slot) {
        List<ItemStack> lootPool = HeroSMP.PVP_CHEST_LOOT_MANAGER.getLootPool(world.getMinecraftServer());
        List<BlockPos> spawnedPositions = new ArrayList<BlockPos>();
        if (lootPool.isEmpty()) {
            return spawnedPositions;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int chestsToPlace = random.nextInt(MIN_MATCH_CHESTS, MAX_MATCH_CHESTS + 1);
        int minX = slot.minBlockX + CHEST_EDGE_PADDING;
        int maxX = slot.maxBlockX - CHEST_EDGE_PADDING;
        int minZ = slot.minBlockZ + CHEST_EDGE_PADDING;
        int maxZ = slot.maxBlockZ - CHEST_EDGE_PADDING;
        Set<BlockPos> used = new HashSet<BlockPos>();
        List<TileEntityChest> spawnedChests = new ArrayList<TileEntityChest>();

        int spawned = 0;
        for (int attempts = 0; attempts < CHEST_PLACEMENT_ATTEMPTS && spawned < chestsToPlace; attempts++) {
            int x = random.nextInt(minX, maxX + 1);
            int z = random.nextInt(minZ, maxZ + 1);
            BlockPos spawn = findNaturalSpawn(world, slot, x, z);
            if (spawn == null || !used.add(spawn)) {
                continue;
            }

            if (!world.isAirBlock(spawn)) {
                continue;
            }
            if (!world.getBlockState(spawn.down()).getMaterial().blocksMovement()) {
                continue;
            }

            world.setBlockState(spawn, Blocks.CHEST.getDefaultState(), 2);
            TileEntity tile = world.getTileEntity(spawn);
            if (!(tile instanceof TileEntityChest)) {
                world.setBlockToAir(spawn);
                continue;
            }

            TileEntityChest chest = (TileEntityChest) tile;
            clearChest(chest);
            spawnedPositions.add(new BlockPos(spawn));
            spawnedChests.add(chest);
            spawned++;
        }

        distributeLootEvenlyAcrossChests(spawnedChests, lootPool, random);
        return spawnedPositions;
    }

    private void clearChest(TileEntityChest chest) {
        for (int i = 0; i < chest.getSizeInventory(); i++) {
            chest.setInventorySlotContents(i, ItemStack.EMPTY);
        }
        chest.markDirty();
    }

    private void distributeLootEvenlyAcrossChests(List<TileEntityChest> chests, List<ItemStack> lootPool, ThreadLocalRandom random) {
        if (chests.isEmpty()) {
            return;
        }

        int distributionOffset = random.nextInt(chests.size());
        for (ItemStack template : lootPool) {
            if (template.isEmpty() || template.getCount() <= 0) {
                continue;
            }

            int remaining = template.getCount();
            int stacksPlacedForItem = 0;
            int chestCount = chests.size();
            int basePortions = Math.min(chestCount, Math.max(1, remaining));

            for (int i = 0; i < basePortions && remaining > 0; i++) {
                int chestIndex = (distributionOffset + i) % chestCount;
                int slotsLeft = basePortions - i;
                int amount = (int) Math.ceil((double) remaining / (double) slotsLeft);
                amount = Math.max(1, Math.min(amount, template.getMaxStackSize()));
                ItemStack piece = template.copy();
                piece.setCount(amount);
                if (insertIntoChest(chests.get(chestIndex), piece, random)) {
                    remaining -= amount;
                    stacksPlacedForItem++;
                }
            }

            while (remaining > 0) {
                int chestIndex = (distributionOffset + stacksPlacedForItem) % chestCount;
                int amount = Math.max(1, Math.min(remaining, template.getMaxStackSize()));
                ItemStack piece = template.copy();
                piece.setCount(amount);
                if (!insertIntoChest(chests.get(chestIndex), piece, random)) {
                    break;
                }
                remaining -= amount;
                stacksPlacedForItem++;
            }

            distributionOffset = (distributionOffset + 1) % chests.size();
        }
    }

    private boolean insertIntoChest(TileEntityChest chest, ItemStack stack, ThreadLocalRandom random) {
        List<Integer> freeSlots = new ArrayList<Integer>();
        for (int i = 0; i < chest.getSizeInventory(); i++) {
            if (chest.getStackInSlot(i).isEmpty()) {
                freeSlots.add(i);
            }
        }
        if (freeSlots.isEmpty()) {
            return false;
        }
        int slot = freeSlots.get(random.nextInt(freeSlots.size()));
        chest.setInventorySlotContents(slot, stack);
        chest.markDirty();
        return true;
    }

    private void teardownArenaSlot(MinecraftServer server, ArenaSlot slot) {
        if (slot == null) {
            return;
        }
        WorldServer world = server.getWorld(ARENA_DIMENSION_ID);
        if (world != null) {
            purgeArenaSlot(world, slot);
        }
        pendingArenaCleanup.put(arenaSlotKey(slot), new ArenaCleanupState(slot));
        allocatedArenaSlots.remove(arenaSlotKey(slot));
        ArenaWorldProvider.clearArenaSlot(slot.slotX, slot.slotZ);
    }

    public synchronized void tickCleanup(MinecraftServer server) {
        if (pendingArenaCleanup.isEmpty()) {
            return;
        }

        WorldServer world = server.getWorld(ARENA_DIMENSION_ID);
        if (world == null) {
            return;
        }

        List<Long> toRemove = new ArrayList<Long>();
        for (Map.Entry<Long, ArenaCleanupState> entry : pendingArenaCleanup.entrySet()) {
            ArenaCleanupState state = entry.getValue();

            if (isArenaSlotInUse(state.slot)) {
                state.ticksSinceQueued = 0;
                continue;
            }

            if (state.ticksSinceQueued % CLEANUP_UNLOAD_RETRY_TICKS == 0) {
                unloadArenaSlotChunks(world, state.slot);
            }
            if (areArenaSlotChunksLoaded(world, state.slot)) {
                state.ticksSinceQueued++;
                continue;
            }

            if (state.ticksSinceQueued < CLEANUP_DELETE_DELAY_TICKS) {
                state.ticksSinceQueued++;
                continue;
            }

            deleteArenaSlotRegionFile(state.slot);
            toRemove.add(entry.getKey());
        }

        for (Long key : toRemove) {
            pendingArenaCleanup.remove(key);
        }
    }

    public synchronized void tickMatchProgress(MinecraftServer server) {
        tickFfaQueue(server);
        for (ActiveMatch match : new HashSet<ActiveMatch>(playerToMatch.values())) {
            tickMatch(server, match);
        }
        for (FfaMatch match : new HashSet<FfaMatch>(playerToFfaMatch.values())) {
            tickFfaMatch(server, match);
        }
        for (SoloMatch solo : new ArrayList<SoloMatch>(soloMatches.values())) {
            tickSoloMatch(server, solo);
        }
        tickSpectatorBats(server);
        tickVictorySequences(server);
    }

    private void tickSpectatorBats(MinecraftServer server) {
        if (spectators.isEmpty()) {
            return;
        }

        for (Map.Entry<UUID, SpectatorSession> entry : new ArrayList<Map.Entry<UUID, SpectatorSession>>(spectators.entrySet())) {
            EntityPlayerMP spectator = server.getPlayerList().getPlayerByUUID(entry.getKey());
            SpectatorSession session = entry.getValue();
            if (spectator == null || spectator.isDead || spectator.interactionManager.getGameType() != GameType.SPECTATOR) {
                removeSpectatorBat(server, session);
                continue;
            }

            WorldServer world = server.getWorld(spectator.dimension);
            if (world == null) {
                removeSpectatorBat(server, session);
                continue;
            }

            EntityBat bat = getSpectatorBat(world, session);
            if (bat == null || bat.isDead) {
                spawnSpectatorBat(world, spectator, session);
                bat = getSpectatorBat(world, session);
            }
            if (bat == null) {
                continue;
            }

            bat.setNoAI(false);
            bat.setNoGravity(true);
            bat.setIsBatHanging(false);
            bat.setSilent(true);
            bat.setEntityInvulnerable(true);
            bat.setHealth(bat.getMaxHealth());
            double eyeY = spectator.posY + spectator.getEyeHeight();
            bat.setPositionAndRotation(spectator.posX, eyeY, spectator.posZ, spectator.rotationYaw, spectator.rotationPitch);
            bat.rotationYawHead = spectator.rotationYaw;
            bat.renderYawOffset = spectator.rotationYaw;
            bat.prevRotationYaw = spectator.rotationYaw;
            bat.prevRotationYawHead = spectator.rotationYaw;
            bat.prevRenderYawOffset = spectator.rotationYaw;
            bat.motionX = 0.0D;
            bat.motionY = 0.0D;
            bat.motionZ = 0.0D;
            bat.fallDistance = 0.0F;
            syncSpectatorBatVisibility(server, spectator, session, bat);
        }
    }

    private void syncSpectatorBatVisibility(MinecraftServer server, EntityPlayerMP owner, SpectatorSession session, EntityBat bat) {
        SPacketDestroyEntities destroyPacket = new SPacketDestroyEntities(bat.getEntityId());
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            if (player == null || player.dimension != owner.dimension) {
                continue;
            }
            if (!shouldPlayerSeeSpectatorBat(player, owner, session)) {
                player.connection.sendPacket(destroyPacket);
            }
        }
    }

    private boolean shouldPlayerSeeSpectatorBat(EntityPlayerMP viewer, EntityPlayerMP owner, SpectatorSession session) {
        if (viewer.getUniqueID().equals(owner.getUniqueID())) {
            return false;
        }

        UUID viewerId = viewer.getUniqueID();
        if (session.target == SpectateTarget.ACTIVE_MATCH) {
            ActiveMatch match = findMatchById(session.targetId);
            return match != null && (match.firstPlayer.equals(viewerId) || match.secondPlayer.equals(viewerId) || match.spectators.contains(viewerId));
        }
        if (session.target == SpectateTarget.FFA_MATCH) {
            FfaMatch match = findFfaMatchById(session.targetId);
            return match != null && (match.playerOrder.contains(viewerId) || match.spectators.contains(viewerId));
        }
        SoloMatch solo = findSoloMatchById(session.targetId);
        return solo != null && (solo.playerId.equals(viewerId) || solo.spectators.contains(viewerId));
    }

    private void tickFfaQueue(MinecraftServer server) {
        if (ffaQueuedPlayers.size() >= FFA_MAX_PLAYERS) {
            startFfaFromQueue(server, true);
            return;
        }
        if (ffaQueueCountdownTicks < 0) {
            if (ffaQueuedPlayers.size() >= FFA_MIN_PLAYERS) {
                ffaQueueCountdownTicks = FFA_QUEUE_WAIT_TICKS;
            }
            return;
        }

        if (ffaQueuedPlayers.size() < FFA_MIN_PLAYERS) {
            ffaQueueCountdownTicks = -1;
            return;
        }

        if (ffaQueueCountdownTicks % 20 == 0) {
            int seconds = Math.max(1, ffaQueueCountdownTicks / 20);
            if (seconds == 20 || seconds == 10 || seconds <= 5) {
                sendQueuedFfaPlayers(server, TextFormatting.AQUA + "FFA starts in " + seconds + "s...");
            }
        }
        ffaQueueCountdownTicks--;
        if (ffaQueueCountdownTicks <= 0) {
            startFfaFromQueue(server, false);
        }
    }

    private void tickVictorySequences(MinecraftServer server) {
        if (pendingVictorySequences.isEmpty()) {
            return;
        }

        for (VictorySequence sequence : new ArrayList<VictorySequence>(pendingVictorySequences.values())) {
            EntityPlayerMP winner = server.getPlayerList().getPlayerByUUID(sequence.playerId);
            if (winner == null) {
                pendingVictorySequences.remove(sequence.playerId);
                pendingReturns.put(sequence.playerId, sequence.returnState);
                pendingInventoryReturns.put(sequence.playerId, sequence.inventorySnapshot);
                teardownArenaSlot(server, sequence.slot);
                continue;
            }

            WorldServer world = server.getWorld(sequence.dimensionId);
            if (world == null || winner.dimension != sequence.dimensionId || winner.isDead) {
                pendingVictorySequences.remove(sequence.playerId);
                if (winner.isDead) {
                    pendingReturns.put(sequence.playerId, sequence.returnState);
                    pendingInventoryReturns.put(sequence.playerId, sequence.inventorySnapshot);
                } else {
                    restorePlayer(winner, sequence.returnState);
                    sequence.inventorySnapshot.restore(winner);
                    clearPersistedState(winner);
                }
                teardownArenaSlot(server, sequence.slot);
                continue;
            }

            if (sequence.ticksRemaining % 20 == 0) {
                spawnVictoryFirework(world, winner.posX, winner.posY + 0.5D, winner.posZ);
            }

            sequence.ticksRemaining--;
            if (sequence.ticksRemaining > 0) {
                continue;
            }

            pendingVictorySequences.remove(sequence.playerId);
            winner.removePotionEffect(MobEffects.GLOWING);
            restorePlayer(winner, sequence.returnState);
            sequence.inventorySnapshot.restore(winner);
            clearPersistedState(winner);
            send(winner, TextFormatting.GOLD + "Winner: " + TextFormatting.WHITE + sequence.winnerName + TextFormatting.GRAY + ". Returned to original location.");
            teardownArenaSlot(server, sequence.slot);
        }
    }

    private void tickMatch(MinecraftServer server, ActiveMatch match) {
        WorldServer world = server.getWorld(match.dimensionId);
        if (world == null) {
            return;
        }

        if (match.phase == MatchPhase.PREPARE) {
            freezeMatchPlayers(server, match);
            if (allMatchPlayersReady(match)) {
                match.phase = MatchPhase.START_COUNTDOWN;
                match.phaseTicksRemaining = PREP_COUNTDOWN_TICKS;
                sendMatchChat(server, match, TextFormatting.GREEN + "Both players selected kits. Starting now...");
                return;
            }
            if (match.phaseTicksRemaining % 20 == 0) {
                int seconds = Math.max(1, match.phaseTicksRemaining / 20);
                sendMatchChat(server, match, TextFormatting.YELLOW + "Duel starts in " + seconds + "s." + TextFormatting.GRAY + " Pick your kit now.");
            }
            match.phaseTicksRemaining--;
            if (match.phaseTicksRemaining <= 0) {
                match.phase = MatchPhase.START_COUNTDOWN;
                match.phaseTicksRemaining = PREP_COUNTDOWN_TICKS;
            }
            return;
        }

        if (match.phase == MatchPhase.START_COUNTDOWN) {
            freezeMatchPlayers(server, match);
            if (match.phaseTicksRemaining % 20 == 0) {
                int seconds = Math.max(1, match.phaseTicksRemaining / 20);
                sendCountdownTitle(server, match, String.valueOf(seconds), TextFormatting.GOLD + "Fight begins soon");
                playMatchSound(world, match.slot, SoundEvents.BLOCK_NOTE_HARP, SoundCategory.PLAYERS, 1.0F, 1.0F);
            }
            match.phaseTicksRemaining--;
            if (match.phaseTicksRemaining <= 0) {
                startActiveRound(server, match, world);
            }
            return;
        }

        if (match.phase != MatchPhase.ACTIVE) {
            return;
        }

        match.phaseTicksRemaining--;
        updateRoundBossBar(match);

        if (!match.chestsRevealed && match.phaseTicksRemaining <= Math.max(20, match.roundTicks / 2)) {
            revealChests(server, match, world);
            match.chestsRevealed = true;
        }

        if (!match.playersRevealed && match.phaseTicksRemaining <= PLAYER_REVEAL_AT_SECONDS * 20) {
            revealPlayers(server, match);
            match.playersRevealed = true;
        }

        if (match.phaseTicksRemaining <= 0) {
            resolveMatchByTimeout(server, match);
        }
    }

    private void tickFfaMatch(MinecraftServer server, FfaMatch match) {
        WorldServer world = server.getWorld(match.dimensionId);
        if (world == null) {
            return;
        }

        if (match.phase == MatchPhase.PREPARE) {
            freezeFfaPlayers(server, match);
            if (allFfaPlayersReady(match)) {
                match.phase = MatchPhase.START_COUNTDOWN;
                match.phaseTicksRemaining = PREP_COUNTDOWN_TICKS;
                sendFfaChat(server, match, TextFormatting.GREEN + "All players selected kits. Starting now...");
                return;
            }
            if (match.phaseTicksRemaining % 20 == 0) {
                int seconds = Math.max(1, match.phaseTicksRemaining / 20);
                sendFfaChat(server, match, TextFormatting.YELLOW + "FFA starts in " + seconds + "s." + TextFormatting.GRAY + " Pick your kit now.");
            }
            match.phaseTicksRemaining--;
            if (match.phaseTicksRemaining <= 0) {
                match.phase = MatchPhase.START_COUNTDOWN;
                match.phaseTicksRemaining = PREP_COUNTDOWN_TICKS;
            }
            return;
        }

        if (match.phase == MatchPhase.START_COUNTDOWN) {
            freezeFfaPlayers(server, match);
            if (match.phaseTicksRemaining % 20 == 0) {
                int seconds = Math.max(1, match.phaseTicksRemaining / 20);
                sendFfaTitle(server, match, String.valueOf(seconds), TextFormatting.GOLD + "Fight begins soon");
                playMatchSound(world, match.slot, SoundEvents.BLOCK_NOTE_HARP, SoundCategory.PLAYERS, 1.0F, 1.0F);
            }
            match.phaseTicksRemaining--;
            if (match.phaseTicksRemaining <= 0) {
                startActiveFfaRound(server, match, world);
            }
            return;
        }

        if (match.phase != MatchPhase.ACTIVE) {
            return;
        }

        match.phaseTicksRemaining--;
        updateRoundBossBar(match);

        if (!match.chestsRevealed && match.phaseTicksRemaining <= Math.max(20, match.roundTicks / 2)) {
            revealFfaChests(server, match, world);
            match.chestsRevealed = true;
        }

        if (!match.playersRevealed && match.phaseTicksRemaining <= PLAYER_REVEAL_AT_SECONDS * 20) {
            revealFfaPlayers(server, match);
            match.playersRevealed = true;
        }

        if (match.alivePlayers.size() <= 1) {
            UUID winnerId = match.alivePlayers.isEmpty() ? null : match.alivePlayers.iterator().next();
            endFfaMatch(server, match, winnerId);
            return;
        }

        if (match.phaseTicksRemaining <= 0) {
            resolveFfaByTimeout(server, match);
        }
    }

    private void tickSoloMatch(MinecraftServer server, SoloMatch solo) {
        WorldServer world = server.getWorld(solo.dimensionId);
        if (world == null) {
            return;
        }

        EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(solo.playerId);
        if (player == null || player.dimension != solo.dimensionId) {
            return;
        }

        if (solo.phase == MatchPhase.PREPARE) {
            freezePlayer(server, solo.playerId, solo.dimensionId, solo.spawn);
            if (!awaitingKitSelection.contains(solo.playerId)) {
                solo.phase = MatchPhase.START_COUNTDOWN;
                solo.phaseTicksRemaining = PREP_COUNTDOWN_TICKS;
                sendSoloChat(server, solo, TextFormatting.GREEN + "Kit selected. Starting now...");
                return;
            }
            if (solo.phaseTicksRemaining % 20 == 0) {
                int seconds = Math.max(1, solo.phaseTicksRemaining / 20);
                sendSoloChat(server, solo, TextFormatting.YELLOW + "Duel starts in " + seconds + "s." + TextFormatting.GRAY + " Pick your kit now.");
            }
            solo.phaseTicksRemaining--;
            if (solo.phaseTicksRemaining <= 0) {
                solo.phase = MatchPhase.START_COUNTDOWN;
                solo.phaseTicksRemaining = PREP_COUNTDOWN_TICKS;
            }
            return;
        }

        if (solo.phase == MatchPhase.START_COUNTDOWN) {
            freezePlayer(server, solo.playerId, solo.dimensionId, solo.spawn);
            if (solo.phaseTicksRemaining % 20 == 0) {
                int seconds = Math.max(1, solo.phaseTicksRemaining / 20);
                sendTitle(player, String.valueOf(seconds), TextFormatting.GOLD + "Fight begins soon", 12);
                playMatchSound(world, solo.slot, SoundEvents.BLOCK_NOTE_HARP, SoundCategory.PLAYERS, 1.0F, 1.0F);
            }
            solo.phaseTicksRemaining--;
            if (solo.phaseTicksRemaining <= 0) {
                startActiveSoloRound(server, solo, world, player);
            }
            return;
        }

        if (solo.phase != MatchPhase.ACTIVE) {
            return;
        }

        solo.phaseTicksRemaining--;
        updateRoundBossBar(solo);

        if (!solo.chestsRevealed && solo.phaseTicksRemaining <= Math.max(20, solo.roundTicks / 2)) {
            revealSoloChests(server, solo, world);
            solo.chestsRevealed = true;
        }

        if (!solo.playersRevealed && solo.phaseTicksRemaining <= PLAYER_REVEAL_AT_SECONDS * 20) {
            player.addPotionEffect(new PotionEffect(MobEffects.GLOWING, Math.max(60, solo.phaseTicksRemaining), 0, false, false));
            sendSoloChat(server, solo, TextFormatting.RED + "Final moments: players are now revealed.");
            solo.playersRevealed = true;
        }

        if (solo.phaseTicksRemaining <= 0) {
            sendSoloChat(server, solo, TextFormatting.YELLOW + "Time is up. " + TextFormatting.GRAY + "Debug round ended.");
            removeChestHighlights(world, solo.chestHighlightIds);
            solo.clearBossBar(server);
            returnSpectatorsForSolo(server, solo);
            player.removePotionEffect(MobEffects.SLOWNESS);
            player.removePotionEffect(MobEffects.JUMP_BOOST);
            player.removePotionEffect(MobEffects.GLOWING);
            restorePlayer(player, solo.returnState);
            solo.inventory.restore(player);
            clearPersistedState(player);
            teardownArenaSlot(server, solo.slot);
            awaitingKitSelection.remove(solo.playerId);
            soloMatches.remove(solo.playerId);
        }
    }

    private void freezeMatchPlayers(MinecraftServer server, ActiveMatch match) {
        freezePlayer(server, match.firstPlayer, match.dimensionId, match.firstSpawn);
        freezePlayer(server, match.secondPlayer, match.dimensionId, match.secondSpawn);
    }

    private boolean allMatchPlayersReady(ActiveMatch match) {
        return !awaitingKitSelection.contains(match.firstPlayer) && !awaitingKitSelection.contains(match.secondPlayer);
    }

    private boolean allFfaPlayersReady(FfaMatch match) {
        for (UUID playerId : match.playerOrder) {
            if (awaitingKitSelection.contains(playerId)) {
                return false;
            }
        }
        return true;
    }

    private void freezeFfaPlayers(MinecraftServer server, FfaMatch match) {
        for (UUID playerId : match.alivePlayers) {
            BlockPos spawn = match.spawns.get(playerId);
            if (spawn != null) {
                freezePlayer(server, playerId, match.dimensionId, spawn);
            }
        }
    }

    private void freezePlayer(MinecraftServer server, UUID playerId, int dimensionId, BlockPos target) {
        EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
        if (player == null || player.dimension != dimensionId) {
            return;
        }

        double tx = target.getX() + 0.5D;
        double ty = target.getY();
        double tz = target.getZ() + 0.5D;
        double dx = player.posX - tx;
        double dz = player.posZ - tz;
        if ((dx * dx + dz * dz) > 4.0D) {
            player.connection.setPlayerLocation(tx, ty, tz, player.rotationYaw, player.rotationPitch);
        }

        player.motionX = 0.0D;
        if (player.motionY > 0.0D) {
            player.motionY = 0.0D;
        }
        player.motionZ = 0.0D;
        player.fallDistance = 0.0F;
        player.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 25, 10, false, false));
    }

    private void startActiveRound(MinecraftServer server, ActiveMatch match, WorldServer world) {
        match.phase = MatchPhase.ACTIVE;
        match.roundTicks = getRoundDurationTicks(server);
        match.phaseTicksRemaining = match.roundTicks;
        clearFreezeEffects(server, match);
        sendCountdownTitle(server, match, TextFormatting.GREEN + "FIGHT!", TextFormatting.RED + "Eliminate your opponent");
        playMatchSound(world, match.slot, SoundEvents.ENTITY_ENDERDRAGON_GROWL, SoundCategory.HOSTILE, 1.5F, 1.0F);
        match.bossBar = createRoundBossBar();
        match.addBossBarPlayers(server);
        for (UUID spectatorId : match.spectators) {
            EntityPlayerMP spectator = server.getPlayerList().getPlayerByUUID(spectatorId);
            if (spectator != null) {
                match.bossBar.addPlayer(spectator);
            }
        }
        updateRoundBossBar(match);
    }

    private void startActiveSoloRound(MinecraftServer server, SoloMatch solo, WorldServer world, EntityPlayerMP player) {
        solo.phase = MatchPhase.ACTIVE;
        solo.roundTicks = getRoundDurationTicks(server);
        solo.phaseTicksRemaining = solo.roundTicks;
        player.removePotionEffect(MobEffects.SLOWNESS);
        player.removePotionEffect(MobEffects.JUMP_BOOST);
        sendTitle(player, TextFormatting.GREEN + "FIGHT!", TextFormatting.RED + "Debug solo round started", 18);
        playMatchSound(world, solo.slot, SoundEvents.ENTITY_ENDERDRAGON_GROWL, SoundCategory.HOSTILE, 1.5F, 1.0F);
        solo.bossBar = createRoundBossBar();
        solo.addBossBarPlayer(server);
        for (UUID spectatorId : solo.spectators) {
            EntityPlayerMP spectator = server.getPlayerList().getPlayerByUUID(spectatorId);
            if (spectator != null) {
                solo.bossBar.addPlayer(spectator);
            }
        }
        updateRoundBossBar(solo);
    }

    private void startActiveFfaRound(MinecraftServer server, FfaMatch match, WorldServer world) {
        match.phase = MatchPhase.ACTIVE;
        match.roundTicks = getRoundDurationTicks(server);
        match.phaseTicksRemaining = match.roundTicks;
        for (UUID playerId : match.playerOrder) {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
            if (player != null) {
                player.removePotionEffect(MobEffects.SLOWNESS);
                player.removePotionEffect(MobEffects.JUMP_BOOST);
            }
        }
        sendFfaTitle(server, match, TextFormatting.GREEN + "FIGHT!", TextFormatting.RED + "Eliminate all opponents");
        playMatchSound(world, match.slot, SoundEvents.ENTITY_ENDERDRAGON_GROWL, SoundCategory.HOSTILE, 1.5F, 1.0F);
        match.bossBar = createRoundBossBar();
        for (UUID playerId : match.playerOrder) {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
            if (player != null) {
                match.bossBar.addPlayer(player);
            }
        }
        for (UUID spectatorId : match.spectators) {
            EntityPlayerMP spectator = server.getPlayerList().getPlayerByUUID(spectatorId);
            if (spectator != null) {
                match.bossBar.addPlayer(spectator);
            }
        }
        updateRoundBossBar(match);
    }

    private void clearFreezeEffects(MinecraftServer server, ActiveMatch match) {
        EntityPlayerMP first = server.getPlayerList().getPlayerByUUID(match.firstPlayer);
        EntityPlayerMP second = server.getPlayerList().getPlayerByUUID(match.secondPlayer);
        if (first != null) {
            first.removePotionEffect(MobEffects.SLOWNESS);
            first.removePotionEffect(MobEffects.JUMP_BOOST);
        }
        if (second != null) {
            second.removePotionEffect(MobEffects.SLOWNESS);
            second.removePotionEffect(MobEffects.JUMP_BOOST);
        }
    }

    private BossInfoServer createRoundBossBar() {
        return new BossInfoServer(new TextComponentString(TextFormatting.RED + "PvP Round"), BossInfo.Color.RED, BossInfo.Overlay.NOTCHED_10);
    }

    private void updateRoundBossBar(ActiveMatch match) {
        if (match.bossBar == null) {
            return;
        }
        int secondsLeft = Math.max(0, (match.phaseTicksRemaining + 19) / 20);
        float totalTicks = Math.max(1.0F, match.roundTicks);
        float progress = Math.max(0.0F, Math.min(1.0F, match.phaseTicksRemaining / totalTicks));
        ITextComponent title = new TextComponentString(TextFormatting.DARK_RED + "PvP Round " + TextFormatting.GOLD + secondsLeft + "s");
        match.bossBar.setName(title);
        match.bossBar.setPercent(progress);
    }

    private void updateRoundBossBar(SoloMatch solo) {
        if (solo.bossBar == null) {
            return;
        }
        int secondsLeft = Math.max(0, (solo.phaseTicksRemaining + 19) / 20);
        float totalTicks = Math.max(1.0F, solo.roundTicks);
        float progress = Math.max(0.0F, Math.min(1.0F, solo.phaseTicksRemaining / totalTicks));
        ITextComponent title = new TextComponentString(TextFormatting.DARK_RED + "PvP Round " + TextFormatting.GOLD + secondsLeft + "s");
        solo.bossBar.setName(title);
        solo.bossBar.setPercent(progress);
    }

    private void updateRoundBossBar(FfaMatch match) {
        if (match.bossBar == null) {
            return;
        }
        int secondsLeft = Math.max(0, (match.phaseTicksRemaining + 19) / 20);
        float totalTicks = Math.max(1.0F, match.roundTicks);
        float progress = Math.max(0.0F, Math.min(1.0F, match.phaseTicksRemaining / totalTicks));
        ITextComponent title = new TextComponentString(TextFormatting.DARK_RED + "FFA Round " + TextFormatting.GOLD + secondsLeft + "s");
        match.bossBar.setName(title);
        match.bossBar.setPercent(progress);
    }

    private void revealChests(MinecraftServer server, ActiveMatch match, WorldServer world) {
        if (match.chestPositions.isEmpty()) {
            sendMatchChat(server, match, TextFormatting.YELLOW + "No supply chests were generated this round.");
            return;
        }

        spawnChestHighlights(world, match.chestPositions, match.chestHighlightIds);
        sendMatchChat(server, match, TextFormatting.GOLD + "Supply chests revealed and highlighted:");
        for (BlockPos chestPos : match.chestPositions) {
            sendMatchChat(server, match, TextFormatting.AQUA + "- " + chestPos.getX() + ", " + chestPos.getY() + ", " + chestPos.getZ());
            world.spawnParticle(EnumParticleTypes.VILLAGER_HAPPY, true, chestPos.getX() + 0.5D, chestPos.getY() + 1.1D, chestPos.getZ() + 0.5D, 24, 0.35D, 0.4D, 0.35D, 0.0D);
        }
    }

    private void revealPlayers(MinecraftServer server, ActiveMatch match) {
        int revealDuration = Math.max(60, match.phaseTicksRemaining);
        EntityPlayerMP first = server.getPlayerList().getPlayerByUUID(match.firstPlayer);
        EntityPlayerMP second = server.getPlayerList().getPlayerByUUID(match.secondPlayer);
        if (first != null) {
            first.addPotionEffect(new PotionEffect(MobEffects.GLOWING, revealDuration, 0, false, false));
        }
        if (second != null) {
            second.addPotionEffect(new PotionEffect(MobEffects.GLOWING, revealDuration, 0, false, false));
        }
        sendMatchChat(server, match, TextFormatting.RED + "Final moments: all players are now revealed!");
    }

    private void revealFfaChests(MinecraftServer server, FfaMatch match, WorldServer world) {
        if (match.chestPositions.isEmpty()) {
            sendFfaChat(server, match, TextFormatting.YELLOW + "No supply chests were generated this round.");
            return;
        }
        spawnChestHighlights(world, match.chestPositions, match.chestHighlightIds);
        sendFfaChat(server, match, TextFormatting.GOLD + "Supply chests revealed and highlighted:");
        for (BlockPos chestPos : match.chestPositions) {
            sendFfaChat(server, match, TextFormatting.AQUA + "- " + chestPos.getX() + ", " + chestPos.getY() + ", " + chestPos.getZ());
            world.spawnParticle(EnumParticleTypes.VILLAGER_HAPPY, true, chestPos.getX() + 0.5D, chestPos.getY() + 1.1D, chestPos.getZ() + 0.5D, 24, 0.35D, 0.4D, 0.35D, 0.0D);
        }
    }

    private void revealFfaPlayers(MinecraftServer server, FfaMatch match) {
        int revealDuration = Math.max(60, match.phaseTicksRemaining);
        for (UUID playerId : match.alivePlayers) {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
            if (player != null) {
                player.addPotionEffect(new PotionEffect(MobEffects.GLOWING, revealDuration, 0, false, false));
            }
        }
        sendFfaChat(server, match, TextFormatting.RED + "Final moments: all players are now revealed!");
    }

    private void revealSoloChests(MinecraftServer server, SoloMatch solo, WorldServer world) {
        if (solo.chestPositions.isEmpty()) {
            sendSoloChat(server, solo, TextFormatting.YELLOW + "No supply chests were generated this round.");
            return;
        }

        spawnChestHighlights(world, solo.chestPositions, solo.chestHighlightIds);
        sendSoloChat(server, solo, TextFormatting.GOLD + "Supply chests revealed and highlighted:");
        for (BlockPos chestPos : solo.chestPositions) {
            sendSoloChat(server, solo, TextFormatting.AQUA + "- " + chestPos.getX() + ", " + chestPos.getY() + ", " + chestPos.getZ());
            world.spawnParticle(EnumParticleTypes.VILLAGER_HAPPY, true, chestPos.getX() + 0.5D, chestPos.getY() + 1.1D, chestPos.getZ() + 0.5D, 24, 0.35D, 0.4D, 0.35D, 0.0D);
        }
    }

    private void resolveMatchByTimeout(MinecraftServer server, ActiveMatch match) {
        EntityPlayerMP first = server.getPlayerList().getPlayerByUUID(match.firstPlayer);
        EntityPlayerMP second = server.getPlayerList().getPlayerByUUID(match.secondPlayer);

        if (first == null && second == null) {
            endMatch(server, match, null);
            return;
        }

        if (first == null) {
            if (second != null) {
                send(second, TextFormatting.GREEN + "Victory! " + TextFormatting.GRAY + "Opponent left before the timer ended.");
            }
            endMatch(server, match, match.firstPlayer);
            return;
        }

        if (second == null) {
            send(first, TextFormatting.GREEN + "Victory! " + TextFormatting.GRAY + "Opponent left before the timer ended.");
            endMatch(server, match, match.secondPlayer);
            return;
        }

        double firstScore = first.getHealth() + first.getAbsorptionAmount();
        double secondScore = second.getHealth() + second.getAbsorptionAmount();

        if (Math.abs(firstScore - secondScore) < 0.1D) {
            send(first, TextFormatting.YELLOW + "Time is up. " + TextFormatting.GRAY + "Round ended in a draw.");
            send(second, TextFormatting.YELLOW + "Time is up. " + TextFormatting.GRAY + "Round ended in a draw.");
            endMatch(server, match, null);
            return;
        }

        UUID loserId = firstScore > secondScore ? second.getUniqueID() : first.getUniqueID();
        EntityPlayerMP winner = loserId.equals(first.getUniqueID()) ? second : first;
        EntityPlayerMP loser = loserId.equals(first.getUniqueID()) ? first : second;
        send(winner, TextFormatting.GREEN + "Victory! " + TextFormatting.GRAY + "Higher health when the timer ended.");
        send(loser, TextFormatting.RED + "Defeat. " + TextFormatting.GRAY + "Lower health when the timer ended.");
        endMatch(server, match, loserId);
    }

    private void resolveFfaByTimeout(MinecraftServer server, FfaMatch match) {
        UUID winnerId = null;
        double bestScore = -1.0D;
        for (UUID playerId : new HashSet<UUID>(match.alivePlayers)) {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
            if (player == null) {
                match.alivePlayers.remove(playerId);
                continue;
            }
            double score = player.getHealth() + player.getAbsorptionAmount();
            if (score > bestScore) {
                bestScore = score;
                winnerId = playerId;
            }
        }
        endFfaMatch(server, match, winnerId);
    }

    private void forfeitMatch(MinecraftServer server, EntityPlayerMP leaver, ActiveMatch match) {
        UUID leaverId = leaver.getUniqueID();
        if (!match.firstPlayer.equals(leaverId) && !match.secondPlayer.equals(leaverId)) {
            send(leaver, TextFormatting.RED + "You are not part of this match.");
            return;
        }

        UUID winnerId = match.getOpponent(leaverId);
        EntityPlayerMP winner = server.getPlayerList().getPlayerByUUID(winnerId);
        send(leaver, TextFormatting.YELLOW + "You forfeited the match.");
        if (winner != null) {
            send(winner, TextFormatting.GREEN + "Victory! " + TextFormatting.GRAY + "Opponent forfeited via /return.");
        }
        endMatch(server, match, leaverId);
    }

    private void forfeitFfaMatch(MinecraftServer server, EntityPlayerMP leaver, FfaMatch match) {
        eliminateFfaPlayer(server, match, leaver.getUniqueID(), false, TextFormatting.YELLOW + leaver.getName() + " forfeited.");
    }

    private void eliminateFfaPlayer(MinecraftServer server, FfaMatch match, UUID playerId, boolean deadElimination, String reasonMessage) {
        if (!match.playerOrder.contains(playerId)) {
            return;
        }
        if (!match.alivePlayers.remove(playerId)) {
            return;
        }

        EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
        ReturnState returnState = match.returnStates.get(playerId);
        PlayerInventorySnapshot snapshot = match.inventorySnapshots.get(playerId);

        if (player != null) {
            player.removePotionEffect(MobEffects.SLOWNESS);
            player.removePotionEffect(MobEffects.JUMP_BOOST);
            player.removePotionEffect(MobEffects.GLOWING);
            if (deadElimination || player.isDead) {
                pendingReturns.put(playerId, returnState);
                pendingInventoryReturns.put(playerId, snapshot);
            } else {
                restorePlayer(player, returnState);
                snapshot.restore(player);
            }
        } else {
            pendingReturns.put(playerId, returnState);
            pendingInventoryReturns.put(playerId, snapshot);
        }

        playerToFfaMatch.remove(playerId);
        awaitingKitSelection.remove(playerId);
        sendFfaChat(server, match, reasonMessage);

        if (match.alivePlayers.size() <= 1) {
            UUID winnerId = match.alivePlayers.isEmpty() ? null : match.alivePlayers.iterator().next();
            endFfaMatch(server, match, winnerId);
        }
    }

    private void endFfaMatch(MinecraftServer server, FfaMatch match, @Nullable UUID winnerId) {
        WorldServer arenaWorld = server.getWorld(match.dimensionId);
        if (arenaWorld != null) {
            removeChestHighlights(arenaWorld, match.chestHighlightIds);
        }
        if (match.bossBar != null) {
            for (EntityPlayerMP player : new ArrayList<EntityPlayerMP>(match.bossBar.getPlayers())) {
                match.bossBar.removePlayer(player);
            }
            match.bossBar.setVisible(false);
            match.bossBar = null;
        }
        returnSpectatorsForFfa(server, match);

        EntityPlayerMP winner = winnerId == null ? null : server.getPlayerList().getPlayerByUUID(winnerId);
        boolean winnerCelebration = winner != null && !winner.isDead;

        for (UUID playerId : new ArrayList<UUID>(match.playerOrder)) {
            playerToFfaMatch.remove(playerId);
            awaitingKitSelection.remove(playerId);

            ReturnState returnState = match.returnStates.get(playerId);
            PlayerInventorySnapshot snapshot = match.inventorySnapshots.get(playerId);
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
            if (player == null) {
                continue;
            }

            player.removePotionEffect(MobEffects.SLOWNESS);
            player.removePotionEffect(MobEffects.JUMP_BOOST);
            player.removePotionEffect(MobEffects.GLOWING);

            if (winnerCelebration && playerId.equals(winnerId)) {
                beginVictorySequence(player, returnState, snapshot, match.dimensionId, match.slot);
                continue;
            }

            if (player.isDead) {
                pendingReturns.put(playerId, returnState);
                pendingInventoryReturns.put(playerId, snapshot);
            } else {
                restorePlayer(player, returnState);
                snapshot.restore(player);
            }
        }

        if (winnerId != null) {
            sendFfaChat(server, match, TextFormatting.GREEN + "Winner: " + TextFormatting.WHITE + (winner != null ? winner.getName() : "Unknown"));
        } else {
            sendFfaChat(server, match, TextFormatting.YELLOW + "FFA ended in a draw.");
        }

        if (!winnerCelebration) {
            teardownArenaSlot(server, match.slot);
        }
    }

    private void beginVictorySequence(EntityPlayerMP winner, ReturnState returnState, PlayerInventorySnapshot inventorySnapshot, int dimensionId, ArenaSlot slot) {
        pendingVictorySequences.put(winner.getUniqueID(), new VictorySequence(
                winner.getUniqueID(),
                winner.getName(),
                returnState,
                inventorySnapshot,
                dimensionId,
                slot,
                VICTORY_SEQUENCE_TICKS
        ));
        sendTitle(winner, TextFormatting.GOLD + "VICTORY!", TextFormatting.YELLOW + "You won the duel", 30);
        winner.addPotionEffect(new PotionEffect(MobEffects.GLOWING, VICTORY_SEQUENCE_TICKS + 40, 0, false, false));
        WorldServer world = winner.getServer().getWorld(dimensionId);
        if (world != null) {
            spawnVictoryFirework(world, winner.posX, winner.posY + 0.5D, winner.posZ);
            playMatchSound(world, slot, SoundEvents.ENTITY_FIREWORK_BLAST, SoundCategory.PLAYERS, 1.2F, 1.0F);
        }
    }

    private void spawnVictoryFirework(WorldServer world, double x, double y, double z) {
        ItemStack stack = new ItemStack(Items.FIREWORKS);
        NBTTagCompound fireworkData = new NBTTagCompound();
        fireworkData.setByte("Flight", (byte) 1);

        NBTTagList explosions = new NBTTagList();
        NBTTagCompound explosion = new NBTTagCompound();
        explosion.setBoolean("Flicker", true);
        explosion.setBoolean("Trail", true);
        explosion.setByte("Type", (byte) ThreadLocalRandom.current().nextInt(3));
        int[] palette = new int[]{0xFF5555, 0x55FFFF, 0x55FF55, 0xFFFF55, 0xFF55FF, 0xFFFFFF};
        int color = palette[ThreadLocalRandom.current().nextInt(palette.length)];
        explosion.setIntArray("Colors", new int[]{color});
        explosions.appendTag(explosion);

        fireworkData.setTag("Explosions", explosions);
        NBTTagCompound stackTag = new NBTTagCompound();
        stackTag.setTag("Fireworks", fireworkData);
        stack.setTagCompound(stackTag);

        EntityFireworkRocket firework = new EntityFireworkRocket(world, x, y, z, stack);
        world.spawnEntity(firework);
    }

    private void sendMatchChat(MinecraftServer server, ActiveMatch match, String message) {
        EntityPlayerMP first = server.getPlayerList().getPlayerByUUID(match.firstPlayer);
        EntityPlayerMP second = server.getPlayerList().getPlayerByUUID(match.secondPlayer);
        if (first != null) {
            send(first, message);
        }
        if (second != null) {
            send(second, message);
        }
        for (UUID spectatorId : match.spectators) {
            EntityPlayerMP spectator = server.getPlayerList().getPlayerByUUID(spectatorId);
            if (spectator != null) {
                send(spectator, message);
            }
        }
    }

    private void sendFfaChat(MinecraftServer server, FfaMatch match, String message) {
        for (UUID playerId : match.playerOrder) {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
            if (player != null) {
                send(player, message);
            }
        }
        for (UUID spectatorId : match.spectators) {
            EntityPlayerMP spectator = server.getPlayerList().getPlayerByUUID(spectatorId);
            if (spectator != null) {
                send(spectator, message);
            }
        }
    }

    private void sendSoloChat(MinecraftServer server, SoloMatch solo, String message) {
        EntityPlayerMP soloPlayer = server.getPlayerList().getPlayerByUUID(solo.playerId);
        if (soloPlayer != null) {
            send(soloPlayer, message);
        }
        for (UUID spectatorId : solo.spectators) {
            EntityPlayerMP spectator = server.getPlayerList().getPlayerByUUID(spectatorId);
            if (spectator != null) {
                send(spectator, message);
            }
        }
    }

    private void sendCountdownTitle(MinecraftServer server, ActiveMatch match, String title, String subtitle) {
        EntityPlayerMP first = server.getPlayerList().getPlayerByUUID(match.firstPlayer);
        EntityPlayerMP second = server.getPlayerList().getPlayerByUUID(match.secondPlayer);
        if (first != null) {
            sendTitle(first, title, subtitle, 12);
        }
        if (second != null) {
            sendTitle(second, title, subtitle, 12);
        }
    }

    private void sendFfaTitle(MinecraftServer server, FfaMatch match, String title, String subtitle) {
        for (UUID playerId : match.playerOrder) {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
            if (player != null) {
                sendTitle(player, title, subtitle, 12);
            }
        }
    }

    private void sendTitle(EntityPlayerMP player, String title, String subtitle, int stayTicks) {
        player.connection.sendPacket(new SPacketTitle(2, stayTicks, 3));
        player.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.TITLE, new TextComponentString(title)));
        if (subtitle != null && !subtitle.isEmpty()) {
            player.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.SUBTITLE, new TextComponentString(subtitle)));
        }
    }

    private void playMatchSound(WorldServer world, ArenaSlot slot, net.minecraft.util.SoundEvent sound, SoundCategory category, float volume, float pitch) {
        world.playSound(null, slot.centerX + 0.5D, 80.0D, slot.centerZ + 0.5D, sound, category, volume, pitch);
    }

    private void spawnChestHighlights(WorldServer world, List<BlockPos> chestPositions, List<UUID> highlightIds) {
        removeChestHighlights(world, highlightIds);
        for (BlockPos chestPos : chestPositions) {
            EntityShulker marker = new EntityShulker(world);
            marker.setPosition(chestPos.getX() + 0.5D, chestPos.getY(), chestPos.getZ() + 0.5D);
            marker.setNoAI(true);
            marker.setSilent(true);
            marker.setEntityInvulnerable(true);
            marker.setInvisible(true);
            marker.setGlowing(true);
            marker.setNoGravity(true);
            marker.addPotionEffect(new PotionEffect(MobEffects.INVISIBILITY, 20 * 60 * 5, 0, false, false));
            world.spawnEntity(marker);
            highlightIds.add(marker.getUniqueID());
            chestHighlightTargets.put(marker.getUniqueID(), new BlockPos(chestPos));
        }
    }

    private void removeChestHighlights(WorldServer world, List<UUID> highlightIds) {
        if (highlightIds.isEmpty()) {
            return;
        }
        Set<UUID> ids = new HashSet<UUID>(highlightIds);
        for (net.minecraft.entity.Entity entity : new ArrayList<net.minecraft.entity.Entity>(world.loadedEntityList)) {
            if (ids.contains(entity.getUniqueID())) {
                entity.setDead();
            }
        }
        for (UUID id : highlightIds) {
            chestHighlightTargets.remove(id);
        }
        highlightIds.clear();
    }

    private void spawnSpectatorBat(WorldServer world, EntityPlayerMP spectator, SpectatorSession session) {
        removeSpectatorBat(spectator.getServer(), session);
        EntityBat bat = new EntityBat(world);
        bat.setNoAI(false);
        bat.setNoGravity(true);
        bat.setIsBatHanging(false);
        bat.setSilent(true);
        bat.setEntityInvulnerable(true);
        double eyeY = spectator.posY + spectator.getEyeHeight();
        bat.setPositionAndRotation(spectator.posX, eyeY, spectator.posZ, spectator.rotationYaw, spectator.rotationPitch);
        bat.rotationYawHead = spectator.rotationYaw;
        bat.renderYawOffset = spectator.rotationYaw;
        bat.prevRotationYaw = spectator.rotationYaw;
        bat.prevRotationYawHead = spectator.rotationYaw;
        bat.prevRenderYawOffset = spectator.rotationYaw;
        bat.motionX = 0.0D;
        bat.motionY = 0.0D;
        bat.motionZ = 0.0D;
        world.spawnEntity(bat);
        session.proxyBatId = bat.getUniqueID();
        syncSpectatorBatVisibility(spectator.getServer(), spectator, session, bat);
    }

    @Nullable
    private EntityBat getSpectatorBat(WorldServer world, SpectatorSession session) {
        if (session == null || session.proxyBatId == null) {
            return null;
        }
        Entity entity = world.getEntityFromUuid(session.proxyBatId);
        return entity instanceof EntityBat ? (EntityBat) entity : null;
    }

    private void removeSpectatorBat(MinecraftServer server, @Nullable SpectatorSession session) {
        if (session == null || session.proxyBatId == null) {
            return;
        }
        for (WorldServer world : server.worlds) {
            if (world == null) {
                continue;
            }
            Entity entity = world.getEntityFromUuid(session.proxyBatId);
            if (entity != null) {
                entity.setDead();
                break;
            }
        }
        session.proxyBatId = null;
    }

    public synchronized boolean isSpectatorBat(Entity entity) {
        if (!(entity instanceof EntityBat)) {
            return false;
        }
        UUID entityId = entity.getUniqueID();
        for (SpectatorSession session : spectators.values()) {
            if (entityId.equals(session.proxyBatId)) {
                return true;
            }
        }
        return false;
    }

    private void returnSpectatorsForMatch(MinecraftServer server, ActiveMatch match) {
        for (UUID spectatorId : new ArrayList<UUID>(match.spectators)) {
            SpectatorSession session = spectators.remove(spectatorId);
            EntityPlayerMP spectator = server.getPlayerList().getPlayerByUUID(spectatorId);
            if (session == null || spectator == null) {
                continue;
            }
            removeSpectatorBat(server, session);
            restorePlayer(spectator, session.returnState);
            send(spectator, TextFormatting.YELLOW + "Match ended. You were returned.");
        }
        match.spectators.clear();
    }

    private void returnSpectatorsForFfa(MinecraftServer server, FfaMatch match) {
        for (UUID spectatorId : new ArrayList<UUID>(match.spectators)) {
            SpectatorSession session = spectators.remove(spectatorId);
            EntityPlayerMP spectator = server.getPlayerList().getPlayerByUUID(spectatorId);
            if (session == null || spectator == null) {
                continue;
            }
            removeSpectatorBat(server, session);
            restorePlayer(spectator, session.returnState);
            send(spectator, TextFormatting.YELLOW + "Match ended. You were returned.");
        }
        match.spectators.clear();
    }

    private void returnSpectatorsForSolo(MinecraftServer server, SoloMatch solo) {
        for (UUID spectatorId : new ArrayList<UUID>(solo.spectators)) {
            SpectatorSession session = spectators.remove(spectatorId);
            EntityPlayerMP spectator = server.getPlayerList().getPlayerByUUID(spectatorId);
            if (session == null || spectator == null) {
                continue;
            }
            removeSpectatorBat(server, session);
            restorePlayer(spectator, session.returnState);
            send(spectator, TextFormatting.YELLOW + "Match ended. You were returned.");
        }
        solo.spectators.clear();
    }

    public synchronized boolean handleChestHighlightInteract(EntityPlayerMP player, Entity target) {
        BlockPos chestPos = chestHighlightTargets.get(target.getUniqueID());
        if (chestPos == null) {
            return false;
        }
        if (player.dimension != target.dimension) {
            return false;
        }
        if (player.getDistanceSq(chestPos.getX() + 0.5D, chestPos.getY() + 0.5D, chestPos.getZ() + 0.5D) > 64.0D) {
            return false;
        }

        Block block = player.world.getBlockState(chestPos).getBlock();
        if (!(block instanceof BlockChest)) {
            return false;
        }
        ILockableContainer container = ((BlockChest) block).getLockableContainer(player.world, chestPos);
        if (container == null) {
            return false;
        }
        player.displayGUIChest(container);
        return true;
    }

    public synchronized void tickArenaSafety(MinecraftServer server) {
        for (ActiveMatch match : new HashSet<ActiveMatch>(playerToMatch.values())) {
            enforcePlayerInsideArena(server, match.firstPlayer, match.dimensionId, match.slot);
            enforcePlayerInsideArena(server, match.secondPlayer, match.dimensionId, match.slot);
        }
        for (FfaMatch match : new HashSet<FfaMatch>(playerToFfaMatch.values())) {
            for (UUID playerId : match.playerOrder) {
                enforcePlayerInsideArena(server, playerId, match.dimensionId, match.slot);
            }
        }

        for (SoloMatch solo : new ArrayList<SoloMatch>(soloMatches.values())) {
            enforcePlayerInsideArena(server, solo.playerId, solo.dimensionId, solo.slot);
        }
    }

    /**
     * If a player has escaped to a completely different dimension (e.g. via the
     * Heroes Expansion Tesseract), forfeit their match so it doesn't run forever.
     * This runs every tick, so escape is detected within ~1 server tick.
     */
    public synchronized void tickDimensionEscapeCheck(MinecraftServer server) {
        for (ActiveMatch match : new HashSet<ActiveMatch>(playerToMatch.values())) {
            checkDimensionEscape(server, match.firstPlayer, match.dimensionId, "1v1");
            checkDimensionEscape(server, match.secondPlayer, match.dimensionId, "1v1");
        }
        for (FfaMatch match : new HashSet<FfaMatch>(playerToFfaMatch.values())) {
            for (UUID playerId : new ArrayList<UUID>(match.playerOrder)) {
                checkDimensionEscape(server, playerId, match.dimensionId, "FFA");
            }
        }
        for (SoloMatch solo : new ArrayList<SoloMatch>(soloMatches.values())) {
            checkDimensionEscape(server, solo.playerId, solo.dimensionId, "Solo");
        }
    }

    private void checkDimensionEscape(MinecraftServer server, UUID playerId, int expectedDim, String matchType) {
        EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
        if (player == null) return;

        // If the player has arrived in the arena dimension, clear the pending flag.
        if (player.dimension == expectedDim) {
            pendingArenaArrivals.remove(playerId);
            return;
        }

        // Still waiting for initial teleport to complete — not an escape.
        if (pendingArenaArrivals.contains(playerId)) return;

        // Player is in a different dimension after having arrived — forfeit.
        pendingArenaArrivals.remove(playerId);
        send(player, TextFormatting.RED + "You left the arena dimension and forfeited the " + matchType + " match.");

        ActiveMatch activeMatch = playerToMatch.get(playerId);
        if (activeMatch != null) {
            UUID winnerId = activeMatch.getOpponent(playerId);
            EntityPlayerMP winner = server.getPlayerList().getPlayerByUUID(winnerId);
            if (winner != null) {
                send(winner, TextFormatting.GREEN + "Victory! " + TextFormatting.GRAY + "Your opponent escaped the arena.");
            }
            endMatch(server, activeMatch, playerId);
            return;
        }

        FfaMatch ffaMatch = playerToFfaMatch.get(playerId);
        if (ffaMatch != null) {
            eliminateFfaPlayer(server, ffaMatch, playerId, false,
                TextFormatting.YELLOW + player.getName() + " escaped the arena.");
            return;
        }

        SoloMatch soloMatch = soloMatches.get(playerId);
        if (soloMatch != null) {
            // End solo debug match
            removeChestHighlights(server.getWorld(soloMatch.dimensionId), soloMatch.chestHighlightIds);
            soloMatch.clearBossBar(server);
            returnSpectatorsForSolo(server, soloMatch);
            player.removePotionEffect(MobEffects.SLOWNESS);
            player.removePotionEffect(MobEffects.JUMP_BOOST);
            player.removePotionEffect(MobEffects.GLOWING);
            restorePlayer(player, soloMatch.returnState);
            soloMatch.inventory.restore(player);
            clearPersistedState(player);
            teardownArenaSlot(server, soloMatch.slot);
            awaitingKitSelection.remove(playerId);
            soloMatches.remove(playerId);
        }
    }

    private void enforcePlayerInsideArena(MinecraftServer server, UUID playerId, int dimensionId, ArenaSlot slot) {
        EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
        if (player == null || player.dimension != dimensionId) {
            return;
        }

        int minX = slot.minBlockX;
        int maxX = slot.maxBlockX;
        int minZ = slot.minBlockZ;
        int maxZ = slot.maxBlockZ;

        boolean out = player.posX < minX || player.posX > maxX || player.posZ < minZ || player.posZ > maxZ || player.posY < 10;
        if (!out) {
            return;
        }

        int clampedX = (int) Math.max(minX + 2, Math.min(maxX - 2, player.posX));
        int clampedZ = (int) Math.max(minZ + 2, Math.min(maxZ - 2, player.posZ));

        WorldServer world = server.getWorld(dimensionId);
        if (world == null) {
            return;
        }

        BlockPos safe = findNaturalSpawn(world, slot, clampedX, clampedZ);
        if (safe == null) {
            return;
        }
        player.connection.setPlayerLocation(safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D, player.rotationYaw, 0.0F);
        player.fallDistance = 0.0F;
    }

    private void purgeArenaSlot(WorldServer world, ArenaSlot slot) {
        unloadArenaSlotChunks(world, slot);
        for (Entity entity : new ArrayList<Entity>(world.loadedEntityList)) {
            if (entity instanceof EntityPlayerMP) {
                continue;
            }
            if (entity.posX >= slot.minBlockX - 2 && entity.posX <= slot.maxBlockX + 2
                    && entity.posZ >= slot.minBlockZ - 2 && entity.posZ <= slot.maxBlockZ + 2) {
                entity.setDead();
            }
        }
    }

    private void unloadArenaSlotChunks(WorldServer world, ArenaSlot slot) {
        for (int chunkX = slot.minChunkX - 1; chunkX <= slot.maxChunkX + 1; chunkX++) {
            for (int chunkZ = slot.minChunkZ - 1; chunkZ <= slot.maxChunkZ + 1; chunkZ++) {
                net.minecraft.world.chunk.Chunk chunk = world.getChunkProvider().getLoadedChunk(chunkX, chunkZ);
                if (chunk != null) {
                    world.getChunkProvider().queueUnload(chunk);
                }
            }
        }
    }

    private boolean areArenaSlotChunksLoaded(WorldServer world, ArenaSlot slot) {
        for (int chunkX = slot.minChunkX - 1; chunkX <= slot.maxChunkX + 1; chunkX++) {
            for (int chunkZ = slot.minChunkZ - 1; chunkZ <= slot.maxChunkZ + 1; chunkZ++) {
                if (world.getChunkProvider().getLoadedChunk(chunkX, chunkZ) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private void deleteArenaSlotRegionFile(ArenaSlot slot) {
        File saveRoot = DimensionManager.getCurrentSaveRootDirectory();
        if (saveRoot == null) {
            return;
        }

        File regionFile = new File(new File(new File(saveRoot, "DIM" + ARENA_DIMENSION_ID), "region"), "r." + slot.slotX + "." + slot.slotZ + ".mca");
        if (regionFile.exists()) {
            regionFile.delete();
        }
    }

    private int getQueuePosition(UUID playerId) {
        int index = 1;
        for (UUID id : queue) {
            if (id.equals(playerId)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    private boolean isArenaSlotInUse(ArenaSlot slot) {
        for (ActiveMatch match : playerToMatch.values()) {
            if (arenaSlotKey(match.slot) == arenaSlotKey(slot)) {
                return true;
            }
        }
        for (FfaMatch match : playerToFfaMatch.values()) {
            if (arenaSlotKey(match.slot) == arenaSlotKey(slot)) {
                return true;
            }
        }
        for (SoloMatch match : soloMatches.values()) {
            if (arenaSlotKey(match.slot) == arenaSlotKey(slot)) {
                return true;
            }
        }
        return false;
    }

    private boolean isArenaDimension(int dimensionId) {
        return dimensionId == ARENA_DIMENSION_ID;
    }

    /** Returns true if the player has been sent to an arena but has not yet arrived there. */
    public synchronized boolean isPendingArenaArrival(UUID playerId) {
        return pendingArenaArrivals.contains(playerId);
    }

    /** Returns true if the player is currently queued for or in any PvP session. */
    public synchronized boolean isPlayerInPvpSession(UUID playerId) {
        return playerToMatch.containsKey(playerId)
                || playerToFfaMatch.containsKey(playerId)
                || soloMatches.containsKey(playerId)
                || queuedPlayers.contains(playerId)
                || ffaQueuedPlayers.contains(playerId)
                || spectators.containsKey(playerId);
    }

    private void send(EntityPlayerMP player, String message) {
        player.sendMessage(new TextComponentString(CHAT_PREFIX + message));
    }

    private void sendQueuedFfaPlayers(MinecraftServer server, String message) {
        for (UUID queuedId : new ArrayList<UUID>(ffaQueue)) {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(queuedId);
            if (player != null) {
                send(player, message);
            }
        }
    }

    public synchronized int getRoundDurationSeconds(MinecraftServer server) {
        return PvpSettingsSavedData.get(server).getRoundDurationSeconds();
    }

    public synchronized void setRoundDurationSeconds(MinecraftServer server, int seconds) {
        PvpSettingsSavedData.get(server).setRoundDurationSeconds(seconds);
    }

    private int getRoundDurationTicks(MinecraftServer server) {
        return getRoundDurationSeconds(server) * 20;
    }

    private int nextMatchId() {
        return matchCounter.incrementAndGet();
    }

    private static class ArenaAllocation {
        private final int dimensionId;
        private final WorldServer world;
        private final ArenaSlot slot;

        private ArenaAllocation(int dimensionId, WorldServer world, ArenaSlot slot) {
            this.dimensionId = dimensionId;
            this.world = world;
            this.slot = slot;
        }
    }

    private static class ArenaPreparedArena {
        private final int dimensionId;
        private final WorldServer world;
        private final ArenaSlot slot;
        private final BlockPos firstSpawn;
        private final BlockPos secondSpawn;

        private ArenaPreparedArena(int dimensionId, WorldServer world, ArenaSlot slot, BlockPos firstSpawn, BlockPos secondSpawn) {
            this.dimensionId = dimensionId;
            this.world = world;
            this.slot = slot;
            this.firstSpawn = firstSpawn;
            this.secondSpawn = secondSpawn;
        }
    }

    private static class ArenaCleanupState {
        private final ArenaSlot slot;
        private int ticksSinceQueued = 0;

        private ArenaCleanupState(ArenaSlot slot) {
            this.slot = slot;
        }
    }

    private static class ArenaSlot {
        private final int slotX;
        private final int slotZ;
        private final int minChunkX;
        private final int maxChunkX;
        private final int minChunkZ;
        private final int maxChunkZ;
        private final int minBlockX;
        private final int maxBlockX;
        private final int minBlockZ;
        private final int maxBlockZ;
        private final int centerX;
        private final int centerZ;

        private ArenaSlot(int slotX, int slotZ) {
            this.slotX = slotX;
            this.slotZ = slotZ;
            int baseChunkX = slotX * ARENA_SLOT_CHUNKS;
            int baseChunkZ = slotZ * ARENA_SLOT_CHUNKS;
            this.minChunkX = baseChunkX + ARENA_MIN_CHUNK;
            this.maxChunkX = baseChunkX + ARENA_MAX_CHUNK;
            this.minChunkZ = baseChunkZ + ARENA_MIN_CHUNK;
            this.maxChunkZ = baseChunkZ + ARENA_MAX_CHUNK;
            this.minBlockX = slotX * ARENA_SLOT_BLOCKS + ARENA_MIN_BLOCK;
            this.maxBlockX = slotX * ARENA_SLOT_BLOCKS + ARENA_MAX_BLOCK;
            this.minBlockZ = slotZ * ARENA_SLOT_BLOCKS + ARENA_MIN_BLOCK;
            this.maxBlockZ = slotZ * ARENA_SLOT_BLOCKS + ARENA_MAX_BLOCK;
            this.centerX = (minBlockX + maxBlockX) / 2;
            this.centerZ = (minBlockZ + maxBlockZ) / 2;
        }

        private int toWorldX(int localX) {
            return slotX * ARENA_SLOT_BLOCKS + localX;
        }

        private int toWorldZ(int localZ) {
            return slotZ * ARENA_SLOT_BLOCKS + localZ;
        }
    }

    private static class SpectatorSession {
        private final ReturnState returnState;
        private final SpectateTarget target;
        private final int targetId;
        private UUID proxyBatId;

        private SpectatorSession(ReturnState returnState, SpectateTarget target, int targetId) {
            this.returnState = returnState;
            this.target = target;
            this.targetId = targetId;
        }
    }

    private enum SpectateTarget {
        ACTIVE_MATCH,
        FFA_MATCH,
        DEBUG_SOLO
    }

    private static class VictorySequence {
        private final UUID playerId;
        private final String winnerName;
        private final ReturnState returnState;
        private final PlayerInventorySnapshot inventorySnapshot;
        private final int dimensionId;
        private final ArenaSlot slot;
        private int ticksRemaining;

        private VictorySequence(UUID playerId, String winnerName, ReturnState returnState, PlayerInventorySnapshot inventorySnapshot, int dimensionId, ArenaSlot slot, int ticksRemaining) {
            this.playerId = playerId;
            this.winnerName = winnerName;
            this.returnState = returnState;
            this.inventorySnapshot = inventorySnapshot;
            this.dimensionId = dimensionId;
            this.slot = slot;
            this.ticksRemaining = ticksRemaining;
        }
    }

    private enum MatchPhase {
        PREPARE,
        START_COUNTDOWN,
        ACTIVE
    }

    private static class ActiveMatch {
        private final int matchId;
        private final UUID firstPlayer;
        private final UUID secondPlayer;
        private final ReturnState firstReturn;
        private final ReturnState secondReturn;
        private final PlayerInventorySnapshot firstInventory;
        private final PlayerInventorySnapshot secondInventory;
        private final int dimensionId;
        private final ArenaSlot slot;
        private final BlockPos firstSpawn;
        private final BlockPos secondSpawn;
        private final List<BlockPos> chestPositions;
        private final List<UUID> chestHighlightIds = new ArrayList<UUID>();
        private final Set<UUID> spectators = new HashSet<UUID>();
        private MatchPhase phase = MatchPhase.PREPARE;
        private int phaseTicksRemaining = PREP_FREEZE_TICKS;
        private int roundTicks = 20 * 300;
        private boolean chestsRevealed = false;
        private boolean playersRevealed = false;
        private BossInfoServer bossBar;

        private ActiveMatch(int matchId, UUID firstPlayer, UUID secondPlayer, ReturnState firstReturn, ReturnState secondReturn, PlayerInventorySnapshot firstInventory, PlayerInventorySnapshot secondInventory, int dimensionId, ArenaSlot slot, BlockPos firstSpawn, BlockPos secondSpawn, List<BlockPos> chestPositions) {
            this.matchId = matchId;
            this.firstPlayer = firstPlayer;
            this.secondPlayer = secondPlayer;
            this.firstReturn = firstReturn;
            this.secondReturn = secondReturn;
            this.firstInventory = firstInventory;
            this.secondInventory = secondInventory;
            this.dimensionId = dimensionId;
            this.slot = slot;
            this.firstSpawn = new BlockPos(firstSpawn);
            this.secondSpawn = new BlockPos(secondSpawn);
            this.chestPositions = new ArrayList<BlockPos>(chestPositions);
        }

        private UUID getOpponent(UUID playerId) {
            return firstPlayer.equals(playerId) ? secondPlayer : firstPlayer;
        }

        private ReturnState getReturnState(UUID playerId) {
            return firstPlayer.equals(playerId) ? firstReturn : secondReturn;
        }

        private PlayerInventorySnapshot getInventorySnapshot(UUID playerId) {
            return firstPlayer.equals(playerId) ? firstInventory : secondInventory;
        }

        private void addBossBarPlayers(MinecraftServer server) {
            if (bossBar == null) {
                return;
            }
            EntityPlayerMP first = server.getPlayerList().getPlayerByUUID(firstPlayer);
            EntityPlayerMP second = server.getPlayerList().getPlayerByUUID(secondPlayer);
            if (first != null) {
                bossBar.addPlayer(first);
            }
            if (second != null) {
                bossBar.addPlayer(second);
            }
        }

        private void clearBossBar(MinecraftServer server) {
            if (bossBar == null) {
                return;
            }
            for (EntityPlayerMP player : new ArrayList<EntityPlayerMP>(bossBar.getPlayers())) {
                bossBar.removePlayer(player);
            }
            bossBar.setVisible(false);
            bossBar = null;
        }
    }

    private static class FfaMatch {
        private final int matchId;
        private final int dimensionId;
        private final ArenaSlot slot;
        private final List<UUID> playerOrder = new ArrayList<UUID>();
        private final Set<UUID> alivePlayers = new HashSet<UUID>();
        private final Map<UUID, ReturnState> returnStates = new HashMap<UUID, ReturnState>();
        private final Map<UUID, PlayerInventorySnapshot> inventorySnapshots = new HashMap<UUID, PlayerInventorySnapshot>();
        private final Map<UUID, BlockPos> spawns = new HashMap<UUID, BlockPos>();
        private final List<BlockPos> chestPositions;
        private final List<UUID> chestHighlightIds = new ArrayList<UUID>();
        private final Set<UUID> spectators = new HashSet<UUID>();
        private MatchPhase phase = MatchPhase.PREPARE;
        private int phaseTicksRemaining = PREP_FREEZE_TICKS;
        private int roundTicks = 20 * 300;
        private boolean chestsRevealed = false;
        private boolean playersRevealed = false;
        private BossInfoServer bossBar;

        private FfaMatch(int matchId, int dimensionId, ArenaSlot slot, List<BlockPos> chestPositions) {
            this.matchId = matchId;
            this.dimensionId = dimensionId;
            this.slot = slot;
            this.chestPositions = new ArrayList<BlockPos>(chestPositions);
        }
    }

    private static class SoloMatch {
        private final int matchId;
        private final UUID playerId;
        private final ReturnState returnState;
        private final PlayerInventorySnapshot inventory;
        private final int dimensionId;
        private final ArenaSlot slot;
        private final BlockPos spawn;
        private final List<BlockPos> chestPositions;
        private final List<UUID> chestHighlightIds = new ArrayList<UUID>();
        private final Set<UUID> spectators = new HashSet<UUID>();
        private MatchPhase phase = MatchPhase.PREPARE;
        private int phaseTicksRemaining = PREP_FREEZE_TICKS;
        private int roundTicks = 20 * 300;
        private boolean chestsRevealed = false;
        private boolean playersRevealed = false;
        private BossInfoServer bossBar;

        private SoloMatch(int matchId, UUID playerId, ReturnState returnState, PlayerInventorySnapshot inventory, int dimensionId, ArenaSlot slot, BlockPos spawn, List<BlockPos> chestPositions) {
            this.matchId = matchId;
            this.playerId = playerId;
            this.returnState = returnState;
            this.inventory = inventory;
            this.dimensionId = dimensionId;
            this.slot = slot;
            this.spawn = new BlockPos(spawn);
            this.chestPositions = new ArrayList<BlockPos>(chestPositions);
        }

        private void addBossBarPlayer(MinecraftServer server) {
            if (bossBar == null) {
                return;
            }
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
            if (player != null) {
                bossBar.addPlayer(player);
            }
        }

        private void clearBossBar(MinecraftServer server) {
            if (bossBar == null) {
                return;
            }
            for (EntityPlayerMP player : new ArrayList<EntityPlayerMP>(bossBar.getPlayers())) {
                bossBar.removePlayer(player);
            }
            bossBar.setVisible(false);
            bossBar = null;
        }
    }

    private static class ReturnState {
        private final int dimension;
        private final BlockPos pos;
        private final float yaw;
        private final float pitch;
        private final GameType gameType;

        private ReturnState(int dimension, BlockPos pos, float yaw, float pitch, GameType gameType) {
            this.dimension = dimension;
            this.pos = pos;
            this.yaw = yaw;
            this.pitch = pitch;
            this.gameType = gameType == null ? GameType.SURVIVAL : gameType;
        }

        private static ReturnState capture(EntityPlayerMP player) {
            return new ReturnState(player.dimension, player.getPosition(), player.rotationYaw, player.rotationPitch, player.interactionManager.getGameType());
        }

        private static ReturnState overworldSpawn(MinecraftServer server) {
            WorldServer overworld = server.getWorld(0);
            if (overworld == null) {
                return new ReturnState(0, BlockPos.ORIGIN, 0.0F, 0.0F, GameType.SURVIVAL);
            }
            BlockPos spawn = overworld.getSpawnPoint();
            return new ReturnState(0, spawn, 0.0F, 0.0F, GameType.SURVIVAL);
        }
    }

    private static class PlayerInventorySnapshot {
        private final NonNullList<ItemStack> main;
        private final NonNullList<ItemStack> armor;
        private final NonNullList<ItemStack> offhand;

        private PlayerInventorySnapshot(NonNullList<ItemStack> main, NonNullList<ItemStack> armor, NonNullList<ItemStack> offhand) {
            this.main = main;
            this.armor = armor;
            this.offhand = offhand;
        }

        private static PlayerInventorySnapshot capture(EntityPlayerMP player) {
            NonNullList<ItemStack> main = NonNullList.withSize(player.inventory.mainInventory.size(), ItemStack.EMPTY);
            NonNullList<ItemStack> armor = NonNullList.withSize(player.inventory.armorInventory.size(), ItemStack.EMPTY);
            NonNullList<ItemStack> offhand = NonNullList.withSize(player.inventory.offHandInventory.size(), ItemStack.EMPTY);

            for (int i = 0; i < main.size(); i++) {
                main.set(i, player.inventory.mainInventory.get(i).copy());
            }
            for (int i = 0; i < armor.size(); i++) {
                armor.set(i, player.inventory.armorInventory.get(i).copy());
            }
            for (int i = 0; i < offhand.size(); i++) {
                offhand.set(i, player.inventory.offHandInventory.get(i).copy());
            }
            return new PlayerInventorySnapshot(main, armor, offhand);
        }

        private void restore(EntityPlayerMP player) {
            HeroSMP.KIT_MANAGER.clearPlayerInventory(player);

            for (int i = 0; i < player.inventory.mainInventory.size() && i < main.size(); i++) {
                player.inventory.mainInventory.set(i, main.get(i).copy());
            }
            for (int i = 0; i < player.inventory.armorInventory.size() && i < armor.size(); i++) {
                player.inventory.armorInventory.set(i, armor.get(i).copy());
            }
            for (int i = 0; i < player.inventory.offHandInventory.size() && i < offhand.size(); i++) {
                player.inventory.offHandInventory.set(i, offhand.get(i).copy());
            }

            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
        }
    }
}
