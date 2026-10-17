package com.matoon.herosmp.client.gui.bedrock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * UI sound cues for the store screens, all built from vanilla sounds (no resource pack needed).
 * Volumes are deliberately low: the hover tick in particular should be felt more than heard.
 */
public final class UiSounds {

    private UiSounds() {}

    /** What a click region sounds like when pressed. */
    public enum Cue { CLICK, PRIMARY, TAB, TOGGLE_ON, TOGGLE_OFF, DANGER, NONE }

    private static long lastHover;

    public static void play(Cue cue) {
        switch (cue) {
            case CLICK:      click(1.0f); break;
            case PRIMARY:    click(1.12f); play(SoundEvents.BLOCK_NOTE_HAT, 1.6f, 0.12f); break;
            case TAB:        click(1.25f); play(SoundEvents.UI_TOAST_OUT, 1.9f, 0.18f); break;
            case TOGGLE_ON:  play(SoundEvents.BLOCK_LEVER_CLICK, 0.75f, 0.22f); play(SoundEvents.BLOCK_NOTE_PLING, 1.5f, 0.16f); break;
            case TOGGLE_OFF: play(SoundEvents.BLOCK_LEVER_CLICK, 0.55f, 0.22f); play(SoundEvents.BLOCK_NOTE_PLING, 0.9f, 0.10f); break;
            case DANGER:     click(0.85f); break;
            case NONE:       break;
        }
    }

    /** Quiet tick when the pointer moves onto a new clickable thing. */
    public static void hover() {
        long now = System.currentTimeMillis();
        if (now - lastHover < 45) return;
        lastHover = now;
        play(SoundEvents.BLOCK_NOTE_HAT, 2.0f, 0.06f);
    }

    public static void dialogOpen()  { play(SoundEvents.UI_TOAST_IN, 1.35f, 0.35f); }
    public static void dialogClose() { play(SoundEvents.UI_TOAST_OUT, 1.35f, 0.30f); }
    public static void menuOpen()    { play(SoundEvents.UI_TOAST_IN, 1.9f, 0.22f); }

    public static void success() {
        play(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f + ThreadLocalRandom.current().nextFloat() * 0.25f, 0.30f);
    }

    public static void deleted() {
        play(SoundEvents.BLOCK_FIRE_EXTINGUISH, 1.6f, 0.18f);
        play(SoundEvents.ENTITY_ITEM_BREAK, 1.3f, 0.18f);
    }

    public static void warn() {
        play(SoundEvents.BLOCK_NOTE_BASS, 0.75f, 0.45f);
    }

    /** Big moments: a download or install finished. */
    public static void celebrate() {
        play(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.3f, 0.35f);
        play(SoundEvents.BLOCK_NOTE_CHIME, 1.5f, 0.30f);
    }

    // ---------------------------------------------------------------------

    private static void click(float pitch) {
        float jitter = (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.08f;
        play(SoundEvents.UI_BUTTON_CLICK, pitch + jitter, 0.25f);
    }

    private static void play(SoundEvent e, float pitch, float volume) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getSoundHandler() == null) return;
        mc.getSoundHandler().playSound(new PositionedSoundRecord(e.getSoundName(), SoundCategory.MASTER,
                volume, pitch, false, 0, ISound.AttenuationType.NONE, 0f, 0f, 0f));
    }
}
