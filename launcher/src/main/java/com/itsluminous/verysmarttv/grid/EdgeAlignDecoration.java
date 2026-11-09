package com.itsluminous.verysmarttv.grid;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.itsluminous.verysmarttv.R;

/**
 * The grid's tiles share the header's edges: the first column's tile starts where the clock does and the last
 * column's ends where the pills do, with the same gap between every pair. A GridLayoutManager centres each tile in
 * its cell, which left the first tile a cell's half-gap in from the header; this decoration insets each tile in
 * its cell by its column's share of the free space instead, {@link #insetsPx}: none on the left for column 0, all
 * of it for the last. A full-span item (the All apps row) and a sideways single row are left alone.
 */
public final class EdgeAlignDecoration extends RecyclerView.ItemDecoration {
    private final boolean rtl;
    private final int[] insets = new int[2];

    /** rtl: the grid lays its spans out from the right (the Hebrew home), so column 0 is the rightmost. */
    public EdgeAlignDecoration(boolean rtl) {
        this.rtl = rtl;
    }

    /**
     * The left and right inset, px, of the tile in column spanIndex of spanCount, in a grid innerPx wide (its width
     * less its padding) whose tiles are tilePx wide: tile i starts at i * (innerPx - tilePx) / (spanCount - 1), so the
     * first starts at the inner's left edge, the last ends at its right, and every gap between is the same. The cell
     * it sits in is the one GridLayoutManager lays out ({@link #cellStartPx}), so the insets leave the tile's own width.
     * One column, or a tile wider than its cell, centres as before.
     */
    static int[] insetsPx(int spanIndex, int spanCount, int innerPx, int tilePx, int[] out) {
        out[0] = out[1] = 0;
        if (spanCount <= 0 || innerPx <= 0 || tilePx <= 0) return out;
        int i = Math.max(0, Math.min(spanCount - 1, spanIndex));
        int start = cellStartPx(i, spanCount, innerPx), width = cellStartPx(i + 1, spanCount, innerPx) - start;
        if (width <= tilePx) return out;
        if (spanCount == 1) {
            out[0] = (width - tilePx) / 2;
            out[1] = width - tilePx - out[0];
            return out;
        }
        int left = Math.round(i * (innerPx - tilePx) / (float) (spanCount - 1)) - start;
        out[0] = Math.max(0, Math.min(width - tilePx, left));
        out[1] = width - tilePx - out[0];
        return out;
    }

    /**
     * Where cell i of spanCount starts in a grid innerPx wide, px: GridLayoutManager's own borders (calculateItemBorders),
     * which spread the pixels innerPx / spanCount leaves over the cells one at a time.
     */
    static int cellStartPx(int i, int spanCount, int innerPx) {
        int sizePerSpan = innerPx / spanCount, remainder = innerPx % spanCount, consumed = 0, border = 0;
        for (int s = 1; s <= i; s++) {
            int item = sizePerSpan;
            consumed += remainder;
            if (consumed > 0 && spanCount - consumed < remainder) {
                item += 1;
                consumed -= spanCount;
            }
            border += item;
        }
        return border;
    }

    /** The gap between two neighbouring tiles under this layout, px: what the span leaves once the tiles are placed. */
    static float gapPx(int spanCount, int innerPx, int tilePx) {
        if (spanCount <= 1) return 0f;
        return (innerPx - spanCount * tilePx) / (float) (spanCount - 1);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        RecyclerView.LayoutManager lm = parent.getLayoutManager();
        if (!(lm instanceof GridLayoutManager) || ((GridLayoutManager) lm).getOrientation() != RecyclerView.VERTICAL) return;
        if (!(view.getLayoutParams() instanceof GridLayoutManager.LayoutParams)) return;
        GridLayoutManager.LayoutParams lp = (GridLayoutManager.LayoutParams) view.getLayoutParams();
        int count = ((GridLayoutManager) lm).getSpanCount();
        if (lp.getSpanSize() != 1 || count <= 1) return;
        View card = view.findViewById(R.id.card);
        int tile = card == null ? 0 : card.getLayoutParams().width;
        if (tile <= 0) return;
        int inner = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
        insetsPx(lp.getSpanIndex(), count, inner, tile, insets);
        outRect.left += rtl ? insets[1] : insets[0];
        outRect.right += rtl ? insets[0] : insets[1];
    }
}
