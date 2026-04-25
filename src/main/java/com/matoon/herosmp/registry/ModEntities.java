package com.matoon.herosmp.registry;

import com.matoon.herosmp.HeroSMP;
import com.matoon.herosmp.entity.EntityTaggedArrow;
import com.matoon.herosmp.integration.EntityLucraftInjection;
import com.matoon.herosmp.integration.RenderLucraftInjection;
import com.matoon.herosmp.npc.EntityStaticNpc;
import com.matoon.herosmp.npc.client.RenderStaticNpc;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderArrow;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.IRenderFactory;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public final class ModEntities {

    private static final int STATIC_NPC_ENTITY_ID    = 1;
    private static final int LUCRAFT_INJECTION_ID     = 2;
    private static final int TAGGED_ARROW_ID          = 3;

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

        EntityRegistry.registerModEntity(
                new ResourceLocation(HeroSMP.MODID, "lucraft_injection"),
                EntityLucraftInjection.class,
                "lucraft_injection",
                LUCRAFT_INJECTION_ID,
                HeroSMP.MODID,
                64,
                1,
                false,
                0xAA44FF,
                0x550088
        );

        EntityRegistry.registerModEntity(
                new ResourceLocation(HeroSMP.MODID, "tagged_arrow"),
                EntityTaggedArrow.class,
                "tagged_arrow",
                TAGGED_ARROW_ID,
                HeroSMP.MODID,
                64,
                2,
                false,
                0xFFDD44,
                0xAA8800
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

        RenderingRegistry.registerEntityRenderingHandler(EntityLucraftInjection.class, new IRenderFactory<EntityLucraftInjection>() {
            @Override
            public Render<? super EntityLucraftInjection> createRenderFor(RenderManager manager) {
                return new RenderLucraftInjection(manager);
            }
        });

        RenderingRegistry.registerEntityRenderingHandler(EntityTaggedArrow.class, new IRenderFactory<EntityTaggedArrow>() {
            @Override
            public Render<? super EntityTaggedArrow> createRenderFor(RenderManager manager) {
                return new RenderArrow<EntityTaggedArrow>(manager) {
                    private static final String ARROW_TEX = "textures/entity/projectiles/arrow.png";
                    @Override
                    protected net.minecraft.util.ResourceLocation getEntityTexture(EntityTaggedArrow entity) {
                        return new net.minecraft.util.ResourceLocation(ARROW_TEX);
                    }
                };
            }
        });
    }
}
