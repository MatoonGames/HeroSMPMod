package com.matoon.herosmp.client;

import lucraft.mods.lucraftcore.superpowers.render.RenderSuperpowerLayerEvent;
import lucraft.mods.lucraftcore.superpowers.render.SuperpowerRenderer;
import lucraft.mods.lucraftcore.util.helper.LCRenderHelper;
import lucraft.mods.lucraftcore.util.helper.PlayerHelper;
import lucraft.mods.lucraftcore.util.render.ModelCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side renderer that permanently overlays a snapped-hand skin texture
 * on the snapper's player model until they die.
 *
 * Visible to all players — renders for any player whose UUID is in the
 * tracked set, not just the local player.
 *
 * Two textures are used depending on which hand held the gauntlet:
 *  - snapped_right_hand.png → gauntlet was in the main (right) hand
 *  - snapped_left_hand.png  → gauntlet was in the off (left) hand
 *
 * Uses ModelPlayer(0.5f) inflation to sit just outside the normal skin layer,
 * preventing z-fighting — the same technique LucraftCore's EffectSkinOverlay uses.
 */
@SideOnly(Side.CLIENT)
public class SnapSkinOverlay {

    private static final ResourceLocation TEXTURE_RIGHT =
        new ResourceLocation("herosmp", "textures/abilities/snapped_right_hand.png");
    private static final ResourceLocation TEXTURE_LEFT =
        new ResourceLocation("herosmp", "textures/abilities/snapped_left_hand.png");

    /**
     * Maps snapper UUID → true (main hand / right) or false (off hand / left).
     * ConcurrentHashMap because packet callbacks and render thread may race.
     */
    private static final Map<UUID, Boolean> SNAPPED_PLAYERS = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Public API — called from PacketSnapOverlay.Handler on the client main thread
    // -------------------------------------------------------------------------

    public static void add(UUID uuid, boolean mainHand) {
        SNAPPED_PLAYERS.put(uuid, mainHand);
    }

    public static void remove(UUID uuid) {
        SNAPPED_PLAYERS.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onRenderSuperpowerLayer(RenderSuperpowerLayerEvent event) {
        EntityPlayer player = event.getPlayer();
        UUID uuid = player.getUniqueID();

        Boolean mainHand = SNAPPED_PLAYERS.get(uuid);
        if (mainHand == null) return;

        ResourceLocation texture = mainHand ? TEXTURE_RIGHT : TEXTURE_LEFT;

        RenderPlayer renderPlayer = event.getRenderPlayer();
        boolean smallArms = PlayerHelper.hasSmallArms(player);
        String cacheKey = "herosmp_snapped_" + smallArms;

        ModelPlayer overlayModel = (ModelPlayer) ModelCache.getOrStoreModel(
            cacheKey,
            () -> new ModelPlayer(0.5f, smallArms)
        );

        GlStateManager.pushMatrix();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        overlayModel.setModelAttributes(renderPlayer.getMainModel());
        Minecraft.getMinecraft().renderEngine.bindTexture(texture);

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
}
