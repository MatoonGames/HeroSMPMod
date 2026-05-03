package com.matoon.herosmp.events;

import com.matoon.herosmp.entity.EntityTaggedArrow;
import com.matoon.herosmp.item.LifeLinkManager;
import com.matoon.herosmp.registry.ModItems;
import com.matoon.herosmp.registry.ModPotions;
import lucraft.mods.lucraftcore.superpowers.Superpower;
import lucraft.mods.lucraftcore.superpowers.SuperpowerHandler;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ArrowEffectHandler {

    // Life Link duration: 45 seconds = 900 ticks
    private static final int LIFE_LINK_DURATION = 900;
    // Supe Virus duration: 45 seconds = 900 ticks
    private static final int SUPE_VIRUS_DURATION = 900;

    // Re-entrancy guard: when we call attackEntityFrom on the linker (shooter),
    // Forge fires LivingHurtEvent on the linker. We guard by UUID so we don't
    // accidentally redirect that damage again.
    private static final Set<UUID> REDIRECTING = new HashSet<>();

    // Custom unblockable damage source for Life Link redirect — bypasses armor
    // so the full amount is always transferred.
    private static final DamageSource LIFE_LINK_DAMAGE =
            new DamageSource("life_link").setDamageBypassesArmor().setMagicDamage();

    // Custom unblockable damage source for Supe Virus ticks.
    private static final DamageSource SUPE_VIRUS_DAMAGE =
            new DamageSource("supe_virus").setDamageBypassesArmor().setMagicDamage();

    // -------------------------------------------------------------------------
    // Arrow hit
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onProjectileImpact(net.minecraftforge.event.entity.ProjectileImpactEvent event) {
        if (!(event.getEntity() instanceof EntityTaggedArrow)) return;
        EntityTaggedArrow arrow = (EntityTaggedArrow) event.getEntity();

        // Server-side only — all effects must be applied on the server.
        if (arrow.world.isRemote) return;

        net.minecraft.util.math.RayTraceResult result = event.getRayTraceResult();
        if (result == null || result.entityHit == null) return;
        if (!(result.entityHit instanceof EntityLivingBase)) return;

        EntityLivingBase target = (EntityLivingBase) result.entityHit;
        EntityLivingBase shooter = arrow.shootingEntity instanceof EntityLivingBase
                ? (EntityLivingBase) arrow.shootingEntity : null;

        // Cancel the arrow's normal impact so it deals no physical damage.
        event.setCanceled(true);
        arrow.setDead();

        applyArrowEffect(arrow.getTag(), shooter, target);
    }

    private void applyArrowEffect(EntityTaggedArrow.Tag tag, EntityLivingBase shooter, EntityLivingBase target) {
        if (shooter == null) return;

        // Totem of Reversal: swap shooter and target so the effect hits the shooter.
        if (target instanceof EntityPlayer) {
            if (hasAndConsumeTotem((EntityPlayer) target, ModItems.TOTEM_OF_REVERSAL)) {
                spawnParticlesAround(target, EnumParticleTypes.VILLAGER_HAPPY, 20);
                applyEffectDirect(tag, target, shooter);
                return;
            }
        }

        // Totem of Protection: block the effect entirely.
        if (target instanceof EntityPlayer) {
            if (hasAndConsumeTotem((EntityPlayer) target, ModItems.TOTEM_OF_PROTECTION)) {
                spawnParticlesAround(target, EnumParticleTypes.BARRIER, 8);
                return;
            }
        }

        applyEffectDirect(tag, shooter, target);
    }

    private void applyEffectDirect(EntityTaggedArrow.Tag tag, EntityLivingBase shooter, EntityLivingBase target) {
        switch (tag) {
            case POWER_SWAP:
                applyPowerSwap(shooter, target);
                break;
            case LIFE_LINK:
                applyLifeLink(shooter, target);
                break;
            case SUPE_VIRUS:
                applySupeVirus(target);
                break;
        }
    }

    // -------------------------------------------------------------------------
    // Power Swap
    // -------------------------------------------------------------------------

    private void applyPowerSwap(EntityLivingBase shooter, EntityLivingBase target) {
        if (!(shooter instanceof EntityPlayerMP) || !(target instanceof EntityPlayerMP)) return;
        EntityPlayerMP shooterPlayer = (EntityPlayerMP) shooter;
        EntityPlayerMP targetPlayer  = (EntityPlayerMP) target;

        try {
            Superpower shooterPower = SuperpowerHandler.getSuperpower(shooterPlayer);
            Superpower targetPower  = SuperpowerHandler.getSuperpower(targetPlayer);

            SuperpowerHandler.removeSuperpower(shooterPlayer);
            SuperpowerHandler.removeSuperpower(targetPlayer);
            SuperpowerHandler.syncToAll(shooterPlayer);
            SuperpowerHandler.syncToAll(targetPlayer);

            if (targetPower != null) {
                SuperpowerHandler.giveSuperpower(shooterPlayer, targetPower);
                SuperpowerHandler.syncToAll(shooterPlayer);
            }
            if (shooterPower != null) {
                SuperpowerHandler.giveSuperpower(targetPlayer, shooterPower);
                SuperpowerHandler.syncToAll(targetPlayer);
            }

            // Particle burst on both players — gold + white sparkles.
            spawnParticlesAround(shooterPlayer, EnumParticleTypes.SPELL_WITCH, 30);
            spawnParticlesAround(targetPlayer,  EnumParticleTypes.SPELL_WITCH, 30);
            spawnParticlesAround(shooterPlayer, EnumParticleTypes.FIREWORKS_SPARK, 20);
            spawnParticlesAround(targetPlayer,  EnumParticleTypes.FIREWORKS_SPARK, 20);

        } catch (Exception e) {
            System.err.println("[HeroSMP] power swap failed: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Life Link
    // -------------------------------------------------------------------------

    private void applyLifeLink(EntityLivingBase shooter, EntityLivingBase target) {
        // Guard: don't apply if target is dead, invulnerable, or a creative-mode player.
        if (target.isDead) return;
        if (target.isEntityInvulnerable(LIFE_LINK_DAMAGE)) return;
        if (target instanceof EntityPlayer && ((EntityPlayer) target).capabilities.isCreativeMode) return;

        // createLink(linker=target, linked=shooter) → LINKS.put(shooter, target)
        // → getChainEnd(shooter) = target → damage to shooter goes to target.
        boolean created = LifeLinkManager.createLink(target, shooter);
        if (!created) return;

        // The potion goes on the TARGET — they are the entity the damage is redirected TO,
        // so the effect icon displays on them. The redirect itself is gated by LifeLinkManager.
        target.addPotionEffect(new PotionEffect(ModPotions.LIFE_LINK, LIFE_LINK_DURATION, 0, false, true));

        // Visual feedback: blue particles on both players.
        spawnParticlesAround(shooter, EnumParticleTypes.PORTAL, 25);
        spawnParticlesAround(target,  EnumParticleTypes.PORTAL, 15);
        spawnParticlesAround(shooter, EnumParticleTypes.ENCHANTMENT_TABLE, 20);
    }

    /**
     * Intercepts incoming damage BEFORE armor/resistance reduction.
     * If the damaged entity has the Life Link effect, the damage is redirected
     * to the chain-end linker instead, bypassing armor on the recipient.
     *
     * Only runs server-side. The re-entrancy guard prevents infinite recursion.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingHurt(LivingHurtEvent event) {
        // Life Link redirect is purely server logic.
        if (event.getEntityLiving().world.isRemote) return;

        EntityLivingBase entity = event.getEntityLiving();

        // Skip damage we ourselves caused to avoid recursion.
        if (REDIRECTING.contains(entity.getUniqueID())) return;

        // Only intercept entities that have an active life link in the manager.
        // The potion effect is displayed on the target (redirect recipient), not the shooter.
        if (!LifeLinkManager.isLinked(entity.getUniqueID())) return;

        // Don't redirect damage that is already from us (supe_virus or life_link source).
        DamageSource src = event.getSource();
        if ("life_link".equals(src.damageType) || "supe_virus".equals(src.damageType)) return;

        UUID entityUuid = entity.getUniqueID();
        UUID chainEnd   = LifeLinkManager.getChainEnd(entityUuid);
        if (chainEnd == null) return;

        EntityLivingBase recipient = findEntityByUUID(entity, chainEnd);
        if (recipient == null || recipient.getUniqueID().equals(entityUuid)) return;

        float amount = event.getAmount();

        // Cancel the original damage — the target takes nothing.
        event.setCanceled(true);

        // Reset the recipient's invulnerability timer so the damage always lands.
        int savedHurtTime = recipient.hurtResistantTime;
        recipient.hurtResistantTime = 0;

        // Guard against re-entry in case recipient is also in a link chain.
        REDIRECTING.add(chainEnd);
        try {
            recipient.attackEntityFrom(LIFE_LINK_DAMAGE, amount);
        } finally {
            REDIRECTING.remove(chainEnd);
            // Restore hurt-resistant time so normal combat timing isn't broken.
            // Only restore if the attack didn't actually land (e.g. recipient died).
            // We always reset to 0 first to guarantee the hit lands.
        }

        // Particle flash on the recipient (damage indicator).
        spawnParticlesAround(recipient, EnumParticleTypes.CRIT_MAGIC, 12);
    }

    /**
     * Blocks ALL healing while the Supe Virus effect is active, including natural
     * food saturation regen, absorption, and any other heal() calls.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingHeal(LivingHealEvent event) {
        if (event.getEntityLiving().world.isRemote) return;
        if (event.getEntityLiving().isPotionActive(ModPotions.SUPE_VIRUS)) {
            event.setCanceled(true);
        }
    }

    // -------------------------------------------------------------------------
    // Supe Virus
    // -------------------------------------------------------------------------

    private void applySupeVirus(EntityLivingBase target) {
        // Must be a server-side player with an active Lucraft superpower.
        if (!(target instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) target;

        if (!hasSuperpower(player)) return;

        player.addPotionEffect(new PotionEffect(ModPotions.SUPE_VIRUS, SUPE_VIRUS_DURATION, 0, false, true));

        // Green infection particles on hit.
        spawnParticlesAround(player, EnumParticleTypes.VILLAGER_ANGRY, 20);
        spawnParticlesAround(player, EnumParticleTypes.SPELL_MOB, 15, 0.0f, 0.8f, 0.0f);
    }

    /** Per-tick handler: prune expired links, suppress Supe regen, emit virus particles. */
    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        if (entity.world.isRemote) return;

        // Prune life links whose potion has expired.
        // The potion sits on the TARGET (chain-end / damage recipient).
        // The shooter is the KEY in LifeLinkManager; target is the VALUE.
        UUID entityUuid = entity.getUniqueID();

        // Case 1: this entity is the shooter (key). If the target no longer has
        // the potion, the link is stale — remove it and strip the potion from the
        // target to ensure the icon clears.
        if (LifeLinkManager.isLinked(entityUuid)) {
            UUID chainEnd = LifeLinkManager.getChainEnd(entityUuid);
            if (chainEnd == null) {
                LifeLinkManager.removeLink(entityUuid);
            } else {
                EntityLivingBase chainEndEntity = findEntityByUUID(entity, chainEnd);
                if (chainEndEntity == null || !chainEndEntity.isPotionActive(ModPotions.LIFE_LINK)) {
                    LifeLinkManager.removeLink(entityUuid);
                    // Explicitly clear the potion from the target in case it somehow survived.
                    if (chainEndEntity != null) {
                        chainEndEntity.removePotionEffect(ModPotions.LIFE_LINK);
                    }
                }
            }
        }

        // Case 2: this entity is the target (value). If the potion expired, remove
        // all links pointing to this entity and strip the potion to be sure.
        if (!entity.isPotionActive(ModPotions.LIFE_LINK)) {
            LifeLinkManager.removeLinksPointingTo(entityUuid);
        } else {
            // Potion is active — make sure the link still exists. If the link was
            // already removed (e.g. shooter logged off) but the potion is still ticking,
            // forcibly remove the potion so the icon goes away.
            if (!LifeLinkManager.hasLinkerFor(entityUuid)) {
                entity.removePotionEffect(ModPotions.LIFE_LINK);
            }
        }

        // Supe Virus per-tick logic.
        if (entity.isPotionActive(ModPotions.SUPE_VIRUS) && entity instanceof EntityPlayerMP) {
            suppressSupeRegen((EntityPlayerMP) entity);

            // Emit green particles every second (20 ticks).
            if (entity.ticksExisted % 20 == 0) {
                spawnParticlesAround(entity, EnumParticleTypes.SPELL_MOB, 8, 0.0f, 0.8f, 0.0f);
            }
        }

        // Life Link: emit blue particles every second on both the shooter and the target.
        if (entity.ticksExisted % 20 == 0) {
            if (LifeLinkManager.isLinked(entityUuid) || entity.isPotionActive(ModPotions.LIFE_LINK)) {
                spawnParticlesAround(entity, EnumParticleTypes.PORTAL, 5);
            }
        }
    }

    private void suppressSupeRegen(EntityPlayerMP player) {
        if (!hasSuperpower(player)) {
            player.removePotionEffect(ModPotions.SUPE_VIRUS);
            return;
        }
        // Strip any regen or absorption potion effects every tick.
        // Natural food regen is blocked by the LivingHealEvent handler.
        if (player.isPotionActive(net.minecraft.init.MobEffects.REGENERATION)) {
            player.removePotionEffect(net.minecraft.init.MobEffects.REGENERATION);
        }
        if (player.isPotionActive(net.minecraft.init.MobEffects.ABSORPTION)) {
            player.removePotionEffect(net.minecraft.init.MobEffects.ABSORPTION);
        }
    }

    // -------------------------------------------------------------------------
    // Totem helpers
    // -------------------------------------------------------------------------

    private boolean hasAndConsumeTotem(EntityPlayer player, net.minecraft.item.Item totemItem) {
        // Only counts if held in main hand or off-hand — not sitting in inventory.
        for (net.minecraft.util.EnumHand hand : net.minecraft.util.EnumHand.values()) {
            ItemStack stack = player.getHeldItem(hand);
            if (!stack.isEmpty() && stack.getItem() == totemItem) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Lucraft helpers
    // -------------------------------------------------------------------------

    private boolean hasSuperpower(EntityPlayerMP player) {
        try {
            return SuperpowerHandler.hasSuperpower(player);
        } catch (Exception e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Particle helpers
    // -------------------------------------------------------------------------

    /**
     * Spawns particles around an entity using the server's sendParticles mechanism.
     * Only works server-side (particles are sent to nearby clients).
     */
    private void spawnParticlesAround(EntityLivingBase entity, EnumParticleTypes type, int count) {
        if (!(entity.world instanceof net.minecraft.world.WorldServer)) return;
        net.minecraft.world.WorldServer world = (net.minecraft.world.WorldServer) entity.world;
        double cx = entity.posX;
        double cy = entity.posY + entity.height * 0.5;
        double cz = entity.posZ;
        world.spawnParticle(type, cx, cy, cz, count, 0.4, entity.height * 0.4, 0.4, 0.2);
    }

    /**
     * Spawns colored SPELL_MOB particles (RGB 0–1 passed as xOff/yOff/zOff, speed must be 1).
     * For non-mob-spell types the r/g/b values are ignored and the regular overload is used.
     */
    private void spawnParticlesAround(EntityLivingBase entity, EnumParticleTypes type, int count,
                                       float r, float g, float b) {
        if (!(entity.world instanceof net.minecraft.world.WorldServer)) return;
        net.minecraft.world.WorldServer world = (net.minecraft.world.WorldServer) entity.world;
        double cx = entity.posX;
        double cy = entity.posY + entity.height * 0.5;
        double cz = entity.posZ;
        // For SPELL_MOB, xOff/yOff/zOff encode the color (0–1 range) and speed must be 1.
        world.spawnParticle(type, cx, cy, cz, count, r, g, b, 1.0);
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private EntityLivingBase findEntityByUUID(EntityLivingBase relativeTo, UUID uuid) {
        for (net.minecraft.entity.Entity e : relativeTo.world.loadedEntityList) {
            if (e instanceof EntityLivingBase && e.getUniqueID().equals(uuid)) {
                return (EntityLivingBase) e;
            }
        }
        return null;
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LifeLinkManager.removeLink(event.player.getUniqueID());
    }
}
