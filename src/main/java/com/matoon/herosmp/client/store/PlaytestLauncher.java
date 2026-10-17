package com.matoon.herosmp.client.store;

import com.matoon.herosmp.HeroSMP;
import com.matoon.herosmp.client.gui.GuiPackStore;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI;
import com.matoon.herosmp.npc.playtest.KitPlaytest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreenWorking;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Random;
import java.util.UUID;

/**
 * Playtests from the Content Packs store, in a fresh throwaway "Beyonder Playtest" world:
 * <ul>
 *   <li>Map: starts a solo debug Hunger Games match on that map.</li>
 *   <li>Kit: builds a small arena with damage-reporting test dummies and a button that starts a
 *       solo debug PVP match ({@link KitPlaytest}).</li>
 * </ul>
 * Leaving (Save and Quit) returns to the store; the world is deleted like the map editor world.
 */
@SideOnly(Side.CLIENT)
public final class PlaytestLauncher {

    public static final String FOLDER = "beyonder_playtest";
    public static final String NAME = "Beyonder Playtest";

    private enum Stage { IDLE, STOPPING, WAIT_JOIN, ACTIVE }

    private static Stage stage = Stage.IDLE;
    private static String label;
    private static String mapName;
    private static NBTTagCompound kitTag;
    private static int ticks;
    private static boolean returnToStore;
    /** Server of the world being left, until its thread has stopped. */
    private static IntegratedServer stopping;

    public static boolean isBusy() {
        return stage != Stage.IDLE;
    }

    /** True while a playtest world is closing or loading; further launches are ignored. */
    public static boolean isStarting() {
        return stage == Stage.STOPPING || stage == Stage.WAIT_JOIN;
    }

    /** True while the player is inside a running playtest. */
    public static boolean isInPlaytest() {
        return stage == Stage.ACTIVE;
    }

    public static void playtestMap(String map) {
        if (isStarting()) return;
        mapName = map;
        kitTag = null;
        label = map;
        start();
    }

    public static void playtestKit(NBTTagCompound tag, String name) {
        if (isStarting()) return;
        kitTag = tag.copy();
        mapName = null;
        label = name;
        start();
    }

    /**
     * Leaves whatever world is open, waits for its integrated server to finish saving and stop,
     * deletes the old playtest world, and only then launches a fresh one (see onClientTick).
     * Relaunching straight away would reopen the same folder while the old server still holds it:
     * the previous arena, dummies and match came back, and the old server's final save collided
     * with the new session lock.
     */
    private static void start() {
        Minecraft mc = Minecraft.getMinecraft();
        returnToStore = false;
        ticks = 0;
        if (mc.world != null) {
            IntegratedServer server = mc.getIntegratedServer();
            // Multiplayer needs the disconnect packet; for singleplayer it races the server
            // shutdown and can deadlock, so only loadWorld(null) there.
            if (server == null) mc.world.sendQuittingDisconnectingPacket();
            mc.loadWorld(null);
            stopping = server;
        } else {
            stopping = mc.getIntegratedServer();
        }
        GuiScreenWorking working = new GuiScreenWorking();
        mc.displayGuiScreen(working);
        working.resetProgressAndMessage("Starting playtest");
        working.displayLoadingString(label == null ? "" : label);
        stage = Stage.STOPPING;
    }

    private static void launch() {
        Minecraft mc = Minecraft.getMinecraft();
        stopping = null;
        try {
            mc.getSaveLoader().flushCache();
            mc.getSaveLoader().deleteWorldDirectory(FOLDER); // always start from a fresh world
        } catch (Exception e) {
            System.err.println("[Beyonder] Couldn't remove the old playtest world: " + e.getMessage());
        }
        stage = Stage.WAIT_JOIN;
        ticks = 0;
        WorldSettings settings = new WorldSettings(new Random().nextLong(), GameType.SURVIVAL, false, false, WorldType.FLAT);
        settings.enableCommands();
        mc.launchIntegratedServer(FOLDER, NAME, settings);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.IDLE) return;
        Minecraft mc = Minecraft.getMinecraft();
        ticks++;
        if (stage == Stage.STOPPING) {
            // Wait for the previous server thread to save and exit (capped at 30s).
            if (stopping == null || stopping.isServerStopped() || ticks > 20 * 30) launch();
            return;
        }
        if (stage == Stage.WAIT_JOIN) {
            IntegratedServer server = mc.getIntegratedServer();
            if (mc.player != null && mc.world != null && mc.currentScreen == null && server != null
                    && FOLDER.equals(server.getFolderName()) && mc.player.ticksExisted > 20) {
                final UUID id = mc.player.getUniqueID();
                final String map = mapName;
                final NBTTagCompound kit = kitTag;
                server.addScheduledTask(() -> {
                    EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
                    if (p == null) return;
                    if (map != null) HeroSMP.HUNGER_GAMES_MANAGER.startSoloMatch(server, p, map);
                    else KitPlaytest.setup(server, p, kit);
                });
                stage = Stage.ACTIVE;
                returnToStore = true;
            } else if (ticks > 20 * 120) {
                stage = Stage.IDLE; // the world never loaded
            }
        } else if (stage == Stage.ACTIVE && mc.world == null) {
            stage = Stage.IDLE; // left the playtest world
        }
    }

    /** Back on the main menu after a playtest: clean up and show the store again. */
    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (!(event.getGui() instanceof GuiMainMenu) || !returnToStore || stage != Stage.IDLE) return;
        returnToStore = false;
        MapEditorLauncher.cleanupEditorWorlds();
        event.setGui(GuiPackStore.installedTab(new GuiMainMenu()));
    }

    /** "PLAYTEST" tag in the top-left corner. */
    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Text event) {
        if (stage != Stage.ACTIVE) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || label == null) return;
        // Small tag in the top-left corner, clear of the boss bars / round HUD at the top centre.
        String chip = "PLAYTEST";
        String hint = "Esc > Save and Quit to leave";
        int chipW = BedrockUI.chipWidth(mc.fontRenderer, chip);
        int x = 4, y = 4;
        BedrockUI.chip(mc.fontRenderer, x, y, chip, BedrockUI.GREEN, BedrockUI.TEXT);
        mc.fontRenderer.drawString(label, x + chipW + 5, y + 2, BedrockUI.TEXT, true);
        mc.fontRenderer.drawString(hint, x, y + 14, 0xA0B1B2B5, true);
    }
}
