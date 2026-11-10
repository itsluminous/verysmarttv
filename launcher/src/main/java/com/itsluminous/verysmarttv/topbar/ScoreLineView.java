package com.itsluminous.verysmarttv.topbar;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.itsluminous.verysmarttv.R;

/**
 * One line of the score pill under the top-right pills (DESIGN.md §9.2, ADR-041): a state dot and the kicker in the
 * state colour, then the companion's line in white, on its own shade ({@code score_line_bg}, its over-art twin while a
 * backdrop is up). GONE while there is nothing to show; focusable only while visible, with the glass focus ring. The
 * dot pulses only while the row is live and animations are on. {@link ScoreLine} decides what shows; this view only
 * draws it; {@link ScorePill} stacks the lines.
 */
public final class ScoreLineView extends TextView {
    static final int DOT_DP = 8;
    static final long PULSE_MS = 900L;
    private final GradientDrawable dot = new GradientDrawable();
    @Nullable
    private ObjectAnimator pulse;
    @Nullable
    private ScoreLine.Text current;

    public ScoreLineView(@NonNull Context context) {
        this(context, null);
    }

    public ScoreLineView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        int size = Math.round(DOT_DP * getResources().getDisplayMetrics().density);
        dot.setShape(GradientDrawable.OVAL);
        dot.setSize(size, size);
        dot.setBounds(0, 0, size, size);
        setSingleLine(true);
        setEllipsize(TextUtils.TruncateAt.END);
        setTextColor(0xFFFFFFFF);   // the line is white; only the kicker takes the state colour
        setFocusable(false);
        setVisibility(GONE);
    }

    /** Shows {@code text}, or hides when it says so; {@code animations} gates the pulse. */
    public void show(@NonNull ScoreLine.Text text, boolean animations) {
        current = text;
        if (!text.shown) {
            stopPulse();
            setVisibility(GONE);
            setFocusable(false);
            setText(null);
            return;
        }
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (!text.kicker.isEmpty()) {
            sb.append(text.kicker);
            sb.setSpan(new ForegroundColorSpan(text.color), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append("  ");
        }
        sb.append(text.line);
        setText(sb);
        setContentDescription(text.kicker.isEmpty() ? text.line : text.kicker + " " + text.line);
        if (text.dot) {
            dot.setColor(text.color);
            dot.setAlpha(255);
            setCompoundDrawablesRelative(dot, null, null, null);
        } else {
            setCompoundDrawablesRelative(null, null, null, null);
        }
        setAlpha(text.dim ? ScoreLine.STALE_ALPHA : 1f);
        setVisibility(VISIBLE);
        setFocusable(true);
        if (text.dot && text.pulse && animations) startPulse();
        else stopPulse();
    }

    /** What the line shows now; null before the first {@link #show}. */
    @Nullable
    public ScoreLine.Text current() {
        return current;
    }

    /** Over the carousel's backdrop: the shade over a dark base, as the round pills; the padding is kept across the swap. */
    public void setOverArt(boolean on) {
        int l = getPaddingLeft(), t = getPaddingTop(), r = getPaddingRight(), b = getPaddingBottom();
        setBackgroundResource(on ? R.drawable.score_line_bg_over_art : R.drawable.score_line_bg);
        setPadding(l, t, r, b);
    }

    /** The dot is pulsing (live, animations on, pill on screen). */
    public boolean pulsing() {
        return pulse != null && (pulse.isStarted() || pulse.isRunning());
    }

    private void startPulse() {
        if (pulse != null && pulse.isRunning()) return;
        pulse = ObjectAnimator.ofInt(dot, "alpha", 255, 70);
        pulse.setDuration(PULSE_MS);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.start();
    }

    private void stopPulse() {
        if (pulse != null) {
            pulse.cancel();
            pulse = null;
        }
        dot.setAlpha(255);
    }

    @Override
    protected void onDetachedFromWindow() {
        stopPulse();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility != VISIBLE) stopPulse();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        // Home covered by an app: the animator is dropped; the feed's next row (onResume reads one) starts it again.
        if (visibility != VISIBLE) stopPulse();
    }
}
