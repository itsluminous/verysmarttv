package com.itsluminous.verysmarttv.topbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.FrameLayout;

import com.itsluminous.verysmarttv.core.contract.ScoreContract;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * The score pill views (DESIGN.md §14.2 ScorePillTest, ADR-041): a line is GONE and unfocusable for none and for no
 * row, shows the kicker in the state colour before the white line, one line, a dot only for live and break, the pulse
 * only for live with animations on, the stale dim, and the sport icon at the line's end with the sport in the content
 * description (ADR-042); the stack shows one line per text, hides the rest and itself, focuses a row clamped to the
 * lines shown, and swaps every line's shade over art.
 */
@RunWith(RobolectricTestRunner.class)
public class ScorePillTest {
    private static final String LINE = "T20I \u00b7 AFG 19/4 (6) v ZIM";
    private ScorePill stack;
    /** The stack's first line, after {@link #show}: what the one-line tests below read. */
    private ScoreLineView pill;

    @Before
    public void aPillInARoot() {
        Context ctx = RuntimeEnvironment.getApplication();
        // In a window, as on Home, so a line can take focus (a TV is never in touch mode).
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.getWindow().setLocalFocus(true, false);
        stack = new ScorePill(ctx);
        FrameLayout root = new FrameLayout(ctx);
        root.addView(stack);
        activity.setContentView(root);
        pill = new ScoreLineView(ctx);
        new FrameLayout(ctx).addView(pill);
    }

    private static ScoreLine.Text text(String state, String kicker, String line, boolean stale) {
        return ScoreLine.of(state, kicker, line, null, stale);
    }

    /** The line's text child. */
    private static android.widget.TextView textOf(ScoreLineView line) {
        return line.findViewById(com.itsluminous.verysmarttv.R.id.scoreLineText);
    }

    /** The line's sport icon. */
    private static android.widget.ImageView iconOf(ScoreLineView line) {
        return line.findViewById(com.itsluminous.verysmarttv.R.id.scoreSportIcon);
    }

    private static ScoreLine.Text row(String id, String state) {
        return ScoreLine.of(state, id, ScoreContract.SPORT_FOOTBALL, "K", "line " + id, null, false);
    }

    // ---- the stack ------------------------------------------------------------------------------------

    @Test
    public void theStackStartsHiddenAndShowsOneLinePerText() {
        assertEquals(View.GONE, stack.getVisibility());
        assertEquals(0, stack.rows());
        assertNull(stack.line(0));
        stack.show(Arrays.asList(row("a", ScoreContract.STATE_LIVE), row("b", ScoreContract.STATE_UPCOMING), row("c", ScoreContract.STATE_FINAL)), true);
        assertEquals(View.VISIBLE, stack.getVisibility());
        assertEquals(3, stack.rows());
        assertEquals("K  line a", stack.line(0).text().toString());
        assertEquals("K  line c", stack.line(2).text().toString());
        assertTrue("every line is a focus target", stack.line(1).isFocusable());
        assertEquals("the lines are stacked, right-aligned", android.widget.LinearLayout.VERTICAL, stack.getOrientation());
        assertEquals(View.VISIBLE, stack.line(2).getVisibility());
        // Fewer: the extra lines hide and the count follows; none: the stack hides.
        stack.show(Collections.singletonList(row("a", ScoreContract.STATE_LIVE)), true);
        assertEquals(1, stack.rows());
        assertEquals(View.GONE, stack.getChildAt(2).getVisibility());
        assertNull(stack.line(1));
        stack.show(Collections.emptyList(), true);
        assertEquals(View.GONE, stack.getVisibility());
        assertEquals(0, stack.rows());
    }

    @Test
    public void theStackShowsAtMostFiveLines() {
        java.util.List<ScoreLine.Text> seven = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) seven.add(row("e" + i, ScoreContract.STATE_UPCOMING));
        stack.show(seven, false);
        assertEquals(ScoreContract.MAX_ROWS, stack.rows());
        assertEquals(ScoreContract.MAX_ROWS, stack.getChildCount());
    }

    @Test
    public void focusRowClampsToTheLinesShownAndReportsTheFocusedOne() {
        assertFalse("nothing shows: nothing to focus", stack.focusRow(0));
        assertEquals(-1, stack.focusedRow());
        stack.show(Arrays.asList(row("a", ScoreContract.STATE_LIVE), row("b", ScoreContract.STATE_UPCOMING), row("c", ScoreContract.STATE_FINAL)), true);
        assertTrue(stack.focusRow(2));
        assertEquals(2, stack.focusedRow());
        assertTrue(stack.line(2).hasFocus());
        assertTrue(stack.focusRow(9));
        assertEquals("past the bottom: the bottom line", 2, stack.focusedRow());
        assertTrue(stack.focusRow(-1));
        assertEquals("above the top: the top line", 0, stack.focusedRow());
    }

    @Test
    public void overArtSwapsEveryLinesShadeAndKeepsItsPadding() {
        stack.show(Arrays.asList(row("a", ScoreContract.STATE_LIVE), row("b", ScoreContract.STATE_UPCOMING)), true);
        int pad = stack.line(0).getPaddingStart();
        assertTrue(pad > 0);
        stack.setOverArt(true);
        assertEquals(pad, stack.line(1).getPaddingStart());
        assertNotNull(stack.line(1).getBackground());
        stack.show(Arrays.asList(row("a", ScoreContract.STATE_LIVE), row("b", ScoreContract.STATE_UPCOMING), row("c", ScoreContract.STATE_FINAL)), true);
        assertEquals("a line added over art gets the same padding", pad, stack.line(2).getPaddingStart());
        stack.setOverArt(false);
        assertEquals(pad, stack.line(0).getPaddingStart());
    }

    // ---- one line ---------------------------------------------------------------------------------------

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
        assertEquals("", pill.text().toString());
        pill.show(text(null, null, null, false), true);
        assertEquals(View.GONE, pill.getVisibility());
    }

    @Test
    public void liveShowsTheKickerInRedThenTheLineInWhite() {
        pill.show(text(ScoreContract.STATE_LIVE, "LIVE", LINE, false), true);
        assertEquals(View.VISIBLE, pill.getVisibility());
        assertTrue(pill.isFocusable());
        assertEquals("LIVE  " + LINE, pill.text().toString());
        Spanned s = (Spanned) pill.text();
        ForegroundColorSpan[] spans = s.getSpans(0, s.length(), ForegroundColorSpan.class);
        assertEquals("one coloured span, the kicker", 1, spans.length);
        assertEquals(ScoreLine.LIVE_COLOR, spans[0].getForegroundColor());
        assertEquals(0, s.getSpanStart(spans[0]));
        assertEquals("LIVE".length(), s.getSpanEnd(spans[0]));
        assertEquals("the line itself keeps the view's white", 0xFFFFFFFF, textOf(pill).getCurrentTextColor());
        assertNotNull("a dot leads the kicker", textOf(pill).getCompoundDrawablesRelative()[0]);
        assertEquals(1, textOf(pill).getMaxLines());
        assertEquals("the sport closes the description; the row has none, so the generic name", "LIVE " + LINE + ". Sport", pill.getContentDescription().toString());
        assertEquals("no sport given: the trophy", "ic_sport_other", iconOf(pill).getTag());
    }

    @Test
    public void theSportIconSitsAtTheEndOfTheLineAndNamesTheSport() {
        pill.show(ScoreLine.of(ScoreContract.STATE_LIVE, "ev", ScoreContract.SPORT_CRICKET, "LIVE", LINE, null, false), true);
        android.widget.ImageView icon = iconOf(pill);
        assertEquals(View.VISIBLE, icon.getVisibility());
        assertNotNull(icon.getDrawable());
        assertEquals("ic_sport_cricket", icon.getTag());
        assertEquals("the icon is the last child, after the text", icon, pill.getChildAt(pill.getChildCount() - 1));
        assertEquals("LIVE " + LINE + ". Cricket", pill.getContentDescription().toString());
        assertEquals("the children leave accessibility to the row", View.IMPORTANT_FOR_ACCESSIBILITY_NO, icon.getImportantForAccessibility());
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, textOf(pill).getImportantForAccessibility());
        int dp16 = Math.round(16 * pill.getResources().getDisplayMetrics().density);
        assertEquals(dp16, icon.getLayoutParams().width);
        assertEquals(dp16, icon.getLayoutParams().height);
        pill.show(ScoreLine.of(ScoreContract.STATE_UPCOMING, "ev", ScoreContract.SPORT_TENNIS, "19:30", "ATP \u00b7 A v B", null, false), true);
        assertEquals("ic_sport_tennis", icon.getTag());
        assertEquals("19:30 ATP \u00b7 A v B. Tennis", pill.getContentDescription().toString());
        pill.show(ScoreLine.of(ScoreContract.STATE_UPCOMING, "ev", "kabaddi", "19:30", "PKL \u00b7 A v B", null, false), true);
        assertEquals("a sport the launcher has not learnt: the trophy", "ic_sport_other", icon.getTag());
        pill.show(text(ScoreContract.STATE_NONE, "", "", false), true);
        assertNull("hidden: no icon, no tag", icon.getDrawable());
        assertNull(icon.getTag());
    }

    @Test
    public void theDotPulsesOnlyWhileLiveWithAnimationsOn() {
        pill.show(text(ScoreContract.STATE_LIVE, "LIVE", LINE, false), true);
        assertTrue(pill.pulsing());
        pill.show(text(ScoreContract.STATE_LIVE, "LIVE", LINE, false), false);
        assertFalse("animations off: a still dot", pill.pulsing());
        pill.show(text(ScoreContract.STATE_BREAK, "STUMPS", LINE, false), true);
        assertNotNull("a break has its dot", textOf(pill).getCompoundDrawablesRelative()[0]);
        assertFalse("but it does not pulse", pill.pulsing());
        pill.show(text(ScoreContract.STATE_NONE, "", "", false), true);
        assertFalse(pill.pulsing());
    }

    @Test
    public void upcomingAndFinalHaveNoDotAndAGreyKicker() {
        pill.show(text(ScoreContract.STATE_UPCOMING, "19:30", "ISL \u00b7 Bengaluru v Mumbai City", false), true);
        assertNull(textOf(pill).getCompoundDrawablesRelative()[0]);
        Spanned s = (Spanned) pill.text();
        assertEquals(ScoreLine.GREY_COLOR, s.getSpans(0, s.length(), ForegroundColorSpan.class)[0].getForegroundColor());
        pill.show(text(ScoreContract.STATE_FINAL, "FT", "T20I \u00b7 AFG won by 23 runs", false), true);
        assertNull(textOf(pill).getCompoundDrawablesRelative()[0]);
        assertEquals("FT  T20I \u00b7 AFG won by 23 runs", pill.text().toString());
    }

    @Test
    public void anEmptyKickerShowsTheLineAlone() {
        pill.show(text(ScoreContract.STATE_UPCOMING, "", "ISL \u00b7 Bengaluru v Mumbai City", false), true);
        assertEquals("ISL \u00b7 Bengaluru v Mumbai City", pill.text().toString());
        assertEquals("ISL \u00b7 Bengaluru v Mumbai City. Sport", pill.getContentDescription().toString());
    }

    @Test
    public void aStaleRowDimsThePill() {
        pill.show(text(ScoreContract.STATE_LIVE, "LIVE", LINE, true), true);
        assertEquals(ScoreLine.STALE_ALPHA, pill.getAlpha(), 0.001f);
        pill.show(text(ScoreContract.STATE_LIVE, "LIVE", LINE, false), true);
        assertEquals(1f, pill.getAlpha(), 0.001f);
    }
}
