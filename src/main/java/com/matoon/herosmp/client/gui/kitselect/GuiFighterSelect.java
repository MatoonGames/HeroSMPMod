package com.matoon.herosmp.client.gui.kitselect;

import com.matoon.herosmp.client.gui.KitAvatar;
import com.matoon.herosmp.client.gui.bedrock.BedrockScreen;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI;
import com.matoon.herosmp.client.gui.bedrock.UiSounds;
import com.matoon.herosmp.hungergames.music.HungerGamesMusicManager;
import com.matoon.herosmp.network.ModNetwork;
import com.matoon.herosmp.network.PacketKitSelectStatus;
import com.matoon.herosmp.network.PacketOpenKitSelection;
import com.matoon.herosmp.network.PacketSelectKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.matoon.herosmp.client.gui.kitselect.ArenaDraw.arc;
import static com.matoon.herosmp.client.gui.kitselect.ArenaDraw.beginColor;
import static com.matoon.herosmp.client.gui.kitselect.ArenaDraw.endColor;
import static com.matoon.herosmp.client.gui.kitselect.ArenaDraw.para;
import static com.matoon.herosmp.client.gui.kitselect.ArenaDraw.vtx;

/**
 * Fighter select for the start of a PVP round, staged like a fighting-game tournament: slabs slam
 * together and burst apart, "CHOOSE YOUR FIGHTER!" punches in, and the roster cascades onto the
 * screen as 3D kit figures. Picking a kit raises the "READY TO FIGHT!" banner (click it, or press
 * Enter, to lock in); opponents' cards fill in live as they lock, and once everyone is in, a VS
 * splash slams down and the screen wipes away into the round.
 * <p>
 * The server sends the roster ({@link PacketOpenKitSelection}) and every lock-in
 * ({@link PacketKitSelectStatus}); the kit itself is still applied server-side by
 * {@link PacketSelectKit}. Music comes from the server's {@code KitSelect} / {@code Fight} music
 * folders when it has tracks there.
 */
public class GuiFighterSelect extends BedrockScreen {

    // ---------------------------------------------------------------------
    // Entry points (client thread)
    // ---------------------------------------------------------------------

    private static PacketKitSelectStatus lastStatus;
    private static long lastStatusAt;

    public static void open(PacketOpenKitSelection packet) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof GuiFighterSelect && ((GuiFighterSelect) mc.currentScreen).state != State.OUTRO) {
            ((GuiFighterSelect) mc.currentScreen).reopen(packet); // the server rejected our pick
            return;
        }
        mc.displayGuiScreen(new GuiFighterSelect(packet));
    }

    public static void status(PacketKitSelectStatus packet) {
        lastStatus = packet;
        lastStatusAt = System.currentTimeMillis();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof GuiFighterSelect) ((GuiFighterSelect) mc.currentScreen).applyStatus(packet);
    }

    // ---------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------

    private enum State { SELECT, LOCKED, OUTRO }

    /** Slot colours, fighting-game style: P1 red, P2 blue, P3 yellow, P4 green, then extras. */
    private static final int[] SLOT_COLORS = {0xFFE53935, 0xFF1E88E5, 0xFFFDD835, 0xFF43A047, 0xFF8E24AA, 0xFF00ACC1};

    private List<PacketOpenKitSelection.KitEntry> kits;
    private List<KitAvatar.Loadout> loadouts;
    private final String mode;
    private long deadlineAt;
    private final String musicTrack, fightTrack;

    private final long openedAt = System.currentTimeMillis();
    private State state = State.SELECT;
    private int cursor = -1, pending = -1, lockedIndex = -1;
    private long pendingAt, lockedAt, outroAt;
    private String myLockedKey = "";
    private int lastMouseX = Integer.MIN_VALUE, lastMouseY;

    private final List<PacketKitSelectStatus.Fighter> roster = new ArrayList<PacketKitSelectStatus.Fighter>();
    private final Map<UUID, Long> stampAt = new HashMap<UUID, Long>();

    // Random pick: the highlight races around the roster and slows onto the winner.
    private long rouletteAt = -1;
    private int rouletteFrom, rouletteSteps, rouletteTarget, rouletteLastStep = -1;

    private long shakeAt;
    private float shakeAmp;
    private long flashAt = -1;
    private int flashColor = 0xFFFFFFFF;
    private final boolean[] introCues = new boolean[3];
    private int cascadeCued = -1;
    private boolean vsCued;
    private int lastTickSecond = -1;
    private ISound music;
    /** Blurred flight over the arena behind everything. */
    private final ArenaFlyCam flyCam = new ArenaFlyCam();

    private GuiFighterSelect(PacketOpenKitSelection p) {
        super(null, "Choose your fighter");
        this.chromeless = true;
        this.mode = p.mode == null || p.mode.isEmpty() ? "PVP" : p.mode;
        this.musicTrack = p.musicTrack;
        this.fightTrack = p.fightTrack;
        setKits(p);
        if (lastStatus != null && System.currentTimeMillis() - lastStatusAt < 4000 && rosterHasMe(lastStatus)) {
            applyStatus(lastStatus);
        }
    }

    private void setKits(PacketOpenKitSelection p) {
        kits = new ArrayList<PacketOpenKitSelection.KitEntry>(p.kits);
        loadouts = new ArrayList<KitAvatar.Loadout>(kits.size());
        for (PacketOpenKitSelection.KitEntry k : kits) loadouts.add(KitAvatar.Loadout.ofGear(k.gear));
        deadlineAt = p.seconds > 0 ? System.currentTimeMillis() + p.seconds * 1000L : 0;
    }

    private void reopen(PacketOpenKitSelection p) {
        setKits(p);
        state = State.SELECT;
        pending = -1;
        lockedIndex = -1;
        myLockedKey = "";
        rouletteAt = -1;
        toastWarn("That kit isn't available anymore - pick another");
    }

    @Override
    public void initGui() {
        super.initGui();
        if (music == null) startMusic();
        if (!flyCam.active()) flyCam.start();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        flyCam.tick();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false; // the round's timer keeps running, even in singleplayer
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        stopMusic();
        flyCam.stop();
    }

    /** Esc: nothing while choosing (you have to pick); after locking in it just hides the screen. */
    @Override
    protected void goBack() {
        if (state != State.SELECT) mc.displayGuiScreen(null);
    }

    // ---------------------------------------------------------------------
    // Roster updates
    // ---------------------------------------------------------------------

    private UUID me() {
        return mc.player != null ? mc.player.getUniqueID() : null;
    }

    private boolean rosterHasMe(PacketKitSelectStatus s) {
        UUID me = me();
        for (PacketKitSelectStatus.Fighter f : s.fighters) if (f.id.equals(me)) return true;
        return false;
    }

    private void applyStatus(PacketKitSelectStatus s) {
        long now = System.currentTimeMillis();
        boolean quiet = now - openedAt < 400; // picks that were already in when we opened don't slam
        UUID me = me();
        for (PacketKitSelectStatus.Fighter f : s.fighters) {
            boolean wasLocked = stampAt.containsKey(f.id);
            if (!f.kitKey.isEmpty() && !wasLocked) {
                stampAt.put(f.id, quiet ? now - 10000 : now);
                if (!quiet && !f.id.equals(me)) {
                    play(SoundEvents.ITEM_ARMOR_EQUIP_DIAMOND, 1.0f, 0.6f);
                    play(SoundEvents.BLOCK_NOTE_PLING, 1.4f, 0.4f);
                    shake(3f);
                }
            } else if (f.kitKey.isEmpty() && wasLocked && !f.id.equals(me)) {
                stampAt.remove(f.id);
            }
        }
        roster.clear();
        roster.addAll(s.fighters);
    }

    /** Fighters to show: the server's roster, or just us until it arrives. */
    private List<PacketKitSelectStatus.Fighter> fighters() {
        if (!roster.isEmpty()) return roster;
        List<PacketKitSelectStatus.Fighter> solo = new ArrayList<PacketKitSelectStatus.Fighter>();
        if (mc.player != null) solo.add(new PacketKitSelectStatus.Fighter(mc.player.getUniqueID(), mc.player.getName(), myLockedKey));
        return solo;
    }

    private String lockedKeyOf(PacketKitSelectStatus.Fighter f) {
        if (f.id.equals(me()) && !myLockedKey.isEmpty()) return myLockedKey;
        return f.kitKey;
    }

    private boolean everyoneLocked() {
        List<PacketKitSelectStatus.Fighter> fs = fighters();
        if (fs.isEmpty()) return false;
        for (PacketKitSelectStatus.Fighter f : fs) if (lockedKeyOf(f).isEmpty()) return false;
        return true;
    }

    private int kitIndex(String key) {
        for (int i = 0; i < kits.size(); i++) if (kits.get(i).key.equals(key)) return i;
        return -1;
    }

    // ---------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------

    /** First click: put your token on the kit and raise the READY TO FIGHT banner. */
    private void choose(int index) {
        if (state != State.SELECT || index < 0 || index >= kits.size()) return;
        if (!usable(index)) {
            toastWarn(kits.get(index).displayName + " needs " + kits.get(index).missing + " on the server");
            play(SoundEvents.BLOCK_NOTE_BASS, 0.7f, 0.6f);
            return;
        }
        if (pending == index) { lockIn(index); return; }
        boolean first = pending < 0;
        pending = index;
        pendingAt = System.currentTimeMillis();
        play(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.15f, 0.5f);
        if (first) play(SoundEvents.UI_TOAST_IN, 1.1f, 0.5f);
        play(SoundEvents.ITEM_ARMOR_EQUIP_IRON, 1.0f, 0.5f);
        shake(2.5f);
    }

    /** False for kits the server can't load fully (a mod or Hero Addon is missing there). */
    private boolean usable(int index) {
        return index >= 0 && index < kits.size() && kits.get(index).missing.isEmpty();
    }

    /** A random kit the server can load, or -1 if there are none. */
    private int randomUsable() {
        List<Integer> ok = new ArrayList<Integer>();
        for (int i = 0; i < kits.size(); i++) if (usable(i)) ok.add(i);
        return ok.isEmpty() ? -1 : ok.get(ThreadLocalRandom.current().nextInt(ok.size()));
    }

    private void lockIn(int index) {
        if (state != State.SELECT || !usable(index)) return;
        state = State.LOCKED;
        lockedIndex = index;
        pending = index;
        rouletteAt = -1;
        lockedAt = System.currentTimeMillis();
        myLockedKey = kits.get(index).key;
        UUID me = me();
        if (me != null) stampAt.put(me, lockedAt);
        ModNetwork.CHANNEL.sendToServer(new PacketSelectKit(myLockedKey));

        play(SoundEvents.ENTITY_FIREWORK_LARGE_BLAST, 1.0f, 0.7f);
        play(SoundEvents.BLOCK_ANVIL_LAND, 1.5f, 0.3f);
        play(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.2f, 0.45f);
        flash(0xFFFFE27A);
        shake(8f);
        confetti(70);
    }

    private void cancelPending() {
        if (state != State.SELECT || pending < 0 || rouletteAt >= 0) return;
        pending = -1;
        play(SoundEvents.UI_TOAST_OUT, 1.2f, 0.5f);
    }

    private void startRoulette() {
        if (state != State.SELECT || rouletteAt >= 0 || kits.isEmpty()) return;
        int n = kits.size();
        int target = randomUsable();
        if (target < 0) { toastWarn("None of these kits can be loaded on this server"); return; }
        if (n == 1) { choose(target); return; }
        rouletteTarget = target;
        rouletteFrom = Math.max(0, cursor >= 0 && cursor < n ? cursor : 0);
        rouletteSteps = n * 2 + Math.floorMod(rouletteTarget - rouletteFrom, n);
        rouletteAt = System.currentTimeMillis();
        rouletteLastStep = -1;
        play(SoundEvents.UI_TOAST_IN, 1.6f, 0.4f);
    }

    /** Roulette step reached at {@code elapsed} ms: intervals stretch from 35ms to ~230ms. */
    private int rouletteStep(long elapsed) {
        long t = 0;
        for (int k = 0; k < rouletteSteps; k++) {
            float f = k / (float) rouletteSteps;
            t += 35 + (long) (195 * f * f);
            if (elapsed < t) return k;
        }
        return rouletteSteps;
    }

    private void startOutro() {
        state = State.OUTRO;
        outroAt = System.currentTimeMillis();
        stopMusic();
        play(SoundEvents.UI_TOAST_IN, 0.8f, 0.6f);
        play(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 0.6f);
    }

    // ---------------------------------------------------------------------
    // Frame
    // ---------------------------------------------------------------------

    @Override
    protected void drawBody(float partialTicks) {
        long now = System.currentTimeMillis();
        float t = (now - openedAt) / 1000f;
        tickLogic(now, t);
        // The arena comes into focus as the slabs open, then softens; the VS splash blurs it right out.
        float outroBlur = state == State.OUTRO ? Math.min(1f, (now - outroAt) / 400f) : 0f;
        flyCam.setBlur(2f + 8f * BedrockUI.easeOut(t / 1.4f) + 14f * outroBlur);

        // Screen shake: decaying jitter on everything.
        float shake = shakeAmp * Math.max(0f, 1f - (now - shakeAt) / 380f);
        GlStateManager.pushMatrix();
        if (shake > 0.05f) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            GlStateManager.translate((r.nextFloat() - 0.5f) * 2 * shake, (r.nextFloat() - 0.5f) * 2 * shake, 0);
        }

        drawBackground(t);
        float outro = state == State.OUTRO ? (now - outroAt) / 1000f : -1f;
        if (outro < 0.45f) {
            Layout L = layout();
            drawTopBar(L, t, now);
            drawGrid(L, t, now);
            drawPanels(L, t, now);
            drawBanner(L, t, now);
        }
        if (outro >= 0) drawOutro(outro, t);
        GlStateManager.popMatrix();

        // Flash and intro go over the shake so the edges never show.
        if (flashAt >= 0) {
            float f = 1f - (now - flashAt) / 320f;
            if (f > 0) BedrockUI.rect(0, 0, width, height, BedrockUI.fade(flashColor, f * 0.75f));
        }
        drawIntro(t);
    }

    private void tickLogic(long now, float t) {
        // Intro beats.
        if (!introCues[0]) { introCues[0] = true; play(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 0.7f); }
        if (!introCues[1] && t > 0.26f) {
            introCues[1] = true;
            play(SoundEvents.BLOCK_END_PORTAL_SPAWN, 1.25f, 0.28f);
            play(SoundEvents.ENTITY_GENERIC_EXPLODE, 1.5f, 0.35f);
            flash(0xFFFFFFFF);
            shake(9f);
        }
        if (!introCues[2] && t > 0.42f) { introCues[2] = true; play(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 0.8f, 0.6f); }
        int cascade = (int) ((t - 0.5f) / 0.04f);
        if (cascade > cascadeCued && cascade <= kits.size() && t > 0.5f) {
            if (cascade % 2 == 0) play(SoundEvents.BLOCK_NOTE_HAT, 1.0f + Math.min(1f, cascade / 14f), 0.25f);
            cascadeCued = cascade;
        }

        // Countdown ticks.
        if (deadlineAt > 0 && state == State.SELECT) {
            int left = (int) Math.ceil((deadlineAt - now) / 1000.0);
            if (left != lastTickSecond && left >= 0) {
                lastTickSecond = left;
                if (left <= 5 && left > 0) {
                    play(SoundEvents.BLOCK_NOTE_BASEDRUM, 1.0f, 0.7f);
                    play(SoundEvents.BLOCK_NOTE_HAT, 1.4f + (5 - left) * 0.1f, 0.5f);
                } else if (left <= 10 && left > 0) {
                    play(SoundEvents.BLOCK_NOTE_HAT, 1.2f, 0.25f);
                }
            }
            if (now >= deadlineAt) {
                // Time's up: lock whatever is picked or pointed at, else a random fighter.
                int pick = pending >= 0 ? pending : usable(cursor) ? cursor : randomUsable();
                if (pick >= 0) lockIn(pick);
                else deadlineAt = 0; // nothing loadable: stop retrying every frame
            }
        }

        // Roulette.
        if (rouletteAt >= 0 && state == State.SELECT) {
            int step = rouletteStep(now - rouletteAt);
            if (step != rouletteLastStep) {
                rouletteLastStep = step;
                if (step >= rouletteSteps) {
                    rouletteAt = -1;
                    cursor = rouletteTarget;
                    choose(rouletteTarget);
                    play(SoundEvents.ENTITY_FIREWORK_TWINKLE, 1.2f, 0.4f);
                } else {
                    cursor = (rouletteFrom + step) % kits.size();
                    play(SoundEvents.BLOCK_NOTE_XYLOPHONE, 0.8f + 0.8f * step / (float) rouletteSteps, 0.35f);
                }
            }
        }

        // Into the VS splash once everyone's in (or the timer ran out after we locked).
        if (state == State.LOCKED && now - lockedAt > 900) {
            boolean timeUp = deadlineAt > 0 && now > deadlineAt + 1500;
            boolean noWord = deadlineAt == 0 && roster.isEmpty() && now - lockedAt > 6000;
            if (everyoneLocked() || timeUp || noWord) startOutro();
        }
        if (state == State.OUTRO && now - outroAt > 2150) mc.displayGuiScreen(null);
    }

    // ---------------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------------

    private static final class Layout {
        int topH, gridX, gridY, gridW, gridH, panelY, panelH, bannerY;
        int cols, rows, tileW, tileH, gap;
    }

    private Layout layout() {
        Layout L = new Layout();
        L.topH = 46;
        L.panelH = Math.max(78, Math.min(132, (int) (height * 0.30f)));
        L.panelY = height - L.panelH - 6;
        L.bannerY = L.panelY - 36;
        L.gridX = 14;
        L.gridW = width - 28;
        L.gridY = L.topH + 6;
        L.gridH = Math.max(40, L.bannerY - 6 - L.gridY);
        L.gap = 6;
        int n = kits.size() + 1; // + RANDOM
        int bestW = 0, bestCols = 1;
        for (int cols = 1; cols <= n; cols++) {
            int rows = (n + cols - 1) / cols;
            int byW = (L.gridW - (cols - 1) * L.gap) / cols;
            int byH = (int) (((L.gridH - (rows - 1) * L.gap) / (float) rows) * 0.78f);
            int w = Math.min(byW, byH);
            if (w > bestW) { bestW = w; bestCols = cols; }
        }
        L.tileW = Math.max(26, Math.min(78, bestW));
        L.tileH = (int) (L.tileW / 0.78f);
        L.cols = bestCols;
        L.rows = (n + bestCols - 1) / bestCols;
        return L;
    }

    // ---------------------------------------------------------------------
    // Background
    // ---------------------------------------------------------------------

    private void drawBackground(float t) {
        if (flyCam.active()) {
            // A dusky wash over the blurred arena flight: dark enough for the UI to pop, thin enough to see the map.
            BedrockUI.vGradient(0, 0, width, height, 0x8C0A0D26, 0xA81C0820);
        } else {
            BedrockUI.vGradient(0, 0, width, height, 0xFF0A0D26, 0xFF1C0820);
        }

        // Rotating sun: a warm core and rays, blended additively so they light the scene behind.
        float cx = width / 2f, cy = height * 0.40f;
        float r = (float) Math.hypot(width, height);
        int rays = 18;
        double rot = t * 0.12;
        KitAvatar.glow(cx, cy, Math.min(width, height) * 0.42f, 0.32f + 0.05f * (float) Math.sin(t * 1.3));
        beginColor();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < rays; i++) {
            double a0 = rot + i * Math.PI * 2 / rays, a1 = a0 + Math.PI / rays;
            vtx(b, cx, cy, 0x30FFD27A);
            vtx(b, cx + Math.cos(a1) * r, cy + Math.sin(a1) * r, 0x00FFB050);
            vtx(b, cx + Math.cos(a0) * r, cy + Math.sin(a0) * r, 0x00FFB050);
        }
        Tessellator.getInstance().draw();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        endColor();

        // Speed lines streaking across.
        for (int i = 0; i < 16; i++) {
            float speed = 140 + (i * 53) % 160;
            float span = width + 260;
            float x = span - ((i * 197 + t * speed) % span) - 130;
            float w = 2 + (i * 7) % 7;
            int a = 0x0C + (i * 5) % 0x14;
            para(x, 0, w, height, -height * 0.35f, (a << 24) | 0xBFD9FF, (a << 24) | 0xBFD9FF);
        }

        // Embers drifting up.
        for (int i = 0; i < 46; i++) {
            float seed = (i * 0.618034f) % 1f;
            float speed = 18 + (i * 13) % 34;
            float y = height + 10 - ((t * speed + seed * (height + 20)) % (height + 20));
            float x = (seed * width + (float) Math.sin(t * 0.9 + i) * 14) % width;
            float flicker = 0.5f + 0.5f * (float) Math.sin(t * 6 + i * 1.7);
            int col = i % 3 == 0 ? 0xFFFFD27A : i % 3 == 1 ? 0xFFFF8A3D : 0xFFFF4D6D;
            float s = 1 + (i % 2);
            BedrockUI.rectF(x, y, x + s, y + s, BedrockUI.fade(col, 0.35f + 0.5f * flicker));
        }

        // Title band and stage floor.
        para(-30, 0, width * 0.66f, 44, 22, 0xD0A0132B, 0x40A0132B);
        para(width * 0.42f, 0, width * 0.7f, 44, 22, 0x2014327A, 0xA014327A);
        BedrockUI.vGradient(0, height - height / 3, width, height / 3, 0x00000000, 0xC0000000);
    }

    // ---------------------------------------------------------------------
    // Top bar: mode badge, title, timer
    // ---------------------------------------------------------------------

    private void drawTopBar(Layout L, float t, long now) {
        // Mode badge slides in from the left.
        float mb = BedrockUI.easeOutBack((t - 0.45f) / 0.35f);
        if (mb > 0) {
            int bw = fontRenderer.getStringWidth(mode) + 26;
            float bx = -bw + (10 + bw) * mb;
            para(bx - 1, 9, bw + 2, 22, 7, 0xFF1A1000, 0xFF1A1000);
            para(bx, 10, bw, 20, 6, 0xFFFFE27A, 0xFFE0A21E);
            fontRenderer.drawString(mode, (int) bx + 14, 16, 0xFF2A1A00, false);
        }

        // Title.
        String title = state == State.SELECT ? "CHOOSE YOUR FIGHTER!"
                : everyoneLocked() ? "LET'S GO!" : "LOCKED IN!";
        float ts = Math.max(1.4f, Math.min(2.6f, width / 300f));
        float tp = (t - 0.3f) / 0.3f;
        if (tp > 0) {
            float slam = tp < 1 ? 1f + 2.2f * (1 - BedrockUI.easeOutBack(tp)) : 1f;
            float pulse = state == State.SELECT ? 1f + 0.025f * (float) Math.sin(t * 3.2) : 1f;
            float y = 23 + (float) Math.sin(t * 2.1) * 1.2f;
            slantText(title, width / 2f, y - 4 * ts * slam * pulse, ts * slam * pulse,
                    state == State.SELECT ? 0xFFFFE04A : 0xFF7CFF6B, true);
        }

        // Timer disc.
        if (deadlineAt > 0 && state != State.OUTRO) {
            float tm = BedrockUI.easeOutBack((t - 0.55f) / 0.3f);
            if (tm > 0) {
                float total = Math.max(1f, (deadlineAt - openedAt) / 1000f);
                float left = Math.max(0f, (deadlineAt - now) / 1000f);
                int secs = (int) Math.ceil(left);
                boolean hot = secs <= 5;
                float beat = hot ? 1f + 0.18f * (float) Math.max(0, Math.cos((left % 1f) * Math.PI)) : 1f;
                float cx = width - 26, cy = 22, r = 15 * tm * beat;
                BedrockUI.disc(cx, cy, r + 2, 0xFF000000);
                BedrockUI.disc(cx, cy, r, 0xFF1B1C30);
                int ring = hot ? 0xFFFF3B3B : left / total < 0.5f ? 0xFFFFC23A : 0xFF6CFF6B;
                arc(cx, cy, r - 4, r, left / total, ring);
                String s = String.valueOf(secs);
                float sc = 1.4f * tm;
                GlStateManager.pushMatrix();
                GlStateManager.translate(cx, cy, 0);
                GlStateManager.scale(sc, sc, 1);
                fontRenderer.drawString(s, -fontRenderer.getStringWidth(s) / 2f, -3.5f, hot ? 0xFFFF6B6B : 0xFFFFFFFF, true);
                GlStateManager.popMatrix();
            }
        }
    }

    // ---------------------------------------------------------------------
    // Roster grid
    // ---------------------------------------------------------------------

    private void drawGrid(Layout L, float t, long now) {
        int n = kits.size() + 1;
        int rowW = L.cols * L.tileW + (L.cols - 1) * L.gap;
        int gridH = L.rows * L.tileH + (L.rows - 1) * L.gap;
        int startX = L.gridX + (L.gridW - rowW) / 2;
        int startY = L.gridY + Math.max(0, (L.gridH - gridH) / 2);

        // Mouse movement takes the cursor back from the keyboard.
        boolean mouseMoved = mx != lastMouseX || my != lastMouseY;
        lastMouseX = mx;
        lastMouseY = my;
        int hoveredTile = -1;

        for (int i = 0; i < n; i++) {
            int row = i / L.cols, col = i % L.cols;
            int inRow = row == L.rows - 1 ? n - row * L.cols : L.cols; // centre the last row
            int rowStart = startX + (rowW - (inRow * L.tileW + (inRow - 1) * L.gap)) / 2;
            int x = rowStart + col * (L.tileW + L.gap);
            int y = startY + row * (L.tileH + L.gap);

            float appear = (t - 0.5f - i * 0.04f) / 0.35f;
            if (appear <= 0) continue;
            float pop = BedrockUI.easeOutBack(appear);
            boolean random = i == kits.size();
            boolean hov = state == State.SELECT && rouletteAt < 0 && hover(x, y, L.tileW, L.tileH);
            if (hov) hoveredTile = i;
            boolean focus = hov || (i == cursor && rouletteAt >= 0);
            float lift = anim("tile:" + i, focus || i == pending, 9f);

            GlStateManager.pushMatrix();
            float cx = x + L.tileW / 2f, cy = y + L.tileH / 2f;
            float s = (0.55f + 0.45f * pop) * (1f + 0.10f * BedrockUI.easeOut(lift));
            GlStateManager.translate(cx, cy + (1 - Math.min(1f, pop)) * 30 - lift * 3, 0);
            GlStateManager.scale(s, s, 1);
            GlStateManager.translate(-cx, -cy, 0);
            if (random) drawRandomTile(x, y, L.tileW, L.tileH, t, focus);
            else drawKitTile(i, x, y, L.tileW, L.tileH, t, focus, now);
            GlStateManager.popMatrix();

            if (state == State.SELECT && rouletteAt < 0) {
                final int idx = i;
                cue(UiSounds.Cue.NONE);
                hit(x, y, L.tileW, L.tileH, () -> {
                    if (idx == kits.size()) startRoulette();
                    else choose(idx);
                });
            }
        }

        if (mouseMoved && hoveredTile >= 0 && hoveredTile != cursor && rouletteAt < 0) {
            cursor = hoveredTile;
            play(SoundEvents.ITEM_ARMOR_EQUIP_GENERIC, 1.3f, 0.25f);
        }
    }

    private void drawKitTile(int i, int x, int y, int w, int h, float t, boolean focus, long now) {
        int skew = Math.max(3, w / 9);
        boolean isPending = i == pending;
        boolean dimmed = state != State.SELECT && i != lockedIndex;
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 7);

        // Frame: gold when pointed at, your slot colour when chosen.
        int frame = isPending ? myColor() : focus ? BedrockUI.fade(0xFFFFE27A, 0.75f + 0.25f * pulse) : 0xFF05060D;
        para(x - 2, y - 2, w + 4, h + 4, skew, frame, frame);
        para(x, y, w, h, skew, focus ? 0xFF3A3F6E : 0xFF262A4C, 0xFF111326);
        // Diagonal shine strip.
        para(x + w * 0.15f, y, w * 0.18f, h, skew, 0x14FFFFFF, 0x00FFFFFF);

        int nameH = Math.max(11, h / 6);
        KitAvatar.draw(loadouts.get(i), "fs:" + i + ":" + kits.get(i).key, x + 1, y + 1, w - 2, h - nameH - 1, focus || isPending);

        // Name plate.
        para(x, y + h - nameH, w, nameH, 0, 0xF0000000, 0xF0000000);
        String name = BedrockUI.trim(fontRenderer, kits.get(i).displayName.toUpperCase(), (int) ((w - 4) / 0.75f));
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + w / 2f, y + h - nameH / 2f - 3f * 0.75f, 0);
        GlStateManager.scale(0.75f, 0.75f, 1);
        fontRenderer.drawString(name, -fontRenderer.getStringWidth(name) / 2f, 0, focus || isPending ? 0xFFFFE27A : 0xFFFFFFFF, false);
        GlStateManager.popMatrix();

        // Kits the server can't load: greyed out with what's missing.
        if (!kits.get(i).missing.isEmpty()) {
            para(x, y, w, h, skew, 0xB0000000, 0xC8000000);
            int bh = Math.max(10, nameH);
            para(x, y + h - nameH - bh - 1, w, bh, 0, 0xE0A01818, 0xE0A01818);
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + w / 2f, y + h - nameH - bh / 2f - 3f * 0.75f, 0);
            GlStateManager.scale(0.75f, 0.75f, 1);
            fontRenderer.drawString("NEEDS MODS", -fontRenderer.getStringWidth("NEEDS MODS") / 2f, 0, 0xFFFFFFFF, false);
            GlStateManager.popMatrix();
            if (focus) setTooltip(kits.get(i).displayName, "§cNeeds on the server: §f" + kits.get(i).missing);
        }

        // Superpower badge: a lightning bolt in the corner.
        if (!kits.get(i).powerName.isEmpty()) {
            float glowA = 0.75f + 0.25f * (float) Math.sin(t * 5 + i);
            para(x + w - 15, y + 2, 13, 12, 3, BedrockUI.fade(0xFFB57EDC, glowA), BedrockUI.fade(0xFF6A2FA8, glowA));
            bolt(x + w - 12, y + 3, 0xFFFFF4A0);
        }

        if (dimmed) para(x, y, w, h, skew, 0xA0000000, 0xC0000000);

        // Tokens: yours and every locked opponent's on the kit they took.
        int tokenY = y - 4;
        if (isPending) {
            float drop = BedrockUI.easeOutBack((now - pendingAt) / 260f);
            token(x + 2, tokenY - (1 - drop) * 14, myLabel(), myColor());
        }
        int tx = x + w - 2;
        List<PacketKitSelectStatus.Fighter> fs = fighters();
        for (int f = 0; f < fs.size(); f++) {
            PacketKitSelectStatus.Fighter fi = fs.get(f);
            if (fi.id.equals(me()) || !kits.get(i).key.equals(lockedKeyOf(fi))) continue;
            String label = "P" + (f + 1);
            int tw = fontRenderer.getStringWidth(label) + 6;
            tx -= tw;
            token(tx, tokenY, label, SLOT_COLORS[f % SLOT_COLORS.length]);
            tx -= 2;
        }
    }

    private void drawRandomTile(int x, int y, int w, int h, float t, boolean focus) {
        int skew = Math.max(3, w / 9);
        float hue = (t * 0.35f) % 1f;
        int rainbow = 0xFF000000 | java.awt.Color.HSBtoRGB(hue, 0.75f, 1f);
        int frame = focus ? rainbow : 0xFF05060D;
        para(x - 2, y - 2, w + 4, h + 4, skew, frame, frame);
        para(x, y, w, h, skew, focus ? 0xFF3B2F5E : 0xFF221C3A, 0xFF100C1E);
        float qs = Math.max(1.5f, w / 16f) * (focus ? 1f + 0.08f * (float) Math.sin(t * 9) : 1f);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + w / 2f + skew / 2f, y + h * 0.42f, 0);
        GlStateManager.rotate((float) Math.sin(t * 2.4) * 8f, 0, 0, 1);
        GlStateManager.scale(qs, qs, 1);
        fontRenderer.drawString("?", -fontRenderer.getStringWidth("?") / 2f, -4, focus ? rainbow : 0xFFB9A8FF, true);
        GlStateManager.popMatrix();
        int nameH = Math.max(11, h / 6);
        para(x, y + h - nameH, w, nameH, 0, 0xF0000000, 0xF0000000);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + w / 2f, y + h - nameH / 2f - 2.25f, 0);
        GlStateManager.scale(0.75f, 0.75f, 1);
        fontRenderer.drawString("RANDOM", -fontRenderer.getStringWidth("RANDOM") / 2f, 0, focus ? rainbow : 0xFFFFFFFF, false);
        GlStateManager.popMatrix();
        if (state != State.SELECT) para(x, y, w, h, skew, 0xA0000000, 0xC0000000);
    }

    private void token(float x, float y, String label, int color) {
        int tw = fontRenderer.getStringWidth(label) + 6;
        BedrockUI.rectF(x - 1, y - 1, x + tw + 1, y + 11, 0xFF000000);
        BedrockUI.rectF(x, y, x + tw, y + 10, color);
        BedrockUI.rectF(x, y, x + tw, y + 1, 0x60FFFFFF);
        fontRenderer.drawString(label, x + 3, y + 1, 0xFFFFFFFF, true);
    }

    // ---------------------------------------------------------------------
    // Fighter cards
    // ---------------------------------------------------------------------

    private void drawPanels(Layout L, float t, long now) {
        List<PacketKitSelectStatus.Fighter> fs = fighters();
        int count = Math.min(6, Math.max(1, fs.size()));
        int gap = 10;
        int pw = Math.min(230, (width - 20 - (count - 1) * gap) / count);
        int total = count * pw + (count - 1) * gap;
        int x0 = (width - total) / 2;
        UUID me = me();
        for (int i = 0; i < count && i < fs.size(); i++) {
            PacketKitSelectStatus.Fighter f = fs.get(i);
            float in = BedrockUI.easeOutBack((t - 0.65f - i * 0.09f) / 0.4f);
            if (in <= 0) continue;
            int x = x0 + i * (pw + gap);
            int y = L.panelY + (int) ((1 - Math.min(1f, in)) * (L.panelH + 20));
            drawFighterCard(f, i, f.id.equals(me), x, y, pw, L.panelH, t, now);
        }
    }

    private void drawFighterCard(PacketKitSelectStatus.Fighter f, int slot, boolean mine, int x, int y, int w, int h,
                                 float t, long now) {
        int color = SLOT_COLORS[slot % SLOT_COLORS.length];
        int skew = 12;
        String lockedKey = lockedKeyOf(f);
        boolean locked = !lockedKey.isEmpty();
        int kit = locked ? kitIndex(lockedKey) : mine ? (pending >= 0 ? pending : cursor >= 0 && cursor < kits.size() ? cursor : -1) : -1;

        para(x - 2, y - 2, w + 4, h + 4, skew, 0xFF000000, 0xFF000000);
        para(x, y, w, h, skew, darken(color, 0.55f), darken(color, 0.18f));
        para(x, y, w, 3, skew * 3f / h, color, color);
        // Big slot number watermark.
        String label = "P" + (slot + 1);
        slantText(label, x + w - 8 - fontRenderer.getStringWidth(label) * 2.6f, y + 6, 2.6f, BedrockUI.fade(0xFFFFFFFF, 0.22f), false);

        // The figure stands on the card and pops out over its top edge.
        int figW = Math.min((int) (w * 0.42f), (int) (h * 0.75f));
        float figH = h * 1.08f;
        float feetY = y + h - 6;
        float fcx = x + 6 + figW / 2f;
        KitAvatar.glow(fcx, feetY - figH * 0.45f, figH * 0.55f, locked ? 1f : 0.55f);
        Long stamp = stampAt.get(f.id);
        float swing = stamp != null && now - stamp < 320 ? (now - stamp) / 320f : 0f;
        float yaw = -18f + (float) Math.sin(t * 0.9 + slot) * 22f;
        KitAvatar.Loadout lo = kit >= 0 ? loadouts.get(kit) : KitAvatar.Loadout.EMPTY;
        KitAvatar.render(lo, fcx, feetY, KitAvatar.scaleForHeight(figH * 0.86f), swing, (now % 100000L) / 50f, yaw, skinOf(f.id));

        // Text column.
        int tx = x + 10 + figW;
        int tw = x + w - 6 - tx;
        String who = f.name;
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, who, tw - (mine ? 30 : 0)), tx, y + 8, 0xFFE8E8F0, true);
        if (mine) token(tx + Math.min(tw - 26, fontRenderer.getStringWidth(who) + 4), y + 7, "YOU", 0xFF000000 | color);

        String kitName;
        int kitColor;
        if (kit >= 0) {
            kitName = kits.get(kit).displayName.toUpperCase();
            kitColor = locked ? 0xFFFFFFFF : 0xFFFFE27A;
        } else if (locked) {
            kitName = lockedKey.toUpperCase();
            kitColor = 0xFFFFFFFF;
        } else {
            int dots = (int) (t * 3) % 4;
            kitName = "CHOOSING" + "...".substring(0, dots);
            kitColor = 0xFFB0B4D0;
        }
        float ks = tw > 120 ? 1.6f : 1.25f;
        String shown = BedrockUI.trim(fontRenderer, kitName, (int) (tw / ks));
        slantText(shown, tx, y + 22, ks, kitColor, false);
        int powerH = 0;
        if (kit >= 0 && !kits.get(kit).powerName.isEmpty()) {
            // The kit's superpower, as a glowing chip under its name.
            String pn = BedrockUI.trim(fontRenderer, kits.get(kit).powerName.toUpperCase(), tw - 18);
            int py = y + 24 + (int) (ks * 10) + 2;
            int pw = fontRenderer.getStringWidth(pn) + 16;
            para(tx, py, pw, 10, 3, 0xFFB57EDC, 0xFF6A2FA8);
            bolt(tx + 3, py + 1, 0xFFFFF4A0);
            fontRenderer.drawString(pn, tx + 12, py + 1, 0xFFFFFFFF, false);
            powerH = 12;
        }

        // Moveset: the kit's hotbar.
        if (kit >= 0) {
            List<ItemStack> bar = kits.get(kit).hotbar;
            int iy = y + 24 + (int) (ks * 10) + 4 + powerH;
            float is = 0.8f;
            int step = (int) (16 * is) + 2;
            int fit = Math.max(0, Math.min(bar.size(), tw / step));
            for (int k = 0; k < fit; k++) {
                float ix = tx + k * step;
                BedrockUI.rectF(ix - 1, iy - 1, ix + 16 * is + 1, iy + 16 * is + 1, 0x70000000);
                BedrockUI.item(bar.get(k), ix, iy, is);
            }
            if (iy + 16 * is + 12 < y + h) {
                String cnt = kits.get(kit).itemCount + " items";
                fontRenderer.drawString(cnt, tx, (int) (iy + 16 * is + 4), 0xFFB0B4D0, false);
            }
        }

        // READY stamp slams on when they lock in.
        if (locked && stamp != null) {
            float sp = (now - stamp) / 260f;
            float sc = sp < 1 ? 1f + 1.6f * (1 - BedrockUI.easeOut(sp)) : 1f;
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + w - 34, y + h - 16, 0);
            GlStateManager.rotate(-12f, 0, 0, 1);
            GlStateManager.scale(1.5f * sc, 1.5f * sc, 1);
            String r = "READY!";
            int rw = fontRenderer.getStringWidth(r);
            BedrockUI.rectF(-rw / 2f - 3, -6, rw / 2f + 3, 6, 0xFF000000);
            BedrockUI.rectF(-rw / 2f - 2, -5, rw / 2f + 2, 5, 0xFFFF3B3B);
            fontRenderer.drawString(r, -rw / 2f, -4, 0xFFFFFFFF, false);
            GlStateManager.popMatrix();
        }
    }

    // ---------------------------------------------------------------------
    // READY TO FIGHT banner
    // ---------------------------------------------------------------------

    private void drawBanner(Layout L, float t, long now) {
        boolean ready = state == State.SELECT && pending >= 0 && rouletteAt < 0;
        boolean waiting = state == State.LOCKED;
        if (!ready && !waiting) return;
        long since = now - (waiting ? lockedAt : pendingAt);
        float in = waiting ? 1f : BedrockUI.easeOutBack(since / 300f);
        int y = L.bannerY, h = 28;
        float off = (1 - in) * -width;

        GlStateManager.pushMatrix();
        GlStateManager.translate(off, 0, 0);
        float pulse = 1f + 0.035f * (float) Math.sin(t * 9);
        int top = waiting ? 0xFF2FA84F : 0xFFFF6A1A, bottom = waiting ? 0xFF13602A : 0xFFC0102E;
        para(-20, y - 2, width + 40, h + 4, 10, 0xFF000000, 0xFF000000);
        para(-20, y, width + 40, h, 10, top, bottom);
        para(-20, y, width + 40, 2, 0, 0x70FFFFFF, 0x70FFFFFF);
        // Chevrons racing to the right.
        for (int i = 0; i < 24; i++) {
            float cx = ((i * 46 + t * 120) % (width + 92)) - 46;
            para(cx, y + 3, 6, h - 6, 8, 0x26FFFFFF, 0x10FFFFFF);
        }
        String text = waiting ? (everyoneLocked() ? "HERE WE GO!" : since < 700 ? "LOCKED IN!" : waitingText(t)) : "READY TO FIGHT!";
        float ts = Math.min(2f, width / 230f) * (waiting ? 1f : pulse);
        slantText(text, width / 2f, y + h / 2f - 4 * ts, ts, 0xFFFFFFFF, true);
        if (ready) {
            String hint = "CLICK / ENTER";
            fontRenderer.drawString(hint, width - fontRenderer.getStringWidth(hint) - 14, y + h - 10, 0xC0FFFFFF, false);
            String back = "RIGHT-CLICK: BACK";
            fontRenderer.drawString(back, 14, y + h - 10, 0x90FFFFFF, false);
        }
        GlStateManager.popMatrix();

        if (ready) {
            cue(UiSounds.Cue.NONE);
            hit(0, y, width, h, () -> lockIn(pending));
        }
        // Flash the banner white the moment you lock in.
        if (waiting && since < 260) para(-20, y, width + 40, h, 10, BedrockUI.fade(0xFFFFFFFF, 1 - since / 260f), BedrockUI.fade(0xFFFFFFFF, 1 - since / 260f));
    }

    private String waitingText(float t) {
        int waitingOn = 0;
        for (PacketKitSelectStatus.Fighter f : fighters()) if (lockedKeyOf(f).isEmpty()) waitingOn++;
        String dots = "...".substring(0, (int) (t * 3) % 4);
        return waitingOn == 1 ? "WAITING FOR YOUR OPPONENT" + dots : "WAITING FOR " + waitingOn + " FIGHTERS" + dots;
    }

    // ---------------------------------------------------------------------
    // Intro slabs
    // ---------------------------------------------------------------------

    private void drawIntro(float t) {
        if (t > 0.95f) return;
        // Black hold, then two slabs slam together at 0.26s and tear apart.
        if (t < 0.3f) BedrockUI.rect(0, 0, width, height, BedrockUI.fade(0xFF000000, 1f - t / 0.3f * 0.2f));
        float close = BedrockUI.easeOut(Math.min(1f, t / 0.26f));
        float open = t > 0.34f ? BedrockUI.easeOut((t - 0.34f) / 0.5f) : 0f;
        float half = width / 2f + 40;
        float leftX = -half + half * close - half * open * 1.2f;
        float rightX = width - half * close + half * open * 1.2f;
        para(leftX - 40, 0, half + 40, height, 0, 0xFFC4122E, 0xFF5A0616);
        para(rightX, 0, half + 40, height, 0, 0xFF1A4FD0, 0xFF0A1D5C);
        // The seam glows white hot on impact.
        if (t > 0.22f && t < 0.6f) {
            float g = 1f - Math.abs(t - 0.3f) / 0.3f;
            para(width / 2f - 6, 0, 12, height, 0, BedrockUI.fade(0xFFFFFFFF, g), BedrockUI.fade(0xFFFFE27A, g));
        }
    }

    // ---------------------------------------------------------------------
    // VS splash
    // ---------------------------------------------------------------------

    private void drawOutro(float o, float t) {
        List<PacketKitSelectStatus.Fighter> fs = new ArrayList<PacketKitSelectStatus.Fighter>(fighters());
        // Put us on the left, like P1.
        UUID me = me();
        for (int i = 0; i < fs.size(); i++) {
            if (fs.get(i).id.equals(me)) { fs.add(0, fs.remove(i)); break; }
        }
        float dark = Math.min(1f, o / 0.3f);
        BedrockUI.rect(0, 0, width, height, BedrockUI.fade(0xFF05050C, dark * 0.9f));

        float slide = BedrockUI.easeOut(Math.min(1f, o / 0.32f));
        float exit = o > 1.6f ? BedrockUI.easeOut((o - 1.6f) / 0.45f) : 0f;
        float exitX = -exit * (width + 80);

        GlStateManager.pushMatrix();
        GlStateManager.translate(exitX, 0, 0);
        int n = Math.max(1, Math.min(6, fs.size()));
        long now = System.currentTimeMillis();
        if (n == 2) {
            float lx = -width * 0.6f * (1 - slide);
            float rx = width * 0.6f * (1 - slide);
            para(lx - 40, 0, width / 2f + 40, height, height * 0.18f, 0xF0B0122C, 0xF0400410);
            para(width / 2f + rx, 0, width / 2f + 40, height, height * 0.18f, 0xF0123FB0, 0xF0041640);
            // Lightning seam.
            if (o > 0.3f) {
                for (int i = 0; i < 6; i++) {
                    float y0 = height * i / 6f, y1 = height * (i + 1) / 6f;
                    float jx = (float) Math.sin(t * 40 + i * 2.1) * 3;
                    para(width / 2f + height * 0.09f - height * 0.18f * (i / 6f) + jx - 1.5f, y0, 3, y1 - y0, height * 0.03f, 0xFFFFFFFF, 0xFFBFE4FF);
                }
            }
            outroFighter(fs.get(0), 0, width * 0.27f + lx, 32f, o, now);
            outroFighter(fs.get(1), 1, width * 0.73f + rx, -32f, o, now);
            vs("VS", o, t);
        } else if (n == 1) {
            outroFighter(fs.get(0), 0, width / 2f, 0f, o, now);
            vs("GO!", o, t);
        } else {
            for (int i = 0; i < n; i++) {
                float cx = width * (i + 0.5f) / n;
                float drop = BedrockUI.easeOutBack((o - i * 0.05f) / 0.35f);
                outroFighter(fs.get(i), i, cx, 0f, Math.max(0f, drop) * 0.6f + (o > 0.35f ? o : 0f), now);
            }
            vs("FIGHT!", o, t);
        }
        GlStateManager.popMatrix();
    }

    private void outroFighter(PacketKitSelectStatus.Fighter f, int slot, float cx, float yaw, float o, long now) {
        int kit = kitIndex(lockedKeyOf(f));
        KitAvatar.Loadout lo = kit >= 0 ? loadouts.get(kit) : KitAvatar.Loadout.EMPTY;
        int n = Math.max(1, Math.min(6, fighters().size()));
        float figH = height * (n <= 2 ? 0.58f : 0.40f);
        float feetY = height * 0.80f;
        KitAvatar.glow(cx, feetY - figH * 0.45f, figH * 0.6f, 0.9f);
        float swing = o > 0.35f && o < 0.67f ? (o - 0.35f) / 0.32f : 0f;
        KitAvatar.render(lo, cx, feetY, KitAvatar.scaleForHeight(figH * 0.86f), swing, (now % 100000L) / 50f, yaw, skinOf(f.id));
        float ns = n <= 2 ? Math.min(2.2f, width / 260f) : 1.3f;
        slantText(f.name.toUpperCase(), cx, feetY + 8, ns, 0xFFFFFFFF, true);
        String kn = kit >= 0 ? kits.get(kit).displayName : "";
        if (!kn.isEmpty()) {
            String k = BedrockUI.trim(fontRenderer, kn, (int) (width / (float) n - 10));
            fontRenderer.drawString(k, cx - fontRenderer.getStringWidth(k) / 2f, feetY + 10 + ns * 9, SLOT_COLORS[slot % SLOT_COLORS.length], true);
        }
    }

    private void vs(String text, float o, float t) {
        if (o < 0.35f) return;
        if (!vsCued) {
            vsCued = true;
            play(SoundEvents.ENTITY_LIGHTNING_THUNDER, 1.4f, 0.3f);
            play(SoundEvents.ENTITY_GENERIC_EXPLODE, 1.2f, 0.45f);
            playFightStinger();
            flash(0xFFFFFFFF);
            shake(11f);
            sparkle(width / 2f, height * 0.45f, 40, 0xFFFFE27A, 0xFFFFFFFF, 0xFFFF6A1A);
        }
        float p = (o - 0.35f) / 0.25f;
        float sc = (p < 1 ? 1f + 3.5f * (1 - BedrockUI.easeOutBack(p)) : 1f + 0.04f * (float) Math.sin(t * 10))
                * Math.min(5f, width / 110f);
        GlStateManager.pushMatrix();
        GlStateManager.translate(width / 2f, height * 0.42f, 0);
        GlStateManager.rotate(-8f, 0, 0, 1);
        slantText(text, 0, -4 * sc, sc, 0xFFFFD43A, true);
        GlStateManager.popMatrix();
    }

    // ---------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------

    @Override
    protected void onMouseDown(int x, int y, int button) {
        if (button == 1) cancelPending();
    }

    @Override
    protected void onKey(char c, int key) {
        if (state != State.SELECT || rouletteAt >= 0) return;
        int n = kits.size() + 1;
        Layout L = layout();
        int cur = cursor < 0 ? 0 : cursor;
        switch (key) {
            case Keyboard.KEY_LEFT:  case Keyboard.KEY_A: moveCursor(Math.floorMod(cur - 1, n)); break;
            case Keyboard.KEY_RIGHT: case Keyboard.KEY_D: moveCursor((cur + 1) % n); break;
            case Keyboard.KEY_UP:    case Keyboard.KEY_W: moveCursor(Math.floorMod(cur - L.cols, n)); break;
            case Keyboard.KEY_DOWN:  case Keyboard.KEY_S: moveCursor(Math.min(n - 1, cur + L.cols)); break;
            case Keyboard.KEY_RETURN: case Keyboard.KEY_NUMPADENTER: case Keyboard.KEY_SPACE:
                if (pending >= 0 && (cursor < 0 || cursor == pending)) lockIn(pending);
                else if (cur == kits.size()) startRoulette();
                else choose(cur);
                break;
            case Keyboard.KEY_BACK: cancelPending(); break;
            default: break;
        }
    }

    private void moveCursor(int to) {
        cursor = to;
        play(SoundEvents.ITEM_ARMOR_EQUIP_GENERIC, 1.3f, 0.3f);
        UiSounds.hover();
    }

    // ---------------------------------------------------------------------
    // Music & sound
    // ---------------------------------------------------------------------

    private void startMusic() {
        if (musicTrack == null || musicTrack.isEmpty()) return;
        ResourceLocation loc = HungerGamesMusicManager.soundLocation(HungerGamesMusicManager.STING_KIT_SELECT, musicTrack);
        if (mc.getSoundHandler().getAccessor(loc) == null) return; // track not downloaded yet
        mc.getSoundHandler().stop("", SoundCategory.MUSIC); // vanilla background music out of the way
        music = new PositionedSoundRecord(loc, SoundCategory.MUSIC, 1f, 1f, true, 0, ISound.AttenuationType.NONE, 0f, 0f, 0f);
        mc.getSoundHandler().playSound(music);
    }

    private void stopMusic() {
        if (music != null) {
            mc.getSoundHandler().stopSound(music);
            music = null;
        }
    }

    private void playFightStinger() {
        if (fightTrack == null || fightTrack.isEmpty()) return;
        ResourceLocation loc = HungerGamesMusicManager.soundLocation(HungerGamesMusicManager.STING_FIGHT, fightTrack);
        if (mc.getSoundHandler().getAccessor(loc) == null) return;
        mc.getSoundHandler().playSound(new PositionedSoundRecord(loc, SoundCategory.MASTER, 1f, 1f, false, 0,
                ISound.AttenuationType.NONE, 0f, 0f, 0f));
    }

    private void play(SoundEvent e, float pitch, float volume) {
        mc.getSoundHandler().playSound(new PositionedSoundRecord(e.getSoundName(), SoundCategory.MASTER, volume, pitch,
                false, 0, ISound.AttenuationType.NONE, 0f, 0f, 0f));
    }

    private void shake(float amp) {
        long now = System.currentTimeMillis();
        float current = shakeAmp * Math.max(0f, 1f - (now - shakeAt) / 380f);
        shakeAmp = Math.max(amp, current);
        shakeAt = now;
    }

    private void flash(int color) {
        flashAt = System.currentTimeMillis();
        flashColor = color;
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /** Tiny pixel lightning bolt (7x9), the superpower mark. */
    private static void bolt(float x, float y, int color) {
        BedrockUI.pixelIcon(new String[]{
                "...XXX", "..XXX.", ".XXX..", "XXXXXX", "..XXX.", ".XXX..", ".XX...", "XX....", "X....."
        }, x, y, 1f, color, color);
    }

    private int mySlot() {
        List<PacketKitSelectStatus.Fighter> fs = fighters();
        UUID me = me();
        for (int i = 0; i < fs.size(); i++) if (fs.get(i).id.equals(me)) return i;
        return 0;
    }

    private int myColor() {
        return SLOT_COLORS[mySlot() % SLOT_COLORS.length];
    }

    private String myLabel() {
        return "P" + (mySlot() + 1);
    }

    private ResourceLocation skinOf(UUID id) {
        if (mc.getConnection() == null || id == null) return null;
        NetworkPlayerInfo info = mc.getConnection().getPlayerInfo(id);
        return info != null ? info.getLocationSkin() : null;
    }

    private static int darken(int argb, float f) {
        int r = (int) (((argb >> 16) & 0xFF) * f), g = (int) (((argb >> 8) & 0xFF) * f), b = (int) ((argb & 0xFF) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Italic announcer text with a heavy outline (see ArenaDraw.slantText). */
    private void slantText(String s, float x, float y, float scale, int color, boolean centered) {
        ArenaDraw.slantText(fontRenderer, s, x, y, scale, color, centered);
    }
}
