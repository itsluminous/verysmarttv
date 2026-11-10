package com.itsluminous.verysmarttv.topbar;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.itsluminous.verysmarttv.R;

/**
 * One line of the score pill under the top-right pills (DESIGN.md §9.2, ADR-041, ADR-042): a state dot and the kicker
 * in the state colour, then the companion's line in white, then the row's sport icon at the end, all on one shade
 * ({@code score_line_bg}, its over-art twin while a backdrop is up). The row is the focus stop and speaks for its
 * children: its content description is the kicker, the line and the sport's name. GONE while there is nothing to
 * show; focusable only while visible, with the glass focus ring. The dot pulses only while the row is live and
 * animations are on. {@link ScoreLine} decides what shows; this view only draws it; {@link ScorePill} stacks the lines.
 */
public final class ScoreLineView extends LinearLayout {
    static final int DOT_DP = 8;
    static final long PULSE_MS = 900L;
    private final GradientDrawable dot = new GradientDrawable();
    private final TextView text;
    private final ImageView icon;
    @Nullable
    private ObjectAnimator pulse;
    @Nullable
    private ScoreLine.Text current;

    public ScoreLineView(@NonNull Context context) {
        this(context, null);
    }

    public ScoreLineView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.score_line_content, this, true);
        text = findViewById(R.id.scoreLineText);
        icon = findViewById(R.id.scoreSportIcon);
        int size = Math.round(DOT_DP * getResources().getDisplayMetrics().density);
        dot.setShape(GradientDrawable.OVAL);
        dot.setSize(size, size);
        dot.setBounds(0, 0, size, size);
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
            this.text.setText(null);
            icon.setImageDrawable(null);
            icon.setTag(null);
            setContentDescription(null);
            return;
        }
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (!text.kicker.isEmpty()) {
            sb.append(text.kicker);
            sb.setSpan(new ForegroundColorSpan(text.color), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append("  ");
        }
        sb.append(text.line);
        this.text.setText(sb);
        String spoken = text.kicker.isEmpty() ? text.line : text.kicker + " " + text.line;
        setContentDescription(getResources().getString(R.string.score_line_description, spoken, getResources().getString(SportIcons.nameOf(text.sport))));
        if (text.dot) {
            dot.setColor(text.color);
            dot.setAlpha(255);
            this.text.setCompoundDrawablesRelative(dot, null, null, null);
        } else {
            this.text.setCompoundDrawablesRelative(null, null, null, null);
        }
        icon.setImageResource(SportIcons.forSport(text.sport));
        icon.setTag(SportIcons.tag(getResources(), text.sport));
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

    /** The text as drawn: the kicker, two spaces, the line; empty while hidden. */
    @NonNull
    public CharSequence text() {
        CharSequence t = text.getText();
        return t == null ? "" : t;
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
