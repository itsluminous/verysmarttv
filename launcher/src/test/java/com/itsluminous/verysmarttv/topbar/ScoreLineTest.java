package com.itsluminous.verysmarttv.topbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static org.junit.Assert.assertNull;

import com.itsluminous.verysmarttv.core.contract.ScoreContract;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The pill's text for a provider row, state by state (DESIGN.md §9.2, §14.2 ScoreLineTest): hidden for none and for
 * no row, the kicker and dot per state, the companion's colour over the default, the 48-character cut with an
 * ellipsis, and the stale dimming flag; and the stack (ADR-041): the companion's order kept, cut to score_rows held to
 * 1..5, hidden rows dropped, and the lines kept in place while the companion only turns its first row.
 */
public class ScoreLineTest {
    private static final String CRICKET = "T20I \u00b7 AFG 19/4 (6) v ZIM";

    private static ScoreLine.Text row(String id, String state) {
        return ScoreLine.of(state, id, "K", "line " + id, null, false);
    }

    private static List<String> ids(List<ScoreLine.Text> texts) {
        List<String> out = new ArrayList<>();
        for (ScoreLine.Text t : texts) out.add(t.eventId);
        return out;
    }

    // ---- the stack ------------------------------------------------------------------------------------

    @Test
    public void stackKeepsTheCompanionsOrderAndCutsToTheRows() {
        List<ScoreLine.Text> fresh = Arrays.asList(row("live", ScoreContract.STATE_LIVE), row("break", ScoreContract.STATE_BREAK),
                row("up", ScoreContract.STATE_UPCOMING), row("fin", ScoreContract.STATE_FINAL), row("fin2", ScoreContract.STATE_FINAL));
        List<ScoreLine.Text> none = Collections.emptyList();
        assertEquals(Arrays.asList("live", "break", "up"), ids(ScoreLine.stack(none, fresh, 3)));
        assertEquals(Collections.singletonList("live"), ids(ScoreLine.stack(none, fresh, 1)));
        assertEquals(Arrays.asList("live", "break", "up", "fin", "fin2"), ids(ScoreLine.stack(none, fresh, 5)));
        assertEquals("fewer rows than lines: all of them, in order", Arrays.asList("live", "break"), ids(ScoreLine.stack(none, fresh.subList(0, 2), 5)));
        assertEquals("no rows: no lines", none, ScoreLine.stack(none, none, 3));
    }

    @Test
    public void stackHoldsTheRowsToOneThroughFive() {
        List<ScoreLine.Text> fresh = new ArrayList<>();
        for (int i = 0; i < 8; i++) fresh.add(row("e" + i, ScoreContract.STATE_UPCOMING));
        assertEquals(1, ScoreLine.stack(Collections.emptyList(), fresh, 0).size());
        assertEquals(1, ScoreLine.stack(Collections.emptyList(), fresh, -4).size());
        assertEquals(5, ScoreLine.stack(Collections.emptyList(), fresh, 9).size());
        assertEquals(ScoreContract.MAX_ROWS, ScoreLine.MAX_ROWS);
        assertEquals(1, ScoreLine.clampRows(0));
        assertEquals(5, ScoreLine.clampRows(99));
        assertEquals(3, ScoreLine.clampRows(3));
    }

    @Test
    public void stackDropsHiddenRowsBeforeCounting() {
        List<ScoreLine.Text> fresh = Arrays.asList(row("a", ScoreContract.STATE_LIVE), ScoreLine.of(ScoreContract.STATE_NONE, "", "", null, false),
                row("b", ScoreContract.STATE_UPCOMING), ScoreLine.of(null, null, null, null, false), row("c", ScoreContract.STATE_FINAL));
        assertEquals(Arrays.asList("a", "b", "c"), ids(ScoreLine.stack(Collections.emptyList(), fresh, 3)));
    }

    @Test
    public void stackKeepsTheLinesInPlaceWhileTheCompanionOnlyTurnsItsFirstRow() {
        List<ScoreLine.Text> before = ScoreLine.stack(Collections.emptyList(),
                Arrays.asList(row("a", ScoreContract.STATE_LIVE), row("b", ScoreContract.STATE_LIVE), row("c", ScoreContract.STATE_UPCOMING)), 3);
        // 20 s on: b takes the companion's first row.
        List<ScoreLine.Text> turned = Arrays.asList(row("b", ScoreContract.STATE_LIVE), row("a", ScoreContract.STATE_LIVE), row("c", ScoreContract.STATE_UPCOMING));
        assertEquals("the same events in the same states: the lines stay put", Arrays.asList("a", "b", "c"), ids(ScoreLine.stack(before, turned, 3)));
        assertEquals("the texts are the fresh ones, in the old places", "line b", ScoreLine.stack(before, turned, 3).get(1).line);
        // A one-line pill still alternates: its one line is the companion's first row.
        List<ScoreLine.Text> one = ScoreLine.stack(Collections.emptyList(), Arrays.asList(row("a", ScoreContract.STATE_LIVE), row("b", ScoreContract.STATE_LIVE)), 1);
        assertEquals(Collections.singletonList("b"), ids(ScoreLine.stack(one, turned, 1)));
    }

    @Test
    public void stackTakesTheCompanionsOrderAgainOnAnyRealChange() {
        List<ScoreLine.Text> before = Arrays.asList(row("a", ScoreContract.STATE_LIVE), row("b", ScoreContract.STATE_LIVE), row("c", ScoreContract.STATE_UPCOMING));
        assertEquals("a ended: the live one leads again", Arrays.asList("b", "a", "c"),
                ids(ScoreLine.stack(before, Arrays.asList(row("b", ScoreContract.STATE_LIVE), row("a", ScoreContract.STATE_FINAL), row("c", ScoreContract.STATE_UPCOMING)), 3)));
        assertEquals("c left, d arrived", Arrays.asList("b", "a", "d"),
                ids(ScoreLine.stack(before, Arrays.asList(row("b", ScoreContract.STATE_LIVE), row("a", ScoreContract.STATE_LIVE), row("d", ScoreContract.STATE_UPCOMING)), 3)));
        assertEquals("one fewer", Arrays.asList("b", "a"),
                ids(ScoreLine.stack(before, Arrays.asList(row("b", ScoreContract.STATE_LIVE), row("a", ScoreContract.STATE_LIVE)), 3)));
        List<ScoreLine.Text> noIds = Arrays.asList(ScoreLine.of(ScoreContract.STATE_LIVE, "LIVE", "x", null, false), ScoreLine.of(ScoreContract.STATE_LIVE, "LIVE", "y", null, false));
        assertEquals("rows without event ids are never matched up", "y", ScoreLine.stack(noIds, Arrays.asList(noIds.get(1), noIds.get(0)), 2).get(0).line);
        assertNull(noIds.get(0).eventId);
    }

    // ---- one line ---------------------------------------------------------------------------------------

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
