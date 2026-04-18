package com.matoon.herosmp.npc.client;

import com.matoon.herosmp.npc.EntityStaticNpc;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;

import java.util.UUID;

public class RenderStaticNpc extends RenderBiped<EntityStaticNpc> {

    private final ModelPlayer defaultModel;
    private final ModelPlayer slimModel;

    public RenderStaticNpc(RenderManager renderManager) {
        super(renderManager, new ModelPlayer(0.0F, false), 0.5F);
        this.defaultModel = (ModelPlayer) this.mainModel;
        this.slimModel = new ModelPlayer(0.0F, true);
    }

    @Override
    public void doRender(EntityStaticNpc entity, double x, double y, double z, float entityYaw, float partialTicks) {
        this.mainModel = usesSlimModel(entity) ? slimModel : defaultModel;
        super.doRender(entity, x, y, z, entityYaw, partialTicks);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityStaticNpc entity) {
        String skinOwner = entity.getSkinOwner();
        if (skinOwner == null || skinOwner.trim().isEmpty()) {
            skinOwner = "Steve";
        }

        ResourceLocation location = AbstractClientPlayer.getLocationSkin(skinOwner);
        AbstractClientPlayer.getDownloadImageSkin(location, skinOwner);
        return location;
    }

    private boolean usesSlimModel(EntityStaticNpc entity) {
        String profileId = entity.getSkinProfileId();
        if (profileId == null || profileId.trim().isEmpty()) {
            return false;
        }

        try {
            UUID uuid = UUID.fromString(profileId);
            return "slim".equals(DefaultPlayerSkin.getSkinType(uuid));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
