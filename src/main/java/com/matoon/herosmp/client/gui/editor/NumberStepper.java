package com.matoon.herosmp.client.gui.editor;

import com.matoon.herosmp.client.gui.bedrock.BedrockScreen;
import com.matoon.herosmp.client.gui.bedrock.BedrockTextField;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI;
import net.minecraft.client.gui.GuiScreen;

import java.util.function.IntConsumer;

/**
 * Labelled numeric control: [−] [typed value] [+]. Click steps by 1, shift-click by 10; the value can
 * also be typed and is clamped to [min, max] when committed.
 */
final class NumberStepper {

    static final int HEIGHT = 20;

    private final int min, max;
    private final BedrockTextField field;
    private IntConsumer target;

    NumberStepper(BedrockScreen screen, int min, int max) {
        this.min = min;
        this.max = max;
        this.field = BedrockTextField.number(screen.font(), min, max, v -> { if (target != null) target.accept(v); });
    }

    /** Draws the stepper on one row of width {@code w}; returns the row height. */
    int draw(BedrockScreen s, int x, int y, int w, String label, int value, IntConsumer set) {
        this.target = set;
        int bw = 18, fw = 44;
        int fieldX = x + w - bw - fw - bw - 2;
        s.font().drawString(BedrockUI.trim(s.font(), label, fieldX - x - 6), x, y + 6, BedrockUI.TEXT_DIM, false);

        final int v = value;
        s.button(fieldX, y, bw, HEIGHT, "-", BedrockUI.Style.DARK, v > min,
                () -> set.accept(Math.max(min, v - step())));
        field.draw(s, fieldX + bw + 1, y, fw, HEIGHT, value);
        s.button(fieldX + bw + fw + 2, y, bw, HEIGHT, "+", BedrockUI.Style.DARK, v < max,
                () -> set.accept(Math.min(max, v + step())));
        return HEIGHT;
    }

    private static int step() {
        return GuiScreen.isShiftKeyDown() ? 10 : 1;
    }
}
