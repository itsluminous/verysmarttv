package com.itsluminous.verysmarttv.topbar;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.itsluminous.verysmarttv.R;
import com.itsluminous.verysmarttv.core.contract.ScoreContract;
import com.itsluminous.verysmarttv.feed.ScoreFeed;
import com.itsluminous.verysmarttv.grid.FocusRules;
import com.itsluminous.verysmarttv.home.HomeHost;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The score pill on Home (DESIGN.md §9.2, ADR-041): owns the {@link ScorePill} stack and its {@link ScoreFeed} reader,
 * places the stack under the end of the pill row after every layout of the bar, turns the companion's rows into up to
 * {@code score_rows} lines through {@link ScoreLine#stack}, executes the {@link TopBarFocus} verdicts for the keys on
 * each line, and opens the companion on OK (the scoreboard for that line's event, with
 * {@link ScoreContract#EXTRA_EVENT_ID}) and its follow picker on a long press (§9.2). Built by {@code TopBarController}
 * only while score_pill is in force; without it there is no reader at all.
 */
public final class ScorePillController {
    static final int BELOW_PILLS_DP = 8;
    final HomeHost host;
    final FrameLayout root;
    final ScorePill pill;
    /** score_rows after the Live score rows choice: the most lines the stack shows. */
    final int rows;
    private final ScoreFeed feed;
    private final Rect scratch = new Rect();
    /** What each line shows, top first; empty while the pill is hidden. */
    @NonNull
    private List<ScoreLine.Text> shown = Collections.emptyList();
    private final View.OnClickListener open = this::open;
    private final View.OnLongClickListener follow;
    private final View.OnKeyListener keys;

    public ScorePillController(@NonNull HomeHost host, @NonNull FrameLayout root, int rows) {
        this.host = host;
        this.root = root;
        this.rows = ScoreLine.clampRows(rows);
        pill = host.findViewById(R.id.scorePill);
        pill.setOverArt(host.state().pillsOverArt);
        feed = new ScoreFeed(host.activity(), host.handler(), this::onPill);
        View bar = host.findViewById(R.id.topButtons);
        bar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> place(v));
        follow = v -> {
            host.open(new Intent().setComponent(ScoreContract.FOLLOW_ACTIVITY));
            return true;
        };
        keys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            TopBarFocus.Verdict verdict = TopBarFocus.onScorePillKey(keyCode, pill.indexOfChild(v), host.topBar().bar(), host.carousel().showing());
            switch (verdict.stop) {
                case SCORE_PILL:
                    pill.focusRow(verdict.row);
                    return true;
                case PILL:
                    host.topBar().focusPill(verdict.pillId);
                    return true;
                case CAROUSEL:
                    host.carousel().focusCarousel();
                    return true;
                case GRID:
                    host.grid().focusTile(FocusRules.fromBarDown(host.grid().lastFocused(), host.state().columns, host.state().singleRow));
                    return true;
                case STAY:
                    return true;
                default:
                    return false;
            }
        };
    }

    /** End-aligned with the last pill, BELOW_PILLS_DP under the row, in the root's coordinates; mirrored for RTL. */
    private void place(View bar) {
        Rect r = scratch;
        r.set(0, 0, bar.getWidth(), bar.getHeight());
        root.offsetDescendantRectToMyCoords(bar, r);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pill.getLayoutParams();
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.topMargin = r.bottom + Math.round(BELOW_PILLS_DP * host.density());
        lp.setMarginEnd(host.isRtl() ? r.left : root.getWidth() - r.right);
        pill.setLayoutParams(lp);
    }

    /** The feed's rows: the lines the stack shows, or GONE; focus follows a line that moves and leaves a pill that hides. */
    void onPill(@NonNull List<ScoreFeed.Row> fresh) {
        List<ScoreLine.Text> texts = new ArrayList<>(fresh.size());
        for (ScoreFeed.Row r : fresh) texts.add(ScoreLine.of(r.state, r.eventId, r.kicker, r.line, r.color, r.stale));
        List<ScoreLine.Text> next = ScoreLine.stack(shown, texts, rows);
        int focusedRow = pill.focusedRow();
        String focusedEvent = focusedRow >= 0 && focusedRow < shown.size() ? shown.get(focusedRow).eventId : null;
        shown = next;
        pill.show(next, host.state().animations && !host.state().reducedMotion);
        for (int i = 0; i < pill.getChildCount(); i++) wire(pill.getChildAt(i));
        if (focusedRow < 0) return;
        if (next.isEmpty()) {
            host.grid().focusTile(host.grid().lastFocused());
            return;
        }
        // The focused event's line, wherever it went; else the nearest line that is left.
        int at = -1;
        for (int i = 0; i < next.size(); i++) if (focusedEvent != null && focusedEvent.equals(next.get(i).eventId)) at = i;
        pill.focusRow(at >= 0 ? at : focusedRow);
    }

    private void wire(View line) {
        if (line.hasOnClickListeners()) return;
        line.setOnClickListener(open);
        line.setOnLongClickListener(follow);
        line.setOnKeyListener(keys);
    }

    /** OK on a line: the scoreboard for its event; the companion's main screen when the row names none. */
    private void open(View line) {
        ScoreLine.Text text = pill.indexOfChild(line) < shown.size() ? shown.get(pill.indexOfChild(line)) : null;
        Intent i;
        if (text != null && text.eventId != null) {
            i = new Intent().setComponent(ScoreContract.SCOREBOARD_ACTIVITY).putExtra(ScoreContract.EXTRA_EVENT_ID, text.eventId);
        } else {
            PackageManager pm = host.activity().getPackageManager();
            i = pm.getLeanbackLaunchIntentForPackage(ScoreContract.PACKAGE);
            if (i == null) i = pm.getLaunchIntentForPackage(ScoreContract.PACKAGE);
            if (i == null) i = new Intent().setComponent(ScoreContract.SCOREBOARD_ACTIVITY);
        }
        host.open(i);
    }

    /** The pill is on screen: TopBarFocus counts it as the stop above the grid. */
    public boolean shown() {
        return pill.getVisibility() == View.VISIBLE;
    }

    /** The lines on screen, 0 while hidden: the Bar's scoreRows. */
    public int rowsShown() {
        return shown() ? pill.rows() : 0;
    }

    /** Focus line {@code row} of the stack (a TopBarFocus verdict), clamped to the lines shown. */
    public void focus(int row) {
        if (shown()) pill.focusRow(row);
    }

    /** Over the carousel's backdrop the lines darken their shade with the round pills. */
    public void setOverArt(boolean on) {
        pill.setOverArt(on);
    }

    /** onResume: the reader observes and reads; the pill shows the last rows until the new ones land. */
    public void onResume() {
        feed.start();
    }

    /** onPause: no reads while Home is off screen. */
    public void onPause() {
        feed.stop();
    }
}
