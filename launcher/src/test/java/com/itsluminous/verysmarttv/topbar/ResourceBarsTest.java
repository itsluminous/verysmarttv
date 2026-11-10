package com.itsluminous.verysmarttv.topbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.GraphicsMode;

import java.util.Locale;

/**
 * The RAM and Storage block (DESIGN.md §14.2 ResourceBarsTest): it measures 150 dp by two 22 dp rows, its texts are
 * Gb's figure with the unit and "free", two tracks are drawn (one per known row), and a row whose figure is unknown
 * is blank.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ResourceBarsTest {
    private static final long MB = 1024L * 1024L, GB = MB * 1024L;
    private Context ctx;
    private ResourceBars bars;
    private float density;

    @Before
    public void aView() {
        ctx = RuntimeEnvironment.getApplication();
        bars = new ResourceBars(ctx);
        density = ctx.getResources().getDisplayMetrics().density;
    }

    private void layout() {
        bars.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        bars.layout(0, 0, bars.getMeasuredWidth(), bars.getMeasuredHeight());
    }

    @Test
    public void measures150dpByTwoRows() {
        layout();
        assertEquals(Math.round(ResourceBars.WIDTH_DP * density), bars.getMeasuredWidth());
        assertEquals(Math.round(2 * ResourceBars.PITCH_DP * density), bars.getMeasuredHeight());
    }

    @Test
    public void textsAreTheFigureTheUnitAndFree() {
        bars.setValues(true, Math.round(1.1 * GB), 2 * GB, true, Math.round(21.6 * GB), 32 * GB);
        Locale locale = ctx.getResources().getConfiguration().getLocales().get(0);
        assertEquals(Gb.amount(Math.round(1.1 * GB), locale) + " GB free", bars.ramText());
        assertEquals(Gb.amount(Math.round(21.6 * GB), locale) + " GB free", bars.storageText());
        assertEquals("512 MB free", bars.freeText(512 * MB));
        assertEquals("0 B free", bars.freeText(0));
        assertEquals("RAM " + bars.ramText() + ", Storage " + bars.storageText(), bars.getContentDescription().toString());
    }

    @Test
    public void anUnknownRowIsBlank() {
        bars.setValues(false, 0, 0, true, 10 * GB, 20 * GB);
        assertEquals("", bars.ramText());
        assertNotEquals("", bars.storageText());
        assertEquals("Storage " + bars.storageText(), bars.getContentDescription().toString());
        bars.setValues(false, 0, 0, false, 0, 0);
        assertEquals(null, bars.getContentDescription());
    }

    /** Draws into a bitmap and counts the rows whose track pixels are set: one track per known row. */
    private int tracksDrawn() {
        layout();
        Bitmap b = Bitmap.createBitmap(bars.getWidth(), bars.getHeight(), Bitmap.Config.ARGB_8888);
        bars.draw(new Canvas(b));
        int rows = 0;
        float pitch = ResourceBars.PITCH_DP * density;
        float top = (bars.getHeight() - 2 * pitch) / 2f;
        for (int row = 0; row < 2; row++) {
            // The track's own line: a pixel inside it, mid-width, where neither text reaches.
            float trackTop = top + (row + 1) * pitch - ResourceBars.TRACK_DP * density - 2 * density;
            int y = Math.round(trackTop + ResourceBars.TRACK_DP * density / 2f);
            int x = bars.getWidth() / 2;
            if (y >= 0 && y < b.getHeight() && (b.getPixel(x, y) >>> 24) > 0) rows++;
        }
        return rows;
    }

    @Test
    public void drawsOneTrackPerKnownRow() {
        bars.setValues(true, GB, 2 * GB, true, 10 * GB, 20 * GB);
        assertEquals(2, tracksDrawn());
        bars.setValues(true, GB, 2 * GB, false, 0, 0);
        assertEquals(1, tracksDrawn());
        bars.setValues(false, 0, 0, false, 0, 0);
        assertEquals(0, tracksDrawn());
    }

    @Test
    public void theFillCoversTheUsedShare() {
        bars.setValues(true, GB, 4 * GB, false, 0, 0);   // three quarters used
        layout();
        Bitmap b = Bitmap.createBitmap(bars.getWidth(), bars.getHeight(), Bitmap.Config.ARGB_8888);
        bars.draw(new Canvas(b));
        float pitch = ResourceBars.PITCH_DP * density;
        float top = (bars.getHeight() - 2 * pitch) / 2f;
        int y = Math.round(top + pitch - ResourceBars.TRACK_DP * density - 2 * density + ResourceBars.TRACK_DP * density / 2f);
        int filled = b.getPixel(Math.round(bars.getWidth() * 0.5f), y) >>> 24;
        int empty = b.getPixel(Math.round(bars.getWidth() * 0.9f), y) >>> 24;
        assertTrue("the fill (85% white) is more opaque than the track (30% white): " + filled + " vs " + empty, filled > empty);
    }
}
