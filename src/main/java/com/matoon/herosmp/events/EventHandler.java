package com.matoon.herosmp.events;

import com.matoon.herosmp.client.CustomDisconnectedScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundEventAccessor;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

// Add @SideOnly to ensure it's client-side only
@SideOnly(Side.CLIENT)
public class EventHandler {

    // Cached reflection access to PlaySoundEvent's private final 'sound' field.
    private static Field playSoundEventSoundField;
    private static boolean soundFieldResolved = false;

    private static Field getPlaySoundEventSoundField() {
        if (!soundFieldResolved) {
            soundFieldResolved = true;
            try {
                Field f = PlaySoundEvent.class.getDeclaredField("sound");
                f.setAccessible(true);
                // Strip the final modifier so we can write to it.
                Field modifiers = Field.class.getDeclaredField("modifiers");
                modifiers.setAccessible(true);
                modifiers.setInt(f, f.getModifiers() & ~Modifier.FINAL);
                playSoundEventSoundField = f;
            } catch (Exception e) {
                System.err.println("[HeroSMP] Could not access PlaySoundEvent.sound field: " + e);
            }
        }
        return playSoundEventSoundField;
    }

    /**
     * AbilityBlindness$Renderer (HeroesExpansion) subscribes to PlaySoundEvent and
     * unconditionally passes event.getSound() into new EntitySound(world, sound).
     * EntitySound calls sound.createAccessor(soundHandler) and then calls
     * accessor.getSubtitle() without null-checking the accessor first.
     * createAccessor() returns null when the sound's ResourceLocation is not
     * registered in the SoundHandler (e.g. custom music added by a resource pack
     * that hasn't been reloaded yet). This causes an NPE that crashes the client.
     *
     * Fix: at HIGHEST priority, check whether createAccessor() would return null for
     * the incoming sound. If so, replace event.getSound() (via reflection) with a
     * lightweight ISound proxy whose createAccessor() always returns null safely —
     * but more importantly, EntitySound's constructor will see a non-null world and
     * can skip the getSubtitle() call... actually we just need createAccessor to not
     * return null. We wrap the ISound so createAccessor returns a dummy accessor
     * whose getSubtitle() returns null (EntitySound already null-checks getSubtitle).
     *
     * The 'sound' field is private final, so we strip the final modifier at runtime
     * via reflection to allow the swap.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    @SideOnly(Side.CLIENT)
    public void onPlaySound(PlaySoundEvent event) {
        ISound sound = event.getSound();
        if (sound == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getSoundHandler() == null) return;

        // Check if createAccessor would return null for this sound.
        SoundEventAccessor accessor = sound.createAccessor(mc.getSoundHandler());
        if (accessor != null) return; // Safe — no intervention needed.

        // The sound's ResourceLocation is not registered in the SoundHandler.
        // AbilityBlindness$Renderer will crash when it tries to build an EntitySound
        // from this ISound. Wrap it in a proxy whose createAccessor() returns a
        // dummy non-null accessor, so EntitySound's constructor survives.
        Field f = getPlaySoundEventSoundField();
        if (f != null) {
            try {
                f.set(event, new SafeISound(sound));
            } catch (Exception e) {
                System.err.println("[HeroSMP] Could not replace ISound in PlaySoundEvent: " + e);
            }
        }
    }

    /**
     * Thin ISound proxy that delegates everything to the wrapped sound but returns
     * a dummy (non-null) SoundEventAccessor from createAccessor(). This is enough
     * to prevent EntitySound from NPE-ing on accessor.getSubtitle() because
     * EntitySound already null-checks getSubtitle() before calling getFormattedText().
     */
    @SideOnly(Side.CLIENT)
    private static class SafeISound implements ISound {
        private final ISound delegate;

        SafeISound(ISound delegate) { this.delegate = delegate; }

        @Override
        public SoundEventAccessor createAccessor(SoundHandler handler) {
            // Return a dummy accessor so EntitySound never receives null here.
            // EntitySound already null-checks getSubtitle(), so returning null
            // from getSubtitle() is safe.
            return new SoundEventAccessor(delegate.getSoundLocation(), null);
        }

        @Override public ResourceLocation getSoundLocation() { return delegate.getSoundLocation(); }
        @Override public net.minecraft.client.audio.Sound getSound() { return delegate.getSound(); }
        @Override public SoundCategory getCategory() { return delegate.getCategory(); }
        @Override public boolean canRepeat() { return delegate.canRepeat(); }
        @Override public int getRepeatDelay() { return delegate.getRepeatDelay(); }
        @Override public float getVolume() { return delegate.getVolume(); }
        @Override public float getPitch() { return delegate.getPitch(); }
        @Override public float getXPosF() { return delegate.getXPosF(); }
        @Override public float getYPosF() { return delegate.getYPosF(); }
        @Override public float getZPosF() { return delegate.getZPosF(); }
        @Override public AttenuationType getAttenuationType() { return delegate.getAttenuationType(); }
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT) // Ensure this only runs client-side
    public void onClientDisconnect(GuiOpenEvent event) {
        GuiScreen openedGui = event.getGui();

        if (openedGui == null || openedGui instanceof CustomDisconnectedScreen) {
            return;
        }

        String guiName = openedGui.getClass().getName();
        boolean disconnectGui = openedGui instanceof GuiDisconnected
                || guiName.toLowerCase().contains("guidisconnected");

        if (!disconnectGui) {
            return;
        }

        System.out.println("Detected disconnect GUI: " + guiName);

        ITextComponent message = extractDisconnectMessage(openedGui);
        if (message == null) {
            message = new TextComponentString("Disconnected from the server.");
        }

        event.setGui(new CustomDisconnectedScreen(openedGui, message));
        System.out.println("CustomDisconnectedScreen set after disconnection");
    }

    private ITextComponent extractDisconnectMessage(GuiScreen gui) {
        String[] preferredFieldNames = new String[]{"message", "field_146304_f"};

        for (String fieldName : preferredFieldNames) {
            try {
                Field field = gui.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(gui);
                if (value instanceof ITextComponent) {
                    return (ITextComponent) value;
                }
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                // Continue with fallback search
            }
        }

        // Fallback: find any ITextComponent field on this screen class
        for (Field field : gui.getClass().getDeclaredFields()) {
            if (ITextComponent.class.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(gui);
                    if (value instanceof ITextComponent) {
                        return (ITextComponent) value;
                    }
                } catch (IllegalAccessException ignored) {
                    // Keep searching
                }
            }
        }

        return null;
    }
}
