package com.matoon.herosmp.client;

import anvil.infinity.abilities.AbilitySnap;
import com.matoon.herosmp.network.PacketInfPowerUp;
import com.matoon.herosmp.registry.ModSounds;
import lucraft.mods.lucraftcore.superpowers.render.RenderSuperpowerLayerEvent;
import lucraft.mods.lucraftcore.superpowers.render.SuperpowerRenderer;
import lucraft.mods.lucraftcore.util.abilitybar.AbilityBarHandler;
import lucraft.mods.lucraftcore.util.abilitybar.IAbilityBarEntry;
import lucraft.mods.lucraftcore.util.helper.LCRenderHelper;
import lucraft.mods.lucraftcore.util.helper.PlayerHelper;
import lucraft.mods.lucraftcore.util.render.ModelCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side overlay that plays the Infinity Gauntlet power-up sound and
 * cycles between two full-screen images while the sound is active.
 *
 * Lifecycle:
 *  - start(uuid) → begins sound + overlay for the given holder UUID.
 *                  If the holder is the local player: full HUD + progress bar + skin overlay.
 *                  If the holder is someone else: sound + skin overlay on their model only.
 *  - stop(uuid)  → cuts sound + removes state for that holder.
 *
 * Multiple holders can be active simultaneously (e.g. two players with full gauntlets).
 */
@SideOnly(Side.CLIENT)
public class InfPowerUpOverlay {

    private static final ResourceLocation FRAME_1 =
        new ResourceLocation("herosmp", "textures/gui/inf_powering_up_1.png");
    private static final ResourceLocation FRAME_2 =
        new ResourceLocation("herosmp", "textures/gui/inf_powering_up_2.png");

    /** Skin overlay textures cycled on the player model while the power-up is active. */
    private static final ResourceLocation SKIN_OVERLAY_1 =
        new ResourceLocation("herosmp", "textures/abilities/inf_overlay_1.png");
    private static final ResourceLocation SKIN_OVERLAY_2 =
        new ResourceLocation("herosmp", "textures/abilities/inf_overlay_2.png");

    /** How many client ticks each frame is shown before switching. */
    private static final int CYCLE_TICKS = 4;

    /** Total sound duration in ticks — matches PacketInfPowerUp.POWER_UP_DURATION_TICKS. */
    private static final int SOUND_DURATION_TICKS = PacketInfPowerUp.POWER_UP_DURATION_TICKS;

    /**
     * Per-holder state. Key = holder UUID.
     * Value = elapsed ticks for that holder's power-up sequence.
     * ConcurrentHashMap because packet callbacks and render thread may access concurrently.
     */
    private static final Map<UUID, Integer> ACTIVE_HOLDERS = new ConcurrentHashMap<>();

    /**
     * Sound instance playing for the local player's own power-up sequence.
     * Only set when the local player is themselves a holder.
     * Bystanders hear the sound via a world-positioned sound, not tracked here.
     */
    private static ISound localSound = null;

    // -------------------------------------------------------------------------
    // Public API (called from packet handler on client main thread)
    // -------------------------------------------------------------------------

    public static void start(UUID holderUuid) {
        Minecraft mc = Minecraft.getMinecraft();
        ACTIVE_HOLDERS.put(holderUuid, 0);

        if (mc.player != null && holderUuid.equals(mc.player.getUniqueID())) {
            // Local player is the holder: play master (non-attenuated) sound.
            stopLocalSound(mc);
            PositionedSoundRecord sound = PositionedSoundRecord.getMasterRecord(
                ModSounds.INF_POWER_UP, 1.0f);
            localSound = sound;
            mc.getSoundHandler().playSound(sound);
        } else {
            // Bystander: play master-volume sound (server already gated this packet to
            // 80-block radius players, so no additional distance attenuation is needed).
            mc.getSoundHandler().playSound(
                PositionedSoundRecord.getMasterRecord(ModSounds.INF_POWER_UP, 1.0f));
        }
    }

    public static void stop(UUID holderUuid) {
        ACTIVE_HOLDERS.remove(holderUuid);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null && holderUuid.equals(mc.player.getUniqueID())) {
            stopLocalSound(mc);
        }
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        // HUD overlay and progress bar only shown to the local player if they are a holder.
        UUID localUuid = mc.player.getUniqueID();
        if (!ACTIVE_HOLDERS.containsKey(localUuid)) return;

        int ticksElapsed = ACTIVE_HOLDERS.get(localUuid);
        ScaledResolution res = new ScaledResolution(mc);

        int sw = res.getScaledWidth();
        int sh = res.getScaledHeight();

        ResourceLocation frame = ((ticksElapsed / CYCLE_TICKS) % 2 == 0) ? FRAME_1 : FRAME_2;

        mc.getTextureManager().bindTexture(frame);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buf.pos(0,  sh, 0).tex(0, 1).endVertex();
        buf.pos(sw, sh, 0).tex(1, 1).endVertex();
        buf.pos(sw,  0, 0).tex(1, 0).endVertex();
        buf.pos(0,   0, 0).tex(0, 0).endVertex();
        tess.draw();

        GlStateManager.disableBlend();

        drawSnapProgressBar(res, ticksElapsed / (float) SOUND_DURATION_TICKS);
    }

    /**
     * Advances tick counters once per game tick for all active holders.
     * Stops any holder whose sequence has elapsed.
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (ACTIVE_HOLDERS.isEmpty()) return;

        for (UUID uuid : ACTIVE_HOLDERS.keySet()) {
            int ticks = ACTIVE_HOLDERS.get(uuid) + 1;
            if (ticks >= SOUND_DURATION_TICKS) {
                stop(uuid);
            } else {
                ACTIVE_HOLDERS.put(uuid, ticks);
            }
        }
    }

    /**
     * Renders the skin overlay on any active holder's player model.
     * Fires for every player being rendered, so it works for the local player
     * and for all other players visible to the client.
     */
    @SubscribeEvent
    public void onRenderSuperpowerLayer(RenderSuperpowerLayerEvent event) {
        EntityPlayer player = event.getPlayer();
        UUID uuid = player.getUniqueID();
        if (!ACTIVE_HOLDERS.containsKey(uuid)) return;

        int ticksElapsed = ACTIVE_HOLDERS.get(uuid);

        RenderPlayer renderPlayer = event.getRenderPlayer();
        boolean smallArms = PlayerHelper.hasSmallArms(player);
        String cacheKey = "herosmp_inf_overlay_" + smallArms;

        ModelPlayer overlayModel = (ModelPlayer) ModelCache.getOrStoreModel(
            cacheKey,
            () -> new ModelPlayer(0.5f, smallArms)
        );

        GlStateManager.pushMatrix();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        overlayModel.setModelAttributes(renderPlayer.getMainModel());

        ResourceLocation skinFrame = ((ticksElapsed / CYCLE_TICKS) % 2 == 0) ? SKIN_OVERLAY_1 : SKIN_OVERLAY_2;
        Minecraft.getMinecraft().renderEngine.bindTexture(skinFrame);

        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        LCRenderHelper.setLightmapTextureCoords(240.0f, 240.0f);

        SuperpowerRenderer.overrideSkin = false;
        overlayModel.render(
            player,
            event.getLimbSwing(),
            event.getLimbSwingAmount(),
            event.getAgeInTicks(),
            event.getNetHeadYaw(),
            event.getHeadPitch(),
            event.getScale()
        );
        SuperpowerRenderer.overrideSkin = true;

        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        LCRenderHelper.restoreLightmapTextureCoords();

        GlStateManager.popMatrix();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Draws a gray semi-transparent rising bar inside the AbilitySnap slot in
     * LucraftCore's ability bar, showing progress through the power-up sequence.
     */
    private static void drawSnapProgressBar(ScaledResolution res, float progress) {
        List<IAbilityBarEntry> displayed = AbilityBarHandler.getCurrentDisplayedEntries(
            AbilityBarHandler.getActiveEntries());

        int snapIndex = -1;
        for (int i = 0; i < displayed.size(); i++) {
            if (displayed.get(i) instanceof AbilitySnap) {
                snapIndex = i;
                break;
            }
        }
        if (snapIndex < 0) return;

        int sw = res.getScaledWidth();
        int sh = res.getScaledHeight();

        int slotX = sw - 22 - 50 * snapIndex;
        int slotY = sh / 2 - 11;

        int fillHeight = (int) (22 * progress);
        if (fillHeight <= 0) return;

        int fillTop    = slotY + 22 - fillHeight;
        int fillBottom = slotY + 22;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(0.5f, 0.5f, 0.5f, 0.6f);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        buf.pos(slotX,      fillBottom, 0).endVertex();
        buf.pos(slotX + 22, fillBottom, 0).endVertex();
        buf.pos(slotX + 22, fillTop,    0).endVertex();
        buf.pos(slotX,      fillTop,    0).endVertex();
        tess.draw();

        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void stopLocalSound(Minecraft mc) {
        if (localSound != null) {
            mc.getSoundHandler().stopSound(localSound);
            localSound = null;
        }
    }
}
