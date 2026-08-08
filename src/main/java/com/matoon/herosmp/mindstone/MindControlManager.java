package com.matoon.herosmp.mindstone;

import anvil.infinity.helpers.GauntelHelper;
import com.matoon.herosmp.HeroSMP;
import com.matoon.herosmp.network.ModNetwork;
import com.matoon.herosmp.network.PacketMindControlAura;
import com.matoon.herosmp.network.PacketMindControlEntries;
import com.matoon.herosmp.network.PacketMindControlLock;
import com.matoon.herosmp.network.PacketOpenMindControlMenu;
import com.matoon.herosmp.network.PacketMindControlResistance;
import com.matoon.herosmp.network.PacketMindControlResistanceResult;
import lucraft.mods.lucraftcore.superpowers.abilities.Ability;
import lucraft.mods.lucraftcore.superpowers.abilities.AbilityAction;
import lucraft.mods.lucraftcore.superpowers.abilities.AbilityFlight;
import lucraft.mods.lucraftcore.superpowers.Superpower;
import lucraft.mods.lucraftcore.superpowers.SuperpowerHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import lucraft.mods.lucraftcore.superpowers.events.AbilityKeyEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.util.*;
import com.google.common.collect.Multimap;

/**
 * Ownership is indexed by controller and keeps direct entity references. This deliberately avoids
 * world scans and periodic full sidebar packets: only changed controllers are synced.
 */
public class MindControlManager {
    private static final Map<UUID, OwnerControl> BY_OWNER = new HashMap<>();
    private static final Map<UUID, UUID> TARGET_OWNER = new HashMap<>();
    private static final Map<UUID, Long> LAST_ROBOT_ATTACK = new HashMap<>();
    private static final Set<UUID> AUTOMATED_ATTACKS = new HashSet<>();
    private static final Map<UUID,ResistanceAttempt> RESISTANCE=new HashMap<>();
    private static final int LOW_HEALTH = 2;
    private static class Controlled { final EntityLivingBase target; final long expires; final float startingHealth; final PlayerBotNavigator navigator=new PlayerBotNavigator(); final Set<String> autoToggles=new HashSet<>(); long nextAi, nextAbilityCast, heldRelease,nextResistance; int blockedTicks,resistanceLevel; boolean abilityAutocast=true,navigationFlight; Ability heldAbility; Controlled(EntityLivingBase t, long e) { target=t; expires=e; startingHealth=t.getHealth(); nextResistance=t.world.getTotalWorldTime()+400; } }
    private static class ResistanceAttempt { final UUID token=UUID.randomUUID();final EntityPlayerMP owner,target;final boolean escape;final long start,end;final int windowStart,windowEnd;boolean answered;ResistanceAttempt(EntityPlayerMP o,EntityPlayerMP t,boolean escape,long now,int level){owner=o;target=t;this.escape=escape;start=now;end=now+40;int width=Math.min(14,4+level*2),center=12+o.getRNG().nextInt(17);windowStart=center-width/2;windowEnd=center+width/2;} }
    private static class OwnerControl { EntityPlayerMP owner; final Map<UUID, Controlled> targets=new HashMap<>(); boolean soulLast; EntityLivingBase threat; long threatUntil; OwnerControl(EntityPlayerMP p) { owner=p; soulLast=GauntelHelper.hasSoulStone(p); } }

    public static void control(EntityPlayerMP owner, EntityLivingBase target) {
        if(target instanceof EntityPlayerMP){startResistance(owner,(EntityPlayerMP)target,false,0);return;}
        applyControl(owner,target);
    }
    private static void applyControl(EntityPlayerMP owner, EntityLivingBase target) {
        if (target == owner || target.isDead || !(target instanceof EntityCreature || target instanceof EntityPlayerMP) || target instanceof net.minecraft.entity.item.EntityArmorStand) return;
        UUID previous=TARGET_OWNER.get(target.getUniqueID());
        if (previous != null) releaseInternal(previous, target.getUniqueID(), false);
        OwnerControl state=BY_OWNER.computeIfAbsent(owner.getUniqueID(), id -> new OwnerControl(owner));
        state.owner=owner;
        state.targets.put(target.getUniqueID(), new Controlled(target, owner.world.getTotalWorldTime()+HeroSMP.mindControlDurationSeconds*20L));
        TARGET_OWNER.put(target.getUniqueID(), owner.getUniqueID());
        aura(target, true);
        if (target instanceof EntityPlayerMP) {ModNetwork.CHANNEL.sendTo(new PacketMindControlLock(true,HeroSMP.mindControlDurationSeconds*20),(EntityPlayerMP)target);success((EntityPlayerMP)target,"You've been mind controlled by "+owner.getName());}
        success(owner,"You have mind controlled "+target.getName());
        sync(state);
    }
    private static void startResistance(EntityPlayerMP owner,EntityPlayerMP target,boolean escape,int level){if(owner==target||RESISTANCE.containsKey(target.getUniqueID()))return;long now=owner.world.getTotalWorldTime();ResistanceAttempt attempt=new ResistanceAttempt(owner,target,escape,now,level);RESISTANCE.put(target.getUniqueID(),attempt);ModNetwork.CHANNEL.sendTo(new PacketMindControlResistance(attempt.token,40,attempt.windowStart,attempt.windowEnd,escape),target);}
    public static void resistanceResponse(EntityPlayerMP target,UUID token,int elapsed){ResistanceAttempt a=RESISTANCE.get(target.getUniqueID());if(a==null||a.answered||!a.token.equals(token)||elapsed<0||elapsed>40)return;a.answered=true;RESISTANCE.remove(target.getUniqueID());boolean resisted=elapsed>=a.windowStart&&elapsed<=a.windowEnd;ModNetwork.CHANNEL.sendTo(new PacketMindControlResistanceResult(),target);if(resisted){if(a.escape){failure(a.owner,target.getName()+" has broken your mind control");failure(target,"You broke free from "+a.owner.getName()+"'s mind control");release(a.owner,target.getUniqueID(),false);}else{failure(a.owner,target.getName()+" has resisted your mind control attempt");failure(target,"You resisted "+a.owner.getName()+"'s mind control attempt");}}else if(!a.escape)applyControl(a.owner,target);}
    private static void status(EntityPlayerMP player,String message,net.minecraft.util.text.TextFormatting color){player.sendStatusMessage(new net.minecraft.util.text.TextComponentString(color+message),true);}
    private static void sound(EntityPlayerMP player,net.minecraft.util.SoundEvent sound,float pitch){player.connection.sendPacket(new net.minecraft.network.play.server.SPacketSoundEffect(sound,SoundCategory.PLAYERS,player.posX,player.posY,player.posZ,1F,pitch));}
    private static void success(EntityPlayerMP player,String message){status(player,message,net.minecraft.util.text.TextFormatting.LIGHT_PURPLE);sound(player,SoundEvents.ENTITY_PLAYER_LEVELUP,1.2F);}
    private static void failure(EntityPlayerMP player,String message){status(player,message,net.minecraft.util.text.TextFormatting.RED);sound(player,SoundEvents.BLOCK_GLASS_BREAK,.9F);}

    public static boolean release(EntityPlayerMP owner, UUID targetId, boolean sacrifice) {
        if (!owner.getUniqueID().equals(TARGET_OWNER.get(targetId))) return false;
        Controlled control=get(owner.getUniqueID(), targetId);
        if (control == null) return false;
        if (sacrifice && control.target.getHealth()>1) { float stolen=control.target.getHealth()-1; control.target.setHealth(1); owner.heal(stolen); }
        releaseInternal(owner.getUniqueID(), targetId, true); return true;
    }
    /** Builds a read-only server snapshot for the companion management GUI. */
    public static void openMenu(EntityPlayerMP owner, UUID targetId) {
        Controlled control=get(owner.getUniqueID(),targetId); if(control==null)return;
        EntityLivingBase target=control.target; List<ItemStack> items=new ArrayList<>(); boolean player=target instanceof EntityPlayerMP;
        if(player) { EntityPlayerMP p=(EntityPlayerMP)target; for(int i=0;i<p.inventory.getSizeInventory();i++)items.add(p.inventory.getStackInSlot(i).copy()); }
        else for(net.minecraft.inventory.EntityEquipmentSlot slot:net.minecraft.inventory.EntityEquipmentSlot.values())items.add(target.getItemStackFromSlot(slot).copy());
        int remaining=(int)Math.max(0,control.expires-owner.world.getTotalWorldTime());
        List<PacketOpenMindControlMenu.AbilityInfo> abilities=new ArrayList<>();
        for(Ability ability:Ability.getAbilities(target)) if(ability.isUnlocked()&&!ability.isHidden()) {
            boolean triggerable=ability.getAbilityType()!=Ability.AbilityType.CONSTANT;
            abilities.add(new PacketOpenMindControlMenu.AbilityInfo(ability.getKey(),ability.getDisplayName(),ability.getDisplayDescription(),triggerable&&!ability.isCoolingdown(),triggerable));
        }
        Superpower superpower=SuperpowerHandler.getSuperpower(target);
        String superpowerId=superpower==null||superpower.getRegistryName()==null?"":superpower.getRegistryName().toString();
        String superpowerName=superpower==null?"":superpower.getDisplayName();
        ModNetwork.CHANNEL.sendTo(new PacketOpenMindControlMenu(targetId,target.getEntityId(),target.getName(),player,items,target.getHealth(),target.getMaxHealth(),target.getTotalArmorValue(),remaining,abilities,control.abilityAutocast,superpowerId,superpowerName),owner);
    }
    /** Drain retains the original sacrifice behaviour; release simply ends control. */
    public static void menuAction(EntityPlayerMP owner, UUID targetId, boolean drain) { release(owner,targetId,drain); }
    /** Ability controls are deliberately server-side: the client can only request an action for its own companion. */
    public static void triggerAbility(EntityPlayerMP owner, UUID targetId, String key) {
        Controlled control=get(owner.getUniqueID(),targetId); if(control==null||key==null)return;
        for(Ability ability:getActiveAbilities(control.target)) if(key.equals(ability.getKey())&&!ability.isCoolingdown()) { activate(control,ability,owner.world.getTotalWorldTime(),false); return; }
    }
    public static void setAbilityAutocast(EntityPlayerMP owner, UUID targetId, boolean enabled) { Controlled control=get(owner.getUniqueID(),targetId); if(control!=null){control.abilityAutocast=enabled; control.nextAbilityCast=0;} }
    private static List<Ability> getActiveAbilities(EntityLivingBase entity) { List<Ability> result=new ArrayList<>(); for(Ability ability:Ability.getAbilities(entity))if(ability.isUnlocked()&&!ability.isHidden()&&ability.getAbilityType()!=Ability.AbilityType.CONSTANT)result.add(ability); return result; }
    private static void activate(Controlled control,Ability ability,long now,boolean automatic){
        if(ability.getAbilityType()==Ability.AbilityType.HELD){if(control.heldAbility!=null)control.heldAbility.onKeyReleased();ability.onKeyPressed();control.heldAbility=ability;control.heldRelease=now+30;}
        else {ability.onKeyPressed();ability.onKeyReleased();if(automatic&&ability.getAbilityType()==Ability.AbilityType.TOGGLE&&ability.isEnabled())control.autoToggles.add(ability.getKey());}
    }
    private static void stopAutomaticAbilities(Controlled control,boolean preserveNavigationFlight){
        if(control.heldAbility!=null){control.heldAbility.onKeyReleased();control.heldAbility=null;}
        if(!control.autoToggles.isEmpty()){for(Ability ability:getActiveAbilities(control.target))if(control.autoToggles.contains(ability.getKey())&&!(preserveNavigationFlight&&ability instanceof AbilityFlight)&&ability.getAbilityType()==Ability.AbilityType.TOGGLE&&ability.isEnabled()){ability.onKeyPressed();ability.onKeyReleased();}if(preserveNavigationFlight)control.autoToggles.removeIf(key->{Ability flight=findFlight(control.target);return flight==null||!key.equals(flight.getKey());});else control.autoToggles.clear();}
    }
    private static void autocast(EntityPlayerMP owner, Controlled control, long now) {
        if(control.heldAbility!=null&&now>=control.heldRelease){control.heldAbility.onKeyReleased();control.heldAbility=null;}
        EntityLivingBase enemy=combatThreat(owner,control.target);if(!control.abilityAutocast||enemy==null){stopAutomaticAbilities(control,control.navigationFlight);return;}if(now<control.nextAbilityCast||control.heldAbility!=null)return;
        face(control.target,enemy);List<Ability> ready=new ArrayList<>(); for(Ability ability:getActiveAbilities(control.target))if(!(ability instanceof AbilityFlight)&&!ability.isCoolingdown()&&(ability.getAbilityType()!=Ability.AbilityType.TOGGLE||!ability.isEnabled()))ready.add(ability);
        control.nextAbilityCast=now+80+owner.getRNG().nextInt(61);
        if(!ready.isEmpty())activate(control,ready.get(owner.getRNG().nextInt(ready.size())),now,true);
    }
    private static Ability findFlight(EntityLivingBase entity){for(Ability ability:Ability.getAbilities(entity))if(ability instanceof AbilityFlight&&ability.isUnlocked()&&!ability.isHidden())return ability;return null;}
    private static void face(EntityLivingBase source,EntityLivingBase target){double dx=target.posX-source.posX,dz=target.posZ-source.posZ,dy=target.posY+target.getEyeHeight()-(source.posY+source.getEyeHeight()),flat=Math.sqrt(dx*dx+dz*dz);source.rotationYaw=(float)(Math.atan2(-dx,dz)*180D/Math.PI);source.rotationYawHead=source.rotationYaw;source.rotationPitch=(float)(-Math.atan2(dy,flat)*180D/Math.PI);}
    /** Autopilot is also valid for injected mobs; use their actual combat target as well as threats to the controller. */
    private static EntityLivingBase combatThreat(EntityPlayerMP owner, EntityLivingBase companion) {
        EntityLivingBase target=threat(owner); if(target!=null)return target;
        target=companion.getRevengeTarget(); if(validThreat(target,owner))return target;
        if(companion instanceof EntityCreature) { target=((EntityCreature)companion).getAttackTarget(); if(validThreat(target,owner))return target; }
        if(companion instanceof EntityPlayerMP) { target=((EntityPlayerMP)companion).getLastAttackedEntity(); if(validThreat(target,owner))return target; }
        return null;
    }
    private static Controlled get(UUID owner, UUID target) { OwnerControl s=BY_OWNER.get(owner); return s==null?null:s.targets.get(target); }
    private static void releaseInternal(UUID ownerId, UUID targetId, boolean sync) {
        OwnerControl state=BY_OWNER.get(ownerId); if (state==null) return;
        Controlled removed=state.targets.remove(targetId); TARGET_OWNER.remove(targetId);
        if (removed != null) {
            stopAutomaticAbilities(removed,false);
            if (removed.target instanceof EntityCreature) ((EntityCreature)removed.target).setAttackTarget(null);
            aura(removed.target, false);
            if (removed.target instanceof EntityPlayerMP) ModNetwork.CHANNEL.sendTo(new PacketMindControlLock(false,0), (EntityPlayerMP)removed.target);
        }
        if (sync) sync(state);
        // Empty owner buckets are pruned by the server tick. Keeping removal there avoids
        // modifying BY_OWNER from combat/death callbacks while it is being iterated.
    }
    private static void aura(EntityLivingBase entity, boolean enabled) { ModNetwork.CHANNEL.sendToAll(new PacketMindControlAura(entity.getUniqueID(), enabled)); }
    private static void sync(OwnerControl state) {
        if (state.owner == null || !state.owner.connection.netManager.isChannelOpen()) return;
        List<PacketMindControlEntries.Entry> entries=new ArrayList<>();
        if (GauntelHelper.hasSoulStone(state.owner)) for (Map.Entry<UUID,Controlled> e:state.targets.entrySet()) entries.add(new PacketMindControlEntries.Entry(e.getKey(),e.getValue().target.getName(),e.getValue().target instanceof EntityPlayerMP,e.getValue().target.getEntityId()));
        ModNetwork.CHANNEL.sendTo(new PacketMindControlEntries(entries),state.owner);
        state.soulLast=GauntelHelper.hasSoulStone(state.owner);
    }

    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for(Iterator<Map.Entry<UUID,ResistanceAttempt>> ri=RESISTANCE.entrySet().iterator();ri.hasNext();){ResistanceAttempt a=ri.next().getValue();long now=a.target.world.getTotalWorldTime();boolean invalid=a.owner.isDead||a.target.isDead||a.owner.world!=a.target.world||!GauntelHelper.hasMindStone(a.owner);if(invalid||now>=a.end){ri.remove();ModNetwork.CHANNEL.sendTo(new PacketMindControlResistanceResult(),a.target);if(!invalid&&!a.escape)applyControl(a.owner,a.target);}}
        Iterator<Map.Entry<UUID,OwnerControl>> owners=BY_OWNER.entrySet().iterator();
        while (owners.hasNext()) {
            Map.Entry<UUID, OwnerControl> ownerEntry=owners.next();
            UUID ownerId=ownerEntry.getKey(); OwnerControl state=ownerEntry.getValue(); EntityPlayerMP owner=state.owner;
            // One cheap player-list lookup per controller every second handles logout/reconnect, never per companion.
            if (owner == null || owner.ticksExisted%20==0) owner=net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayerByUUID(ownerId);
            if (owner == null || !GauntelHelper.hasMindStone(owner)) {
                for (Controlled c:state.targets.values()) { stopAutomaticAbilities(c,false); TARGET_OWNER.remove(c.target.getUniqueID()); aura(c.target,false); if(c.target instanceof EntityPlayerMP)ModNetwork.CHANNEL.sendTo(new PacketMindControlLock(false),(EntityPlayerMP)c.target); }
                if(owner!=null)ModNetwork.CHANNEL.sendTo(new PacketMindControlEntries(Collections.emptyList()),owner);
                owners.remove(); continue;
            }
            state.owner=owner;
            if (state.soulLast != GauntelHelper.hasSoulStone(owner)) sync(state);
            long now=owner.world.getTotalWorldTime();
            for (Iterator<Map.Entry<UUID,Controlled>> it=state.targets.entrySet().iterator();it.hasNext();) {
                Controlled c=it.next().getValue(); EntityLivingBase target=c.target;
                // A freshly drained target may be controlled again at half a heart. Only release
                // for low health when it fell there during this particular control session.
                boolean healthBreak=target.getHealth()<=LOW_HEALTH&&c.startingHealth>LOW_HEALTH;
                if (target.isDead || target.world!=owner.world || healthBreak || now>=c.expires) { if(healthBreak){failure(owner,"Your mind control was broken on "+target.getName());if(target instanceof EntityPlayerMP)sound((EntityPlayerMP)target,SoundEvents.BLOCK_GLASS_BREAK,.9F);}stopAutomaticAbilities(c,false); it.remove(); TARGET_OWNER.remove(target.getUniqueID()); aura(target,false); if(target instanceof EntityPlayerMP)ModNetwork.CHANNEL.sendTo(new PacketMindControlLock(false),(EntityPlayerMP)target); sync(state); continue; }
                if(target instanceof EntityPlayerMP) robot((EntityPlayerMP)target,owner,c,now);
                autocast(owner,c,now);
                if(target instanceof EntityPlayerMP&&now>=c.nextResistance&&!RESISTANCE.containsKey(target.getUniqueID())){startResistance(owner,(EntityPlayerMP)target,true,c.resistanceLevel);c.resistanceLevel++;c.nextResistance=now+400;}
                if (now<c.nextAi) continue; c.nextAi=now+5;
                if(target instanceof EntityCreature) steer((EntityCreature)target,owner,c,now);
            }
            if(state.targets.isEmpty()) owners.remove();
        }
    }
    private static void steer(EntityCreature mob, EntityPlayerMP owner,Controlled control,long now) {
        if(mob.getAttackTarget()==owner||!validThreat(mob.getAttackTarget(),owner))mob.setAttackTarget(null);
        EntityLivingBase threat=threat(owner);
        EntityLivingBase destination=threat!=null&&threat!=mob?threat:owner;if(threat!=null&&threat!=mob)mob.setAttackTarget(threat);else if(mob.getAttackTarget()==mob||isControlled(mob.getAttackTarget()))mob.setAttackTarget(null);
        Ability flight=findFlight(mob);double distance=Math.sqrt(mob.getDistanceSq(destination)),dy=destination.posY-mob.posY;boolean needed=control.abilityAutocast&&flight!=null&&(Math.abs(dy)>3D||distance>20D||hasUnsafeGap(mob,destination.posX,destination.posZ));control.navigationFlight=needed;
        if(needed&&!flight.isEnabled())activate(control,flight,now,true);else if(!needed&&flight!=null&&control.autoToggles.remove(flight.getKey())&&flight.isEnabled()){flight.onKeyPressed();flight.onKeyReleased();}
        if(flight!=null&&flight.isEnabled()){double dx=destination.posX-mob.posX,dz=destination.posZ-mob.posZ,targetY=destination.posY+Math.max(1D,destination.height*.5D),fy=targetY-mob.posY,length=Math.sqrt(dx*dx+fy*fy+dz*dz);if(length>.4D){face(mob,destination);double speed=distance>12D?.38D:.24D;mob.moveForward=1F;mob.motionX=dx/length*speed;mob.motionY=fy/length*speed;mob.motionZ=dz/length*speed;mob.fallDistance=0;}else{mob.moveForward=0;mob.motionX=mob.motionY=mob.motionZ=0;}return;}
        if(destination==owner&&mob.getDistanceSq(owner)>25)mob.getNavigator().tryMoveToEntityLiving(owner,1.15D);
    }
    private static EntityLivingBase threat(EntityPlayerMP owner) {
        OwnerControl state=BY_OWNER.get(owner.getUniqueID()); long now=owner.world.getTotalWorldTime();
        if(state!=null&&state.threat!=null&&state.threatUntil>=now&&validThreat(state.threat,owner)&&owner.getDistanceSq(state.threat)<=324)return state.threat;
        EntityLivingBase result=owner.getRevengeTarget();
        if(!validThreat(result,owner)||owner.getDistanceSq(result)>324){
            result=null; List<EntityMob> mobs=owner.world.getEntitiesWithinAABB(EntityMob.class,owner.getEntityBoundingBox().grow(12));
            // Do not choose arbitrary nearby mobs: only defend against a mob that is actively
            // hostile to this controller, preventing phantom/punching-at-air targets.
            for(EntityMob mob:mobs)if(validThreat(mob,owner)&&(mob.getAttackTarget()==owner||mob.getRevengeTarget()==owner)){result=mob;break;}
        }
        return result;
    }
    private static boolean validThreat(EntityLivingBase entity, EntityPlayerMP owner) {
        return entity!=null&&!entity.isDead&&!entity.isInvisible()&&entity!=owner&&!isControlled(entity)&&!(entity instanceof net.minecraft.entity.item.EntityArmorStand)
                &&(entity instanceof EntityMob||entity instanceof EntityPlayerMP)&&(owner==null||owner.canEntityBeSeen(entity));
    }
    private static void robot(EntityPlayerMP player, EntityPlayerMP owner, Controlled control, long now) {
        // Players have no PathNavigate. Advance their authoritative server position in small,
        // collision-checked steps so they visibly walk as bodyguards instead of snapping to owner.
        EntityLivingBase threat=threat(owner);
        EntityLivingBase destination=threat!=null&&owner.getDistanceSq(threat)<=324&&player.getDistanceSq(threat)<400?threat:owner;
        double controllerDistance=Math.sqrt(player.getDistanceSq(owner));
        // Hold formation instead of continuously correcting to an anchor as the controller turns.
        // This prevents controlled players from orbiting/crowding a stationary controller.
        boolean holdFormation=destination==owner&&controllerDistance>=4D&&controllerDistance<=8D;
        double desiredX=destination.posX,desiredY=destination.getEntityBoundingBox().minY,desiredZ=destination.posZ;
        if(destination==owner) { net.minecraft.util.math.Vec3d facing=owner.getLookVec(); desiredX-=facing.x*6D; desiredZ-=facing.z*6D; }
        Ability flight=findFlight(player);double verticalDifference=desiredY-player.posY,horizontalToDestination=player.getDistance(destination);
        boolean gap=hasUnsafeGap(player,desiredX,desiredZ),flightNeeded=control.abilityAutocast&&flight!=null&&(Math.abs(verticalDifference)>3D||horizontalToDestination>20D||control.blockedTicks>=6||gap);
        control.navigationFlight=flightNeeded;
        if(flightNeeded&&!flight.isEnabled()){activate(control,flight,now,true);}
        else if(!flightNeeded&&flight!=null&&control.autoToggles.remove(flight.getKey())&&flight.isEnabled()){flight.onKeyPressed();flight.onKeyReleased();}
        boolean flying=flight!=null&&flight.isEnabled();
        if(flying)desiredY=destination.posY+(destination==owner?2.5D:Math.max(1D,destination.height*.55D));
        net.minecraft.util.math.BlockPos requested=new net.minecraft.util.math.BlockPos(desiredX,desiredY,desiredZ);
        // Near or visible targets use cheap direct walking. A* is only spent on genuinely
        // difficult, long routes where its extra reasoning pays for itself.
        boolean needsRoute=player.getDistanceSq(destination)>144D&&!player.canEntityBeSeen(destination);
        net.minecraft.util.math.BlockPos waypoint=needsRoute ? control.navigator.next(player,requested,now) : requested;
        double dx=waypoint.getX()+.5D-player.posX, dz=waypoint.getZ()+.5D-player.posZ, dy=flying?desiredY-player.posY:0D, distance=Math.sqrt(dx*dx+dz*dz),travelDistance=flying?Math.sqrt(dx*dx+dy*dy+dz*dz):distance;
        boolean sprint=destination==owner&&owner.getDistanceSq(player)>81D||destination==threat&&player.getDistanceSq(threat)>25D;
        player.setSprinting(sprint);
        if(!holdFormation&&travelDistance>.18D) {
            double speed=flying?(sprint?.48D:.32D):(sprint?.21D:.14D), step=Math.min(speed,travelDistance), mx=dx/travelDistance*step, mz=dz/travelDistance*step;
            float yaw=(float)(Math.atan2(-mx,mz)*180D/Math.PI);
            // move() performs vanilla collision resolution and uses the player's normal 0.6 step
            // height; setPlayerLocation alone would place the player inside walls or in mid-air.
            player.rotationYaw=yaw;
            // A small local movement controller: vanilla collision/step resolution, gravity,
            // jump arcs for one-block obstacles, ladder climbing, and alternate side-steps when
            // the direct line is blocked. It never places the player through geometry.
            double vertical;
            if(flying){vertical=dy/travelDistance*step;player.fallDistance=0;player.moveForward=1F;player.rotationPitch=(float)(-Math.atan2(dy,Math.max(.001D,distance))*180D/Math.PI);}
            else if(player.isOnLadder()) { vertical=destination.posY>player.posY+.25D?.15D:(destination.posY<player.posY-.25D?-.10D:0D); player.fallDistance=0; }
            else vertical=player.onGround?0D:Math.max(-.42D,player.motionY-.08D);
            double beforeX=player.posX,beforeZ=player.posZ;
            player.move(net.minecraft.entity.MoverType.SELF,mx,vertical,mz);
            if(!flying&&player.collidedHorizontally&&player.onGround&&!player.isOnLadder()) { player.motionY=.42D; control.blockedTicks++; }
            else { control.blockedTicks=0; player.motionY=flying?vertical:(player.onGround?0D:vertical*.98D); }
            if(control.blockedTicks>=4&&Math.abs(player.posX-beforeX)+Math.abs(player.posZ-beforeZ)<.01D) {
                double side=control.blockedTicks%16<8?1D:-1D;
                player.move(net.minecraft.entity.MoverType.SELF,-mz*side,0D,mx*side);
            }
            player.connection.setPlayerLocation(player.posX,player.posY,player.posZ,yaw,player.rotationPitch);
        } else if(flying){player.moveForward=0F;player.motionX=0D;player.motionZ=0D;player.motionY=0D;player.fallDistance=0;}
        Long last=LAST_ROBOT_ATTACK.get(player.getUniqueID());
        if(threat!=null&&owner.getDistanceSq(threat)<=324&&player.getDistanceSq(threat)<=16&&(last==null||now-last>=10)){
            equipBestWeapon(player); AUTOMATED_ATTACKS.add(player.getUniqueID());
            try {
                // This is Minecraft's real player attack path: weapon attributes, enchantments,
                // attack cooldown, crits and mod weapon hooks all remain authoritative.
                player.swingArm(net.minecraft.util.EnumHand.MAIN_HAND); player.attackTargetEntityWithCurrentItem(threat);
            }
            finally { AUTOMATED_ATTACKS.remove(player.getUniqueID()); }
            LAST_ROBOT_ATTACK.put(player.getUniqueID(),now);
        }
        if(now%40==0) feed(player);
    }
    private static boolean hasUnsafeGap(EntityLivingBase entity,double targetX,double targetZ){double dx=targetX-entity.posX,dz=targetZ-entity.posZ,length=Math.sqrt(dx*dx+dz*dz);if(length<3D)return false;double sample=Math.min(4D,length),x=entity.posX+dx/length*sample,z=entity.posZ+dz/length*sample;net.minecraft.util.math.BlockPos base=new net.minecraft.util.math.BlockPos(x,entity.getEntityBoundingBox().minY,z);for(int down=1;down<=4;down++)if(entity.world.getBlockState(base.down(down)).getMaterial().blocksMovement())return false;return true;}
    private static void feed(EntityPlayerMP p) { if(p.getFoodStats().getFoodLevel()>16&&p.getHealth()>p.getMaxHealth()*.5)return; for(int i=0;i<p.inventory.getSizeInventory();i++){ItemStack s=p.inventory.getStackInSlot(i);if(!s.isEmpty()&&s.getItem() instanceof ItemFood){p.getFoodStats().addStats((ItemFood)s.getItem(),s);s.shrink(1);p.heal(1);p.world.playSound(null,p.posX,p.posY,p.posZ,SoundEvents.ENTITY_GENERIC_EAT,SoundCategory.PLAYERS,.5F,1);return;}} }
    /** Chooses the hotbar stack with the highest normal main-hand attack attribute, including modded weapons. */
    private static void equipBestWeapon(EntityPlayerMP player) {
        int bestSlot=player.inventory.currentItem; double bestDamage=mainHandDamage(player.inventory.getStackInSlot(bestSlot));
        for(int slot=0;slot<9;slot++) { double damage=mainHandDamage(player.inventory.getStackInSlot(slot)); if(damage>bestDamage){bestDamage=damage;bestSlot=slot;} }
        if(player.inventory.currentItem!=bestSlot){player.inventory.currentItem=bestSlot;player.connection.sendPacket(new net.minecraft.network.play.server.SPacketHeldItemChange(bestSlot));player.inventoryContainer.detectAndSendChanges();}
    }
    private static double mainHandDamage(ItemStack stack) {
        if(stack.isEmpty())return 0D;
        Multimap<String,AttributeModifier> modifiers=stack.getAttributeModifiers(EntityEquipmentSlot.MAINHAND);
        double damage=0D;
        for(AttributeModifier modifier:modifiers.get(net.minecraft.entity.SharedMonsterAttributes.ATTACK_DAMAGE.getName()))damage+=modifier.getAmount();
        return damage;
    }
    private static boolean isControlled(Entity entity){return entity!=null&&TARGET_OWNER.containsKey(entity.getUniqueID());}
    private static boolean controlled(net.minecraft.entity.player.EntityPlayer p){return isControlled(p);}
    @SubscribeEvent public void attacked(LivingAttackEvent e){
        Entity source=e.getSource().getTrueSource(); if(!(source instanceof EntityLivingBase))return;
        // Native hostile AI may briefly reacquire its old target between our steering updates.
        // Never allow that lapse to damage its controller or a fellow controlled companion.
        UUID sourceOwner=TARGET_OWNER.get(source.getUniqueID());
        if(sourceOwner!=null&&(sourceOwner.equals(e.getEntityLiving().getUniqueID())||isControlled(e.getEntityLiving()))){
            e.setCanceled(true); if(source instanceof EntityCreature)((EntityCreature)source).setAttackTarget(null); return;
        }
        if(isControlled(source)||!validThreat((EntityLivingBase)source,e.getEntityLiving() instanceof EntityPlayerMP?(EntityPlayerMP)e.getEntityLiving():null))return;
        OwnerControl state=BY_OWNER.get(e.getEntityLiving().getUniqueID());if(state!=null){state.threat=(EntityLivingBase)source;state.threatUntil=e.getEntityLiving().world.getTotalWorldTime()+100;for(Controlled c:state.targets.values())if(c.target instanceof EntityCreature&&c.target!=source)((EntityCreature)c.target).setAttackTarget((EntityLivingBase)source);}
    }
    @SubscribeEvent public void targetChanged(net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent e) {
        if(!(e.getEntityLiving() instanceof EntityCreature)||!isControlled(e.getEntityLiving()))return;
        UUID ownerId=TARGET_OWNER.get(e.getEntityLiving().getUniqueID()); EntityLivingBase target=e.getTarget();
        if(ownerId!=null&&(ownerId.equals(target==null?null:target.getUniqueID())||isControlled(target))&&!e.getEntityLiving().world.isRemote)
            ((net.minecraft.world.WorldServer)e.getEntityLiving().world).addScheduledTask(()->((EntityCreature)e.getEntityLiving()).setAttackTarget(null));
    }
    @SubscribeEvent public void death(LivingDeathEvent e){UUID owner=TARGET_OWNER.get(e.getEntityLiving().getUniqueID());if(owner!=null)releaseInternal(owner,e.getEntityLiving().getUniqueID(),true);}
    @SubscribeEvent public void interact(PlayerInteractEvent e){
        // LeftClickEmpty is deliberately non-cancelable in Forge 1.12; attempting to cancel it
        // crashes the client. Other interaction variants remain blocked while controlled.
        if(controlled(e.getEntityPlayer())&&e.isCancelable())e.setCanceled(true);
    }
    @SubscribeEvent public void attack(AttackEntityEvent e){if(controlled(e.getEntityPlayer())&&!AUTOMATED_ATTACKS.contains(e.getEntityPlayer().getUniqueID()))e.setCanceled(true);}
    @SubscribeEvent public void breakSpeed(PlayerEvent.BreakSpeed e){if(controlled(e.getEntityPlayer()))e.setNewSpeed(0);}
    /** Reject Lucraft key packets from the victim. Bot-driven abilities call the ability instance
     * directly on the server and therefore remain available to the controller/autopilot. */
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void abilityKey(AbilityKeyEvent.Server e){if(isControlled(e.entity)&&e.isCancelable())e.setCanceled(true);}
}
