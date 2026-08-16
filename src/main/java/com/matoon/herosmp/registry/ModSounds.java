package com.matoon.herosmp.registry;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ModSounds {

    public static SoundEvent TIME_EFFECT;
    public static SoundEvent INF_POWER_UP;
    public static SoundEvent SNAP_1;
    public static SoundEvent SNAP_2;
    public static SoundEvent SNAP_3;
    public static SoundEvent UNLOCK_POWER;

    /**
     * Called during RegistryEvent.Register<SoundEvent> to register all mod sounds.
     * The resource location must match the key in sounds.json exactly.
     */
    @SubscribeEvent
    public void onRegisterSounds(RegistryEvent.Register<SoundEvent> event) {
        TIME_EFFECT  = register(event, "time_effect");
        INF_POWER_UP = register(event, "inf_power_up");
        SNAP_1       = register(event, "snap_1");
        SNAP_2       = register(event, "snap_2");
        SNAP_3       = register(event, "snap_3");
        UNLOCK_POWER = register(event, "unlock_power");
    }

    private static SoundEvent register(RegistryEvent.Register<SoundEvent> event, String name) {
        ResourceLocation rl = new ResourceLocation("herosmp", name);
        SoundEvent sound = new SoundEvent(rl).setRegistryName(rl);
        event.getRegistry().register(sound);
        return sound;
    }
}
