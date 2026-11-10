package com.itsluminous.verysmarttv.topbar;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.itsluminous.verysmarttv.R;
import com.itsluminous.verysmarttv.core.contract.ScoreContract;

import java.util.List;

/**
 * The live-score pill under the top-right pills (DESIGN.md §9.2, ADR-041): up to {@link ScoreContract#MAX_ROWS}
 * {@link ScoreLineView}s stacked under one another, right-aligned, {@link #GAP_DP} apart, each on its own shade. GONE
 * while there is nothing to show. Row 0 is the top line; the bottom line is the one nearest the grid, where Up from a
 * tile lands ({@link TopBarFocus}). {@link ScoreLine#stack} decides which lines show; this view only draws them.
 */
public final class ScorePill extends LinearLayout {
    static final int GAP_DP = 4;

    public ScorePill(@NonNull Context context) {
        this(context, null);
    }

    public ScorePill(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setVisibility(GONE);
    }

    /** Shows one line per text, in order; hides the rest and the whole pill when there are none. {@code animations} gates the pulse. */
    public void show(@NonNull List<ScoreLine.Text> texts, boolean animations) {
        int n = Math.min(texts.size(), ScoreContract.MAX_ROWS);
        while (getChildCount() < n) addLine();
        for (int i = 0; i < getChildCount(); i++) {
            ScoreLineView line = (ScoreLineView) getChildAt(i);
            line.show(i < n ? texts.get(i) : ScoreLine.Text.HIDDEN, animations);
        }
        setVisibility(n == 0 ? GONE : VISIBLE);
    }

    private void addLine() {
        ScoreLineView line = (ScoreLineView) LayoutInflater.from(getContext()).inflate(R.layout.score_line, this, false);
        LayoutParams lp = (LayoutParams) line.getLayoutParams();
        if (getChildCount() > 0) lp.topMargin = Math.round(GAP_DP * getResources().getDisplayMetrics().density);
        addView(line, lp);
    }

    /** How many lines show. */
    public int rows() {
        int n = 0;
        for (int i = 0; i < getChildCount(); i++) if (getChildAt(i).getVisibility() == VISIBLE) n++;
        return n;
    }

    /** The line at {@code row} (0 the top); null when no such line shows. */
    @Nullable
    public ScoreLineView line(int row) {
        return row >= 0 && row < rows() ? (ScoreLineView) getChildAt(row) : null;
    }

    /** The row of the focused line, or -1 when none of them has focus. */
    public int focusedRow() {
        for (int i = 0; i < getChildCount(); i++) if (getChildAt(i).hasFocus()) return i;
        return -1;
    }

    /** Focuses the line at {@code row}, clamped to the lines that show; false when none shows. */
    public boolean focusRow(int row) {
        int n = rows();
        if (n == 0) return false;
        View line = getChildAt(Math.max(0, Math.min(row, n - 1)));
        return line.requestFocus();
    }
}
