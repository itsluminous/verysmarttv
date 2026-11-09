package com.itsluminous.verysmarttv.topbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.FrameLayout;

import com.itsluminous.verysmarttv.core.contract.ScoreContract;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * The score pill view (DESIGN.md §14.2 ScorePillTest): GONE and unfocusable for none and for no row, the kicker in the
 * state colour before the white line, one line, a dot only for live and break, the pulse only for live with animations
 * on, and the stale dim.
 */
@RunWith(RobolectricTestRunner.class)
public class ScorePillTest {
    private static final String LINE = "T20I \u00b7 AFG 19/4 (6) v ZIM";
    private ScorePill pill;

    @Before
    public void aPillInARoot() {
        Context ctx = RuntimeEnvironment.getApplication();
        pill = new ScorePill(ctx);
        new FrameLayout(ctx).addView(pill);
    }

    private static ScoreLine.Text text(String state, String kicker, String line, boolean stale) {
        return ScoreLine.of(state, kicker, line, null, stale);
    }

    @Test
    public void startsHiddenAndUnfocusable() {
        assertEquals(View.GONE, pill.getVisibility());
        assertFalse(pill.isFocusable());
        assertNull(pill.current());
    }

    @Test
    public void noneAndNoRowHideThePill() {
        pill.show(text(ScoreContract.STATE_LIVE, "LIVE", LINE, false), true);
        assertEquals(View.VISIBLE, pill.getVisibility());
        pill.show(text(ScoreContract.STATE_NONE, "", "", false), true);
        assertEquals(View.GONE, pill.getVisibility());
        assertFalse(pill.isFocusable());
        assertEquals("", pill.getText().toString());
        pill.show(text(null, null, null, false), true);
        assertEquals(View.GONE, pill.getVisibility());
    }

    @Test
    public void liveShowsTheKickerInRedThenTheLineInWhite() {
        pill.show(text(ScoreContract.STATE_LIVE, "LIVE", LINE, false), true);
        assertEquals(View.VISIBLE, pill.getVisibility());
        assertTrue(pill.isFocusable());
        assertEquals("LIVE  " + LINE, pill.getText().toString());
        Spanned s = (Spanned) pill.getText();
        ForegroundColorSpan[] spans = s.getSpans(0, s.length(), ForegroundColorSpan.class);
        assertEquals("one coloured span, the kicker", 1, spans.length);
        assertEquals(ScoreLine.LIVE_COLOR, spans[0].getForegroundColor());
        assertEquals(0, s.getSpanStart(spans[0]));
        assertEquals("LIVE".length(), s.getSpanEnd(spans[0]));
        assertEquals("the line itself keeps the view's white", 0xFFFFFFFF, pill.getCurrentTextColor());
        assertNotNull("a dot leads the kicker", pill.getCompoundDrawablesRelative()[0]);
        assertEquals(1, pill.getMaxLines());
        assertEquals("LIVE " + LINE, pill.getContentDescription().toString());
    }

    @Test
    public void theDotPulsesOnlyWhileLiveWithAnimationsOn() {
        pill.show(text(ScoreContract.STATE_LIVE, "LIVE", LINE, false), true);
        assertTrue(pill.pulsing());
        pill.show(text(ScoreContract.STATE_LIVE, "LIVE", LINE, false), false);
        assertFalse("animations off: a still dot", pill.pulsing());
        pill.show(text(ScoreContract.STATE_BREAK, "STUMPS", LINE, false), true);
        assertNotNull("a break has its dot", pill.getCompoundDrawablesRelative()[0]);
        assertFalse("but it does not pulse", pill.pulsing());
        pill.show(text(ScoreContract.STATE_NONE, "", "", false), true);
        assertFalse(pill.pulsing());
    }

    @Test
    public void upcomingAndFinalHaveNoDotAndAGreyKicker() {
        pill.show(text(ScoreContract.STATE_UPCOMING, "19:30", "ISL \u00b7 Bengaluru v Mumbai City", false), true);
        assertNull(pill.getCompoundDrawablesRelative()[0]);
        Spanned s = (Spanned) pill.getText();
        assertEquals(ScoreLine.GREY_COLOR, s.getSpans(0, s.length(), ForegroundColorSpan.class)[0].getForegroundColor());
        pill.show(text(ScoreContract.STATE_FINAL, "FT", "T20I \u00b7 AFG won by 23 runs", false), true);
        assertNull(pill.getCompoundDrawablesRelative()[0]);
        assertEquals("FT  T20I \u00b7 AFG won by 23 runs", pill.getText().toString());
    }

    @Test
    public void anEmptyKickerShowsTheLineAlone() {
        pill.show(text(ScoreContract.STATE_UPCOMING, "", "ISL \u00b7 Bengaluru v Mumbai City", false), true);
        assertEquals("ISL \u00b7 Bengaluru v Mumbai City", pill.getText().toString());
        assertEquals("ISL \u00b7 Bengaluru v Mumbai City", pill.getContentDescription().toString());
    }

    @Test
    public void aStaleRowDimsThePill() {
        pill.show(text(ScoreContract.STATE_LIVE, "LIVE", LINE, true), true);
        assertEquals(ScoreLine.STALE_ALPHA, pill.getAlpha(), 0.001f);
        pill.show(text(ScoreContract.STATE_LIVE, "LIVE", LINE, false), true);
        assertEquals(1f, pill.getAlpha(), 0.001f);
    }
}
