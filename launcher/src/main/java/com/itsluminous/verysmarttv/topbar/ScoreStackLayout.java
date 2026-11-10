package com.itsluminous.verysmarttv.topbar;

import android.graphics.Rect;

import androidx.annotation.NonNull;

/**
 * The score stack and the top shelf as independent layers (DESIGN.md §9.2, ADR-046), pure. The stack hangs under the
 * right end of the pill row and the shelf is placed from the dock line, so neither ever moves the other; what remains
 * is that the shelf's text can reach under the stack, and the carousel's backdrop shows through it. These three
 * functions give, from the numbers the controllers already have, the stack's bounds, the width the shelf's text may
 * take so it ends before the stack, and the region of the backdrop to fade under the stack. Table test:
 * {@code ScoreStackLayoutTest}.
 */
public final class ScoreStackLayout {
    private ScoreStackLayout() {}

    /**
     * The stack's bounds in the root's coordinates: {@code rows} lines of {@code lineHeightPx}, {@code gapPx} between
     * them, the top line's top at {@code topPx}, every line ending at {@code rightPx}, the widest {@code widthPx} wide.
     * Empty (zero height and width, at the top-right corner) for no rows.
     */
    @NonNull
    public static Rect stackBounds(int rows, int lineHeightPx, int gapPx, int topPx, int rightPx, int widthPx) {
        if (rows <= 0 || lineHeightPx <= 0) return new Rect(rightPx, topPx, rightPx, topPx);
        int height = rows * lineHeightPx + (rows - 1) * Math.max(0, gapPx);
        return new Rect(rightPx - Math.max(0, widthPx), topPx, rightPx, topPx + height);
    }

    /**
     * How wide the shelf's text block may be when it starts at {@code heroStartPx} (its left edge, or its right edge
     * when {@code rtl}, where the stack hangs under the bar's left end): {@code fullWidthPx} while the stack is empty
     * or the block, from {@code heroTopPx} to {@code heroBottomPx}, lies wholly above or below it; otherwise up to
     * {@code clearancePx} short of the stack's near edge, never negative: the text ends before the stack whatever it
     * costs, since a crossing reads worse than an ellipsis. A block that would not reach the stack at full width
     * keeps its full width.
     */
    public static int heroTextMaxWidth(@NonNull Rect stack, int heroStartPx, int fullWidthPx, int heroTopPx, int heroBottomPx, int clearancePx, boolean rtl) {
        if (stack.isEmpty() || heroBottomPx <= stack.top || heroTopPx >= stack.bottom) return fullWidthPx;
        int limit = rtl ? heroStartPx - clearancePx - stack.right : stack.left - clearancePx - heroStartPx;
        if (limit >= fullWidthPx) return fullWidthPx;
        return Math.max(0, limit);
    }

    /**
     * Where the carousel's backdrop is faded under the stack: the stack grown by {@code fadePx} towards the screen's
     * middle and below (the fade's run-out), up to the root's top edge and out to the bar's end of the screen
     * ({@code rootRightPx}, or 0 when {@code rtl}), since the pills above the stack already darken over art. Empty for
     * an empty stack.
     */
    @NonNull
    public static Rect shadeBounds(@NonNull Rect stack, int fadePx, int rootRightPx, boolean rtl) {
        if (stack.isEmpty()) return new Rect();
        return rtl ? new Rect(0, 0, stack.right + fadePx, stack.bottom + fadePx) : new Rect(stack.left - fadePx, 0, rootRightPx, stack.bottom + fadePx);
    }
}
