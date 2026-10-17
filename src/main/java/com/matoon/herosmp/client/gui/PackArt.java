package com.matoon.herosmp.client.gui;

import com.matoon.herosmp.client.gui.bedrock.BedrockUI;
import com.matoon.herosmp.client.store.PackThumbnails;
import com.matoon.herosmp.client.store.StorePack;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/** Resolves and draws a pack's artwork, falling back to a styled placeholder. */
final class PackArt {

    private PackArt() {}

    private static final ItemStack MAP_ICON = new ItemStack(Blocks.GRASS);
    private static final ItemStack KIT_ICON = new ItemStack(Items.DIAMOND_SWORD);

    /** Installed art wins (works offline); otherwise the store's Drive art, fetched lazily. */
    static PackThumbnails.Thumb thumb(StorePack pack, boolean installed) {
        if (pack.type != StorePack.PackType.MAP) return null;
        if (installed) {
            PackThumbnails.Thumb local = PackThumbnails.installedMap(pack.displayName);
            if (local != null) return local;
        }
        if (pack.isFolder && pack.driveId != null && !pack.driveId.isEmpty()) {
            return PackThumbnails.remote(pack.displayName, pack.driveId);
        }
        return null;
    }

    /** Fills the box with the pack's art (cover-cropped) or a placeholder. */
    static void draw(StorePack pack, boolean installed, int x, int y, int w, int h) {
        draw(pack, installed, x, y, w, h, false);
    }

    /**
     * Same, hover-aware: kit packs show the kit's own figure (armor + weapon, glow behind), which
     * swings while hovered, once the pack's kit file has been fetched.
     */
    /** Store name: the Drive name, with a Hero Addon's file name tidied ("Neons+Villains+1.3.zip" -> "Neons Villains 1.3"). */
    static String title(StorePack pack) {
        return pack.displayName;
    }

    /** Line under the name on tiles: the type, plus a Hero Addon's author. */
    static String subtitle(StorePack pack) {
        if (pack.type == StorePack.PackType.ADDON) {
            com.matoon.herosmp.client.store.AddonMeta.Info i = com.matoon.herosmp.client.store.AddonMeta.remote(pack);
            if (i != null && !i.author.isEmpty()) return "Hero Addon  ·  by " + i.author;
        }
        return pack.typeLabel();
    }

    /**
     * A Hero Addon's art: its logo (contain-fit) on a gradient made from the logo's own colours, with
     * a soft glow of its main colour behind. Returns false if there's no icon to draw.
     */
    static boolean drawAddonIcon(com.matoon.herosmp.client.store.AddonMeta.Info info, int x, int y, int w, int h, boolean hovered) {
        PackThumbnails.Thumb icon = info == null ? null : info.icon();
        if (icon == null) return false;
        addonBackdrop(info.primary, info.secondary, x, y, w, h);
        float pulse = hovered ? 1f : 0.75f + 0.1f * (float) Math.sin(System.currentTimeMillis() / 700.0);
        glow(info.primary, x + w / 2f, y + h / 2f, Math.min(w, h) * 0.6f, pulse * 0.55f);
        float box = Math.min(w, h) * (hovered ? 0.80f : 0.74f);
        float sc = box / Math.max(icon.w, icon.h);
        float iw = icon.w * sc, ih = icon.h * sc;
        net.minecraft.client.Minecraft.getMinecraft().getTextureManager().bindTexture(icon.rl);
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        BedrockUI.texQuad(x + (w - iw) / 2f, y + (h - ih) / 2f, iw, ih, 0, 0, 1, 1);
        net.minecraft.client.renderer.GlStateManager.disableBlend();
        return true;
    }

    /** Diagonal gradient from the logo's main colour to its second one, darkened so the logo stands out. */
    static void addonBackdrop(int primary, int secondary, int x, int y, int w, int h) {
        int top = mix(primary, 0xFF000000, 0.30f), bottom = mix(secondary, 0xFF000000, 0.62f);
        BedrockUI.gradient(x, y, w, h, top, mix(top, bottom, 0.5f), mix(top, bottom, 0.5f), bottom);
        BedrockUI.gradient(x, y, w, h, 0x26FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x00000000);
    }

    /** Still fetching the pack's logo: a shimmering card with loading dots. */
    static void addonLoading(int x, int y, int w, int h) {
        BedrockUI.skeleton(x, y, w, h);
        BedrockUI.loadingDots(x + w / 2, y + h / 2);
    }

    /** The pack has no readable logo: its initials on a neutral card. */
    static void addonMonogram(String name, int x, int y, int w, int h) {
        BedrockUI.vGradient(x, y, w, h, 0xFF3A3B40, 0xFF1C1D20);
        StringBuilder in = new StringBuilder();
        for (String word : name.split("[^A-Za-z0-9]+")) {
            if (!word.isEmpty() && Character.isLetter(word.charAt(0))) in.append(Character.toUpperCase(word.charAt(0)));
            if (in.length() == 2) break;
        }
        String t = in.length() == 0 ? "?" : in.toString();
        net.minecraft.client.gui.FontRenderer fr = net.minecraft.client.Minecraft.getMinecraft().fontRenderer;
        float sc = Math.max(1.5f, Math.min(4f, Math.min(w, h) / 22f));
        BedrockUI.text(fr, t, x + (w - fr.getStringWidth(t) * sc) / 2f, y + (h - 8 * sc) / 2f, 0xFFD8D9DE, sc, true);
    }

    /** Additive round glow tinted with {@code rgb}. */
    static void glow(int rgb, float cx, float cy, float r, float strength) {
        int cr = (rgb >> 16) & 0xFF, cg = (rgb >> 8) & 0xFF, cb = rgb & 0xFF;
        int core = Math.round(150 * strength);
        net.minecraft.client.renderer.GlStateManager.disableTexture2D();
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE);
        net.minecraft.client.renderer.GlStateManager.shadeModel(org.lwjgl.opengl.GL11.GL_SMOOTH);
        net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.BufferBuilder b = tess.getBuffer();
        b.begin(org.lwjgl.opengl.GL11.GL_TRIANGLE_FAN, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
        b.pos(cx, cy, 0).color(Math.min(255, cr + 60), Math.min(255, cg + 60), Math.min(255, cb + 60), core).endVertex();
        for (int i = 0; i <= 40; i++) {
            double a = i * Math.PI * 2 / 40;
            b.pos(cx + Math.cos(a) * r, cy + Math.sin(a) * r, 0).color(cr, cg, cb, 0).endVertex();
        }
        tess.draw();
        net.minecraft.client.renderer.GlStateManager.shadeModel(org.lwjgl.opengl.GL11.GL_FLAT);
        net.minecraft.client.renderer.GlStateManager.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
        net.minecraft.client.renderer.GlStateManager.disableBlend();
        net.minecraft.client.renderer.GlStateManager.enableTexture2D();
    }

    static int mix(int a, int b, float f) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | ((int) (ar + (br - ar) * f) << 16) | ((int) (ag + (bg - ag) * f) << 8) | (int) (ab + (bb - ab) * f);
    }

    static void draw(StorePack pack, boolean installed, int x, int y, int w, int h, boolean hovered) {
        if (pack.type == StorePack.PackType.ADDON) {
            com.matoon.herosmp.client.store.AddonMeta.Info i = com.matoon.herosmp.client.store.AddonMeta.remote(pack);
            if (drawAddonIcon(i, x, y, w, h, hovered)) return;
            if (i == null && com.matoon.herosmp.client.store.AddonMeta.loading(pack)) addonLoading(x, y, w, h);
            else addonMonogram(pack.displayName, x, y, w, h);
            return;
        }
        if (pack.type == StorePack.PackType.KIT && pack.driveId != null && !pack.driveId.isEmpty()) {
            net.minecraft.nbt.NBTTagCompound kit = com.matoon.herosmp.client.store.PackMeta.remoteKitTag(pack.driveId);
            if (kit != null) {
                BedrockUI.vGradient(x, y, w, h, 0xFF2C2D31, 0xFF141517);
                try {
                    KitAvatar.draw(KitAvatar.of(kit), "pack:" + pack.driveId, x, y, w, h, hovered);
                    return;
                } catch (RuntimeException e) {
                    // Unreadable kit: fall through to the placeholder.
                }
            }
        }
        PackThumbnails.Thumb t = thumb(pack, installed);
        if (t != null) {
            BedrockUI.imageCover(t, x, y, w, h, 1f, false);
        } else {
            placeholder(pack.type, x, y, w, h);
        }
    }

    static void placeholder(StorePack.PackType type, int x, int y, int w, int h) {
        boolean map = type == StorePack.PackType.MAP;
        if (type == StorePack.PackType.ADDON) { addonLoading(x, y, w, h); return; }
        BedrockUI.vGradient(x, y, w, h, map ? 0xFF3E7A32 : 0xFF7A3232, map ? 0xFF16301A : 0xFF2E1414);
        // Subtle diagonal light for depth.
        BedrockUI.gradient(x, y, w, h, 0x26FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x00000000);

        float scale = Math.max(1f, Math.min(4f, (float) Math.floor(Math.min(w, h) / 30f)));
        int size = (int) (16 * scale);
        BedrockUI.item(map ? MAP_ICON : KIT_ICON, x + (w - size) / 2f, y + (h - size) / 2f, scale);
    }
}
