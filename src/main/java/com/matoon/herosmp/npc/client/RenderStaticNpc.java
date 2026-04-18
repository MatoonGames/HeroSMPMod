package com.matoon.herosmp.npc.client;

import com.matoon.herosmp.npc.EntityStaticNpc;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntitySkull;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RenderStaticNpc extends RenderBiped<EntityStaticNpc> {

    private final ModelPlayer defaultModel;
    private final ModelPlayer slimModel;
    private final Map<String, SkinState> skinStates = new HashMap<String, SkinState>();

    public RenderStaticNpc(RenderManager renderManager) {
        super(renderManager, new ModelPlayer(0.0F, false), 0.5F);
        this.defaultModel = (ModelPlayer) this.mainModel;
        this.slimModel = new ModelPlayer(0.0F, true);
    }

    @Override
    public void doRender(EntityStaticNpc entity, double x, double y, double z, float entityYaw, float partialTicks) {
        this.mainModel = usesSlimModel(entity) ? slimModel : defaultModel;
        super.doRender(entity, x, y, z, entityYaw, partialTicks);
        renderFloatingItem(entity, x, y, z, partialTicks);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityStaticNpc entity) {
        String skinOwner = normalizeOwner(entity.getSkinOwner());
        SkinState state = resolveSkinState(skinOwner);
        return state.location != null ? state.location : DefaultPlayerSkin.getDefaultSkin(state.profileId);
    }

    private boolean usesSlimModel(EntityStaticNpc entity) {
        return resolveSkinState(normalizeOwner(entity.getSkinOwner())).slim;
    }

    private SkinState resolveSkinState(String skinOwner) {
        SkinState state = skinStates.get(skinOwner);
        if (state != null) {
            return state;
        }

        GameProfile requested = new GameProfile((UUID) null, skinOwner);
        GameProfile resolved = TileEntitySkull.updateGameprofile(requested);
        UUID profileId = resolved != null && resolved.getId() != null
                ? resolved.getId()
                : UUID.nameUUIDFromBytes(("OfflinePlayer:" + skinOwner).getBytes(java.nio.charset.StandardCharsets.UTF_8));

        state = new SkinState(profileId);
        skinStates.put(skinOwner, state);
        final SkinState resolvedState = state;

        Minecraft.getMinecraft().getSkinManager().loadProfileTextures(resolved != null ? resolved : requested, new net.minecraft.client.resources.SkinManager.SkinAvailableCallback() {
            @Override
            public void skinAvailable(MinecraftProfileTexture.Type type, ResourceLocation location, MinecraftProfileTexture profileTexture) {
                if (type != MinecraftProfileTexture.Type.SKIN) {
                    return;
                }
                resolvedState.location = location;
                String model = profileTexture == null ? null : profileTexture.getMetadata("model");
                resolvedState.slim = "slim".equals(model);
            }
        }, true);

        return state;
    }

    private String normalizeOwner(String skinOwner) {
        if (skinOwner == null || skinOwner.trim().isEmpty()) {
            return "Steve";
        }
        return skinOwner.trim();
    }

    private ItemStack getDisplayItem(EntityStaticNpc entity) {
        String itemId = entity.getDisplayItemId();
        if (itemId == null || itemId.trim().isEmpty()) {
            return ItemStack.EMPTY;
        }
        Item item = Item.getByNameOrId(itemId.trim());
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private void renderFloatingItem(EntityStaticNpc entity, double x, double y, double z, float partialTicks) {
        ItemStack stack = getDisplayItem(entity);
        if (stack.isEmpty()) return;

        float age = entity.ticksExisted + partialTicks;
        float bob = MathHelper.sin(age / 20.0F) * 0.1F;
        float spin = age * 3.0F % 360.0F;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y + entity.height + 1 + bob, z);
        GlStateManager.rotate(spin, 0.0F, 1.0F, 0.0F);
        GlStateManager.scale(1.0F, 1.0F, 1.0F);
        RenderHelper.enableStandardItemLighting();
        Minecraft.getMinecraft().getRenderItem().renderItem(stack, ItemCameraTransforms.TransformType.GROUND);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    private static class SkinState {
        private final UUID profileId;
        private ResourceLocation location;
        private boolean slim;

        private SkinState(UUID profileId) {
            this.profileId = profileId;
            this.location = null;
            this.slim = "slim".equals(DefaultPlayerSkin.getSkinType(profileId));
        }
    }
}
