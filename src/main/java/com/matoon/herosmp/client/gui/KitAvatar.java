package com.matoon.herosmp.client.gui;

import com.matoon.herosmp.client.gui.bedrock.BedrockUI;
import com.matoon.herosmp.npc.kit.KitDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.ForgeHooksClient;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A kit's "icon": a little Steve wearing the kit's armor and holding its weapon (and offhand item),
 * standing in front of a soft glow. Hovering makes him swing. Also renders a low-resolution
 * snapshot of the same figure for kit card files.
 *
 * There's no player entity at the main menu, so this poses Steve's model directly with vanilla's own
 * animation code ({@code ModelBiped.setRotationAngles}, which tolerates a missing entity) and
 * replicates vanilla's armor and held-item layers. Any item a mod renders badly is skipped safely.
 */
public final class KitAvatar {

    private KitAvatar() {}

    private static final ResourceLocation STEVE = DefaultPlayerSkin.getDefaultSkinLegacy();
    private static final ModelBiped BODY = new ModelBiped(0f, 0f, 64, 64);
    private static final ModelBiped ARMOR_OUTER = new ModelBiped(1.0f);
    private static final ModelBiped ARMOR_INNER = new ModelBiped(0.5f);
    private static final Map<String, ResourceLocation> ARMOR_TEXTURES = new HashMap<String, ResourceLocation>();

    private static final long SWING_MS = 320L, SWING_EVERY_MS = 1100L;
    /** Head-top to feet in blocks: the biped model is 32px (2 blocks) tall, drawn at the player scale 0.9375. */
    private static final float MODEL_HEIGHT = 2.0f * 0.9375f;

    // ---------------------------------------------------------------------
    // What the figure wears
    // ---------------------------------------------------------------------

    /** The six things the figure shows. Built from a kit's 41 slots. */
    public static final class Loadout {
        final ItemStack head, chest, legs, feet, main, off;

        private Loadout(ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet, ItemStack main, ItemStack off) {
            this.head = head;
            this.chest = chest;
            this.legs = legs;
            this.feet = feet;
            this.main = main;
            this.off = off;
        }

        /** KitDefinition slot layout: 0-8 hotbar, 9-35 main, 36 feet, 37 legs, 38 chest, 39 head, 40 offhand. */
        public static Loadout of(ItemStack[] slots) {
            return new Loadout(get(slots, 39), get(slots, 38), get(slots, 37), get(slots, 36), weapon(slots), get(slots, 40));
        }

        /** From the six worn/held stacks directly: head, chest, legs, feet, main hand, off hand. */
        public static Loadout ofGear(ItemStack[] g) {
            return new Loadout(get(g, 0), get(g, 1), get(g, 2), get(g, 3), get(g, 4), get(g, 5));
        }

        /** Nothing worn or held. */
        public static final Loadout EMPTY = new Loadout(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);

        public static Loadout of(KitDefinition kit) {
            NonNullList<ItemStack> c = kit.getContentsCopy();
            return of(c.toArray(new ItemStack[0]));
        }

        private static ItemStack get(ItemStack[] s, int i) {
            return i < s.length && s[i] != null ? s[i] : ItemStack.EMPTY;
        }

        /** The hotbar weapon the kit is built around (else the first hotbar item). */
        private static ItemStack weapon(ItemStack[] s) {
            for (int i = 0; i < 9 && i < s.length; i++) {
                Item it = get(s, i).getItem();
                if (it instanceof ItemSword) return s[i];
            }
            for (int i = 0; i < 9 && i < s.length; i++) {
                Item it = get(s, i).getItem();
                if (it instanceof ItemAxe || it instanceof ItemBow || it instanceof ItemTool) return s[i];
            }
            for (int i = 0; i < 9 && i < s.length; i++) if (!get(s, i).isEmpty()) return s[i];
            return ItemStack.EMPTY;
        }
    }

    private static final Map<NBTTagCompound, Loadout> TAG_CACHE = new WeakHashMap<NBTTagCompound, Loadout>();

    /** Loadout for a kit tag (KitDefinition format), cached per tag object. */
    public static Loadout of(NBTTagCompound kitTag) {
        Loadout l = TAG_CACHE.get(kitTag);
        if (l == null) {
            l = Loadout.of(KitDefinition.deserialize(kitTag));
            TAG_CACHE.put(kitTag, l);
        }
        return l;
    }

    // ---------------------------------------------------------------------
    // Icon (glow + animated figure)
    // ---------------------------------------------------------------------

    private static final class Anim {
        boolean hovered;
        long swingStart = -1;
    }

    private static final Map<String, Anim> ANIMS = new HashMap<String, Anim>();

    /**
     * Draws the kit icon into a box: glow behind, figure in front. {@code key} identifies the icon
     * so its swing animation is tracked across frames; hovering starts a swing (and repeats it).
     */
    public static void draw(Loadout l, String key, int x, int y, int w, int h, boolean hovered) {
        long now = System.currentTimeMillis();
        Anim a = ANIMS.get(key);
        if (a == null) { a = new Anim(); ANIMS.put(key, a); }
        if (hovered && (!a.hovered || now - a.swingStart > SWING_EVERY_MS)) a.swingStart = now;
        a.hovered = hovered;
        float swing = 0f;
        if (a.swingStart >= 0 && now - a.swingStart < SWING_MS) swing = (now - a.swingStart) / (float) SWING_MS;

        BedrockUI.pushClip(x, y, w, h);
        try {
            float pulse = 0.85f + 0.15f * (float) Math.sin(now / 700.0);
            glow(x + w / 2f, y + h * 0.52f, Math.min(w, h) * 0.62f, hovered ? 1f : 0.75f * pulse);

            // Model is 2 blocks tall head-to-feet (x0.9375 player scale); leave headroom for helmets.
            float figureH = h * 0.80f;
            float scale = figureH / MODEL_HEIGHT;
            float age = (now % 100000L) / 50f; // in ticks, for the idle arm sway
            render(l, x + w / 2f, y + h * 0.91f, scale, swing, age, hovered ? -12f : -24f);
        } finally {
            // Always unwind, or every row drawn after a failing figure would be clipped away.
            BedrockUI.popClip();
        }
    }

    /** Pixels per block that make the figure {@code figureH} pixels tall (head to feet). */
    public static float scaleForHeight(float figureH) {
        return figureH / MODEL_HEIGHT;
    }

    /** Soft round glow (additive), centred on (cx, cy). */
    public static void glow(float cx, float cy, float r, float strength) {
        int core = Math.round(150 * strength), mid = Math.round(60 * strength);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        // Inner disc: warm white core fading to gold.
        b.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        b.pos(cx, cy, 0).color(255, 236, 170, core).endVertex();
        for (int i = 0; i <= 40; i++) {
            double t = i * Math.PI * 2 / 40;
            b.pos(cx + Math.cos(t) * r * 0.45, cy + Math.sin(t) * r * 0.45, 0).color(255, 196, 80, mid).endVertex();
        }
        tess.draw();
        // Outer ring: gold fading to nothing.
        b.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= 40; i++) {
            double t = i * Math.PI * 2 / 40;
            b.pos(cx + Math.cos(t) * r * 0.45, cy + Math.sin(t) * r * 0.45, 0).color(255, 196, 80, mid).endVertex();
            b.pos(cx + Math.cos(t) * r, cy + Math.sin(t) * r, 0).color(255, 170, 40, 0).endVertex();
        }
        tess.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    // ---------------------------------------------------------------------
    // The figure
    // ---------------------------------------------------------------------

    /**
     * Renders the figure with its feet at (cx, feetY). {@code scale} is pixels per block,
     * {@code swing} 0..1 the attack swing progress, {@code yaw} the body turn (0 = facing the viewer).
     */
    public static void render(Loadout l, float cx, float feetY, float scale, float swing, float age, float yaw) {
        render(l, cx, feetY, scale, swing, age, yaw, null);
    }

    /** As above, wearing {@code skin} (a player skin texture; null = Steve). */
    public static void render(Loadout l, float cx, float feetY, float scale, float swing, float age, float yaw,
                              ResourceLocation skin) {
        Minecraft mc = Minecraft.getMinecraft();
        BedrockUI.clearDepth();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableColorMaterial();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.pushMatrix();
        // Same setup as the inventory screen's player preview.
        GlStateManager.translate(cx, feetY, 50f);
        GlStateManager.scale(-scale, scale, scale);
        GlStateManager.rotate(180f, 0f, 0f, 1f);
        GlStateManager.rotate(135f, 0f, 1f, 0f);
        RenderHelper.enableStandardItemLighting();
        GlStateManager.rotate(-135f, 0f, 1f, 0f);
        // RenderLivingBase turns the body 180 degrees before drawing; without it we would see his back.
        GlStateManager.rotate(180f + yaw, 0f, 1f, 0f);
        // Same as RenderLivingBase/RenderPlayer before drawing the model.
        GlStateManager.enableRescaleNormal();
        GlStateManager.scale(-1f, -1f, 1f);
        GlStateManager.scale(0.9375f, 0.9375f, 0.9375f);
        GlStateManager.translate(0f, -1.501f, 0f);

        // Stack depth inside our pushMatrix, so a failure can unwind exactly to it.
        int depth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
        try {
            pose(BODY, l, swing, age);
            mc.getTextureManager().bindTexture(skin != null ? skin : STEVE);
            renderParts(BODY, true, true, true, true);

            armor(l.legs, EntityEquipmentSlot.LEGS, l, swing, age);
            armor(l.feet, EntityEquipmentSlot.FEET, l, swing, age);
            armor(l.chest, EntityEquipmentSlot.CHEST, l, swing, age);
            armor(l.head, EntityEquipmentSlot.HEAD, l, swing, age);

            held(l.main, EnumHandSide.RIGHT);
            held(l.off, EnumHandSide.LEFT);
        } catch (RuntimeException e) {
            BedrockUI.recoverRenderState(depth);
        } finally {
            GlStateManager.disableRescaleNormal();
            GlStateManager.popMatrix();
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableColorMaterial();
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.color(1f, 1f, 1f, 1f);
        BedrockUI.clearDepth();
    }

    private static void pose(ModelBiped m, Loadout l, float swing, float age) {
        m.swingProgress = swing;
        m.isSneak = false;
        m.isChild = false;
        m.rightArmPose = l.main.isEmpty() ? ModelBiped.ArmPose.EMPTY : ModelBiped.ArmPose.ITEM;
        m.leftArmPose = l.off.isEmpty() ? ModelBiped.ArmPose.EMPTY : ModelBiped.ArmPose.ITEM;
        // No entity: vanilla's posing code only touches it behind instanceof checks.
        m.setRotationAngles(0f, 0f, age, 0f, 0f, 0.0625f, null);
    }

    private static void renderParts(ModelBiped m, boolean head, boolean body, boolean arms, boolean legs) {
        float s = 0.0625f;
        if (head) { m.bipedHead.render(s); m.bipedHeadwear.render(s); }
        if (body) m.bipedBody.render(s);
        if (arms) { m.bipedRightArm.render(s); m.bipedLeftArm.render(s); }
        if (legs) { m.bipedRightLeg.render(s); m.bipedLeftLeg.render(s); }
    }

    // ---------------------------------------------------------------------
    // Armor (mirrors LayerBipedArmor)
    // ---------------------------------------------------------------------

    private static void armor(final ItemStack stack, final EntityEquipmentSlot slot, final Loadout l,
                              final float swing, final float age) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemArmor)) return;
        final ItemArmor item = (ItemArmor) stack.getItem();
        if (item.armorType != slot) return;
        BedrockUI.safely(item, () -> {
            ModelBiped base = slot == EntityEquipmentSlot.LEGS ? ARMOR_INNER : ARMOR_OUTER;
            ModelBiped model = base;
            try {
                ModelBiped custom = ForgeHooksClient.getArmorModel(null, stack, slot, base);
                if (custom != null) model = custom;
            } catch (RuntimeException ignored) {
                model = base; // custom models that need a real player fall back to the vanilla shape
            }
            model.setModelAttributes(BODY);
            pose(model, l, swing, age);
            boolean head = slot == EntityEquipmentSlot.HEAD;
            boolean chest = slot == EntityEquipmentSlot.CHEST;
            boolean legsSlot = slot == EntityEquipmentSlot.LEGS;
            boolean feet = slot == EntityEquipmentSlot.FEET;

            Minecraft mc = Minecraft.getMinecraft();
            mc.getTextureManager().bindTexture(armorTexture(stack, slot, null));
            if (item.hasColor(stack)) {
                int c = item.getColor(stack);
                GlStateManager.color(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, 1f);
            }
            renderParts(model, head, chest || legsSlot, chest, legsSlot || feet);
            if (item.hasOverlay(stack)) {
                GlStateManager.color(1f, 1f, 1f, 1f);
                mc.getTextureManager().bindTexture(armorTexture(stack, slot, "overlay"));
                renderParts(model, head, chest || legsSlot, chest, legsSlot || feet);
            }
            GlStateManager.color(1f, 1f, 1f, 1f);
        });
    }

    /** Same path rules as LayerArmorBase.getArmorResource (including Forge's per-item override). */
    private static ResourceLocation armorTexture(ItemStack stack, EntityEquipmentSlot slot, String type) {
        ItemArmor item = (ItemArmor) stack.getItem();
        String texture = item.getArmorMaterial().getName();
        String domain = "minecraft";
        int idx = texture.indexOf(':');
        if (idx != -1) {
            domain = texture.substring(0, idx);
            texture = texture.substring(idx + 1);
        }
        String path = String.format("%s:textures/models/armor/%s_layer_%d%s.png", domain, texture,
                slot == EntityEquipmentSlot.LEGS ? 2 : 1, type == null ? "" : "_" + type);
        try {
            path = ForgeHooksClient.getArmorTexture(null, stack, path, slot, type);
        } catch (RuntimeException ignored) {
            // Items that need a real wearer keep the default path.
        }
        ResourceLocation rl = ARMOR_TEXTURES.get(path);
        if (rl == null) {
            rl = new ResourceLocation(path);
            ARMOR_TEXTURES.put(path, rl);
        }
        return rl;
    }

    // ---------------------------------------------------------------------
    // Held items (mirrors LayerHeldItem)
    // ---------------------------------------------------------------------

    private static void held(final ItemStack stack, final EnumHandSide side) {
        if (stack.isEmpty()) return;
        BedrockUI.safely(stack.getItem(), () -> {
            GlStateManager.pushMatrix();
            BODY.postRenderArm(0.0625f, side);
            GlStateManager.rotate(-90f, 1f, 0f, 0f);
            GlStateManager.rotate(180f, 0f, 1f, 0f);
            boolean left = side == EnumHandSide.LEFT;
            GlStateManager.translate((left ? -1 : 1) / 16f, 0.125f, -0.625f);
            Minecraft.getMinecraft().getRenderItem().renderItem(stack, left
                    ? ItemCameraTransforms.TransformType.THIRD_PERSON_LEFT_HAND
                    : ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND);
            GlStateManager.popMatrix();
        });
    }

    // ---------------------------------------------------------------------
    // Low-resolution snapshot (kit card picture)
    // ---------------------------------------------------------------------

    /** Size the figure is rendered at before being blown up: deliberately chunky. */
    private static final int SNAP_W = 40, SNAP_H = 52, SNAP_SCALE = 6;

    /**
     * Renders the figure off-screen at low resolution on a dark card with the glow, and returns it
     * enlarged with hard pixel edges as PNG bytes. Client thread only; null if the GPU can't render
     * off-screen.
     */
    public static byte[] snapshotPng(Loadout l) {
        if (!OpenGlHelper.isFramebufferEnabled()) return null;
        Minecraft mc = Minecraft.getMinecraft();
        Framebuffer fb = new Framebuffer(SNAP_W, SNAP_H, true);
        try {
            fb.setFramebufferColor(0f, 0f, 0f, 0f);
            fb.framebufferClear();
            fb.bindFramebuffer(true);

            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.ortho(0, SNAP_W, SNAP_H, 0, 1000, 3000);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.translate(0f, 0f, -2000f);

            BedrockUI.vGradient(0, 0, SNAP_W, SNAP_H, 0xFF2A2B2E, 0xFF151618);
            glow(SNAP_W / 2f, SNAP_H * 0.5f, SNAP_W * 0.62f, 0.95f);
            float scale = (SNAP_H * 0.80f) / MODEL_HEIGHT;
            render(l, SNAP_W / 2f, SNAP_H * 0.91f, scale, 0f, 0f, -24f);

            ByteBuffer buf = BufferUtils.createByteBuffer(SNAP_W * SNAP_H * 4);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glReadPixels(0, 0, SNAP_W, SNAP_H, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);

            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();

            BufferedImage img = new BufferedImage(SNAP_W * SNAP_SCALE, SNAP_H * SNAP_SCALE, BufferedImage.TYPE_INT_ARGB);
            for (int py = 0; py < SNAP_H; py++) {
                for (int px = 0; px < SNAP_W; px++) {
                    int i = ((SNAP_H - 1 - py) * SNAP_W + px) * 4; // GL rows are bottom-up
                    int argb = ((buf.get(i + 3) & 255) << 24) | ((buf.get(i) & 255) << 16)
                            | ((buf.get(i + 1) & 255) << 8) | (buf.get(i + 2) & 255);
                    argb |= 0xFF000000; // the card background is opaque
                    for (int dy = 0; dy < SNAP_SCALE; dy++)
                        for (int dx = 0; dx < SNAP_SCALE; dx++)
                            img.setRGB(px * SNAP_SCALE + dx, py * SNAP_SCALE + dy, argb);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            System.err.println("[Beyonder] Couldn't render the kit picture: " + e.getMessage());
            return null;
        } finally {
            fb.unbindFramebuffer();
            fb.deleteFramebuffer();
            mc.getFramebuffer().bindFramebuffer(true);
        }
    }
}
