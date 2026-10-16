package com.matoon.herosmp.client.store;

import com.matoon.herosmp.HeroSMP;
import com.matoon.herosmp.client.gui.GuiPackStore;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI;
import com.matoon.herosmp.network.ModNetwork;
import com.matoon.herosmp.network.PacketConfigureMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Makes "Edit Map" work from anywhere, including the main menu.
 *
 * Configure-map mode is a server-side session, so a world must be running. From the main menu we
 * load (creating on first use) a dedicated creative "Beyonder Map Editor" world with cheats on,
 * wait for the player to arrive, then request the configure session. When the session ends
 * ({@code /return} saves and returns the player to the overworld) we quit back to the store. From
 * inside an existing world the request is sent directly and the player stays in that world after.
 */
@SideOnly(Side.CLIENT)
public final class MapEditorLauncher {

    public static final String EDITOR_FOLDER = "beyonder_map_editor";
    public static final String EDITOR_NAME = "Beyonder Map Editor";

    private enum Stage { IDLE, WAIT_JOIN, WAIT_ENTER, EDITING, RETURNING }

    private static Stage stage = Stage.IDLE;
    private static String mapName;
    private static boolean returnToStore;
    private static int ticks;

    /** Opens {@code map} in configure mode, loading the editor world first if needed. */
    public static void editMap(String map) {
        Minecraft mc = Minecraft.getMinecraft();
        mapName = map;
        ticks = 0;

        if (mc.world != null && mc.player != null) {
            returnToStore = isEditorWorld(mc);
            ModNetwork.CHANNEL.sendToServer(new PacketConfigureMap(map));
            stage = Stage.WAIT_ENTER;
            mc.displayGuiScreen(null);
            return;
        }

        returnToStore = true;
        stage = Stage.WAIT_JOIN;
        WorldSettings settings = new WorldSettings(0L, GameType.CREATIVE, false, false, WorldType.FLAT);
        settings.enableCommands();
        mc.launchIntegratedServer(EDITOR_FOLDER, EDITOR_NAME, settings);
    }

    public static boolean isEditing() {
        return stage == Stage.EDITING;
    }

    /** True while {@code map} is open (or opening) in in-world configure mode. */
    public static boolean isEditingMap(String map) {
        return stage != Stage.IDLE && map != null && map.equalsIgnoreCase(mapName);
    }

    private static boolean isEditorWorld(Minecraft mc) {
        IntegratedServer server = mc.getIntegratedServer();
        return server != null && EDITOR_FOLDER.equals(server.getFolderName());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.IDLE) return;
        Minecraft mc = Minecraft.getMinecraft();
        ticks++;

        switch (stage) {
            case WAIT_JOIN:
                // Wait for the world to finish loading and the loading screen to close.
                if (mc.player != null && mc.world != null && mc.currentScreen == null) {
                    if (ticks > 0 && mc.player.ticksExisted > 20) {
                        ModNetwork.CHANNEL.sendToServer(new PacketConfigureMap(mapName));
                        stage = Stage.WAIT_ENTER;
                        ticks = 0;
                    }
                } else if (ticks > 20 * 120) {
                    stage = Stage.IDLE; // world never loaded
                }
                break;

            case WAIT_ENTER:
                if (mc.player == null || mc.world == null) { stage = Stage.IDLE; break; }
                if (mc.player.dimension != 0) {
                    stage = Stage.EDITING;
                    ticks = 0;
                } else if (ticks > 20 * 15) {
                    stage = Stage.IDLE; // server refused (it messages the player why)
                }
                break;

            case EDITING:
                if (mc.player == null || mc.world == null) { stage = Stage.IDLE; break; }
                if (mc.player.dimension == 0) {
                    // Session ended: the map was saved and the player restored to the overworld.
                    if (returnToStore && isEditorWorld(mc)) {
                        stage = Stage.RETURNING;
                        ticks = 0;
                    } else {
                        stage = Stage.IDLE;
                    }
                }
                break;

            case RETURNING:
                if (mc.player == null || mc.world == null) { stage = Stage.IDLE; break; }
                // The tick that ends a configure session is very heavy (copying the map back to
                // disk, then unloading/deleting the configure dimension), and the client sees the
                // player land in the overworld partway through it. Wait until the server has
                // finished the session, plus a short grace period, before shutting the world down.
                IntegratedServer server = mc.getIntegratedServer();
                boolean sessionDone = server == null
                        || !HeroSMP.HUNGER_GAMES_MANAGER.isInConfigureMode(mc.player.getUniqueID());
                if (sessionDone && ticks >= 20) {
                    stage = Stage.IDLE;
                    quitToStore(mc, mapName);
                } else if (ticks > 20 * 30) {
                    stage = Stage.IDLE; // something went wrong; leave the player in the world
                }
                break;

            default:
                break;
        }
    }

    /**
     * Leaves the editor world and opens the store.
     *
     * This deliberately does NOT send the "quitting" disconnect packet first (as the pause menu
     * does). {@code loadWorld(null)} queues a task on the integrated server and blocks until the
     * server runs it; if a disconnect is already pending, the server can process it, stop its loop
     * in that same tick and never run the task, leaving the client frozen forever. With no
     * disconnect pending, the server keeps ticking, runs the task, then shuts down normally
     * (saving the player and worlds and closing the connection itself), and loadWorld returns once
     * it has stopped.
     */
    private static void quitToStore(Minecraft mc, String map) {
        mc.loadWorld((WorldClient) null);
        cleanupEditorWorlds();
        GuiPackStore store = GuiPackStore.installedTab(new GuiMainMenu());
        store.toast("Saved changes to " + map);
        mc.displayGuiScreen(store);
    }

    // ---------------------------------------------------------------------
    // Throwaway editor world cleanup
    // ---------------------------------------------------------------------

    /** Current editor folder plus the one used before the mod was renamed to Beyonder. */
    private static final String[] EDITOR_FOLDERS = {EDITOR_FOLDER, "herosmp_map_editor"};

    /**
     * Deletes the editor world(s) so they never linger in Singleplayer. The editor world holds
     * nothing worth keeping (map edits are saved into herosmp_hg_maps), and it's recreated on
     * demand. Skipped while any integrated server is running.
     */
    public static void cleanupEditorWorlds() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getIntegratedServer() != null || stage != Stage.IDLE) return;
        for (String folder : EDITOR_FOLDERS) {
            if (!new java.io.File(new java.io.File(mc.gameDir, "saves"), folder).isDirectory()) continue;
            try {
                mc.getSaveLoader().flushCache();
                mc.getSaveLoader().deleteWorldDirectory(folder);
            } catch (Exception e) {
                System.err.println("[Beyonder] Couldn't remove editor world '" + folder + "': " + e.getMessage());
            }
        }
    }

    /** Also covers leaving with Save and Quit, crashes, and leftovers from older versions. */
    @SubscribeEvent
    public void onGuiOpen(net.minecraftforge.client.event.GuiOpenEvent event) {
        if (event.getGui() instanceof GuiMainMenu
                || event.getGui() instanceof net.minecraft.client.gui.GuiWorldSelection) {
            cleanupEditorWorlds();
        }
    }

    /** Editing banner, shown at the top of the screen while in a configure session. */
    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Text event) {
        if (stage != Stage.EDITING) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null) return;

        ScaledResolution sr = new ScaledResolution(mc);
        String left = "EDITING";
        String name = mapName;
        String hint = "Type /return to save & exit";
        int pad = 6;
        int chipW = BedrockUI.chipWidth(mc.fontRenderer, left);
        int w = chipW + pad + mc.fontRenderer.getStringWidth(name) + 12 + mc.fontRenderer.getStringWidth(hint) + pad * 2;
        int h = 20;
        int x = (sr.getScaledWidth() - w) / 2;
        int y = 4;

        BedrockUI.rect(x, y, w, h, BedrockUI.OUTLINE);
        BedrockUI.rect(x + 1, y + 1, w - 2, h - 2, 0xE0313233);
        int cx = x + pad;
        BedrockUI.chip(mc.fontRenderer, cx, y + 4, left, BedrockUI.GOLD, BedrockUI.TEXT_DARK);
        cx += chipW + pad;
        mc.fontRenderer.drawString(name, cx, y + 6, BedrockUI.TEXT, true);
        cx += mc.fontRenderer.getStringWidth(name) + 12;
        mc.fontRenderer.drawString(hint, cx, y + 6, BedrockUI.TEXT_DIM, false);
    }
}
