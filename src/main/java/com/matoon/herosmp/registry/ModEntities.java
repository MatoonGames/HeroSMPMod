package com.matoon.herosmp.registry;

import com.matoon.herosmp.HeroSMP;
import com.matoon.herosmp.npc.EntityStaticNpc;
import com.matoon.herosmp.npc.client.RenderStaticNpc;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.IRenderFactory;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public final class ModEntities {

    private static final int STATIC_NPC_ENTITY_ID = 1;

    private ModEntities() {
    }

    public static void registerEntities() {
        EntityRegistry.registerModEntity(
                new ResourceLocation(HeroSMP.MODID, "static_npc"),
                EntityStaticNpc.class,
                "static_npc",
                STATIC_NPC_ENTITY_ID,
                HeroSMP.MODID,
                64,
                1,
                true,
                0xCCCCCC,
                0x333333
        );
    }

    @SideOnly(Side.CLIENT)
    public static void registerRenderers() {
        RenderingRegistry.registerEntityRenderingHandler(EntityStaticNpc.class, new IRenderFactory<EntityStaticNpc>() {
            @Override
            public Render<? super EntityStaticNpc> createRenderFor(RenderManager manager) {
                return new RenderStaticNpc(manager);
            }
        });
    }
}
