package com.matoon.herosmp.client.gui;

import com.matoon.herosmp.client.gui.bedrock.BedrockScreen;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI.Style;
import com.matoon.herosmp.client.store.HeroAddons;
import com.matoon.herosmp.client.store.StoreCatalog;
import com.matoon.herosmp.client.store.StorePack;
import com.matoon.herosmp.npc.kit.KitRequirements;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * "This kit needs mods you don't have" prompt. Lists each missing mod or Hero Addon; Hero Addons
 * the store offers get an Install button (they load after a restart), mods get a note to add them
 * to the mods folder. An optional continue action (e.g. "Playtest anyway") runs the original intent.
 */
public final class MissingModsOverlay implements BedrockScreen.Overlay {

    private final BedrockScreen screen;
    private final String kitName;
    private final List<KitRequirements.Requirement> missing;
    private final String continueLabel;
    private final Runnable onContinue;

    private MissingModsOverlay(BedrockScreen screen, String kitName, List<KitRequirements.Requirement> missing,
                               String continueLabel, Runnable onContinue) {
        this.screen = screen;
        this.kitName = kitName;
        this.missing = missing;
        this.continueLabel = continueLabel;
        this.onContinue = onContinue;
    }

    /** Shows the prompt (no continue button). */
    public static void show(BedrockScreen screen, String kitName, List<KitRequirements.Requirement> missing) {
        show(screen, kitName, missing, null, null);
    }

    public static void show(BedrockScreen screen, String kitName, List<KitRequirements.Requirement> missing,
                            String continueLabel, Runnable onContinue) {
        com.matoon.herosmp.client.gui.bedrock.UiSounds.warn();
        screen.openOverlay(new MissingModsOverlay(screen, kitName, missing, continueLabel, onContinue));
    }

    /** Runs {@code action} straight away when nothing is missing, otherwise asks first. */
    public static void guard(BedrockScreen screen, String kitName, net.minecraft.nbt.NBTTagCompound kitTag,
                             String continueLabel, Runnable action) {
        List<KitRequirements.Requirement> m = KitRequirements.missing(kitTag);
        if (m.isEmpty()) action.run();
        else show(screen, kitName, m, continueLabel, action);
    }

    @Override
    public void draw(BedrockScreen s, float partialTicks) {
        BedrockUI.rect(0, 0, s.width, s.height, 0xB0000000);
        s.block(0, 0, s.width, s.height);
        int rowH = 30;
        int w = Math.min(340, s.width - 30);
        List<String> intro = s.font().listFormattedStringToWidth(
                "Some of this kit's items or its superpower come from mods you don't have, so they won't load. "
                        + "Install these, then restart Minecraft:", w - 24);
        int h = 30 + 10 + intro.size() * 10 + 8 + missing.size() * (rowH + 4) + 10 + 24 + 12;
        h = Math.min(h, s.height - 20);
        int x = (s.width - w) / 2, y = (s.height - h) / 2;

        BedrockUI.shadow(x, y, w, h);
        BedrockUI.rect(x, y, w, h, BedrockUI.OUTLINE);
        BedrockUI.rect(x + 1, y + 1, w - 2, h - 2, BedrockUI.PANEL);
        BedrockUI.rect(x + 1, y + 1, w - 2, 26, 0xFF5A2A20);
        BedrockUI.rect(x + 1, y + 27, w - 2, 1, BedrockUI.OUTLINE);
        String title = BedrockUI.trim(s.font(), "Missing mods for " + kitName, w - 24);
        s.font().drawString(title, x + (w - s.font().getStringWidth(title)) / 2, y + 10, BedrockUI.TEXT, true);

        int ty = y + 38;
        for (String line : intro) {
            s.font().drawString(line, x + 12, ty, BedrockUI.TEXT_DIM, false);
            ty += 10;
        }
        ty += 8;

        List<StorePack> store = StoreCatalog.lastPacks;
        int bottom = y + h - 12 - 24 - 6;
        BedrockUI.pushClip(x + 1, ty - 2, w - 2, Math.max(0, bottom - ty + 2));
        for (KitRequirements.Requirement r : missing) {
            BedrockUI.rect(x + 12, ty, w - 24, rowH, BedrockUI.OUTLINE);
            BedrockUI.rect(x + 13, ty + 1, w - 26, rowH - 2, BedrockUI.SURFACE);
            boolean addon = r.kind == KitRequirements.Kind.ADDON;
            final StorePack match = HeroAddons.storeMatch(r, store);
            if (addon) {
                // The addon's own logo when the store has it; its initials otherwise.
                com.matoon.herosmp.client.store.AddonMeta.Info info = match != null ? com.matoon.herosmp.client.store.AddonMeta.remote(match) : null;
                if (!PackArt.drawAddonIcon(info, x + 15, ty + 4, 22, 22, false)) {
                    if (match != null && com.matoon.herosmp.client.store.AddonMeta.loading(match)) PackArt.addonLoading(x + 15, ty + 4, 22, 22);
                    else PackArt.addonMonogram(r.name, x + 15, ty + 4, 22, 22);
                }
            } else {
                BedrockUI.item(new ItemStack(Items.BOOK), x + 18, ty + 7, 1f);
            }
            int cw = BedrockUI.chipWidth(s.font(), r.kindLabel().toUpperCase());
            BedrockUI.chip(s.font(), x + 40, ty + 4, r.kindLabel().toUpperCase(), addon ? 0xFF7A3FC0 : 0xFF4A4B4D, BedrockUI.TEXT);
            int btnW = 64;
            int textW = w - 24 - 40 - 8 - (match != null ? btnW + 8 : 0);
            s.font().drawString(BedrockUI.trim(s.font(), r.name, textW - cw - 6), x + 46 + cw, ty + 5, BedrockUI.TEXT, true);
            String note = match != null ? (HeroAddons.isInstalled(match) ? "Installed: restart Minecraft to load it" : "Available in the store")
                    : addon ? "Not in the store: get it from its author" : "Add it to your mods folder (\"" + r.id + "\")";
            s.font().drawString(BedrockUI.trim(s.font(), note, textW), x + 40, ty + 17, BedrockUI.TEXT_MUTED, false);
            if (match != null && !HeroAddons.isInstalled(match)) {
                s.button(x + w - 12 - 6 - btnW, ty + 5, btnW, 20, "Install", Style.PRIMARY, true, () -> {
                    s.closeOverlay();
                    Minecraft.getMinecraft().displayGuiScreen(new GuiDownloadProgress(screen, match, null));
                });
            }
            ty += rowH + 4;
        }
        BedrockUI.popClip();

        int by = y + h - 12 - 24;
        if (onContinue != null) {
            int bw = (w - 24 - 8) / 2;
            s.button(x + 12, by, bw, 24, "Close", Style.DARK, true, s::closeOverlay);
            s.button(x + 12 + bw + 8, by, w - 24 - bw - 8, 24, continueLabel, Style.DANGER, true, () -> {
                s.closeOverlay();
                onContinue.run();
            });
        } else {
            s.button(x + (w - 120) / 2, by, 120, 24, "OK", Style.PRIMARY, true, s::closeOverlay);
        }
    }
}
