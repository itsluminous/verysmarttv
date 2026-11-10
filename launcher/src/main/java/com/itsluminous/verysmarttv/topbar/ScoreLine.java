package com.itsluminous.verysmarttv.topbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.itsluminous.verysmarttv.core.contract.ScoreContract;

import java.util.ArrayList;
import java.util.List;

/**
 * What the score pill shows for the {@code /pill} rows (DESIGN.md §9.2, ADR-015, ADR-041): the launcher renders the
 * companion's kicker and line and decides only presentation: whether a line shows at all, the kicker's colour, whether
 * a state dot leads it and pulses, the line cut to {@link ScoreContract#LINE_MAX_CHARS} with an ellipsis, dimming
 * while the row is stale, and ({@link #stack}) how many of the companion's rows the pill shows and in which order.
 * Pure; {@code ScoreLineTest} is its table.
 */
public final class ScoreLine {
    private ScoreLine() {
    }

    /** The kicker colours when the row names none: red for live, amber for a break, grey otherwise. */
    public static final int LIVE_COLOR = 0xFFE53935, BREAK_COLOR = 0xFFFFB300, GREY_COLOR = 0xFF9E9E9E;
    /** The pill's alpha while the row is stale. */
    public static final float STALE_ALPHA = 0.6f;

    /** The fewest and the most lines the pill stacks (score_rows). */
    public static final int MIN_ROWS = 1, MAX_ROWS = ScoreContract.MAX_ROWS;

    /** The pill's text and look for one row. */
    public static final class Text {
        /** False: the line is GONE (no row, state none, or no line). */
        public final boolean shown;
        /** The row's event, so a line keeps its place while the companion reorders; null when the row names none. */
        @Nullable
        public final String eventId;
        /** The state as the companion gave it; empty when hidden. */
        @NonNull
        public final String state;
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

        Text(boolean shown, @Nullable String eventId, @NonNull String state, @NonNull String kicker, @NonNull String line, int color, boolean dot, boolean pulse, boolean dim) {
            this.shown = shown;
            this.eventId = eventId;
            this.state = state;
            this.kicker = kicker;
            this.line = line;
            this.color = color;
            this.dot = dot;
            this.pulse = pulse;
            this.dim = dim;
        }

        static final Text HIDDEN = new Text(false, null, "", "", "", GREY_COLOR, false, false, false);

        @Override
        @NonNull
        public String toString() {
            if (!shown) return "hidden";
            return (dot ? (pulse ? "pulsing dot " : "dot ") : "") + "[" + kicker + "] " + line + (dim ? " (stale)" : "");
        }
    }

    /**
     * The line for a row's columns; {@code state} null means no row (provider absent or refused). {@code color} is the
     * companion's {@code #RRGGBB}, or null/invalid for the state's default.
     */
    @NonNull
    public static Text of(@Nullable String state, @Nullable String kicker, @Nullable String line, @Nullable String color, boolean stale) {
        return of(state, null, kicker, line, color, stale);
    }

    /** {@link #of} with the row's event id, what {@link #stack} keeps a line in place by. */
    @NonNull
    public static Text of(@Nullable String state, @Nullable String eventId, @Nullable String kicker, @Nullable String line, @Nullable String color, boolean stale) {
        if (state == null || ScoreContract.STATE_NONE.equals(state) || line == null || line.trim().isEmpty()) return Text.HIDDEN;
        boolean live = ScoreContract.STATE_LIVE.equals(state);
        boolean brk = ScoreContract.STATE_BREAK.equals(state);
        int argb = parseColor(color, live ? LIVE_COLOR : brk ? BREAK_COLOR : GREY_COLOR);
        return new Text(true, eventId == null || eventId.isEmpty() ? null : eventId, state, kicker == null ? "" : kicker.trim(), shorten(line.trim()), argb, live || brk, live, stale);
    }

    /** {@code rows} held to MIN_ROWS..MAX_ROWS: a theme or choice outside it reads as the nearer end. */
    public static int clampRows(int rows) {
        return Math.max(MIN_ROWS, Math.min(MAX_ROWS, rows));
    }

    /**
     * The lines the pill shows for the companion's rows: the shown ones, in the companion's order, the first
     * {@link #clampRows} of them. When those are the same events in the same states as {@code previous}, only
     * reordered, the previous order is kept: the companion turns its first row every 20 s while two matches are live,
     * which a one-line pill wants and a stacked one would show as its lines swapping places. Any other change (an
     * event gone, arrived, or in another state) takes the companion's order again, live first.
     */
    @NonNull
    public static List<Text> stack(@NonNull List<Text> previous, @NonNull List<Text> fresh, int rows) {
        int max = clampRows(rows);
        List<Text> kept = new ArrayList<>(max);
        for (Text t : fresh) {
            if (!t.shown) continue;
            kept.add(t);
            if (kept.size() == max) break;
        }
        if (!sameEventsAndStates(previous, kept)) return kept;
        List<Text> ordered = new ArrayList<>(kept.size());
        for (Text p : previous) {
            for (Text k : kept) {
                if (k.eventId != null && k.eventId.equals(p.eventId)) {
                    ordered.add(k);
                    break;
                }
            }
        }
        return ordered.size() == kept.size() ? ordered : kept;
    }

    private static boolean sameEventsAndStates(List<Text> a, List<Text> b) {
        if (a.size() != b.size() || a.size() < 2) return false;
        for (Text x : a) {
            if (x.eventId == null) return false;
            boolean found = false;
            for (Text y : b) {
                if (x.eventId.equals(y.eventId)) {
                    found = x.state.equals(y.state);
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
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
