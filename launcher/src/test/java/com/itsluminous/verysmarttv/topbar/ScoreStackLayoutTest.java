package com.itsluminous.verysmarttv.topbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * The score stack against the top shelf (DESIGN.md §9.2, ADR-046), the maths alone: the stack's bounds for 0 to 5
 * rows, the shelf text's width when the block crosses the stack vertically and when it lies clear of it, and the
 * backdrop's shade under the stack. Numbers in px at density 1, so 56 is the header's 56dp margin.
 */
@RunWith(RobolectricTestRunner.class)
public class ScoreStackLayoutTest {
    private static final int LINE = 28, GAP = 4, TOP = 104, RIGHT = 904;

    private static Rect stack(int rows, int width) {
        return ScoreStackLayout.stackBounds(rows, LINE, GAP, TOP, RIGHT, width);
    }

    @Test
    public void noRows_isEmptyAtTheTopRightCorner_andCostsTheShelfNothing() {
        Rect none = stack(0, 300);
        assertTrue(none.isEmpty());
        assertEquals(RIGHT, none.right);
        assertEquals(TOP, none.top);
        assertEquals(560, ScoreStackLayout.heroTextMaxWidth(none, 56, 560, 136, 337, 16, false));
        assertTrue(ScoreStackLayout.shadeBounds(none, 24, 960, false).isEmpty());
    }

    @Test
    public void rows_stackUnderOneAnotherWithTheGapBetween_endAlignedAtRight() {
        assertEquals(new Rect(RIGHT - 300, TOP, RIGHT, TOP + LINE), stack(1, 300));
        assertEquals(new Rect(RIGHT - 300, TOP, RIGHT, TOP + 3 * LINE + 2 * GAP), stack(3, 300));
        assertEquals(new Rect(RIGHT - 300, TOP, RIGHT, TOP + 5 * LINE + 4 * GAP), stack(5, 300));
        assertEquals("the widest line sets the left edge", RIGHT - 410, stack(5, 410).left);
    }

    @Test
    public void aShelfBelowTheStack_keepsItsFullWidth() {
        // One row ends at 132; the carousel shelf at hero_height 100 starts at 136.
        assertEquals(560, ScoreStackLayout.heroTextMaxWidth(stack(1, 330), 56, 560, 136, 337, 16, false));
        // The plain shelf under a short hero_height sits well under a five-row stack (ends 260).
        assertEquals(560, ScoreStackLayout.heroTextMaxWidth(stack(5, 330), 56, 560, 270, 330, 16, false));
    }

    @Test
    public void aShelfCrossingTheStack_endsAClearanceBeforeItsLeftEdge() {
        Rect three = stack(3, 330);   // left 574
        assertEquals(574 - 16 - 56, ScoreStackLayout.heroTextMaxWidth(three, 56, 560, 136, 337, 16, false));
        // A narrow stack the full width never reaches changes nothing.
        assertEquals(560, ScoreStackLayout.heroTextMaxWidth(stack(5, 200), 56, 560, 136, 337, 16, false));   // left 704 > 56 + 560 + 16
        // Touching the top or bottom edge exactly is not a crossing.
        assertEquals(560, ScoreStackLayout.heroTextMaxWidth(three, 56, 560, three.bottom, 400, 16, false));
        assertEquals(560, ScoreStackLayout.heroTextMaxWidth(three, 56, 560, 0, three.top, 16, false));
    }

    @Test
    public void aStackWiderThanTheShelfsRoom_leavesNoNegativeWidth() {
        assertEquals(0, ScoreStackLayout.heroTextMaxWidth(stack(2, 900), 56, 560, 100, 200, 16, false));
    }

    @Test
    public void theShade_isTheStackGrownLeftAndDown_toTheTopAndRightEdges() {
        Rect five = stack(5, 330);
        assertEquals(new Rect(five.left - 24, 0, 960, five.bottom + 24), ScoreStackLayout.shadeBounds(five, 24, 960, false));
    }

    @Test
    public void rtl_theStackHangsOnTheLeft_theShelfStartsOnTheRight() {
        // Mirrored: the stack's lines end at 56 and reach to 386; the shelf's text starts at 904 and runs leftwards.
        Rect three = ScoreStackLayout.stackBounds(3, LINE, GAP, TOP, 56 + 330, 330);
        assertEquals(904 - 16 - 386, ScoreStackLayout.heroTextMaxWidth(three, 904, 560, 136, 337, 16, true));
        assertEquals(560, ScoreStackLayout.heroTextMaxWidth(three, 904, 560, 200, 337, 16, true));
        assertEquals(new Rect(0, 0, 386 + 24, three.bottom + 24), ScoreStackLayout.shadeBounds(three, 24, 960, true));
    }
}
