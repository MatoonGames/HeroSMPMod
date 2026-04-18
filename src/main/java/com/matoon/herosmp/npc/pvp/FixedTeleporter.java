package com.matoon.herosmp.npc.pvp;

import net.minecraft.entity.Entity;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

public class FixedTeleporter extends Teleporter {

    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public FixedTeleporter(WorldServer world, double x, double y, double z, float yaw, float pitch) {
        super(world);
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    @Override
    public void placeInPortal(Entity entityIn, float rotationYaw) {
        entityIn.setLocationAndAngles(this.x, this.y, this.z, this.yaw, this.pitch);
        entityIn.motionX = 0.0D;
        entityIn.motionY = 0.0D;
        entityIn.motionZ = 0.0D;
    }

    @Override
    public boolean placeInExistingPortal(Entity entityIn, float rotationYaw) {
        placeInPortal(entityIn, rotationYaw);
        return true;
    }

    @Override
    public boolean makePortal(Entity entityIn) {
        return true;
    }

    @Override
    public void removeStalePortalLocations(long worldTime) {
    }
}
