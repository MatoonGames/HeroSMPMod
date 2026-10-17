package com.matoon.herosmp.client.gui;

import com.matoon.herosmp.client.gui.bedrock.BedrockScreen;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI.Style;
import com.matoon.herosmp.client.store.PlaytestLauncher;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;

/** Confirm-then-launch for map and kit playtests, shared by the store screens and the kit editor. */
public final class Playtest {

    private Playtest() {}

    private static String leaving() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) return "";
        if (PlaytestLauncher.isInPlaytest()) return " The current playtest ends and a fresh test world is made.";
        return mc.getIntegratedServer() != null
                ? " You'll leave the world you're in (it's saved first)."
                : " You'll disconnect from this server.";
    }

    /** A playtest world is already closing or loading: say so instead of stacking another launch. */
    private static boolean busy(BedrockScreen screen) {
        if (!PlaytestLauncher.isStarting()) return false;
        screen.toastWarn("A playtest is already starting");
        return true;
    }

    public static void map(BedrockScreen screen, final String mapName) {
        if (busy(screen)) return;
        screen.confirm("Playtest " + mapName + "?",
                "Opens a temporary test world and starts a solo Hunger Games match on " + mapName + "."
                        + leaving() + " Save and Quit when you're done to come back here.",
                "Start Playtest", Style.PRIMARY, () -> PlaytestLauncher.playtestMap(mapName));
    }

    public static void kit(BedrockScreen screen, final NBTTagCompound kitTag, final String kitName) {
        if (busy(screen)) return;
        screen.confirm("Playtest " + kitName + "?",
                "Opens a temporary test world with the kit equipped, test dummies that show the damage you deal, "
                        + "and a button that starts a solo PVP match." + leaving()
                        + " Save and Quit when you're done to come back here.",
                "Start Playtest", Style.PRIMARY, () -> PlaytestLauncher.playtestKit(kitTag, kitName));
    }
}
