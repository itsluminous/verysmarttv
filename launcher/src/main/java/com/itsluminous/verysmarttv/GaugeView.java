package com.itsluminous.verysmarttv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;

import java.util.Locale;

/**
 * widgets: one ring gauge, drawn with Canvas. A frosted glass disc in the pills' finish (pill_bg: a white gradient,
 * brighter at the top, inside a thin white rim), a thin track ring on it, an arc from twelve o'clock clockwise for the
 * percent full with a soft glow, the percent inside the ring and a small caps label under the disc (RAM, CPU,
 * STORAGE). Fixed size: DISC_DP across whatever the label, so three gauges are the same width and always fit beside
 * the date; a label wider than the disc is set smaller (fitLabelSp), never clipped and never widening the gauge.
 */
public class GaugeView extends View {

    /** The disc's diameter (the gauge's width), the ring's outer diameter inside it, its stroke, dp. */
    static final float DISC_DP = 48f, RING_DP = 44f, STROKE_DP = 3f;
    /** The gap from the disc to the label and the label's line, dp. */
    static final float LABEL_GAP_DP = 3f, LABEL_LINE_DP = 13f;
    /** The label's size: its full size, and the smallest it shrinks to when it is wider than the gauge, sp. */
    static final float LABEL_SP = 10f, LABEL_MIN_SP = 7f, LABEL_TRACKING = 0.08f;
    /** The glow around the arc: how much wider than the arc it is, dp. */
    static final float GLOW_DP = 2f;

    /** pill_bg's frost: white 18% at the top to 8% at the bottom, inside a 1dp white 22% rim. */
    static final int FROST_TOP = 0x2EFFFFFF, FROST_BOTTOM = 0x14FFFFFF, RIM = 0x38FFFFFF;
    /** pill_bg_over_art's dark base under the frost while the carousel's backdrop is behind the top bar. */
    static final int OVER_ART_BASE = 0x66000000;
    /** The track white 20%, the arc white 90% with a glow at 15%, the percent white 92%, the label white 70%. */
    static final int TRACK = 0x33FFFFFF, ARC = 0xE6FFFFFF, GLOW = 0x26FFFFFF, VALUE = 0xEBFFFFFF, LABEL = 0xB3FFFFFF;

    private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint frost = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ring = new RectF();
    /** Pixels per dp, and per sp (the TV's font scale included). */
    private final float density, scaledDensity;
    private int percent = -1;
    private boolean overArt;
    private String labelText = "";
    private String valueText = "";

    public GaugeView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        scaledDensity = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, getResources().getDisplayMetrics());
        float stroke = STROKE_DP * density, disc = DISC_DP * density;
        base.setColor(OVER_ART_BASE);
        frost.setShader(new LinearGradient(0, 0, 0, disc, FROST_TOP, FROST_BOTTOM, Shader.TileMode.CLAMP));
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(density);
        rim.setColor(RIM);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(stroke);
        track.setColor(TRACK);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(stroke + 2 * GLOW_DP * density);
        glow.setStrokeCap(Paint.Cap.ROUND);
        glow.setColor(GLOW);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(stroke);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(ARC);
        Typeface medium = UiFont.get(context, R.string.font_medium);
        value.setTypeface(medium);
        value.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, getResources().getDisplayMetrics()));
        value.setColor(VALUE);
        value.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(medium);
        label.setTextSize(LABEL_SP * scaledDensity);
        label.setLetterSpacing(LABEL_TRACKING);
        label.setColor(LABEL);
        label.setTextAlign(Paint.Align.CENTER);
        setWillNotDraw(false);
    }

    /** The caps label under the ring, in the TV's language's upper case, at the size that fits the gauge. */
    void setLabel(CharSequence text) {
        String caps = text == null ? "" : text.toString().toUpperCase(Locale.getDefault());
        if (caps.equals(labelText)) return;
        labelText = caps;
        setLabelSp(fittedLabelSp());
        describe();
        invalidate();
    }

    /**
     * The size, sp, at which this gauge's own label fits its width: LABEL_SP when it does, else the largest half-sp
     * step down to LABEL_MIN_SP that fits (fitLabelSp). The three gauges in a row share the smallest of theirs
     * (MainActivity.setUpWidgets), so RAM, CPU and STORAGE read in one size.
     */
    float fittedLabelSp() {
        if (labelText.isEmpty()) return LABEL_SP;
        float size = label.getTextSize();
        label.setTextSize(LABEL_SP * scaledDensity);
        float atFull = label.measureText(labelText);
        float room = widthPx(density) - 2, sp = fitLabelSp(atFull, widthPx(density));
        // Glyph widths do not scale quite linearly (hinting): step down further if the estimate still overflows.
        label.setTextSize(sp * scaledDensity);
        while (sp > LABEL_MIN_SP && label.measureText(labelText) > room) {
            sp -= 0.5f;
            label.setTextSize(sp * scaledDensity);
        }
        label.setTextSize(size);
        return sp;
    }

    /** The label's size, sp. */
    void setLabelSp(float sp) {
        float px = sp * scaledDensity;
        if (px == label.getTextSize()) return;
        label.setTextSize(px);
        invalidate();
    }

    float labelSp() {
        return label.getTextSize() / scaledDensity;
    }

    /**
     * The label size that fits: the full LABEL_SP when the label's width at that size (its tracking included) is
     * within the gauge's width less a pixel each side; otherwise the largest half-sp step, down to LABEL_MIN_SP,
     * that is. Text width scales with its size, so no second measure is needed. LABEL_MIN_SP even if that still
     * does not fit: a label that long is a translation to shorten, and 7sp is as small as the label can read.
     */
    static float fitLabelSp(float widthAtFullSpPx, int gaugeWidthPx) {
        float room = gaugeWidthPx - 2;
        if (widthAtFullSpPx <= room || widthAtFullSpPx <= 0) return LABEL_SP;
        float exact = LABEL_SP * room / widthAtFullSpPx;
        float stepped = (float) (Math.floor(exact * 2) / 2.0);
        return Math.max(LABEL_MIN_SP, stepped);
    }

    /** What a screen reader (and a uiautomator dump) gets for the gauge: its label and its percent. */
    private void describe() {
        setContentDescription(valueText.isEmpty() ? labelText : labelText + " " + valueText);
    }

    /** 0 to 100 fills the arc and writes the percent inside; anything else clears both (the figure cannot be read). */
    void setPercent(int p) {
        int clamped = p < 0 ? -1 : Math.min(100, p);
        if (clamped == percent) return;
        percent = clamped;
        valueText = clamped < 0 ? "" : getResources().getString(R.string.widget_percent, clamped);
        describe();
        invalidate();
    }

    int percent() {
        return percent;
    }

    /**
     * The carousel's backdrop behind the top bar: the disc gets pill_bg_over_art's dark base under its frost, as the
     * pills do (MainActivity.setPillsOverArt), so the white figures read over a bright sky; off again with it.
     */
    void setOverArt(boolean on) {
        if (overArt == on) return;
        overArt = on;
        invalidate();
    }

    boolean overArt() {
        return overArt;
    }

    /** The arc's sweep, degrees, for a percent: 3.6 per point, clockwise from twelve o'clock. */
    static float sweepDegrees(int percent) {
        return Math.max(0, Math.min(100, percent)) * 3.6f;
    }

    /** The gauge's width: the disc's diameter, the ring's and 4dp, the same for every label. */
    static int widthPx(float density) {
        return Math.round(DISC_DP * density);
    }

    static int heightPx(float density) {
        return Math.round((DISC_DP + LABEL_GAP_DP + LABEL_LINE_DP) * density);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(resolveSize(widthPx(density), widthMeasureSpec), resolveSize(heightPx(density), heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float disc = DISC_DP * density, size = RING_DP * density, half = STROKE_DP * density / 2f;
        float cx = getWidth() / 2f, cy = disc / 2f;
        float inset = (disc - size) / 2f;
        // The disc: the dark base over art, the frost, the rim half a pixel in so it is not clipped at the edge.
        if (overArt) canvas.drawCircle(cx, cy, disc / 2f, base);
        canvas.drawCircle(cx, cy, disc / 2f, frost);
        canvas.drawCircle(cx, cy, disc / 2f - density / 2f, rim);
        float left = cx - size / 2f;
        ring.set(left + half, inset + half, left + size - half, inset + size - half);
        canvas.drawOval(ring, track);
        if (percent >= 0) {
            float sweep = sweepDegrees(percent);
            if (sweep > 0) {
                canvas.drawArc(ring, -90f, sweep, false, glow);
                canvas.drawArc(ring, -90f, sweep, false, arc);
            }
            // The percent on the ring's centre line: half the cap height up from the baseline centres the digits.
            float ty = cy - (value.ascent() + value.descent()) / 2f;
            canvas.drawText(valueText, cx, ty, value);
        }
        if (!labelText.isEmpty()) {
            float top = disc + LABEL_GAP_DP * density;
            float baseline = top + LABEL_LINE_DP * density / 2f - (label.ascent() + label.descent()) / 2f;
            canvas.drawText(labelText, cx, baseline, label);
        }
    }
}
