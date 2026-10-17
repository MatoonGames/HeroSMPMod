package com.matoon.herosmp.client.render;

import com.matoon.herosmp.npc.playtest.EntityTestDummy;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Test dummies look like Steve and show any armor they wear. */
@SideOnly(Side.CLIENT)
public class RenderTestDummy extends RenderBiped<EntityTestDummy> {

    private static final ResourceLocation SKIN = DefaultPlayerSkin.getDefaultSkinLegacy();

    public RenderTestDummy(RenderManager manager) {
        super(manager, new ModelPlayer(0f, false), 0.5f);
        addLayer(new LayerBipedArmor(this));
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityTestDummy entity) {
        return SKIN;
    }
}
