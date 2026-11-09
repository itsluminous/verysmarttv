package com.itsluminous.traktshelf;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** The look: Outfit, frosted pills, rating chips. The same language as the launcher: thin large titles, glass over slate. */
final class Ui {
    static final int BG = 0xFF101828;
    static final int ACCENT = 0xFFED1C24;
    private static Typeface outfit;

    private Ui() {
    }

    static Typeface font(Context c) {
        if (outfit == null) outfit = c.getResources().getFont(R.font.outfit);
        return outfit;
    }

    static float dp(Context c, float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, c.getResources().getDisplayMetrics());
    }

    /** A frosted pill: translucent white slab with a hairline, brighter and white-edged while focused. */
    static StateListDrawable pill(Context c, float radiusDp) {
        float r = dp(c, radiusDp);
        GradientDrawable rest = new GradientDrawable();
        rest.setColor(0x1AFFFFFF);
        rest.setStroke((int) dp(c, 1), 0x33FFFFFF);
        rest.setCornerRadius(r);
        GradientDrawable focus = new GradientDrawable();
        focus.setColor(0x40FFFFFF);
        focus.setStroke((int) dp(c, 2), 0xE6FFFFFF);
        focus.setCornerRadius(r);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focus);
        s.addState(new int[]{}, rest);
        return s;
    }

    /** One rating chip: "IMDb 8.7", the border in the source's colour. Null value: no chip. */
    static TextView chip(Context c, String label, String value, int border) {
        if (value == null || value.isEmpty()) return null;
        TextView t = new TextView(c);
        t.setTypeface(font(c), Typeface.NORMAL);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTextColor(0xFFFFFFFF);
        t.setText(c.getString(R.string.rating_chip, label, value));
        t.setLetterSpacing(0.02f);
        int ph = (int) dp(c, 12), pv = (int) dp(c, 6);
        t.setPadding(ph, pv, ph, pv);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x1AFFFFFF);
        bg.setStroke((int) dp(c, 1), border);
        bg.setCornerRadius(dp(c, 999));
        t.setBackground(bg);
        return t;
    }

    static List<TextView> ratingChips(Context c, Item it) {
        List<TextView> out = new ArrayList<>();
        TextView imdb = chip(c, "IMDb", it.imdbRating, 0x99F5C518);
        TextView rt = chip(c, "RT", it.rtRating, 0x99FA320A);
        TextView trakt = chip(c, "Trakt", it.traktPercent(), 0xA6ED1C24);
        if (imdb != null) out.add(imdb);
        if (rt != null) out.add(rt);
        if (trakt != null) out.add(trakt);
        return out;
    }

    /**
     * A title that stays on one line: from maxSp down to minSp, whichever largest size fits the view's width, then an
     * ellipsis. The view gets a fixed height, one line at maxSp, so a shrunk title does not move what sits below it
     * (TextView's auto-size needs a bounded box, and with wrap_content it would never grow back after shrinking);
     * the text sits at the bottom of that box, next to the line under it. Needs a bounded width (match_parent or dp).
     */
    static void fitOneLine(TextView t, float maxSp, float minSp) {
        t.setMaxLines(1);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setHorizontallyScrolling(false);
        t.setIncludeFontPadding(false);
        t.setLineSpacing(0f, 1f);
        t.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.START);
        android.view.ViewGroup.LayoutParams lp = t.getLayoutParams();
        lp.height = oneLinePx(t, maxSp);
        t.setLayoutParams(lp);
        t.setAutoSizeTextTypeUniformWithConfiguration(Math.round(minSp), Math.round(maxSp), 1, TypedValue.COMPLEX_UNIT_SP);
    }

    /** The height of one line of the view's typeface at sp, without font padding, plus a pixel so that size still fits. */
    static int oneLinePx(TextView t, float sp) {
        android.graphics.Paint p = new android.graphics.Paint(t.getPaint());
        p.setTypeface(font(t.getContext()));
        p.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, t.getResources().getDisplayMetrics()));
        android.graphics.Paint.FontMetricsInt fm = p.getFontMetricsInt();
        return fm.descent - fm.ascent + t.getPaddingTop() + t.getPaddingBottom() + 1;
    }

    static void applyFont(View v) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            Typeface current = t.getTypeface();
            t.setTypeface(font(v.getContext()), current == null ? Typeface.NORMAL : current.getStyle());
        } else if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) applyFont(g.getChildAt(i));
        }
    }

    /**
     * The kicker for a title not out yet (Item.upcoming): "Coming 17 May", with the year when it is not this year's,
     * or "Unreleased" when only Trakt's status says so. Null for a title that is out.
     */
    static String coming(Context c, Item it) {
        if (!it.upcoming()) return null;
        long at = it.comingAt();
        if (at == 0) return c.getString(R.string.unreleased);
        int flags = android.text.format.DateUtils.FORMAT_SHOW_DATE | android.text.format.DateUtils.FORMAT_ABBREV_MONTH;
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        if (java.time.Instant.ofEpochMilli(at).atZone(zone).getYear() != java.time.LocalDate.now(zone).getYear())
            flags |= android.text.format.DateUtils.FORMAT_SHOW_YEAR;
        else flags |= android.text.format.DateUtils.FORMAT_NO_YEAR;
        return c.getString(R.string.coming, android.text.format.DateUtils.formatDateTime(c, at, flags));
    }
}
