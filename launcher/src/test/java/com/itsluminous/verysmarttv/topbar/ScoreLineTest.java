package com.itsluminous.verysmarttv.topbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.itsluminous.verysmarttv.core.contract.ScoreContract;

import org.junit.Test;

/**
 * The pill's text for a provider row, state by state (DESIGN.md §9.2, §14.2 ScoreLineTest): hidden for none and for
 * no row, the kicker and dot per state, the companion's colour over the default, the 48-character cut with an
 * ellipsis, and the stale dimming flag.
 */
public class ScoreLineTest {
    private static final String CRICKET = "T20I \u00b7 AFG 19/4 (6) v ZIM";

    @Test
    public void liveHasAPulsingDotAndTheRedKicker() {
        ScoreLine.Text t = ScoreLine.of(ScoreContract.STATE_LIVE, "LIVE", CRICKET, null, false);
        assertTrue(t.shown);
        assertEquals("LIVE", t.kicker);
        assertEquals(CRICKET, t.line);
        assertEquals(ScoreLine.LIVE_COLOR, t.color);
        assertTrue(t.dot);
        assertTrue(t.pulse);
        assertFalse(t.dim);
        assertEquals("pulsing dot [LIVE] " + CRICKET, t.toString());
    }

    @Test
    public void breakHasAStillAmberDotAndTheFeedsWord() {
        ScoreLine.Text t = ScoreLine.of(ScoreContract.STATE_BREAK, "STUMPS", "Test \u00b7 BAN 386 & 49/2 v WI", null, false);
        assertTrue(t.shown);
        assertEquals("STUMPS", t.kicker);
        assertEquals(ScoreLine.BREAK_COLOR, t.color);
        assertTrue(t.dot);
        assertFalse("only live pulses", t.pulse);
    }

    @Test
    public void upcomingAndFinalAreGreyWithoutADot() {
        ScoreLine.Text up = ScoreLine.of(ScoreContract.STATE_UPCOMING, "19:30", "ISL \u00b7 Bengaluru v Mumbai City", null, false);
        assertEquals("19:30", up.kicker);
        assertEquals(ScoreLine.GREY_COLOR, up.color);
        assertFalse(up.dot);
        assertFalse(up.pulse);
        ScoreLine.Text fin = ScoreLine.of(ScoreContract.STATE_FINAL, "FT", "T20I \u00b7 AFG won by 23 runs", null, false);
        assertEquals("FT", fin.kicker);
        assertEquals(ScoreLine.GREY_COLOR, fin.color);
        assertFalse(fin.dot);
        assertEquals("[FT] T20I \u00b7 AFG won by 23 runs", fin.toString());
    }

    @Test
    public void noneNoRowAndAnEmptyLineHideThePill() {
        assertFalse(ScoreLine.of(ScoreContract.STATE_NONE, "", "", null, false).shown);
        assertFalse("no row: the provider is absent or refused", ScoreLine.of(null, null, null, null, false).shown);
        assertFalse("a state with nothing to say", ScoreLine.of(ScoreContract.STATE_LIVE, "LIVE", "   ", null, false).shown);
        assertEquals("hidden", ScoreLine.of(null, null, null, null, false).toString());
    }

    @Test
    public void theCompanionsColourWinsOverTheDefault() {
        assertEquals(0xFF1E88E5, ScoreLine.of(ScoreContract.STATE_LIVE, "LIVE", CRICKET, "#1E88E5", false).color);
        assertEquals("eight digits carry their alpha", 0x80FFB300, ScoreLine.of(ScoreContract.STATE_BREAK, "HT", CRICKET, "#80FFB300", false).color);
        assertEquals("an unparsable colour falls back", ScoreLine.LIVE_COLOR, ScoreLine.of(ScoreContract.STATE_LIVE, "LIVE", CRICKET, "red", false).color);
        assertEquals(ScoreLine.GREY_COLOR, ScoreLine.of(ScoreContract.STATE_FINAL, "FT", CRICKET, "#GGGGGG", false).color);
        assertEquals(ScoreLine.GREY_COLOR, ScoreLine.of(ScoreContract.STATE_FINAL, "FT", CRICKET, "", false).color);
    }

    @Test
    public void aLongLineIsCutAtFortyEightCharactersWithAnEllipsis() {
        String fifty = "Premier League \u00b7 Manchester United 2\u20131 Liverpool 90'";
        assertTrue(fifty.length() > ScoreContract.LINE_MAX_CHARS);
        String cut = ScoreLine.shorten(fifty);
        assertEquals(ScoreContract.LINE_MAX_CHARS, cut.length());
        assertTrue(cut.endsWith("\u2026"));
        assertEquals(fifty.substring(0, ScoreContract.LINE_MAX_CHARS - 1).trim() + "\u2026", cut);
        String exact = "x".repeat(ScoreContract.LINE_MAX_CHARS);
        assertEquals("a line at the limit is left alone", exact, ScoreLine.shorten(exact));
        assertEquals(cut, ScoreLine.of(ScoreContract.STATE_LIVE, "LIVE", fifty, null, false).line);
    }

    @Test
    public void aStaleRowDims() {
        ScoreLine.Text t = ScoreLine.of(ScoreContract.STATE_LIVE, "LIVE", CRICKET, null, true);
        assertTrue(t.shown);
        assertTrue(t.dim);
        assertTrue(t.toString().endsWith("(stale)"));
        assertEquals(0.6f, ScoreLine.STALE_ALPHA, 0.001f);
    }

    @Test
    public void kickerAndLineAreTrimmedAndAMissingKickerIsEmpty() {
        ScoreLine.Text t = ScoreLine.of(ScoreContract.STATE_UPCOMING, null, "  ISL \u00b7 Bengaluru v Mumbai City  ", null, false);
        assertEquals("", t.kicker);
        assertEquals("ISL \u00b7 Bengaluru v Mumbai City", t.line);
    }
}
