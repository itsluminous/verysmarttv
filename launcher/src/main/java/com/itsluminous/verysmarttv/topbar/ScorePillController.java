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

/**
 * The score pill on Home (DESIGN.md §9.2): owns the {@link ScorePill} view and its {@link ScoreFeed} reader, places the
 * pill under the end of the pill row after every layout of the bar, turns each row into text through
 * {@link ScoreLine}, executes the {@link TopBarFocus} verdicts for its keys, and opens the companion on OK (the
 * scoreboard for the row's event, with {@link ScoreContract#EXTRA_EVENT_ID}) and its follow picker on a long press.
 * Built by {@code TopBarController} only while score_pill is in force; without it there is no reader at all.
 */
public final class ScorePillController {
    static final int BELOW_PILLS_DP = 8;
    final HomeHost host;
    final FrameLayout root;
    final ScorePill pill;
    private final ScoreFeed feed;
    private final Rect scratch = new Rect();
    @Nullable
    private ScoreFeed.Row row;

    public ScorePillController(@NonNull HomeHost host, @NonNull FrameLayout root) {
        this.host = host;
        this.root = root;
        pill = host.findViewById(R.id.scorePill);
        feed = new ScoreFeed(host.activity(), host.handler(), this::onPill);
        View bar = host.findViewById(R.id.topButtons);
        bar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> place(v));
        pill.setOnClickListener(v -> open());
        pill.setOnLongClickListener(v -> {
            host.open(new Intent().setComponent(ScoreContract.FOLLOW_ACTIVITY));
            return true;
        });
        pill.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            TopBarFocus.Verdict verdict = TopBarFocus.onScorePillKey(keyCode, host.topBar().bar(), host.carousel().showing());
            switch (verdict.stop) {
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
        });
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

    /** The feed's row: the pill's text, or GONE; focus leaves a pill that hides. */
    void onPill(@Nullable ScoreFeed.Row row) {
        this.row = row;
        ScoreLine.Text text = row == null ? ScoreLine.of(null, null, null, null, false)
                : ScoreLine.of(row.state, row.kicker, row.line, row.color, row.stale);
        boolean hadFocus = pill.hasFocus();
        pill.show(text, host.state().animations && !host.state().reducedMotion);
        if (!text.shown && hadFocus) host.grid().focusTile(host.grid().lastFocused());
    }

    /** OK: the scoreboard for the row's event; the companion's main screen when the row names none. */
    private void open() {
        Intent i;
        if (row != null && row.eventId != null) {
            i = new Intent().setComponent(ScoreContract.SCOREBOARD_ACTIVITY).putExtra(ScoreContract.EXTRA_EVENT_ID, row.eventId);
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

    public void focus() {
        if (shown()) pill.requestFocus();
    }

    /** onResume: the reader observes and reads; the pill shows the last row until the new one lands. */
    public void onResume() {
        feed.start();
    }

    /** onPause: no reads while Home is off screen. */
    public void onPause() {
        feed.stop();
    }
}
