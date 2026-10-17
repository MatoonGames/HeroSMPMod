package com.matoon.herosmp.client.gui.kitselect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Shader;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.client.shader.ShaderUniform;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Backdrop camera for the fighter select: slowly circles and drifts over the arena the round is
 * about to be fought in, seen through vanilla's blur post shader so the UI on top keeps its
 * contrast. Works by pointing the game's render view at an invisible stand-in entity (never added
 * to the world) and moving it every client tick; the frozen player stays where they are.
 */
final class ArenaFlyCam {

    private static final ResourceLocation BLUR = new ResourceLocation("shaders/post/blur.json");
    private static Field shaderList;

    private final Minecraft mc = Minecraft.getMinecraft();
    private EntityArmorStand cam;
    private boolean hadHideGui;
    private boolean blurLoaded;
    private double cx, cy, cz, camY = Double.NaN;
    private double angle0;
    private int ticks;

    void start() {
        if (mc.world == null || mc.player == null) return;
        hadHideGui = mc.gameSettings.hideGUI;
        mc.gameSettings.hideGUI = true; // no hotbar, hand or crosshair over the flight
        recentre();
        // Start behind the player, looking the way they face.
        double yaw = Math.toRadians(mc.player.rotationYaw);
        angle0 = Math.atan2(-Math.cos(yaw), Math.sin(yaw));
        cam = new EntityArmorStand(mc.world, cx, cy, cz);
        cam.setInvisible(true);
        place(true);
        mc.setRenderViewEntity(cam);
        if (OpenGlHelper.shadersSupported) {
            try {
                mc.entityRenderer.loadShader(BLUR);
                blurLoaded = mc.entityRenderer.getShaderGroup() != null;
            } catch (RuntimeException e) {
                blurLoaded = false;
            }
        }
    }

    boolean active() {
        return cam != null;
    }

    /** Called every client tick while the screen is open. */
    void tick() {
        if (cam == null) return;
        if (mc.world == null || mc.player == null) { stop(); return; }
        if (cam.world != mc.world) {
            // The client switched worlds (arena dimension arrived late): follow it.
            cam.setWorld(mc.world);
            camY = Double.NaN;
        }
        // The server is still placing the player for the first couple of seconds.
        if (ticks < 50) recentre();
        ticks++;
        place(false);
        if (mc.getRenderViewEntity() != cam) mc.setRenderViewEntity(cam); // something reset the view
    }

    private void recentre() {
        cx = mc.player.posX;
        cy = mc.player.posY;
        cz = mc.player.posZ;
    }

    /** Moves the camera along its path; {@code snap} skips interpolation (first placement). */
    private void place(boolean snap) {
        double t = ticks / 20.0;
        double a = angle0 + t * 0.045;               // a full circle every ~2.3 minutes
        double r = 26 + 5 * Math.sin(t * 0.11);      // breathe in and out a little
        double x = cx + Math.cos(a) * r, z = cz + Math.sin(a) * r;
        double ground = Math.max(groundAt(x, z), groundAt(cx, cz));
        double target = ground + 15 + 3 * Math.sin(t * 0.13);
        camY = Double.isNaN(camY) ? target : camY + (target - camY) * 0.04;

        // Look a little ahead along the path, down at the arena floor.
        double lx = cx - Math.sin(a) * 7, lz = cz + Math.cos(a) * 7, ly = groundAt(cx, cz) + 1.5;
        double dx = lx - x, dz = lz - z, dy = ly - camY;
        float yaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90f;
        float pitch = (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180 / Math.PI);

        double eye = cam.getEyeHeight();
        if (snap) {
            cam.setLocationAndAngles(x, camY - eye, z, yaw, pitch);
            cam.prevPosX = cam.lastTickPosX = cam.posX;
            cam.prevPosY = cam.lastTickPosY = cam.posY;
            cam.prevPosZ = cam.lastTickPosZ = cam.posZ;
            cam.prevRotationYaw = cam.rotationYaw;
            cam.prevRotationPitch = cam.rotationPitch;
            return;
        }
        cam.prevPosX = cam.lastTickPosX = cam.posX;
        cam.prevPosY = cam.lastTickPosY = cam.posY;
        cam.prevPosZ = cam.lastTickPosZ = cam.posZ;
        cam.prevRotationYaw = cam.rotationYaw;
        cam.prevRotationPitch = cam.rotationPitch;
        cam.posX = x;
        cam.posY = camY - eye;
        cam.posZ = z;
        // Keep yaw continuous so interpolation never spins the long way round.
        cam.rotationYaw = cam.prevRotationYaw + MathHelper.wrapDegrees(yaw - cam.prevRotationYaw);
        cam.rotationPitch = pitch;
        cam.rotationYawHead = cam.rotationYaw;
    }

    private double groundAt(double x, double z) {
        int h = mc.world.getHeight(MathHelper.floor(x), MathHelper.floor(z));
        return h > 0 ? h : cy; // chunk not loaded yet: assume the player's level
    }

    /** Blur strength in pixels (vanilla's default is 20). */
    void setBlur(float radius) {
        if (!blurLoaded) return;
        ShaderGroup group = mc.entityRenderer.getShaderGroup();
        if (group == null) return;
        try {
            if (shaderList == null) shaderList = ReflectionHelper.findField(ShaderGroup.class, "listShaders", "field_148031_d");
            @SuppressWarnings("unchecked")
            List<Shader> shaders = (List<Shader>) shaderList.get(group);
            for (Shader s : shaders) {
                ShaderUniform u = s.getShaderManager().getShaderUniform("Radius");
                if (u != null) u.set(radius);
            }
        } catch (Exception e) {
            blurLoaded = false; // leave vanilla's radius
        }
    }

    void stop() {
        if (cam == null) return;
        cam = null;
        mc.gameSettings.hideGUI = hadHideGui;
        if (mc.player != null) {
            Entity back = mc.player;
            mc.setRenderViewEntity(back); // also drops our blur shader
        }
        if (blurLoaded && mc.entityRenderer.getShaderGroup() != null
                && mc.entityRenderer.isShaderActive()) {
            mc.entityRenderer.stopUseShader();
        }
        blurLoaded = false;
    }
}
