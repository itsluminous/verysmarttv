package com.itsluminous.verysmarttv.topbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.itsluminous.verysmarttv.core.contract.ScoreContract;

/**
 * What the score pill shows for one {@code /pill} row (DESIGN.md §9.2, ADR-015): the launcher renders the companion's
 * kicker and line and decides only presentation: whether the pill shows at all, the kicker's colour, whether a state
 * dot leads it and pulses, the line cut to {@link ScoreContract#LINE_MAX_CHARS} with an ellipsis, and dimming while
 * the row is stale. Pure; {@code ScoreLineTest} is its table.
 */
public final class ScoreLine {
    private ScoreLine() {
    }

    /** The kicker colours when the row names none: red for live, amber for a break, grey otherwise. */
    public static final int LIVE_COLOR = 0xFFE53935, BREAK_COLOR = 0xFFFFB300, GREY_COLOR = 0xFF9E9E9E;
    /** The pill's alpha while the row is stale. */
    public static final float STALE_ALPHA = 0.6f;

    /** The pill's text and look for one row. */
    public static final class Text {
        /** False: the pill is GONE (no row, state none, or no line). */
        public final boolean shown;
        /** The kicker as the companion gave it, possibly empty. */
        @NonNull
        public final String kicker;
        /** The line, at most LINE_MAX_CHARS, ellipsised. */
        @NonNull
        public final String line;
        /** ARGB colour of the dot and the kicker. */
        public final int color;
        /** A state dot leads the kicker (live and break). */
        public final boolean dot;
        /** The dot pulses (live; the view also asks for animations on). */
        public final boolean pulse;
        /** The pill dims to STALE_ALPHA. */
        public final boolean dim;

        Text(boolean shown, @NonNull String kicker, @NonNull String line, int color, boolean dot, boolean pulse, boolean dim) {
            this.shown = shown;
            this.kicker = kicker;
            this.line = line;
            this.color = color;
            this.dot = dot;
            this.pulse = pulse;
            this.dim = dim;
        }

        static final Text HIDDEN = new Text(false, "", "", GREY_COLOR, false, false, false);

        @Override
        @NonNull
        public String toString() {
            if (!shown) return "hidden";
            return (dot ? (pulse ? "pulsing dot " : "dot ") : "") + "[" + kicker + "] " + line + (dim ? " (stale)" : "");
        }
    }

    /**
     * The pill for a row's columns; {@code state} null means no row (provider absent or refused). {@code color} is the
     * companion's {@code #RRGGBB}, or null/invalid for the state's default.
     */
    @NonNull
    public static Text of(@Nullable String state, @Nullable String kicker, @Nullable String line, @Nullable String color, boolean stale) {
        if (state == null || ScoreContract.STATE_NONE.equals(state) || line == null || line.trim().isEmpty()) return Text.HIDDEN;
        boolean live = ScoreContract.STATE_LIVE.equals(state);
        boolean brk = ScoreContract.STATE_BREAK.equals(state);
        int argb = parseColor(color, live ? LIVE_COLOR : brk ? BREAK_COLOR : GREY_COLOR);
        return new Text(true, kicker == null ? "" : kicker.trim(), shorten(line.trim()), argb, live || brk, live, stale);
    }

    /** The line as the pill shows it: at most LINE_MAX_CHARS characters, the last one an ellipsis when cut. */
    @NonNull
    public static String shorten(@NonNull String line) {
        int max = ScoreContract.LINE_MAX_CHARS;
        if (line.length() <= max) return line;
        return line.substring(0, max - 1).trim() + "\u2026";
    }

    /** {@code #RRGGBB} or {@code #AARRGGBB} to ARGB; {@code fallback} for anything else. */
    public static int parseColor(@Nullable String color, int fallback) {
        if (color == null) return fallback;
        String c = color.trim();
        if (c.length() != 7 && c.length() != 9) return fallback;
        if (c.charAt(0) != '#') return fallback;
        try {
            long v = Long.parseLong(c.substring(1), 16);
            if (c.length() == 7) v |= 0xFF000000L;
            return (int) v;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
