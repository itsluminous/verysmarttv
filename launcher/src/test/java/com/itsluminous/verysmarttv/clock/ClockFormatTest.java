package com.itsluminous.verysmarttv.clock;

import android.content.Context;
import android.widget.FrameLayout;
import android.widget.TextClock;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.assertEquals;

/** clock_format: "12" or "24" makes every TextClock under Home's root use that format whatever the TV is set to. */
@RunWith(RobolectricTestRunner.class)
public class ClockFormatTest {
    private FrameLayout root;
    private TextClock clock, nested;

    @Before
    public void twoClocksUnderOneRoot() {
        Context ctx = RuntimeEnvironment.getApplication();
        root = new FrameLayout(ctx);
        clock = new TextClock(ctx);
        nested = new TextClock(ctx);
        for (TextClock c : new TextClock[]{clock, nested}) {
            c.setFormat12Hour("h:mm");
            c.setFormat24Hour("HH:mm");
        }
        root.addView(clock);
        FrameLayout inner = new FrameLayout(ctx);
        inner.addView(nested);
        root.addView(inner);
    }

    @Test
    public void twelveMakesTheTwentyFourHourPatternTheTwelveHourOne() {
        ClockFormat.apply(root, "12");
        for (TextClock c : new TextClock[]{clock, nested}) {
            assertEquals("h:mm", c.getFormat24Hour().toString());
            assertEquals("h:mm", c.getFormat12Hour().toString());
        }
    }

    @Test
    public void twentyFourMakesTheTwelveHourPatternTheTwentyFourHourOne() {
        ClockFormat.apply(root, "24");
        assertEquals("HH:mm", clock.getFormat12Hour().toString());
        assertEquals("HH:mm", nested.getFormat12Hour().toString());
    }

    @Test
    public void systemLeavesBothPatternsToTheTv() {
        ClockFormat.apply(root, "system");
        assertEquals("h:mm", clock.getFormat12Hour().toString());
        assertEquals("HH:mm", clock.getFormat24Hour().toString());
    }
}
