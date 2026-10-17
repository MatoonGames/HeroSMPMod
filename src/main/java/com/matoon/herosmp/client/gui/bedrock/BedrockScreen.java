package com.matoon.herosmp.client.gui.bedrock;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Base for every store screen. It provides the Bedrock header bar with a back button, a click
 * system for custom-drawn widgets (hit regions are registered while drawing, so what you see is
 * exactly what you can click, clipping included), text-field focus, drag callbacks, popover menus,
 * modal overlays, Bedrock-style dialogs, toasts, and smooth scrolling. Subclasses implement
 * {@link #drawBody}.
 * <p>
 * It also owns the "feel": every click region carries a sound cue ({@link UiSounds}), clicks
 * leave a ripple, the pointer ticks softly when it moves onto something clickable, buttons get a
 * light sweep on hover, and dialogs, menus, toasts and page changes animate in. Screens can use
 * {@link #anim}, {@link #hoverSweep}, {@link #sparkle} and {@link #confetti} for their own pieces.
 */
public abstract class BedrockScreen extends GuiScreen {

    public static final int HEADER_H = 30;

    protected final GuiScreen parent;
    protected String title;
    /** Show the Beyonder wordmark before the title (used on the store's home screen). */
    protected boolean headerLogo;
    /** Full-screen mode: no backdrop, header bar or page transition; the body owns the whole screen. */
    protected boolean chromeless;

    protected int mx, my;
    private final List<Hit> hits = new ArrayList<Hit>();
    private boolean suppressInput;

    private Dialog dialog;
    private Menu menu;
    private Overlay overlay;
    private BedrockTextField focused;
    private String toastText;
    private long toastAt;
    private ToastKind toastKind = ToastKind.SUCCESS;
    private boolean toastSparkle;
    private List<String> tooltip;

    /** Sound for the next registered click region (then back to a plain click). */
    private UiSounds.Cue nextCue;
    private long frameAt;
    private float frameDt;
    private int frame;
    private int lastMx = Integer.MIN_VALUE, lastMy;
    private int[] lastHoverRect;
    private final Map<Object, Anim> anims = new HashMap<Object, Anim>();
    private final List<Ripple> ripples = new ArrayList<Ripple>();
    private final List<Particle> particles = new ArrayList<Particle>();
    private long dialogAt, menuAt, transitionAt;

    protected BedrockScreen(GuiScreen parent, String title) {
        this.parent = parent;
        this.title = title;
    }

    public FontRenderer font() {
        return fontRenderer;
    }

    public int mouseX() { return mx; }
    public int mouseY() { return my; }

    // ---------------------------------------------------------------------
    // Frame
    // ---------------------------------------------------------------------

    @Override
    public final void drawScreen(int mouseX, int mouseY, float partialTicks) {
        mx = mouseX;
        my = mouseY;
        hits.clear();
        tooltip = null;
        nextCue = null;
        BedrockUI.resetClip();
        long now = System.currentTimeMillis();
        frameDt = frameAt == 0 ? 0 : Math.min(0.1f, (now - frameAt) / 1000f);
        frameAt = now;
        frame++;

        if (!chromeless) BedrockUI.backdrop(width, height);

        boolean modal = dialog != null || menu != null || overlay != null;
        suppressInput = modal;
        int bodyTop = chromeless ? 0 : HEADER_H;
        BedrockUI.pushClip(0, bodyTop, width, height - bodyTop);
        // Page change: the body rises a few pixels out of a dark fade.
        float tt = (now - transitionAt) / 240f;
        boolean transitioning = tt < 1f && !chromeless;
        if (transitioning) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(0, (1 - BedrockUI.easeOut(tt)) * 8, 0);
        }
        drawBody(partialTicks);
        if (transitioning) {
            GlStateManager.popMatrix();
            BedrockUI.rect(0, HEADER_H, width, height - HEADER_H, BedrockUI.fade(0xFF161617, (1 - BedrockUI.easeOut(tt)) * 0.6f));
        }
        BedrockUI.popClip();

        BedrockUI.clearDepth();
        if (!chromeless) drawHeader();
        drawFooter();
        drawToast();

        if (overlay != null) {
            suppressInput = dialog != null || menu != null;
            BedrockUI.clearDepth();
            overlay.draw(this, partialTicks);
        }
        if (menu != null) {
            suppressInput = dialog != null;
            BedrockUI.clearDepth();
            drawMenu();
        }
        suppressInput = false;
        if (dialog != null) {
            BedrockUI.clearDepth();
            drawDialog();
        }
        BedrockUI.clearDepth();
        drawRipples(now);
        drawParticles();
        hoverTick();
        pruneAnims();
        if (tooltip != null) {
            BedrockUI.clearDepth();
            drawHoveringText(tooltip, mx, my);
        }
    }

    protected abstract void drawBody(float partialTicks);

    /** Optional fixed footer drawn above the body (e.g. an action bar). */
    protected void drawFooter() {}

    /** Extra header controls (tabs etc.) drawn after the title. */
    protected void drawHeaderExtras() {}

    /** Right edge the header title may extend to (before any header controls). */
    protected int headerTitleLimit() {
        return width - 10;
    }

    private void drawHeader() {
        BedrockUI.rect(0, 0, width, HEADER_H, BedrockUI.PANEL);
        BedrockUI.rect(0, HEADER_H - 1, width, 1, BedrockUI.OUTLINE);
        BedrockUI.vGradient(0, HEADER_H, width, 5, 0x66000000, 0x00000000);

        int bx = 6, by = 4, bs = 22;
        boolean hov = hover(bx, by, bs, bs);
        BedrockUI.button(fontRenderer, bx, by, bs, bs, null, BedrockUI.Style.DARK, true, hov, hov && Mouse.isButtonDown(0));
        int sink = hov && Mouse.isButtonDown(0) ? BedrockUI.LIP : 0;
        BedrockUI.arrowLeft(bx + bs / 2 - 1, by + 10 + sink, 0xFFFFFFFF);
        hit(bx, by, bs, bs, this::goBack);

        int tx = 36;
        if (headerLogo) {
            // Beyonder wordmark, then a divider and the screen title.
            tx += BedrockUI.logo(tx, 7, 16) + 8;
            BedrockUI.rect(tx, 8, 1, 14, BedrockUI.PANEL_LIGHT);
            tx += 8;
        }
        String t = BedrockUI.trim(fontRenderer, title, headerTitleLimit() - tx);
        if (fontRenderer.getStringWidth(t) > 24) {
            fontRenderer.drawString(t, tx, (HEADER_H - 8) / 2, headerLogo ? BedrockUI.TEXT_DIM : BedrockUI.TEXT, !headerLogo);
        }
        drawHeaderExtras();
    }

    // ---------------------------------------------------------------------
    // Widgets
    // ---------------------------------------------------------------------

    public boolean hover(int x, int y, int w, int h) {
        if (suppressInput) return false;
        int[] r = clipped(x, y, w, h);
        return r != null && mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3];
    }

    public void hit(int x, int y, int w, int h, Runnable action) {
        hit(x, y, w, h, action, false);
    }

    /** Registers a click region; {@code rightToo} also fires on right-click. */
    public void hit(int x, int y, int w, int h, Runnable action, boolean rightToo) {
        UiSounds.Cue cue = nextCue != null ? nextCue : UiSounds.Cue.CLICK;
        nextCue = null;
        if (suppressInput) return;
        int[] r = clipped(x, y, w, h);
        if (r != null) hits.add(new Hit(r, action, rightToo, cue));
    }

    /** Sound for the next click region registered (by hit, button, toggle...). */
    public void cue(UiSounds.Cue cue) {
        nextCue = cue;
    }

    private static UiSounds.Cue cueFor(BedrockUI.Style style) {
        return style == BedrockUI.Style.PRIMARY || style == BedrockUI.Style.GOLD ? UiSounds.Cue.PRIMARY
                : style == BedrockUI.Style.DANGER ? UiSounds.Cue.DANGER : UiSounds.Cue.CLICK;
    }

    /** Swallows clicks in an area (e.g. an opaque footer over scrolling content). */
    public void block(int x, int y, int w, int h) {
        hit(x, y, w, h, null, true);
    }

    private static int[] clipped(int x, int y, int w, int h) {
        int[] r = {x, y, x + w, y + h};
        int[] c = BedrockUI.currentClip();
        if (c != null) {
            r[0] = Math.max(r[0], c[0]);
            r[1] = Math.max(r[1], c[1]);
            r[2] = Math.min(r[2], c[2]);
            r[3] = Math.min(r[3], c[3]);
        }
        return (r[2] > r[0] && r[3] > r[1]) ? r : null;
    }

    /** Draws a clickable Bedrock button. */
    public void button(int x, int y, int w, int h, String label, BedrockUI.Style style,
                       boolean enabled, Runnable action) {
        boolean hov = enabled && hover(x, y, w, h);
        boolean down = hov && Mouse.isButtonDown(0);
        BedrockUI.button(fontRenderer, x, y, w, h, label, style, enabled, hov, down);
        float sweep = hoverSweep("btn:" + x + ":" + y + ":" + w + ":" + h, hov && !down);
        BedrockUI.sheen(x + 1, y + 1, w - 2, h - 2 - BedrockUI.LIP, sweep, style == BedrockUI.Style.SECONDARY ? 110 : 60);
        if (enabled && action != null) {
            if (nextCue == null) nextCue = cueFor(style);
            hit(x, y, w, h, action);
        } else {
            nextCue = null;
        }
    }

    /**
     * Button with an attached "more" segment (three dots) on its right. {@code onMore} receives the
     * screen position the dropdown should anchor to (bottom-right of the button).
     */
    public void splitButton(int x, int y, int w, int h, String label, BedrockUI.Style style,
                            Runnable onMain, final MoreHandler onMore) {
        int moreW = 18;
        int mainW = w - moreW;
        button(x, y, mainW + 1, h, label, style, true, onMain);
        final int ax = x + w, ay = y + h;
        boolean hov = hover(x + mainW, y, moreW, h);
        boolean down = hov && Mouse.isButtonDown(0);
        BedrockUI.button(fontRenderer, x + mainW, y, moreW, h, null, style, true, hov, down);
        BedrockUI.sheen(x + mainW + 1, y + 1, moreW - 2, h - 2 - BedrockUI.LIP,
                hoverSweep("more:" + x + ":" + y, hov && !down), 60);
        // Divider between the two segments.
        BedrockUI.rect(x + mainW, y + 1, 1, h - 2 - BedrockUI.LIP + (down ? BedrockUI.LIP : 0), BedrockUI.OUTLINE);
        int cx = x + mainW + moreW / 2;
        int cy = y + 1 + (h - 2 - BedrockUI.LIP) / 2 + (down ? BedrockUI.LIP : 0);
        for (int i = -1; i <= 1; i++) BedrockUI.rect(cx - 1, cy - 1 + i * 4, 2, 2, style.text);
        hit(x + mainW, y, moreW, h, () -> onMore.open(ax, ay));
    }

    public interface MoreHandler { void open(int anchorRight, int anchorBottom); }

    public static final int TOGGLE_W = 34, TOGGLE_H = 18;

    /**
     * Bedrock on/off switch: a track (green with a check when on, grey when off) and a chunky knob
     * that sits right when on and left when off.
     */
    public void toggle(int x, int y, boolean on, boolean enabled, Runnable onToggle) {
        int w = TOGGLE_W, h = TOGGLE_H;
        boolean hov = enabled && hover(x, y, w, h);
        // The knob glides between sides and the green fills in behind it.
        float pos = BedrockUI.easeOut(anim("tgl:" + x + ":" + y, on, 8f));
        int knob = h - 2;
        int kx = x + 1 + Math.round((w - 2 - knob) * pos);
        BedrockUI.rect(x, y, w, h, BedrockUI.OUTLINE);
        BedrockUI.rect(x + 1, y + 1, w - 2, h - 2, !enabled ? 0xFF2A2B2C : (hov ? 0xFF4A4B4D : 0xFF38393B));
        if (enabled) BedrockUI.rect(x + 1, y + 1, kx + knob / 2 - (x + 1), h - 2, hov ? 0xFF4C9D33 : BedrockUI.GREEN);
        BedrockUI.rect(x + 1, y + 1, w - 2, 1, 0x30000000);
        if (pos > 0.5f) BedrockUI.check(x + 5, y + 5, enabled ? 0xFFFFFFFF : 0xFF8A8B8D);
        else BedrockUI.outline(x + w - 12, y + 5, 7, 7, 1, enabled ? 0xFFB1B2B5 : 0xFF6A6B6D);
        BedrockUI.button(fontRenderer, kx, y + 1, knob, knob, null,
                enabled ? BedrockUI.Style.SECONDARY : BedrockUI.Style.DISABLED, true, hov, false);
        if (enabled && onToggle != null) {
            final boolean wasOn = on;
            final int sx = x + w - 1 - knob / 2, sy = y + h / 2;
            cue(wasOn ? UiSounds.Cue.TOGGLE_OFF : UiSounds.Cue.TOGGLE_ON);
            hit(x, y, w, h, () -> {
                onToggle.run();
                if (!wasOn) sparkle(sx, sy, 12, BedrockUI.GREEN_LIGHT, 0xFFFFFFFF, 0xFFB8F5A0);
            });
        }
    }

    public void setTooltip(String... lines) {
        List<String> l = new ArrayList<String>();
        for (String s : lines) l.add(s);
        tooltip = l;
    }

    public void setTooltip(List<String> lines) {
        tooltip = lines;
    }

    // ---------------------------------------------------------------------
    // Text field focus
    // ---------------------------------------------------------------------

    public void focus(BedrockTextField field) {
        if (focused == field) return;
        if (focused != null) focused.blur();
        focused = field;
        if (field != null) field.setFocused(true);
    }

    public boolean isFocused(BedrockTextField field) {
        return focused == field;
    }

    @Override
    public void updateScreen() {
        if (focused != null) focused.tick();
    }

    // ---------------------------------------------------------------------
    // Popover menu
    // ---------------------------------------------------------------------

    /** Opens a dropdown whose top-right corner sits at (anchorRight, anchorBottom + 2). */
    public void openMenu(int anchorRight, int anchorBottom, String[] labels, Runnable[] actions) {
        menu = new Menu(anchorRight, anchorBottom + 2, labels, actions);
        menuAt = System.currentTimeMillis();
        UiSounds.menuOpen();
    }

    private void drawMenu() {
        final Menu m = menu;
        int w = 0;
        for (String l : m.labels) w = Math.max(w, fontRenderer.getStringWidth(l));
        w += 24;
        int rowH = 20;
        int h = m.labels.length * rowH + 2;
        int x = Math.max(4, Math.min(m.right - w, width - w - 4));
        int y = m.top;
        if (y + h > height - 4) y = Math.max(HEADER_H, m.top - h - 26);

        // Unfolds downwards.
        float open = BedrockUI.easeOut((System.currentTimeMillis() - menuAt) / 140f);
        BedrockUI.pushClip(x - 2, y - 2, w + 6, Math.max(1, (int) ((h + 6) * open)));
        BedrockUI.shadow(x, y, w, h);
        BedrockUI.rect(x, y, w, h, BedrockUI.OUTLINE);
        BedrockUI.rect(x + 1, y + 1, w - 2, h - 2, BedrockUI.PANEL_LIGHT);
        for (int i = 0; i < m.labels.length; i++) {
            final int idx = i;
            int ry = y + 1 + i * rowH;
            boolean hov = hover(x + 1, ry, w - 2, rowH);
            if (hov) BedrockUI.rect(x + 1, ry, w - 2, rowH, 0xFF5E5F61);
            if (i > 0) BedrockUI.rect(x + 6, ry, w - 12, 1, 0xFF3A3B3C);
            fontRenderer.drawString(m.labels[i], x + 10, ry + 6, BedrockUI.TEXT, false);
            if (hov) BedrockUI.rect(x + 1, ry + 2, 2, rowH - 4, BedrockUI.GREEN_LIGHT);
            hit(x + 1, ry, w - 2, rowH, () -> { menu = null; m.actions[idx].run(); });
        }
        BedrockUI.popClip();
    }

    // ---------------------------------------------------------------------
    // Overlay (modal panel drawn above the body, e.g. a picker)
    // ---------------------------------------------------------------------

    public interface Overlay {
        void draw(BedrockScreen screen, float partialTicks);
        /** Wheel notch (+1 up / -1 down). */
        default void onScroll(int direction) {}
    }

    public void openOverlay(Overlay o) {
        overlay = o;
    }

    public void closeOverlay() {
        overlay = null;
        focus(null);
    }

    public boolean overlayOpen() {
        return overlay != null;
    }

    // ---------------------------------------------------------------------
    // Toast
    // ---------------------------------------------------------------------

    /** Toast flavour: accent colour, icon and sound. */
    public enum ToastKind { SUCCESS, DELETED, WARN }

    /** Toast whose kind is guessed from the wording ("Deleted ..." / "Couldn't ..." / anything else). */
    public void toast(String text) {
        String t = text == null ? "" : text;
        ToastKind kind = t.startsWith("Deleted") || t.startsWith("Removed") ? ToastKind.DELETED
                : t.startsWith("Couldn't") || t.contains(" failed") ? ToastKind.WARN : ToastKind.SUCCESS;
        toast(text, kind);
    }

    /** Toast for "you can't do that yet" messages: amber, with a soft low note. */
    public void toastWarn(String text) {
        toast(text, ToastKind.WARN);
    }

    public void toast(String text, ToastKind kind) {
        toastText = text;
        toastKind = kind;
        toastAt = System.currentTimeMillis();
        toastSparkle = kind == ToastKind.SUCCESS;
        if (kind == ToastKind.SUCCESS) UiSounds.success();
        else if (kind == ToastKind.DELETED) UiSounds.deleted();
        else UiSounds.warn();
    }

    private static final long TOAST_MS = 3400;

    private void drawToast() {
        if (toastText == null) return;
        long age = System.currentTimeMillis() - toastAt;
        if (age > TOAST_MS) { toastText = null; return; }
        float in = BedrockUI.easeOutBack(age / 280f);
        float a = age > TOAST_MS - 450 ? (TOAST_MS - age) / 450f : Math.min(1f, age / 120f);
        int accent = toastKind == ToastKind.SUCCESS ? BedrockUI.GREEN_LIGHT
                : toastKind == ToastKind.DELETED ? BedrockUI.RED : 0xFFE0A040;
        int w = fontRenderer.getStringWidth(toastText) + 40;
        int h = 22;
        int x = (width - w) / 2;
        int y = height - 34 + Math.round((1 - in) * 22) + Math.round((1 - a) * 6);

        BedrockUI.rect(x + 2, y + h, w, 2, BedrockUI.fade(0x50000000, a));
        BedrockUI.rect(x, y, w, h, BedrockUI.fade(BedrockUI.OUTLINE, a));
        BedrockUI.rect(x + 1, y + 1, w - 2, h - 2, BedrockUI.fade(BedrockUI.PANEL_LIGHT, a));
        // Icon tile.
        BedrockUI.rect(x + 1, y + 1, 20, h - 2, BedrockUI.fade(accent, a));
        BedrockUI.rect(x + 1, y + 1, 20, 1, BedrockUI.fade(0x50FFFFFF, a));
        int ic = BedrockUI.fade(0xFFFFFFFF, a);
        if (toastKind == ToastKind.SUCCESS) {
            BedrockUI.check(x + 7, y + 8, ic);
        } else if (toastKind == ToastKind.DELETED) {
            for (int i = 0; i < 6; i++) {
                BedrockUI.rect(x + 8 + i, y + 8 + i, 1, 1, ic);
                BedrockUI.rect(x + 13 - i, y + 8 + i, 1, 1, ic);
            }
        } else {
            BedrockUI.rect(x + 10, y + 6, 2, 6, ic);
            BedrockUI.rect(x + 10, y + 14, 2, 2, ic);
        }
        // Time left, draining along the bottom edge.
        float left = 1f - age / (float) TOAST_MS;
        BedrockUI.rect(x + 21, y + h - 2, Math.round((w - 22) * left), 1, BedrockUI.fade(accent, a * 0.8f));
        if (a > 0.1f) fontRenderer.drawString(toastText, x + 28, y + 7, BedrockUI.fade(0xFFFFFFFF, a), false);

        if (toastSparkle && age > 140) {
            toastSparkle = false;
            sparkle(x + 11, y + h / 2, 10, BedrockUI.GREEN_LIGHT, BedrockUI.GOLD, 0xFFFFFFFF);
        }
    }

    // ---------------------------------------------------------------------
    // Dialog
    // ---------------------------------------------------------------------

    public void confirm(String title, String message, String confirmLabel, BedrockUI.Style style, Runnable onConfirm) {
        dialog = new Dialog(title, message, confirmLabel, style, onConfirm, null, null);
        dialogOpened();
    }

    /** Dialog with an extra secondary choice (e.g. Discard), shown left of Cancel. */
    public void confirm(String title, String message, String secondaryLabel, Runnable onSecondary,
                        String confirmLabel, BedrockUI.Style style, Runnable onConfirm) {
        dialog = new Dialog(title, message, confirmLabel, style, onConfirm, secondaryLabel, onSecondary);
        dialogOpened();
    }

    private void dialogOpened() {
        dialogAt = System.currentTimeMillis();
        UiSounds.dialogOpen();
    }

    public boolean dialogOpen() {
        return dialog != null;
    }

    private void drawDialog() {
        final Dialog d = dialog;
        long age = System.currentTimeMillis() - dialogAt;
        BedrockUI.rect(0, 0, width, height, BedrockUI.fade(0xB0000000, BedrockUI.easeOut(age / 160f)));

        int w = Math.min(d.secondaryLabel != null ? 320 : 280, width - 30);
        List<String> lines = fontRenderer.listFormattedStringToWidth(d.message, w - 24);
        int h = 30 + 14 + lines.size() * 10 + 16 + 24 + 12;
        int x = (width - w) / 2;
        int y = (height - h) / 2;

        // Pops in from slightly smaller, with a little overshoot.
        float s = 0.86f + 0.14f * BedrockUI.easeOutBack(age / 230f);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + w / 2f, y + h / 2f, 0);
        GlStateManager.scale(s, s, 1);
        GlStateManager.translate(-(x + w / 2f), -(y + h / 2f), 0);

        BedrockUI.shadow(x, y, w, h);
        BedrockUI.rect(x, y, w, h, BedrockUI.OUTLINE);
        BedrockUI.rect(x + 1, y + 1, w - 2, h - 2, BedrockUI.PANEL);
        BedrockUI.rect(x + 1, y + 1, w - 2, 26, BedrockUI.PANEL_LIGHT);
        BedrockUI.rect(x + 1, y + 27, w - 2, 1, BedrockUI.OUTLINE);
        fontRenderer.drawString(d.title, x + (w - fontRenderer.getStringWidth(d.title)) / 2, y + 10, BedrockUI.TEXT, true);

        int ty = y + 40;
        for (String line : lines) {
            fontRenderer.drawString(line, x + 12, ty, BedrockUI.TEXT_DIM, false);
            ty += 10;
        }

        int by = y + h - 12 - 24;
        int count = d.secondaryLabel != null ? 3 : 2;
        int bw = (w - 24 - 8 * (count - 1)) / count;
        int bx = x + 12;
        if (d.secondaryLabel != null) {
            button(bx, by, bw, 24, d.secondaryLabel, BedrockUI.Style.DANGER, true, () -> {
                dialog = null;
                d.onSecondary.run();
            });
            bx += bw + 8;
        }
        cue(UiSounds.Cue.NONE);
        button(bx, by, bw, 24, "Cancel", BedrockUI.Style.DARK, true, () -> { dialog = null; UiSounds.dialogClose(); });
        bx += bw + 8;
        button(bx, by, bw, 24, d.confirmLabel, d.style, true, () -> {
            dialog = null;
            d.onConfirm.run();
        });
        GlStateManager.popMatrix();
    }

    // ---------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        // Any left click blurs the focused field (committing it); a click on a field refocuses it.
        if (focused != null && mouseButton == 0) focus(null);

        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mouseButton != 0 && !(mouseButton == 1 && h.rightToo)) continue;
            if (mouseX >= h.r[0] && mouseX < h.r[2] && mouseY >= h.r[1] && mouseY < h.r[3]) {
                if (h.action == null) return; // blocker
                if (mouseButton == 0) {
                    UiSounds.play(h.cue);
                    ripples.add(new Ripple(mouseX, mouseY, h.r, System.currentTimeMillis()));
                }
                h.action.run();
                return;
            }
        }
        // Click outside any widget: dismiss popovers, otherwise let the screen handle it.
        if (menu != null) { menu = null; return; }
        if (dialog != null || overlay != null) return;
        onMouseDown(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (activeDrag != null) {
            activeDrag.drag(mouseX, mouseY);
            return;
        }
        if (dialog == null && menu == null && overlay == null) onDrag(mouseX, mouseY, clickedMouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (activeDrag != null) {
            activeDrag = null;
            return;
        }
        onRelease(mouseX, mouseY, state);
    }

    // ---------------------------------------------------------------------
    // Widget drags (sliders etc.)
    // ---------------------------------------------------------------------

    public interface DragHandler { void drag(int x, int y); }

    private DragHandler activeDrag;

    /** Routes mouse drags to {@code h} until the button is released. */
    public void startDrag(DragHandler h) {
        activeDrag = h;
    }

    /**
     * Horizontal slider bar. {@code t} is the 0..1 position; clicking or dragging anywhere on the bar
     * reports a new position to {@code onChange}.
     */
    public void slider(final int x, int y, final int w, float t, final java.util.function.DoubleConsumer onChange) {
        t = Math.max(0f, Math.min(1f, t));
        int h = 16, trackH = 8, thumbW = 10;
        int ty = y + (h - trackH) / 2;
        boolean hov = hover(x, y, w, h);
        boolean dragging = activeDrag != null && hov;

        BedrockUI.rect(x, ty, w, trackH, BedrockUI.OUTLINE);
        BedrockUI.rect(x + 1, ty + 1, w - 2, trackH - 2, 0xFF161718);
        int fill = (int) ((w - 2) * t);
        if (fill > 0) {
            BedrockUI.rect(x + 1, ty + 1, fill, trackH - 2, BedrockUI.GREEN);
            BedrockUI.rect(x + 1, ty + 1, fill, 1, 0xFF5FB043);
        }
        int thumbX = x + Math.round((w - thumbW) * t);
        BedrockUI.button(fontRenderer, thumbX, y, thumbW, h, null, BedrockUI.Style.SECONDARY, true, hov, dragging);

        final int usable = w - thumbW;
        hit(x, y, w, h, () -> {
            DragHandler handler = (mx2, my2) -> onChange.accept(Math.max(0, Math.min(1, (mx2 - x - thumbW / 2.0) / usable)));
            handler.drag(mx, my);
            startDrag(handler);
        });
    }

    /** Press that didn't land on any widget. */
    protected void onMouseDown(int x, int y, int button) {}
    protected void onDrag(int x, int y, int button) {}
    protected void onRelease(int x, int y, int button) {}

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || dialog != null || menu != null) return;
        if (overlay != null) overlay.onScroll(Integer.signum(wheel));
        else onScroll(Integer.signum(wheel));
    }

    /** Wheel notch: +1 = up, -1 = down. */
    protected void onScroll(int direction) {}

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (focused != null) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                focus(null);
            } else {
                focused.type(typedChar, keyCode);
            }
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (dialog != null) { dialog = null; UiSounds.dialogClose(); }
            else if (menu != null) menu = null;
            else if (overlay != null) closeOverlay();
            else goBack();
            return;
        }
        if (dialog == null && menu == null && overlay == null) onKey(typedChar, keyCode);
    }

    /** Key press with no text field focused and no modal open. */
    protected void onKey(char typedChar, int keyCode) {}

    protected void goBack() {
        mc.displayGuiScreen(parent);
    }

    @Override
    public void onGuiClosed() {
        focus(null);
        BedrockUI.resetClip();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        transition();
    }

    // ---------------------------------------------------------------------
    // Effects
    // ---------------------------------------------------------------------

    /** Plays the page-change animation (the body rises out of a short fade). */
    public void transition() {
        transitionAt = System.currentTimeMillis();
    }

    /**
     * Value easing towards 1 while {@code on} and towards 0 otherwise, at {@code speed} per second.
     * Keyed so it survives between frames; a new key starts at its target (no animation on first
     * sight). Keys not used for a couple of frames are forgotten.
     */
    public float anim(Object key, boolean on, float speed) {
        Anim a = anims.get(key);
        if (a == null) {
            a = new Anim();
            a.v = on ? 1 : 0;
            anims.put(key, a);
        }
        a.frame = frame;
        float target = on ? 1 : 0;
        float step = speed * frameDt;
        a.v = a.v < target ? Math.min(target, a.v + step) : Math.max(target, a.v - step);
        return a.v;
    }

    /** 0..1 progress of a one-shot light sweep that starts when {@code hovered} turns true; -1 otherwise. */
    public float hoverSweep(Object key, boolean hovered) {
        Anim a = anims.get(key);
        if (a == null) {
            a = new Anim();
            a.start = -1;
            anims.put(key, a);
        }
        a.frame = frame;
        if (!hovered) {
            a.start = -1;
            return -1;
        }
        if (a.start < 0) a.start = System.currentTimeMillis();
        return (System.currentTimeMillis() - a.start) / 450f;
    }

    private void pruneAnims() {
        for (Iterator<Anim> it = anims.values().iterator(); it.hasNext(); ) {
            if (frame - it.next().frame > 2) it.remove();
        }
    }

    /** Small burst of sparks around a point. */
    public void sparkle(float x, float y, int count, int... colors) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            double ang = r.nextDouble(Math.PI * 2);
            float speed = 25 + r.nextFloat() * 65;
            Particle p = new Particle();
            p.x = x;
            p.y = y;
            p.vx = (float) Math.cos(ang) * speed;
            p.vy = (float) Math.sin(ang) * speed - 20;
            p.gravity = 90;
            p.max = p.life = 0.35f + r.nextFloat() * 0.45f;
            p.size = r.nextFloat() < 0.3f ? 2 : 1;
            p.color = colors[r.nextInt(colors.length)];
            particles.add(p);
        }
    }

    /** Celebration: confetti thrown up from both bottom corners of the screen. */
    public void confetti(int count) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int[] colors = {BedrockUI.GREEN_LIGHT, BedrockUI.GOLD, BedrockUI.BLUE, 0xFFE06464, 0xFFFFFFFF, 0xFFB57EDC};
        for (int i = 0; i < count; i++) {
            boolean leftSide = i % 2 == 0;
            Particle p = new Particle();
            p.x = leftSide ? width * 0.15f + r.nextFloat() * 30 : width * 0.85f - r.nextFloat() * 30;
            p.y = height + 4;
            float speed = 180 + r.nextFloat() * 170;
            double ang = Math.toRadians(leftSide ? -60 - r.nextDouble(25) : -120 + r.nextDouble(25));
            p.vx = (float) Math.cos(ang) * speed;
            p.vy = (float) Math.sin(ang) * speed;
            p.gravity = 260;
            p.drag = 0.9f;
            p.max = p.life = 1.4f + r.nextFloat() * 0.9f;
            p.size = 2 + r.nextInt(2);
            p.color = colors[r.nextInt(colors.length)];
            p.spin = 0.01f + r.nextFloat() * 6.28f;
            particles.add(p);
        }
    }

    private void drawRipples(long now) {
        for (Iterator<Ripple> it = ripples.iterator(); it.hasNext(); ) {
            Ripple rp = it.next();
            float t = (now - rp.at) / 380f;
            if (t >= 1) { it.remove(); continue; }
            float dx = Math.max(rp.x - rp.r[0], rp.r[2] - rp.x), dy = Math.max(rp.y - rp.r[1], rp.r[3] - rp.y);
            float radius = (float) Math.sqrt(dx * dx + dy * dy) * BedrockUI.easeOut(t);
            BedrockUI.pushClip(rp.r[0], rp.r[1], rp.r[2] - rp.r[0], rp.r[3] - rp.r[1]);
            BedrockUI.disc(rp.x, rp.y, radius, BedrockUI.fade(0x55FFFFFF, 1 - t));
            BedrockUI.popClip();
        }
    }

    private void drawParticles() {
        float dt = frameDt;
        for (Iterator<Particle> it = particles.iterator(); it.hasNext(); ) {
            Particle p = it.next();
            p.life -= dt;
            if (p.life <= 0 || p.y > height + 40) { it.remove(); continue; }
            p.vy += p.gravity * dt;
            if (p.drag > 0) p.vx *= (float) Math.pow(p.drag, dt * 4);
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            float a = Math.min(1f, p.life / (p.max * 0.4f));
            float w = p.size, h = p.size;
            if (p.spin != 0) {
                // Confetti flutter: the strip turns, so its width wobbles.
                p.spin += dt * 9;
                w = Math.max(0.6f, Math.abs((float) Math.cos(p.spin)) * p.size * 1.6f);
            }
            BedrockUI.rectF(p.x - w / 2, p.y - h / 2, p.x + w / 2, p.y + h / 2, BedrockUI.fade(p.color, a));
        }
    }

    /** Soft tick when the pointer moves onto a different clickable region. */
    private void hoverTick() {
        int[] over = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx >= h.r[0] && mx < h.r[2] && my >= h.r[1] && my < h.r[3]) {
                if (h.action != null) over = h.r;
                break;
            }
        }
        boolean moved = lastMx != Integer.MIN_VALUE && (mx != lastMx || my != lastMy);
        if (over != null && moved && !Arrays.equals(over, lastHoverRect)) UiSounds.hover();
        lastHoverRect = over;
        lastMx = mx;
        lastMy = my;
    }

    @Override
    public boolean doesGuiPauseGame() {
        // Opened from the pause menu, the store keeps singleplayer paused (no effect on servers).
        return true;
    }

    // ---------------------------------------------------------------------

    private static final class Hit {
        final int[] r;
        final Runnable action;
        final boolean rightToo;
        final UiSounds.Cue cue;
        Hit(int[] r, Runnable action, boolean rightToo, UiSounds.Cue cue) {
            this.r = r;
            this.action = action;
            this.rightToo = rightToo;
            this.cue = cue;
        }
    }

    private static final class Anim {
        float v;
        long start;
        int frame;
    }

    private static final class Ripple {
        final float x, y;
        final int[] r;
        final long at;
        Ripple(float x, float y, int[] r, long at) { this.x = x; this.y = y; this.r = r; this.at = at; }
    }

    private static final class Particle {
        float x, y, vx, vy, gravity, drag, life, max, size, spin;
        int color;
    }

    private static final class Menu {
        final int right, top;
        final String[] labels;
        final Runnable[] actions;
        Menu(int right, int top, String[] labels, Runnable[] actions) {
            this.right = right;
            this.top = top;
            this.labels = labels;
            this.actions = actions;
        }
    }

    private static final class Dialog {
        final String title, message, confirmLabel, secondaryLabel;
        final BedrockUI.Style style;
        final Runnable onConfirm, onSecondary;
        Dialog(String title, String message, String confirmLabel, BedrockUI.Style style, Runnable onConfirm,
               String secondaryLabel, Runnable onSecondary) {
            this.title = title;
            this.message = message;
            this.confirmLabel = confirmLabel;
            this.style = style;
            this.onConfirm = onConfirm;
            this.secondaryLabel = secondaryLabel;
            this.onSecondary = onSecondary;
        }
    }

    /** Eased scroll value. Call {@link #value()} once per frame. */
    public static final class SmoothScroll {
        private float target, current;
        private long last = System.currentTimeMillis();

        public void add(float d) { target += d; }
        public void set(float v) { target = v; current = v; }
        public float target() { return target; }

        public void clamp(float max) {
            target = Math.max(0, Math.min(target, Math.max(0, max)));
        }

        public float value() {
            long now = System.currentTimeMillis();
            float dt = Math.min(0.1f, (now - last) / 1000f);
            last = now;
            current += (target - current) * Math.min(1f, dt * 14f);
            if (Math.abs(target - current) < 0.3f) current = target;
            return current;
        }
    }
}
