package com.matoon.herosmp.mindstone;

import lucraft.mods.lucraftcore.superpowers.abilities.AbilityAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import java.util.List;

/** A server-authoritative 32 block ray that recruits the first living target hit. */
public class AbilityMindControl extends AbilityAction {
    private static final double RANGE = 32.0D;
    private static final ResourceLocation ICON = new ResourceLocation("herosmp", "textures/abilities/mind_control.png");
    public AbilityMindControl(EntityLivingBase entity) { super(entity); setMaxCooldown(60); }
    @Override public boolean action() {
        if (!(entity instanceof EntityPlayerMP) || entity.world.isRemote || isCoolingdown()) return false;
        EntityPlayerMP owner = (EntityPlayerMP) entity;
        EntityLivingBase target = findTarget(owner);
        if (target == null) return false;
        MindControlManager.control(owner, target);
        // Particle points make the ray visible to every nearby client, including on dedicated servers.
        // Keep the cast ray visually clear without broadcasting a packet per particle per viewer.
        for (int i = 1; i <= 8; i++) {
            double t = i / 8.0D;
            Vec3d point = start(owner).add(owner.getLook(1).scale(RANGE * t));
            ((net.minecraft.world.WorldServer) owner.world).spawnParticle(EnumParticleTypes.SPELL_MOB,
                    point.x, point.y, point.z, 1, .03D, .01D, .08D, 1.0D, 165, 60, 255);
        }
        setCooldown(getMaxCooldown());
        owner.world.playSound(null, owner.posX, owner.posY, owner.posZ, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, .9F, 1.35F);
        return true;
    }
    private Vec3d start(EntityPlayerMP owner) { return owner.getPositionEyes(1); }
    private EntityLivingBase findTarget(EntityPlayerMP owner) {
        Vec3d start = owner.getPositionEyes(1), end = start.add(owner.getLook(1).scale(RANGE));
        RayTraceResult block = owner.world.rayTraceBlocks(start, end, false, true, false);
        if (block != null) end = block.hitVec;
        AxisAlignedBB box = owner.getEntityBoundingBox().expand(end.x - start.x, end.y - start.y, end.z - start.z).grow(1);
        List<Entity> hits = owner.world.getEntitiesWithinAABBExcludingEntity(owner, box);
        EntityLivingBase best = null; double nearest = start.squareDistanceTo(end);
        for (Entity hit : hits) if (hit instanceof EntityLivingBase && (hit instanceof net.minecraft.entity.EntityCreature || hit instanceof EntityPlayerMP) && !(hit instanceof net.minecraft.entity.item.EntityArmorStand) && hit.canBeCollidedWith()) {
            RayTraceResult r = hit.getEntityBoundingBox().grow(.25D).calculateIntercept(start, end);
            if (r != null && start.squareDistanceTo(r.hitVec) < nearest) { best = (EntityLivingBase) hit; nearest = start.squareDistanceTo(r.hitVec); }
        }
        return best;
    }
    @Override public void drawIcon(Minecraft mc, Gui gui, int x, int y) {
        mc.getTextureManager().bindTexture(ICON);
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, 16, 16, 16, 16);
    }
}
