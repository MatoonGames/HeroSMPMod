package com.matoon.herosmp.client.gui.bedrock;

import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.SoundEvents;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Base for every store screen. It provides the Bedrock header bar with a back button, a click
 * system for custom-drawn widgets (hit regions are registered while drawing, so what you see is
 * exactly what you can click, clipping included), text-field focus, drag callbacks, popover menus,
 * modal overlays, Bedrock-style dialogs, toasts, and smooth scrolling. Subclasses implement
 * {@link #drawBody}.
 */
public abstract class BedrockScreen extends GuiScreen {

    public static final int HEADER_H = 30;

    protected final GuiScreen parent;
    protected String title;
    /** Show the Beyonder wordmark before the title (used on the store's home screen). */
    protected boolean headerLogo;

    protected int mx, my;
    private final List<Hit> hits = new ArrayList<Hit>();
    private boolean suppressInput;

    private Dialog dialog;
    private Menu menu;
    private Overlay overlay;
    private BedrockTextField focused;
    private String toastText;
    private long toastAt;
    private List<String> tooltip;

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
        BedrockUI.resetClip();

        BedrockUI.backdrop(width, height);

        boolean modal = dialog != null || menu != null || overlay != null;
        suppressInput = modal;
        BedrockUI.pushClip(0, HEADER_H, width, height - HEADER_H);
        drawBody(partialTicks);
        BedrockUI.popClip();

        BedrockUI.clearDepth();
        drawHeader();
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
        if (suppressInput) return;
        int[] r = clipped(x, y, w, h);
        if (r != null) hits.add(new Hit(r, action, rightToo));
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
        BedrockUI.button(fontRenderer, x, y, w, h, label, style, enabled, hov, hov && Mouse.isButtonDown(0));
        if (enabled && action != null) hit(x, y, w, h, action);
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
        // Divider between the two segments.
        BedrockUI.rect(x + mainW, y + 1, 1, h - 2 - BedrockUI.LIP + (down ? BedrockUI.LIP : 0), BedrockUI.OUTLINE);
        int cx = x + mainW + moreW / 2;
        int cy = y + 1 + (h - 2 - BedrockUI.LIP) / 2 + (down ? BedrockUI.LIP : 0);
        for (int i = -1; i <= 1; i++) BedrockUI.rect(cx - 1, cy - 1 + i * 4, 2, 2, style.text);
        hit(x + mainW, y, moreW, h, () -> onMore.open(ax, ay));
    }

    public interface MoreHandler { void open(int anchorRight, int anchorBottom); }

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
            hit(x + 1, ry, w - 2, rowH, () -> { menu = null; m.actions[idx].run(); });
        }
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

    public void toast(String text) {
        toastText = text;
        toastAt = System.currentTimeMillis();
    }

    private void drawToast() {
        if (toastText == null) return;
        long age = System.currentTimeMillis() - toastAt;
        if (age > 3200) { toastText = null; return; }
        float a = age < 200 ? age / 200f : (age > 2700 ? (3200 - age) / 500f : 1f);
        int alpha = (int) (a * 255) << 24;
        int w = fontRenderer.getStringWidth(toastText) + 26;
        int h = 22;
        int x = (width - w) / 2;
        int y = height - 34 + (int) ((1 - a) * 8);
        BedrockUI.rect(x, y, w, h, (alpha & 0xFF000000) | (BedrockUI.OUTLINE & 0xFFFFFF));
        BedrockUI.rect(x + 1, y + 1, w - 2, h - 2, (alpha & 0xFF000000) | (BedrockUI.PANEL_LIGHT & 0xFFFFFF));
        BedrockUI.rect(x + 1, y + 1, 3, h - 2, (alpha & 0xFF000000) | (BedrockUI.GREEN_LIGHT & 0xFFFFFF));
        if (a > 0.1f) fontRenderer.drawString(toastText, x + 14, y + 7, (alpha & 0xFF000000) | 0xFFFFFF, false);
    }

    // ---------------------------------------------------------------------
    // Dialog
    // ---------------------------------------------------------------------

    public void confirm(String title, String message, String confirmLabel, BedrockUI.Style style, Runnable onConfirm) {
        dialog = new Dialog(title, message, confirmLabel, style, onConfirm, null, null);
    }

    /** Dialog with an extra secondary choice (e.g. Discard), shown left of Cancel. */
    public void confirm(String title, String message, String secondaryLabel, Runnable onSecondary,
                        String confirmLabel, BedrockUI.Style style, Runnable onConfirm) {
        dialog = new Dialog(title, message, confirmLabel, style, onConfirm, secondaryLabel, onSecondary);
    }

    public boolean dialogOpen() {
        return dialog != null;
    }

    private void drawDialog() {
        final Dialog d = dialog;
        BedrockUI.rect(0, 0, width, height, 0xB0000000);

        int w = Math.min(d.secondaryLabel != null ? 320 : 280, width - 30);
        List<String> lines = fontRenderer.listFormattedStringToWidth(d.message, w - 24);
        int h = 30 + 14 + lines.size() * 10 + 16 + 24 + 12;
        int x = (width - w) / 2;
        int y = (height - h) / 2;

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
        button(bx, by, bw, 24, "Cancel", BedrockUI.Style.DARK, true, () -> dialog = null);
        bx += bw + 8;
        button(bx, by, bw, 24, d.confirmLabel, d.style, true, () -> {
            dialog = null;
            d.onConfirm.run();
        });
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
                    mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
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
            if (dialog != null) dialog = null;
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
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ---------------------------------------------------------------------

    private static final class Hit {
        final int[] r;
        final Runnable action;
        final boolean rightToo;
        Hit(int[] r, Runnable action, boolean rightToo) { this.r = r; this.action = action; this.rightToo = rightToo; }
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
