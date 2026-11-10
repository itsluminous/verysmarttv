package com.itsluminous.verysmarttv.topbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.itsluminous.verysmarttv.R;

import java.util.Locale;

/**
 * The RAM and Storage block right of the date (DESIGN.md §9.1, ADR-018): one view, two rows. Each row is a 12sp
 * label at the left and a 12sp "N GB free" at the right on one baseline, with a 2dp track under them spanning the
 * block, the used part filled light and the rest dark. The block is {@link #WIDTH_DP} wide and two {@link #PITCH_DP}
 * rows tall; a row whose figure could not be read is left blank. One view rather than six stock widgets: one measure
 * and one draw a tick.
 */
public final class ResourceBars extends View {
    public static final int WIDTH_DP = 150, PITCH_DP = 22;
    static final int TRACK_DP = 2, TEXT_SP = 12;
    static final int TRACK_COLOR = 0x4DFFFFFF, FILL_COLOR = 0xD9FFFFFF;
    static final int LABEL_COLOR = 0xA6FFFFFF, VALUE_COLOR = 0xF2FFFFFF;

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private boolean ramKnown, storageKnown;
    private long ramFree, ramTotal, storageFree, storageTotal;

    public ResourceBars(@NonNull Context context) {
        this(context, null);
    }

    public ResourceBars(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        text.setTextSize(TEXT_SP * getResources().getDisplayMetrics().scaledDensity);
        text.setTypeface(Typeface.create(getResources().getString(R.string.font_regular), Typeface.NORMAL));
        text.setLetterSpacing(0.04f);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** The latest reading; a row whose figure is not known is left blank. */
    public void setValues(boolean ramKnown, long ramFree, long ramTotal, boolean storageKnown, long storageFree, long storageTotal) {
        this.ramKnown = ramKnown;
        this.ramFree = ramFree;
        this.ramTotal = ramTotal;
        this.storageKnown = storageKnown;
        this.storageFree = storageFree;
        this.storageTotal = storageTotal;
        setContentDescription(ramKnown || storageKnown ? description() : null);
        invalidate();
    }

    public void setValues(@NonNull ResourceSampler.Sample s) {
        setValues(s.ramKnown, s.ramFree, s.ramTotal, s.storageKnown, s.storageFree, s.storageTotal);
    }

    /** "N GB free" for a row, from {@link Gb} and the unit strings. */
    @NonNull
    public String freeText(long bytes) {
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        int unit;
        switch (Gb.unit(bytes)) {
            case GB: unit = R.string.unit_gb; break;
            case MB: unit = R.string.unit_mb; break;
            default: unit = R.string.unit_b; break;
        }
        return getResources().getString(R.string.bars_free, Gb.amount(bytes, locale), getResources().getString(unit));
    }

    /** What the RAM row reads, or empty while unknown. */
    @NonNull
    public String ramText() {
        return ramKnown ? freeText(ramFree) : "";
    }

    /** What the Storage row reads, or empty while unknown. */
    @NonNull
    public String storageText() {
        return storageKnown ? freeText(storageFree) : "";
    }

    private String description() {
        StringBuilder sb = new StringBuilder();
        if (ramKnown) sb.append(getResources().getString(R.string.bars_ram)).append(' ').append(ramText());
        if (storageKnown) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(getResources().getString(R.string.bars_storage)).append(' ').append(storageText());
        }
        return sb.toString();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(Math.round(WIDTH_DP * density), widthSpec),
                resolveSize(Math.round(2 * PITCH_DP * density), heightSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float pitch = PITCH_DP * density;
        // Two rows centred in the view: the header aligns the block with the pills' 42dp, a row shorter than 44.
        float top = (getHeight() - 2 * pitch) / 2f;
        if (ramKnown) drawRow(canvas, top, getResources().getString(R.string.bars_ram), ramText(), Gb.usedShare(ramFree, ramTotal));
        if (storageKnown) drawRow(canvas, top + pitch, getResources().getString(R.string.bars_storage), storageText(), Gb.usedShare(storageFree, storageTotal));
    }

    private void drawRow(Canvas canvas, float top, String label, String value, float used) {
        float w = getWidth();
        float track = TRACK_DP * density;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        // The text's baseline sits above the track with a 4dp gap; the track ends the row.
        float trackTop = top + PITCH_DP * density - track - 2 * density;
        float baseline = trackTop - 4 * density;
        // Label at the start, value at the end, the fill growing from the start: mirrored for a right-to-left TV.
        text.setColor(LABEL_COLOR);
        text.setTextAlign(rtl ? Paint.Align.RIGHT : Paint.Align.LEFT);
        canvas.drawText(label, rtl ? w : 0, baseline, text);
        text.setColor(VALUE_COLOR);
        text.setTextAlign(rtl ? Paint.Align.LEFT : Paint.Align.RIGHT);
        canvas.drawText(value, rtl ? 0 : w, baseline, text);
        bar.setColor(TRACK_COLOR);
        rect.set(0, trackTop, w, trackTop + track);
        canvas.drawRoundRect(rect, track / 2, track / 2, bar);
        if (used > 0f) {
            bar.setColor(FILL_COLOR);
            float fill = Math.max(track, w * used);
            rect.set(rtl ? w - fill : 0, trackTop, rtl ? w : fill, trackTop + track);
            canvas.drawRoundRect(rect, track / 2, track / 2, bar);
        }
    }
}
