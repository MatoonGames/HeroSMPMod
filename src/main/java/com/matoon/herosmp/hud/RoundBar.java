package com.matoon.herosmp.hud;

import net.minecraft.util.text.TextFormatting;

/**
 * Round progress bars travel as ordinary boss bars (so the server needs no extra packets and the
 * percent / players stay vanilla-synced), but their title is tagged so the client can swap the
 * vanilla bar for the themed round HUD. The tag is made of formatting codes that render as
 * nothing, so even a plain renderer shows a readable "label  value  extra" line.
 * <p>
 * Layout (S = section sign): {@code Sy Sb} + S kind + S flag + {@code Sr} + label + {@code Sz S7} + value
 * + {@code Sz S8} + extra.
 */
public final class RoundBar {

    private RoundBar() {}

    /** The section sign that starts every formatting code. */
    private static final char S = (char) 0xA7;
    private static final String MARKER = "" + S + 'y' + S + 'b';
    private static final String SEP = "" + S + 'z';

    /** Which round the bar belongs to; picks the HUD's chip label and colour. */
    public enum Kind {
        DUEL('D', "DUEL"), FFA('F', "FREE-FOR-ALL"), TRAINING('S', "TRAINING"),
        CROWNFALL('C', "CROWNFALL"), HUNGER_GAMES('H', "HUNGER GAMES");

        public final char code;
        public final String chip;

        Kind(char code, String chip) {
            this.code = code;
            this.chip = chip;
        }

        static Kind of(char c) {
            for (Kind k : values()) if (k.code == c) return k;
            return null;
        }
    }

    /** Mood of the bar: normal, a calm phase (grace), or the last seconds (urgent). */
    public enum Flag {
        NORMAL('-'), CALM('g'), URGENT('u');

        public final char code;

        Flag(char code) {
            this.code = code;
        }

        static Flag of(char c) {
            for (Flag f : values()) if (f.code == c) return f;
            return NORMAL;
        }
    }

    public static String encode(Kind kind, Flag flag, String label, String value, String extra) {
        return MARKER + S + kind.code + S + flag.code + TextFormatting.RESET + clean(label)
                + SEP + " " + TextFormatting.GRAY + clean(value)
                + SEP + " " + TextFormatting.DARK_GRAY + clean(extra);
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace(SEP, "");
    }

    /** m:ss for a tick count. */
    public static String clock(int ticks) {
        int secs = Math.max(0, (ticks + 19) / 20);
        return String.format("%d:%02d", secs / 60, secs % 60);
    }

    /** A decoded round bar title. */
    public static final class Parsed {
        public final Kind kind;
        public final Flag flag;
        public final String label, value, extra;

        Parsed(Kind kind, Flag flag, String label, String value, String extra) {
            this.kind = kind;
            this.flag = flag;
            this.label = label;
            this.value = value;
            this.extra = extra;
        }
    }

    /** Null when the title isn't a round bar (some other boss bar). */
    public static Parsed decode(String formatted) {
        if (formatted == null) return null;
        int at = formatted.indexOf(MARKER);
        if (at < 0 || formatted.length() < at + MARKER.length() + 4) return null;
        String rest = formatted.substring(at + MARKER.length());
        Kind kind = Kind.of(rest.charAt(1));
        if (kind == null) return null;
        Flag flag = Flag.of(rest.charAt(3));
        String[] parts = rest.substring(4).split(SEP, -1);
        String label = plain(parts[0]);
        String value = parts.length > 1 ? plain(parts[1]) : "";
        String extra = parts.length > 2 ? plain(parts[2]) : "";
        return new Parsed(kind, flag, label, value, extra);
    }

    /** Strips every section-sign code, including the non-standard ones used for the tag. */
    private static String plain(String s) {
        return s.replaceAll(S + ".", "").trim();
    }
}
