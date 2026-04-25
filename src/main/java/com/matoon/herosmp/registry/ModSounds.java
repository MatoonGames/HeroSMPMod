package com.matoon.herosmp.registry;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ModSounds {

    public static SoundEvent TIME_EFFECT;

    /**
     * Called during RegistryEvent.Register<SoundEvent> to register all mod sounds.
     * The resource location must match the key in sounds.json exactly.
     */
    @SubscribeEvent
    public void onRegisterSounds(RegistryEvent.Register<SoundEvent> event) {
        TIME_EFFECT = register(event, "time_effect");
    }

    private static SoundEvent register(RegistryEvent.Register<SoundEvent> event, String name) {
        ResourceLocation rl = new ResourceLocation("herosmp", name);
        SoundEvent sound = new SoundEvent(rl).setRegistryName(rl);
        event.getRegistry().register(sound);
        return sound;
    }
}
