package com.itsluminous.verysmarttv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.itsluminous.verysmarttv.support.DummyTiles;
import com.itsluminous.verysmarttv.support.HomeTestRule;
import com.itsluminous.verysmarttv.support.TestTheme;
import com.itsluminous.verysmarttv.topbar.ResourceBars;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

/**
 * The RAM and Storage block on the emulator (DESIGN.md §14.3 ResourceBarsVisibleTest): present with the widgets key and
 * reading "N GB free" per row from the TV's own figures, 16dp left of the pills and centred on them; absent without it.
 */
@RunWith(AndroidJUnit4.class)
public class ResourceBarsVisibleTest {
    private static final Pattern FREE = Pattern.compile("\\d+([.,]\\d)? (GB|MB) free");

    @Rule public final HomeTestRule home = new HomeTestRule();

    @Test
    public void barsShowWithTheKeyAndReadTheTvsFigures() throws Exception {
        DummyTiles.enable(6);
        home.pushTheme(new TestTheme().columns(4).widgets());
        home.launchHome();
        home.waitFor("the sampler's first reading", a -> {
            ResourceBars bars = a.findViewById(R.id.resourceBars);
            return bars.getVisibility() == View.VISIBLE && !bars.ramText().isEmpty() && !bars.storageText().isEmpty();
        });
        String ram = home.onHome(a -> ((ResourceBars) a.findViewById(R.id.resourceBars)).ramText());
        String storage = home.onHome(a -> ((ResourceBars) a.findViewById(R.id.resourceBars)).storageText());
        assertTrue(ram, FREE.matcher(ram).matches());
        assertTrue(storage, FREE.matcher(storage).matches());
        int[] geometry = home.onHome(a -> {
            View root = a.findViewById(R.id.root), bar = a.findViewById(R.id.topButtons), bars = a.findViewById(R.id.resourceBars);
            Rect b = new Rect(0, 0, bar.getWidth(), bar.getHeight()), r = new Rect(0, 0, bars.getWidth(), bars.getHeight());
            ((ViewGroup) root).offsetDescendantRectToMyCoords(bar, b);
            ((ViewGroup) root).offsetDescendantRectToMyCoords(bars, r);
            float d = a.getResources().getDisplayMetrics().density;
            return new int[]{b.left - r.right, Math.round(16 * d), r.width(), Math.round(ResourceBars.WIDTH_DP * d), r.centerY() - b.centerY()};
        });
        assertEquals("16dp between the block and the pills", geometry[1], geometry[0], 1);
        assertEquals("150dp wide", geometry[3], geometry[2]);
        assertTrue("centred on the pills: " + geometry[4], Math.abs(geometry[4]) <= 2);
    }

    @Test
    public void barsAreAbsentWithoutTheKey() throws Exception {
        DummyTiles.enable(6);
        home.pushTheme(new TestTheme().columns(4));
        home.launchHome();
        assertEquals(View.GONE, (int) home.onHome(a -> a.findViewById(R.id.resourceBars).getVisibility()));
        assertEquals("nothing is read while the row is off", "", home.onHome(a -> ((ResourceBars) a.findViewById(R.id.resourceBars)).ramText()));
    }
}
