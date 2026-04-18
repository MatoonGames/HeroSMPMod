package com.matoon.herosmp.npc;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldSavedData;

public class PvpSettingsSavedData extends WorldSavedData {

    public static final String DATA_NAME = "herosmp_pvp_settings";
    private static final int DEFAULT_ROUND_SECONDS = 300;

    private int roundDurationSeconds = DEFAULT_ROUND_SECONDS;

    public PvpSettingsSavedData() {
        super(DATA_NAME);
    }

    public PvpSettingsSavedData(String name) {
        super(name);
    }

    public int getRoundDurationSeconds() {
        return roundDurationSeconds;
    }

    public void setRoundDurationSeconds(int roundDurationSeconds) {
        this.roundDurationSeconds = Math.max(30, Math.min(1800, roundDurationSeconds));
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        if (nbt.hasKey("RoundDurationSeconds")) {
            this.roundDurationSeconds = Math.max(30, Math.min(1800, nbt.getInteger("RoundDurationSeconds")));
        } else {
            this.roundDurationSeconds = DEFAULT_ROUND_SECONDS;
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        compound.setInteger("RoundDurationSeconds", roundDurationSeconds);
        return compound;
    }

    public static PvpSettingsSavedData get(MinecraftServer server) {
        WorldServer overworld = server.getWorld(0);
        if (overworld == null) {
            return new PvpSettingsSavedData();
        }
        PvpSettingsSavedData data = (PvpSettingsSavedData) overworld.getPerWorldStorage().getOrLoadData(PvpSettingsSavedData.class, DATA_NAME);
        if (data == null) {
            data = new PvpSettingsSavedData();
            overworld.getPerWorldStorage().setData(DATA_NAME, data);
        }
        return data;
    }
}
