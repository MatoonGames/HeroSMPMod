package com.matoon.herosmp.client.gui.bedrock;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Bedrock-styled text input wrapping vanilla {@link GuiTextField}. Free-text fields report every
 * change; numeric fields accept digits only and commit a clamped value on blur / Enter. While a
 * numeric field isn't focused it mirrors the model value passed to {@link #draw}.
 */
public class BedrockTextField {

    private final GuiTextField field;
    private final boolean numeric;
    private final int min, max;
    private IntConsumer onCommitInt;
    private Consumer<String> onChange;
    private String lastText = "";

    private BedrockTextField(FontRenderer fr, int maxLength, boolean numeric, int min, int max) {
        // Screens only get their font when opened, and fields are often created in constructors,
        // so fall back to the game's font rather than keeping a null renderer.
        if (fr == null) fr = net.minecraft.client.Minecraft.getMinecraft().fontRenderer;
        this.field = new GuiTextField(0, fr, 0, 0, 10, 10);
        this.field.setEnableBackgroundDrawing(false);
        this.field.setMaxStringLength(maxLength);
        this.field.setTextColor(0xFFFFFF);
        this.numeric = numeric;
        this.min = min;
        this.max = max;
        if (numeric) this.field.setValidator(s -> s != null && s.matches("\\d{0,6}"));
    }

    public static BedrockTextField text(FontRenderer fr, int maxLength, Consumer<String> onChange) {
        BedrockTextField f = new BedrockTextField(fr, maxLength, false, 0, 0);
        f.onChange = onChange;
        return f;
    }

    public static BedrockTextField number(FontRenderer fr, int min, int max, IntConsumer onCommit) {
        BedrockTextField f = new BedrockTextField(fr, 6, true, min, max);
        f.onCommitInt = onCommit;
        return f;
    }

    public String getText() {
        return field.getText();
    }

    public void setText(String s) {
        field.setText(s);
        lastText = s;
    }

    // ---------------------------------------------------------------------

    /** Draws a free-text field. */
    public void draw(final BedrockScreen screen, int x, int y, int w, int h, String placeholder) {
        drawFrame(screen, x, y, w, h);
        if (field.getText().isEmpty() && !screen.isFocused(this) && placeholder != null) {
            screen.font().drawString(BedrockUI.trim(screen.font(), placeholder, w - 10), x + 5, y + (h - 8) / 2, BedrockUI.TEXT_MUTED, false);
        }
    }

    /** Draws a numeric field showing {@code value} whenever it isn't being edited. */
    public void draw(final BedrockScreen screen, int x, int y, int w, int h, int value) {
        if (!screen.isFocused(this)) {
            String v = String.valueOf(value);
            if (!v.equals(field.getText())) setText(v);
        }
        drawFrame(screen, x, y, w, h);
    }

    private void drawFrame(final BedrockScreen screen, int x, int y, int w, int h) {
        boolean focused = screen.isFocused(this);
        boolean hov = screen.hover(x, y, w, h);
        BedrockUI.rect(x, y, w, h, focused ? 0xFFE8E8E8 : BedrockUI.OUTLINE);
        BedrockUI.rect(x + 1, y + 1, w - 2, h - 2, hov || focused ? 0xFF1E1F21 : 0xFF161718);
        field.x = x + 5;
        field.y = y + (h - 8) / 2;
        field.width = w - 10;
        field.height = h;
        field.drawTextBox();
        screen.hit(x, y, w, h, () -> {
            screen.focus(this);
            field.mouseClicked(screen.mouseX(), screen.mouseY(), 0);
        });
    }

    // ---------------------------------------------------------------------
    // Called by BedrockScreen
    // ---------------------------------------------------------------------

    void setFocused(boolean f) {
        field.setFocused(f);
        if (f) field.setCursorPositionEnd();
    }

    void blur() {
        field.setFocused(false);
        if (numeric) commit();
    }

    void tick() {
        field.updateCursorCounter();
    }

    void type(char c, int key) {
        field.textboxKeyTyped(c, key);
        if (!numeric && !field.getText().equals(lastText)) {
            lastText = field.getText();
            if (onChange != null) onChange.accept(lastText);
        }
    }

    private void commit() {
        if (onCommitInt == null) return;
        String t = field.getText();
        if (t.isEmpty()) return; // leave the model unchanged; the field resyncs next frame
        try {
            int v = Integer.parseInt(t);
            onCommitInt.accept(Math.max(min, Math.min(max, v)));
        } catch (NumberFormatException ignored) {
            // The validator only admits digits; nothing to do.
        }
    }
}
